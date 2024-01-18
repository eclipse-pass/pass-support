#!/bin/sh
java -DCOEUS_HOME=$COEUS_HOME_ENV -Dpass.core.url=$PASS_CORE_URL -Dpass.core.user=$PASS_CORE_USER -Dpass.core.password=$PASS_CORE_PASSWORD -jar jhu-grant-loader-exec.jar "$@"