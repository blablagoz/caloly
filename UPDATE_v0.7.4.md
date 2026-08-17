# Caloly v0.7.4 build compatibility fix

- Compose BOM: `2026.08.00` -> `2026.06.00`
- Keeps `compileSdk = 36`
- Keeps AGP `8.10.0`
- Keeps Gradle `8.14.4`
- `versionCode = 11`
- `versionName = 0.7.4`
- GitHub Actions artifact: `Caloly-v0.7.4-debug-apk`
- GitHub Actions Java setup updated to `actions/setup-java@v5`

Reason: Compose 1.12.x requires API 37 and AGP 9.x. Compose BOM 2026.06.00 keeps the project on the Compose 1.11.x stable line suitable for the current API 36/AGP 8.10 build chain.
