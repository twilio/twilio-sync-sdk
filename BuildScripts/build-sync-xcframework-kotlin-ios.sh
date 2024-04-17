#!/bin/bash

set -e
set -x

MONOREPO_DIR=`git rev-parse --show-toplevel`
OUTPUT_DIR="output"

cd "$MONOREPO_DIR/ios/TwilioSync"
rm -rf "$OUTPUT_DIR"

xcodebuild archive \
    -project "TwilioSync.xcodeproj" \
    -scheme TwilioSync \
    -configuration "Release" \
    -destination "generic/platform=iOS" \
    -derivedDataPath DerivedData \
    -archivePath "$OUTPUT_DIR/archives/TwilioSync-iphoneos.xcarchive" \
    SKIP_INSTALL=NO \
    BUILD_LIBRARY_FOR_DISTRIBUTION=YES

xcodebuild archive \
    -project "TwilioSync.xcodeproj" \
    -scheme TwilioSync \
    -configuration "Release" \
    -destination "generic/platform=iOS Simulator" \
    -derivedDataPath DerivedData \
    -archivePath "$OUTPUT_DIR/archives/TwilioSync-iphonesimulator.xcarchive" \
    SKIP_INSTALL=NO \
    BUILD_LIBRARY_FOR_DISTRIBUTION=YES

xcodebuild -create-xcframework \
    -framework "$OUTPUT_DIR/archives/TwilioSync-iphoneos.xcarchive/Products/Library/Frameworks/TwilioSync.framework" \
    -framework "$OUTPUT_DIR/archives/TwilioSync-iphonesimulator.xcarchive/Products/Library/Frameworks/TwilioSync.framework" \
    -output "$OUTPUT_DIR/xcframeworks/TwilioSync.xcframework"
