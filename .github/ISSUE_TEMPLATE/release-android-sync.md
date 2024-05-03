---
name: Sync Android SDK release
about: Sync Android SDK release checklist
title: "[Release] Sync Android SDK <VERSION>"
labels: RELEASE
assignees: ''

---

Please check off the following release steps as you go:

- [ ] Release candidate build is tested by TestDevLab team
- [ ] Change logs are generated and edited appropriately in the [GutHub's Releases](https://github.com/twilio/rtd-sdk-monorepo/releases)
- [ ] Change logs are submitted to the [docs](https://github.com/twilio-internal/docs) repo for approval: [Android Sync SDK changelog](https://github.com/twilio-internal/docs/pull/1679)[^1]
    - [ ] Change logs are reviewed by at least two developers (approved with 👍 reaction) and one product manager (approved with 🚀 reaction)

Close this issue after:
- [ ] Release candidate build is promoted to Release
- [ ] Release is published to:
    - [ ] Twilio CDN: 
        - [[Documentation for Kotlin]](https://media.twiliocdn.com/sdk/android/sync/releases/4.0.0/docs/sync-android-kt)
        - [[Documentation for Java]](https://media.twiliocdn.com/sdk/android/sync/releases/4.0.0/docs/sync-android-java/)
    - [ ] MavenCentral repositories for Sync SDK:
        - [ ] [Sync Android SDK For Kotlin](https://central.sonatype.com/artifact/com.twilio/sync-android-kt)
        - [ ] [Sync Android SDK For Java](https://central.sonatype.com/artifact/com.twilio/sync-android-java)
- [ ] The changelog has been published: [Sync Android SDK changelog](https://www.twilio.com/docs/sync/android/changelog)
- [ ] "[Download and install the SDKs](https://www.twilio.com/docs/sync/sync-sdk-download)" page on twilio.com is updated with new version and new links in page's code samples/guide
    + [ ] The page has been published
- [ ] "[Versioning and Support Lifecycle](https://www.twilio.com/docs/sync/versioning-and-support-lifecycle)" page on twilio.com is updated if this was major version release
    + [ ] The page has been published
- [ ] [Airtable "SDK eos/eol dates"](https://airtable.com/appZusMvCI6Ea2b7W/tbllmBPTXdwJo1Dhw/viwNfr4GqFDBLiNz3?blocks=hide) updated if this was major version release
- [ ] [Wiki table "SDK Releases"](https://wiki.hq.twilio.com/display/RTDSDK/Building+and+Releasing+SDKs) updated with recent links to artifacts and changelogs 

[^1]: NB: approvals are required before submission to the [docs](https://github.com/twilio-internal/docs) repo
