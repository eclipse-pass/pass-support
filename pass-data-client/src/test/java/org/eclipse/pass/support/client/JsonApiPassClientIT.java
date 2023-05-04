package org.eclipse.pass.support.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.pass.support.client.model.AggregatedDepositStatus;
import org.eclipse.pass.support.client.model.AwardStatus;
import org.eclipse.pass.support.client.model.Contributor;
import org.eclipse.pass.support.client.model.ContributorRole;
import org.eclipse.pass.support.client.model.CopyStatus;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.DepositStatus;
import org.eclipse.pass.support.client.model.EventType;
import org.eclipse.pass.support.client.model.File;
import org.eclipse.pass.support.client.model.FileRole;
import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.IntegrationType;
import org.eclipse.pass.support.client.model.Journal;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.PerformerRole;
import org.eclipse.pass.support.client.model.PmcParticipation;
import org.eclipse.pass.support.client.model.Policy;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.Publisher;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Source;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionEvent;
import org.eclipse.pass.support.client.model.SubmissionStatus;
import org.eclipse.pass.support.client.model.User;
import org.eclipse.pass.support.client.model.UserRole;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class JsonApiPassClientIT {
    private static PassClient client;

    @BeforeAll
    public static void setup() {
        client = PassClient.newInstance();
    }

    @Test
    public void testCreateSimpleObject() throws IOException {
        Publication pub = new Publication();
        pub.setIssue("issue");
        pub.setPmid("pmid");

        client.createObject(pub);

        assertNotNull(pub.getId());

        Publication test = client.getObject(pub);

        assertEquals(pub, test);
    }

    @Test
    public void testCreateGetObject() throws IOException {
        User pi = new User();
        pi.setDisplayName("Bessie Cow");
        pi.setRoles(Arrays.asList(UserRole.ADMIN));

        client.createObject(pi);

        List<User> copis = new ArrayList<>();

        for (String name : Arrays.asList("Jessie Farmhand", "Cassie Farmhand")) {
            User user = new User();
            user.setDisplayName(name);
            user.setRoles(Arrays.asList(UserRole.SUBMITTER));

            client.createObject(user);
            copis.add(user);
        }

        Funder funder = new Funder();
        funder.setName("Farmer Bob");

        client.createObject(funder);

        Grant grant = new Grant();

        grant.setAwardNumber("award");
        grant.setLocalKey("localkey");
        grant.setAwardDate(dt("2014-03-28T00:00:00.000Z"));
        grant.setStartDate(dt("2016-01-10T02:12:13.040Z"));
        grant.setDirectFunder(funder);
        grant.setPi(pi);
        grant.setCoPis(copis);

        client.createObject(grant);

        // Get the grant with the relationship target objects included
        Grant test = client.getObject(grant, "directFunder", "pi", "coPis");

        assertEquals(grant, test);

        // Get the grant without the relationship target objects included
        test = client.getObject(grant);

        // Relationship targets should just have id
        grant.setDirectFunder(new Funder(funder.getId()));
        grant.setPi(new User(pi.getId()));
        grant.setCoPis(copis.stream().map(u -> new User(u.getId())).collect(Collectors.toList()));

        assertEquals(grant, test);

        // Get the grant with one relationship, other relationship targets should just
        // have id
        test = client.getObject(grant, "directFunder");

        grant.setDirectFunder(funder);

        assertEquals(grant, test);
    }

    @Test
    public void testUpdateObject() throws IOException {

        Publication pub1 = new Publication();
        pub1.setTitle("Ten puns");

        Publication pub2 = new Publication();
        pub1.setTitle("Twenty puns");

        client.createObject(pub1);
        client.createObject(pub2);

        Submission sub = new Submission();

        sub.setAggregatedDepositStatus(AggregatedDepositStatus.NOT_STARTED);
        sub.setSource(Source.PASS);
        sub.setPublication(pub1);
        sub.setSubmitterName("Name");
        sub.setSubmitted(false);

        client.createObject(sub);

        assertEquals(sub, client.getObject(sub, "publication"));

        // Try to update pub1 attributes
        pub1.setTitle("Different title");

        client.updateObject(pub1);
        assertEquals(pub1, client.getObject(pub1));

        // Try to update sub attributes and relationship
        sub.setSource(Source.OTHER);
        sub.setSubmissionStatus(SubmissionStatus.CANCELLED);
        sub.setPublication(pub2);

        client.updateObject(sub);
        assertEquals(sub, client.getObject(sub, "publication"));
    }

    @Test
    public void testSelectObjects() throws IOException {
        String pmid = "" + UUID.randomUUID();

        Journal journal = new Journal();
        journal.setJournalName("The ministry of silly walks");

        client.createObject(journal);

        List<Publication> pubs = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            Publication pub = new Publication();

            pub.setIssue("Number: " + i);
            pub.setTitle("Title: " + i);
            pub.setPmid(pmid);
            pub.setJournal(journal);

            client.createObject(pub);
            pubs.add(pub);
        }

        String filter = RSQL.equals("pmid", pmid);
        PassClientSelector<Publication> selector = new PassClientSelector<>(Publication.class, 0, 100, filter, "id");
        selector.setInclude("journal");
        PassClientResult<Publication> result = client.selectObjects(selector);

        assertEquals(pubs.size(), result.getTotal());
        assertIterableEquals(pubs, result.getObjects());

        // Test selecting with an offset
        selector = new PassClientSelector<>(Publication.class, 5, 100, filter, "id");
        selector.setInclude("journal");
        result = client.selectObjects(selector);

        assertEquals(pubs.size(), result.getTotal());
        assertIterableEquals(pubs.subList(5, pubs.size()), result.getObjects());

        // Test using a stream which will make multiple calls. Do not include journal.
        selector = new PassClientSelector<>(Publication.class, 0, 2, filter, "id");
        pubs.forEach(p -> p.setJournal(new Journal(journal.getId())));
        assertIterableEquals(pubs, client.streamObjects(selector).collect(Collectors.toList()));
    }

    private static ZonedDateTime dt(String s) {
        return ZonedDateTime.parse("2010-12-10T02:01:20.300Z", Util.dateTimeFormatter());
    }

    @Test
    public void testAllObjects() {
        User pi = new User();
        pi.setAffiliation(Collections.singleton("affil"));
        pi.setDisplayName("Farmer Bob");
        pi.setEmail("farmerbob@example.com");
        pi.setFirstName("Bob");
        pi.setLastName("Bobberson");
        pi.setLocatorIds(Collections.singletonList("locator1"));
        pi.setMiddleName("Bobbit");
        pi.setOrcidId("23xx-xxxx-xxxx-xxxx");
        pi.setRoles(Arrays.asList(UserRole.SUBMITTER));
        pi.setUsername("farmerbob1");

        User copi = new User();
        copi.setAffiliation(Collections.singleton("barn"));
        copi.setDisplayName("Bessie The Cow");
        copi.setEmail("bessie@example.com");
        copi.setFirstName("Bessie");
        copi.setLastName("Cow");
        copi.setLocatorIds(Collections.singletonList("locator2"));
        copi.setMiddleName("The");
        copi.setOrcidId("12xx-xxxx-xxxx-xxxx");
        copi.setRoles(Arrays.asList(UserRole.SUBMITTER));
        copi.setUsername("bessie1");

        User preparer = new User();
        copi.setAffiliation(Collections.singleton("dairy"));
        copi.setDisplayName("Darren Dairy");
        copi.setEmail("darren@example.com");
        copi.setFirstName("Darren");
        copi.setLastName("Dairy");
        copi.setLocatorIds(Collections.singletonList("locator4"));
        copi.setOrcidId("15xx-xxxx-xxxx-xxxx");
        copi.setRoles(Arrays.asList(UserRole.SUBMITTER));
        copi.setUsername("darren1");

        Repository repository = new Repository();

        repository.setAgreementText("I agree to everything.");
        repository.setDescription("Repository description");
        repository.setFormSchema("form schema");
        repository.setIntegrationType(IntegrationType.FULL);
        repository.setName("Barn repository");
        repository.setRepositoryKey("barn");
        repository.setSchemas(Arrays.asList(URI.create("http://example.com/schema")));
        repository.setUrl(URI.create("http://example.com/barn.html"));

        Policy policy = new Policy();

        policy.setDescription("This is a policy description");
        policy.setInstitution(URI.create("https://jhu.edu"));
        policy.setPolicyUrl(URI.create("http://example.com/policy/oa.html"));
        policy.setRepositories(Arrays.asList(repository));
        policy.setTitle("Policy title");

        Funder primary = new Funder();

        primary.setLocalKey("bovine");
        primary.setName("Bovines R Us");
        primary.setPolicy(policy);
        primary.setUrl(URI.create("http://example.com/bovine"));

        Funder direct = new Funder();

        direct.setLocalKey("icecream");
        direct.setName("Icecream is great");
        direct.setPolicy(policy);
        direct.setUrl(URI.create("http://example.com/ice"));

        Grant grant = new Grant();

        grant.setAwardDate(dt("2010-01-10T02:01:20.300Z"));
        grant.setAwardNumber("moo42");
        grant.setAwardStatus(AwardStatus.ACTIVE);
        grant.setCoPis(Arrays.asList(copi));
        grant.setDirectFunder(direct);
        grant.setPrimaryFunder(primary);
        grant.setEndDate(dt("2015-12-10T02:04:20.300Z"));
        grant.setLocalKey("moo:42");
        grant.setPi(pi);
        grant.setProjectName("Moo Thru revival");
        grant.setStartDate(dt("2011-02-13T01:05:20.300Z"));

        Publisher publisher = new Publisher();

        publisher.setName("Publisher ");
        publisher.setPmcParticipation(null);

        Journal journal = new Journal();

        journal.setIssns(Arrays.asList("issn1"));
        journal.setJournalName("Ice Cream International");
        journal.setPmcParticipation(PmcParticipation.A);
        journal.setPublisher(publisher);

        Publication publication = new Publication();

        publication.setDoi("doi");
        publication.setIssue("3");
        publication.setJournal(journal);
        publication.setPmid("pmid");
        publication.setPublicationAbstract("Let x be...");
        publication.setTitle("This is a huge title");
        publication.setVolume("1 liter");

        Submission submission = new Submission();
        submission.setAggregatedDepositStatus(AggregatedDepositStatus.ACCEPTED);
        submission.setEffectivePolicies(Arrays.asList(policy));
        submission.setGrants(Arrays.asList(grant));
        submission.setMetadata("metadata");
        submission.setPreparers(Arrays.asList(preparer));
        submission.setPublication(publication);
        submission.setSource(Source.PASS);
        submission.setSubmissionStatus(null);
        submission.setSubmitted(true);
        submission.setSubmittedDate(dt("2012-12-10T02:01:20.300Z"));
        submission.setSubmitter(pi);
        submission.setSubmitterEmail(URI.create("mailto:" + pi.getEmail()));
        submission.setSubmitterName(pi.getDisplayName());

        SubmissionEvent event = new SubmissionEvent();
        event.setComment("This is a comment.");
        event.setEventType(EventType.SUBMITTED);
        event.setLink(URI.create("http://example.com/link"));
        event.setPerformedBy(pi);
        event.setPerformedDate(dt("2010-12-10T02:01:20.300Z"));
        event.setPerformerRole(PerformerRole.SUBMITTER);
        event.setSubmission(submission);

        RepositoryCopy rc = new RepositoryCopy();
        rc.setAccessUrl(URI.create("http://example.com/repo/item"));
        rc.setCopyStatus(CopyStatus.ACCEPTED);
        rc.setExternalIds(Arrays.asList("rc1"));
        rc.setPublication(publication);
        rc.setRepository(repository);

        File file = new File();

        file.setDescription("This is a file");
        file.setFileRole(FileRole.MANUSCRIPT);
        file.setMimeType("application/pdf");
        file.setName("ms.pdf");
        file.setSubmission(submission);
        file.setUri(URI.create("http://example.com/ms.pdf"));

        Deposit deposit = new Deposit();

        deposit.setDepositStatus(DepositStatus.ACCEPTED);
        deposit.setRepository(repository);
        deposit.setRepositoryCopy(rc);

        Contributor contrib = new Contributor();

        contrib.setAffiliation(Collections.singleton("field"));
        contrib.setDisplayName("Connie Contributor");
        contrib.setEmail("connie@example.com");
        contrib.setFirstName("Connie");
        contrib.setMiddleName("Charlie");
        contrib.setLastName("Contributor");
        contrib.setOrcidId("35xx-xxxx-xxxx-xxxx");
        contrib.setRoles(Arrays.asList(ContributorRole.CORRESPONDING_AUTHOR));
        contrib.setUser(pi);
        contrib.setPublication(publication);

        // Check that all the objects can be created.
        // Order such that relationship targets are created first.
        List<PassEntity> objects = Arrays.asList(pi, copi, preparer, repository, policy, primary, direct, grant,
                publisher, journal, publication, submission, event, rc, file, deposit, contrib);

        objects.forEach(o -> {
            try {
                client.createObject(o);
                assertNotNull(o.getId());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Check that objects can be retrieved.
        // For equality test, relationship targets must only have id

        policy.setRepositories(Arrays.asList(new Repository(repository.getId())));
        primary.setPolicy(new Policy(policy.getId()));
        direct.setPolicy(new Policy(policy.getId()));
        grant.setCoPis(Arrays.asList(new User(copi.getId())));
        grant.setPi(new User(pi.getId()));
        grant.setDirectFunder(new Funder(direct.getId()));
        grant.setPrimaryFunder(new Funder(primary.getId()));
        journal.setPublisher(new Publisher(publisher.getId()));
        publication.setJournal(new Journal(journal.getId()));
        submission.setGrants(Arrays.asList(new Grant(grant.getId())));
        submission.setEffectivePolicies(Arrays.asList(new Policy(policy.getId())));
        submission.setPreparers(Arrays.asList(new User(preparer.getId())));
        submission.setPublication(new Publication(publication.getId()));
        submission.setSubmitter(new User(pi.getId()));
        event.setPerformedBy(new User(pi.getId()));
        event.setSubmission(new Submission(submission.getId()));
        rc.setPublication(new Publication(publication.getId()));
        rc.setRepository(new Repository(repository.getId()));
        file.setSubmission(new Submission(submission.getId()));
        deposit.setRepository(new Repository(repository.getId()));
        deposit.setRepositoryCopy(new RepositoryCopy(rc.getId()));
        contrib.setUser(new User(pi.getId()));
        contrib.setPublication(new Publication(publication.getId()));

        objects.forEach(o -> {
            try {
                assertEquals(o, client.getObject(o));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
