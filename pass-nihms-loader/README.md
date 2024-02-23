# PASS NIHMS Submission ETL

The NIHMS Submission ETL contains the components required to download, transform and load Submission information from
NIHMS to PASS. The project includes two command line tools. The first uses the NIH API to download the CSV(s) containing
compliant, non-compliant, and in-process publication information. The second tool reads those files, transforms the data
to the PASS data model and then loads them to PASS.

For background information on PACM, see
the [user guide](https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/static/pacm-user-guide.pdf). Limited information on using
the PACM API can be
found [here](https://www.nlm.nih.gov/pubs/techbull/mj19/brief/mj19_api_public_access_compliance.html).

## NIHMS Data Harvest CLI

The NIHMS Data Harvest CLI uses the NIH API to download the PACM data.

### Pre-requisites

The following are required to run this tool:

* Java 8 or later
* Download the latest nihms-data-harvest-cli-exec.jar from
  the [releases page](https://github.com/OA-PASS/nihms-submission-etl/releases) and place in a folder on the machine
  where the application will run.
* Get an account for the NIH PACM website, and obtain an API key. The API key is only valid for 3 months, so it will 
  need to be updated periodically.
* Create a data folder that files will be downloaded to.

### Data Harvest Configuration

There are several ways to configure the Data Harvest CLI. Data Harvest CLI is a Spring Boot Application, so it can be
configured as described here: [Spring Boot Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)

You will need to set values for the following props: `nihmsetl.api.url.param.inst`, `nihmsetl.api.url.param.ipf`, 
and `nihmsetl.api.url.param.api-token`.  There are a number of ways to do this with a spring boot app that are described
in the link above.

Here is an example using Java system properties `-D`.
```
> java -Dnihmsetl.api.url.param.inst=my-inst -Dnihmsetl.api.url.param.ipf=my-ipf -Dnihmsetl.api.url.param.api-token=my-token -jar nihms-data-harvest-cli-exec.jar
```

### Running the Data Harvester

Once the Data Harvest CLI has been configured, there are a few additional options you can add when running from the
command line.

By default all 3 publication statuses - compliant, non-compliant, and in-process CSVs will be downloaded. To download
one or two of them, you can add them individually at the command line:

* `-c, -compliant, --compliant` - Download compliant publication CSV.
* `-p, -inprocess, --inprocess` - Download in-process publication CSV.
* `-n, -noncompliant, --noncompliant` - Download non-compliant publication CSV.

You can also specify a start date, by default the PACM system sets the start date to 1 year prior to the date of the
download. You can change this by adding a start date parameter:

* `-s, -startDate --startDate` - This will return all records published since the date provided. The syntax is `mm-yyyy`
  .

So, for example, to download the compliant publications published since December 2012, you would do the following:

```
> java -jar nihms-data-harvest-cli-exec.jar -s 12-2012 -c
```

On running this command, files will be downloaded and renamed with a prefix according to their status ("compliant", "
noncompliant", or "inprocess") and a timestamp integer e.g. `noncompliant_nihmspubs_20180507104323.csv`.

## NIHMS Data Transform-Load CLI

The NIHMS Data Transform-Load CLI reads data in from CSVs that were downloaded from the PACM system, converts them to
PASS compliant data and loads them into the PASS database.

### Pre-requisites

The following is required to run this tool:

* Java 8 or later
* Download latest nihms-data-transform-load-cli-exec.jar from
  the [releases page](https://github.com/OA-PASS/nihms-submission-etl/releases) and place in a folder on the machine
  where the application will run.

### Data Transform-Load Configuration

There are several ways to configure the Data Transform-Load CLI. Data Transform-Load CLI is a Spring Boot Application, 
so it can be configured as described here: [Spring Boot Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)

You will need to set values for the following props: `nihmsetl.repository.id`, `pass.client.url`, `pass.client.user`
and `pass.client.password`.  There are a number of ways to do this with a spring boot app that are described
in the link above.

Here is an example using Java system properties `-D`.
```
> java -Dnihmsetl.repository.id=my-repo-id -Dpass.client.url=my-url -Dpass.client.user=my-user -Dpass.client.password=my-pw -jar nihms-data-transform-load-exec.jar 
```

### Running the Data Transform-Load

Once the Data Transform-Load CLI has been configured, there are a few additional options you can add when running from
the command line.

By default all 3 publication statuses - compliant, non-compliant, and in-process CSVs will be downloaded. To download
one or two of them, you can add them individually at the command line:

* `-c, -compliant, --compliant` - Download compliant publication CSV.
* `-p, -inprocess, --inprocess` - Download in-process publication CSV.
* `-n, -noncompliant, --noncompliant` - Download non-compliant publication CSV.

So, for example, to process non-compliant spreadsheets only:

```
> java -jar nihms-data-transform-load-cli-exec.jar -n
```

When run, each row will be loaded into the application and new Publications, Submissions, and RepositoryCopies will be
created in PASS as needed. The application will also update any Deposit.repositoryCopy links where a new one is
discovered. Once a CSV file has been processed, it will be renamed with a suffix of ".done"
e.g. `noncompliant_nihmspubs_20180507104323.csv.done`. To re-process the file, simply rename it to remove the `.done`
suffix and re-run the application.
