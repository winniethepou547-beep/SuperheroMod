# Sinematik glTF aktarımı — ilk çalışan aşama

> Güncelleme: aşağıdaki ilk animasyon aşamasına ek olarak model aktarımı da bağlandı. Ayrıntılar belgenin sonundaki “Ağırlıklı model aktarımı” bölümünde. İlk aşama açıklamaları tarihsel kapsamı anlatır.

9 Eylül 2026

## Ne yapıldı?

Animasyon oynatıcısı artık `PoseClip` üzerinden çalışıyor. Eski JSON klipleri ve yeni `GltfPoseClip` aynı zaman çizelgesi katmanına takılabiliyor. SandArmy H provasında ilk karşı vuruşun sağ dirseği `counter_elbow.gltf` dosyasından geliyor. Hazırlık, vuruş ve geri çekilme arasındaki dönüşler gerçek zamanlı örnekleniyor. Bu dosya küçük bir aktarım testi; yeni bir film koreografisi veya Blender'dan alınmış bir sanat asseti değildir.

Bu aşama **animasyon aktarımıdır**. Genel glTF model/mesh, materyal, iskelet, GLB ve Blender sahnesi içe aktarma henüz uygulanmadı. Oyuncu modeli önceki turdaki ağırlıklı sinematik yüzeyidir. İlk vuruş dışında sahnenin kalan animasyonları mevcut JSON klipleriyle çalışıyor.

## Desteklenen dosya sözleşmesi

- glTF 2.0 JSON, tek animasyon ve tek gömülü base64 binary buffer.
- `extras.puppetProfile: "identity-rest-v1"` zorunlu. Bu bayrak genel bir iskeleti otomatik uyumlu yapmaz; iskelet eşlemesi yapılmış assetin açık beyanıdır.
- Mevcut 11 eklemin adlarını kullanan `rotation` kanalları.
- FLOAT SCALAR zaman, FLOAT VEC4 quaternion değerleri; saniye birimi.
- LINEAR dönüşler SLERP ile, STEP dönüşler basamaklı örneklenir.
- Accessor byteOffset/byteStride desteklenir; aralık ve hizalama doğrulanır.
- Kanal başına en çok 4096 anahtar, en fazla 600 saniye.
- Döngü, katman ağırlığı, başlangıç/bitiş karışımı, toplamsal dönüş ve zamana doğrudan arama.

Dosya yeri:

`src/main/resources/assets/superheromod/cinematics/gltf/counter_elbow.gltf`

Eklem adları: `head`, `chest`, `hips`, `right_upper_arm`, `right_lower_arm`, `left_upper_arm`, `left_lower_arm`, `right_upper_leg`, `right_lower_leg`, `left_upper_leg`, `left_lower_leg`.

Rotasyonlar mevcut kuklanın yerel eklem eksenlerinde, kimlik dönüşlü dinlenme pozuna göre mutlak quaternion değerleridir. Rastgele bir Blender rigindeki bone roll ve rest dönüşleri aynı değildir. Bu yüzden normal bir Blender exportunu buraya koymak yeterli değildir. Retarget/koordinat dönüşümü ve model importer bir sonraki ayrı iştir.

Desteklenmeyen CUBICSPLINE, translation, scale, sparse accessor, zorunlu uzantı, mesh/skin veya bilinmeyen eklem açık hata üretir. Bunlar sessizce atlanmaz. Şimdilik dosyalar mod kaynaklarından yüklenir; oyun içi yeniden yükleme yoktur.

## Neden quaternion?

glTF dönüş animasyonlarının LINEAR örneklemesinde quaternion SLERP kullanılır. Bu, Euler eksenlerini bağımsız doğrusal karıştırmak yerine dönme yönünü birlikte ele alır. Kaynak: [Khronos animasyon açıklaması](https://github.khronos.org/glTF-Tutorials/gltfTutorial/gltfTutorial_007_Animations.html).

Sonuç mevcut `ActorPose` adaptörüne Euler olarak aktarılır; tamamen quaternion tabanlı, serbest kemik sayılı rig henüz yoktur. Bu geçiş adaptörü mevcut IK ve modellerle uyumluluk sağlar. Aynı asset zamana bağlı örneklenir; önceki karelerin oynatılması zorunlu değildir. Örnekleme sırasında çalışma vektörleri yeniden kullanılır.

## Kontroller

`gradlew.bat cinematicMotionCheck build` şu davranışları kontrol eder:

- saniye/tick dönüşümü ve klip süresi;
- anahtarlar arasındaki quaternion dönüşü;
- yalnızca dosyada tanımlı eklemin değiştirilmesi;
- STEP davranışı ve toplamsal katman ağırlığı;
- 30/60/144 örnekleme sıklığından sonra aynı ana aramanın aynı pozu vermesi;
- taşan buffer view, bilinmeyen eklem/profil ve desteklenmeyen interpolasyonun reddi;
- mevcut skinning, IK, hareket ve zaman çizelgesi kontrolleri.

Bunlar oyun içi görüntü veya FPS ölçümü değildir. Görsel deneme: güncel istemcide hedef NPC'ye bakıp H ile prova; ilk karşı vuruş yaklaşık 2.2–3.7 saniyededir. `/cinematic preview sand_army <hedef> at 2.9` ile vuruş anında durularak incelenebilir. Yetkili/cheats açık test ortamı gerekir.

## Sıradaki kabul edilecek sonuç

Bu yolun sonraki adımı, isim eşlemesi/koordinat profiliyle bir Blender rigini dönüştürmek ve ağırlıklı modelin kendisini de yüklemek. Sonra aynı iki karakterli temas sahnesi assetlerden oynatılmalı. Sis, ışık, hazır bariyeri ve daha uzun ordu koreografisi ayrı çalışma kalemleri olarak duruyor; bu aktarımla tamamlanmış sayılmıyorlar.

## Ağırlıklı model aktarımı — ikinci aşama

`GltfSkinnedModel` eklendi ve `PuppetRenderer` humanoid sahne karakterlerini artık bu dosyalardan çiziyor:

- `src/main/resources/assets/superheromod/cinematics/gltf/puppet_wide.gltf`
- `src/main/resources/assets/superheromod/cinematics/gltf/puppet_slim.gltf`

Bunlar dışarıdan alınmış sanat çalışmaları değil; önceki kesintisiz yüzeyin yeniden üretilebilir glTF örnekleri. Üretim komutu: `node art/generate-cinematic-puppet.cjs`. Her model 1136 köşe ve 568 üçgenden oluşuyor. Mevcut oyuncu skini çalışma zamanında bağlanıyor. Geometriyi Java içinde yeniden kurmak yerine glTF dosyasının POSITION, TEXCOORD_0, JOINTS_0, WEIGHTS_0, üçgen indeksleri ve inverseBindMatrices verileri okunuyor. İskelet parent/child ilişkileri dosyadan geliyor; ebeveynlerin dosyada çocuklardan önce gelmesi zorunlu değil.

Köşe başına dört etki destekleniyor. Kemikler önce hiyerarşiye göre dönüştürülüyor, ardından global matris × inverse bind ile köşeler deforme ediliyor. Çalışma matrisleri ve köşe sonuçları tekrar kullanılıyor. CPU skinning uygulanıyor; GPU skinning henüz yok. Üçgenler mevcut entity materyal grubuna dejenere quad olarak veriliyor. Bu uyumluluk tercihi ek köşe gönderimi yapar; optimize GPU yolunun yerini aldığı iddia edilmiyor.

Model dosyası için `extras.puppetProfile="model-space-v1"` gerekiyor. Ölçüler blok cinsinden, mevcut kuklanın model eksenlerindedir: Y aşağı, nötr ayak yüksekliği 1.5; sahne renderer'ı modelden dünya koordinatına mevcut dönüşümü uygular. Rastgele Blender rigleri için eksen/bone-roll/retarget dönüşümü henüz hazır değildir.

Okuyucu tek skin, tek mesh instance, tek üçgen primitive ve tek gömülü binary buffer kabul eder. FLOAT konum/UV/ağırlık/matris, unsigned byte veya short eklem/indeks desteklenir. En fazla 128 node ve 16384 köşe kabul edilir. Döngülü/çok ebeveynli iskelet, hatalı indeks, taşan accessor, geçersiz ağırlık ve tekil inverse-bind reddedilir. Materyal, morph target, harici buffer, GLB ve model içine gömülü animasyon desteklenmez. Animasyon önceki okuyucudan ayrı gelir.

İskelet kemik sayısı dosyadan okunur; mevcut `ActorPose` adaptörü yalnızca bilinen 11 eklemi sürer. Ek kemikler parent hareketini izler fakat henüz kendi animasyon kanallarına sahip değildir. Bu nedenle “sınırsız rig/Blender sahnesi desteği tamamlandı” denemez.

Yeni kontroller iki modelde bütün nötr köşelerin dosyadaki konuma geri geldiğini, ebeveyn sırasının çözülmesini, başın dirsek hareketinden etkilenmediğini, çömelmenin bir kez uygulanmasını, pozun sıfırlanmasını ve bozuk model reddini doğrular. Oyun içi kamera/kaplama/ışık değerlendirmesi ayrıca gereklidir.

Sonraki somut iş: genel kemik animasyonunu bu iskelet örnekleyicisine bağlamak ve dış üretim aracının koordinat eşlemesini kurmak. Model yolunun çalışması, yeni koreografinin veya sis/ışık sisteminin tamamlandığı anlamına gelmez.

## Genel kemik animasyonu — üçüncü aşama

Model okuyucu artık aynı glTF içindeki adlandırılmış animasyonları da okuyor. Kanal doğrudan dosyanın node indeksini hedefliyor; `ActorPose` listesindeki 11 isimle sınırlı değil. Dönüş (quaternion SLERP), konum ve ölçek kanalları, LINEAR/STEP, döngü ve zamana doğrudan arama destekleniyor. En fazla 32 klip, klip başına 384 kanal, kanal başına 4096 anahtar kabul edilir. Desteklenmeyen CUBICSPLINE açık hata verir.

`RigClipPlacement` gömülü klibi aktörün ortak sahne zamanına yerleştirir: ad, başlangıç/bitiş tick'i, hız, giriş/çıkış karışımı ve döngü. Şimdilik aktör başına tek gömülü rig klibi yerleştirilir. Eski `PoseClip` katmanları ayrıca çalışmaya devam eder.

Steve ve Alex örneklerine `right_wrist` ve `left_wrist` eklendi. Ek ağırlıklar mevcut el yüzeyinin uç bölümüne dağıtıldı; köşe/üçgen sayısı artmadı. `guard_wrists` klibi bu iki kemiği hareket ettirir. SandArmy hedef rolüne 34–110 tick arasında yerleştirildi. Bu kısa bilek salınımı bir aktarım/deformasyon örneğidir; parmak rig'i veya tamamlanmış savunma koreografisi değildir.

Hesap sırası: her örnekte dinlenme TRS'sine dön → glTF kanallarını ağırlıkla uygula → mevcut `ActorPose` dönüşlerini ekle → global iskelet matrislerini hesapla → ağırlıklı köşeleri deforme et. Bu sayede eski koreografi ve IK çalışır. Ancak aynı bilinen kol kemiğini glTF ile de oynatırsan eski poz üstüne eklenir; mutlak el teması düzeltmesi glTF sonrası yeniden çözülmüş değildir. Mevcut örnek yalnızca ek bilekleri oynatarak bu çakışmayı önler.

Yeni testler ek kemiklerin gerçekten köşe hareketi ürettiğini, yalnızca el bölgesinin etkilendiğini, döngü ve sıfır ağırlıkta pozun deterministik kaldığını, yerleşim giriş/çıkışlarının söndüğünü ve yanlış kanal hedeflerinin reddedildiğini kontrol eder. Bu testlerin geçmesi oyun içi görsel onay değildir.

Blender koordinat/bone-roll dönüşümü, retargeting ve tam beden IK hâlâ ayrı işlerdir. Önceki bölümlerdeki “gömülü animasyon desteklenmez” sınırı bu aşamada kaldırıldı; materyal/GLB/harici buffer sınırlamaları sürüyor.
