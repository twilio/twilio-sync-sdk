#!/bin/bash

set -e
set -x

echo "Deleting test .dSYM files (iOS)..."
find "$1" -type f -path "*.app.dSYM/*" -delete -print
find "$1" -type f -path "*.xctest.dSYM/*" -delete -print
echo
echo "Deleting object files (iOS)..."
find "$1" -type f -path "*/Objects-normal/*" -delete -print
echo
echo "Deleting ModuleCache.noindex files (iOS)..."
find "$1" -type f -path "*/ModuleCache.noindex/*" -delete -print
echo
# Because of both release and releaseWithSymbols jobs persist sdk/build/intermediates/compile_r_class_jar/release/R.jar
# into workspace after running apiCheck. So with this conflict the workspace cannot be attached.
echo "Deleting R.jar files (Android)..."
find "$1" -type f -path "*/R.jar" -delete -print
echo
echo "Deleting other files..."
find "$1" -type f ! -name "*.so" -a ! -name "*.apk" -a ! -name "*.aar" -a ! -name "*.jar" -a ! -name "*.cmake" -a ! -name "*UT*" -a ! -name "*FT*" -a ! -name "*IT*" -a ! -path "*.xcodeproj/*" -a ! -path "*.app/*" -delete -print
echo
echo "Deleting empty folders..."
find "$1" -type d -empty -delete -print
