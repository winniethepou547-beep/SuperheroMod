# Black Panther

T'Challa, Wakanda'nın Kralı. `/superhero hero black_panther` ile ya da P (şampiyon seçimi) ekranından seçilir.

## Tuşlar
| Tuş | Yetenek | Ne yapar |
|---|---|---|
| Sol tık | Vibranyum Pençeleri | Sağ pençe → sol pençe → çift pençe → pençe aparkatı (rakibi havaya kaldırır). Her pençeden 4 ince iz (çiftte 8). |
| Sol tık basılı | Çılgın Pençe | Kombo bitince yakında rakip varsa sağ-sol-sağ-sol hızlı kısa vuruşlar; rakip kaçarsa ayakları mesafeyi korur. |
| SHIFT | Panter Atılışı | Bir an çömelip öne fırlar. Rakibe çarparsa üstünden burgulu takla atar (kamera aksiyonun etrafında döner), arkasından uçan yan tekme atar, rakip uçar, yere düşer ve zemine göre toz/toprak/taş/kum saçarak sürünür. Iskalarsa öne takla atıp yumuşakça iner. |
| Q | Dönen Üçlü Tekme | Havada hiç inmeden dönerek sağ ayak, sol ayak, sağ ayak; üçüncüsü fırlatır. |
| E | Vibranyum Patlaması | Aldığın hasarın bir kısmı takımda enerji olarak birikir (zırhtaki mor çizgiler bölge bölge yanar). E: ayaklarını sabitler, kollarını açar, enerji ayaklardan ellere akar, küre şeklinde patlar. Herkes Panter'e göre bulunduğu yöne fırlar. Patlamadan sonra çizgiler söner. Az enerji = küçük patlama, dolu = dev patlama. |
| R | Panter Refleksi | 6 saniye boyunca gelen yakın dövüş ve ok gibi saldırılardan kendiliğinden kaçar (sağdan gelen → sola, soldan → sağa, önden → geri, yukarıdan → eğilir). Kaçarken gövdesi saldırana dönük kalır. |
| X | (Yakında) | Sinematik ulti daha sonra tasarlanacak. |

Panter düşerken kedi gibi iner (düşme hasarı çok az), koşarken normal oyuncudan hızlıdır.

## Ayarlar
- `config/superheromod-panther.toml`: hasarlar, menziller, atılma hızı/mesafesi, tekme fırlatma gücü, sürünme süresi,
  dönen tekme mesafesi/yüksekliği, enerji (en fazla, hasar başına), patlama yarıçapı/gücü/yükseltme, refleks süresi,
  kaçış sayısı ve mesafesi, bütün bekleme süreleri.
- `config/superheromod-panther-client.toml`: takla kamerası açık/kapalı, hayalet görüntüler, kamera sarsıntısı, toz miktarı.

## Kod
- `heroes/panther/`: `PantherAction` (hareket kimlikleri ve zamanlamalar), `PantherConfig`, `PantherController` (sunucu: vuruşlar,
  yollar, fırlatma ve sürünme, enerji, refleks), `PantherPath` (atılma/takla/tekme/dönüş yolları; sunucu ve istemci aynısını kullanır).
- `client/render/panther/`: `PantherMotion` (tüm pozlar, anahtar pozlar arası yumuşak eğri), `PantherBody` (kutulardan MCU kostümü,
  enerji çizgileri), `PantherLayer` (geçişler, koşu, iniş, darbe tepkisi, bakışın gövdeye dağılması), `PantherClient` (durum, E tuşu,
  vücudu yolda sürme, takla kamerası, HUD), `PantherFx` (izler, hayaletler, küre, sürünme tozu, birinci şahıs pençeler).
