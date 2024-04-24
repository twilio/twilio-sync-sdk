#!/usr/bin/env bash

cd `dirname $0`

WORKING_DIR=`pwd`

CDN_PINNING=false
CDN_UPLOADING=true

source cdn-common/cdn-prepare.sh

echo "==== Uploading $SDK_NAME with version $RELEASE_VERSION to ${DEPLOY_REALM} CDN ===="
./sdk-release-tool upload ${SDK_RELEASE_TOOL_OPTIONS} "$ARTIFACTS_DIR"
