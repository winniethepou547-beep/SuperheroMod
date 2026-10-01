# Sinematik animasyon klip katmanı — 9 Eylül 2026

## Neden değişti?

Eski ActorTrack her anahtarda tüm pozu aynı smoothstep ile değiştiriyordu.
Bu, koşu ve yumruk gibi hareketleri küçük duruşlardan oluşuyormuş gibi
gösterebiliyor; bel ve ön kolun farklı zamanlarda hızlanmasını zorlaştırıyordu.

Yeni sıra: sahne kök hareketi → temel poz → animasyon klipleri → el teması IK → render.
Kamera ve sahne hareketleri poz kliplerinden bağımsızdır. Geçerli sahneler korunur.

## Gelen özellikler

- Her eklem/eksenin ayrı anahtar zamanları ve kesintisiz kübik hız eğrisi.
- Eğriler ara değerlerde durmak zorunda değildir. Monoton Hermite eğrisi taşmayı sınırlar.
- Hazırlık, ağırlık aktarımı, uzanma, temas, takip hareketi ve toparlanma kanalları.
- Klip başlatma zamanı, hız, döngü, giriş/çıkış harmanlaması.
- Yalnızca yazılmış kanalları değiştirme; override veya additive kullanım.
- Rastgele zamanda örnekleme: geçmiş kareye veya gerçek mob AI'ına bağlı değildir.
- Klipteki contact işareti aynı sahnede IK ve kamera darbesini zamanlar.
- Klipler başlangıçta yüklenir; render sırasında dosya okunmaz veya kanal nesnesi üretilmez.

Sand Army: karşı yumruk, yana kaçış/ters kol darbesi, darbe sonrası savrulma,
ilerleme döngüsü. İlk iki karşılaşma kamerası profilden görünecek şekilde değişti.
Diğer askerler ikinci karşılaşmanın sonuna kadar gelmez.

## Dosyalar ve format

`src/main/resources/assets/superheromod/cinematics/clips/`

- `counter_cross.json`
- `evade_backhand.json`
- `struck_back.json`
- `advance_cycle.json`

Bu özel motor formatıdır; doğrudan Blockbench/GeckoLib animasyonu değildir.
Şimdilik paket içi kaynakları açılışta okur; kaynak paketiyle canlı reload yoktur.

```json
{
  "schema": 1,
  "duration": 20,
  "markers": {"contact": 12},
  "channels": {
    "right_lower_arm.x": [[0,-70],[8,-110],[12,-5],[20,-70]],
    "chest.y": [[0,0],[6,25],[12,-30],[20,0]],
    "body.crouch": [[0,0],[6,0.15],[12,0.03],[20,0]]
  }
}
```

Zaman tick; açılar derece; crouch blok. Kök rotasyonları body.roll/body.pitch.
Eklemler: head, chest, hips, right/left_upper_arm, right/left_lower_arm,
right/left_upper_leg, right/left_lower_leg. Eksenler x/y/z.
Yazılmayan kanallar temel pozu korur. Anahtarlar kesin artan sırada olmalıdır.
Döngüde başlangıç ve son pozlar eşleşmelidir.

## Hâlâ eksik olanlar

Bu katman tek başına film kalitesinde animasyon sağlamaz. Parmak/bilek/yüz rigleri,
otomatik ayak sabitleme, yere tam temas, çarpışmasız kalabalık yerleşimi, yüzey boyunca
tırmanma, gerçek kum mesh dönüşümü, ışığa tepki veren hacimsel sis, dış editör importu
ve kaliteli sahneye özgü animasyon üretimi ayrı işlerdir. Mevcut 11 eklemli rig sürer.

Testler kanal maskesi, derece dönüşümü, ara anahtarda hız sürekliliği, taşma sınırı,
additive blend, döngü, blend-out, bütün paket klipleri ve 30/60/144 FPS örneklemesini
kontrol eder. Bunlar oyun içindeki gerçek FPS veya estetik kalite ölçümü değildir.
