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
import static org.junit.jupiter.api.Assertions.fail;
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
    public void startup() {
        String cachepath = FileUtil.getCurrentDirectory() + "/cache/compliant-cache.data";
        System.setProperty("nihmsetl.loader.cachepath", cachepath);
        completedPubsCache = CompletedPublicationsCache.getInstance();
    }

    @AfterEach
    public void cleanup() throws IOException {
        completedPubsCache.clear();

        nihmsPassClientService.clearCache();

        //clean out all data from the following (note Grant URIs added to createdUris the createGrant() method as we
        // don't want to delete pre-loaded data)
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        subSelector.setFilter(RSQL.equals("@type", "Submission"));
        createdEntities.put((PassEntity) passClient.selectObjects(subSelector).getObjects(), Submission.class);

        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        pubSelector.setFilter(RSQL.equals("@type", "Publication"));
        createdEntities.put((PassEntity) passClient.selectObjects(pubSelector).getObjects(), Publication.class);

        PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
        repoCopySelector.setFilter(RSQL.equals("@type", "RepositoryCopy"));
        createdEntities.put((PassEntity) passClient.selectObjects(repoCopySelector).getObjects(), RepositoryCopy.class);

        PassClientSelector<Deposit> depoSelector = new PassClientSelector<>(Deposit.class);
        depoSelector.setFilter(RSQL.equals("@type", "Deposit"));
        createdEntities.put((PassEntity) passClient.selectObjects(depoSelector).getObjects(), RepositoryCopy.class);

        //need to log fail if this doesn't work as it could mess up re-testing if data isn't cleaned out
        try {
            String idCheck = null;

            if (createdEntities.size() > 0) {
                for (PassEntity entity : createdEntities.keySet()) {
                    idCheck = entity.getId();
                    passClient.deleteObject(entity);
                }
                final String finalIdCheck = idCheck;
                attempt(RETRIES, ()  -> {
                    try {
                        assertNull(passClient.getObject(PassEntity.class, finalIdCheck),
                                "Entity " + finalIdCheck + " was not deleted");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                createdEntities.clear();
            }

        } catch (Exception ex) {
            fail("Could not clean up from test, this may interfere with results of other tests");
        }
    }

    protected String createGrant(String awardNumber, String userId) throws Exception {
        Funder primaryFunder = new Funder("funder:id1");
        Funder directFunder = new Funder("funder:id2");
        User user = new User(userId);
        User coPi = new User("user:id");
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
                    System.out.println("... waiting for index to update");
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

}
