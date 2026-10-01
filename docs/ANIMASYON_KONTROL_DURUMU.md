# Animasyon kontrol durumu — 2026-09-08

## Asker grubu: yapılanlar

- Blockbench kaynağı ile runtime aynı iskelet/klip verisini kullanıyor.
- Gövde → omuz → dirsek → el hiyerarşisi; dizler ayrı.
- Büyük saldırı kütlesinin boyutları önceki kaynağa göre %32 küçültüldü; eldeki ebeveyn bağlantısı korunuyor.
- Eğriler şekil koruyan Hermite ara değerlerinden 60 Hz anahtarlara dönüştürüldü. Render karelerinde ara değerleme ve anahtar aramada binary search kullanılıyor.
- Menzilli atışa 8 tick toparlanma eklendi. Aim sıfırlanınca tüm klibin tersten oynanması engellendi.
- Ağır ve menzilli saldırı goal'ları her oyun tick'inde ilerliyor; ağır saldırı cooldown'u goal sorgu sıklığına bağımlı değil.
- Kaynak/runtime eşleşmesi ve geçersiz key zamanları için doğrulama komutu var.

## Kontrol sonucu

Duvar güncellemesi: dikenlerin rastgele tohumu dünya X konumundan çıkarıldı; hareket ederken şekil değişmiyor. Dağılmada gövde, üst kenar, 190 yüzey parçası ve dikenler ortak merkez/ölçeği kullanıyor. Ölçek sıfıra inerken taban yer seviyesinde korunuyor. Bu bir model yeniden tasarımı değil; mevcut parçaların hareket/dağılma tutarlılığı düzeltmesi. Oyun içi görsel kontrol bekliyor.

Çeken el hareket düzeltmesi: zemin sorgusunun blok merkezine yuvarladığı X/Z artık modele uygulanmıyor; yalnızca zemin yüksekliği kullanılıyor. Kum şekilleri istemci tick oranından bağımsız, monoton saatli 8 kayıtlık tamponla örnekleniyor. Bu, 100 ms görsel tampon gecikmesi ekler; sunucu hasar zamanını değiştirmez. El ve kaya aynı şekil sistemini kullandığı için ikisi de etkilenir. `tests/TimedSnapshotBufferCheck.java` düzensiz geliş, yeni paket sırasında geri sarma, bağlantı duraklaması, aynı zaman damgası, bellek sınırı ve temizleme kontrollerini geçti. Oyun içi eğimli zemin ve PvP gecikme değerlendirmesi henüz yapılmadı.

Kum yolculuğu ek güncellemesi: modelin çökme/yükselme eğrisini takip eden 24 adet vanilla kum dokulu model parçası eklendi. Parçalar ayrı entity değil; 48 blok mesafe sınırı ve ortam ışığı kullanılıyor. Tam çöküşte oyuncu modeli gizleniyor. Bu katman üçüncü şahıs görünümü içindir; birinci şahıs el animasyonu ve oyun içi görsel/FPS kontrolü henüz tamamlanmadı. Tam JAR derlemesi geçti.

- Derleme: geçti (mevcut deprecated API uyarıları var).
- Kaynak/runtime doğrulaması: geçti.
- Blockbench: kum dokusu açılıyor; sağ vuruşun hazırlık/temas pozlarında kol ve gövde hareketi gözlendi.
- Oyun içi normal/ranged/giant saldırı tetiklemesi, darbe konumu, hareket sırasında geçiş ve FPS: henüz bu sürüm için doğrulanmadı.

## Bitmiş sayılmayan işler

1. Giant kütlesinin iki el arasında oturmasının görsel kontrolü. Ters gövde eğimi düzeltildi; düz zemindeki geometrik testte alt yüzey 0,014 blok yüksekte, merkez 2,311 blok önde (hasar merkezi 2,4). Eğimli arazi ve hareketli hedef testi hâlâ gerekli.
2. Tüm asker varyantlarının oyun içi görsel testi ve orantı düzeltmeleri.
3. Duvar modeli ve dağılma; çeken elin arazi üzerinde hareketi.
4. Kum yolculuğuna ilk geçiş eklendi: 8 tick ayak hizasına çökme, sunucuda hedef boşluğu kontrolü/transfer, 10 tick yeniden yükselme. Gerçek oyuncu modeline ölçek animasyonu uygulanıyor; ayrılan kum parçaları/eklemli dönüşüm ve oyun içi görsel kontrol henüz tamamlanmadı. Takibe yeni giren oyuncular zaman çizelgesini alıyor; durumlar zaman aşımı/çıkışta temizleniyor. Ağ protokolü 2: istemci ve sunucu birlikte güncellenmeli.
5. Colossus model/eklem/kristal/atış mekanikleri.
6. X-ray sahne aktörleri, kesintisiz zaman çizelgesi, ışık/sis ve temiz çıkış.

## İlham kaynakları

Referansların varlıkları kopyalanmıyor. Ağır hareketin hazırlık ve toparlanmayla okunması, güç arttığında siluetin genişlemesi ve kontrollü kamera temposu tasarım hedefi.

- [Spider-Man 2 — açılış ve Sandman üzerine geliştirici röportajı](https://blog.playstation.com/?p=385660)
- [Marvel Tōkon — resmi tanıtım ve görsel yaklaşım](https://blog.playstation.com/?p=405590)
- [Marvel Tōkon — oynanış değerlendirmesi](https://blog.playstation.com/2025/09/26/marvel-tokon-fighting-souls-hands-on-report/)
