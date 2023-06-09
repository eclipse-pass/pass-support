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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.pass.client.nihms.cache.GrantIdCache;
import org.eclipse.pass.client.nihms.cache.NihmsDepositIdCache;
import org.eclipse.pass.client.nihms.cache.NihmsRepositoryCopyIdCache;
import org.eclipse.pass.client.nihms.cache.PublicationIdCache;
import org.eclipse.pass.client.nihms.cache.UserPubSubmissionsCache;
import org.eclipse.pass.loader.nihms.util.ConfigUtil;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.PassClientResult;
import org.eclipse.pass.support.client.PassClientSelector;
import org.eclipse.pass.support.client.RSQL;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.Journal;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.Repository;
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

    /**
     * The field name for the ISSN
     */
    public static final String ISSNS_FLD = "issns";

    /**
     * The field name for the submission
     */
    public static final String SUBMISSION_FLD = "submission";

    /**
     * The field name for the repository
     */
    public static final String REPOSITORY_FLD = "repository";

    /**
     * The field name for the award number
     */
    public static final String AWARD_NUMBER_FLD = "awardNumber";

    /**
     * The field name for the publication
     */
    public static final String PUBLICATION_FLD = "publication";

    /**
     * The field name for the submitter
     */
    public static final String SUBMITTER_FLD = "submitter";

    /**
     * The error message when a publication is missing a DOI or PMID
     */
    static final String ERR_CREATE_PUBLICATION =
        "Refusing to create a Publication: it must have either a DOI or a PMID.";

    /**
     * The PassClient used by this service to interact and persist PASS data
     */
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

    /**
     * Default constructor that uses the default PassClient
     */
    public NihmsPassClientService() {
        this(PassClient.newInstance());
    }

    /**
     * Constructor that allows a PassClient to be passed in
     * @param passClient the PassClient used to persist data
     */
    public NihmsPassClientService(PassClient passClient) {
        this.passClient = passClient;
        nihmsRepoId = ConfigUtil.getNihmsRepositoryId();
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
     * @throws IOException if there is an error reading the grant from the PassClient
     */
    public Grant findMostRecentGrantByAwardNumber(String awardNumber) throws IOException {
        if (StringUtils.isEmpty(awardNumber)) {
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
        grants.addAll(grantResult.getObjects());

        //try with no spaces
        String modAwardNum = awardNumber.replaceAll("\\s+", "");
        if (!awardNumber.equals(modAwardNum)) {
            grantSelector.setFilter(RSQL.equals(AWARD_NUMBER_FLD, modAwardNum));
            grantResult = passClient.selectObjects(grantSelector);
            grants.addAll(grantResult.getObjects());
        }

        //if there is a "-##" at the end of the award number, remove it and search again
        if (modAwardNum.contains("-") && modAwardNum.indexOf("-") > 9) {
            modAwardNum = modAwardNum.substring(0, modAwardNum.indexOf("-"));
            grantSelector.setFilter(RSQL.equals(AWARD_NUMBER_FLD, modAwardNum));
            grantResult = passClient.selectObjects(grantSelector);
            grants.addAll(grantResult.getObjects());
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
     * @throws IOException if there is an error reading the publication from the PassClient
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
     * @throws IOException if there is an error reading the publication from the PassClient
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
     * @throws IOException if there is an error reading the repository copy
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

            if (CollectionUtils.isEmpty(repositoryCopies)) {
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

        if (repoCopyId != null) {
            RepositoryCopy repoCopy = passClient.getObject(RepositoryCopy.class, repoCopyId);
            this.nihmsRepoCopyCache.put(pubId, repoCopy.getId());
            return repoCopy;
        }

        return null;
    }

    /**
     * Searches for Submissions matching a specific publication and User Id (Submission.submitter)
     *
     * @param pubId  the publication id
     * @param userId the user id
     * @return the submissions, may be empty but never {@code null}
     * @throws IOException if there is an error reading the submissions
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
     * @param articleId   the article id
     * @param idFieldName the name of the field on the Submission model that will be matched e.g. "pmid" or "doi"
     * @return the publication, or {@code null} if it can't be found
     * @throws IOException if there is an error reading the publication
     */
    private String findPublicationByArticleId(String articleId, String idFieldName) throws IOException {
        if (StringUtils.isEmpty(articleId)) {
            throw new IllegalArgumentException("article ID cannot be empty");
        }
        if (StringUtils.isEmpty(idFieldName)) {
            throw new IllegalArgumentException("idFieldName cannot be empty");
        }

        String pubFilter = RSQL.equals(idFieldName, articleId);
        PassClientSelector<Publication> pubSelector = new PassClientSelector<>(Publication.class);
        pubSelector.setFilter(pubFilter);
        PassClientResult<Publication> pubResult = passClient.selectObjects(pubSelector);
        List<Publication> publications = pubResult.getObjects();

        if (publications.size() == 1) {
            return publications.get(0).getId();
        } else if (publications.size() > 1) {
            throw new IOException("Multiple publications found for " + idFieldName + " " + articleId);
        }
        return null;
    }

    /**
     * Look up Journal ID using ISSN
     *
     * @param issn the issn
     * @return the journal ID for the ISSN, may be {@code null} if not found
     * @throws IOException if there is an error reading the journal
     */
    public String findJournalByIssn(String issn) throws IOException {
        if (StringUtils.isEmpty(issn)) {
            return null;
        }
        String journalFilter = RSQL.hasMember(ISSNS_FLD, issn);
        PassClientSelector<Journal> journalSelector = new PassClientSelector<>(Journal.class);
        journalSelector.setFilter(journalFilter);
        PassClientResult<Journal> journalResult = passClient.selectObjects(journalSelector);
        List<Journal> journals = journalResult.getObjects();
        if (journals.size() == 1) {
            return journals.get(0).getId();
        } else if (journals.size() > 1) {
            throw new IOException("Multiple journals found for " + ISSNS_FLD + " " + issn);
        }
        return null;
    }

    /**
     * Searches for a NIHMS Deposit that matches a SubmissionID
     *
     * @param submissionId the submission id
     * @return the deposit associated with the submission, may be {@code null} if not found
     * @throws IOException if there is an error reading the deposit
     */
    public Deposit findNihmsDepositForSubmission(String submissionId) throws IOException {
        if (submissionId == null) {
            throw new IllegalArgumentException("submissionId cannot be empty");
        }

        //if the depositId is in the cache, retrieve it.
        String depositId = nihmsDepositCache.get(submissionId);

        if (depositId == null) {
            String depositFilter = RSQL.and(
                    RSQL.equals(SUBMISSION_FLD, submissionId),
                    RSQL.equals(REPOSITORY_FLD, nihmsRepoId));
            PassClientSelector<Deposit> depositSelector = new PassClientSelector<>(Deposit.class);
            depositSelector.setFilter(depositFilter);
            PassClientResult<Deposit> subResult = passClient.selectObjects(depositSelector);
            List<Deposit> deposits = subResult.getObjects();

            if (deposits != null && deposits.size() == 1) {
                Deposit deposit = deposits.get(0);
                nihmsDepositCache.put(deposit.getSubmission().getId(), deposit.getId());
                return deposit;
            } else if (!CollectionUtils.isEmpty(deposits)) {
                throw new RuntimeException(
                    String.format("There are multiple Deposits matching submissionId %s and repositoryId %s. "
                                  + "This indicates a data corruption, please check the data and try again.",
                                  submissionId, nihmsRepoId));
            }
        }
        return null;
    }

    /**
     * Retrieve full grant record from database
     *
     * @param grantId the grant id
     * @return Grant if found, or null if not found
     * @throws IOException if there is an error reading the grant
     */
    public Grant readGrant(String grantId) throws IOException {
        if (grantId == null) {
            throw new IllegalArgumentException("grantId cannot be empty");
        }
        return (passClient.getObject(Grant.class, grantId));
    }

    /**
     * Retrieve full publication record from database
     *
     * @param publicationId the publication id
     * @return Publication if found, or null if not found
     * @throws IOException if there is an error reading the publication
     */
    public Publication readPublication(String publicationId) throws IOException {
        if (publicationId == null) {
            throw new IllegalArgumentException("publicationId cannot be empty");
        }
        return (passClient.getObject(Publication.class, publicationId));
    }

    /**
     * Retrieve full Submission record
     *
     * @param submissionId the submission id
     * @return matching submission or null if none found
     * @throws IOException if there is an error reading the submission
     */
    public Submission readSubmission(String submissionId) throws IOException {
        if (submissionId == null) {
            throw new IllegalArgumentException("submissionId cannot be empty");
        }
        return (passClient.getObject(Submission.class, submissionId));
    }

    /**
     * Retrieve full deposit record from database
     *
     * @param depositId the deposit id
     * @return the deposit, or null if not found
     * @throws IOException if there is an error reading the deposit
     */
    public Deposit readDeposit(String depositId) throws IOException {
        if (depositId == null) {
            throw new IllegalArgumentException("depositId cannot be empty");
        }
        return (passClient.getObject(Deposit.class, depositId));
    }

    /**
     * Retrieve repository record from database
     *
     * @param repositoryId the repository id
     * @return the deposit, or null if not found
     * @throws IOException if there is an error reading the Repository
     */
    public Repository readRepository(String repositoryId) throws IOException {
        if (repositoryId == null) {
            throw new IllegalArgumentException("repositoryId cannot be empty");
        }
        return (passClient.getObject(Repository.class, repositoryId));
    }

    /**
     * @param publication the publication
     * @return the uri of the created publication
     * @throws IOException if there is an error creating the publication
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
     * @throws IOException if there is an error creating the submission
     */
    public String createSubmission(Submission submission) throws IOException {
        passClient.createObject(submission);
        LOG.info("New Submission created with ID {}", submission.getId());
        String key = userIdPubIdKey(submission.getSubmitter().getId(), submission.getPublication().getId());
        userPubSubsCache.addToOrCreateEntry(key, submission.getId());
        return submission.getId();
    }

    /**
     * Create a new RepositoryCopy in PASS
     *
     * @param repositoryCopy the repository copy
     * @return the entity ID of the created repository copy
     * @throws IOException if unable to create repository copy
     */
    public String createRepositoryCopy(RepositoryCopy repositoryCopy) throws IOException {
        passClient.createObject(repositoryCopy);
        LOG.info("New RepositoryCopy created with ID {}", repositoryCopy.getId());
        nihmsRepoCopyCache.put(repositoryCopy.getPublication().getId(), repositoryCopy.getId());
        return repositoryCopy.getId();
    }

    /**
     * Update Publication in PASS
     *
     * @param publication the publication
     * @return true if record needed to be updated, false if no update
     * @throws IOException if unable to update publication
     */
    public boolean updatePublication(Publication publication) throws IOException {
        Publication origPublication = passClient.getObject(Publication.class, publication.getId());
        if (!origPublication.equals(publication)) {
            passClient.updateObject(publication);
            LOG.info("Publication with entity ID {} was updated ", publication.getId());
            return true;
        }
        return false;
    }

    /**
     * Update Submission in PASS
     *
     * @param submission the submission
     * @return true if record needed to be updated, false if no update
     * @throws IOException if unable to update submission
     */
    public boolean updateSubmission(Submission submission) throws IOException {
        Submission origSubmission = passClient.getObject(Submission.class, submission.getId());
        if (!origSubmission.equals(submission)) {
            passClient.updateObject(submission);
            //shouldnt be necessary, but just to be sure... make sure this is in cache:
            String key = userIdPubIdKey(submission.getSubmitter().getId(), submission.getPublication().getId());
            userPubSubsCache.addToOrCreateEntry(key, submission.getId());

            LOG.info("Submission with entity ID {} was updated ", submission.getId());
            return true;
        }
        return false;
    }

    /**
     * Update RepositoryCopy in PASS
     *
     * @param repositoryCopy the repository copy
     * @return true if record needed to be updated, false if no update
     * @throws IOException if unable to update repository copy
     */
    public boolean updateRepositoryCopy(RepositoryCopy repositoryCopy) throws IOException {
        RepositoryCopy origRepoCopy = passClient.getObject(RepositoryCopy.class, repositoryCopy.getId());
        if (!origRepoCopy.equals(repositoryCopy)) {
            passClient.updateObject(repositoryCopy);
            LOG.info("RepositoryCopy with entity ID {} was updated ", repositoryCopy.getId());
            return true;
        }
        return false;
    }

    /**
     * Update Deposit in PASS
     *
     * @param deposit the deposit
     * @return true if record needed to be updated, false if no update
     * @throws IOException if unable to update deposit
     */
    public boolean updateDeposit(Deposit deposit) throws IOException {
        Deposit origDeposit = passClient.getObject(Deposit.class, deposit.getId());
        if (!origDeposit.equals(deposit)) {
            passClient.updateObject(deposit);
            LOG.info("Deposit with entity ID {} was updated ", deposit.getId());
            return true;
        }
        return false;
    }

    private static String userIdPubIdKey(String userId, String pubId) {
        return userId.toString() + pubId.toString();
    }

}
