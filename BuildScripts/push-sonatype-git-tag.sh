#!/bin/bash

set -e
set -x

function generateRcVersion () {
  local LIB_VERSION="$1"
  local LIB_NAME="$2"
  local LIB_RC_TAGS=$(git tag -l "$LIB_NAME-$LIB_VERSION-rc*")
  local RC_VERSION=1

  for TAG in $LIB_RC_TAGS; do
      if [[ "$TAG" =~ ^$LIB_NAME-$LIB_VERSION-rc([0-9]+)$ ]]; then
          RC=${BASH_REMATCH[1]}
          (( RC < RC_VERSION )) || RC_VERSION=$(( RC + 1 ))
      fi
  done

  echo $RC_VERSION
}

if [ $# -ne 1 ]; then
    SELF=`basename $0`
    echo "Usage: $SELF <GRADLE_OUTPUT_FILE>"
    exit 1
fi

REPO_OUTPUT=`cat "$1" | grep 'Created staging repository'`

if ! [[ "$REPO_OUTPUT" =~ "Created staging repository '"(.+)"'" ]]; then
    echo "Cannot parse staging repository name"
    exit 1
fi

REPO_NAME=${BASH_REMATCH[1]}

TWILSOCK_OUTPUT=`cat "$1" | grep 'Publishing twilsock v' || true`
if [[ "$TWILSOCK_OUTPUT" =~ ^Publishing\ twilsock\ v([0-9]+\.[0-9]+\.[0-9]+)$ ]]; then
    TWILSOCK_VERSION=${BASH_REMATCH[1]}
    RC_VERSION=`generateRcVersion $TWILSOCK_VERSION "twilsock"`
    git tag "twilsock-$TWILSOCK_VERSION-rc$RC_VERSION"
fi

SHARED_INTERNAL_OUTPUT=$(cat "$1" | grep 'Publishing sharedInternal v' || true)
if [[ "$SHARED_INTERNAL_OUTPUT" =~ ^Publishing\ sharedInternal\ v([0-9]+\.[0-9]+\.[0-9]+)$ ]]; then
    SHARED_INTERNAL_VERSION=${BASH_REMATCH[1]}
    RC_VERSION=$(generateRcVersion "$SHARED_INTERNAL_VERSION" "shared-internal")
    git tag "shared-internal-$SHARED_INTERNAL_VERSION-rc$RC_VERSION"
fi

SHARED_PUBLIC_OUTPUT=$(cat "$1" | grep 'Publishing sharedPublic v' || true)
if [[ "$SHARED_PUBLIC_OUTPUT" =~ ^Publishing\ sharedPublic\ v([0-9]+\.[0-9]+\.[0-9]+)$ ]]; then
    SHARED_PUBLIC_VERSION=${BASH_REMATCH[1]}
    RC_VERSION=$(generateRcVersion "$SHARED_PUBLIC_VERSION" "shared-public")
    git tag "shared-public-$SHARED_PUBLIC_VERSION-rc$RC_VERSION"
fi

SYNC_OUTPUT=`cat "$1" | grep 'Publishing sync v' || true`
CONVO_OUTPUT=`cat "$1" | grep 'Publishing convo v' || true`

if [[ ! -z "$SYNC_OUTPUT" && ! -z "$CONVO_OUTPUT" ]]; then
    git tag "sonatype/sync-convo/$REPO_NAME"
elif [[ ! -z "$SYNC_OUTPUT" ]]; then
    git tag "sonatype/sync/$REPO_NAME"
elif [[ ! -z "$CONVO_OUTPUT" ]]; then
    git tag "sonatype/convo/$REPO_NAME"
fi

git push --tags
