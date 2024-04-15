#!/bin/bash

set -e
set -x

MONOREPO_DIR=`git rev-parse --show-toplevel`
IOS_PROVISION_PROFILE="a6ad9352-2b60-48ca-9cb2-76cc2d5e0002"
FOLDER_WITH_TEST_OUTPUT="DerivedData"

cd "$MONOREPO_DIR/ios/TwilioSync"
rm -rf "$FOLDER_WITH_TEST_OUTPUT"

# Set manual signing only for TestHost target
if [ ! -z "$CIRCLECI" ]; then
    $MONOREPO_DIR/BuildScripts/set-manual-signing.rb "TestHost" $IOS_PROVISION_PROFILE TwilioSync.xcodeproj
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
