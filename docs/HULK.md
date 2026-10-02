# Hulk — kontroller, ayarlar, sunucu davranışı

Minecraft **1.20.1**, **Forge** (Java 17). Kahramanı seçmek için: `/superhero hero hulk`.

## Tuşlar (hepsi Ayarlar → Kontroller'den değiştirilebilir)
| Tuş | Ne yapar |
|---|---|
| **G** | Bruce Banner ⇄ Hulk dönüşümü (aşamalı: başını tutar, vücut büyür, gömlek ve gözlük yırtılır, kükreme) |
| **Sol tık (bas-çek)** | Sırayla sağ–sol patlayıcı yumruk: sağ el sağ önündekine, sol el sol önündekine vurur (nişan almak gerekmez); vurduğu yerde küçük patlama, yumruk büyüklüğünde blok koparıp fırlatır |
| **Sol tık basılı tut** | Şarjlı Yıkıcı Yumruk (bar ~1 sn'de dolar): şok dalgası yolundaki her şeyi kırarak ilerler; ilk çarptığı kişide (o kişi ~20 blok uçar), çok sert bir duvarda ya da menzil sonunda patlar |
| **Sağ tık basılı tut** | Gard: önden gelen hasarı çok, arkadan geleni az azaltır; yavaşlatır; dayanıklılık çubuğu biter |
| **R** | Thunderclap: el çırpar, önüne yerden giden hava duvarı (havadayken de); iter, sersemletir, toprak parçaları fırlatır, arkasında toz bırakır; Hulk biraz geri itilir |
| **F** | Yer Sarsan Yumruk: yarık 3 blok önünden başlar, hızlı ve düzensiz ilerler, ~6 blok geniş ve 15 blok derin; toprak sağa-sola fırlar, yarık fırlayan toprakla birlikte açılır |
| **C** | Kaya Sök ve Fırlat: yerden dev bir kaya söker, başının üstünde taşır, fırlatır; çarptığı yerde alan hasarı ve yavaşlatma, kaya bir süre oraya saplı kalır |
| **Boşluk (bas-çek)** | Normal zıplama, hiçbir şey kırılmaz |
| **Boşluk basılı tut** | Şarjlı Sıçrama (bar yarım saniyede dolar): baktığın yöne fırlar, ağır ve hızlı düşer; iniş gücü düşüş hızına göre, krater, bir kez seker |
| **X** | GAMA ÖFKESİ (ulti, önünde bir hedef ister) |

Banner iken sadece **G** çalışır; diğer her şey Hulk'a özel. Hulk iken fare normal kazma / eşya kullanma yapmaz.

## X — ONE PUNCH (Durdurulamaz Güç)
1. Açık bir ova, uzakta dev bir dağ. Hulk solda, rakip sağda; sessiz bir bakışma.
2. Beş ayrı, okunur yumruk (sağ-sol), her birinde bütün vücut dönüyor; rakip her darbede ters yöne sendeliyor.
3. Yumruklar giderek hızlanıp makineli tüfek gibi oluyor; her darbede şok halkası, toz büyüyüp ikisini sarıyor.
4. Aniden duruyor; sessizlik; toz dağılınca Hulk dimdik ayakta.
5. Gerilme (Saitama pozu): geniş duruş, yumruk geride; zemin çatlıyor, kısa bir donma.
6. Son yumruk: siyah-kırmızı titreşen darbe kareleri.
7. Yumruktan doğan dev hava akımı ovada ileri koşup dağa çarpıyor, sonra yavaşça dağılıyor.
8. Dağın ortasında devasa bir yarık; kayalar düşüyor. Geniş planda ikisi de minicik: Hulk solda, rakip sağda.
9. Kol iner, kontrol geri gelir; rakip yumruğun yönünde savrulur.

Hasar: yumruk yağmuru toplamda maks. canın %25'i (her darbeye bölünür), son yumruk %50.
Sinematik kendi sahnesinde oynar; gerçek dünyada arazi değişmez.

## Ayar dosyaları (oyun klasöründe `config/`)
- `superheromod-hulk.toml` (sunucu kuralları ve denge):
  dost ateşi, blok kırma açık/kapalı, korumalı bölgelerde kırma, saldırı başına en fazla blok, en fazla blok sertliği,
  yasaklı bloklar listesi, ağır düşmanların geri itilme direnci, kopan blokların gerçek düşen blok olarak uçması
  (`flyingBlocks`), indikleri yere yerleşmeleri (`debrisLands`, kapalıyken yere çarpınca dağılır), aynı anda en fazla uçan blok; her yeteneğin hasarı, menzili, geri itmesi,
  sersemletme süresi, bekleme süresi; ultinin menzili, iki darbenin hasar payı, dalış gecikmesi, krater boyutu.
- `superheromod-hulk-client.toml` (her oyuncunun kendi görüntüsü):
  efekt miktarı (0 = kapalı, 1 = normal, 2 = çok), kamera sarsıntısı, **sakin sinematik kamera** (ultide sert
  sarsıntı ve hızlı kamera savurmalarını azaltır), HUD açık/kapalı.

## Sunucu davranışı
- Hasar, blok kırma, bekleme süreleri, sıçrama ve gard sunucuda hesaplanır; istemci sadece görüntüyü çizer.
- Hiçbir zaman kırılmaz: ana kaya (bedrock) ve kırılamaz bloklar, içinde veri tutan bloklar (sandık vb.), yasaklı
  listedekiler, korumalı bölgeler (ayar açılmadıkça), sertlik sınırını aşanlar. Kırma olayı diğer modlara da bildirilir.
- Her saldırının kırabileceği blok sayısı sınırlıdır; kırılan bloklar eşya düşürmez.
- Kaya yüklenmemiş bölgeye ya da dünya dışına gidemez; öyle olursa havada dağılır.
- Dost ateşi kapalıyken takım arkadaşlarına vurmaz.

## Dürüst not
Bu kod bulut ortamında yazıldı; orada Minecraft/Forge indirilemediği için derlenip oyunda denenemedi.
İlk denemede derleme hatası ya da görünüş sorunu çıkarsa ekran görüntüsü yeterli.
