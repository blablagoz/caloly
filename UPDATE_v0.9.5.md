# Caloly v0.9.5

## Arama deneyimi

- Çevrimdışı ve internet sonuçları artık tek **Eşleşmeler** listesinde iç içe gösterilir.
- Ürün kartlarındaki teknik kaynak adları ve barkod numaraları kullanıcı arayüzünden kaldırıldı.
- Bilinen marka sorguları doğrudan marka kataloğunda aranır.
- İnternet sonuçlarına önceki/sonraki ve numaralı sayfalarla erişilebilir.
- Aynı barkoda sahip yerel ve internet ürünü tek sonuç olarak gösterilir.

## Canlı sosyal altyapı

- Mevcut hesaplar canlı Supabase `profiles` tablosuna aktarıldı.
- Arkadaş arama, istek, bağlantı ve paylaşım RPC'leri kuruldu.
- Türkçe karakter ve büyük/küçük harf uyumlu kişi araması etkinleştirildi.
- Ortak hedefler, paylaşılabilir öğün/gün şablonları ve avatar altyapısı kuruldu.
- Kullanıcı adıyla giriş için `login-identifier` Edge Function dağıtıldı.

## Doğrulama

- Android birim testleri ve debug derlemesi çalıştırılır.
- Canlı kişi arama fonksiyonu mevcut hesaplar arasında sonuç döndürür.
