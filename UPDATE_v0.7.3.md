# Caloly v0.7.3

Build compatibility update after GitHub Actions `checkDebugAarMetadata` failure.

- compileSdk: 35 -> 36
- versionCode: 10
- versionName: 0.7.3
- GitHub Actions Gradle: 8.14.4
- GitHub Actions installs Android API 36 before build
- APK artifact name: Caloly-v0.7.3-debug-apk

Reason: Health Connect 1.1.0 requires consumers to compile against Android API 36 or newer.
