#!/usr/bin/env bash

cd `dirname $0`

WORKING_DIR=`pwd`

if [[ ( -z "${SDK_NAME}" ) || ( -z "${RELEASE_VERSION}" ) ]]; then
    echo "Environment variables SDK_NAME and RELEASE_VERSION should be provided" >&2  # write error message to stderr
    exit 1
fi

if [[ ! "${RELEASE_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-rc[0-9]+)?$ ]]; then
    echo "Release version ${RELEASE_VERSION} is not release or release candidate" >&2  # write error message to stderr
    exit 1
fi


ARTIFACTS_DIR="$WORKING_DIR/artifacts/$SDK_NAME/$RELEASE_VERSION"

echo "==== Cleaning ${ARTIFACTS_DIR} ===="
rm -rf "$ARTIFACTS_DIR"

echo "==== Fetching SDK $SDK_NAME with version $RELEASE_VERSION to ${ARTIFACTS_DIR} ===="
ARTIFACTS_DIR="$ARTIFACTS_DIR" RELEASE_VERSION=$RELEASE_VERSION ./fetch/$SDK_NAME.sh
echo "==== Resulting files ===="
find "$ARTIFACTS_DIR"
