# Onaylı görselden film — üretim ve kabul denemesi

24 Eylül 2026. Kullanıcı tasarımı onayladı; basit Blender yeniden yapımı kalite hedefini karşılamadı. Yeni üretim başlangıcı doğrudan sandman-film-design-v3.png. Görsel kalite için 3B model üretimi ön koşul değildir.

## İlk deneme: 5 saniye, tek çekim

Başlangıç görseli: art/film/sand-army-lookdev/sandman-film-design-v3.png
Önerilen ilk model: Runway Gen-4.5 image-to-video, 1280:720. Bağlantı sonrası mevcut modeller ve hesap kredisi doğrulanacak. Şu anda hesap bağlı değil, üretim yapılmadı ve ücret harcanmadı.

### Hareket yönergesi

A single continuous cinematic shot. The green-striped commander keeps his recognizable face and costume and gently closes the fingers of his extended sandy hand, giving a restrained command. The eight sandstone soldiers already visible in the image accelerate into a run, each on a distinct unobstructed path with staggered starts, varied strides and natural opposing arm swings. The camera smoothly dollies backward at their pace, keeping the commander readable between them. Feet plant firmly, push off and kick up fine sunlit sand. Preserve the starting image's sculpted blocky anatomy, golden granular materials, warm side lighting, cool blue atmospheric depth, detailed contact shadows and cinematic tonal contrast throughout. Movement belongs to the characters and the drifting sand; the scene is a coherent animated film shot. Keep the camera outside every character. The shot ends while the soldiers are still running.

## İnceleme — üretimden önce tanımlanmış kabul koşulları

Çıktıyı normal hızda ve ağır çekimde izle. 0, 0.5, 1, 2, 3, 4 ve son saniyeden örnek kareler çıkar; komut eli, yüz ve koşan askerleri yakın kırpımlarda karşılaştır. Sadece ilk kareye bakmak yeterli değil.

1. Kimlik: Sandman yüzü, saç, gömlek ve vücut oranları sabit. Askerler insanlar gibi erimiyor veya birleşmiyor.
2. Anatomi: uzuv sayısı ve bağlantıları korunuyor; ters el, gövdeden geçen kol, kayan parmak yok.
3. Hareket: askerler koşuyor; tek poz kaydırılmıyor. Başlangıçlar ve adımlar senkron değil; ayaklar zeminde kaymıyor.
4. Görsel kalite: referansın kum taneleri, kenar ışığı, yüz gölgeleri ve sıcak/soğuk ayrımı klip boyunca korunuyor. Doku yüzey üzerinde yüzmüyor, parlaklık titremiyor.
5. Kamera: kontrollü dolly; modele girme, ani atlama ve gereksiz kesme yok.
6. Sahne: askerler sebepsiz ortaya çıkmıyor/kaybolmuyor; siluetler okunuyor.

Her koşul için geçti/kaldı ve zaman damgalı somut bulgu yaz. Herhangi bir belirgin anatomi veya kimlik hatası kabulü durdurur. Otomatik benzerlik puanı tek başına kabul sayılmaz. Yeni denemede bir sorunu hedefleyerek prompt/kadraj değiştirilir. Deneme sayısı ve kredi maliyeti tutulur; sınırsız yeniden üretim yapılmaz.

## Sonraki üretim

İlk test kabul edilmeden uzun ultimate üretme. Sonra kullanıcı senaryosunu kısa planlara ayır, onaylı görselden aynı karakteri taşıyan başlangıç/bitiş kareleri hazırla. Çok karakterli fiziksel temasları ayrı kısa çekimlerde çöz. Kabul edilen çekimleri kurgu ve sesle birleştir. İki katılımcıda senkron video oynatma, oyun kilidi ve final hasarı ayrı mod entegrasyonu işidir. Önceden render edilmiş sabit rakip görüntüsü gerçek oyuncu skinini kendiliğinden değiştirmez; final rakip görünüş politikası ayrıca netleştirilecek.

## Araştırma kaynakları

- https://docs.dev.runwayml.com/guides/using-the-api/ — başlangıç görseli ile video üretimi.
- https://help.runwayml.com/hc/en-us/articles/48324313115155-Image-to-Video-Prompting-Guide — hareket odaklı yönerge.
- https://docs.dev.runwayml.com/guides/pricing/ — API Gen-4.5: 12 kredi/saniye, kredi 0.01 USD; 5 saniye 0.60 USD (vergiler hariç). Uygulama/bağlantı fiyatı ayrıca doğrulanmalı; API tarifesi otomatik uygulama fiyatı sayılmaz.
- https://deepmind.google/models/veo/ — alternatif referans güdümlü video yaklaşımı; bu proje için henüz test edilmedi.
