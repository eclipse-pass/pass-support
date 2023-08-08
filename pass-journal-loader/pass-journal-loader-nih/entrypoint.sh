#!/bin/sh
java -Dpmc=$PMC_FILE -Dmedline=$MEDLINE_FILE -Dpass.core.url=$PASS_CORE_URL -Dpass.core.user=$PASS_CORE_USER -Dpass.core.password=$PASS_CORE_PASSWORD -jar pass-journal-loader-nih-exec.jar