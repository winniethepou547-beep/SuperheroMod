# Blender sinematik çalışma dosyası ve doğrulanmış aktarım

Blender 5.2.1 resmî Blender Foundation winget paketinden kuruldu. Çalıştırılabilir dosya:

`C:\Program Files\Blender Foundation\Blender 5.2\blender.exe`

## Hazır çalışma dosyası

`art/blender/cinematic-puppet.blend`

Bu dosya gerçekten Blender'da oluşturulup kaydedildi. İçinde oyuncu biçiminde ağırlıklı yüzey, 13 kemikli armature, iki bilek kemiği, `guard_wrists` ve Blender'da eklenen `contact_test` animasyonu bulunur. Stüdyo kamerası/ışığı ve kum renkli düz bir önizleme materyali de vardır. Bu materyal oyuncu skini değildir; oyunda görünüş role göre bağlanır.

`art/blender/puppet-preview.png`, 16. karede bükülen kolların Blender render'ıdır. Bu bir aktarım ve deformasyon provasıdır; tamamlanmış Sandman tasarımı veya film sahnesi değildir.

Dosyayı Blender'da açmak için File → Open ile `.blend` dosyasını seç. `Armature` nesnesini seçip Pose Mode'a geçerek kemikleri düzenleyebilirsin. Zaman çizelgesinde 1–25 arasında hareketi incele. Bu turda kendin bir şey hazırlaman gerekmiyor; sonraki geliştirme için düzenlenebilir başlangıç dosyası hazır.

## Bu turda gerçekten yapılan kontrol

1. Modun örnek glTF modeli editör koordinatlarına dönüştürüldü.
2. Blender modeli içe aldı; 13 kemiğin bulunduğu doğrulandı.
3. Blender içinde iki üst/alt kola anahtar kareler yazılarak `contact_test` oluşturuldu.
4. Model ve animasyon Blender'ın kendi glTF exporter'ıyla dışa verildi.
5. Dönüştürücü sonucu mod koordinatlarına çevirdi.
6. `gltfAssetCheck`, bu gerçek dışa aktarımı modun `GltfSkinnedModel` okuyucusuyla açıp deformasyonunu örnekler. Sadece JSON sözdizimi denetlenmez.

Bu kontrol Minecraft ekranında görsel oynatma testi değildir. Blender render'ı incelendi; oyun içindeki kamera/ışık görünüşü ayrıca test edilmelidir.

## Tekrar üretme

Proje kökünde:

```powershell
node art/cinematic-gltf-convert.cjs src/main/resources/assets/superheromod/cinematics/gltf/puppet_wide.gltf art/blockbench/cinematic-puppet-editor.gltf --to-editor

$env:BLENDER_USER_RESOURCES = Join-Path (Get-Location) 'build/blender-user'
& 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' --background --python-exit-code 1 --python art/blender-roundtrip.py -- 'C:\Users\user\Desktop\SuperheroMod'

node art/cinematic-gltf-convert.cjs art/blender/puppet-blender-roundtrip.gltf art/blender/puppet-game-roundtrip.gltf

.\gradlew.bat gltfAssetCheck cinematicMotionCheck build
```

`blender-roundtrip.py` bir **test sahnesi üreticisidir**. Her çalıştırıldığında fabrika boş sahnesinden başlayıp belirtilen `.blend` ve önizleme çıktılarını yeniden üretir. Kendi düzenlediğin sahneyi farklı adla kaydet; bu test komutunu düzenlemelerini saklamak için kullanma.

## Düzenlenmiş sahneyi dışa aktarırken

Armature ve ona bağlı tek mesh seçilmelidir. Blender glTF exporter'ında glTF Separate, Selected Objects, animasyonları dışa aktarma ve animasyonu örnekleme kullanılmalıdır. Materyal aktarımı bu aşamada kapalıdır. Scriptteki gerçek API seçenekleri: `export_format='GLTF_SEPARATE'`, `use_selection=True`, `export_materials='NONE'`, `export_animations=True`, `export_force_sampling=True`.

Sonra çıkan `.gltf` dosyasını dönüştürücüye **ayrı bir çıktı yolu** ile ver. Yanındaki `.bin` dosyasını silme; dönüştürücü bu veriyi okuyup tek gömülü buffer hâline getirir. Shader/material görünüşleri aktarılmaz; oyundaki skin/material bağlaması geçerlidir.

## Eksen ve iskelet sözleşmesi

Araç glTF'nin Y-yukarı sahnesi ile mevcut kuklanın Y-aşağı model uzayı arasında dönüşüm yapar. Konumlar `(-x, 1.5-y, z)` ile dönüştürülür; çocuk kemiklerin yerel dönüşümüne zemin ofseti eklenmez. Quaternion, animasyon konumu ve inverse-bind matrisleri de aynı koordinat değişimine göre çevrilir. Yalnızca modelin tepesini ters çevirmek yeterli değildir.

Blender, mesh'i çoğu zaman statik kimlik dönüşümlü bir `Armature` nesnesinin altına koyar. Araç bu özel durumda mesh'i güvenli şekilde ayırır. Atalar hareketliyse veya gerçek bir dönüşümleri varsa bunları sessizce silmez; açık hata verir.

Bu araç rastgele bir rig için otomatik retargeter değildir. Farklı bone-roll, farklı beden oranları, çoklu mesh/material veya başka iskelet adlandırması için ayrıca uyarlama gerekir. Mevcut iskeleti import edip düzenlemek doğrulanan başlangıç yoludur. Oyun tarafında gömülü kemik animasyonlarının üstüne mevcut ActorPose düzeltmeleri eklenebildiği için tam hareket klibini mevcut koreografiye rastgele üst üste bindirmemek gerekir.

## Sınırlar

- JSON glTF + gömülü veya kaynak klasörü içindeki `.bin`; GLB yok.
- Tek skinned mesh ve primitive; dört ağırlık, mevcut bütçeler.
- LINEAR/STEP animasyon; CUBICSPLINE önce örneklenmeli.
- Harici ağ URI'leri ve kaynak klasörü dışına çıkan buffer yolları okunmaz.
- Şu anki model okuyucusu unsigned byte/short indeks kabul eder. Büyük/32-bit indeksli exporter çıktıları ayrıca dönüştürülmeli; otomatik destek iddiası yoktur.
- Yeni film koreografisi, ortam, hacimli sis ve çok oyunculu hazır bariyeri bu araçla tamamlanmış değildir.

Koordinat testini Blender olmadan tekrar çalıştırmak için: `node art/check-cinematic-conversion.cjs`. Gerçek Blender dışa aktarım testi bunun yerine geçmez; iki kontrol birbirini tamamlar.
