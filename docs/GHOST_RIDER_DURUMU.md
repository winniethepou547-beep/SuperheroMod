# Johnny Blaze — uygulama durumu

## Kullanıcının kesin kapsamı

- Sol tık: zincir yakın dövüş kombosu. Savaşta zincir kızışır, yanar ve hasarı artar.
- Sağ tık: zincir fırlatılır. Canlıya tutununca sonraki sağ tık hedefi çeker; sol tık Ghost Rider'ı hedefe götürür.
- Shift: yanan motosiklete binme. Sağda yakıt barı. Yakıt bitince oyuncu iner, motor ilerleyip ilk çarptığı şeyde patlar. Motor dik yüzeylere tırmanabilir; arkada ateş izi kalır.
- R ve Q henüz kararlaştırılmadı; atanmayacak.
- Karakter, zincir ve motorun ayrıntılı modeli ve akıcı animasyonu öncelikli. Sinematik video çalışması bırakıldı. GitHub'a gönderilmeyecek.

## Bu aşamada eklenenler

`/superhero hero ghost_rider` kaydı. Oyuncu başına zincir durum makinesi: IDLE/SWING/CAST/LATCHED/PULL/REEL. Üç vuruşluk kombo; başarılı temaslarla 0–10 ısı, boşta azalma. Zincir ucu saniyede 34 blok ilerler; menzil yaklaşık 20 blok. Tutunma 4 saniye içinde seçim gerektirir. Duvar, hedef kaybı, ölüm, kahraman değişimi ve sinematik durumunda zincir sonlandırılır.

İlk görsel prototip: dönüşümlü yönlerde oval, hacimli mesh halkaları; kare başına interpolasyon; ısı ile metalden turuncuya renk geçişi; el savurma pozu; ısı ve seçim yazısı. Lazer/partikül zinciri kullanılmıyor. Mesafe ve halka sayısı sınırlı.

## Henüz tamamlanmayanlar (hazır diye sunulmayacak)

- Kafatası, deri kıyafet, ateş katmanları ve Johnny Blaze model/textureları.
- Zincirin modelin gerçek el kemiğine tam bağlanması; mevcut dünya konumu yaklaşık el hizasıdır. Kombo mesh yolu ve hasar konisinin daha hassas eşleştirilmesi.
- Zincirin kızışma renginden gerçek katmanlı aleve geçişi.
- Motosiklet entity/model, sürüş pozu, süspansiyon, duvar sürüşü, yakıt, iz ve sürücüsüz patlama. Shift henüz kayıtlı değil.
- Birinci/üçüncü kişi, farklı bakış açıları ve iki oyunculu oyun içi doğrulama.

## Oyun içi test sırası

1. İki Ghost Rider'da ayrı ısı ve ayrı tutunma doğrula.
2. Sol tık komboda boş vuruş ısı kazandırmamalı; canlıya temas artırmalı.
3. Sağ tık zincir uçuşu duvarda bitmeli. Tutununca ikinci sağ tık hedefi çekmeli.
4. Tekrar tutunup sol tıkla hedefe git; duvar üzerinden ışınlanmamalı.
5. Ölüm/çıkış/boyut veya kahraman değişimi: kalan zincir olmamalı.
6. Düşey bakışlarda halkalar bozulmamalı, kol Cyclops visor pozunda kalmamalı.

Ağ protokolü 6: istemci ve sunucu aynı mod derlemesini kullanmalı.

## Model ve motosiklet aşaması
Önceki eksik listesini günceller: GhostRiderLayer ile deri kıyafet, ayrı çene/diş/göz çukuru geometrili kafatası, eklemli önkol ve alt bacak eklendi. GhostMaterials model malzemesi ve katmanlı alev yüzeyleri kullanılıyor. Kafa alevi hareket yönünün tersine sönümlü tepki verir.
HellCycleEntity + HellCycleRenderer: uzun maşalı chopper, telli tekerlek geometrisi, V motor, çift egzoz, sele ve gidon; dünya ışığına bağlı metal/deri, emissive far/alev. Shift motoru yaklaşık 6 blok arkadan getirir, 7. tick oyuncu zıplar, 24. tick yakınsa bindirilir. Kapalı yaklaşma koridorunda summon iptal olur. W/S hız, A/D dönüş. 600 tick yakıt, sağda gösterge. Yakıt bitince sürücüsüz ilerler, ilk yatay engel/canlı temasında hasarlı fakat blok yıkmayan patlama. Dik engelde yükselme prototipi var; gerçek yüzey normaline yapışan tam duvar sürüşü henüz yok.
ChainDynamics: 120 Hz Verlet, iki sabit uç, yerçekimi/sönüm/kısıt çözümü. Halkalar eğriye göre yönlenir. 30/144 FPS, sarkma, ters dönüş, sıfır uzunluk, teleport sıfırlama testleri geçti.
SINIRLAR: Modeller kodla oluşturulmuş ilk geçiştir; oyun içi görüntü doğrulanmadı. Zincirin el ankrajı yaklaşık, serbest uç ve bloklarla fizik çarpışması henüz yok. Motor tekerlek dönüşü gerçek katedilen mesafe ile henüz eşlenmedi. Ateş izi şu anda sınırlı parçacık efekti, kalıcı hasar alanı değil. Çevrim içi iki oyuncu/sürücü kaybı/duvar iniş geçişi canlı test bekliyor. Ağ protokolü 7.


## 2026-09-27 — gidon, alev ve kol senkronu düzeltmesi
- Kafa ateşi vanilla fire_0/fire_1 atlası ile yoğun dış taç + iç katman (28 quad). Hareket yönüne bağlı bükülme korunur.
- Tekerlek ateşi motorun yerel arka yönüne çevrildi ve yatay uzatıldı.
- Sürüş gövdesi belden öne eğilir. GhostRidingArms iki eklemli kol çözümüyle iki eli modeldeki gidon tutuş noktalarına taşır. Gidon geometrisi ve el hedefi aynı sabitleri kullanır.
- Aktif zincir kombosunun kol geometrisi artık doğrudan GhostComboMotion.armMatrix üzerinden çizilir; vanilla model açılarının sonraki değişikliklerine bağımlı değildir. Dirsek, çizilen el ve zincir başlangıcı aynı kare zamanını ve dönüşümü kullanır.
- build, ghostChainPhysicsCheck ve ghostMotionCheck geçti. Matematik kontrolleri: iki el-gidon teması, düz zeminde kol uzamaması, geriye yönelen tekerlek alevi, çizilen el/zincir ankrajı, sele hizası, sürekli kombo yolu.
- Bu revizyon oyun içinde görsel olarak doğrulanmadı. Açık istemci yeniden başlatılmadan yeni Java kodu yüklenmez. İki oyunculu test ve duvara tırmanırken gövde/kol oranlarının görsel kontrolü bekliyor.

## 2026-10-01 — R: held hellfire breath
- Hold R on the ground to breathe a widening 18-block flame stream. Movement is locked while aiming remains available; release stops gameplay immediately.
- Low first pulse: 0.5 HP. Already burning targets receive 1.8x breath damage; sustained contact ramps logarithmically, capped at 6 HP per half second. Contact loss resets exposure. Slowness II and 3-second ignition accompany hits.
- Continuous vanilla-fire-textured geometry, braced upper-body/arms and opening jaw. Release dissipates the stream over 9 ticks; capped visual scorch decals remain 45 seconds without replacing world blocks.
- Heartbeat timeout, disconnect, dimension and hero-change cleanup. Network protocol 8: update both client and server.
- Build and motion/chain checks passed, including new damage/cone assertions. Live visual quality and multiplayer gameplay still require in-game verification.

## 2026-10-01 — VFX revision after reference video
The preceding vanilla-atlas flame and coal-block decals have been replaced by a custom
flow/erosion core shader, curved crown/bike flames, continuous breath layers and joined,
world-space cooling soot. See `GHOST_RIDER_VFX_YENILEME.md` for measured validation,
reference findings and explicit limitations. GPU previews are not in-game screenshots.
