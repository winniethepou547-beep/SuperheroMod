# Sinematik motoru: araştırma, mevcut durum ve üretim mimarisi

9 Eylül 2026. Bu belge mevcut kod incelemesini, doğrulanabilen kaynakları ve önerilen geliştirmeleri birbirinden ayırır. Bir özellik aşağıda tasarlanmış olması nedeniyle oyunda yapılmış sayılmaz.

## 1. İstenen sonuç

Tuşa basıldığında saldırgan ve hedef, normal Minecraft hareketlerinden bağımsız bir film sahnesinin oyuncuları olacak. Sandman askerlerini yönetebilecek; hedef saldırıları karşılayacak, bazı askerleri savuracak, daha sonra sayısal üstünlüğe yenilecek. Kamera, çevre, karakter hareketi, kumun dönüşümü, ışık ve ses aynı zaman çizelgesini izleyecek. Normal oynanışta karakterin takla atamaması veya parmaklarını oynatamaması sinematik rigini sınırlamamalı.

Sahnede mevcut oyuncunun görünüşünün kullanılması önemlidir. Bu nedenle temel tercih, önceden kaydedilmiş MP4 değil, önceden hazırlanmış **üç boyutlu bir sahnenin çalışma zamanında oynatılmasıdır**. Video seçeneği sabit oyuncular ve sabit kamera için uygundur; rastgele katılan oyuncunun skinini ve bedenini doğru şekilde değiştirmek için ayrıca yeniden render/compositing gerektirir. Bu proje için ana çözüm değildir.

Anahtar kare kullanmak akıcılığa engel değildir. Anahtar kareler hareketin tanımıdır; oyun aradaki zamanı her görüntü karesinde örnekler. Sorun birkaç kaba pozun, yeterince eklemi olmayan ve parçaları birbirinden ayrılan bir modele uygulanmasıdır. glTF de animasyonu zaman örnekleri, hedef kanallar ve interpolasyonla tanımlar. [2]

## 2. Referanslardan gerçekten çıkarılabilenler

### Gönderilen kısa film

[Gus Protects his Village – @skytrohd](https://www.youtube.com/shorts/ILE86_AL5kI?feature=share) tarayıcıda açıldı. Başlığı Blender/animation etiketleri içeriyor. İncelenen görüntüde demir golem, yakın plandaki nesne, alan derinliği ve yönlü aydınlatma birlikte kompozisyon oluşturuyor. Buradan alınacak ilke, Minecraft biçim dilinin film kamerası ve sahne aydınlatmasıyla birlikte kullanılabilmesidir. Videonun kaynak sahnesine/rigine erişilmedi; kullandığı gerçek teknik veya tüm hareketlerinin çözümlendiği iddia edilmiyor.

### Marvel Tokon

Resmî PlayStation röportajı Amerikan çizgi romanı ve Japon manga etkisini, özel saldırı sunumlarında manga paneli yaklaşımını açıklıyor. Bu, görünüş ve kurgu için birincil kaynaktır; oyunun iç render koduna dair belge değildir. Bizim için çıkarım: saldırının okunması, kuvvetin artışı ve kadraj dili birlikte tasarlanmalı. Tokon'un belirli shader veya rig tekniğini kullandığını tahmin ederek gerçekmiş gibi aktarmamalıyız. [3]

### Üretim araçları ve oyun motorları

Unreal Sequencer'ın dinamik bağlama yaklaşımı, sahnedeki rol ile o rolü oynayan gerçek aktörü ayırır. Bizde `attacker`, `target`, `soldier_0` gibi rollerin oyuncu/NPC görünüşlerine bağlanması aynı mimari sorunu çözer; Unreal kodunu Forge'a taşımak anlamına gelmez. [4]

Motion Warping, belirli animasyon pencerelerinde kök hareketini hedefe uydurmayı ele alır. Bizim çıkarımımız: yumruğu hedefe rastgele ışınlamak yerine yaklaşma mesafesini ve son temas penceresini düzeltmek gerekir. Temas anı, ayak basışı ve kök hareketi birlikte ele alınmalıdır. [5]

## 3. Kodun mevcut durumu

| Alan | Gerçekten mevcut | Eksik veya sınır |
|---|---|---|
| Sahne | Oyun konumundan ayrı sahne koordinatları ve görsel set | Ayrı render hedefi/sahne grafiği değil; dünya çizim olaylarına bağlı |
| Zaman | Sunucu oturumu, kesirli istemci saati, durdurma/arama/yavaşlatma | İki istemci için asset hazır bariyeri yok |
| Oyuncular | Katılımcı rolleri, skin bağlama, kuklalar | Zırh, yüz, parmak, tüm mob rigleri yok |
| Hareket | Kök hareketi, eğrili klipler, bağımsız kanallar | 11 sabit eklem; genel iskelet asset yükleyicisi yok |
| Temas | İki kemikli kol IK ve rol hedefleri | Ayak kilidi, gövde teması, tam beden çözümü yok |
| Kamera | Shot, Bezier yol, FOV, roll, sarsıntı | Bazı geçişler önceki çizim karesine bağlı |
| Efekt | Geometrik küre/parçalanma/çizgiler, post efekt | Gerçek ışık alan hacimli sis ve sahne ışık sistemi yok |
| Oynanış | Katılımcı kilidi ve geri yükleme | Hazırlık/başlama protokolü ayrıca sağlamlaştırılmalı |
| Prova | H ve `/cinematic` komutları | Tam görsel sahne editörü yok |

Mevcut motor boş değildir. Oturum, prova, rol bağlama ve hareket örnekleme korunabilir. En büyük teknik değişiklikler model deformasyonu, asset üretim yolu ve bağımsız sahne renderer'ında gerekir. Kodun tamamını silmek bu faydalı altyapıyı gereksiz yere kaybettirir.

## 4. Bu turda uygulanan temel değişiklik

`PuppetSkinSurface`, üst/alt kol ve bacak kutularının yerine her uzuv boyunca kapalı, kesintisiz bir yüzey çiziyor. Gövde de bel ekleminin iki tarafındaki etkiyi karıştırıyor. Dirsek ve dizde iç kapak yüzleri yok; ardışık yüzey halkaları aynı konum ve ağırlık kuralını paylaşıyor.

Her köşe iki eklemin etkisini taşıyor. Eklem matrisi `animasyonlu_global × ters_bağlama` olarak hesaplanıyor. Bağlama pozunda deformasyon kimlik dönüşümü oluyor. Bu yaklaşım, glTF skinning belgelerinde açıklanan ağırlıklı eklem dönüşümü ilkesinin küçük, mevcut rigle uyumlu bir uygulamasıdır. [1]

Yeni çizim mevcut `PuppetRenderer` içinden kullanılıyor; yalnızca kullanılmayan bir yardımcı sınıf eklenmedi. Mevcut klipler ve el IK sistemi aynı kemik dönüşümlerini sürmeye devam ediyor. Steve/Alex genişlikleri korunuyor. Yüzey boyunca UV, bütün uzvun skin bölgesini izliyor; dirsekte ayrı kutunun üst kapağı çizilmiyor.

Performans yaklaşımı: ağ bir kez üretiliyor; dönüşüm matrisleri ve çizim sırasında kullanılan vektörler tekrar kullanılıyor. Parçacık veya entity oluşturulmuyor. Şimdilik CPU üzerinde iki etkili skinning var. Bu, GPU skinning veya glTF desteği değildir. Mevcut düşük köşe sayısı için başlangıç çözümüdür; gerçek FPS kazanımı ölçülmüş değildir.

Sınırlar: aşırı bükülmede doğrusal skinning hacim kaybedebilir; bilek/parmak/yüz kemiği eklenmiş değildir. Baş ve omuz bağlantısı hâlâ stilize Minecraft anatomisindedir. Farklı mobların mevcut entity renderer'ına düşen yolu bu yüzey rigini kullanmaz. Kum askerlerin dokusu için de ayrıca doğru UV'li bir sahne materyali gerekir.

## 5. Hedef asset üretim yolu

Önerilen çalışma şekli: Blender'da tek sahne içinde bütün rollerin koreografisini hazırlamak; oyun tarafında bunları yeniden kurmak yerine asset olarak oynatmak. Blockbench blok biçimli model tasarımı için kullanılabilir. Ağırlıklı deformasyon, karmaşık çok karakterli etkileşim ve kamera düzeni için Blender merkezli bir üretim yolu araştırılmalıdır.

Önerilen paket:

```text
cinematics/sand_army/
  scene.json          # roller, zamanlama, kalite bütçesi, olaylar
  actors.glb          # iskeletler, ağırlıklı ağlar, hareket klipleri
  set.glb             # bağımsız sahne ve dekor
  cameras.json        # lens, odak hedefi, yol ve kurgu
  effects.json        # kum, ışık, sis, parçalanma zamanlaması
  audio.json          # ses olayları ve ses seviyesi eğrileri
```

**Bu bir hedef sözleşmedir; şu anda bu dosyaları okuyabilen importer yok.** glTF; iskelet, ağırlık ve dönüşüm animasyonları için mantıklı adaydır. Özel kurgu, oynanış olayı ve efekt anlamlarının yanında ayrı metadata gerekir. Importer, desteklemediği kanal/uzantıyı sessizce yok saymamalı; okunabilir hata vermelidir. [1][2]

Rig, sabit 11 indeks yerine adlandırılmış kemiklerden oluşmalı. Omurga, boyun, omuz kuşağı, üst/alt kol, bilek, parmak, üst/alt bacak, ayak ve göz/yüz kontrolleri gereksinime göre eklenebilir. Her modelin her kemiği zorunlu olmamalı. Bir retarget profili, kaynak iskeleti rolün kontrol iskeletine bağlamalı. Küçük/büyük karakterler için ölçek ve temas toleransları açık olmalı.

## 6. Koreografi: savunma gerçekten okunmalı

SandArmy'nin ilk kısa bölümünde yalnızca iki askerin saldırısı işlenmeli. İlk asker yaklaşır; hedef ağırlığını arka ayağa alır, omuzla hazırlanır ve karşı vuruş yapar. Yumruğun eldivene/bedene temas ettiği karede saldırgan tepki klibine geçer. İkinci saldırı farklı yükseklik veya taraftan gelmeli. Kamera bu iki olayı diğer askerlerin arkasına saklamamalı.

Sonra hedef aynı hareketleri tekrar ederek kazanamamalı: üçüncü asker kolu tutar, başka biri dengeyi bozar, hedef birini iterken diğerleri yaklaşır. Yenilgi sayısal baskıdan anlaşılmalı. Bütün askerleri tek bir noktaya ölçekleyerek kaybetmek, üzerlerine atlamayı veya kum dönüşümünü tek başına anlatmaz. Dönüşüm için beden siluetini kum parçalara ayıran, zamanı yazarlı ayrı bir efekt gerekir.

Temas sistemi önce animasyonu örneklemeli; sonra sınırlı düzeltme uygulamalı. Yumruk hazırlığının tamamına IK uygulamak oyuncunun güzel yay çizmesini bozar. Temas penceresinde el sabitlenebilir; çıkışta serbest bırakılır. Ayaklar için de basılı/serbest aralıklar gerekir. Tamamen fizik simülasyonuna bırakılan bedenler iki istemcide farklı sonuç üretebilir; yazarlı hareket ana otorite olmalıdır.

## 7. Sahne ve kamera

Hedef, oyundaki konuma uzak bir set göstermekten daha kapsamlıdır: sinematik kendi görünür nesne listesini, ışıklarını, materyallerini, kamera lensini ve render hedefini yönetmelidir. Son görüntü katılımcı ekranına kompozitlenir. Diğer oyuncuların kamerası değişmez.

Kamera yolları mutlak sinematik zamanından hesaplanmalı. Aynı zamana doğrudan aramak ile baştan oynatmak aynı kadrajı üretmeli. Mevcut kare geçmişine bağlı yumuşatma bu şartı her durumda karşılamıyor. Geçiş, önceki kameranın tanımlı bitiş pozu ile yeni shot'ın örneklenmiş pozu arasında zaman tabanlı olmalı. Lens, odak mesafesi ve FOV da aynı ilkeye uymalı.

Sarsıntı tek bir rastgele gürültü olmamalı: darbe yönü, kısa başlangıç itkisi ve sönüm eğrisiyle kamera dönüşü/konumu ayrılmalı. Uzak patlamanın etkisi yakın yumruktan farklı olmalı. Sürekli titreşim karakter temasını gizlememeli. Yakın plan, aksiyon ekseni ve negatif alan otomatik olarak iyi olmaz; sahne bazında incelenmelidir.

## 8. Sis, ışık ve materyal

Fog mesafesi ve renk değiştirmek, ışık alan hacimli sis üretmekle aynı şey değildir. Hedef sis, sahne derinliğiyle kesilen ve yerel ışık/beam katkısıyla aydınlanan hacim olmalı. Yarı çözünürlükte render, sınırlı adım sayısı, kamera yakınında yüksek detay ve kalite seviyeleri değerlendirilmeli.

Epic'in volumetric fog açıklaması, hacim çözünürlüğü ve ışıkların maliyetini; temporal yeniden kullanımdaki hızlı ışık izlerini özellikle ele alıyor. Bizim çıkarımımız: ani lazer ve patlamalarda geçmiş görüntü ağırlığını azaltmak, düşük kaliteli modda daha basit atmosfer kullanmak gerekir. Belgede başka donanım/motor için verilen süreleri Minecraft FPS garantisine çeviremeyiz. [6]

Materyaller ayrılmalı: kum mat ve pürüzlü; kristal kontrollü ışık yayıcı; lazer parlak çekirdekli; yüz/beden okunabilir gölgeli. Her şeyi full-bright yapmak atmosfer oluşturmaz. Önce sahne ışığı ve kontrast doğru olmalı, bloom sonrasında ölçülü eklenmeli. Oyuncunun başka post efektini ezmemek için efekt sahipliği ve geri yükleme açık tasarlanmalıdır.

## 9. İki oyunculu oturum

Önerilen durum sırası: `PREPARE → READY → PLAY → RESOLVE → RESTORE`.

Sunucu katılımcıların uygunluğunu doğrular, oturum kimliği ve içerik sürümünü gönderir. İstemciler gerekli assetlerin hazır olduğunu bildirir. Belirlenmiş süre içinde hazır olmayan istemci varsa oynanış kilidi temizlenerek iptal edilir. Sunucu ortak başlangıç zamanını duyurur. İstemciler her tick poz paketi beklemek yerine yerel sahneyi bu saate göre örnekler.

Hasar yalnızca sunucunun tek seferlik çözüm olayında uygulanır. Prova modu hasar çalıştırmaz. İstemcinin “animasyon bitti” demesi hasar yetkisi olmamalı. Oturum kimliği, olay kimliği ve çözülmüş bayrağı tekrar paketlerini etkisiz kılmalı. Ayrılma, ölüm, boyut değiştirme ve zaman aşımı aynı geri yükleme yolunu kullanmalı.

Forge SimpleImpl, paket işleme ve dağıtım altyapısını sunar; hazır bariyeri, sahne sürüm kontrolü ve tek seferlik sonuç semantiği bizim uygulamamızın sorumluluğudur. Mevcut per-tick senkronizasyon hazır bariyeri yapılmış gibi sunulmamalıdır. [7]

## 10. Kontrol ve kabul sırası

1. **Deformasyon:** nötr poz, 45/90/150 derece dirsek/diz; iki skin tipi; UV dikişi ve yüzey açıklığı incelemesi. Bu tur matematik testleri eklendi; oyun içi görsel kabul henüz verilmedi.
2. **Tek temas:** iki aktör, tek yumruk, ayak sabitliği ve el teması. Önce yakın ve yan kameradan okunmalı.
3. **Asset yolu:** küçük ağırlıklı model + tek klip importu. Desteklenen format kapsamı açıkça belgelenmeli.
4. **Bağımsız sahne:** bir ışık, bir zemin, kontrollü sis; farklı dünya konumlarında aynı görüntü.
5. **İki istemci:** hazırlık, gecikme, bağlantı kaybı, tekrar paket ve geri yükleme testleri.
6. **Tam koreografi:** savunma → baskı → yenilgi. Önce hareket; sonra efekt/ses.
7. **Profil:** normal oyun ile aynı kamerada karşılaştırmalı CPU/GPU kare süreleri, %95/%99 süreler, actor/vertex/draw-call sayısı. Matematikte 144 Hz örnekleme testi, 144 FPS performans ölçümü değildir.

Bu tur sonrasında mevcut H provası yeni humanoid yüzeyi kullanır. Nihai film kalitesi, tam asset importer, hacimli sis veya tüm koreografi tamamlandı denemez. Bir sonraki geliştirme, aynı ilk temas sahnesini yeni yüzeyle görsel olarak doğrulayıp asset/rig kapsamını genişletmek olmalıdır.

## Kaynaklar

1. [Khronos — glTF Skins](https://github.khronos.org/glTF-Tutorials/gltfTutorial/gltfTutorial_020_Skins.html): joint ağırlıkları ve inverse bind matematiği.
2. [Khronos — glTF Animations](https://github.khronos.org/glTF-Tutorials/gltfTutorial/gltfTutorial_007_Animations.html): kanallar, zaman örnekleri ve interpolasyon.
3. [PlayStation — Marvel Tokon Fighting Souls interview](https://blog.playstation.com/2025/10/03/marvel-tokon-fighting-souls-interview/): sanat ve sunum yönü.
4. [Epic — Dynamic/Custom Binding in Sequencer](https://dev.epicgames.com/documentation/en-us/unreal-engine/dynamic-binding-in-sequencer): rol/aktör bağlama.
5. [Epic — Motion Warping](https://dev.epicgames.com/documentation/en-us/unreal-engine/motion-warping-in-unreal-engine): animasyon penceresinde hedefe uyarlama.
6. [Epic — Volumetric Fog](https://dev.epicgames.com/documentation/en-us/unreal-engine/volumetric-fog-in-unreal-engine): hacim, ışık ve temporal maliyet/sınırlar.
7. [Forge 1.20.1 — SimpleImpl](https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/): ağ altyapısı.

Blender çevrimiçi kılavuzunun bazı sayfaları bu araştırmada erişim hatası verdi. Okunamayan sayfalara dayanarak belirli exporter seçenekleri doğrulanmış gibi anlatılmadı. Referansların sanat eserleri veya animasyon assetleri kopyalanmadı.
