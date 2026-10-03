# Black Panther

T'Challa, Wakanda'nın Kralı. `/superhero hero black_panther` ile ya da P (şampiyon seçimi) ekranından seçilir.

## Tuşlar
| Tuş | Yetenek | Ne yapar |
|---|---|---|
| Sol tık | Vibranyum Pençeleri | Önündeki alana (nişangaha değil) geniş vuruşlar: sağ pençe → sol pençe → çift pençe → pençe aparkatı. Her pençeden 4 kalın iz (çiftte 8). |
| Sol tık basılı | Vahşi Pençe | Wolverine'in durmadan vuruşu gibi: iki el sırayla, çapraz, nefes almadan; kombodan bağımsız, aparkat yok. |
| (Vuruşlar) | Mor çizik işareti | Vurduğun herkesin üstünde 2 saniye mor pençe çiziği kalır, bitmeye yakın yanıp söner. |
| Sağ tık | Pençe Atılışı | İşaretli bir hedef varsa ona yerden atılır, pençeleri göğsünde çapraz; varınca içten dışa açarak keser. 2 sn bekleme. İşaret yoksa çalışmaz. |
| SHIFT (bas) | Panter Atılışı | Öne fırlar. Rakibe çarparsa üstünden burgulu takla atar (kamera aksiyonun etrafında döner), arkasından uçan yan tekme atar, rakip uçar, yere düşer ve zemine göre toz/toprak/taş/kum saçarak, yerde oluk bırakarak sürünür. Iskalarsa öne takla atıp yumuşakça iner. |
| SHIFT (basılı) | Kamuflaj | Çömelir (yavaş yürür). 2 saniye çömelince 5 saniye yarı saydam, ışığı büken bir kamuflaja girer. Hasar alırsa bozulma efektiyle görünür olur. |
| Boşluk (havada) | Çift Zıplama | Havada bir kez daha zıplar, öne takla atar; ayaklarının altında beyaz hava patlaması. |
| Q | Dönen Üçlü Tekme | Havada hiç inmeden dönerek sağ ayak, sol ayak, sağ ayak; üçüncüsü fırlatır. |
| E | Vibranyum Patlaması | Aldığın hasarın bir kısmı takımda enerji olarak birikir (zırhtaki mor çizgiler bölge bölge yanar). E: ayaklarını sabitler, kollarını açar, enerji ayaklardan ellere akar, küre şeklinde patlar. Herkes Panter'e göre bulunduğu yöne fırlar. Patlamadan sonra çizgiler söner. Az enerji = küçük patlama, dolu = dev patlama. |
| R | Panter Refleksi | 6 saniye boyunca koruma duruşunda durur (Daredevil'in savuşturması gibi: ön kollar yüzünün önünde, pençeler titrer). Gelen yakın dövüş ve ok gibi saldırılardan belirgin pozlarla kaçar: yana derin eğilme, geriye kavis, iki kolu başının üstünde çapraz eğilme, dönerek kaçış. |
| X | Son Kovalamaca | Sinematik ulti (38 sn, aşağıda). Hedef gerekmez; film sadece sana oynar. Film boyunca hasar almazsın; filmdeki büyük patlama anında gerçek dünyada da etrafındakiler savrulur. |

Normal duruşu dik ve rahattır (kolları gövdeden hafif açık). Düşerken kedi gibi iner (düşme hasarı çok az), koşarken normal oyuncudan hızlıdır.

## X — Son Kovalamaca (The Final Pursuit)
Gece, neon ışıklı ıslak bir şehir bulvarında araba kovalamacası:
1. Maskesinin gözüne çok yakından başlar, kamera yavaşça geri çekilir: sürücü koltuğunda, elleri direksiyonda.
2. Aynanın yanından sürücüye çapraz bakış; dışarıda şehir akar (yakındakiler hızlı, uzaktakiler yavaş).
3. Arka koltuktaki silahlı adam (her seferinde farklı görünüşlü) silahını hazırlar, arka camı kırar, dışarı sarkıp ateş eder.
   Kurşunların bir kısmı arabaya çarpar (kıvılcım, cam çatlağı), bir kısmı zırha isabet eder: mor parlama, Wakanda deseni,
   enerji çizgilerde yayılır.
4. Panther camdan çıkar; kamera döner ve onu tavanın kenarından içeri bakarken gösterir. Adam tavandan yukarı ateş eder,
   Panther arkadaki SUV'nin üstüne atlar, çömelir ve hayvan gibi yaylanıp arabanın tavanına geri atlar; pençeleriyle tutunur.
5. İçeriden: tavandaki göçük, kurşun delikleri, deliklerden süzülen neon ışığı.
6. Panther döner, önce sağ sonra sol eliyle pençelerini metal tavana geçirir, tüm gövdesiyle tavanı söküp arkaya fırlatır,
   adamı yakasından tutup gökyüzüne fırlatır.
7. Enerji dolar, kısa bir sessizlik, BOOM: beyaz çekirdek, mor küre, darbe karesi; ağır çekim. Araba burnunu asfalta gömüp
   üstünden takla atarak havalanır (fiziğe göre döner, sonunda yana yatar). Panther havada döner, toparlanır, üç noktalı
   inişle yere iner ve kayar.
8. Kamera arabaya döner: araba takla atarak iner ve parçalanır (önce flaş, sonra mor enerji, sonra ateş topu, sonra duman).
9. Toz bulutu her şeyi kaplar, rüzgâr iki yana açar, Panther'ın silueti ortaya çıkar. Ayağa kalkar, çapraz bakar, pençeleri
   içeri girer, zırhtaki enerji göğüsten kollara, kollardan bacaklara doğru söner. Kamera oyuna geri döner.

## Ayarlar
- `config/superheromod-panther.toml`: hasarlar, menziller, atılma hızı/mesafesi, tekme fırlatma gücü, sürünme süresi,
  dönen tekme mesafesi/yüksekliği, enerji (en fazla, hasar başına), patlama yarıçapı/gücü/yükseltme, refleks süresi,
  kaçış sayısı ve mesafesi, bütün bekleme süreleri; `finalPursuit`: X'in bekleme süresi, patlamanın hasarı, menzili ve savurma gücü.
- `config/superheromod-panther-client.toml`: takla kamerası açık/kapalı, hayalet görüntüler, kamera sarsıntısı, toz miktarı.

## Kod
- `heroes/panther/`: `PantherAction` (hareket kimlikleri ve zamanlamalar), `PantherConfig`, `PantherController` (sunucu: vuruşlar,
  yollar, fırlatma ve sürünme, enerji, refleks), `PantherPath` (atılma/takla/tekme/dönüş yolları; sunucu ve istemci aynısını kullanır).
- `client/render/panther/`: `PantherMotion` (tüm pozlar, anahtar pozlar arası yumuşak eğri), `PantherBody` (kutulardan MCU kostümü,
  enerji çizgileri), `PantherLayer` (geçişler, koşu, iniş, darbe tepkisi, bakışın gövdeye dağılması), `PantherClient` (durum, E tuşu,
  vücudu yolda sürme, takla kamerası, HUD), `PantherFx` (izler, hayaletler, küre, sürünme tozu, birinci şahıs pençeler).
- X filmi: `client/render/film/FinalPursuitFilm` (ses, renk, başlık), `client/render/panther/Pursuit*` (zaman çizelgesi ve
  araba fiziği `PursuitPath`, pozlar `PursuitMoves`, kamera `PursuitCamera`, şehir `PursuitCity`, arabalar `PursuitCar`,
  silahlı adam `PursuitThug`, efektler `PursuitFx`, gece ışığı `PursuitShade`, çizim sırası `PursuitStage`),
  sunucu `heroes/panther/PantherUltSession`.
