#!/bin/bash

# Usage: fetchFromSonatype <artifacts-dir> <sonatypeArtifactId> <release-version> <docs-type>
function fetchFromSonatype() {
  local RESULT_ARTIFACTS_DIR=$1
  local SONATYPE_REPO_ID=$2
  local SONATYPE_ARTIFACT_ID=$3
  local SONATYPE_RELEASE_VERSION=$4

  if [[ ("${SONATYPE_RELEASE_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$) || ("${SONATYPE_RELEASE_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+-rc[0-9]+$) ]]; then
    REMOTE_REPOSITORY="sonatype-twilio::::https://oss.sonatype.org/content/repositories/$SONATYPE_REPO_ID"
    ARTIFACT="com.twilio:${SONATYPE_ARTIFACT_ID}:${SONATYPE_RELEASE_VERSION}:jar:dokka"
    ARTIFACT_FILENAME="$SONATYPE_ARTIFACT_ID-$SONATYPE_RELEASE_VERSION-dokka.jar"
  else
    echo "Version ${SONATYPE_RELEASE_VERSION} is not Release or Release Candidate" >&2  # write error message to stderr
    exit 1
  fi
  echo "==== Fetching package $SONATYPE_ARTIFACT_ID version $SONATYPE_RELEASE_VERSION from Sonatype $REMOTE_REPOSITORY ===="

  mvn dependency:get -settings sonatype-settings.xml -DremoteRepositories="$REMOTE_REPOSITORY" -Dartifact="$ARTIFACT" -Dtransitive=false || exit 1
  
  mkdir -p "$RESULT_ARTIFACTS_DIR/sdk/build/docs/dokka/$SONATYPE_ARTIFACT_ID"
  unzip "$HOME/.m2/repository/com/twilio/$SONATYPE_ARTIFACT_ID/$SONATYPE_RELEASE_VERSION/$ARTIFACT_FILENAME" -d "$RESULT_ARTIFACTS_DIR/sdk/build/docs/dokka/$SONATYPE_ARTIFACT_ID" || exit 1
}
