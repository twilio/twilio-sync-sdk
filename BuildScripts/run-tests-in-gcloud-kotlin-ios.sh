#!/bin/bash

set -e
set -x

if [ $# -lt 3 ]; then
    SELF=`basename $0`
    echo "Usage: $SELF <TEST_ARCHIVE> <RESULTS_DIR> <ARTIFACTS_DIR>"
    exit 1
fi

TEST_ARCHIVE=$1
RESULTS_DIR=$2
ARTIFACTS_DIR=$3

DEVICE="model=iphone13pro,version=15.2"
LOG_FILE="$RESULTS_DIR/gcloud_output.txt"

rm -rf "$RESULTS_DIR"
mkdir -p "$RESULTS_DIR"
mkdir -p "$ARTIFACTS_DIR"

gcloud --version

gcloud firebase test ios run \
        --test "$TEST_ARCHIVE" \
        --device "$DEVICE" \
        --num-flaky-test-attempts=2 \
        --timeout=30m \
        --no-record-video 2>&1 | tee $LOG_FILE

EXIT_CODE=${PIPESTATUS[0]}
echo "gcloud finished with code $EXIT_CODE"

GS_FOLDER=`cat $LOG_FILE | grep "Raw results will be stored in your GCS bucket at" | sed "s/.*\/\(test-lab-.*\)\/.*/\1/"`
echo GS_FOLDER=$GS_FOLDER

gsutil -m cp -r "gs://$GS_FOLDER/*" "$RESULTS_DIR" || EXIT_CODE=$?
echo "gsutil finished with code $?"

find "$RESULTS_DIR" -name "*.zip" -delete
tar czf "$ARTIFACTS_DIR/gcloud_results.zip" -C "$RESULTS_DIR" .

# remove merged results, keep .xml only in per-device subfolders, otherwise circleci displays doubled tests count
rm "$RESULTS_DIR"/*test_results_merged.xml

# keep only .xml files, otherwise circleci won't display a test summary
find "$RESULTS_DIR" -type f ! -name "*.xml" -delete
find "$RESULTS_DIR" -type d -empty -delete

exit $EXIT_CODE
