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
package org.eclipse.pass.loader.nihms.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.commons.io.IOUtils;
import org.eclipse.pass.client.PassClient;
import org.eclipse.pass.client.PassClientFactory;
import org.eclipse.pass.client.SubmissionStatusService;
import org.eclipse.pass.client.nihms.NihmsPassClientService;
import org.eclipse.pass.entrez.PmidLookup;
import org.eclipse.pass.entrez.PubMedEntrezRecord;
import org.eclipse.pass.loader.nihms.CompletedPublicationsCache;
import org.eclipse.pass.loader.nihms.util.FileUtil;
import org.eclipse.pass.model.Deposit;
import org.eclipse.pass.model.Grant;
import org.eclipse.pass.model.Grant.AwardStatus;
import org.eclipse.pass.model.PassEntity;
import org.eclipse.pass.model.Publication;
import org.eclipse.pass.model.RepositoryCopy;
import org.eclipse.pass.model.Submission;
import org.joda.time.DateTime;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;

/**
 * @author Karen Hanson
 */
public abstract class NihmsSubmissionEtlITBase {

    //use when need to return reliable record information instead of using entrez api
    @Mock
    protected PmidLookup mockPmidLookup;

    protected Map<URI, Class<? extends PassEntity>> createdUris = new HashMap<URI, Class<? extends PassEntity>>();

    protected static final int RETRIES = 12;

    protected final PassClient client = PassClientFactory.getPassClient();

    protected final SubmissionStatusService statusService = new SubmissionStatusService(client);

    protected final NihmsPassClientService nihmsPassClientService = new NihmsPassClientService(client);

    protected static String path = TransformAndLoadSmokeIT.class.getClassLoader().getResource("data").getPath();

    static {
        if (System.getProperty("pass.fedora.baseurl") == null) {
            System.setProperty("pass.fedora.baseurl", "http://localhost:8080/fcrepo/rest/");
        }
        if (System.getProperty("pass.fedora.user") == null) {
            System.setProperty("pass.fedora.user", "fedoraAdmin");
        }
        if (System.getProperty("pass.fedora.password") == null) {
            System.setProperty("pass.fedora.password", "moo");
        }
        if (System.getProperty("pass.elasticsearch.url") == null) {
            System.setProperty("pass.elasticsearch.url", "http://localhost:9200/pass/");
        }
        if (System.getProperty("nihmsetl.data.dir") == null) {
            System.setProperty("nihmsetl.data.dir", path);
        }
    }

    protected static CompletedPublicationsCache completedPubsCache;

    @Before
    public void startup() {
        String cachepath = FileUtil.getCurrentDirectory() + "/cache/compliant-cache.data";
        System.setProperty("nihmsetl.loader.cachepath", cachepath);
        completedPubsCache = CompletedPublicationsCache.getInstance();
    }

    @After
    public void cleanup() {
        completedPubsCache.clear();

        nihmsPassClientService.clearCache();

        //clean out all data from the following (note Grant URIs added to createdUris the createGrant() method as we
        // don't want to delete pre-loaded data)
        putAllInCreatedUris(client.findAllByAttribute(Submission.class, "@type", "Submission"), Submission.class);
        putAllInCreatedUris(client.findAllByAttribute(Publication.class, "@type", "Publication"), Publication.class);
        putAllInCreatedUris(client.findAllByAttribute(RepositoryCopy.class, "@type", "RepositoryCopy"),
                            RepositoryCopy.class);
        putAllInCreatedUris(client.findAllByAttribute(Deposit.class, "@type", "Deposit"), Deposit.class);

        //need to log fail if this doesn't work as it could mess up re-testing if data isn't cleaned out
        try {
            URI uriCheck = null;

            if (createdUris.size() > 0) {
                for (URI uri : createdUris.keySet()) {
                    client.deleteResource(uri);
                    uriCheck = uri;
                }
                final URI finalUriCheck = uriCheck;
                attempt(RETRIES, () -> {
                    final URI uri = client.findByAttribute(createdUris.get(finalUriCheck), "@id", finalUriCheck);
                    assertEquals(null, uri);
                });
                createdUris.clear();
            }

        } catch (Exception ex) {
            fail("Could not clean up from test, this may interfere with results of other tests");
        }
    }

    private void putAllInCreatedUris(Set<URI> uris, Class<? extends PassEntity> cls) {
        if (uris != null) {
            for (URI uri : uris) {
                createdUris.put(uri, cls);
            }
        }
    }

    protected URI createGrant(String awardNumber, String userId) throws Exception {
        Grant grant = new Grant();
        grant.setAwardNumber(awardNumber);
        grant.setPi(new URI(userId));
        grant.setPrimaryFunder(new URI("funder:id1"));
        grant.setDirectFunder(new URI("funder:id2"));
        grant.setAwardStatus(AwardStatus.ACTIVE);
        List<URI> copis = new ArrayList<URI>();
        copis.add(new URI("user:id"));
        grant.setCoPis(copis);
        grant.setProjectName("test");
        grant.setStartDate(new DateTime());
        grant.setAwardDate(new DateTime());
        URI uri = client.createResource(grant);
        createdUris.put(uri, Grant.class);
        return uri;
    }

    /**
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

    /**
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
