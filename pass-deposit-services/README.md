# PASS Deposit Services

Deposit Services are responsible for the transfer of custodial content and metadata from end users to repositories. End
users transfer custody of their content to PASS by performing a submission through the HTML user interface, and Deposit
Services subsequently transfers the custody of content to downstream repositories.

Deposit Services is deployed as "back-end" infrastructure. It has no user-facing elements. In particular, Deposit
Services is unaware of the internal/external duality of resource URIs. This means that when looking at URIs in Deposit
Services' logging output, some adjustment may be necessary for a developer or systems operator to retrieve the resource
from their location in the network topology.

## Configuration

The primary mechanism for configuring Deposit Services is through environment variables. This aligns with the patterns
used in development and production infrastructure which rely on Docker and its approach to runtime configuration.

### Production Configuration Variables

| Environment Variable    | Default Value                                                                  |Description|
|-------------------------|--------------------------------------------------------------------------------|-----------|
| `DSPACE_HOST`           | localhost                                                                      |the IP address or host name of the server running the SWORD protocol version 2 endpoint
| `DSPACE_PORT`           | 8181                                                                           |the TCP port exposing the SWORD protocol version 2 endpoint
| `FTP_HOST`              | localhost                                                                      |the IP address or  host name of the NIH FTP server
| `FTP_PORT`              | 21                                                                             |the TCP control port of the NIH FTP server
| `PASS_DEPOSIT_JOBS_CONCURRENCY` | 2                                                                              |the number of Scheduled jobs that may be run concurrently.
| `PASS_DEPOSIT_JOBS_DEFAULT_INTERVAL_MS` | 600000                                                                         |the amount of time, in milliseconds, that Scheduled jobs run.
| `PASS_DEPOSIT_QUEUE_SUBMISSION_NAME` | submission                                                                     |the name of the JMS queue that has messages pertaining to `Submission` resources (used by the `JmsSubmissionProcessor`)
| `PASS_DEPOSIT_QUEUE_DEPOSIT_NAME` | deposit                                                                        |the name of the JMS queue that has messages pertaining to `Deposit` resources (used by the `JmsDepositProcessor`)
| `PASS_DEPOSIT_REPOSITORY_CONFIGURATION` | classpath:/repositories.json                                                   |points to a properties file containing the configuration for the transport of custodial content to remote repositories. Values must be [Spring Resource URIs][1]. See below for customizing the repository configuration values.
| `PASS_DEPOSIT_WORKERS_CONCURRENCY` | 4                                                                              |the number of Deposit Worker threads that can simultaneously run.
| `PASS_CLIENT_URL`       | localhost:8080                                                                 |the URL used to communicate with the PASS Core API. Normally this variable does not need to be changed (see note below)
| `PASS_CLIENT_PASSWORD`        | fakepassword                                                                           |the password used for `Basic` HTTP authentication to the PASS Core API
| `PASS_CLIENT_USER`            | fakeuser                                                                           |the username used for `Basic` HTTP authentication to the PASS Core API

### Repositories Configuration

The Repository configuration contains the parameters used for connecting and depositing custodial material to downstream
repositories. The format of the configuration file is JSON, defining multiple downstream repositories in a single file.

Each repository configuration has a top-level key that is used to identify a particular configuration. Importantly, each
top-level key _must_ map to a [`Repository` resource][5] within the PASS repository. This implies that the top-level
keys in `repositories.json` are not arbitrary. In fact, the top level key must be:

* the value of a `Repository.repositoryKey` field (of a `Repository` resource in the PASS repository)

Deposit Services comes with a default repository configuration, but a production environment will want to override the
default. Defaults are overridden by creating a copy of the default configuration, editing it to suit, and
setting `PASS_DEPOSIT_REPOSITORY_CONFIGURATION` to point to the new location.

> Acceptable values for `PASS_DEPOSIT_REPOSITORY_CONFIGURATION` must be a form of [Spring Resource URI][1].

A possible repository configuration is replicated below:

```json
{
  "JScholarship": {
    "deposit-config": {
      "processing": {
        "beanName": "org.eclipse.pass.deposit.messaging.status.DefaultDepositStatusProcessor"
      },
      "mapping": {
        "http://dspace.org/state/archived": "accepted",
        "http://dspace.org/state/withdrawn": "rejected",
        "default-mapping": "submitted"
      }
    },
    "assembler": {
      "specification": "http://purl.org/net/sword/package/METSDSpaceSIP"
    },
    "transport-config": {
      "auth-realms": [
        {
          "mech": "basic",
          "username": "user",
          "password": "pass",
          "url": "https://jscholarship.library.jhu.edu/"
        },
        {
          "mech": "basic",
          "username": "user",
          "password": "pass",
          "url": "https://dspace-prod.mse.jhu.edu:8080/"
        },
        {
          "mech": "basic",
          "username": "dspace-admin@oapass.org",
          "password": "foobar",
          "url": "http://${dspace.host}:${dspace.port}/swordv2"
        }
      ],
      "protocol-binding": {
        "protocol": "SWORDv2",
        "username": "dspace-admin@oapass.org",
        "password": "foobar",
        "server-fqdn": "${dspace.host}",
        "server-port": "${dspace.port}",
        "service-doc": "http://${dspace.host}:${dspace.port}/swordv2/servicedocument",
        "default-collection": "http://${dspace.host}:${dspace.port}/swordv2/collection/123456789/2",
        "on-behalf-of": null,
        "deposit-receipt": true,
        "user-agent": "pass-deposit/x.y.z"
      }
    }
  },
  "PubMed Central": {
    "deposit-config": {
      "processing": {
      },
      "mapping": {
        "INFO": "accepted",
        "ERROR": "rejected",
        "WARN": "rejected",
        "default-mapping": "submitted"
      }
    },
    "assembler": {
      "specification": "nihms-native-2017-07"
    },
    "transport-config": {
      "protocol-binding": {
        "protocol": "ftp",
        "username": "nihmsftpuser",
        "password": "nihmsftppass",
        "server-fqdn": "${ftp.host}",
        "server-port": "${ftp.port}",
        "data-type": "binary",
        "transfer-mode": "stream",
        "use-pasv": true,
        "default-directory": "/logs/upload/%s"
      }
    }
  }
}
```

#### Customizing Repository Configuration Elements

The repository configuration above will not be suitable for production. A production deployment needs to provide
updated authentication credentials and insure the correct value for the default SWORD collection URL
- `default-collection`. Each `transport-config` section should be reviewed for correctness, paying special attention
to `protocol-binding` and `auth-realm` blocks: update `username` and `password` elements, and insure correct values for
URLs.

Values may be parameterized by any property or environment variable.

To create your own configuration, copy and paste the default configuration into an empty file and modify the JSON as
described above. The configuration _must_ be referenced by the `pass.deposit.repository.configuration` property, or is
environment equivalent `PASS_DEPOSIT_REPOSITORY_CONFIGURATION`. Allowed values are any [Spring Resource path][1] (
e.g. `classpath:/`, `classpath*:`, `file:`, `http://`, `https://`). For example, if your configuration is stored as a
file in `/etc/deposit-services.json`, then you would set the environment
variable `PASS_DEPOSIT_REPOSITORY_CONFIGURATION=file:/etc/deposit-services.json` prior to starting Deposit Services.
Likewise, if you kept the configuration accessible at a URL, you could
use `PASS_DEPOSIT_REPOSITORY_CONFIGURATION=http://example.org/deposit-services.json`.

## Failure Handling

A "failed" `Deposit` or `Submission` has `Deposit.DepositStatus = FAILED`
or `Submission.AggregateDepositStatus = FAILED`. When a resource has been marked `FAILED`, Deposit Services will ignore
any messages relating to the resource. Failed `Deposit` resources will be retried as part of the 
`DepositStatusUpdaterJob` job.  Once all `Deposit` resource are successful, the failed 
`Submission.AggregateDepositStatus` will be updated.

A resource will be considered as failed when errors occur during the processing of `Submission` and `Deposit` resources.
Some errors may be caused by transient network issues, or a server being rebooted.  In the case of such failures,
Deposit Services will retry for n number of days after the `Submission` is created. The number of days
is set in an application property named `pass.status.update.window.days`.

`Submission` resources are failed when:

1. Failure to build the Deposit Services model for a Submission
1. There are no files attached to the Submission
1. Any file attached to the Submission is missing a location URI (the URI used to retrieve the bytes of the file).
1. An error occurs saving the state of the `Submission` in the repository (arguably a transient error)

See `SubmissionProcessor` for details. Right now, when a `Submission` is failed, manual intervention may be required.
Deposit Services does retry the failed `Deposit` resources of the `Submission`. However, some of the failure scenarios
above would have to be resolved by the user. It is possible the end-user will need to re-create the submission in 
the user interface, and resubmit it.

`Deposit` resources are failed when:

1. An error occurs building a package
1. An error occurs streaming a package to a `Repository` (arguably transient)
1. An error occurs polling (arguably transient) or parsing the status of a `Deposit`
1. An error occurs saving the state of a `Deposit` in the repository (again, arguably transient)

See `DepositTask` for details. Deposits fail for transient reasons; a server being down, an interruption in network
communication, or invalid credentials for the downstream repository are just a few examples. As stated, DS will retry
failed `Deposit` resources for n number of days after the creation of the associated `Submission`.  The number of days
is set in an application property named `pass.status.update.window.days`.

## Build and Deployment

Deposit Services' primary artifact is a single self-executing jar. In the PASS infrastructure,
the Deposit Services self-executing jar is deployed inside of a simple Docker container.

Deposit Services can be built by running:

* `mvn clean install`

The main Deposit Services deployment artifact is found in `deposit-core/target/pass-deposit-service-exec.jar`. It
is this jarfile that is included in the Docker image for Deposit Services, and posted on the GitHub Release page.

[1]: https://docs.spring.io/spring/docs/5.0.7.RELEASE/spring-framework-reference/core.html#resources-implementations

[2]: https://docs.spring.io/spring/docs/5.0.7.RELEASE/javadoc-api/org/springframework/util/ErrorHandler.html

[3]: https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.UncaughtExceptionHandler.html

[4]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/RejectedExecutionHandler.html
