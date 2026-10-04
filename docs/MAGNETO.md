# Magneto

Erik Lehnsherr, manyetizmanın efendisi. `/superhero hero magneto` ile ya da P (şampiyon seçimi) ekranından seçilir.
Yüz: beyaz kaşlar, beyaz bıyık ve sakal (Marvel Rivals portresi gibi). Yetenek kullanırken kullanılan el saydam mor parlar ve minik açık mor/beyaz kıvılcımlar saçar.
Görünüş: kırmızı zırhlı kostüm (V şeklinde göğüs plakaları), yanlarda mor paneller, uzun mor eldivenler ve botlar,
kırmızı yuvarlak miğfer (tepe sırtı, yanaklık, yüz açıklığında mor şerit), içinde yaşlı bir yüz, omuzlardan dökülen
uzun mor pelerin (uçarken dalgalanır).

## Tuşlar
| Tuş | Yetenek | Ne yapar |
|---|---|---|
| SHIFT (ya da Boşluk x2) | Uçuş | Havalanır, sakin ve orta hızda süzülür. Baktığın yöne gider (W/A/S/D), Boşluk yükselir, CTRL alçalır; dururken hafifçe aşağı yukarı salınır. Uçarken kollar açıktır. Yere değince iner. |
| Sol tık | Metal Kıymık | Elinden keskin bir metal parçası fırlatır (yumruk açıkken: yumruk atar; birini tutarken: onu fırlatır). |
| Q | Demir Yağmuru | 3 kullanım hakkı (her biri ~7 sn'de dolar). Nişan aldığın yerin ~8 blok üstünde 5 dev demir çubuk/kiriş belirir, hafif eğik açıyla, kendi ekseninde dönerek düşer, toprağa saplanır: küçük patlama, toprak, kıvılcım, ses, alan hasarı. Zemini kırmaz. Çubuklar birkaç saniye saplı kalır, sonra toprağa gömülüp kaybolur. |
| E | Hurda Telekinezisi | Baktığın hedefin arkasından hurda metal parçaları uçup onu sarar ve havaya kaldırır. 3 saniye boyunca fareyi oynattıkça hedef havada sürüklenir (ışınlanmaz, zemine gömülmez). Tekrar E: bırakır; fareyi hızla savurup E'ye basarsan hedef o hızla fırlar ve neye çarparsa hızı kadar (hızlıysa çok daha fazla) hasar alır. Tutarken sol tık: ileri fırlatır, sağ tık: havaya fırlatır. |
| R | Dev Demir Yumruk | Önünde metal parçalarının birleşmesiyle dev bir yumruk oluşur, nişanını takip eder (hep görüş alanında kalır, yere bakınca bile). Her sol tıkta yere hızla iner: alan hasarı, şok dalgası, toz/metal, ağır ses. Tam 5 yumruk; sonra parçalanıp dağılır. Tekrar R: yumruğu dağıtır. Kalkan açıkken R: kalkanı patlatır. |
| F | Manyetik Demir Kalkan | Marvel Rivals gibi: etrafında mor, camsı bir enerji küresi açılır; topraktan kopan demir plakalar kürenin çevresinde döner, yüzeyinde hafif elektrik gezinir. Oklar ve mermiler küreye çarpıp durur (trident/oklar düşer, silinmez), yakın saldırıların %70'ini kalkan alır; vurulan yerden küre dalgalanır ve elektrik çatırdar. Tekrar F (ya da R): küre patlar, plakalar parçalanıp her yöne fırlar, çarptıklarına hasar verir. |
| X | Manyetik İnfaz | Sinematik ulti (aşağıda). Önündeki birine bakarak basılır. |

## X — Manyetik İnfaz (20,6 sn)
Kendi sahnesinde oynar: kıyamet sonrası ölü bir dünya, kızıl fırtınalı gökyüzü, sürekli şimşek, sağanak yağmur, ıslak
ve gökyüzünü yansıtan kırık zemin, ufukta yıkık kuleler, yerde kafatasları ve hurda.
1. Magneto ortada sakin duruyor, etrafında onlarca metal parça farklı hızlarda dönüyor; elinin küçük hareketlerine tepki veriyor.
2. Elini yavaşça kaldırıyor, yörüngeler hızlanıyor; eliyle iterek metali hedefe gönderiyor.
3. Parçalar hedefin bileklerine, ayak bileklerine ve göğsüne kilitleniyor; kolları ve bacakları X şeklinde açılıp havaya kalkıyor.
4. Magneto elleriyle X çiziyor (sağ el sağ üstten sol alta, sol el sol üstten sağ alta); iki dev demir sütun fırtınadan
   çapraz açıyla inip hedefin arkasında dev bir X oluşturuyor (sarı parlayan kenarlar, kıvılcım, su sıçraması, toz,
   şok halkası ve aynı anda yıldırım).
5. Hedef X'in ortasına çekilip çakılıyor (kafatası tepesinin üstünde, 2. görseldeki gibi).
6. Magneto arkasını dönüp kameraya doğru yürüyor; elini kaldırıp yumruk yapıyor, sonra çöp atar gibi küçük bir el hareketi.
7. Sütunlar birbirine çekilip bükülüyor, hedefin etrafına sarılıp küçük bir metal topa sıkışıyor; top fırtınanın içine
   fırlatılıyor, çok uzakta patlıyor. Magneto hiç arkasına bakmadan yürümeye devam ediyor.
Hasar: ezilmede hedefin canının %70'i (ayar), film bitince geri itme + yavaşlık.

## Ayarlar
- `config/superheromod-magneto.toml`: uçuş hızı, her yeteneğin hasarı, menzili, bekleme süresi, süresi; çubuk sayısı ve
  yüksekliği, hurda parça sayısı, yumruk sayısı, sütun sayısı, patlama parça sayısı, kalkanın azaltma oranı, X'in hasarı.
- `config/superheromod-magneto-client.toml`: kamera sarsıntısı, efekt miktarı (performans için), manyetik alan çizgileri.

## Kod
- `heroes/magneto/`: `MagnetoAction` (kimlikler, zamanlamalar), `MagnetoConfig`, `MagnetoController` (sunucu: uçuş,
  çubukların/parçaların fiziği ve isabetleri, tutma/sürükleme/çarpma, yumruk, kalkan), `MagnetoUltSession` (X: sunucu tarafı).
- `client/render/magneto/`: `MagnetoClient` (durum, uçuşu kendi istemcisinde yumuşak sürme, E tuşu, HUD), `MagnetoMotion`
  (pozlar, Panther'in eklem düzeniyle), `MagnetoBody` (kutulardan kostüm ve pelerin), `MagnetoLayer` (geçişler, uçuş/duruş,
  nişanı takip eden el, pelerin yayı), `MetalMesh` (çubuk, hurda, sütun, yumruk, parça şekilleri), `MagnetoFx` (dünyada
  metal, toz, kıvılcım, halkalar, alan çizgileri, birinci şahıs eldivenler).
- X filmi: `MagneticPath` (zaman çizelgesi: Magneto'nun pozları ve yürüyüşü, dönen metal, hedefin yeri ve pozu, sütunlar,
  ezilme, top, şimşekler, kameralar), `MagneticStage` (sahneyi çizer), `film/MagneticExecutionFilm` (ses, renk, başlık).
  Arka plan: `film_backdrop.fsh` sahne 10.
