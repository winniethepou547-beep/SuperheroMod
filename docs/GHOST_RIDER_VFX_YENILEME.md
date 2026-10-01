# Ghost Rider efekt yenilemesi — 1 Ekim 2026

## Referans ve teşhis

Kullanıcının `WhatsApp Video 2026-10-01 at 14.16.00.mp4` kaydı 19.35 saniye.
Kareler `art/ghost-vfx-review/reference-sheet-*.jpg` içinde incelendi.
İlk bölümde beyaz/mavi ışık sütunu ve çevreyi saran katmanlar, orta bölümde
birbirinden ayrı hareket eden altın saat/halkalar, sonunda genişleyen bir etki
alanı ve geride kalan çevre tepkisi görülüyor. Hangi mod/araçla yapıldığı doğrulanmadı.
Referansın varlıkları kopyalanmadı.

Önceki sürümde aynı vanilla fire atlası her kısa şeritte tekrar ediyordu.
Kafada kesişen düz sprite yüzeyleri ve zemindeki kömür dokulu kareler fark ediliyordu.
Bu bir Minecraft zorunluluğu veya hazır efekt kütüphanesi sınırı değildi;
uygulamanın malzeme ve hareket tasarımı yetersizdi.

## Bu sürümde gerçekten değişenler

- `GhostFireMaterial`: yeniden kullanılabilir özel Forge core shader. Harici shaderpack gerektirmez.
- Alev şekli, renkleri ve akan sınırları GLSL ile hesaplanıyor. Alev PNG/atlası kullanılmıyor.
- Farklı hızlarda akan ve birbirini bozan gürültü alanları, sıcak çekirdek ve koyu dış alev.
- Kafa ve motor ateşi: kıvrılan, farklı fazlarda hareket eden bölümlü geometri.
  Mevcut harekete bağlı eğilme/atalet korundu.
- R: tek boyuna malzeme alanı, devamlı akan beş alev katmanı, ayrı hafif duman ve
  yumuşak parlama katmanı. Mesafeye bağlı geometri azaltımı.
- Yanık izi: kömür dokusu kaldırıldı. Dünya koordinatlarında devamlı kurum malzemesi;
  komşu yüzeylerin birleşen kapsamı, kısa süreli köz ve soğuma; 45 saniyede silinme.
- Zemine örnekleme her beş tikte oyuncu başına en çok 64 adayla sınırlı;
  iz sayısı 512, mesafe sınırları mevcut. Kalıcı blok değişimi yok.
- Hasar, tutuşma, yavaşlatma, basılı tutma ve kilit temizleme davranışları değiştirilmedi.

## Doğrulama

- `build/ghost-flow-vfx-final.log`: BUILD SUCCESSFUL; GhostMotionCheck ve GhostChainPhysicsCheck PASS.
- `art/ghost-vfx-review/preview_shader.py`, moddaki aynı GLSL dosyalarını gerçek GPU üzerinde derler.
- 60 püskürtme + 60 kafa alevi karesi üretildi ve örnek kareler görsel olarak incelendi.
  İlk önizleme çok yumuşak bulundu; iç kontrast ve sınır aşınması tekrar düzenlendi.
- Tek yüzey / 25 bitişik yüzey kurum çizimi karşılaştırmasında ortalama kanal farkı
  0.00042/255: materyal UV dikişi görülmüyor. Arazi yükseklikleri bu testin kapsamı dışında.
- RTX 4060 Ti, 960×540, on tam ekran malzeme katmanı GPU mikrotesti yaklaşık 0.49 ms.
  Bu Minecraft FPS ölçümü, çok oyunculu yük testi veya düşük donanım garantisi değildir.
- Ayrıntılı ölçüm: `art/ghost-vfx-review/validation.json`.

## Dürüst sınırlar / oyun içi kontrol

Bu bir tam akışkan simülasyonu ya da ışın yürütmeli hacim değildir: özel shaderlı
hareketli geometri kullanır. Yumuşak parlama gerçek ekran bloom'u değildir.
Sahne derinliğine göre yumuşak kesişim, gerçek çevresel ışık ve ısı kırılması henüz yok.
Son sürümün Minecraft içindeki görüntüsü henüz doğrulanmadı. Önizlemeler oyun ekran
görüntüsü olarak sunulmamalı.

Oyun içi kontrol: gündüz/gece, yandan/önden/arkadan, kafanın okunabilirliği, yürürken
alevin eğilmesi, motor arka izi, R'nin kısa/uzun mesafe ve duvar teması, bırakma,
yeniden basma, merdiven/yarım blok yüzeyleri, F3+T kaynak yenileme ve iki oyuncu.

## Araştırma kaynakları

- [Aka — Simple Fire Shader Breakdown](https://realtimevfx.com/t/simple-fire-shader-breakdown/11213):
  farklı hızlarda örnekleme, hareketli kenar maskesi, dalga ve bağımsız fazlar.
- [NVIDIA SDK effects](https://download.nvidia.com/developer/SDK/Individual_Samples/effects_video.html):
  katmanlı ateş ve zamana bağlı koordinat bozma örnekleri.
- [NVIDIA GPU Gems 3, bölüm 30](https://developer.nvidia.com/gpugems/gpugems3/part-v-physics-simulation/chapter-30-real-time-simulation-and-rendering-3d-fluids):
  hacimsel yaklaşım ve gerçek zamanlı maliyetler; bu sürüm tam simülasyon uygulamaz.

Sonraki kalite kararları yalnızca derlemenin geçmesine değil, oyun içindeki
hareketli görüntüye dayanmalı. Refere edilen videoya eşit kalite iddiası henüz yok.


## 1 Ekim — 1.mp4 / 2.mp4 ve 10–13.png revizyonu

Referanslar yerel videolardan çıkarılan karelerle incelendi. Görsel hedef: dik iki zincir halkası,
ısısız zincirde beyaz hız izi, ısınmış zincirde sarı/turuncu/kırmızı hareketli iz, hareketle savrulan
ve kopan alev uçları. Referans materyalleri mod varlığı olarak kopyalanmadı.

- Kısa sol tık: sağ el çapraz, sol el ters çapraz, iki elle iki zincirli aşağı vuruş.
- Basılı sol tık: altı tick sonra iki dik yan halka ile şarj; açısal hız yavaş başlayıp artar.
  Bırakma mevcut kombo saldırısını başlatır; ısı hasar hesabına taşınır.
- Kol IK hedefleri, zincirin başlangıcı ve sunucu temas yolları ortak hareket tanımını kullanır.
  Son vuruşta iki zincir de temas taramasına katılır; aynı hedef bir vuruşta iki kez hasar almaz.
- Sağ tık çekmede iki el tutuşu ve gövde dönüşü. Şarj/saldırı geçişinde eski iz geçmişi temizlenir.
- Zincir izi yalnızca dış bölümde ve kısa ömürlüdür; ele kadar uzanan opak yelpaze kaldırıldı.
- Son aşağı vuruşta zemine temas varsa kısa alevler ve ince çatlak görseli oluşur.
  Çatlaklar kalıcı blok hasarı değildir; R'nin mevcut yanık izi değiştirilmedi.
- Motor farı siyah dairesel çerçeve ve turuncu mercek oldu.
- Kafa ve motor alevleri hareketli yoğunluk alanı, yükselen dağılan kümeler ve kopan küçük
  sıcak parçacık geometrisi kullanır. Kafatası kendi ateşi altında gece okunabilir tutulur.
- R ana alevi kesişen uzun şeritler yerine örtüşen yoğunluk hacimleriyle çizilir.
  Bu, ekranı kaplayan sınırlı örneklemeli shader tekniğidir; tam fiziksel sıvı simülasyonu değildir.
- Protokol 9: sunucu ve istemci aynı yeni mod jarını kullanmalı.

Doğrulama: build, ghostMotionCheck, ghostChainPhysicsCheck geçti.
Aktif el, çapraz yönler, iki el tutuşları, şarjda dik düzlem ve hızlanma, dönüş kovaryansı,
kol/zincir bağlantısı, koltuk ve gidon temasları, farklı kare hızlarında zincir fizik davranışı kontrol edildi.
GPU üzerinde güncel GLSL 60 alev + 60 kafa alevi karesiyle derlenip görüntülendi;
ilk kontrolde görülen dikdörtgen sınırlar ilave kenar sönümüyle düzeltildi.
960x540 çözünürlükte 10 tam ekran hacim katmanı yaklaşık 1.21 ms (RTX 4060 Ti).
Bu Minecraft FPS ölçümü değildir. GPU çalışma görselleri art/ghost-vfx-review içinde;
oyun ekran görüntüsü değildir. Nihai Minecraft görünüşü ve çok oyunculu akış bu turda
canlı test edilmedi; referans kalitesine eşdeğerlik iddiası yok.
