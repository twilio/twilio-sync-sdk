#!/bin/bash

source `dirname $0`/common/fetch-sonatype-android.sh

fetchFromSonatype "$ARTIFACTS_DIR" $SONATYPE_REPO_ID sync-android-kt $RELEASE_VERSION
fetchFromSonatype "$ARTIFACTS_DIR" $SONATYPE_REPO_ID sync-android-java $RELEASE_VERSION
