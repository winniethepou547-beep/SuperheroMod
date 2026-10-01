# Motor provası: Blender animasyonu, temas ve model çizimi

Kullanıcının nihai senaryosu değiştirilmeden `SANDMAN_ULTIMATE_KULLANICI_SENARYOSU_2026-09-10.md` dosyasına kaydedildi. Aşağıdaki çalışma o senaryonun nihai uygulaması değildir. Kullanıcının istediği sıraya göre önce motorun üretim/oynatma yolu tamamlanıyor.

## Bu aşamada yapılan

- `art/blender/sand-army-defense.blend`: Blender'da hazırlanmış savunma, ilk karşı vuruş, toparlanma ve ikinci karşı vuruş animasyonu.
- `art/blender-author-defense.py`: düzenlenebilir sahneyi ve geniş/dar oyuncu modellerini tekrar üretir. Çalıştırılması kendi çıktılarını yeniden yazar.
- `defense_wide.gltf` ve `defense_slim.gltf`: Blender'dan dışa verilen ve oyun koordinatlarına dönüştürülen kaynaklar; mod kaynaklarına dahil.
- H provasında hedef rolünün 34–108 tick bölümü bu dosyalardaki `defense_counters` klibini kullanır. Diğer sahne bölümleri hâlâ eski prova koreografisidir.
- Aynı bölümdeki eski üç kol klibi kaldırıldı; animasyonun üzerine ikinci bir kol hareketi binmez.

İşleme sırası artık şu şekilde:

1. Aktörün sahne konumu ve temel pozu örneklenir.
2. Gömülü Blender klibinin bilinen eklemleri temel poza karıştırılır.
3. El temas çözümü bu son pozu düzeltir.
4. Çizimde bilinen eklemlerin hareketi ikinci kez uygulanmaz.
5. Ek kemikler ve konum/ölçek kanalları dosyadan örneklenir.
6. Ağırlıklı model deforme edilir; yüzey normalleri de eklem dönüşümlerini izler.

Temas çözümünün varsaydığı kol uzunlukları bu örnek iskelete göredir. Farklı vücut oranlarının otomatik retarget/IK uyarlaması yapılmış değildir. Yüzey normal değişikliği oyun renderer'ındadır; daha önce üretilen Blender PNG'si bu son oyun çizimini göstermez.

## Kontroller

Blender önizlemesinde gerçek kol/gövde hareketi görüldü. Otomatik kontroller doğrudan glTF örneklemesiyle temas öncesi/sonrası köprü üzerinden örneklenen köşelerin uyuşmasını, pozun iki kez uygulanmamasını, temas düzeltmesinin son çizimde korunmasını ve normallerin sonlu/birim olmasını kapsar.

Oyun içi görsel prova ve iki ayrı istemci testi hâlâ gereklidir. “Motor tamamlandı” veya “nihai sahne yapıldı” sonucu çıkarılmamalı.

Kontroller sırasında çok eksenli quaternion → Euler aktarımında fark yakalandı. `JointRotations` ile ModelPart'ın Rz × Ry × Rx sırasına uygun dönüşüm kullanıldı; aynı düzeltme klip adaptörüne ve el temas çözümüne uygulandı. Son `cinematicMotionCheck build` çalışması geçti; geniş/dar modelde yarım ve tam ağırlıkla örnekleme, temas düzeltmesinin korunması ve deforme normal kontrolleri başarılı.

## Yeni senaryoya geçmeden kapatılacak motor işleri

- Kamera geçişleri, lens ve sis için zaman tabanlı örnekleme tamamlandı; oyun içi görsel kontrol bekliyor.
- Oyuncu/NPC görünüşü ve farklı beden oranlarında temas testi.
- İki istemcinin içerik hazırlığı ve ortak başlangıç bariyeri.
- Gövdenin bölgesel kumlaşması, yığının taneciklere çözülmesi, kürenin bütün düşmesi için yazarlı geometri efektleri.
- Kamera, kum etkileri, ışık ve sesin ortak zaman çizelgesi.
- Oyun içi render ve performans ölçümü.

Nihai senaryo için orijinal metin tek başvuru kaynağıdır. İlk iki yumruğun anlatımında vuran kişi ve dağılan kafanın sahibi bazı cümlelerde farklı okunabiliyor; bu sahne animasyonuna geçmeden netleştirilecek, motor çalışması sırasında varsayımla değiştirilmeyecek.

## Kamera örneklemesi — 10 Eylül ek kontrol

CinematicCameraSampler önceki çizilen kareyi saklamadan kadraj, bakış noktası, FOV, roll ve sis üretir. SMOOTH geçişi önceki çekimin tam bitişinden başlar; hedefin o bitiş zamanındaki koreografi konumu kullanılır. Geçiş süresi çekim süresini aşmaz. CUT lens dahil hemen yeni çekime geçer. Kamera yatışı en kısa açı üzerinden harmanlanır. Darbe sarsıntısı mevcut zaman tabanlı, sınırlı katman olarak korunur.

Her iki çekimde sis tanımlıysa mesafe ve renk harmanlanır. Sis tanımsız olduğunda dünya rendererına bırakılır; bilinmeyen dünya sisi için yapay mesafe üretilmez, önceki çekimin rengi taşınmaz. Böyle bir sınırda atmosferin açılıp kapanması yumuşatılmaz; sürekli atmosfer istenen sahneye taban sis tanımlanmalıdır. Koreografi kaydı olmayan eski sahnelerde bakış hedefi hâlâ canlı entity konumuna bağlıdır; tam tekrar üretilebilirlik aktör yollarının yazılmış olmasını gerektirir.

cinematicMotionCheck build başarılı: hareketli hedefle çekim sınırı, kısa ardışık geçişler, açı sarımı, sis temizliği ve 30/60/144 örnekleme sıralarından sonra aynı ana seek kontrol edildi. Bu bir oyun FPS ölçümü değildir. Oyun içinde kamera/ışık görünüşü ve iki istemcili senkron henüz doğrulanmadı.

## Katılımcı hazırlık bariyeri

Sunucu prepare paketi yollar; her katılımcı geniş/dar glTF modellerini önbelleğe alır, istenen klipleri doğrular ve ilk deformasyon örneğini hesaplar. Yanıt sunucuda gönderen UUID ve oturum UUID ile doğrulanır. İki oyuncuda ikisi, NPC provasında yalnız oyuncu beklenir. Hazırlık sırasında zaman çizelgesi/hasar ilerlemez; karakterler tutulur. Hata veya 200 sunucu tick yanıt gelmemesi mevcut restore yoluyla iptal eder. Eski/tekrarlı/yabancı yanıtlar yok sayılır.

Yeni paketler yönleriyle kayıtlıdır. Ağ protokolü 5 oldu: sunucu ve istemciler aynı yeni mod sürümünü kullanmalı. Bu bariyer glTF/CPU hazırlığı içindir; shader/texture GPU ısınması, görünüş indirme ve gecikmeye göre planlanmış ortak ekran başlangıç zamanı henüz kapsanmaz. İki istemcili ağ testi yapılmadı. Readiness testleri iki katılımcı, NPC, hatalı oturum, tekrar, yabancı yanıt, zaman aşımı ve yükleme hatasını kapsar.

## Zombi provası geri bildirimi

Zombinin modern oyuncu UV alanlarında şeffaf kalan sol kol/bacak yüzeyleri eski sağ uzuv doku bölgelerine yönlendirildi. Soldier ölçeğini sıfırlama yerine zaman çizelgesine dissolve aralığı eklendi: yüzey parçaları kademeli kaybolur ve aktör başına en çok 64 küçük kum küpü dağılıp küçülerek solar. Entity/partikül spawn edilmez. Kuşatmada sabit IK tutuşu 158. tickte biter; askerler dönüşümlü üç kısa yumruk atar.

Zombinin 210. tickte ara yükseliş konumu tanımlandı; yükselişi izleyen çekim sabit sahne noktasına bakarak zeminle yüksekliğin okunmasını sağlar. Patlama sonrası kök yüksekliği .12 ve yatay son pozla yere yerleşir. Önceki yorumda şüphelenilen dönüş ekseni kontrol edildi; ActorPose.body(roll,pitch) sırası gereği son pozun doğru pitch=-90 değeri korundu. Küre, izole sahnede dünya ışığı yerine aktörlerle uyumlu sabit sinematik ışıkla kum dokusunda çizilir.

Bunlar H provasına uygulanmıştır; oyun içinde görsel doğrulama henüz yapılmamıştır.

## Dönüş ve kalabalık hareketi düzeltmesi

ActorPose eklem geçişleri Euler bileşenlerini doğrusal karıştırmak yerine quaternion kısa-yol slerp kullanır; reaksiyon gecikmeleri korunur. El teması omuzun arkasına geçtiğinde IK ağırlığı yumuşakça bırakılır, dirsek kutbu öne alınır. Bu değişikliklerin bildirilen baş tersliği/kol geçişini tamamen çözdüğü oyun görüntüsüyle henüz doğrulanmadı.

Askerlere sabit tohumlu sürekli nefes/gövde/el hareketi katmanı, farklı yürüyüş hızları, kademeli yumruk zamanları ve farklı dirsek açılmaları eklendi. Kum çözülmesinde üçgenler aniden kesilmek yerine kısa bir aralıkta solar. 170 derece ile -170 derece arasında dönüş ve tekrar örneklemede aynı kalabalık hareketi kontrolleri eklendi.

## Blender yön düzeltmesi — 21 Eylül

Export edilen defense klibinin ilk kol quaternion X değeri pozitiftir; yazılan oyun pozu negatif X ile öne kaldırılmalıydı. Blender imported rig eksenlerine yazarken X/Y işaretleri dönüştürülmediği için guard klibi geriye kalkıyordu. blender-author-defense.py giriş açılarını (-x,-y,z) ile dönüştürür; wide/slim dosyaları ve blend tekrar üretildi. Runtime glTF dönüştürücü değiştirilmedi. Başlangıç kol uçlarının -Z ön yarısında ve kafa ileri vektörünün dik/önde olması test eklendi. Yeni Blender temas karesi incelendi; oyun içindeki zombi dokulu bütün sekansın görsel doğrulaması henüz yapılmadı.
