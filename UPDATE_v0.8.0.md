# Caloly v0.8.0 — Magic Link Auth

- Signup verification switched from 6-digit OTP UI to email verification link.
- Email/password signup explicitly uses redirectUrl `caloly://auth`.
- Passwordless email login explicitly uses redirectUrl `caloly://auth`.
- Existing Android deep-link configuration remains unchanged.
- Existing MainActivity `handleDeeplinks(intent)` flow remains unchanged.
- versionCode: 17
- versionName: 0.8.0
- Artifact: Caloly-v0.8.0-debug-apk

Supabase dashboard must contain:
- Site URL: caloly://auth
- Redirect URL: caloly://auth
