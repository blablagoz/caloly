# Caloly — Supabase kurulumu

## Temel kurulum

1. Bir Supabase projesi oluşturun.
2. SQL Editor içinde migration dosyalarını sırayla çalıştırın:
   - `supabase/001_auth_profiles.sql`
   - `supabase/002_social.sql`
   - `supabase/003_social_dashboard.sql`
   - `supabase/004_profile_onboarding.sql`
   - `supabase/005_v092_nutrition_templates.sql`
3. Authentication > URL Configuration bölümünde `caloly://auth` adresini izin verilen yönlendirmelere ekleyin.
4. Kodla e-posta doğrulaması için Authentication > Email Templates içindeki şablona `{{ .Token }}` ekleyin.
5. Yerel `gradle.properties` dosyasına istemci için güvenli proje bilgilerini ekleyin:

```properties
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
```

Service-role veya secret anahtarını Android APK içine eklemeyin. Mobil uygulama yalnızca publishable key ve RLS/RPC izinlerini kullanır.

## Sosyal özellikler

- `002_social.sql` arkadaş arama, istekler, bağlantılar ve kişi bazlı paylaşım izinlerini kurar.
- `003_social_dashboard.sql` birlikte takip hedeflerini ve avatar Storage alanını kurar.
- `005_v092_nutrition_templates.sql` arkadaşlarla paylaşılabilen örnek öğün/gün kayıtlarını kurar.
- Bir arkadaşın örnek öğünleri yalnızca o kişi “Beslenme detayları” paylaşım iznini açtıysa görünür.

Ekranda `PGRST202` veya “schema cache” hatası oluşması, ilgili migration dosyasının Supabase projesinde henüz çalıştırılmadığını gösterir. v0.9.2 bu teknik metni kullanıcıya göstermez; yine de özelliğin çalışması için migration kurulmalıdır.

## Kullanıcı adıyla giriş

1. Supabase CLI ile `supabase functions deploy login-identifier` çalıştırın.
2. Edge Function ortamında `SUPABASE_URL`, `SUPABASE_ANON_KEY` ve `SUPABASE_SERVICE_ROLE_KEY` secret değerlerini tanımlayın.
3. Service-role anahtarı yalnızca Edge Function ortamında kalmalı, APK içine eklenmemelidir.

## Google ile giriş

1. Google Cloud Console içinde OAuth istemcilerini oluşturun.
2. Supabase Authentication > Providers > Google bölümünü etkinleştirin ve Web client ID/secret değerlerini girin.
3. Authentication > URL Configuration içinde `caloly://auth` yönlendirmesini doğrulayın.

Uygulama, Google sağlayıcısı kapalıysa tarayıcıda ham JSON hata ekranı açmak yerine anlaşılır bir uyarı gösterir. Sağlayıcıyı etkinleştirmek yine de Supabase/Google yönetim panellerinde yapılması gereken harici bir kurulumdur.
