# Caloly v0.9.4

## Kullanıcıya görünen yenilikler

- Besin araması artık ana ekranı kilitlemeden arka planda çalışır ve yazma sırasında kısa bir bekleme uygular.
- İnternet araması yalnızca kullanıcı `İnternette Ara` düğmesine bastığında başlar.
- İlgili çevrimdışı sonuçlar internet sonuçlarıyla birlikte korunur; eski veya alakasız sonuçlar yeni sorguya karışmaz.
- Çevrimdışı ve internet eşleşmeleri ayrı başlıklarla gösterilir ve tekrar eden ürünler tekilleştirilir.
- Starbucks, Espressolab, Gloria Jean's Coffees ve Nevada Coffee kahve, tatlı ve sandviçleri internet aramasına eklendi.
- Resmî olmayan kafe besin değerleri uygulamada açıkça tahmini olarak işaretlenir.
- Meyve ve sebzelerde gramın yanında ürün bazlı ortalama ağırlık kullanan adet seçeneği bulunur.
- Tek besin, öğün veya gün silinmeden önce onay istenir; işlem sonrasında Geri Al kullanılabilir.
- Ana sayfadaki makro, aktivite ve son öğün kartları ilgili Beslenme/Aktivite sekmelerine geçer.
- İlk girişte Android bildirim izni istenir. Profilde günlük hatırlatmayı açma/kapatma ve saat seçme ekranı bulunur.

## Teknik doğrulama

- `testDebugUnitTest`: 17 test başarılı.
- `assembleDebug`: başarılı.
- Android 13 ve sonrasında `POST_NOTIFICATIONS` çalışma zamanı izni desteklenir.
