# Magneto

Erik Lehnsherr, manyetizmanın efendisi. `/superhero hero magneto` ile ya da P (şampiyon seçimi) ekranından seçilir.
Görünüş: kırmızı zırhlı kostüm (V şeklinde göğüs plakaları), yanlarda mor paneller, uzun mor eldivenler ve botlar,
kırmızı yuvarlak miğfer (tepe sırtı, yanaklık, yüz açıklığında mor şerit), içinde yaşlı bir yüz, omuzlardan dökülen
uzun mor pelerin (uçarken dalgalanır).

## Tuşlar
| Tuş | Yetenek | Ne yapar |
|---|---|---|
| SHIFT (ya da Boşluk x2) | Uçuş | Havalanır, sakin ve orta hızda süzülür. Baktığın yöne gider (W/A/S/D), Boşluk yükselir, CTRL alçalır; dururken hafifçe aşağı yukarı salınır. Uçarken kollar açıktır. Yere değince iner. |
| Sol tık | Metal Kıymık | Elinden keskin bir metal parçası fırlatır (yumruk açıkken: yumruk atar; birini tutarken: onu fırlatır). |
| Q | Demir Yağmuru | 3 kullanım hakkı (her biri ~7 sn'de dolar). Nişan aldığın yerin ~8 blok üstünde 5 dev demir çubuk/kiriş belirir, hafif eğik açıyla, kendi ekseninde dönerek düşer, toprağa saplanır: küçük patlama, toprak, kıvılcım, ses, alan hasarı. Zemini kırmaz. Çubuklar birkaç saniye saplı kalır, sonra toprağa gömülüp kaybolur. |
| E | Hurda Telekinezisi | Baktığın hedefin arkasından hurda metal parçaları uçup onu sarar ve havaya kaldırır. 3 saniye boyunca fareyi oynattıkça hedef havada sürüklenir (ışınlanmaz). Hızla duvara ya da yere çarparsa hasar, ses, toz ve küçük şok dalgası. Sol tık: baktığın yöne fırlatır. Süre bitince metal dökülür. E/sağ tık: erken bırakır. |
| R | Dev Demir Yumruk | Önünde metal parçalarının birleşmesiyle dev bir yumruk oluşur, nişanını takip eder. Her sol tıkta yere hızla iner: alan hasarı, şok dalgası, toz/metal, ağır ses. Tam 5 yumruk; sonra parçalanıp dağılır. |
| F | Manyetik Demir Kalkan | Etrafından toprağı yararak demir sütunlar yükselir ve etrafında durur: oklar ve mermiler sütunlara çarpıp durur, yakın saldırıların %70'ini sütunlar alır. Tekrar F: sütunlar parçalanıp onlarca parça halinde her yöne döne döne fırlar, çarptıklarına hasar verir, yere/duvara saplanır. |
| X | Manyetik İnfaz | Sinematik ulti (aşağıda). Önündeki birine bakarak basılır. |

## Ayarlar
- `config/superheromod-magneto.toml`: uçuş hızı, her yeteneğin hasarı, menzili, bekleme süresi, süresi; çubuk sayısı ve
  yüksekliği, hurda parça sayısı, yumruk sayısı, sütun sayısı, patlama parça sayısı, kalkanın azaltma oranı, X'in hasarı.
- `config/superheromod-magneto-client.toml`: kamera sarsıntısı, efekt miktarı (performans için), manyetik alan çizgileri.

## Kod
- `heroes/magneto/`: `MagnetoAction` (kimlikler, zamanlamalar), `MagnetoConfig`, `MagnetoController` (sunucu: uçuş,
  çubukların/parçaların fiziği ve isabetleri, tutma/sürükleme/çarpma, yumruk, kalkan), `MagnetoUltSession` (X).
- `client/render/magneto/`: `MagnetoClient` (durum, uçuşu kendi istemcisinde yumuşak sürme, E tuşu, HUD), `MagnetoMotion`
  (pozlar, Panther'in eklem düzeniyle), `MagnetoBody` (kutulardan kostüm ve pelerin), `MagnetoLayer` (geçişler, uçuş/duruş,
  nişanı takip eden el, pelerin yayı), `MetalMesh` (çubuk, hurda, sütun, yumruk, parça şekilleri), `MagnetoFx` (dünyada
  metal, toz, kıvılcım, halkalar, alan çizgileri, birinci şahıs eldivenler).
