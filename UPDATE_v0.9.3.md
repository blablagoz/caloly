# Caloly v0.9.3

## Kullanıcıya görünen yenilikler

- Besin kayıtları sola kaydırılarak silinebilir; silme işlemi geri alınabilir.
- Bir besin kaydına dokunarak miktarı ve öğünü değiştirilebilir.
- Öğünün tamamı veya seçili gün tek işlemle silinebilir.
- Takvim aylar arasında parmakla kaydırılır; ay ve yıl başlıklarından doğrudan seçim yapılır.
- Türkçe ve Unicode kullanıcı adları kayıt, profil güncelleme ve kullanıcı adıyla girişte desteklenir.
- Besin araması büyük/küçük harf ve Türkçe karakter farklarından etkilenmez.
- Ekmek, kahve gibi genel aramalar ilgili alt türleri de getirir; sonuçlar cihaz dili/ülkesine göre sıralanır.
- Favoriler ve son kullanılan besinler hızlı ekleme alanında öne çıkar.
- Barkod veritabanında bulunmayan ve elle oluşturulan ürünler cihazda saklanır; sonraki taramada yeniden bulunur.
- 2024 ve sonrasında güncellenmiş açık lisanslı Türkiye ürünleri çevrimdışı kataloğa eklendi.
- 2026 ODbL açık veri sürümünden kalite filtresini geçen 8.923 Türkçe genel besin adı ve makrosu çevrimdışı aramaya eklendi.
- Aktif kalori, toplam enerji ve bazal metabolizma ayrı gösterilir. Health Connect verisi yoksa aktif kalori `Veri yok` yazar.
- Bazal metabolizma Health Connect'ten okunur; yoksa isteğe bağlı profil bilgilerinden Mifflin–St Jeor tahmini gösterilir. Cinsiyet belirtilmediyse tek sayı yerine aralık gösterilir.

## Yayın notları

Supabase tarafında sırasıyla mevcut migration'lar ve `supabase/006_v093_unicode_usernames.sql`
çalıştırılmalıdır. Ardından `supabase/functions/login-identifier` yeniden deploy edilmelidir.

Paketli ürün verileri Open Food Facts ODbL lisansı kapsamındadır. Ayrıntı için
`THIRD_PARTY_DATA.md` dosyasına bakın.
