package org.eclipse.pass.deposit.assembler;

import static java.lang.Math.floorDiv;
import static org.eclipse.pass.deposit.util.DepositTestUtil.openArchive;
import static org.eclipse.pass.deposit.util.DepositTestUtil.packageFile;
import static org.eclipse.pass.deposit.util.DepositTestUtil.savePackage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.apache.commons.io.FileUtils;
import org.eclipse.pass.deposit.AbstractDepositSubmissionIT;
import org.eclipse.pass.deposit.assembler.PackageOptions.Archive;
import org.eclipse.pass.deposit.assembler.PackageOptions.Compression;
import org.eclipse.pass.deposit.builder.DepositSubmissionModelBuilder;
import org.eclipse.pass.deposit.model.DepositSubmission;
import org.eclipse.pass.deposit.util.ResourceTestUtil;
import org.eclipse.pass.deposit.util.SubmissionTestUtil;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Submission;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Invokes a single instance of an {@link Assembler} by multiple threads, insuring that the {@code Assembler} does not
 * maintain any state across threads that would produce corrupt packages.
 * <p>
 * The packages produced by the {@code Assembler} are opaque to this test, therefore subclasses must implement several
 * methods that verify the contents of the packages produced by the {@code Assembler}.
 * </p>
 * <h3>Methods to implement</h3>
 * <dl>
 *     <dt>{@link #assemblerUnderTest()}</dt>
 *     <dd>Must return a single instance of an {@code Assembler}, fully configured and ready to produce packages</dd>
 *     <dt>{@link #packageOptions()}</dt>
 *     <dd>Must return the a {@code Map} of options used to configure the {@code Assembler}.  Required options are
 *         {@link Compression.OPTS} and {@link Archive.OPTS}.  {@code Compression.OPTS} may be specified as
 *         {@link Compression.OPTS#NONE NONE}, but {@link Archive.OPTS#NONE Archive.OPTS.NONE} is <em>not</em> a valid
 *         option.  This test expects the {@code Assembler} to stream an archive, not individual resources</dd>
 *     <dt>{@link #packageVerifier()}</dt>
 *     <dd>Returns an implementation of {@link PackageVerifier} used to validate the packages produced by this test</dd>
 * </dl>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public abstract class AbstractThreadedAssemblyIT extends AbstractDepositSubmissionIT {

    /**
     * Manages the threads used for the test execution.  Each thread will use the same instance of the {@code Assembler}
     * under test, and run {@link Assembler#assemble(DepositSubmission, Map)}.
     */
    private static ExecutorService itExecutorService;

    /**
     * The number of threads to launch.  The {@link #itExecutorService} should account for this and allow this number
     * of threads to execute simultaneously.
     */
    private static final int NO_THREADS = 10;

    /**
     * Used to name the threads created by the {@link #itExecutorService}.
     */
    private static final AtomicInteger IT_THREAD = new AtomicInteger(0);

    /**
     * Logger
     */
    protected static Logger LOG = LoggerFactory.getLogger(AbstractThreadedAssemblyIT.class);

    /**
     * The factory used to create instances of {@link org.eclipse.pass.deposit.assembler.MetadataBuilder}.
     * {@code MetadataBuilder} is used to add or create metadata describing the {@link PackageStream}.  The factory is
     * typically invoked <em>once</em> when building a {@code PackageStream} to create a single instance of
     * {@code MetadataBuilder}.
     */
    protected MetadataBuilderFactory mbf;

    /**
     * The factory used to create instances of {@link org.eclipse.pass.deposit.assembler.ResourceBuilder}.
     * {@code ResourceBuilder} is used to add or create metadata describing individual resources in the {@link
     * PackageStream}.  This factory is typically invoked <em>once per resource</em>.  There will be an instance of
     * {@code ResourceBuilder} instantiated for every resource contained in the {@code PackageStream}.
     */
    protected ResourceBuilderFactory rbf;

    /**
     * If {@code true} (the default) the packages produced by this test will be removed after this test succeeds.  Set
     * this flag to {@code false} to always leave the packages on the filesystem regardless of the test outcome.
     */
    protected boolean performCleanup = true;

    protected List<File> packagesToCleanup = new ArrayList<>();

    /**
     * The {@link Assembler} being tested.  Private so that sub classes are unable to change the object reference.
     */
    private Assembler underTest;

    private PackageVerifier verifier;

    @Autowired protected SubmissionTestUtil submissionUtil;
    @Autowired protected DepositSubmissionModelBuilder modelBuilder;

    /**
     * Instantiates the {@link #itExecutorService}.
     */
    @BeforeAll
    public static void setUpExecutorService() {
        ThreadFactory itTf = r -> new Thread(r, "ThreadedAssemblyITPool-" + IT_THREAD.getAndIncrement());
        itExecutorService = new ThreadPoolExecutor(floorDiv(NO_THREADS, 2), NO_THREADS, 10,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(floorDiv(NO_THREADS, 2)),
            itTf);
    }

    /**
     * Shuts down the {@link #itExecutorService}.
     *
     * @throws InterruptedException if the service is interrupted while awaiting termination
     */
    @AfterAll
    public static void stopExecutorService() throws InterruptedException {
        itExecutorService.shutdown();
        itExecutorService.awaitTermination(30, TimeUnit.SECONDS);
    }

    @BeforeEach
    public void setUpAssembler() {
        this.mbf = new DefaultMetadataBuilderFactory();
        this.rbf = new DefaultResourceBuilderFactory();
        this.underTest = assemblerUnderTest();
        this.verifier = packageVerifier();
    }

    @AfterEach
    public void cleanupPackageDirectories() {
        if (performCleanup) {
            LOG.info("Cleaning up packages created by this test.");
            packagesToCleanup.forEach(FileUtils::deleteQuietly);
        }
    }

    @Test
    public void testMultiplePackageStreams(TestInfo testInfo) throws IOException {
        Map<String, Object> packageOptions = packageOptions();

        assertTrue(packageOptions.containsKey(Archive.KEY));
        assertTrue(packageOptions.containsKey(Compression.KEY));
        assertFalse(packageOptions.containsValue(Archive.OPTS.NONE));

        Map<Integer, Future<PackageStream>> results = new HashMap<>();
        Map<Integer, File> packages = new HashMap<>();

        InputStream jsonInputStream = ResourceTestUtil.readSubmissionJson("sample1");
        List<PassEntity> entities = new ArrayList<>();
        Submission passSubmission = submissionUtil.readSubmissionJsonAndAddToPass(jsonInputStream, entities);
        DepositSubmission depositSubmission = modelBuilder.build(passSubmission.getId());

        LOG.info("Submitting packages to the assembler:");
        IntStream.range(0, NO_THREADS).forEach((index) ->
            results.put(
                index,
                itExecutorService.submit(() -> underTest.assemble(depositSubmission, packageOptions))
            )
        );

        LOG.info("Waiting for results from the assembler, and saving each package:");
        // Get each result insuring no exceptions were thrown.
        String testMethodName = testInfo.getTestMethod().get().getName();
        results.forEach((submissionIndex, future) -> {
            try {
                LOG.info("{} ...", submissionIndex);
                PackageStream stream = future.get();
                assertNotNull(stream.metadata());
                assertNotNull(stream.metadata().archive());
                assertNotNull(stream.metadata().compression());
                assertEquals(stream.metadata().archive(), packageOptions.get(Archive.KEY));
                assertEquals(stream.metadata().compression(), packageOptions.get(Compression.KEY));

                // TODO: verify resources metadata, but PackageStream#resources is unsupported
                File packageFile = savePackage(packageFile(this.getClass(),
                    testMethodName + submissionIndex, stream.metadata()), stream);
                packages.put(submissionIndex, packageFile);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        List<File> toClean = new ArrayList<>();

        LOG.info("Opening and verifying each package:");
        packages.forEach((submissionIndex, packageFile) -> {
            LOG.info(".");
            File dir;
            try {
                dir = openArchive(packageFile, (Archive.OPTS) packageOptions.get(Archive.KEY),
                    (Compression.OPTS) packageOptions.get(Compression.KEY));
                LOG.info("Extracted package {} to {}", packageFile, dir);
                // Have subclass verify the content in the extracted package directory
                verifier.verify(depositSubmission, new ExplodedPackage(packageFile, dir), packageOptions);
                LOG.info("Successfully verified package in {}", dir);
                toClean.add(dir);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Upon success, add the package directories for cleanup.
        // Failure will leave the directories behind for diagnosis.
        packagesToCleanup.addAll(packages.values());
        packagesToCleanup.addAll(toClean);
    }

    /**
     * To be implemented by subclasses: must return a fully functional instance of the {@link Assembler} to be
     * tested.  {@link #setUpAssembler()} stores this instance as {@link #underTest}.  This instance is used to process
     * a {@link DepositSubmission} and produce a package containing the custodial content of the submission and any
     * supplemental files like BagIT tag files or additional metadata.
     *
     * @return the {@code AbstractAssembler} under test
     */
    protected abstract Assembler assemblerUnderTest();

    /**
     * To be implemented by subclasses: must return the {@link PackageOptions} that will be used when invoking
     * {@link Assembler#assemble(DepositSubmission, Map)}.  {@link Compression.OPTS} and {@link Archive.OPTS} must be
     * supplied.  Note that {@link Archive.OPTS#NONE} <strong>is not supported</strong> at this time.
     *
     * @return the package options supplied to the {@code Assembler} under test
     */
    protected abstract Map<String, Object> packageOptions();

    /**
     * Returns a {@link PackageVerifier} used to verify the package created by this test.
     *
     * @return the PackageVerifier
     */
    protected abstract PackageVerifier packageVerifier();

}
