# Colossus boss formu — 8 Eylül 2026

Kaydedilmiş kaynak: `art/blockbench/colossus.bbmodel`.
Oyun geometrisi: `src/main/resources/assets/superheromod/rigs/colossus.json`.
43 kemik/dekor grubu, 106 küp; yaklaşık 10 blok yükseklik. Yüz kaldırıldı; gövde, omuz ve kum tabanında çapraz kırıklı katmanlar var. Kaynakta kılıç dahil bütün parçalar edit modunda görünür; oyun yalnızca ilgili saldırıda kılıcı açar. Kristaller ayrı çizicide eklem bağlantılarına takılır, bbmodel içinde değildir.

Oluşum 100 tick: ilk 40 tickte modellenmiş kum parçaları birleşir, 40–60 arasında iki elle yere kapanır, 60–72 arasında tutunur, 72–100 arasında doğrulur. İstemci ara kareleri zamanla örnekler. Düz zeminde hesaplanan avuç merkezi yüksekliği 0,25 blok, ileri mesafesi 4,58 blok; avuç geometrisinin alt yüzü zemine ulaşır.

Son düzeltmeler: kılıç omuzdan itibaren bütün kolun yerini alır, 18–24 tickte kolun kısa ileri hamlesi vardır. Darbe konumu gerçek kılıç ucundan hesaplanır. Yumruk teması yaklaşık 4,89 blok öndedir. Her iki yıkım kendi tabanının dört blok çevresini korur. Kaya tek kapalı, düzensiz yüzeyli ağ olarak çizilir. Yeni amber mineral dokusu ve farklı yönlere çıkan gerçek sivri yüzeyli kristaller kullanılır. Kaya nişanı sırasında kamera yukarı/yan tarafa geçip hedefe bakar; yerel hedef halkası gövde arkasında kaybolmaz.

- Sol tık: gövdeyi alçaltarak yere vuruş, elde hesaplanan temas noktası çevresinde şok dalgası. Açık düz zeminde normal yerçekimiyle yaklaşık 11,4 blok dikey savurma. Tavan ve başka fizik etkileri sonucu değiştirir.
- Sağ tık: basılıyken kaya elde büyür, nişan göstergesi görünür; kamera yana açılır. Bırakınca kaya göstergenin merkezine yönelir. Yoldaki engeller erken çarpışmaya neden olabilir.
- R: kol ucunda kum kılıcı oluşur. 24. tickte hasar ve en fazla 72 blokluk kazı/gerçek düşen kum; 44 tickte toparlanma. Normal formdaki R korunmuştur.
- Kristaller: gövde/kafa/omuz kemik dönüşümlerini kullanır. Sunucu ve istemci aynı ColossusPose eğrilerini değerlendirir. Kristal hasar çarpanı 2; kırılma etkileri korunur.
- Sağlık: formda maksimum can 2 kat, mevcut can yüzdesi korunur. Gövde hasar azaltması %65. Süre bitimi, çıkış, ölüm ve bağlantı kesilmesinde sağlık bonusu temizlenir.

## Doğrulama

`gradlew.bat colossusPoseCheck build` başarılı. Poz kontrolü dört hareket boyunca sonlu kristal koordinatlarını, omuz hareketinin kristale aktarımını ve yaklaşık dikey yükselişi denetler.
Blockbench kaynak ihracı ile paket geometrisi kemik adına göre, 1e-6 sayı toleransında eşleşir.
Blockbench'te model açılarak baş/gövde görünüşü incelendi. Bu oyun içi görsel onay değildir.

## Oyun içinde hâlâ kontrol edilmesi gerekenler

Çok oyunculu gecikmede kristal isabetleri; kılıcın eğimli zeminde teması; yakındaki duvarlarda kamera; büyük kayanın engellerle erken teması; yumruk darbesi ve yere eğilmenin görsel oranları. Bu kontroller oynanarak tamamlanmadı. Final görsel kalite onayı verilmedi.

Model düzenlendikten sonra `export-rig.ps1 -Source art/blockbench/colossus.bbmodel -Output src/main/resources/assets/superheromod/rigs/colossus.json` kullanılabilir. `build-colossus.cjs --force` kaynak modeli baştan üretir; elle yapılan düzenlemelerden sonra kullanılmamalıdır.

## 2026-09-08: krater, kesiş ve sis güncellemesi

Önceki R saplama açıklamasının yerini yeni davranış aldı: sağ kol kılıç olarak 18–30. tick arasında soldan sağa zemini keser. Kılıç ucu paylaşılan iskelet eğrisinden hesaplanır; en fazla 120 blok kesilir, aynı hedef bir savuruşta bir kez hasar alır. Oyuncunun dört blokluk taban çevresi korunur. Kum renkli yumuşak dokulu sis, kesiş boyunca yedi quad ile çizilir; eski CLOUD parçacıkları kaldırıldı.

Sağ tık: nişan halkasıyla hesaplanan noktaya gider; el ile hedef arasındaki gerçek mesafe kullanıldığı için azami menzilde erken havada parçalanma kaldırıldı. Aradaki engeller veya canlılar daha erken çarpabilir. Çarpışma yerde krater açar ve ortada tek bir kalıcı, dünyayla kaydedilen kum kayası bırakır. Kaya sonradan hasarla kırılabilir. Krater kazısı 220 blokla sınırlı; kaplar, sıvılar ve kırılamaz bloklar atlanır.

Son doğrulama: `gradlew.bat colossusPoseCheck cinematicMotionCheck build` başarılı. Yeni davranışın oyun içi görsel onayı henüz verilmedi.
