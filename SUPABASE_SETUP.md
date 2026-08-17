# Caloly — Supabase Setup

1. Create a Supabase project.
2. Run `supabase/001_auth_profiles.sql` in SQL Editor.
3. Authentication > URL Configuration: add `caloly://auth` as an allowed redirect URL.
4. Authentication > Email Templates:
   - For code based sign-in/confirmation, put `{{ .Token }}` in the e-mail body.
   - Keep password recovery configured; Caloly redirects recovery links to `caloly://auth`.
5. Authentication > Providers > Google:
   - enable Google
   - enter Google OAuth client ID/secret requested by Supabase.
6. Add project config to local `gradle.properties`:

```
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
```

Do not ship service-role/secret keys in an Android APK. Caloly is designed to use the client-safe publishable key and RLS.

## v0.6.0 Social migration
Auth/profile migration tamamlandıktan sonra SQL Editor içinde:

1. `supabase/002_social.sql` çalıştırın.
2. Data API ayarlarında `public` şemasının erişilebilir olduğundan emin olun.
3. Mobil uygulama yalnız publishable key kullanır. Service-role/secret key eklemeyin.
4. `daily_summaries` tablosu kullanıcıya kendi satırlarında RLS ile erişim verir. Başka kullanıcıların özetleri yalnız `get_caloly_shared_daily_summary` RPC'sinden, ilişki ve paylaşım izinleri kontrol edilerek döner.

## v0.7.0 ek kurulumu

`001_auth_profiles.sql` ve `002_social.sql` sonrasında SQL Editor'da:

```sql
-- supabase/003_social_dashboard.sql içeriğini çalıştırın
```

Bu migration:
- `relationship_goals` tablosunu,
- ortak hedef RPC'lerini,
- public `avatars` Storage bucket'ını,
- yalnızca kullanıcının kendi avatar klasörüne yazmasına izin veren Storage politikalarını oluşturur.

Ortak hedef RPC'si partnerin adım/kalori paylaşım izinlerini kontrol eder; kapalı bir metrik hedef kartı üzerinden açığa çıkarılmaz.

## v0.9.1 profil ve kullanıcı adı girişi

1. SQL Editor içinde `supabase/004_profile_onboarding.sql` dosyasını çalıştırın.
2. Supabase CLI ile `supabase functions deploy login-identifier` çalıştırın.
3. Edge Function için `SUPABASE_URL`, `SUPABASE_ANON_KEY` ve `SUPABASE_SERVICE_ROLE_KEY` secret'larının tanımlı olduğunu doğrulayın. Service-role anahtarı yalnız Edge Function ortamında kalmalı, APK'ya eklenmemelidir.
4. Google Cloud'da OAuth istemcilerini oluşturup Supabase Authentication > Providers > Google bölümünde Web client ID/secret değerlerini tanımlayın.
5. Authentication > URL Configuration içinde `caloly://auth` yönlendirmesinin kayıtlı olduğunu doğrulayın.
