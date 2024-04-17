#!/bin/bash

set -e
set -x

MONOREPO_DIR=`git rev-parse --show-toplevel`
IOS_SCRIPTS_DIR="$MONOREPO_DIR/sdk/ios/Scripts"
FOLDER_WITH_TEST_OUTPUT="DerivedData"

cd "$MONOREPO_DIR/root-projects/ios/TwilioSync"
rm -rf "$FOLDER_WITH_TEST_OUTPUT"

# Set manual signing only for TestHost target
if [ ! -z "$CIRCLECI" ]; then
    source "$IOS_SCRIPTS_DIR/env-common.sh"
    $IOS_SCRIPTS_DIR/set-manual-signing.rb "TestHost" $IOS_PROVISION_PROFILE TwilioSync.xcodeproj
fi

$MONOREPO_DIR/BuildScripts/generate-test-constants-kotlin-ios.sh

xcodebuild -project TwilioSync.xcodeproj \
   -scheme TwilioSync \
   -configuration "Release" \
   -destination "generic/platform=iOS" \
   -derivedDataPath "$FOLDER_WITH_TEST_OUTPUT" \
   -sdk iphoneos \
   ENABLE_TESTABILITY=YES \
   build-for-testing

cd "$FOLDER_WITH_TEST_OUTPUT/Build/Products"
zip -r TwilioSyncTests.zip Release-iphoneos *.xctestrun
