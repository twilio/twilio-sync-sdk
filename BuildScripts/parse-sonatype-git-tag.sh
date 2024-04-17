#!/bin/bash

set -e
set -x

if [[ "$CIRCLE_TAG" =~ ^release-(convo|sync)-android-[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
	PRODUCT=${BASH_REMATCH[1]}
elif [[ "$CIRCLE_TAG" =~ ^release-sync-android-[0-9]+\.[0-9]+\.[0-9]+-convo-android-[0-9]+\.[0-9]+\.[0-9]+$ \
	 || "$CIRCLE_TAG" =~ ^release-convo-android-[0-9]+\.[0-9]+\.[0-9]+-sync-android-[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
 	PRODUCT="sync-convo"
else
    echo "Error parsing CIRCLE_TAG: $CIRCLE_TAG"
    exit 1	
fi

SONATYPE_TAG=`git tag --points-at HEAD "sonatype/$PRODUCT/*"`

if ! [[ "$SONATYPE_TAG" =~ ^sonatype\/$PRODUCT\/([^\/]+)$ ]]; then
	echo "Cannot parse sonatype tag. Exactly one tag starting with 'sonatype/$PRODUCT/*' expected"
	exit 1
fi

REPO_NAME=${BASH_REMATCH[1]}
echo "$REPO_NAME"
