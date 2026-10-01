# Bağımsız Sandman film provası

Bu dosyalar Minecraft rendererından bağımsız kısa film üretiminin ilk çalışır örneğidir. Tam ultimate, nihai kalite örneği veya oyun içi video oynatıcısı değildir.

- `create-film-study.py`: sahne, kum malzemeleri, alan ışıkları, hacim sisi, ana karakter, sekiz asker, oluşum parçaları ve kamera animasyonu üretir. Askerlerin çıkış zamanı, koşu hızı, adım ritmi ve rotası sabit bir rastgelelik tohumu ile farklılaştırılır.
- `render-film-study.py`: yüz detaylarını ekler, hazırlanan sahneden 5 saniyelik 640×360 / 24 fps MP4 prova render eder. Blender 5.2 VIDEO media_type kullanılır. Sessizdir.
- `sand-army-lookdev/sand-army-film-study.blend`: düzenlenebilir kaynak.
- `sand-army-lookdev/sand-army-film-study-v2.mp4`: güncel inceleme videosu. Kol dönüş eksenleri düzeltildi; saç, kaş, göz ve ağız eklendi. Kamera koşan askerlerden uzaklaşır. Önceki MP4 karşılaştırma için korunur.

Oynatım henüz H tuşuna bağlanmadı. Geçerli H provası eski gerçek zamanlı sahnedir. Bu çalışmada mod kodu değiştirilmedi.

Kalite açıkları: askerler şimdilik basit aynı rig varyantları; oluşum hâlâ ölçek büyümesi + tanecik birleşmesi; ana karakterin elleri, yüzü, duruşu, kum yüzey detayları ve koşu ayak temasları nihai değil. Müzik ve ses yok. Bu çıktı referans film/MK/Tokon kalitesinde ilan edilmemeli.

Sahne kullanıcı senaryosunun yalnız ilk komut/ordu bölümünün teknik yorumudur. Rakip içermediği için sabit-rakip görünüş tercihini karara bağlamaz. Film üretiminin sonraki gereksinimleri: karakter tasarımı, ayak basma ve momentumla animasyon, temas koreografisi, kum simülasyonu, final kurgu, ses, yüksek kaliteli render ve iki katılımcıya senkron medya oynatma.

## Onaylanan tasarımın 3B uyarlaması — v3

- Görsel hedef: `sand-army-lookdev/sandman-film-design-v3.png` (kullanıcı onayladı).
- Üretim: `build-approved-design.py`; mevcut film riglerinin hareketlerini koruyarak yeni, eklemlere bağlı geometri oluşturur.
- Kaynak: `sand-army-lookdev/sandman-film-v3.blend`.
- Video: `sand-army-lookdev/sandman-film-v3.mp4`, 960×540, 24 fps, 5 saniye, sessiz.
- Eklenenler: ayrı parmaklar, saç katmanları, göz/kaş/burun, yuvarlatılmış kenarlar, askerlerde ekleme bağlı kum taşı plakaları.
- Sınır: görsel hedefin birebir rekonstrüksiyonu değildir. Yüz oyunculuğu, ayak temasları, gövde oranları, çevre ve kum simülasyonu geliştirilmelidir. H oynatıcısı bu çıktıyı henüz kullanmıyor.
