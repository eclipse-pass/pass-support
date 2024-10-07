#!/bin/sh

# Execute NIHMS harvest
java $PASS_NIHMS_LOADER_JAVA_OPTS -jar nihms-data-harvest-cli-exec.jar "$@"

# Execute NIHMS transform and load into PASS
java $PASS_NIHMS_LOADER_JAVA_OPTS -jar nihms-data-transform-load-exec.jar