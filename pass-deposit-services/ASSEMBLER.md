# Assemblers

Assemblers are responsible for gathering custodial and supplemental resources associated with a submission and returning
a stream of those resources - a `PackageStream` - according to a packaging specification. Deposit Services will then
stream the package to a downstream repository via a `Transport`.

## Use Case

Why develop an `Assembler`? Broadly speaking, an `Assembler` implementation is required for every packaging
specification you wish to support. For example, if you want to
produce [BagIt packages](https://tools.ietf.org/html/rfc8493)
and [DSpace METS packages](https://wiki.duraspace.org/display/DSPACE/DSpaceMETSSIPProfile), you would need
two `Assembler` implementations, each one responsible for producing packages that comport with their respective
specifications.

Another reason to develop an `Assembler` is to control how the metadata of a submission is mapped into your package. For
example, if your DSpace installation requires custom metadata elements, you would need to develop or extend an
existing `Assembler` (for example, by implementing a custom Package Provider) to include the custom metadata as
appropriate to your environment.

Fortunately, you should be able to extend a base `Assembler` class without having to write something from scratch.

## Quick Start

1. Create your Assembler class that extends `org.eclipse.pass.deposit.assembler.AbstractAssembler`
2. Create your Package Provider class that
   implements `org.eclipse.pass.deposit.assembler.PackageProvider`

To get started with testing:
Create your package verifier that implements `org.eclipse.pass.deposit.assembler.PackageVerifier`
Extend and implement `org.eclipse.pass.deposit.assembler.AbstractThreadedAssemblyIT`

# API Overview

## Assembler API

The main entrypoint into the Assembler API is on the `Assembler` interface:
`PackageStream assemble(DepositSubmission, Map<String, Object>)`
where the `DepositSubmission` is the internal representation of a `Submission`, and the `Map` is a set of package
options read from `repositories.json`.

The `AbstractAssembler` provides an implementation of `assemble(DepositSubmission, Map)`, and requires its subclasses to implement:

    PackageStream createPackageStream(DepositSubmission, List<DepositFileResource>, MetadataBuilder, ResourceBuilderFactory, Map<String, Object>)

Where the `List<DepositFileResource>` is the custodial content of the submission, the `MetadataBuilder` allowing
modification of the package-level metadata, and the `ResourceBuilderFactory` used to generate an instance
of `ResourceBuilder` for each `DepositFileResource`.

The primary benefit of extending `AbstractAssembler` is that the logic for identifying the custodial resources in the
submission and creating their representation as `List<DepositSubmission>` is shared. Subclasses of `AbstractAssembler`
must instantiate and return a `PackageStream`.

Examples can be found at: `DspaceMetsAssembler`, `NihmsAssembler`, and `BagItAssembler`

## PackageStream API

Assemblers are invoked by Deposit Services and return
a `PackageStream`. The `PackageStream` represents the content to be sent to a downstream repository. Conceptually, 
the `PackageStream` behaves like a Java `InputStream`: the bytes for the stream can come from anywhere (memory, a 
file on disk, or retrieved from another network resource), and can generally only be read once.

Practically, the `PackageStream` represents an archive file: either a ZIP, TAR, or some variant like TAR.GZ. This is
encapsulated by the `ArchivingPackageStream`class. Re-using the `ArchivingPackageStream` class has the advantage 
that your package resources will be bundled up in a single archive file according to the options supplied to 
the `Assembler` (e.g. compression and archive type to use).

To instantiate an `ArchivingPackageStream` class requires an instance of `PackageProvider`.

## PackageProvider API

The `PackageProvider`
interface was developed as an ad hoc lifecycle for streaming a package: there's a `start(...)` and `finish(...)` method,
along with a `packagePath(...)` method.   `PackageProvider` also defines a new
interface: `SupplementalResource`
. This interface is returned by the `finish(...)` method, allowing the `PackageProvider` implementation to generate
supplemental (i.e. BagIt tag files or METS.xml files) content after the rest of the package has been streamed.

Implementing this interface therefore allows for customizing where resources will appear in the package, and to
customize the metadata that appears in the package.

Because packaging specifications generally have something to say about what resources are included where in the package,
a Package Provider is loosely coupled to a package specification. For example, a Package Provider that placed custodial
resources in the `<package root>/foo` directory would be incompatible with a BagIt packaging specification, which
requires custodial resources to appear under `<package root>/data`. Similarly, if your Package Provider is to comport
with a DSpace METS packaging scheme, it will need to produce a `<package root>/METS.xml` file with the required content.
Therefore, any `PackageProvider` implementation can be used with any `Assembler` implementation as long as the package
specification shared between the two is not violated.

Examples: `DspaceMetsPackageProvider`
, `NihmsPackageProvider`
, `BagItPackageProvider`

## Recap

Implementations of `AbstractAssembler` return an `ArchivingPackageStream` which uses a `PackageProvider` to path
resources and generate supplemental metadata contained in the package.

When developing your own `Assembler`, you will need to:

* Extend `AbstractAssembler`
* Implement `PackageProvider`, including the logic to produce supplemental package content like BagIt tag files or
  DSpace METS.xml files
* Construct `ArchivingPackageStream` with your `PackageProvider` and return that from your `AbstractAssembler`
  implementation

Here are three examples:

* `DspaceMetsPackageProvider`
* `NihmsPackageProvider`
* `BagItPackageProvider`

# Concurrency

Assemblers exist in the Deposit Services runtime as singletons. A single `Assembler` instance may be invoked from
multiple threads, therefore all the code paths executed by an `Assembler` must be thread-safe.

`AbstractAssembler` and `ArchivingPackageStream` are already thread-safe; your concrete implementation
of `AbstractAssembler` and `PackageProvider` will need to maintain that thread safety. Streaming a package inherently
involves maintaining state, including the updating of metadata for resources as they are streamed. Package Providers
will often maintain state as they generate supplemental resources for a package; the `J10PMetadataDomWriter`
, for example, builds a METS.xml file using a DOM.

One strategy for maintaining thread safety is to scope any state maintained over the course of streaming a package to
the executing thread.  `Assembler` implementations are free to use whatever mechanisms they wish to insure thread
safety, but Deposit Services accomplishes this in its codebase by simply instantiating a new instance of
state-maintaining classes each time the `Assembler.assemble(...)` is invoked, and insures that state is not shared (i.e.
kept on the Thread stack and not in the JVM heap). For example:

* `AbstractAssembler` instantiates a new `MetadataBuilder` each time using a factory pattern
* `AbstractAssembler` implementations instantiate a new `ArchivingPackageStream` each time
* `DefaultStreamWriterImpl` instantiates a new `ResourceBuilder` for each resource being streamed using a factory
  pattern
* The `DspaceMetsAssembler` uses a factory pattern to instantiate its state-maintaining objects.

The factory objects may be kept in shared memory (i.e. as instance member variables), but the objects produced by the
factories are maintained in the Thread stack (as method variables). After a `PackageStream` has been opened and
subsequently closed, these objects will be released and garbage collected by the JVM. To help insure thread safety,
there is an integration test fixture, `ThreadedAssemblyIT`
, which can be subclassed and used by `Assembler` integration tests to verify thread safety.

# Testing

Adequate test coverage of `Assemblers` includes proper unit testing. This document presumes that you've adequately unit
tested your implementation, and instead focuses on integration testing.

Integration testing of `Assemblers` is supported by some shared test fixtures in the core Deposit Services codebase.

## ThreadedAssemblyIT

The approach taken by the shared `ThreadedAssemblyIT` is to invoke the `Assembler` under test directly using
random `DepositSubmission`s.  
a singleton `Assembler` implementation under test is retrieved from the IT subclass that you provide a number of
different `DepositSubmission`s are used to concurrently invoke `Assembler.assemble(...)` on the singleton instance under
test the `PackageStreams` returned by the `Assembler` under test are streamed to and stored on the filesystem A package
verifier supplied by the IT subclass verifies the content of the packages.

The advantage of extending `ThreadedAssemblyIT` is that it insures that your `Assembler` can be invoked concurrently by
multiple threads while avoiding the complexity of setting up and configuring the Deposit Services runtime. The Spring
Framework is not used, the Deposit Services runtime is not required, and no Docker containers are needed: the IT is
simple Java and JUnit. The downside is that your full runtime is not being integration tested, only your `Assembler`.

To use `ThreadedAssemblyIT`, extend it, and implement the required methods:

* `assemblerUnderTest()`: provide an AbstractAssembler instance, fully initialized and ready to be invoked
* `packageOptions()`: provides a set of package options, used when creating the PackageStream and storing it on disk.
  The package options include:
    * The package specification to be used
    * The compression algorithm used when creating the package
    * The checksumming algorithm to be used when calculating package and package resource checksums
* `packageVerifier()`: answers a `PackageVerifier` which inspects a package stored on the filesystem and verifies its
  content. You must implement a `PackageVerifier` for each `Assembler` being tested.

The test logic will execute automatically in `ThreadedAssemblyIT.testMultiplePackageStreams()`. The `PackageVerifier` is
very important: it does most of the heavy lifting with respect to passing or failing the integration test, so it must be
well written and test all aspects of a generated package.

Example: `BagItThreadedAssemblyIT`
, `J10PMetsThreadedAssemblyIT`
, `NihmsThreadedAssemblyIT`

## PackageVerifier

Each `Assembler` that is developed should have a corresponding `PackageVerifier`. The `PackageVerifier` is the primary
interface for verifying that a package written to disk contains the expected content. The primary method to implement
is:
`void verify(DepositSubmission, ExplodedPackage, Map<String, Object>)` where the `DepositSubmission` is the original
submission, `ExplodedPackage` is the generated package on disk, and the `Map` includes the options supplied to
the `Assembler` that created the package.

The verifier is responsible for:

* Insuring that every custodial file from the submission is present and accounted for in the package
* Insuring there are no extraneous custodial files in the package that are not in the submission
* Insuring that the custodial files checksums are correct
* Insuring that the proper supplemental files are present in the package and have the correct content

Essentially all aspects of a generated package must be verified through a `PackageVerifier`.

The `PackageVerifier` interface does come with
a helper method `verifyCustodialFiles`
for ensuring that there is a custodial file in the package for each submitted file, and that there are no unexplained
custodial files present in the package.
`void verifyCustodialFiles(DepositSubmission, File, FileFilter, BiFunction<File, File, DepositFile>)`
where `DepositSubmission` is the original submission, the `File` is the directory on the filesystem that contains the
exploded package, the `FileFilter` selects custodial files from the package directory, and the `BiFunction` accepts
a `DepositFile` from the submission and maps it to its expected location in the package directory.

Examples: `DspaceMetsPackageVerifier`
, `NihmsPackageVerifier`

# Runtime

Deposit Services is a Spring Boot application, and `Assembler`s are simply a component executed within the application.
If you are familiar with Spring and/or Spring Boot, you are welcome to leverage its features as you wish. Regardless of
your views of Spring, you need to be aware of Spring in these cases:

* When extending `SubmitAndValidatePackagesIT` (your IT will need to use the `SpringRunner`)
* Your `Assembler` implementation must be annotated with `@Component`

## Wiring

So, how is your `Assembler`, `PackageStream`, and `PackageProvider` wired together? As outlined above, the wiring of
these components is straightforward. You can either "hardwire" your implementations at compile-time, or you can leverage
Spring dependency injection as you wish.

Deposit Services uses Spring Auto Configuration to discover your `Assembler` on the classpath on boot. Supporting Spring
Auto Configuration is very simple:

* Insure your `Assembler` implementation is annotated with `@Component`.