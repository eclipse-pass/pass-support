# PASS Journal Loader

Parses the PMC type A journal `.csv` file, and/or the medline database `.txt` file, and syncs with the repository:

    * Adds journals if they do not already exist
    * Updates PMC method A participation if it differs from the corresponding resource in the repository

## Usage

Using java system properties to launch the journal loader:
```
    java -Dpmc=https://www.ncbi.nlm.nih.gov/pmc/front-page/NIH_PA_journal_list.csv -Dmedline=https://ftp.ncbi.nih.gov/pubmed/J_Medline.txt -Dpass.core.url=http://localhost:8080 -Dpass.core.user=USER -Dpass.core.password=PASS -jar pass-journal-loader-nih/target/pass-journal-loader-nih-0.6.0-SNAPSHOT-exe.jar
```
### Properties or Environment Variables

The following may be provided as system properties on the command line `-Dprop-value`.

`pass.core.url`
The base url for the pass-core REST API such as `http://localhost:8080`

`pass.core.user`
The pass-core backend user.

`pass.core.password`
The pass-core backend user password.

`dryRun`
Do not add or update resources in the repository, just give statistics of resources that would be added or updated

`pmc`
URL of the PMC "type A" journal .csv file, for example
[https://www.ncbi.nlm.nih.gov/pmc/front-page/NIH_PA_journal_list.csv](https://www.ncbi.nlm.nih.gov/pmc/front-page/NIH_PA_journal_list.csv)

`medline`
URL of the Medline journal file, for example
[https://ftp.ncbi.nih.gov/pubmed/J_Medline.txt](https://ftp.ncbi.nih.gov/pubmed/J_Medline.txt)

`LOG.*`
Adjust the logging level of a particular component, e.g. `LOG.org.eclipse.pass=WARN`
