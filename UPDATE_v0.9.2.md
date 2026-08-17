# Caloly v0.9.2

Bu sürüm günlük beslenme takibini hedef dayatmadan kişisel bir günlüğe dönüştürür ve kritik giriş/sosyal ekran sorunlarını düzeltir.

## Yenilikler

- Kalori ve makrolar hedef değer göstermeden, yalnızca o gün eklenen besinlere göre sıfırdan büyür.
- Günler arasında ileri/geri gezilebilen ve veri girilmiş günleri mor noktayla gösteren aylık takvim eklendi.
- Bir öğün veya günün tamamı daha sonra tekrar kullanılmak üzere kaydedilebilir.
- Arkadaşların izin verdiği örnek öğün ve günler görüntülenip kişisel şablonlara alınabilir.
- Boy, kilo, doğum tarihi ve isteğe bağlı cinsiyet bilgileriyle VKİ hesaplaması eklendi. Bu adım atlanabilir ve profilden daha sonra düzenlenebilir.
- Sosyal ekranlardaki teknik Supabase hata metinleri kullanıcı dostu Türkçe mesajlara dönüştürüldü.
- Sosyal takip dili “Hedef Arkadaşı” ve “Birlikte Takip” olarak yenilendi.
- Google sağlayıcısı etkin değilse ham hata sayfası açılmadan anlaşılır uyarı gösterilir.
- Yeni Caloly uygulama simgesi eklendi.

## Sunucu kurulumu

Sosyal arama ve paylaşım özellikleri için Supabase SQL Editor içinde `001`–`005` migration dosyaları sırayla çalıştırılmalıdır. Google ile giriş ayrıca Supabase Authentication sağlayıcı ayarlarında etkinleştirilmelidir.

## Sonraki sürüm

Fotoğraftan yemek tanıma ve Gemini destekli Caloly Lens, ayrı bir sürüm olarak planlanmıştır.
