#!/bin/bash

if [ $# -lt 3 ]; then
    SELF=`basename $0`
    echo "Usage: $SELF <APK_RUNNER_APP> <APK_RUNNER_ANDROID_TEST> <RENAME_SUFFIX>"
    exit 1
fi

APK_RUNNER_APP=$1
APK_RUNNER_ANDROID_TEST=$2
RENAME_SUFFIX=$3
SHARDS_COUNT=${4:-5}

BUILDSCRIPTS_DIR=`dirname $0`

$BUILDSCRIPTS_DIR/run-android-tests-in-gcloud.sh \
    "$APK_RUNNER_APP" \
    "$APK_RUNNER_ANDROID_TEST" \
    2 \
    /tmp/junit \
    /tmp/gcloud-results \
    -"$RENAME_SUFFIX" \
    "" \
    $SHARDS_COUNT \
    model=Nexus5X,version=24 \
    model=Nexus5X,version=25 \
    model=Nexus5X,version=26 \
    model=HWMHA,version=24 \
    model=cactus,version=27 \
    model=redfin,version=30 \
    model=oriole,version=31 \
    model=oriole,version=32 \
    model=shiba,version=34
