# Sinematik sunum kararı — 21 Eylül 2026

## Hedef ve seçilen yol

Tuşa basınca katılımcıların film izlemesi. Sahne, Minecraft dünyası veya gerçek zamanlı kukla çiziminin sınırlarına göre tasarlanmayacak. Ana üretim yolu önceden render edilmiş, ses miksajı yapılmış film; modun görevi bu filmi oynatmak ve oyun sonucunu sunucuda uygulamak olacak.

Blender bir üretim aracı olarak kullanılabilir; oyuna Blender kemikleri aktarmak veya Blockbench model sınırlarına uymak gerekmiyor. Kamera, çok parçalı kum simülasyonu, temas, ışık, sis ve kompozit efektler render sırasında hesaplanır. Film dosyasını oynatmak bunları oyun esnasında yeniden hesaplamaz; yine de video çözme ve doku aktarım maliyeti vardır. Donanım hızlandırması ölçülmeden garanti edilmeyecek.

AI video, seçilen planların üretiminde veya efekt katmanlarında kullanılabilir. Bu oturumda video üreten bağlı bir araç yok. Bir prompt yazmak, üretilmiş veya doğrulanmış video değildir. AI'nın çok kişili temas koreografisini ve karakter sürekliliğini tek seferde kusursuz üretmesi varsayılmayacak.

## Görünüş tercihi

Sabit video hedef oyuncunun skinini veya zombiyi kendiliğinden değiştiremez. İlk filmde sabit Minecraft tarzı rakip önerildi; kullanıcının yanıtı bekleniyor. Hedefin gerçek görünüşü şartsa katmanlı/dinamik render ya da önceden hazırlanmış karakter varyantları gerekir. Bu değişim şeffaf bir video üstüne model çizmek kadar basit değildir: örtüşme, ışık, gölge, kavrama ve kumun karakteri kapatması birlikte çözülmelidir.

## Film üretim sırası

1. Orijinal senaryo korunur: `SANDMAN_ULTIMATE_KULLANICI_SENARYOSU_2026-09-10.md`.
2. Önce 3–5 saniyelik kalite örneği: koşan tek asker, savunma/yumruk, temas anında kafa ve gövdenin kuma dağılması. Bu örnek final senaryodaki yumruk sahibi belirsizliğini sessizce çözmez; kesin çekim öncesi orijinal anlatımdaki çelişki açıklığa kavuşturulur.
3. Kalite örneği: görünür ağırlık transferi, ayağın zemine basması, uzuvların birbirinin içinden geçmemesi, kontrollü kamera yakınlaşması, düzgün yüz/eller, hacimli ve gölgeli kum. Hız çizgileri yalnız vurgu anında kullanılır.
4. Sonra 15 bölümün tamamı tek film kurgusunda oluşturulur: ordu, hedefin savunması, yakalama, düşürme/yığın, Sandman'in yürüyüşü ve kaldırışı, kuma çözülme, küre/diken, bütün kürenin düşmesi, kum içindeki hedef, uzaklaşma.
5. Atmosfer/ışık ve ses ayrı katmanlarda işlenir. Sessizlik, temas sesi, kum sesi ve gecikmeli büyük darbe ritmi tasarlanır. Müzik yalnız kullanım hakkı olan içerikten alınır.
6. Önce düşük çözünürlüklü kurgu kontrolü; sonra 1920×1080, 60 fps kare dizisi ve ses masterı. 60 fps etiketi tek başına akıcılık değildir; hareket gerçek ara karelerle üretilir. Hedef görsel kalite oyun/film referansıdır, eşdeğer kalite garantisi değildir.

## Oynatıcı entegrasyonu

Bu bölüm henüz uygulanmadı. Sunucu: kimlik, iki katılımcı, içerik hash'i, ortak başlangıç zamanı, süre, darbe zamanı ve iptal. İstemciler: aynı yerel filmi önceden açıp buffer hazır yanıtı verir. Yalnız katılımcıların ekranı filmi gösterir. Ses görüntüyle aynı medya saatine bağlanır. Hasar istemcinin "video bitti" mesajına güvenilerek uygulanmaz; sunucu doğrulanmış darbe zamanını kullanır. Kopma/ölüm/oynatıcı hatasında kilitler temizlenir. H provası hasarsız kalır.

MP4/H.264 teslim biçimi seçildi; Java/Forge içinde decoder henüz seçilip ölçülmedi. Bütün kareleri RAM'e veya GPU'ya yükleyen PNG oynatıcı final çözüm değildir. Sınırlı decoder kuyruğu, doğru renk uzayı, ses saati, pencere boyutu değişimi, alt-tab ve temiz kapatma test edilecek. Hazır olma bariyeri tek başına ağ gecikmesini eşitlemez.

## Bu aşamada gerçekten hazırlananlar

- `art/film/render-profile.json`: üretim/teslim hedefleri; durum açıkça preproduction.
- `art/film/encode-film.cjs`: tamamlanmış PNG kare dizisini ve ses masterını doğrulayıp FFmpeg teslim komutunu oluşturur; mevcut dosyayı ezmez. FFmpeg kurulu değilse çalıştırma hata verir. Final film veya oynatıcı oluşturulmuş değildir.
- Instagram referansı web aracıyla alınamadı; izlenmiş sayılmıyor. Dosya olarak paylaşılırsa doğrudan plan plan incelenebilir.

## Teknik kaynaklar

- Blender: https://docs.blender.org/manual/en/5.0/render/output/animation.html — kare dizisi üretimi ve film çıktısı.
- FFmpeg: https://ffmpeg.org/ffmpeg.html — kodlama, ses/video akışları ve donanım seçenekleri.
