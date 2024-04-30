// swift-tools-version:5.5
import PackageDescription

let package = Package(
{{#sdk_ios}}{{! Render this part for Conversations and Sync products }}
    name: "Twilio{{ PRODUCT }}Client",
    platforms: [
        .iOS(.v13)
    ],
    products: [
        .library(
            name: "Twilio{{ PRODUCT }}Client",
            targets: ["Twilio{{ PRODUCT }}ClientTarget"]),
    ],
    dependencies: [
        .package(
            name: "TwilioTwilsockLib",
            url: "{{ &TWILSOCK_PACKAGE_REPO }}",
            .upToNextMajor(from: "{{ TWILSOCK_VERSION }}"))
    ],
    targets: [
        .target(
          name: "Twilio{{ PRODUCT }}ClientTarget",
          dependencies: [
              .target(name: "Twilio{{ PRODUCT }}Client"), 
              .product(name: "TwilioTwilsockLib", package: "TwilioTwilsockLib")
          ],
          path: "Dummy"
        ),
        .binaryTarget(
            name: "Twilio{{ PRODUCT }}Client",
            url: "{{ &PACKAGE_URL }}",
            checksum: "{{ PACKAGE_CHECKSUM }}"
        ),
    ]
{{/sdk_ios}}
{{#twilsock}}{{! Render this part for Twilsock product }}
    name: "TwilioTwilsockLib",
    platforms: [
        .iOS(.v13)
    ],
    products: [
        .library(
            name: "TwilioTwilsockLib",
            targets: ["TwilioTwilsockLibTarget"]),
    ],
    targets: [
        .target(
          name: "TwilioTwilsockLibTarget",
          dependencies: [
              .target(name: "TwilioTwilsockLib"),
              .target(name: "TwilioCommonLib"),
              .target(name: "TwilioStateMachine") 
          ],
          path: "Dummy"
        ),
        .binaryTarget(
            name: "TwilioTwilsockLib",
            url: "{{ &TWILSOCK_URL }}",
            checksum: "{{ TWILSOCK_CHECKSUM }}"
        ),
        .binaryTarget(
            name: "TwilioCommonLib",
            url: "{{ &COMMONLIB_URL }}",
            checksum: "{{ COMMONLIB_CHECKSUM }}"
        ),
        .binaryTarget(
            name: "TwilioStateMachine",
            url: "{{ &STATEMACHINE_URL }}",
            checksum: "{{ STATEMACHINE_CHECKSUM }}"
        )
    ]
{{/twilsock}}
{{#sdk_kotlin_ios}}{{! Render this part for kotlin native Conversations and Sync products }}
    name: "Twilio{{ PRODUCT }}",
    platforms: [
        .iOS(.v13)
    ],
    products: [
        .library(
            name: "Twilio{{ PRODUCT }}",
            targets: ["Twilio{{ PRODUCT }}Target"]),
    ],
    targets: [
        .target(
          name: "Twilio{{ PRODUCT }}Target",
          dependencies: [
              .target(name: "Twilio{{ PRODUCT }}"),
              .target(name: "Twilio{{ PRODUCT }}Lib"),
          ],
          path: "Dummy"
        ),
        .binaryTarget(
            name: "Twilio{{ PRODUCT }}",
            url: "{{ &SDK_URL }}",
            checksum: "{{ SDK_CHECKSUM }}"
        ),
        .binaryTarget(
            name: "Twilio{{ PRODUCT }}Lib",
            url: "{{ &LIB_URL }}",
            checksum: "{{ LIB_CHECKSUM }}"
        ),
    ]
{{/sdk_kotlin_ios}}
)
