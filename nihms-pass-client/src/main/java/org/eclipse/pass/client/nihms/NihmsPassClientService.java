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
package org.eclipse.pass.client.nihms;

import static org.eclipse.pass.loader.nihms.util.ProcessingUtil.nullOrEmpty;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.pass.client.nihms.cache.GrantIdCache;
import org.eclipse.pass.client.nihms.cache.NihmsDepositIdCache;
import org.eclipse.pass.client.nihms.cache.NihmsRepositoryCopyIdCache;
import org.eclipse.pass.client.nihms.cache.PublicationIdCache;
import org.eclipse.pass.client.nihms.cache.UserPubSubmissionsCache;
import org.eclipse.pass.loader.nihms.util.ConfigUtil;
import org.eclipse.pass.support.client.PassClientResult;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NIHMS PASS client service deals with interactions with the data via the PASS client and controls local data caches
 *
 * @author Karen Hanson
 */
public class NihmsPassClientService {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsPassClientService.class);

    public static final String ISSNS_FLD = "issns";

    public static final String SUBMISSION_FLD = "submission";

    public static final String REPOSITORY_FLD = "repository";

    public static final String AWARD_NUMBER_FLD = "awardNumber";

    public static final String PUBLICATION_FLD = "publication";

    public static final String SUBMITTER_FLD = "submitter";

    static final String ERR_CREATE_PUBLICATION =
        "Refusing to create a Publication: it must have either a DOI or a PMID.";

    private final PassClient passClient;

    /**
     * Local cache of publications, lookup by PMID
     */
    private PublicationIdCache publicationCache;

    /**
     * Local cache of repositoryCopies for NIHMS repo only, lookup by publicationId
     */
    private NihmsRepositoryCopyIdCache nihmsRepoCopyCache;

    /**
     * Local cache of Grants, lookup by awardNumber
     */
    private GrantIdCache grantCache;

    /**
     * Local cache of NIHMS Deposits, lookup by Submission URI
     */
    private NihmsDepositIdCache nihmsDepositCache;

    /**
     * Local cache of the Submissions associated with a User and Publication combination
     * Look up using UserId and PublicationId concatenated
     */
    private UserPubSubmissionsCache userPubSubsCache;

    /**
     * Store NIHMS REPO ID setting
     */
    private final String nihmsRepoId;

    public NihmsPassClientService() {
        this(PassClient.newInstance());
    }

    public NihmsPassClientService(PassClient passClient) {
        this.passClient = passClient;
        nihmsRepoId = ConfigUtil.getNihmsRepositoryUri();
        initCaches();
    }

    private void initCaches() {
        publicationCache = PublicationIdCache.getInstance();
        nihmsRepoCopyCache = NihmsRepositoryCopyIdCache.getInstance();
        grantCache = GrantIdCache.getInstance();
        nihmsDepositCache = NihmsDepositIdCache.getInstance();
        userPubSubsCache = UserPubSubmissionsCache.getInstance();
    }

    /**
     * Remove all data from cache
     */
    public void clearCache() {
        this.publicationCache.clear();
        this.nihmsRepoCopyCache.clear();
        this.grantCache.clear();
        this.nihmsDepositCache.clear();
        this.userPubSubsCache.clear();
    }

    /**
     * Searches for Grant record using awardNumber. Tries this first using the awardNumber as passed in,
     * then again without spaces.
     *
     * @param awardNumber the award number
     * @return the grant, or {@code null} if not found
     */
    public Grant findMostRecentGrantByAwardNumber(String awardNumber) throws IOException {
        if (nullOrEmpty(awardNumber)) {
            throw new IllegalArgumentException("awardNumber cannot be empty");
        }

        //if the awardNumber is in the cache, retrieve URI.
        String grantId = grantCache.get(awardNumber);
        if (grantId != null) {
            return readGrant(grantId);
        }

        // if we are here, there was nothing cached and we need to figure out which grant to return
        List<Grant> grants = new ArrayList<>();
        PassClientSelector<Grant> grantSelector = new PassClientSelector<Grant>(Grant.class);
        grantSelector.setFilter(RSQL.equals(AWARD_NUMBER_FLD, awardNumber));
        PassClientResult<Grant> grantResult = passClient.selectObjects(grantSelector);
        grants = grantResult.getObjects();

        //try with no spaces
        String modAwardNum = awardNumber.replaceAll("\\s+", "");
        if (!awardNumber.equals(modAwardNum)) {
            grantSelector.setFilter(RSQL.equals(AWARD_NUMBER_FLD, modAwardNum));
            grantResult = passClient.selectObjects(grantSelector);
            grants = grantResult.getObjects();
        }

        //if there is a "-##" at the end of the award number, remove it and search again
        if (modAwardNum.contains("-") && modAwardNum.indexOf("-") > 9) {
            modAwardNum = modAwardNum.substring(0, modAwardNum.indexOf("-"));
            grantSelector.setFilter(RSQL.equals(AWARD_NUMBER_FLD, modAwardNum));
            grantResult = passClient.selectObjects(grantSelector);
            grants = grantResult.getObjects();
        }

        if (grants.size() == 1) {
            return grants.get(0);
        } else if (grants.size() > 0) {
            Grant mostRecentGrant = Collections.max(grants, Comparator.comparing(Grant::getStartDate));
            grantCache.put(awardNumber, mostRecentGrant.getId());
            return mostRecentGrant;
        }

        return null;
    }

    /**
     * Looks up publication using PMID, since this is the most reliable field to match. Checks publication
     * cache first, then checks index
     *
     * @param pmid the pub med id
     * @return the publication, or {@code null} if it can't be found
     */
    public Publication findPublicationByPmid(String pmid) throws IOException {
        if (pmid == null) {
            throw new RuntimeException("PMID cannot be null when searching for existing Publication.");
        }

        //if the pmid/publicationId pair is in the cache, retrieve it.
        String publicationId = publicationCache.get(pmid);

        if (publicationId == null) {
            publicationId = findPublicationByArticleId(pmid, "pmid");
        }

        if (publicationId != null) {
            Publication publication = readPublication(publicationId);
            publicationCache.put(pmid, publicationId);
            return publication;
        }

        return null;
    }

    /**
     * Looks up publication using DOI, call should include pmid so that it can check publication
     * cache first, then checks index for DOI
     *
     * @param doi  the digital object identifier
     * @param pmid the pub med id
     * @return the publication, or {@code null} if it can't be found
     */
    public Publication findPublicationByDoi(String doi, String pmid) throws IOException {
        if (pmid == null) {
            throw new RuntimeException("PMID cannot be null when searching for existing Publication.");
        }

        //if the pmid/publicationId pair is in the cache, retrieve it.
        String publicationId = publicationCache.get(pmid);

        if (doi != null) {
            publicationId = findPublicationByArticleId(doi, "doi");
        }

        if (publicationId != null) {
            Publication publication = readPublication(publicationId);
            publicationCache.put(pmid, publicationId);
            return publication;
        }

        return null;
    }

    /**
     * Find NIHMS RepositoryCopy record for a publicationId
     *
     * @param pubId the publication id
     * @return the repository copy, or {@code null} if it can't be found
     */
    public RepositoryCopy findNihmsRepositoryCopyForPubId(String pubId) throws IOException {
        if (pubId == null) {
            throw new RuntimeException("publicationId cannot be null when searching for existing RepositoryCopy.");
        }

        //if the publicationId can be matched in the cache of NIHMS Repository Copy ID mappings, retrieve it
        String repoCopyId = nihmsRepoCopyCache.get(pubId);

        if (repoCopyId == null) {
            String repoCopyFilter = RSQL.and(
                    RSQL.equals(PUBLICATION_FLD, pubId),
                    RSQL.equals(REPOSITORY_FLD, nihmsRepoId));
            PassClientSelector<RepositoryCopy> repoCopySelector = new PassClientSelector<>(RepositoryCopy.class);
            repoCopySelector.setFilter(repoCopyFilter);
            PassClientResult<RepositoryCopy> repoCopyResult = passClient.selectObjects(repoCopySelector);
            List<RepositoryCopy> repositoryCopies = repoCopyResult.getObjects();

            if (nullOrEmpty(repositoryCopies)) {
                return null;
            } else if (repositoryCopies.size() == 1) {
                RepositoryCopy repoCopy = repositoryCopies.get(0);
                this.nihmsRepoCopyCache.put(pubId, repoCopy.getId());
                return repoCopy;
            } else if (repositoryCopies.size() > 1) {
                throw new RuntimeException(
                    String.format("There are multiple repository copies matching RepositoryId %s and PublicationId %s. "
                                  + "This indicates a data corruption, please check the data and try again.", pubId,
                                  nihmsRepoId));
            }
        }
        return null;
    }

    /**
     * Searches for Submissions matching a specific publication and User Id (Submission.submitter)
     *
     * @param pubId  the publication id
     * @param userId the user id
     * @return the submissions, may be empty but never {@code null}
     */
    public List<Submission> findSubmissionsByPublicationAndUserId(String pubId, String userId) throws IOException {
        if (pubId == null) {
            throw new RuntimeException("publicationId cannot be null when searching for existing Submissions");
        }
        if (userId == null) {
            throw new RuntimeException("userId cannot be null when searching for existing Submissions");
        }

        String userIdPubIdKey = userIdPubIdKey(userId, pubId);

        Set<String> subIds = new HashSet<>();
        String subFilter = RSQL.and(
                RSQL.equals(PUBLICATION_FLD, pubId),
                RSQL.equals(SUBMITTER_FLD, userId));
        PassClientSelector<Submission> subSelector = new PassClientSelector<>(Submission.class);
        subSelector.setFilter(subFilter);
        PassClientResult<Submission> subResult = passClient.selectObjects(subSelector);
        List<Submission> submissions = subResult.getObjects();

        for (Submission submission : submissions) {
            subIds.add(submission.getId());
        }

        //in addition we will check the cache to see if it has any other Submissions that the indexer didn't detect
        Set<String> cachedIds = userPubSubsCache.get(userIdPubIdKey);

        if (cachedIds != null) {
            //merge the two sets of URIs to make sure we have all of them
            subIds.addAll(cachedIds);
            //add the cached ids to the list of submissions to be returned
            for (String id : cachedIds) {
                submissions.add(passClient.getObject(Submission.class, id));
            }
        }
        //update the cache with all of the submission IDs
        userPubSubsCache.put(userIdPubIdKey, subIds);

        return submissions;
    }

    /**
     * Searches for Publication record using articleIds. This detects whether we are dealing
     * with a record that was already looked at previously.
     *
     * @param articleId   the artical id
     * @param idFieldName the name of the field on the Submission model that will be matched e.g. "pmid" or "doi"
     * @return the publication, or {@code null} if it can't be found
     */
    private String findPublicationByArticleId(String articleId, String idFieldName) {
        if (nullOrEmpty(articleId)) {
            throw new IllegalArgumentException("article ID cannot be empty");
        }
        if (nullOrEmpty(idFieldName)) {
            throw new IllegalArgumentException("idFieldName cannot be empty");
        }
        URI match = passClient.findByAttribute(Publication.class, idFieldName, articleId);
        return match;
    }

    /**
     * Look up Journal ID using ISSN
     *
     * @param issn the issn
     * @return the journal for the ISSN, may be {@code null} if not found
     */
    public String findJournalByIssn(String issn) {
        if (nullOrEmpty(issn)) {
            return null;
        }
        return passClient.findByAttribute(Journal.class, ISSNS_FLD, issn);
    }

    /**
     * Searches for a NIHMS Deposit that matches a SubmissionID
     *
     * @param submissionId the submission id
     * @return the deposit associated with the submission, may be {@code null} if not found
     */
    public Deposit findNihmsDepositForSubmission(String submissionId) {
        if (submissionId == null) {
            throw new IllegalArgumentException("submissionId cannot be empty");
        }

        //if the depositId is in the cache, retrieve it.
        String depositId = nihmsDepositCache.get(submissionId);

        if (depositId == null) {
            //search for deposit
            Map<String, Object> attribs = new HashMap<String, Object>();
            attribs.put(SUBMISSION_FLD, submissionId);
            attribs.put(REPOSITORY_FLD, nihmsRepoId);
            Set<URI> matches = passClient.findAllByAttributes(Deposit.class, attribs);
            if (matches != null && matches.size() == 1) {
                depositId = matches.iterator().next();
            } else if (!nullOrEmpty(matches)) {
                throw new RuntimeException(
                    String.format("There are multiple Deposits matching submissionId %s and repositoryId %s. "
                                  + "This indicates a data corruption, please check the data and try again.",
                                  submissionId, nihmsRepoId));
            }
        }

        if (depositId != null) {
            Deposit deposit = passClient.readResource(depositId, Deposit.class);
            this.nihmsDepositCache.put(deposit.getSubmission(), deposit.getId());
            return deposit;
        }

        return null;
    }

    /**
     * Retrieve full grant record from database
     *
     * @param grantId the grant id
     * @return Grant if found, or null if not found
     */
    private Grant readGrant(String grantId) throws IOException {
        if (grantId == null) {
            throw new IllegalArgumentException("grantId cannot be empty");
        }
        Object grantObj = passClient.getObject(Grant.class, grantId);
        return (grantObj != null ? (Grant) grantObj : null);
    }

    /**
     * Retrieve full publication record from database
     *
     * @param publicationId the publication id
     * @return Publication if found, or null if not found
     */
    public Publication readPublication(String publicationId) throws IOException {
        if (publicationId == null) {
            throw new IllegalArgumentException("publicationId cannot be empty");
        }
        Object publicationObj = passClient.getObject(Publication.class, publicationId);
        return (publicationObj != null ? (Publication) publicationObj : null);
    }

    /**
     * Retrieve full Submission record
     *
     * @param submissionId the submission id
     * @return matching submission or null if none found
     */
    public Submission readSubmission(String submissionId) throws IOException {
        if (submissionId == null) {
            throw new IllegalArgumentException("submissionId cannot be empty");
        }
        Object submissionObj = passClient.getObject(Submission.class, submissionId);
        return (submissionObj != null ? (Submission) submissionObj : null);
    }

    /**
     * Retrieve full deposit record from database
     *
     * @param depositId the deposit id
     * @return the deposit, or null if not found
     */
    public Deposit readDeposit(String depositId) throws IOException {
        if (depositId == null) {
            throw new IllegalArgumentException("depositId cannot be empty");
        }
        Object depositObj = passClient.getObject(Deposit.class, depositId);
        return (depositObj != null ? (Deposit) depositObj : null);
    }

    /**
     * @param publication the publication
     * @return the uri of the created publication
     */
    public String createPublication(Publication publication) throws IOException {

        // Publication resource must have either a PMID or a DOI
        if (publication.getPmid() == null && publication.getDoi() == null) {
            throw new RuntimeException(ERR_CREATE_PUBLICATION);
        }

        //URI publicationId = passClient.createResource(publication);
        passClient.createObject(publication);
        LOG.info("New Publication created with ID {}", publication.getId());
        //add to local cache for faster lookup
        publicationCache.put(publication.getPmid(), publication.getId());
        return publication.getId();
    }

    /**
     * @param submission the submission
     * @return the entity ID of the created submission
     */
    public String createSubmission(Submission submission) throws IOException {
        passClient.createObject(submission);
        LOG.info("New Submission created with ID {}", submission.getId());
        String key = userIdPubIdKey(submission.getSubmitter(), submission.getPublication());
        userPubSubsCache.addToOrCreateEntry(key, submission.getId());
        return submission.getId();
    }

    /**
     * @param repositoryCopy the repository copy
     * @return the entity ID of the created repository copy
     */
    public String createRepositoryCopy(RepositoryCopy repositoryCopy) throws IOException {
        passClient.createObject(repositoryCopy);
        LOG.info("New RepositoryCopy created with ID {}", repositoryCopy.getId());
        nihmsRepoCopyCache.put(repositoryCopy.getPublication(), repositoryCopy.getId());
        return repositoryCopy.getId();
    }

    /**
     * @param publication the publication
     * @return true if record needed to be updated, false if no update
     */
    public boolean updatePublication(Publication publication) {
        Publication origPublication = (Publication) passClient.readResource(publication.getId(), Publication.class);
        if (!origPublication.equals(publication)) {
            passClient.updateResource(publication);
            LOG.info("Publication with entity ID {} was updated ", publication.getId());
            return true;
        }
        return false;
    }

    /**
     * @param submission the submission
     * @return true if record needed to be updated, false if no update
     */
    public boolean updateSubmission(Submission submission) {
        Submission origSubmission = (Submission) passClient.readResource(submission.getId(), Submission.class);
        if (!origSubmission.equals(submission)) {
            passClient.updateResource(submission);

            //shouldnt be necessary, but just to be sure... make sure this is in cache:
            String key = userIdPubIdKey(submission.getSubmitter(), submission.getPublication());
            userPubSubsCache.addToOrCreateEntry(key, submission.getId());

            LOG.info("Submission with entity ID {} was updated ", submission.getId());
            return true;
        }
        return false;
    }

    /**
     * @param repositoryCopy the repository copy
     * @return true if record needed to be updated, false if no update
     */
    public boolean updateRepositoryCopy(RepositoryCopy repositoryCopy) {
        RepositoryCopy origRepoCopy = (RepositoryCopy) passClient.readResource(repositoryCopy.getId(),
                                                                           RepositoryCopy.class);
        if (!origRepoCopy.equals(repositoryCopy)) {
            passClient.updateResource(repositoryCopy);
            LOG.info("RepositoryCopy with entity ID {} was updated ", repositoryCopy.getId());
            return true;
        }
        return false;
    }

    /**
     * @param deposit the deposit
     * @return true if record needed to be updated, false if no update
     */
    public boolean updateDeposit(Deposit deposit) {
        Deposit origDeposit = (Deposit) passClient.readResource(deposit.getId(), Deposit.class);
        if (!origDeposit.equals(deposit)) {
            passClient.updateResource(deposit);
            LOG.info("Deposit with entity ID {} was updated ", deposit.getId());
            return true;
        }
        return false;
    }

    private static String userIdPubIdKey(String userId, String pubId) {
        return userId.toString() + pubId.toString();
    }

}
