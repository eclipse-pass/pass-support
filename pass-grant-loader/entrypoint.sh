#!/bin/sh
java -DAPP_HOME=$APP_HOME_ENV -Dpass.core.url=$PASS_CORE_URL -Dpass.core.user=$PASS_CORE_USER -Dpass.core.password=$PASS_CORE_PASSWORD -jar jhu-grant-loader-exec.jar "$@"