# rtd-sdk-cdn-pin
Scripts to fetch Messaging SDKs and then upload and pin to CDN.
Usecase is fetch the artifact from distribution system and then with help of `sdk-release-tool` put it to Twilio CDN (dev/stage/prod).

## Supported Messaging SDKs:
* twilio-sync-js
* twilio-sync-android
* twilio-chat-js
* twilio-chat-android
* twilio-conversations-js
* twilio-conversations-android

## Fetching artifacts
Fetching is made with `fetch-artifact.sh` script.
The script takes the artifact and docs from respective distribution system (npmjs or sonatype) and then places it in `artifacts/<sdk-name>/<release-version>` folder preseving structure needed for further upload to cdn with `sdk-release-tool`.

To fetch Android or iOS artifacts `mvn` should be installed and the `sonatype-settings.xml` file should be present at root of workspace to access internal repo on Sonatype.

**Usage:** `SDK_NAME=<sdk-name> RELEASE_VERSION=<release-version> ./fetch-artifact.sh` where `RELEASE_VERSION` can be `x.y.z` or `x.y.z-rcN`

**Examples:**
* `SDK_NAME=twilio-chat-js RELEASE_VERSION=3.3.0 ./fetch-artifact.sh`
* `SDK_NAME=twilio-chat-android RELEASE_VERSION=4.1.1-rc1 ./fetch-artifact.sh`

## Uploading to CDN
Uploading is made with `cdn-upload.sh` script.
Script assumes that the version of artifact is fetched to `artifacts/<sdk-name>/<release-version>` with directory structure required for `sdk-release-tool`.
Default behavior of script is dry run, no forced upload/pinning.

**Usage:** `SDK_NAME=<sdk-name> RELEASE_VERSION=<release-version> SDK_RELEASE_TOOL_HOME=<absolute-path-to-sdk-release-tool> DEPLOY_REALM=<realm> FORCE_OVERWRITE=<force-upload-and-pin?> DRY_RUN=<dry-run?> ./cdn-upload.sh`, where:
* `release-version` can be `x.y.z` or `x.y.z-rcN`
* `realm` can be `dev`, `stage`, `prod`
* `force-upload-and-pin?` is `true` or `false`, indicating should script overwrite (if already exists) files on CDN
* `dry-run?` is `true` or `false`, indicating should script actually perform actions or it should simply print out what it's going to do (dry-run)

**Examples:**
* `SDK_NAME=twilio-chat-js RELEASE_VERSION=3.3.0 SDK_RELEASE_TOOL_HOME=/home/jenkins/sdk-release-tool DEPLOY_REALM=dev DRY_RUN=true ./cdn-upload.sh`
* `SDK_NAME=twilio-chat-android RELEASE_VERSION=4.1.1-rc1 SDK_RELEASE_TOOL_HOME=/home/jenkins/sdk-release-tool DEPLOY_REALM=stage DRY_RUN=false FORCE_OVERWRITE=true ./cdn-upload.sh`

## Pinning to CDN
Pinning is made with `cd-pin.sh` script.
Default behavior of script is dry run, no forced upload/pinning.

**Usage:** `SDK_NAME=<sdk-name> RELEASE_VERSION=<release-version> SDK_RELEASE_TOOL_HOME=<absolute-path-to-sdk-release-tool> DEPLOY_REALM=<realm> PIN_RELEASE=<pin-release?> PIN_LATEST=<pin-latest?> FORCE_OVERWRITE=<force-upload-and-pin?> DRY_RUN=<dry-run?> ./cdn-pin.sh`, where:
* `release-version` can be `x.y.z` or `x.y.z-rcN`
* `realm` can be `dev`, `stage`, `prod`
* `pin-release?` is `true` or `false`, indicating should script pin release or not, i.e. pin `x.y.z` to `x.y` on CDN
* `pin-latest?` is `true` or `false`, indicating should script pin uploaded release to `latest` version code on CDN or not
* `force-upload-and-pin?` is `true` or `false`, indicating should script overwrite (if already exists) files on CDN
* `dry-run?` is `true` or `false`, indicating should script actually perform actions or it should simply print out what it's going to do (dry-run)

**Examples:**
* `SDK_NAME=twilio-chat-js RELEASE_VERSION=3.3.0 SDK_RELEASE_TOOL_HOME=/home/jenkins/sdk-release-tool DEPLOY_REALM=dev PIN_RELEASE=false PIN_LATEST=false DRY_RUN=true ./cdn-pin.sh`
* `SDK_NAME=twilio-chat-android RELEASE_VERSION=4.1.1-rc1 SDK_RELEASE_TOOL_HOME=/home/jenkins/sdk-release-tool DEPLOY_REALM=stage PIN_RELEASE=false PIN_LATEST=false DRY_RUN=false FORCE_OVERWRITE=true ./cdn-pin.sh`
