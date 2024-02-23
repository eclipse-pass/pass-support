#!/bin/sh

# Execute NIHMS harvest
java -jar nihms-data-harvest-cli-exec.jar

# Execute NIHMS transform and load into PASS
java -jar nihms-data-transform-load-exec.jar