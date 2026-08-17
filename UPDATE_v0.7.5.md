# Caloly v0.7.5

Build hardening update.

- Java compile target 1.8 -> 17
- Kotlin JVM toolchain explicitly set to 17
- Fixes `compileDebugJavaWithJavac (1.8)` vs `compileDebugKotlin (17)` incompatibility
- Keeps AGP 8.10.0, compileSdk 36 and Compose BOM 2026.06.00
- Cleans Android SDK 36 install step in GitHub Actions
- Adds a non-blocking warning when Supabase Actions secrets are missing
- Debug artifact renamed to `Caloly-v0.7.5-debug-apk`
- versionCode: 12
- versionName: 0.7.5
