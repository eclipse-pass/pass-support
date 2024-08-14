#!/bin/sh

xvfb-run --auto-servernum npm run refresh-token

./set_aws_param_store.sh