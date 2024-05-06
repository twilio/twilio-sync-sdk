# Twilio Sync SDK

[![CircleCI](https://circleci.com/gh/twilio/twilio-sync-sdk.svg?style=shield&&circle-token=CCIPRJ_GaffVsFYDvP4hPPWrNjFLz_7d742d3cd4851925d74198732fdf7c41ecacb2b1)](https://app.circleci.com/pipelines/github/twilio/twilio-sync-sdk)

Latest available SDK version: 

- Android [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.twilio/sync-android-kt/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.twilio/conversations-android)
- iOS [![SwiftPM](https://img.shields.io/github/v/tag/twilio/twilio-sync-ios?label=SPM&color=4dc51d)](https://github.com/twilio/twilio-sync-ios)


Twilio Sync is Twilio's state synchronization service, offering two-way real-time communication between browsers, mobiles, and the cloud. Using Sync helps you avoid building all the real-time infrastructure to do this yourself.

This repo contains source code for the Twilio Sync SDKs for Android and iOS.
See [official documentation](https://www.twilio.com/docs/sync) for more information about the Twilio Sync product.

## Content

- [Using in your projects](#using-in-your-projects)
  - [Android](#android)
    - [Compatibility](#compatibility)
    - [Example Usage](#example-usage)
  - [iOS](#ios)
- [Build SDK](#build-sdk)
  - [Android](#build-sync-sdk-for-android)
    - [From command line](#from-command-line)
    - [From Android Studio](#from-android-studio)
  - [iOS](#build-sync-sdk-for-ios)
    - [From command line](#from-command-line-1)
    - [From xCode](#from-xcode)
- [Build documentation](#build-documentation)
  - [Android](#android-1)
  - [iOS](#ios-1)
- [License](#license)


## Using in your projects

### Android

Android Sync SDK provides two modules:

- `sync-android-kt` is targeted to be used from Kotlin apps. `SyncClient` exposes `suspend` methods to perform async operations. To use `SyncClient` from your Kotlin app, add the following dependency to your project:

```groovy
dependencies {
    implementation "com.twilio:sync-android-kt:4.0.0"
}
```
See [SyncClient documentation](https://sdk.twilio.com/android/sync/releases/4.0.0/docs/sync-android-kt/-sync%20-android%20-s-d-k/com.twilio.sync.client/-sync-client/index.html) for details on how to create a `SyncClient`.

- `sync-android-java` is targeted to be used from Java apps. `SyncClientJava` exposes methods which receive listeners to perform async operations. To use `SyncClientJava` from your Java app, add the following dependency to your project:

```groovy
dependencies {
    implementation "com.twilio:sync-android-java:4.0.0"
}
```
See [SyncClientJava documentation](https://sdk.twilio.com/android/sync/releases/4.0.0/docs/sync-android-java/-sync%20-android%20-s-d-k%20for%20-java/com.twilio.sync.client.java/-sync-client-java/index.html) for details on how to create a `SyncClientJava`.

#### Compatibility

The Sync SDK now exposes dates using `kotlinx.datetime.Instant`. If you are targeting Android devices running **below API 26**, you will need to enable `coreLibraryDesugaring` to ensure compatibility. Follow the steps below:

```groovy
android { 
    compileOptions { 
        coreLibraryDesugaringEnabled true 
    } 
}

dependencies {
    "com.android.tools:desugar_jdk_libs:1.2.2"
}
```

For more information on using `kotlinx.datetime` in your projects, visit the [official documentation](https://github.com/Kotlin/kotlinx-datetime#using-in-your-projects).

#### Example Usage

Here's an example of how to use the Sync SDK for Android:

```kotlin
    val syncClient = SyncClient(applicationContext) { requestToken() }

    // Create a new map
    val map = syncClient.maps.create()

    // Listen for item-added events flow
    map.events.onItemAdded
        .onEach { println("Item added: ${it.key}") }
        .launchIn(MainScope())

    // Declare custom data type
    @kotlinx.serialization.Serializable
    data class MyData(val data: Int)

    // Add 10 items to the map
    repeat(10) { index ->
        map.setItem("key$index", MyData(index))
    }

    // Read all items from the map
    for (item in map) {
        val myData = item.data<MyData>()
        println("Key: ${item.key}, Value: ${myData.data}")
    }

    // Remove the map
    map.removeMap()
```

### iOS

iOS SDK based on the code base from this repo is still in testing and is not published yet.
We'll update this section after publishing the SDK.
For now please use the currently [latest available](https://github.com/twilio/twilio-sync-ios) iOS SDK.

## Build SDK

Download and setup Android SDK from Google if you don't have it yet

```
ANDROID_SDK_ROOT="$HOME/android-sdk"
LATEST_DIR="$ANDROID_SDK_ROOT/cmdline-tools/latest"

curl https://dl.google.com/android/repository/commandlinetools-mac-7302050_latest.zip --output /tmp/android-commandlinetools.zip
unzip -q -o /tmp/android-commandlinetools.zip -d /tmp
mv /tmp/cmdline-tools/* "$LATEST_DIR"

yes | "$LATEST_DIR/bin/sdkmanager" --licenses

# Put local.properties to the root of the twilio-sync-sdk repo.
echo "sdk.dir=$ANDROID_SDK_ROOT" >> "./local.properties"
```

### Build Sync SDK for Android

#### From command line

```
./gradlew assembleRelease
```

#### From Android Studio

Open `./build.gradle` in Android Studio and build as you would ordinarily.

### Build Sync SDK for iOS

#### From command line

```
# Build kotlin native library
./gradlew sync-android-kt:assembleTwilioSyncLibReleaseXCFramework

# Build swift wrapper for the library
cd "./ios/TwilioSync"

xcodebuild archive \
    -project "TwilioSync.xcodeproj" \
    -scheme TwilioSync \
    -configuration "Release" \
    -destination "generic/platform=iOS" \
    -derivedDataPath DerivedData \
    -archivePath "output/archives/TwilioSync-iphoneos.xcarchive" \
    SKIP_INSTALL=NO \
    BUILD_LIBRARY_FOR_DISTRIBUTION=YES

xcodebuild archive \
    -project "TwilioSync.xcodeproj" \
    -scheme TwilioSync \
    -configuration "Release" \
    -destination "generic/platform=iOS Simulator" \
    -derivedDataPath DerivedData \
    -archivePath "output/archives/TwilioSync-iphonesimulator.xcarchive" \
    SKIP_INSTALL=NO \
    BUILD_LIBRARY_FOR_DISTRIBUTION=YES

xcodebuild -create-xcframework \
    -framework "output/archives/TwilioSync-iphoneos.xcarchive/Products/Library/Frameworks/TwilioSync.framework" \
    -framework "output/archives/TwilioSync-iphonesimulator.xcarchive/Products/Library/Frameworks/TwilioSync.framework" \
    -output "output/xcframeworks/TwilioSync.xcframework"
```

#### From xCode

Open `./ios/TwilioSync/TwilioSync.xcodeproj` in xCode and build as you would ordinarily.

## Build documentation

### Android

For Kotlin
```
./gradlew packageDocs -PdocsSyncKotlin
```

For Java
```
./gradlew packageDocs -PdocsSyncJava
```

To see the built documentation open `./build/dokka/htmlMultiModule/index.html`

### iOS

1. First [Build Sync SDK for iOS](#build-sync-sdk-for-ios)
2. Build documentation:

```
cd "./ios/TwilioSync"

xcodebuild docbuild -scheme TwilioSync \
   -destination "generic/platform=iOS" \
   -configuration "Release" \
   -derivedDataPath DerivedData
```

To see the built documentation open `./ios/TwilioSync/DerivedData/Build/Products/Release-iphoneos/TwilioSync.doccarchive`

## License

    Copyright 2024 Twilio, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
