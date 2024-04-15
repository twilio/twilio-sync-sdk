sdk-release-tool
================

sdk-release-tool allows you to list, upload, and update new Twilio SDK
releases to [media.twiliocdn.com](//media.twiliocdn.com). These releases are
stored in S3 and fronted by CloudFlare. Refer to the [2.0 SDK URLs]
(//wiki.hq.twilio.com/display/SDK/2.0+SDK+URLs) for more information.

- [Installation](#installation)
- [Credentials](#credentials)
- [Schemas](#schemas)
- [Version Numbers](#version-numbers)
- [Do not use latest/ URLs with JavaScript SDKs](#do-not-use-latest-urls-with-javascript-sdks)
- [Usage](#usage)
  - [list](#list)
  - [upload](#upload)
  - [pin](#pin)
  - [pin-latest](#pin-latest)
  - [delete](#delete)
  - [download](#download)
  - [unpin](#unpin)
  - [unpin-latest](#unpin-latest)

Installation
------------

Install sdk-release-tool by running

```
$ make
```

sdk-release-tool will check for files cdn-sdki.prod.json, cdn-sdki.stage.json,
and cdn-sdki.dev.json in the sdk-release-tool directory. If any of these are
not found, sdk-release-tool will prompt you to provide them:

```
$ make

Please provide the AWS access key for cdn-sdki in (prod).

AWS_ACCESS_KEY_ID: █
```

If you would rather use credentials set in environment variables and would like to skip the prompt. Install with the following flag.

```
$ make SKIP_CREDENTIALS=1 install
```

If you would like to use environment variables as credentials then set the following accordingly

```
AWS_DEV_ACCESS_KEY_ID
AWS_DEV_SECRET_ACCESS_KEY
AWS_STAGE_ACCESS_KEY_ID
AWS_STAGE_SECRET_ACCESS_KEY
AWS_PROD_ACCESS_KEY_ID
AWS_PROD_SECRET_ACCESS_KEY
```

See [Credentials](#credentials) for information.

Credentials
-----------

sdk-release-tool updates Amazon S3 buckets. You need the cdn-sdki IAM user
credentials to do this. There is a cdn-sdki IAM user credential for dev, stage,
and prod. A member of the RTD or SDK Teams should be able to share these
securely with you; if not, file an [iRequest](https://issues.corp.twilio.com)
to gain access.

Schemas
-------

Once installed, you can start using sdk-release-tool with any supported SDK.
SDKs can be supported by adding JSON schema files describing the S3 directory
structure for each.

| SDK or SDK Artifacts                                                                   | JSON Schema File                                                       |
|:-------------------------------------------------------------------------------------- |:---------------------------------------------------------------------- |
| [twilio-client-android](//code.hq.twilio.com/client/twilioclient-android-sdk)          | [twilio-client-android.json](twilio-client-android.json)               |
| [twilio-client-android](//code.hq.twilio.com/client/twilioclient-android-sdk)      | [twilio-client-android-aar.json](twilio-client-android.json)               |
| [twilio-voice-android](//code.hq.twilio.com/client/twilioclient-android-sdk)           | [twilio-voice-android.json](twilio-voice-android.json)                 |
| [twilio-chat-ios](//code.hq.twilio.com/client/twilio-chat-ios)                         | [twilio-chat-ios.json](twilio-chat-ios.json)           |
| [twilio-client-ios](code.hq.twilio.com/client/twilioclient-ios-sdk)                    | [twilio-client-ios.json](twilio-client-ios.json)                       |
| [twilio-common-android](//code.hq.twilio.com/client/twilio-common-android)             | [twilio-common-android.json](twilio-common-android.json)               |
| [twilio-common-ios](//code.hq.twilio.com/client/twilio-common-ios)                     | [twilio-common-ios.json](twilio-common-ios.json)                       |
| [twilio-common.js](//github.com/twilio/twilio-common)                                  | [twilio-common-js.json](twilio-common-js.json)                         |
| [twilio-conversations-android](//code.hq.twilio.com/client/signal-sdk-android)         | [twilio-conversations-android.json](twilio-conversations-android.json) |
| [twilio-conversations-ios](//code.hq.twilio.com/client/signal-sdk-ios)                 | [twilio-conversations-ios.json](twilio-conversations-ios.json)         |
| [twilio-conversations.js](//code.hq.twilio.com/client/twilio-rtc-conversations-js)     | [twilio-conversations-js.json](twilio-conversations-js.json)           |
| [twilio-sync-ios](//code.hq.twilio.com/client/twilio-sync-ios)                         | [twilio-sync-ios.json](twilio-sync-ios.json)                           |
| [twilio-ip-messaging-android](//code.hq.twilio.com/client/twilio-ip-messaging-android) | [twilio-ip-messaging-android.json](twilio-ip-messaging-android.json)   |
| [twilio-ip-messaging-android (Maven to CDN)](//code.hq.twilio.com/client/twilio-ip-messaging-android) | [twilio-ip-messaging-android-maven.json](twilio-ip-messaging-android-maven.json)   |
| [twilio-ip-messaging-ios](//code.hq.twilio.com/client/twilio-ip-messaging-ios)         | [twilio-ip-messaging-ios.json](twilio-ip-messaging-ios.json)           |
| [twilio-ip-messaging.js](//code.hq.twilio.com/client/twilio-rtc-ip-messaging-js)       | [twilio-ip-messaging-js.json](twilio-ip-messaging-js.json)             |
| [twilio.js](//code.hq.twilio.com/client/twiliojs) >=1.3                                | [twilio-client-js.json](twilio-client-js.json)                         |
| [twilio.js sounds](//code.hq.twilio.com/client/twilio-client-sounds-js)                | [twilio-client-sounds-js.json](twilio-client-sounds-js.json)           |
| [twilio-video.js](//code.hq.twilio.com/client/twilio-rtc-conversations-js)             | [twilio-video-js.json](twilio-video-js.json)                           |
| [twilio-video-cpp](//code.hq.twilio.com/client/signal-sdk-core)                        | [twilio-video-cpp.json](twilio-video-cpp.json)                         |
| [twilio-voice-ios](//code.hq.twilio.com/client/twilioclient-ios-sdk)                   | [twilio-voice-ios.json](twilio-voice-ios.json)                         |
| [twilio-accessmanager-ios](//code.hq.twilio.com/client/twilio-accessmanager-ios)       | [twilio-accessmanager-ios.json](twilio-accessmanager-ios.json)         |
| [twilio-auth-ios](//https://code.hq.twilio.com/authy/authy-sdk-ios)                    | [twilio-auth-ios.json](twilio-auth-ios.json)                           |
| [twilio-authenticator-ios](//https://code.hq.twilio.com/authy/authy-sdk-ios)           | [twilio-authenticator-ios.json](twilio-authenticator-ios.json)         |
| [twilio-taskrouter.js](//https://code.hq.twilio.com/twilio/twilio-wds-js)              | [twilio-taskrouter.json](twilio-taskrouter.json)                       |


Version Numbers
---------------

SDKs should use [Semantic Versioning (SemVer)](http://semver.org) of the form

```
1.2.3-dev+build
```

Version numbers of the form

```
1.2.3.b4-deadbee
```

are deprecated, but sdk-release-tool will continue supporting them for the
time being.

Do not use latest/ URLs with JavaScript SDKs
--------------------------------------------

Do not specify latest/ URLs or use the [pin-latest](#pin-latest) command with
JavaScript SDKs. If a customer uses such a URL in a &lt;script&gt; tag, then
their application will break as soon as we release a breaking change. For this
reason, latest/ URLs are an anti-pattern and disallowed for JavaScript SDKs.

Usage
-----

For a new release, a typical workflow is

1. [list](#list) existing versions (and ensure they are what you expect)
2. [upload](#upload) your new version
3. [pin](#pin) the new version (assuming it is not a pre-release)

Note that all of the commands documented below accept a realm flag, i.e.

* `--dev`
* `--stage`
* `--prod`

For the sake of example, all of the commands below are written as if modifying
dev. You can run the same commands against stage or prod by passing a different
realm flag.

### list

List the version numbers of uploaded product artifacts and any pinned
major/minor pairs or "latest" versions. For example, the following shows 6
artifacts for `$product-js` in dev, 1 of which is a pre-release version, and two
pinned versions, v1.0 and v2.0; finally, 2.0.1 is pinned "latest":

```
$ ./list $product-js --dev
1.0.0
1.0.1
1.0.2 <- v1.0
2.0.0-dev
2.0.0
2.0.1 <- v2.0 (latest)
```

### upload

Upload product artifacts to a version number. For example, the following
uploads artifacts for `$product-js` 1.2.3 to dev:

```
$ ./upload $product-js 1.2.3 $source_folder --dev
dist/$product.js -> sdk/js/$product/releases/1.2.3/$product.js
```

Pass `--dry-run` to see what artifacts would be uploaded.

By default, the same artifact may not be uploaded twice. To override this
behavior, pass `-f` or `--force`.

### pin

Pin a major/minor pair to a version number. For example, the following pins
v1.0 to version 1.0.1 in dev:

```
$ ./pin $product-js 1.0.1 --dev
sdk/js/$product/v1.0/ -> sdk/js/$product/releases/1.0.1/
```

Pass `--dry-run` to see what S3 Key redirects would be updated.

By default, a pre-release version cannot be pinned. To override this
behavior, pass `-f` or `--force`.

### pin-latest

Pin a "latest" to a version number. For example, the following pins
"latest" to version 1.0.1 in dev:

```
$ ./pin-latest $product-js 1.0.1 --dev
sdk/js/$product/latest/ -> sdk/js/$product/releases/1.0.1/
```

Pass `--dry-run` to see what S3 Key redirects would be updated.

By default, a pre-release version cannot be pinned. To override this
behavior, pass `-f` or `--force`.

### download

Download product artifacts from a version number. For example, the following
downloads artifacts for `$product-js` 1.2.3 to /tmp from dev:

```
$ ./download $product-js 1.2.3 --dev
sdk/js/$product/releases/1.2.3/$product.js -> /tmp/dist/$product.js
```

Pass `--dry-run` to see what files would be downloaded.

### delete

_You should not need to use this!_

Delete product artifacts at a version number. For example, the following
deletes 1.2.3 from dev:

```
$ ./delete $product-js 1.2.3 --dev
sdk/js/$product/releases/1.2.3/$product.js
```

Pass `--dry-run` to see what files would be deleted.

By default, a pinned version cannot be deleted. To override this behavior,
pass `-f` or `--force`.

By default, every file to be deleted requires confirmation. To override this behavior, pass `-s` or `--silent`

### unpin

_You should not need to use this!_

Unpin a major/minor pair from a version number. For example, if v1.0 were
pinned to 1.0.1, the following would unpin it in dev:

```
./unpin $product-js 1.0.1 --dev
```

You almost _never_ need to use this. Instead, refer to [pin](#pin).

Pass `--dry-run` to see what S3 Key redirects would be updated.

### unpin-latest

_You should not need to use this!_

Unpin a "latest" from a version number. For example, if "latest" were
pinned to 1.0.1, the following would unpin it in dev:

```
./unpin-latest $product-js 1.0.1 --dev
```

You almost _never_ need to use this. Instead, refer to [pin](#pin).

Pass `--dry-run` to see what S3 Key redirects would be updated.
