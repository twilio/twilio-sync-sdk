#!/bin/bash

if [[ -z "${DRY_RUN}" ]] ; then
    echo "DRY_RUN not set, falling back to default 'true'"
    DRY_RUN=true
fi

if [[ -z "${FORCE_OVERWRITE}" ]] ; then
    echo "FORCE_OVERWRITE not set, falling back to default 'false'"
    FORCE_OVERWRITE=false
fi

if [ "${CDN_PINNING}" = true ] ; then
    if [[ ( -z "${SDK_NAME}" ) || ( -z "${RELEASE_VERSION}" ) || ( -z "${DRY_RUN}" ) || ( -z "${PIN_RELEASE}" ) || ( -z "${PIN_LATEST}" ) || ( -z "${DEPLOY_REALM}" ) || ( -z "${SDK_RELEASE_TOOL_HOME}" )]]; then
        echo "Environment variables SDK_NAME, RELEASE_VERSION, DRY_RUN, PIN_RELEASE, PIN_LATEST, DEPLOY_REALM, SDK_RELEASE_TOOL_HOME should be provided" >&2  # write error message to stderr
        exit 1
    fi
fi

if [ "${CDN_UPLOADING}" = true ] ; then
    if [[ ( -z "${SDK_NAME}" ) || ( -z "${RELEASE_VERSION}" ) || ( -z "${DRY_RUN}" ) || ( -z "${DEPLOY_REALM}" ) || ( -z "${SDK_RELEASE_TOOL_HOME}" )]]; then
        echo "Environment variables SDK_NAME, RELEASE_VERSION, DRY_RUN, DEPLOY_REALM, SDK_RELEASE_TOOL_HOME should be provided" >&2  # write error message to stderr
        exit 1
    fi
fi

if [[ ! "${RELEASE_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-rc[0-9]+)?$ ]]; then
    echo "Release version ${RELEASE_VERSION} is not release or release candidate" >&2  # write error message to stderr
    exit 1
fi

if [ "${CDN_UPLOADING}" = true ] ; then
    ARTIFACTS_DIR=${WORKING_DIR}/artifacts/${SDK_NAME}/${RELEASE_VERSION}
    if [ ! -d "${ARTIFACTS_DIR}" ]; then
        echo "Directory ${ARTIFACTS_DIR} does not exist, seems like artifact is not fetched" >&2  # write error message to stderr
        exit 1
    fi
fi

if [ ! -d "${SDK_RELEASE_TOOL_HOME}" ]; then
    echo "Directory ${SDK_RELEASE_TOOL_HOME} does not exist, seems like no SDK release tool is installed" >&2  # write error message to stderr
    exit 1
fi

SDK_RELEASE_TOOL_OPTIONS="--${DEPLOY_REALM} ${SDK_NAME} ${RELEASE_VERSION}"
if [ "${DRY_RUN}" = true ] ; then
    echo "Found DRY_RUN set to TRUE"
    SDK_RELEASE_TOOL_OPTIONS="--dry-run ${SDK_RELEASE_TOOL_OPTIONS}"
elif [ "${DRY_RUN}" = false ] ; then
    echo "Found DRY_RUN set to FALSE"
else
    echo "DRY_RUN is not 'true' or 'false'. Exiting" >&2  # write error message to stderr
    exit 1
fi

if [ "${FORCE_OVERWRITE}" = true ] ; then
    echo "Found FORCE_OVERWRITE set to TRUE"
    SDK_RELEASE_TOOL_OPTIONS="--force ${SDK_RELEASE_TOOL_OPTIONS}"
elif [ "${FORCE_OVERWRITE}" = false ] ; then
    echo "Found FORCE_OVERWRITE set to FALSE"
else
    echo "FORCE_OVERWRITE is not 'true' or 'false'. Exiting" >&2  # write error message to stderr
    exit 1
fi


cd "$SDK_RELEASE_TOOL_HOME"
