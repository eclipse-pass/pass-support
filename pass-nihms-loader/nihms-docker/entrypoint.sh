#!/bin/sh

# Execute NIHMS harvest
java -Dnihmsetl.harvester.configfile=$NIHMS_HARVEST_CONFIG -Dpass.core.url=$PASS_CORE_URL -Dpass.core.user=$PASS_CORE_USER -Dpass.core.password=$PASS_CORE_PASSWORD -jar nihms-data-harvest-cli-exec.jar

# Execute NIHMS transform and load into PASS
java -Dnihmsetl.loader.configfile=$NIHMS_LOAD_CONFIG -Dpass.core.url=$PASS_CORE_URL -Dpass.core.user=$PASS_CORE_USER -Dpass.core.password=$PASS_CORE_PASSWORD -jar nihms-data-transform-load-exec.jar