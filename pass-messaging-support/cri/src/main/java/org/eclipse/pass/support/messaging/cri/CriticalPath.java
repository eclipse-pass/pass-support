/*
 *
 *  * Copyright 2018 Johns Hopkins University
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.eclipse.pass.support.messaging.cri;

import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.PassEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Provides the guarantees set by {@link CriticalRepositoryInteraction}, and boilerplate for interacting with, and
 * modifying the state of, repository resources.
 * <p>
 * Note that performCritical may modify the state of a resource as specified by a parameter.
 * If the the critical method modifies state of the resource, the resource
 * will be persisted in the repository.  Likewise, if the critical method does <em>not</em> modify resource state, it
 * will <em>not</em> attempt to persist the resource (since there are no changes to persist).
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class CriticalPath implements CriticalRepositoryInteraction {
    private static final Logger LOG = LoggerFactory.getLogger(CriticalPath.class);

    private PassClient passClient;

    public CriticalPath(PassClient passClient) {
        this.passClient = passClient;
    }

    /**
     * Executes in order:
     * <ol>
     *     <li>Obtain a lock over the interned, string form of the {@code uri}, insuring no interference from other
     *         threads executing in this JVM</li>
     *     <li>Read the {@code PassEntity} identified by {@code uri} from the repository, short-circuiting the
     *         interaction by returning a {@code CriticalResult} if an {@code Exception} is thrown</li>
     *     <li>Apply the pre-condition {@code Predicate}, short-circuiting the interaction by returning a
     *         {@code CriticalResult} if the condition fails</li>
     *     <li>Perform the {@code critical} interaction, short-circuiting the interaction by returning a
     *         {@code CriticalResult} if an {@code Exception} is thrown</li>
     *     <li>If the {@code critical} interaction modifies the resource, update the resource in the repository.  Read
     *         the resource from the repository (even if no update is performed). If any other
     *         {@code Exception} is thrown, the interaction is short-circuited, and a {@code CriticalResult}
     *         returned.</li>
     *     <li>Apply the post-condition {@code Predicate} and returns {@code CriticalResult}</li>
     * </ol>
     * Exception handling
     * <p>
     * All code that executes within this method is executed within {@code try/catch} blocks.  Each lambda passed to
     * this method executes within a {@code try/catch}, and all supporting code within this method executes within
     * {@code try/catch} blocks.  If an {@code Exception} is thrown, it will be caught, placed in the {@code
     * CriticalResult}, and this method immediately returns. The caller is responsible for evaluating the {@code
     * CriticalResult} and determining success or failure of this method.
     * </p>
     *
     * @param id the identifier of the {@code PassEntity} which is the subject of the {@code critical} pathv
     * @param clazz the concrete {@code Class} of the {@code PassEntity} represented by {@code uri}
     * @param precondition precondition that must evaluate to {@code true} for the {@code critical} path to execute
     * @param postcondition postcondition that must evaluate to {@code true} for the {@code CriticalResult} to be
     *                      considered successful
     * @param critical the critical interaction with the repository, which may return a result of type {@code R}
     * @param updatesEntity whether or not the critical function updates the entity
     * @param <T> the type of {@code PassEntity}
     * @param <R> the type of {@code Object} returned by {@code critical}
     * @return a {@code CriticalResult} encapsulating the {@code PassEntity}, the return from the {@code critical} path,
     *         any exception thrown, and the overall success as determined by the post-condition
     */
    @Override
    public <R, T extends PassEntity> CriticalResult<R, T> performCritical(String id, Class<T> clazz,
                                                                          Predicate<T> precondition,
                                                                          Predicate<T> postcondition,
                                                                          Function<T, R> critical,
                                                                          boolean updatesEntity) {
        return performCritical(id, clazz, precondition, (t, r) -> postcondition.test(t), critical, updatesEntity);
    }

    /**
     * Executes in order:
     * <ol>
     *     <li>Obtain a lock over the interned, string form of the {@code uri}, insuring no interference from other
     *         threads executing in this JVM</li>
     *     <li>Read the {@code PassEntity} identified by {@code uri} from the repository, short-circuiting the
     *         interaction by returning a {@code CriticalResult} if an {@code Exception} is thrown</li>
     *     <li>Apply the pre-condition {@code Predicate}, short-circuiting the interaction by returning a
     *         {@code CriticalResult} if the condition fails</li>
     *     <li>Perform the {@code critical} interaction, short-circuiting the interaction by returning a
     *         {@code CriticalResult} if an {@code Exception} is thrown</li>
     *     <li>If the {@code critical} interaction modifies the resource, update the resource in the repository.  Read
     *         the resource from the repository (even if no update is performed). If any other
     *         {@code Exception} is thrown, the interaction is short-circuited, and a {@code CriticalResult}
     *         returned.</li>
     *     <li>Apply the post-condition {@code BiPredicate} and returns {@code CriticalResult}</li>
     * </ol>
     * Exception handling
     * <p>
     * All code that executes within this method is executed within {@code try/catch} blocks.  Each lambda passed to
     * this method executes within a {@code try/catch}, and all supporting code within this method executes within
     * {@code try/catch} blocks.  If an {@code Exception} is thrown, it will be caught, placed in the {@code
     * CriticalResult}, and this method immediately returns. The caller is responsible for evaluating the {@code
     * CriticalResult} and determining success or failure of this method.
     * </p>
     *
     * @param id the identifier of the {@code PassEntity} which is the subject of the {@code critical} pathv
     * @param clazz the concrete {@code Class} of the {@code PassEntity} represented by {@code uri}
     * @param precondition precondition that must evaluate to {@code true} for the {@code critical} path to execute
     * @param postcondition postcondition that must evaluate to {@code true} for the {@code CriticalResult} to be
     *                      considered successful
     * @param critical the critical interaction with the repository, which may return a result of type {@code R}
     * @param updatesEntity whether or not the critical function updates the entity
     * @param <T> the type of {@code PassEntity}
     * @param <R> the type of {@code Object} returned by {@code critical}
     * @return a {@code CriticalResult} encapsulating the {@code PassEntity}, the return from the {@code critical} path,
     *         any exception thrown, and the overall success as determined by the post-condition
     */
    @Override
    public <R, T extends PassEntity> CriticalResult<R, T> performCritical(String id, Class<T> clazz,
                                                                          Predicate<T> precondition,
                                                                          BiPredicate<T, R> postcondition,
                                                                          Function<T, R> critical,
                                                                          boolean updatesEntity) {

        CriticalResult<R, T> cr = null;

        // 1. Obtain a lock over the repository resource URI, then enter the critical section
        String lock = (clazz.getName() + id).intern();

        synchronized (lock) {

            // 2. Read the resource from the repository

            T resource = null;
            try {

                resource = passClient.getObject(clazz, id);
            } catch (Exception e) {
                return new CriticalResult<>(null, null,false, e);
            }

            // 3. Verify that the state of the resource is what is expected from the caller.  If not, return indicating
            //    failure, with a copy of the resource.

            try {
                if (!precondition.test(resource)) {
                    LOG.debug("Precondition for applying the critical path on resource {} failed.", resource.getId());
                    return new CriticalResult<>(null, resource, false);
                }
            } catch (Exception e) {
                return new CriticalResult<>(null, resource, false, e);
            }

            // 4.  Apply the critical update to the resource.

            R updateResult = null;
            try {
                updateResult = critical.apply(resource);
            } catch (Exception e) {
                return new CriticalResult<>(updateResult, resource, false, e);
            }

            // 5. Attempt to update the resource, knowing that another process may have modified the state of the
            //    resource in the interim.  Any conflicts are handled by the ConflictHandler
            try {
                // Avoid updating the resource if it has not been changed by the critical Function.

                if (updatesEntity) {
                    passClient.updateObject(resource);
                }

                resource = passClient.getObject(resource);
            } catch (Exception e) {
                return new CriticalResult<>(updateResult, resource, false, e);
            }

            // 6. Verify the expected end state, and create the result.  Note that the success or failure of a
            //    critical path rests entirely on the verification of this final state: the caller wants to know:
            //    "Did the update I perform result in the state I expected?"

            try {
                if (!postcondition.test(resource, updateResult)) {
                    LOG.debug("Postcondition over resource {} and result {} failed.", resource.getId(), updateResult);
                    return new CriticalResult<>(updateResult, resource, false);
                }
            } catch (Exception e) {
                return new CriticalResult<>(updateResult, resource, false, e);
            }

            cr = new CriticalResult<>(updateResult, resource, true);
        }

        return cr;
    }
}
