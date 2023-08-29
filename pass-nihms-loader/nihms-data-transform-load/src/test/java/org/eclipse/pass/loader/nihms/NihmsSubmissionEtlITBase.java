/*
 * Copyright 2023 Johns Hopkins University
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
package org.eclipse.pass.loader.nihms;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

import org.apache.commons.io.IOUtils;
import org.eclipse.pass.client.nihms.NihmsPassClientService;
import org.eclipse.pass.entrez.PmidLookup;
import org.eclipse.pass.entrez.PubMedEntrezRecord;
import org.eclipse.pass.loader.nihms.util.ConfigUtil;
import org.eclipse.pass.loader.nihms.util.FileUtil;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.SubmissionStatusService;
import org.eclipse.pass.support.client.model.AwardStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.User;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;

/**
 * @author Karen Hanson
 */
public abstract class NihmsSubmissionEtlITBase {

    //use when need to return reliable record information instead of using entrez api
    @Mock
    protected PmidLookup mockPmidLookup;

    protected Map<PassEntity, Class<? extends PassEntity>> createdEntities = new HashMap<>();

    protected static final int RETRIES = 12;

    protected final PassClient passClient = PassClient.newInstance();

    protected final SubmissionStatusService statusService = new SubmissionStatusService(passClient);

    protected final NihmsPassClientService nihmsPassClientService = new NihmsPassClientService(passClient);

    protected static String path = Objects.requireNonNull(TransformAndLoadSmokeIT.class.getClassLoader()
            .getResource("data")).getPath();

    static {
        if (System.getProperty("pass.core.url") == null) {
            System.setProperty("pass.core.url", "http://localhost:8080");
        }
        if (System.getProperty("pass.core.user") == null) {
            System.setProperty("pass.core.user", "backend");
        }
        if (System.getProperty("pass.core.password") == null) {
            System.setProperty("pass.core.password", "backend");
        }
        if (System.getProperty("nihmsetl.data.dir") == null) {
            System.setProperty("nihmsetl.data.dir", path);
        }
    }

    protected static CompletedPublicationsCache completedPubsCache;

    @BeforeEach
    public void startup() throws IOException {
        String cachepath = FileUtil.getCurrentDirectory() + "/cache/compliant-cache.data";
        System.setProperty("nihmsetl.loader.cachepath", cachepath);
        completedPubsCache = CompletedPublicationsCache.getInstance();
        initiateNihmsRepoCopy();
    }

    @AfterEach
    public void cleanup() throws IOException {
        completedPubsCache.clear();
        nihmsPassClientService.clearCache();

        /*
            Clean out all data from the following (note Grant IDs added in the createGrant() method as we
            don't want to delete preloaded data). Need to check that the objects were actually deleted
            otherwise this will cause issues with the next test run.
        */
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        subSelector.setFilter(RSQL.notEquals("id", "-1"));
        for (Submission submission : passClient.selectObjects(subSelector).getObjects()) {
            passClient.deleteObject(submission);
            assertNull(passClient.getObject(Submission.class, submission.getId()));
        }

        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        repoCopySelector.setFilter(RSQL.notEquals("id", "-1"));
        for (RepositoryCopy repoCopy : passClient.selectObjects(repoCopySelector).getObjects()) {
            passClient.deleteObject(repoCopy);
            assertNull(passClient.getObject(Submission.class, repoCopy.getId()));
        }

        PassClientSelector<Deposit> depoSelector = new PassClientSelector<>(Deposit.class);
        depoSelector.setFilter(RSQL.equals("id", "-1"));
        for (Deposit deposit : passClient.selectObjects(depoSelector).getObjects()) {
            passClient.deleteObject(deposit);
            assertNull(passClient.getObject(Submission.class, deposit.getId()));
        }

        PassClientSelector<Repository> repoSelector = new PassClientSelector<>(Repository.class);
        repoSelector.setFilter(RSQL.equals("id", "-1"));
        for (Repository repo : passClient.selectObjects(repoSelector).getObjects()) {
            passClient.deleteObject(repo);
            assertNull(passClient.getObject(Submission.class, repo.getId()));
        }

        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        pubSelector.setFilter(RSQL.notEquals("id", "-1"));
        for (Publication publication : passClient.selectObjects(pubSelector).getObjects()) {
            passClient.deleteObject(publication);
            assertNull(passClient.getObject(Submission.class, publication.getId()));
        }
    }

    protected String createGrant(String awardNumber, User user) throws Exception {
        Funder primaryFunder = new Funder();
        Funder directFunder = new Funder();
        passClient.createObject(primaryFunder);
        passClient.createObject(directFunder);
        User coPi = new User();
        passClient.createObject(coPi);
        Grant grant = new Grant();
        grant.setAwardNumber(awardNumber);
        grant.setPi(user);
        grant.setPrimaryFunder(primaryFunder);
        grant.setDirectFunder(directFunder);
        grant.setAwardStatus(AwardStatus.ACTIVE);
        List<User> copis = new ArrayList<>();
        copis.add(coPi);
        grant.setCoPis(copis);
        grant.setProjectName("test");
        grant.setStartDate(ZonedDateTime.now());
        grant.setAwardDate(ZonedDateTime.now());
        passClient.createObject(grant);
        createdEntities.put(grant, Grant.class);
        return grant.getId();
    }

    /*
     * Try invoking a runnable until it succeeds.
     *
     * @param times  The number of times to run
     * @param thingy The runnable.
     */
    void attempt(final int times, final Runnable thingy) {
        attempt(times, () -> {
            thingy.run();
            return null;
        });
    }

    /*
     * Try invoking a callable until it succeeds.
     *
     * @param times Number of times to try
     * @param it    the thing to call.
     * @return the result from the callable, when successful.
     */
    <T> T attempt(final int times, final Callable<T> it) {

        Throwable caught = null;

        for (int tries = 0; tries < times; tries++) {
            try {
                return it.call();
            } catch (final Throwable e) {
                caught = e;
                try {
                    Thread.sleep(3000);
                } catch (final InterruptedException i) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        throw new RuntimeException("Failed executing task", caught);
    }

    protected void setMockPMRecord(String pmid) throws IOException {
        String json = IOUtils.toString(getClass().getClassLoader().getResourceAsStream("pmidrecord.json"));
        JSONObject rootObj = new JSONObject(json);
        PubMedEntrezRecord pmr = new PubMedEntrezRecord(rootObj);
        when(mockPmidLookup.retrievePubMedRecord(eq(pmid))).thenReturn(pmr);
    }

    protected void initiateNihmsRepoCopy() throws IOException {
        Repository nihmsRepo = new Repository();
        nihmsRepo.setName("NIHMS");
        nihmsRepo.setRepositoryKey("nihms");
        nihmsRepo.setId(ConfigUtil.getNihmsRepositoryId());
        passClient.createObject(nihmsRepo);
    }

}
