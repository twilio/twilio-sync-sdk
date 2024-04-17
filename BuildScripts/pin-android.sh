#!/bin/bash

set -e
set -x

publish () {
    export SDK_NAME="$1"
    export RELEASE_VERSION="$2"

    ./fetch-artifact.sh
    ./cdn-upload.sh
    ./cdn-pin.sh
}

MONOREPO_DIR=`git rev-parse --show-toplevel`

export SONATYPE_REPO_ID=`$MONOREPO_DIR/BuildScripts/parse-sonatype-git-tag.sh`
export SDK_RELEASE_TOOL_HOME="$MONOREPO_DIR/tools/sdk-release-tool"
export DEPLOY_REALM=prod
export FORCE_OVERWRITE=false
export DRY_RUN=false
export PIN_RELEASE=true
export PIN_LATEST=true

cd "$MONOREPO_DIR/tools/rtd-sdk-cdn-pin"

if [[ "$CIRCLE_TAG" =~ ^release-sync-android-([0-9]+\.[0-9]+\.[0-9]+)$ ]]; then
    publish "twilio-sync-android" "${BASH_REMATCH[1]}"
elif [[ "$CIRCLE_TAG" =~ ^release-convo-android-([0-9]+\.[0-9]+\.[0-9]+)$ ]]; then
    publish "twilio-conversations-android" "${BASH_REMATCH[1]}"
elif [[ "$CIRCLE_TAG" =~ ^release-sync-android-([0-9]+\.[0-9]+\.[0-9]+)-convo-android-([0-9]+\.[0-9]+\.[0-9]+)$ ]]; then
    publish "twilio-sync-android" "${BASH_REMATCH[1]}"
    publish "twilio-conversations-android" "${BASH_REMATCH[2]}"
elif [[ "$CIRCLE_TAG" =~ ^release-convo-android-([0-9]+\.[0-9]+\.[0-9]+)-sync-android-([0-9]+\.[0-9]+\.[0-9]+)$ ]]; then
    publish "twilio-conversations-android" "${BASH_REMATCH[1]}"
    publish "twilio-sync-android" "${BASH_REMATCH[2]}"
else
    echo "Error parsing CIRCLE_TAG: $CIRCLE_TAG"
    exit 1  
fi
