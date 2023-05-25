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
* Download the latest nihms-data-harvest-cli-{version}-shaded.jar from
  the [releases page](https://github.com/OA-PASS/nihms-submission-etl/releases) and place in a folder on the machine
  where the application will run.
* Get an account for the NIH PACM website, and obtain an API key.
* Create a data folder that files will be downloaded to.

### Data Harvest Configuration

There are several ways to configure the Data Harvest CLI. You can use a configuration file, environment variables,
system variables, or a combination of these. The configuration file will set system properties. In the absence of a
config file, system properties will be used, and in the absence of those, environment variables will be used. Note that
to use environment variables, the system property name must be converted to upper case, and the periods replaced with
underscores. For example, to define `nihmsetl.data.dir` as an environment variable, use `NIHMSETL_DATA_DIR` instead.

> **Note**: URL parameters specified using system properties named `nihmsetl.api.url.param.<param name>` cannot be specified as _environment variables_. They may only be specified as system properties or in the `nihms-harvest.properties` file.

By default, the application will look for a configuration file named `nihms-harvest.properties` in the folder containing
the java application. You can override the location of the properties file by defining an environment variable
for `nihmsetl.harvester.configfile` e.g.

```
> java -Dnihmsetl.harvester.configfile=/path/to/configfile.properties -jar nihms-data-harvest-cli-1.0.0-SNAPSHOT-shaded.jar 
```

The configuration file should look like this:

```properties
# Example properties file for nihms-data-harvest-cli
#
# Defines a folder to download CSV files to. If it doesn’t exist, it will create it for you.
# Will default to ./data in the folder the Java app runs in
# nihmsetl.data.dir=data
#

# NIH API hostname
nihmsetl.api.host=www.ncbi.nlm.nih.gov

# HTTP scheme
nihmsetl.api.scheme=https

# Currently the port is not used
# nihmsetl.api.port = 

# NIH API URL path
nihmsetl.api.path=/pmc/utils/pacm/

# Allow 30 seconds for a request to be read before timing out
nihmsetl.http.read-timeout-ms=30000

# Allow 30 seconds for establishing connections before timing out
nihmsetl.http.connect-timeout-ms=30000

# URL Parameters
#   Additional parameters may be added, and they will be included in the API URL as request parameters
#   Parameters may be added as 'nihmsetl.api.url.param.<parameter name>' where '<parameter name>' is the
#   URL request parameter

# Format ought to be CSV, otherwise the loader won't be able to process the saved files
nihmsetl.api.url.param.format=csv

# Institution name, unclear as to how it is used
nihmsetl.api.url.param.inst=JOHNS HOPKINS UNIVERSITY

#  IPF (Institutional Profile File) number, the unique ID assigned to a grantee organization in the eRA system. 
nihmsetl.api.url.param.ipf=4134401

# The API token retrieved from the PACM website.  These expire every three months.
nihmsetl.api.url.param.api-token=XXXXXXX-XXXX-XXXX-XXXXXX

# Date in MM/YYYY format that the PACM data should start from (may be set using the `-s` harvester command line option).  By default this date will be set to the current month, one year ago 
# nihmsetl.api.url.param.pdf = 07/2018

# Date in MM/YYYY format that the PACM data should end at (leave commented to default to the current month)
# nihmsetl.api.url.param.pdt = 07/2019

# Undocumented, not used
# nihmsetl.api.url.param.rd =

# Undocumented, not used
# nihmsetl.api.url.param.filter =
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
> java -jar nihms-data-harvest-cli-1.0.0-SNAPSHOT-shaded.jar -s 12-2012 -c
```

On running this command, files will be downloaded and renamed with a prefix according to their status ("compliant", "
noncompliant", or "inprocess") and a timestamp integer e.g. `noncompliant_nihmspubs_20180507104323.csv`.

## NIHMS Data Transform-Load CLI

The NIHMS Data Transform-Load CLI reads data in from CSVs that were downloaded from the PACM system, converts them to
PASS compliant data and loads them into the PASS database.

### Pre-requisites

The following is required to run this tool:

* Java 8 or later
* Download latest nihms-data-transform-load-cli-{version}-shaded.jar from
  the [releases page](https://github.com/OA-PASS/nihms-submission-etl/releases) and place in a folder on the machine
  where the application will run.

### Data Transform-Load Configuration

There are several ways to configure the Data Transform-Load CLI. You can use a configuration file, environment
variables, system variables, or a combination of these. The configuration file will set system properties. In the
absence of a config file, system properties will be used, and in the absence of those, environment variables will be
used.

By default, the application will look for a configuration file named `nihms-loader.properties` in the folder containing
the java application. You can override the location of the properties file by defining an environment variable
for `nihmsetl.harvester.configfile` e.g.

```
> java -Dnihmsetl.loader.configfile=/path/to/configfile.properties -jar nihms-data-transform-load-cli-1.0.0-SNAPSHOT-shaded.jar 
```

The configuration file should look like this:

```
nihmsetl.data.dir=/path/to/pass/loaders/data
nihmsetl.loader.cachepath=/path/to/pass/loaders/cache/compliant-cache.data
nihmsetl.repository.uri=https://example:8080/fcrepo/rest/repositories/aaa/bbb/ccc
nihmsetl.pmcurl.template=https://www.ncbi.nlm.nih.gov/pmc/articles/%s/
pass.core.baseurl=http://localhost:8080/
pass.core.user=admin
pass.core.password=password
```

* `nihmsetl.data.dir` is the path that the CSV files will be read from. If a path is not defined, the app will look for
  a `/data` folder in the folder containing the java app.
* `nihmsetl.loader.cachepath` designates a path to a file that will be used to store a cache of completed compliant data
  so that it is not reprocessed. Note that this file can be deleted to force a complete recheck of the data. If a path
  is not defined, this will default to a file at `/cache/compliant-cache.data` in the folder containing the java app.
* `nihmsetl.repository.uri` the URI for the Repository resource in PASS that represents the PMC repository.
* `nihmsetl.pmcurl.template` is the template URL used to construct the RepositoryCopy.accessUrl. The article PMC is
  passed into this URL.
* `pass.core.baseurl` - The base url for the pass-core REST API such as `http://localhost:8080`
* `pass.core.user` - User name for pass-core access
* `pass.core.password` - Password for pass-core access

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
> java -jar nihms-data-transform-load-cli-1.0.0-SNAPSHOT-shaded.jar -n
```

When run, each row will be loaded into the application and new Publications, Submissions, and RepositoryCopies will be
created in PASS as needed. The application will also update any Deposit.repositoryCopy links where a new one is
discovered. Once a CSV file has been processed, it will be renamed with a suffix of ".done"
e.g. `noncompliant_nihmspubs_20180507104323.csv.done`. To re-process the file, simply rename it to remove the `.done`
suffix and re-run the application.
