# Sand Army — çok aktörlü koreografi provası

Bu sürüm tam bitirici değildir. Motorun aynı anda 12 görsel aktörü (Sandman, hedef, 10 asker) yönetmesini denemek için 15 saniyelik ayrı bir sahnedir. Mevcut X klon yeteneği değiştirilmedi.

## Test

Güncel JAR ile oyunu yeniden başlat. Hileler/operatör yetkisi gerekir. Yakında başka bir oyuncu varsa:

`/cinematic preview sand_army OyuncuAdi`

Tek oyunculu kök hareketi denemesi için yakındaki bir canlı seçilebilir:

`/cinematic preview sand_army @e[type=minecraft:zombie,sort=nearest,limit=1,distance=..32]`

Erken çıkış: `/cinematic stop`

Tam dirsek/diz koreografisi oyuncu kuklasında, kum askerlerinde ve artık zombi ailesinde uygulanır (zombileşmiş piglin hariç). Zombiler kendi dokusunu kullanır; zırh, eldeki eşya ve drowned dış katmanı henüz bu kuklada çizilmez. Diğer mob türleri kendi modellerini korur; yalnızca kök konum, ölçek ve gövde eğimini alır.

## Bu provada olanlar

- Askerler kademeli ölçeklenerek belirir; bu, son kum birleşme efektinin yerine kullanılan geçici oluşumdur.
- On asker gecikmeli koşu hareketleriyle çembere gelir; kollar ve dizler ayrı pozlanır.
- Hedefin savunma, geriye tepki ve tutulma pozları vardır.
- Sandman elini kaldırınca hedef ile askerler birlikte üç blok yükselir.
- Altı kamera çekimi; eğri yollar, sahne zamanına bağlı sarsıntı ve salınım.
- Sahne mesafesi sekiz blok sabittir. Başlangıçtaki gerçek oyuncu mesafesi koreografiyi uzatmaz.
- Gerçek asker entity'si oluşturulmaz; prova hasar ve dünya yıkımı üretmez.

## Henüz olmayanlar

Elle tutma noktalarının IK ile tam eşleştirilmesi, ilk askerleri parçalayarak savuşturma, tam temas uyarlaması ve bitirici hasar akışı henüz tamamlanmadı. Kum askerleri şimdilik eklemli temel insan rigini ve vanilla kum dokusunu kullanır. Nihai model/ışık/sis kalitesi değildir. Çevre hâlâ mevcut dünyadır; ayrı sinematik dekor geçişi yoktur. Seyirciler bu görsel sahneyi henüz almaz.

## Mühendislik ve doğrulama

CinematicActorTrack: adlandırılmış rol, gerçek katılımcı veya kum figürü bağlama, kopyalanmış poz anahtarları, konum/yaw/ölçek, rastgele zamana erişim. Ölçek ve konum yumuşak geçer; yaw kısa dönüş yolunu seçer. 32 aktör bütçesi, yinelenen rol/katılımcı reddi ve sırasız/geçersiz anahtar denetimi vardır. Kamera aktörün görsel konumuna bakar.

2026-09-08: cinematicMotionCheck ve build başarılı. 12 aktör, 300 tick, hasar olayı bulunmaması, 30/60/144 FPS örnekleme sırası, eşzamanlı yükseliş, yaw sarımı, poz kopyalama ve geçersiz anahtar reddi kontrol edildi. Bunlar oyun içi görüntü testi veya FPS ölçümü değildir. Görsel inceleme ve iki istemcili test bekliyor.

## Küre ve parçalanma aşaması
225–245 tick: askerler merkeze çekilip küçülürken katı kum küresi büyür. 245–260: farklı açılarda dikenler kademeli açılır. 260: küre 32 görsel parçaya ayrılır; hedef 281. tick civarında yere iner. 295: tüm görsel parçalar temizlenir. 300: prova biter. Dönüşüm şu anda rig ölçeği ve merkez hareketiyle sağlanır; gerçek mesh erimesi değildir. Hedef düşüşü anahtar pozlarla kontrol edilir. Hasar verilmez.

SetPiece yaşam döngüsü, diken aralıkları, küre kapandığında askerlerin gizlenmesi ve hedefin iniş konumu testlere eklendi.

Son doğrulama: cinematicMotionCheck + build başarılı (2026-09-08 20:47). runClient kaynaklardan başladı, ana menü yüklemesinden sonra normal çıkış yaptı (exit 0); prova sahnesi görsel olarak incelenemedi. Bu nedenle görüntü kalitesi ve gerçek FPS henüz doğrulanmış değildir.

## H tuşuyla tek oyunculu test
Hileler açık / operatör yetkisi gerekir. Yakına yetişkin zombi koy, 24 blok içindeyken yüzüne/gövdesine bak ve H'ye bas. Tekrar H sahneyi durdurur. Ayarlar > Kontroller bölümünden tuş değiştirilebilir. Boşluğa veya duvar arkasına bakarken başlatılmaz. Bu bir prova olduğu için hasar yoktur. Yeni ağ sürümü nedeniyle çok oyunculuda iki taraf da güncel JAR kullanmalıdır.

## El temasları

Her asker için sağ/sol el temas aralığı tanımlandı. Temas noktası hedefin göğüs uzayında tutulur; hedef eğilirken/yükselirken onunla taşınır. Omuz ve dirsek iki kemikli IK ile çözülür, kısa bir yumuşak geçişle poz animasyonuna karışır. Erişim dışındaki hedeflerde kol boyu değişmez. Birleştirme başladığında tutuş çözülür. Asker çemberi kol erişimine yaklaştırıldı.

Oyuncu kuklasının bacak başlangıç pivotu da düzeltildi: kalça y=12 iken üst bacak ayrıca 6 piksel aşağıdan başlamamalıydı. Ayaklar artık nötr pozda y=24 hizasında.

Bu adım gerçek parmak kavraması, ten teması, çarpışma önleme veya tüm NPC modellerine uyarlama içermez. Elin geometrik ucu ile çözülen kol ekseni arasında modelin yarım genişliği kadar fark olabilir; oyun içi görsel kontrol bekliyor.

## 2026-09-09 — sunum revizyonu

Güncel prova 330 tick / 16.5 saniye ve dokuz çekimdir. İlk tekli asker, ters yönden ikinci geliş, çiftli dalgalar, artan sayı, alçak açıdan yığın, Sandman yakın planı, kaldırış, küre ve uzun sonuç çekimi ayrıldı. Askerler yığın aşamasında farklı yüksekliklere çıkar; bu koreografik yerleşimdir, fiziksel tırmanma/temas çözümü değildir.

CinematicImpact: başlangıç, süre, şiddet, frekans ve yön. Altı darbe kaydı kamera yaw/pitch/roll ve kısa FOV genişlemesini sürer. Kare başına rastgele seçim yapılmaz. Açı toplamları sınırlandırılır. Foreground katmanı kısa, açık renkli çizgileri yan bölgelerde hareket ettirir; darbe flaşı en fazla %14 opaklıktadır. Bunlar sürekli karartma veya letterbox değildir. Şimdilik büyük 3B kamera önü kum parçaları ve volumetrik sahne ışığı yerine sınırlı ekran geometrisi kullanılıyor.

Shot.transition(seconds) ile geçiş süresi çekime göre ayarlanabilir. Kürede 0.32 saniyelik yumuşak geçiş, sonuç çekiminde 0.12 saniyelik hızlı geçiş kullanıldı. Sarsıntı ve görsel efekt zamanlaması aynı sahne saatini kullanır.

Ayrı cinematic environment, gerçek humanoid-to-sand mesh çözülmesi, tam temas/çarpışma düzeltmeleri ve referans kalitesinde görsel doğrulama henüz tamamlanmadı. NPC combat bu turda değiştirilmedi. H tuşuyla NPC provası devam eder; hasar yoktur.

## Thragg / Invincible referansı — karşılıklı koreografi

Kaynak: DALRI, Thragg vs Invincible my fan animation: https://www.youtube.com/watch?v=VKmzkPC78ss . Tarayıcıda video açıldı ve seçili kareleri incelendi: yakın yüz kadrajı, çapraz uçuş/çoklu saldırgan yerleşimi ve güçlü perspektifli uzanan el. Tam hareket çözümlemesi veya bütün karelerin ölçümü yapılmadı. Sandman'in orduyu yönetmesi ve hedefin önce başarılı savunup sonra yenilmesi, kullanıcının bu referansa yönelik uyarlama talebidir.

Yeni koreografi: ilk iki asker 58. ve 89. tick'te karşı darbeyle geri savrulur; yığına geri dönmez. Hedef önce karşı yumruk, sonra yana kaçış ve ters kol darbesi yapar. Sonraki dalga altında savunması kırılır. Sekiz kalan asker sıçrayarak katmanlı yığına katılır. Kontrol pozu -> başarılı savunma -> baskının artması -> bastırma -> kaldırma/küre/final zinciri korunur.

Motor değişikliği: her kök hareket segmentine ayrı Easing ve yay yüksekliği atanabilir. Eklem pozu interpolasyonu bu hız eğrisinden bağımsızdır. Koşuda doğrusal kök hareketi, karşı darbede hızlı başlayıp yavaşlayan savrulma, yığına katılmada parabolik sıçrama kullanılıyor. Bu, tam fizik veya animasyon retargeting motoru değildir.

Testler: karşı darbeyle uzaklaşan askerlerin geri görünmemesi, sekiz tutucu için 16 el bağlaması, yayın orta ve bitiş noktaları eklendi. İlk iki askerin yok oluşu hâlâ ölçekle çözülür; gerçek uzuv parçalanması ve görsel temas hassasiyeti sonraki eksiklerdir.

## 9 Eylül — son durum ve gerçek görsel kontrol

Önceki bölümlerin aksine artık görsel sahne haritanın üstünde ayrı bir stüdyoda
çiziliyor; gerçek oyuncu/NPC yerinden taşınmıyor. Oyun içi testte haritadaki kraterin
kadrajda olmadığı, HUD ve birinci şahıs elinin gizlendiği, normal kamera ve HUD'ın
prova sonunda geri geldiği görüldü. Yetişkin husk ile seçilen 2,9 saniyede duraklatılmış
başlatma çalıştı; ardışık gözlemlerde sahne aynı karede kaldı.

Bu inceleme ilk asker yüzünden hedefin kapandığını gösterdi. Sonraki kaynak revizyonu
ilk iki kamerayı profilden bakacak şekilde değiştirdi ve sonraki askerleri geciktirdi.
Sonrasında dört JSON animasyon klibi ve kanal katmanı eklendi; bu **son** klip/kadraj
revizyonu henüz oyunda görsel olarak tekrar incelenmedi. 9 Eylül 20:02 derlemesinde
cinematicMotionCheck + build başarılı. Test istemcisi artık açık değil.

Güncel temas sayısı 18: sekiz tutucunun iki eli + iki karşı yumruk. Kontroller
SINEMATIK_PROVA_KONTROLLERI.md, klip formatı ve sınırları
SINEMATIK_ANIMASYON_KLIPLERI.md dosyasındadır. Kalabalık içindeki hedef görünürlüğü,
kum figürlerinin nihai malzemesi, katı parçalar yerine gerçek kum dönüşümü, kaliteli
ışık/sis ve bütün sahnenin hareket halinde kontrolü hâlâ tamamlanmamış işlerdir.
