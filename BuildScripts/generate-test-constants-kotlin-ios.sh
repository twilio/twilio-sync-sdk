#!/bin/bash

set -e
set -x

MONOREPO_DIR=`git rev-parse --show-toplevel`

cat <<EOF > $MONOREPO_DIR/ios/TwilioSync/TwilioSyncTests/utils/TestConstants.swift
// !!! This file is auto-generated by BuildScripts/generate-test-constants-kotlin-ios.sh !!!
// !!! Don't edit it manually !!!

let SYNC_ACCESS_TOKEN_SERVICE_URL = "$SYNC_ACCESS_TOKEN_SERVICE_URL"
EOF
