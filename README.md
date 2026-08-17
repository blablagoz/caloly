# Caloly v0.7.1
> Marka adı artık **Caloly**. Android package/namespace: `com.caloly.app`; auth deep link: `caloly://auth`.


Caloly, Kotlin + Jetpack Compose ile geliştirilen sosyal beslenme ve kalori takip uygulamasıdır.

## Bu sürümde
- Eflatun-beyaz Caloly tema sistemi ve oval yeşili aksiyon butonları
- Room tabanlı günlük besin kayıtları
- Kahvaltı / Öğle / Akşam / Atıştırmalık seçimi
- Besin arama ve hızlı ekleme
- g / ml / adet / dilim / paket ölçü birimleri
- Miktara göre anlık kalori + protein + karbonhidrat + yağ önizlemesi
- Kaydedilen öğünün ana ekrandaki günlük toplamları anında güncellemesi
- Başlangıç için yerel örnek genel gıda ve Türkiye paketli ürün kataloğu

## Sonraki entegrasyonlar
- Uzak besin API'si + Türkiye ürün cache'i
- Barkod tarama
- Health Connect ile gerçek adım ve aktivite kalorisi
- Supabase Auth ve sosyal takip


## v0.3.0 — İnternet Besin Verisi + Barkod

- Open Food Facts gerçek internet entegrasyonu eklendi.
- Yerel Caloly kataloğu anlık aranmaya devam eder.
- Uzak aramalar rate-limit nedeniyle kullanıcı `İnternette Ara` butonuna bastığında yapılır.
- Paketli ürünler için EAN-8 / EAN-13 / UPC-A / UPC-E barkod taraması eklendi.
- Google Code Scanner kullanıldığı için Caloly doğrudan CAMERA izni istemez.
- Barkod sonucu Open Food Facts v3.6 ürün endpoint'inden besin değerleriyle alınır.
- Uzak üründe paket gramajı mevcutsa `paket` ölçü birimine otomatik dönüştürülür; gram ile giriş de mümkündür.
- Open Food Facts topluluk verisi olduğundan ürün değerleri etikete karşı kullanıcı tarafından kontrol edilebilir olmalıdır.

### Geliştirme notu
Open Food Facts üretim kullanımında özel bir User-Agent ister. `NetworkModule.kt` içindeki geliştirme User-Agent'i, Caloly için gerçek bir iletişim adresi belirlendiğinde yayın öncesi `Caloly/<version> (contact@...)` formatına çevrilmelidir.

## v0.4.0 - Health Connect
- Health Connect availability detection
- Runtime read permission flow for steps, active calories, and total calories burned
- Daily aggregate reads (avoids duplicate step counting across multiple sources)
- Home dashboard now shows live steps, active kcal and total kcal burned
- Health permission rationale activity for Android 13 and Android 14+
- Refreshes health data when the app returns to foreground


## v0.5.0 — Account / Supabase Auth

Added:
- Email + password sign up and sign in
- Passwordless email OTP sign in and OTP verification screen
- Google OAuth sign in through Supabase + Android deep link (`caloly://auth`)
- Forgot password and change password screens
- Account/profile screen with display name and username
- Supabase `profiles` SQL schema + RLS + auth metadata sync trigger
- Session gate: signed-out users see Login, signed-in users see Caloly Home

### Required Supabase setup
1. Create a Supabase project.
2. Run `supabase/001_auth_profiles.sql` in SQL Editor.
3. In Authentication > URL Configuration add redirect URL: `caloly://auth`.
4. For 6 digit e-mail OTP, edit the email template to use `{{ .Token }}` rather than only a confirmation link.
5. Enable Google under Authentication > Providers and configure Google OAuth credentials.
6. Put the following in your local Gradle properties (do not commit real secrets):

```
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_PUBLISHABLE_KEY
```

The mobile app uses a publishable key only. Never place a Supabase secret/service-role key in the APK.

## v0.6.0 — Sosyal Takip ve Paylaşım İzinleri
- Kullanıcı adı / görünen ad ile kullanıcı arama
- Takip isteği gönderme, kabul etme ve reddetme
- Arkadaş / partner / diyetisyen / danışan ilişki tipleri için backend modeli
- Her bağlantı yönü için ayrı paylaşım izinleri
- Kalori, makro, adım, aktivite, kilo, yemek detayı ve geçmiş gün izinleri
- Günlük kalori/makro/Health Connect özetinin Supabase `daily_summaries` tablosuna senkronu
- Bağlantı profili üzerinden izin verilen günlük özetin görüntülenmesi
- Paylaşılmayan metrikler `SECURITY DEFINER` RPC içinde NULL yapılır; mobil istemci tam satırı alamaz

### Supabase
`001_auth_profiles.sql` sonrasında `supabase/002_social.sql` dosyasını SQL Editor'da çalıştırın.

## v0.7.0 — Sosyal Dashboard

- Partner ilişkileri sosyal ekranda ayrı ve öne çıkan kartlarla gösterilir.
- Arkadaş/partner kartlarında izin verilen bugünkü kalori, adım ve protein özeti görünür.
- Kalori hedefinde olan kullanıcı için `Hedefte` rozeti gösterilir.
- Arkadaş isteği yanında `Partner olarak ekle` akışı eklendi.
- İlişki bazlı ortak hedefler eklendi:
  - günlük ortak adım hedefi,
  - iki kişinin de kendi kalori hedefinde kalması.
- Ortak hedefler karşı tarafın paylaşım izinlerini aşamaz.
- Profil fotoğrafları Coil ile ağdan gösterilir; fotoğraf yoksa eflatun baş-harf avatarı kullanılır.
- Hesabım ekranından en fazla 5 MB profil fotoğrafı seçilip Supabase Storage `avatars` bucket'ına yüklenebilir.
- `supabase/003_social_dashboard.sql` migration'ı eklendi.
