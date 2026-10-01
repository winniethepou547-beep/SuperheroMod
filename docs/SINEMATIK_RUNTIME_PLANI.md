# Sinematik motoru: inceleme, referans ve uygulama planı

Tarih: 2026-09-08. Bu belge tamamlanmış bir AAA motoru iddiası değildir. Çalışan parçalarla henüz uygulanmayanları ayrı tutar. Kullanıcının tam görevi SINEMATIK_GOREV_METNI.md dosyasındadır. GitHub'a yükleme yapılmayacak.

## Doğrudan video referansı

Kullanıcının verdiği video: https://www.youtube.com/watch?v=4Rs9Id-ByfI
Başlığı: Marvel Tokon Fighting Souls - All Supers & Ultimate Attacks (4K 60FPS).
Tarayıcıda bölüm listesi okundu; Spider-Man ve Doctor Doom bölümlerinden kareler görsel olarak incelendi. Videonun tamamının izlendiği veya tüm hareket sürelerinin ölçüldüğü iddia edilmiyor. Başlıktaki 60FPS, modda ölçülmüş bir performans sonucu değildir.

- 07:11 Spider-Man: geniş hareket pozu, kırmızı grafik alan ve beyaz yön çizgileri. Saldırının başlatıldığı anın kendine ait görsel kimliği var.
- 07:16 civarı: yüz çok yakın kadrajda; beyaz hız çizgileri arka planda. Gövde/lens yakınlığı ve kadraj, oynanışın sabit yan kamerasından farklı.
- 07:19–07:21 örneklerinde normal yan dövüş kadrajına dönüş görülüyor. Sinematiğin giriş kadar çıkışının da tasarlanması gerekiyor.
- 13:44 Doctor Doom: göz yakın planı, yüzey ve bakış vurgusu.
- 13:49: el ön planda büyük, patlama arkada; parmak/el silueti ışık içinde okunabiliyor. Efektin gücü tüm ekranı sürekli örterek anlatılmıyor.

Bunlardan çıkarılan tasarım kararı: kamera, aktör, grafik hız çizgileri, ışık ve hasar aynı zaman çizelgesinin farklı kanalları olacak. Hız çizgileri dünyaya yayılan Minecraft parçacıkları olmayacak. Referansın varlıkları veya dokuları kopyalanmayacak.

## Mevcut kodun denetimi

| Parça | Bulgu | Karar |
|---|---|---|
| StageFrame | Saldıran/hedefe göre konum ve yön kuruyor | Koru; sabit sahne ölçeği seçeneği ekle |
| Shot | Konum, bakış hedefi, FOV, roll, sis, kesme/yumuşatma var | Koru; eğri yollar, ayrı bakış yolu, süreli geçiş ekle |
| ActorPose/PuppetModel | 11 eklem, dirsek/diz/bel ve gecikmeli tepki zinciri var | Koru; el/bilek/parmak ve rig bağlama katmanı ekle |
| CinematicClient | Kuklalar gerçek varlığın konumunu kullanıyordu | İlk ayrıştırma yapıldı; görsel hedef artık zaman çizelgesinden hesaplanıyor |
| CinematicDirector | Gerçek hedefi teleport/setPos ile uçuruyordu | Oynanış hareketi çağrısı kaldırıldı; görsel hareket örnekleniyor |
| Durum geri yükleme | Yalnız saldıranın açıları geri veriliyordu | Fizik, yerçekimi, AI, dokunulmazlık, hız, açılar için snapshot eklendi |
| Hasar | Her DAMAGE olayı ayrı çalışabiliyordu | Oynatma başına bir hasar uygulaması; yalnız sunucu |
| Senkron | Her pakette saat tamsayı tick'e dönüyordu | Kesintisiz saat ve sınırlı hız düzeltmesi eklendi |
| Proxy kapsamı | Yalnız oyuncu | Mob modeli korunarak konum/gövde dönüşü fallback eklendi; mobun özel eklem koreografisi henüz yok |
| PostFx | Global GameRenderer efektini değiştiriyor | Başka shader zincirlerinin sahipliğini koruyan ayrı hedef gerekli |
| Çok aktör | ATTACKER/TARGET ile sınırlı | Rol kimlikli sınırsız değil, bütçeli çok aktör sistemi gerekli |
| Yazarlık | Java tanımlar | Doğrulamalı JSON formatı ve geliştirme komutları gerekli |
| İzleyiciler | Sadece katılımcılara senkron | Oturum kimliğiyle ayrı izleyici sunumu gerekli |

## Bu aşamada uygulanmış temel

CinematicMotion saf bir değerlendiricidir: aynı zaman ve sahne aynı konumu verir. Oynatma sırasına, FPS'e ve önceki kareye bağımlı değildir. MOVE, LAUNCH ve FREEZE olaylarının arasını hesaplar. Eski Cyclops tanımı bu köprü üzerinden çalışır. Bu, çok aktörlü final motorunun tamamı değil, eski motorun fizik bağımlılığını kaldıran ilk adımdır.

CinematicActorSnapshot iki katılımcının sahip olunan durumlarını tutar. Bitirme, bağlantı kopması, boyut değişimi ve yakalanan çalışma hatalarında geri yükleme çalışır. Sinematik sırasında hareket girdisi, normal saldırı ve yeni yetenek aktivasyonu engellenir. Hasar verilmesi sırasında hedefin sinematik öncesi dokunulmazlık tercihi dikkate alınır.

## Yeni runtime sözleşmesi

1. CinematicSession: benzersiz oturum UUID, tanım kimliği/sürümü, sunucu başlangıç zamanı, rol bağları, sahne çerçevesi, sonuç uygulandı bayrağı, durum snapshotları. Aşamalar: hazırlık, oynatma, toparlanma, iptal, bitti. Restore idempotent olmalı.
2. ActorBinding: attacker/target/soldier_01 gibi rol kimliği, kaynak oyuncu veya geçici rig, skin/material, sahne ölçeği, görünürlük. Preview modeli gerçek oyuncuya bağlanabilmeli.
3. ActorTrack: kök konum, quaternion dönüş, ölçek, eklem pozu, eklem gecikmesi, eklem hedefi. Anahtarlar mutlak zamanlı. Eğriler doğrusal, cubic/quintic, bezier; şiddetli sıçrama yalnız bilinçli kesmede.
4. CameraTrack: bağımsız position/lookAt eğrileri, FOV, roll, geçiş süresi, dolly/orbit/crane/pullback. İki aktörlü kadraj, ayrı aktör takip hedefi. Whip pan bir kamera yolu; rastgele kesme değil.
5. FXTrack: model parçasına veya sahneye bağlanan mesh, ribbon, yumuşak sis, debris, ışık, hız çizgisi. Başlama/bitiş ve tohum sabit. İleri/geri seek mevcut efekt durumunu yeniden kurmalı; eski parçacık listesi biriktirmemeli.
6. GameplayTrack: gerçek hasar ve sonuç yalnız sunucuda. Preview/seek hiçbir zaman tekrar hasar vermemeli. İstemcinin gönderdiği hasar veya hedef konumuna güvenilmemeli.
7. Debug: pause/resume, restart, seek, shot atlama, hız seçimi, kamera yolu ve socket gösterimi. Gerçek savaş oturumundan ayrılmış preview modu.

## Sand Army Finisher: yazarlık sırası ve kabul koşulları

Hedef süre ilk prototip için yaklaşık 14 saniye; veriyle değiştirilebilir. 17 olayın her birini ayrı sert kamera kesmesine çevirmemek gerekir. Yaklaşık 7 ana çekim altında gruplanacak.

1. Kuruluş ve çağırma: Sandman uzakta, hedef önde; yerden asker oluşumu. Siluetler oluşmadan koşu başlamaz.
2. İlk hücum: iki asker farklı zamanlarda ulaşır. Hedef birini vurur, diğerinden sıyrılır. Temaslar el/gövde socketlerine bağlı.
3. Baskı artışı: ikinci ve üçüncü dalga, arkadan gelen darbe; hedef diz çöker. On asker aynı anda aynı pozda yürümemeli.
4. Kuşatma ve tutma: omuz/kol/bacak rolleri ayrı. Hedef hareket edince tutan el temas noktası birlikte hareket eder. Rastgele entity çarpışmasına güvenilmez.
5. Yığılma: 10 asker önceden belirlenmiş konumlara yerleşir; üst üste AI entity spawn değil. Hedefin kaçmaya çalıştığı küçük hareketler okunur.
6. Sessizlik ve yaklaşma: Sandman düşük açıdan, sakin; bir elini kaldırmadan önce omuz-dirsek-bilek zinciri ve küçük bekleme.
7. Yükseliş: kum yığını aynı anda kopmaz; küçük parçalar önce, ana kütle sonra. Kamera crane ile yükselir.
8. Küreye sıkışma: insan siluetleri küçülüp çözünerek kum kütlesine karışır. Sadece modelleri aniden saklamak yeterli değildir.
9. Küre ve dikenler: yüzeyde akış, kısa iç siluet, sonra farklı yönlerde ve gecikmelerle diken. Dikenlerin hepsi dik ve eşit uzunlukta değil.
10. Açılma, düşüş, darbe: Sandman eli açar, küre dağılır; hedef kontrollü ivmelenen yolda düşer. Hasar son darbe anında bir kez. Toz dağıldıktan sonra kısa son kadraj ve giriş kontrolüne dönüş.

Bu bölümün tam uygulaması henüz yok. Sonraki işlem rol kimlikli aktör ve JSON track sistemini bağlamak; ardından bu çekimleri tek tek görsel test etmek. Yeni sahne bitmeden Sandman X üzerindeki mevcut giant çağırmayı sessizce değiştirmemek gerekir; geliştirme komutuyla ayrı denenmeli.

## Performans bütçesi

Başlangıç bütçesi, ölçülmüş sonuç değil: iki ana rig + en fazla on yardımcı rig; ortak mesh/texture; kare başına yeniden model bake yok. Kum küresi tek mesh; yüzey akışı materyal/UV ile. Sis düşük çözünürlüklü ve sınırlı katmanlı; çok sayıda saydam tam ekran katmandan kaçın. Görünmeyen askerleri cull et. Network tam kemik pozlarını her tick göndermemeli; sahne kimliği, zaman ve değişen bağlar yeterli. Ölçümler aynı sahnede shader kapalı/açık ve farklı FPS sınırlarında yapılmalı.

## Araştırma kaynakları ve projeye uyarlama

- Epic, Custom/Dynamic Binding: https://dev.epicgames.com/documentation/en-us/unreal-engine/dynamic-binding-in-sequencer
  Preview aktörünü çalışma anındaki oyuncuya bağlama yaklaşımı, rol kimlikleri için referans. Minecraft'a Unreal API taşınmıyor.
- Epic, Camera Cut Track: https://dev.epicgames.com/documentation/en-us/unreal-engine/cinematic-camera-cut-track-in-unreal-engine
  Kamera seçimi ile kamera dönüşümünü ayırmak ve çekimler arası blend, bizim Shot uzantısına temel oluyor.
- Epic, Root Motion: https://dev.epicgames.com/documentation/unreal-engine/root-motion-in-unreal-engine
  Konumu yalnız fizik hızından üretmemek; görsel kök hareketini animasyona bağlamak açısından incelenecek kaynak.
- Epic, Motion Warping: https://dev.epicgames.com/documentation/unreal-engine/motion-warping-in-unreal-engine?lang=en-US
  El/hedef temas noktalarının değişen katılımcılara uyarlanması açısından ilgili kaynak; bu adaptör henüz uygulanmadı.
- Forge, networking: https://docs.minecraftforge.net/en/latest/networking/
  Görsel istemci durumu ile sunucu oyun sonucunun ayrılması. Bu proje 1.20.1 API'sinde derlenir; yeni sürüm örnekleri birebir kopyalanmaz.

Dört Epic sayfası doğrudan okundu. Root Motion, kök kemiğinin animasyonla taşınmasını ve fizik modunun hareketi nasıl etkilediğini açıklıyor. Motion Warping, belirli animasyon zaman aralıklarında adlandırılmış konum veya kemik hedeflerine hizalamayı açıklıyor; el tutma ve vurma temaslarını katılımcı boyutuna uyarlarken bu yaklaşım kullanılacak. Minecraft uygulaması ayrı yazılacak. Video kareleri motorun nasıl kodlandığını kanıtlamaz; onlardan çıkarılan mimari tercihler bizim tasarım kararlarımızdır.

## Doğrulama

2026-09-08: colossusPoseCheck, cinematicMotionCheck ve build başarılı. Root-motion kontrolü 30/60/144 FPS örnekleme sıralarını, freeze, launch ve döndürülmüş sahneyi denetler. Bu bir FPS benchmark değildir.
Oyun içinde yeni krater, kalıcı kaya, R sisinin görünümü ve yeni sinematik kök hareketi henüz görsel olarak doğrulanmadı. İki gerçek istemciyle disconnect/ölüm/hasar ve kamera geri dönüş testi de bekliyor.

## Kamera yolu ve zaman tutarliligi — 2026-09-08

Kamera konumu artik Shot.positionAt ile orneklenir. Shot.curve dort kontrol noktali kubik Bezier yolu tanimlar; mevcut duz yollar geriye uyumludur. Maximum Power'in iki yaklasma cekimi bu yolu kullanir. Sarsinti ve kamera nefesi render basina rastgelelik / duvar saati yerine ortak sahne zamanindan hesaplanir. Bu degisiklik karakter animasyonu veya cok aktorlu Sand Army sahnesinin tamamlandigi anlamina gelmez.

CinematicMotionCheck: Bezier uc noktalar, orta nokta, sinir disi zaman, gecersiz kontrol noktasi, kamera ofset siniri, 30/60/144 FPS ornekleme sirasi ve sessiz kamera kontrolu eklendi. Oyun ici kadraj ve performans olcumu yapilmadi. Cok aktorlu koreografi, temas/IK, kure donusumu ve tam Sand Army sahnesi halen bekliyor.
