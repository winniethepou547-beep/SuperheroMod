# Hulk — kontroller, ayarlar, sunucu davranışı

Minecraft **1.20.1**, **Forge** (Java 17). Kahramanı seçmek için: `/superhero hero hulk`.

## Tuşlar (hepsi Ayarlar → Kontroller'den değiştirilebilir)
| Tuş | Ne yapar |
|---|---|
| **G** | Bruce Banner ⇄ Hulk dönüşümü (aşamalı: başını tutar, vücut büyür, gömlek ve gözlük yırtılır, kükreme) |
| **Sol tık** | Sırayla sağ–sol yumruk; zayıf blokları (toprak, çim, yaprak vb.) azıcık kırabilir |
| **Sol tık basılı tut** | Şarjlı Yıkıcı Yumruk: bırakınca önüne şok dalgası, şarj ne kadar dolarsa o kadar güçlü |
| **Sağ tık basılı tut** | Gard: önden gelen hasarı çok, arkadan geleni az azaltır; yavaşlatır; dayanıklılık çubuğu biter |
| **R** | Thunderclap: diz çöküp el çırpar, önüne yerden giden hava duvarı; iter ve sersemletir, duvar arkasına geçmez |
| **F** | Yer Sarsan Yumruk: yere vurur, dalga yeri takip ederek ilerler, hendek açar, üstündekileri havaya atar |
| **C** | Kaya Sök ve Fırlat: önündeki uygun zeminden blok söker, ellerinde taşır, baktığı yere fırlatır |
| **Boşluk basılı tut** | Şarjlı Sıçrama: bar dolar, bırakınca sıçrar; iniş gücü düşüş hızına göre, küçük krater, bir kez seker |
| **X** | GAMA ÖFKESİ (ulti, önünde bir hedef ister) |

Banner iken sadece **G** çalışır; diğer her şey Hulk'a özel. Hulk iken fare normal kazma / eşya kullanma yapmaz.

## GAMA ÖFKESİ sırası
1. Gama enerjisi toplanır, titreme, kükreme.
2. Hedefe sıçrar, tek kolla yakalar.
3. Gökyüzüne sıçrar, iki eliyle yüzünün önünde tutup kükrer.
4. Aşağı fırlatır — hedef önce yere çakılır (**ilk hasar**, varsayılan: maks. canın %25'i).
5. Hulk havada kalır, iki yumruğunu başının üstüne kaldırır, ayarlanan gecikmeden sonra (varsayılan **2 sn**) dalış.
6. İkinci büyük darbe (**asıl hasar**, varsayılan %50) + gerçek ama sınırlı krater; yakındaki diğerleri savrulur.
7. Uzak kamera, Hulk tozun içinden doğrulup güç pozu verir, kontrol geri gelir.

Hedef ölür ya da oyundan çıkarsa sahne güvenle biter. İkisi de sahne boyunca yerinde tutulur, sonda
blok içine değil zeminin üstüne konur.

## Ayar dosyaları (oyun klasöründe `config/`)
- `superheromod-hulk.toml` (sunucu kuralları ve denge):
  dost ateşi, blok kırma açık/kapalı, korumalı bölgelerde kırma, saldırı başına en fazla blok, en fazla blok sertliği,
  yasaklı bloklar listesi, ağır düşmanların geri itilme direnci; her yeteneğin hasarı, menzili, geri itmesi,
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
