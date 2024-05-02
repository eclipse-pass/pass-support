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

The grant loader uses Spring Boot Profiles to select the appropriate classes to be used for a given institution.
There is a property in application.properties named `spring.profiles.active` that needs to be set when starting the
grant loader.  This property can be set at runtime as well using the normal spring boot configuration functionality.
[Spring Boot Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)

The code has been factored to ease development for multiple institutions. These are the institution-specific classes 
which typically need to be implemented and annotated with the `@Profile` annotation:

#### Connector

The Connector class connects to the data store for an institution's implementation, and operates on the data to supply,
in as standard a form as possible, the data to be consumed by the Updater.

#### Updater

The Updater class takes the data supplied by the Connector and creates or updates the corresponding objects in the PASS
repository accordingly. There is a Default class whose children may override certain substantive methods if the local
policies require.

## Profiles

### JHU (jhu)

The JHU implementation is used to pull data from the COEUS Oracle database views for the purpose of performing regular
updates. We look at grants which have been updated since a particular time (typically the time of the previous update),
join this with user and funder information associated with the grant, and then use this information to update the data
in the PASS backend. The JHU implementation also treats the COEUS database as authoritative for all fields in the
data. If a grant is being passed in for update, it is assumed that all records for that grant are included in the
input.

## Usage

Refer to the application.properties file to determine which properties that need runtime values set. The grant loader
is a spring boot application, so use the standard Spring Boot configuration functionality 
[Spring Boot Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)

Here is an example using Java system properties `-D`.
```
    java -jar jhu-grant-loader-0.6.0-SNAPSHOT.jar -a load file:./grant-data.csv
```

### Arguments

You can run the above command with `-h` to get a full list of arguments for the grant loader.  In the example
above, we use `startDateTime` and `awardEndDate` for an example.
