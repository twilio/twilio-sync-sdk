#!/bin/bash

set -e
set -x

if [ $# -lt 2 ]; then
    SELF=`basename $0`
    echo "Usage: $SELF <PROJECT_NAME> <HOSTING_BASE_PATH>"
    exit 1
fi

PROJECT_NAME=$1
HOSTING_BASE_PATH=$2

MONOREPO_DIR=`git rev-parse --show-toplevel`

cd "$MONOREPO_DIR/root-projects/ios/$PROJECT_NAME"

xcodebuild docbuild -scheme $PROJECT_NAME \
   -destination "generic/platform=iOS" \
   -configuration "Release" \
   -derivedDataPath DerivedData \
   DOCC_HOSTING_BASE_PATH="$HOSTING_BASE_PATH"
