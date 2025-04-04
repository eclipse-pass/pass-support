/*
 * Copyright 2018 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.pass.deposit.service;

import static java.lang.Integer.toHexString;
import static java.lang.String.format;
import static java.lang.System.identityHashCode;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.eclipse.pass.deposit.DepositServiceRuntimeException;
import org.eclipse.pass.deposit.TransportConnectionException;
import org.eclipse.pass.deposit.assembler.PackageStream;
import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction;
import org.eclipse.pass.deposit.cri.CriticalRepositoryInteraction.CriticalResult;
import org.eclipse.pass.deposit.model.Packager;
import org.eclipse.pass.deposit.service.DepositUtil.DepositWorkerContext;
import org.eclipse.pass.deposit.support.deploymenttest.DeploymentTestDataService;
import org.eclipse.pass.deposit.transport.Transport;
import org.eclipse.pass.deposit.transport.TransportResponse;
import org.eclipse.pass.deposit.transport.TransportSession;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assembles, packages, and transports the custodial content associated with a {@code Submission} to a remote endpoint
 * represented by a {@code Deposit}, {@code Repository} tuple.  Creates any {@code RepositoryCopy} resources, and
 * maintains/updates the status of {@code Deposit} during the process.
 * <p>
 * In particular, this class distinguishes between <em>physical</em> and <em>logical</em> success of a deposit.  The
 * physical outcome refers to whether or not the bytes of the custodial content were successfully received by the the
 * repository endpoint.  This has to do with the physical characteristics of the underlying transport, authentication,
 * etc.
 * </p>
 * <p>
 * The logical outcome refers to whether or not the custodial content of the package was accepted and accessioned by
 * the repository endpoint.  That is to say the logical outcome revolves around the transfer of custody to another
 * repository.  A successful logical outcome indicates that custody was transferred to another repository.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositTask {

    private static final Logger LOG = LoggerFactory.getLogger(DepositTask.class);

    private final DepositWorkerContext dc;
    private final PassClient passClient;
    private final CriticalRepositoryInteraction cri;

    public DepositTask(DepositWorkerContext dc,
                       PassClient passClient,
                       CriticalRepositoryInteraction cri) {
        this.dc = dc;
        this.passClient = passClient;
        this.cri = cri;
    }

    public void executeDeposit() {

        LOG.debug("Running {}@{}", DepositTask.class.getSimpleName(), toHexString(identityHashCode(this)));

        CriticalResult<TransportResponse, Deposit> physicalResult =
            cri.performCritical(dc.deposit().getId(), Deposit.class,

                /*
                 * Only "intermediate" deposits can be processed by {@code DepositTask}
                 */
                                DepositTaskCriFunc.depositPrecondition(),

                /*
                 * Determines *physical* success of the Deposit: were the bytes of the package successfully received?
                 */
                                DepositTaskCriFunc.depositPostcondition(dc),

                /*
                 * Assemble and stream a package of content to the repository endpoint, update status to SUBMITTED
                 */
                                DepositTaskCriFunc.performDeposit(dc));

        // Check *physical* success: were the bytes of the package successfully streamed to endpoint?

        if (!physicalResult.success()) {
            // one of the pre or post conditions failed, or the critical code path (creating a package failed)
            if (physicalResult.throwable().isPresent()) {
                Throwable t = physicalResult.throwable().get();
                String msg = format("Failed to perform deposit for tuple [%s, %s, %s]: %s",
                                    dc.submission().getId(), dc.repository().getId(), dc.deposit().getId(),
                                    t.getMessage());
                throw new DepositServiceRuntimeException(msg, t, dc.deposit());
            }

            String msg = format("Failed to perform deposit for tuple [%s, %s, %s]",
                                dc.submission().getId(), dc.repository().getId(), dc.deposit().getId());
            throw new DepositServiceRuntimeException(msg, dc.deposit());
        }

        TransportResponse transportResponse = physicalResult.result()
                .orElseThrow(() ->
                                  new DepositServiceRuntimeException("Missing TransportResponse for " +
                                      dc.deposit().getId(),
                                      dc.deposit()));

        // Determine *logical* success: was the Deposit accepted by the remote system?
        // Create an IN_PROGRESS placeholder copy, attach the Deposit to the placeholder.  Then supply the Submission,
        // Deposit, and RepositoryCopy to the onSuccess(...) handler of the TransportResponse

        if (dc.repoCopy() == null) {
            RepositoryCopy repoCopy = TransportResponseUpdateFunc.newRepositoryCopy(dc, "", CopyStatus.IN_PROGRESS)
                                                                 .get();
            try {
                passClient.createObject(repoCopy);
                dc.repoCopy(repoCopy);

                dc.deposit().setRepositoryCopy(dc.repoCopy());

                passClient.updateObject(dc.deposit());
            } catch (IOException e) {
                throw new DepositServiceRuntimeException("Failed creating placeholder", e, dc.deposit());
            }
        }

        transportResponse.onSuccess(dc, passClient);
    }

    @Override
    public String toString() {
        return "DepositTask{" + "dc=" + dc + ", passClient=" + passClient + '}';
    }

    /**
     * Critical Repository Interaction functions for updating PASS resources after processing the TransportResponse.
     * <p>
     * Provides a {@code Supplier} that creates new instances of {@link RepositoryCopy}.
     * </p>
     * <p>
     * Provides a {@code Function} that sets {@link Deposit#getDepositStatusRef() Deposit.depositStatusRef} on the
     * supplied {@code Deposit} and creates a {@link RepositoryCopy} with default state.
     * </p>
     * <p>
     * The default state of the {@code RepositoryCopy} created by the {@code Function} is (note that the URI for the
     * external identifier is also used as the access URL):
     * <dl>
     *     <dt>copyStatus</dt>
     *     <dd>IN_PROGRESS</dd>
     *     <dt>repository</dt>
     *     <dd>URI of the {@link DepositWorkerContext#repository()}</dd>
     *     <dt>publication</dt>
     *     <dd>URI of the {@link DepositWorkerContext#submission()} {@code Publication}</dd>
     *     <dt>externalIds</dt>
     *     <dd>the URI provided to this function</dd>
     *     <dt>accessUrl</dt>
     *     <dd>the URI provided to this function</dd>
     * </dl>
     * </p>
     */
    static class TransportResponseUpdateFunc {

        /**
         * Updates resources as a result of a successful deposit to a downstream repository.
         * <ul>
         *     <li>The deposit status reference is set on the Deposit</li>
         *     <li>A RepositoryCopy resource is created according to
         *     {@link #newRepositoryCopy(DepositWorkerContext, String, CopyStatus)}</li>
         *     <li>The external id is set on the newly created RepositoryCopy</li>
         *     <li>The Deposit and RepositoryCopy are updated/persisted in the PASS repository</li>
         * </ul>
         *
         * @param depositStatusRef
         * @param repoCopyExtId
         * @param passClient
         * @param dc
         * @return
         */
        static Function<Deposit, RepositoryCopy> updateResources(String depositStatusRef, String repoCopyExtId,
                                                                 PassClient passClient, DepositWorkerContext dc) {
            return (criDeposit) -> {
                if (depositStatusRef != null) {
                    // TransportResponse was successfully parsed, set the status ref
                    criDeposit.setDepositStatusRef(depositStatusRef);
                }

                // Create a RepositoryCopy, which will record the URL of the Item in DSpace
                RepositoryCopy repoCopy = newRepositoryCopy(dc, repoCopyExtId, CopyStatus.IN_PROGRESS).get();

                try {
                    passClient.createObject(repoCopy);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create repositoryCopy", e);
                }

                criDeposit.setRepositoryCopy(repoCopy);

                dc.repoCopy(repoCopy);

                return repoCopy;
            };
        }

        static Predicate<Deposit> verifyResourceState(DepositWorkerContext dc) {
            return (criDeposit) -> {
                // Update the Deposit Context with the latest Deposit from the repository.  This will be the
                // Deposit resource that is updated by the updateResources(...) method
                dc.deposit(criDeposit);
                return dc.deposit().getDepositStatusRef() != null &&
                       dc.repoCopy() != null;
            };
        }

        /**
         * Creates a new instance of a {@code RepositoryCopy}.  The initial state of the repository copy returned from
         * this method is:
         * <ul>
         *     <li>a copy status set to {@code copyStatus}</li>
         *     <li>a repository URI of the {@code Repository} maintained in the deposit context</li>
         *     <li>a publication URI of the {@code Submission} maintained in the deposit context</li>
         *     <li>a external id set to the {@code extId} (ignored if {@code null} or the empty string)</li>
         *     <li>an access URL set to the URL form of {@code extId}</li>
         * </ul>
         *
         * TODO: review the setting of {@code itemUrl}, as that is Repository-specific.
         *
         * @param dc
         * @param extId
         * @param status
         * @return
         */
        static Supplier<RepositoryCopy> newRepositoryCopy(DepositWorkerContext dc, String extId, CopyStatus status) {
            return () -> {
                RepositoryCopy repoCopy = new RepositoryCopy();

                repoCopy.setCopyStatus(status);
                repoCopy.setRepository(dc.repository());
                repoCopy.setPublication(dc.submission().getPublication());
                if (extId != null && extId.trim().length() > 0) {
                    repoCopy.setExternalIds(Collections.singletonList(extId));
                    try {
                        repoCopy.setAccessUrl(new URI(extId));
                    } catch (URISyntaxException e) {
                        LOG.warn("Error creating an accessUrl from '{}' for a RepositoryCopy associated with {}", extId,
                                 dc.deposit().getId());
                    }
                }

                return repoCopy;
            };
        }
    }

    /**
     * Critical Repository Interaction functions for assembling a package of content and depositing it to a remote
     * repository.
     * <p>
     * Supplies a pre-condition {@code Predicate} that insures the {@code Deposit} resource is not in a <em>terminal
     * </em> state.
     * </p>
     * <p>
     * Supplies a critical {@code Function} that assembles and streams the package to a downstream repository, and sets
     * the {@code Deposit.depositStatus} to {@code SUBMITTED}.  Any exceptions are re-thrown as {@code
     * RuntimeException}.
     * </p>
     * <p>
     * Supplies a post-condition {@code BiPredicate} that insures the deposit was successful, otherwise throws a {@code
     * RuntimeException}
     * </p>
     */
    static class DepositTaskCriFunc {

        /**
         * Answers a {@code Predicate} that checks the {@code Deposit.depositStatus}.  It is meant to determine
         * whether the status of the Deposit is intermediate, or terminal.  If the Deposit
         * status is terminal, the pre-condition should not be met, and the critical function should not be executed.
         *
         * @return the predicate with the precondition
         */
        static Predicate<Deposit> depositPrecondition() {
            return (deposit) -> {
                boolean accept = !DepositStatus.isTerminalStatus(deposit.getDepositStatus());
                if (!accept) {
                    LOG.debug("Precondition failed for {}: Deposit must have an intermediate deposit status",
                              deposit.getId());
                }

                return accept;
            };
        }

        /**
         * Answers a {@code Function} that assembles and deposits a package to a downstream repository.  If the
         * {@code TransportResponse} indicates success, then the Deposit.depositStatus is updated to SUBMITTED.
         * <p>
         * The TransportResponse is returned by this {@code Function} when no exceptions occur closing the package
         * stream.  If there are errors with the downstream repository accepting the package, those will be encapsulated
         * in the returned {@code TransportResponse}.
         * </p>
         * <p>
         * If there is a problem closing the package stream, this {@code Function} will throw a {@code
         * RuntimeException}.
         * </p>
         *
         * @param dc
         * @return
         */
        static Function<Deposit, TransportResponse> performDeposit(DepositWorkerContext dc) {
            return (deposit) -> {
                Packager packager;
                PackageStream packageStream;
                Map<String, String> packagerConfig;

                try {
                    packager = dc.packager();

                    packageStream = packager.getAssembler().assemble(
                        dc.depositSubmission(), packager.getAssemblerOptions());
                    packagerConfig = packager.getConfiguration();
                } catch (Exception e) {
                    throw new RuntimeException("Error resolving a Packager or Packager configuration for " +
                                               dc.deposit().getId(), e);
                }

                Transport transport = getTransport(packager, dc);
                if (dc.isRetryFailedDepositsEnabled() && !transport.checkConnectivity(packagerConfig)) {
                    throw new TransportConnectionException("Transport connectivity failed for deposit " +
                        dc.deposit().getId(), dc.deposit());
                }
                try (TransportSession transportSession = transport.open(packagerConfig)) {
                    TransportResponse tr = transportSession.send(packageStream, packagerConfig);
                    deposit.setDepositStatus(DepositStatus.SUBMITTED);
                    deposit.setDepositStatusRef(packageStream.metadata().packageDepositStatusRef());
                    return tr;
                } catch (Exception e) {
                    throw new RuntimeException("Error closing transport session for deposit " +
                                               dc.deposit().getId() + ": " + e.getMessage(), e);
                }
            };
        }

        static Transport getTransport(Packager packager, DepositWorkerContext dc) {
            return skipDeploymentTestSubmission(dc) ? dc.getDevNullTransport() : packager.getTransport();
        }

        static boolean skipDeploymentTestSubmission(DepositWorkerContext dc) {
            return dc.isSkipDeploymentTestDeposits() && dc.submission().getGrants().stream().anyMatch(
                grant -> grant.getProjectName().contains(DeploymentTestDataService.PASS_E2E_TEST_GRANT)
            );
        }

        /**
         * Answers a {@code BiPredicate} that checks the TransportResponse for success and places the updated Deposit
         * resource in the DepositWorkerContext.  If the TransportResponse indicates an error, the exception is
         * retrieved and re-thrown as a RuntimeException.
         *
         * @return
         */
        static BiPredicate<Deposit, TransportResponse> depositPostcondition(DepositWorkerContext dc) {
            return (deposit, tr) -> {
                if (deposit.getDepositStatus() != DepositStatus.SUBMITTED) {
                    LOG.debug("Postcondition failed for {}: Expected Deposit status '{}' but actual status " +
                              "is '{}'", deposit.getId(), DepositStatus.SUBMITTED, deposit.getDepositStatus());
                    return false;
                }

                if (!tr.success()) {
                    if (tr.error() != null) {
                        final String msg = format("Postcondition failed for %s: Transport of package to " +
                                                  "endpoint failed: %s", deposit.getId(), tr.error().getMessage());
                        throw new RuntimeException(msg, tr.error());
                    } else {
                        throw new RuntimeException(format("Postcondition failed for %s: Transport of package to " +
                                                          "endpoint failed.", deposit.getId()));
                    }
                }

                // Update the DepositWorkerContext with the updated Deposit resource
                dc.deposit(deposit);

                return true;
            };
        }
    }

}
