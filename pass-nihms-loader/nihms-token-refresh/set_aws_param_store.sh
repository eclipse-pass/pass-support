#! /bin/sh

export AWS_PAGER=""

aws ssm put-parameter \
    --name "NIHMS_API_TOKEN" \
    --type "SecureString" \
    --value file://$NIHMS_OUTFILE \
    --overwrite