# Caloly v0.7.7

Kotlin compile-fix update.

Fixed errors from the successful GitHub Actions compile attempt:

- Supabase Auth: nullable `session.user` handled safely.
- Health Connect: `AggregateRequest` import moved to `androidx.health.connect.client.request`.
- Supabase PostgREST: generic `rpc(...)` extension imported so @Serializable parameter DTOs resolve correctly.
- Google Code Scanner: corrected package from `com.google.android.gms.mlkit...` to `com.google.mlkit...`.
- Material 3 experimental APIs: module-level opt-in added.
- JVM 17 / compileSdk 36 / Compose BOM 2026.06.00 kept unchanged.
- versionCode: 14
- versionName: 0.7.7
- Artifact: Caloly-v0.7.7-debug-apk
