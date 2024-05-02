// swift-tools-version:5.5
import PackageDescription

let package = Package(
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
)
