#!/usr/bin/env bash

cd `dirname $0`
WORKING_DIR=`pwd`

CDN_PINNING=true
CDN_UPLOADING=false

source cdn-common/cdn-prepare.sh

echo "==== Pinning $SDK_NAME with version $RELEASE_VERSION to ${DEPLOY_REALM} CDN ===="

if [ "${PIN_RELEASE}" = true ] ; then
    echo "==== Pinning $SDK_NAME release with version $RELEASE_VERSION to MAJOR.MINOR on ${DEPLOY_REALM} CDN ===="
    ./sdk-release-tool pin ${SDK_RELEASE_TOOL_OPTIONS}
elif [ "${PIN_RELEASE}" = false ] ; then
    echo "==== Skip pinning release ===="
else
    echo "PIN_RELEASE is not 'true' or 'false'. Exiting" >&2  # write error message to stderr
    exit 1
fi

if [ "${PIN_LATEST}" = true ] ; then
    echo "==== Pinning $SDK_NAME release with version $RELEASE_VERSION to LATEST on ${DEPLOY_REALM} CDN ===="
    ./sdk-release-tool pin-latest ${SDK_RELEASE_TOOL_OPTIONS}
elif [ "${PIN_LATEST}" = false ] ; then
    echo "==== Skip pinning to LATEST ===="
else
    echo "PIN_LATEST is not 'true' or 'false'. Exiting" >&2  # write error message to stderr
    exit 1
fi
