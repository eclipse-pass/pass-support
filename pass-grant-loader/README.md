# PASS Grant Loader

This module comprises code for retrieving grant data from some kind of data source, and using that data to update
the PASS backend. Typically, the data pull will populate a data structure, which will then be consumed by a loader
class. While this sounds simple in theory, there are several considerations which may add to the complexity of
implementations. An implementor must first determine data requirements for the use of the data once it has been ingested
into PASS, and then map these requirements to data available from the data source. It may be that additional data from
other services may be needed in order to populate the data structures to be loded into PASS. On the loading side, the
implementor may need to support different modes of ingesting data. Additional logic may be needed in the data loading
apparatus to resolve the fields in the data assembled by the pull process. For example, we will need to consider that
several systems may be updating PASS objects, and that other services may be more authoritative for certain fields than
the service providing the grant data. The JHU implementation is complex regarding these issues.

## Developer Notes

This project has been adapted to be able to build several jars for loading data for loading data into PASS instances for
different institutions. For the sake of efficiency, we do this in one project rather than in several projects. We abuse
the shade plugin and provide a separate `<execution>` for each artifact. Because different jars will have different
revision schedules, we control the versioning for each implementation manually. We increment the current version for
each implementation at the end of the `<properties>` section of the main pom file for the project. This is reflected in
the `<finalName>` element in the configuration for the corresponding `<execution>` section for the implementation in the
pass-grant-cli pom file.

The code has been factored to ease development for multiple institutions. The main components are the CLI, the App, the
Connector and the Updater. In addition, there is an institution-specific PassEntityUtility Class which contains the
logic for deciding when an entity needs to be updated.

#### CLI

The CLI class defines which options or files need to be supplied to the App. This needs to be written for each
implementation.

#### App

The App setup has a BaseGrantLoaderApp class which contains a few abstract methods to be implemented in child classes
for each institution. These methods essentially configure the App class for each institution by supplying a Connector
and an Updater implementation. We also let the App know if the implementation has enough data in its feed to enable
updating (requires some kind of latest update timestamp in the data).

#### Connector

The Connector class connects to the data store for an institution's implementation, and operates on the data to supply,
in as standard a form as possible, the data to be consumed by the Updater.

#### Updater

The Updater class takes the data supplied by the Connector and creates or updates the corresponding objects in the PASS
repository accordingly. There is a Default class whose children may override certain substantive methods if the local
policies require. In most cases, the child classes will simply supply a PassEntityUtil class which has been tuned for
the institution, and also a domain string for constructing identifiers.

## Implementations

### JHU

The JHU implementation is used to pull data from the COEUS Oracle database views for the purpose of performing regular
updates. We look at grants which have been updated since a particular time (typically the time of the previous update),
join this with user and funder information associated with the grant, and then use this information to update the data
in the PASS backend. The JHU implementation also treats the COEUS database as authoritative for certain fields in the
data - the logic about whether updates are required is contained in the PassEntityUtil implementation for JHU. Details
about operation are available at
[JHU COEUS Loader](JHU-README.md)

## Usage

Using java system properties to launch the jhu grant loader:
```
    java -DCOEUS_HOME=<full_path_to_home> -Dpass.core.url=http://localhost:8080 -Dpass.core.user=USER -Dpass.core.password=PASS -jar jhu-grant-loader-0.6.0-SNAPSHOT.jar -startDateTime "<yyyy-mm-dd hh:mm:ss.m{mm}>" -awardEndDate <MM/dd/yyyy>

```
### Properties or Environment Variables

The following may be provided as system properties on the command line `-Dprop-value`.

`COEUS_HOME`
The full path to where the connections.properties and policy.properties are located

`pass.core.url`
The base url for the pass-core REST API such as `http://localhost:8080`

`pass.core.user`
The pass-core backend user.

`pass.core.password`
The pass-core backend user password.

### Arguments

You can run the above command with `-h` to get a full list of arguments for the grant loader.  In the example
above, we use `startDateTime` and `awardEndDate` for an example.
