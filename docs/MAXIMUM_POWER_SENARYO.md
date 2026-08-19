# CYCLOPS — MAXIMUM POWER
## Sinematik Yönetmenlik / Kamera / Animasyon / Atmosfer

> Bu dosya sinematiğin **referans senaryosudur**. Kod bunu hedefler.
> Uygulama durumu her sahnenin sonunda işaretlidir.

---

## GENEL KAMERA FELSEFESİ

Kamera sürekli açı değiştirmemeli. Tempo **giderek hızlanmalı**:

| Bölüm | Kamera dili |
|---|---|
| Açılış | Çok yavaş, neredeyse fark edilmeyen drift |
| Orta | Kontrollü orbit + push-in |
| Direniş | Yan profil / 3-4 profil |
| Cyclops | Bel üstü, ağır ve dominant kadraj |
| Maximum Power | Daha yakın kamera, daha fazla lens bloom |
| Son saldırı | Kamera hareketi hızlanır |
| Fırlatma | Whip pan + takip |
| Patlama | Uzak, sabit, sisin arkasından |

**Kritik kural:** Son saldırıya kadar kamera sakin kalmalı. Son 2-3 saniyede
hızlandığında saldırının gücü çok daha büyük hissedilir.

---

## ANİMASYON FELSEFESİ

Eklem hareketleri Minecraft'ın varsayılan mob animasyonu gibi olmamalı.
Ayrı ayrı animasyonlanacaklar:

boyun dönüşü · gözlerin yön değiştirmesi · omuz rotasyonu · dirsek bükülmesi ·
bilek kırılması · parmakların açılması · gövdenin geriye bükülmesi · dizlerin
kırılması · ayakların zeminde kayması · ağırlık transferi · nefes alma ·
beam'e karşı kasılma · fırlatılma sırasında eklem momentumları

### Tepki zincirleri (EN ÖNEMLİ KURAL)

Bütün model aynı anda hareket etmemeli. Kuvvet vücuttan **geçmeli**:

**Lazeri fark ederken:**
`gözler → kafa → boyun → omuz → gövde`

**Lazer iterken:**
`göğüs → omuz → kafa → kollar → kalça → diz → ayak`

**Fırlatılırken (ters yön):**
`gövde → omuz → kollar → kafa → bacaklar`

---

## ATMOSFER VE IŞIK

| An | Atmosfer |
|---|---|
| Başlangıç | %90 karanlık / %10 sis |
| İlk lazer | Kırmızı ışık kısa süreliğine sisin tamamını aydınlatır |
| Cyclops belirir | Arka planda kırmızı volumetric glow |
| Maximum Power | Kırmızı ışık + beyaz optic core + lens bloom + volumetric fog |

**Beam'in geçtiği her yerde sis fiziksel olarak ışık almalı.** Lazer sadece
kırmızı bir çizgi değil; varlığı bütün ortamın ışığını değiştirmeli. Beam'in
çevresindeki sis hareket etmeli, lenste hafif glare oluşmalı.

**Ama:** görüntü tamamen kırmızıya boğulmamalı — karakterin silueti ve
animasyonu her zaman okunabilir kalmalı.

---

## TEMPO HARİTASI

```
yavaş/rahatsız  →  ANİ ŞOK  →  tekrar yavaşlama  →  gerilim yükselir
   →  ağır direniş  →  kahramanca ilerleme  →  SESSİZLİK
   →  ani enerji patlaması  →  tam güç  →  ÇOK HIZLI fırlatma
   →  sessizlik  →  SON BÜYÜK PATLAMA
```

---

# SAHNELER

## SAHNE 1 — TAM KARANLIK / YALNIZLIK

Siyaha yakın ortam. İlk birkaç saniye neredeyse hiçbir şey görünmez.
Karanlık boş değil: **çok yoğun, koyu gri-siyah sis**. Sis zemine yakın
bölgelerde ağır ağır hareket eder. Uzakta hiçbir çevre/duvar/obje seçilmez —
devasa bir boşluğun ortası hissi.

Hedef karakter ekranın tam ortasında, **belden yukarısı** görünür.
Ayakların altında çok hafif zemin hissi, zemin neredeyse görünmez.

**Kamera:** Karakterin tam karşısında DEĞİL. Göğüs hizasında, birkaç metre
önünde, biraz aşağıda. Hafif telephoto hissi. Tamamen sabit değil — çok yavaş
sağa/sola birkaç santimlik, fark edilmeyecek drift.

> Amaç: kameranın "sinematik başladı" diye bağırmaması. Sadece rahatsız edici
> bir sessizlik.

---

## SAHNE 2 — KARAKTERİN ETRAFINI KONTROL ETMESİ

1–1.5 saniye hiçbir şey olmaz. Sonra nefes ve vücut ağırlığı belli olur.

**Vücut:**
- Göğüs çok hafif yükselip iner
- Omuzlar simetrik değil, biri diğerinden az aşağıda
- Sağ el parmakları hafifçe hareket eder

**Sağa bakış:** önce sadece gözler → çene çok hafif hareket → kafa ~20° döner
→ omuzlar hâlâ öne dönük kalır. Birkaç saniye karanlığı inceler, sonra merkeze
döner.

**Sola bakış:** biraz daha hızlı. Kaşlar ve yüz kasları hafif gerilir. Gövde de
birkaç derece sola döner. Sol el çok hafif yukarı kalkar — saldırıya
hazırlanıyormuş gibi.

**Kamera:** Çok yavaş push-in. Bel üstünden göğüs+kafa kadrajına yaklaşır.
**Kamera karakteri TAKİP ETMEZ** — karakter sağa bakınca kamera sağa pan
yapmaz. Kamera sabitken karakterin kadraj içinde hareket etmesi ortamı daha
gerçek ve tehditkâr hissettirir.

---

## SAHNE 3 — İLK OPTIC BLAST

Karakter hâlâ sola bakarken karanlıktan **kırmızı-beyaz ışık patlar**.

Lazer kameraya doğru gelmez: **sol-arka → sağ-ön** çaprazından, karaktere
~30-40° yatay açıyla çarpar.

İlk temasta çok güçlü **bloom flash** — sahne bir kare boyunca neredeyse
beyaza yaklaşır. Ardından kırmızı lazer gövdenin yanından geçerken yüze
kırmızı ışık vurur.

**Tepki:** Lazerden birkaç kare önce gözler tehdidi fark eder. Sonra kafa çok
hızlı lazerin geldiği yöne döner — ani ama yapay değil: **boyun → omuz →
gövde**. Gözler hafif kısılır. Bir el refleks olarak göğüs hizasına çıkar.
Ayaklardan biri geriye yarım adım kayar. Henüz savrulmaz — sadece saldırının
gücünü anlamaya çalışır.

**Kamera:** Hemen açı değiştirmez. Karakterin arkasına doğru çok hafif orbit
başlar. Lazer kadrajı çapraz keser — izleyici lazerin gerçekten uzaktan
geldiğini hisseder. ~1-1.5 saniye bu açıda kalır.

**Atmosfer:** Bloom sisin tamamını aydınlatır. Sis artık siyah değil:
koyu kırmızı + gri + siyah. Lazer kaybolunca ortam tekrar karanlığa gömülür.

---

## SAHNE 4 — SESSİZLİK VE GERİLİM

**Bu bölüm özellikle uzun tutulmalı.**

Karakter birkaç saniye lazerin geldiği karanlığa bakar. Nefes alır. Başını çok
hafif yana yatırır. Bir adım atacakmış gibi ayağını hareket ettirir ama durur.

**Kamera:** Karakterin arkasına doğru yavaşça yaklaşır. Omuzlarının arasından
bakılan bir **over-the-shoulder** oluşur. Önümüzde sadece sis. Hiçbir şey
görünmez.

Sonra uzakta küçücük bir kırmızı ışık belirir. Ne olduğu anlaşılmaz. Nokta
büyür. Bu sefer **kaybolmaz**.

---

## SAHNE 5 — CYCLOPS'UN OPTIC GLOW'U

Karanlıkta Cyclops'un gözlüğündeki kırmızı glow görünür. Önce küçük bir
parıltı, sonra gözlüğün camlarından biri kırmızı parlar. Sis o noktadan
aydınlanmaya başlar.

Cyclops'un yüzü hâlâ tamamen görünmez. Sadece
`gözlük → göz çevresi → çene → omuz` çok yavaş karanlıktan ayrılır.

**Kamera:** Hedefin arkasından Cyclops'a bakar. Hedef foreground'da bulanık,
Cyclops background'da, aralarında yoğun sis.

Cyclops hareket etmez. Sadece durur.

---

## SAHNE 6 — DEVAMLI KALIN OPTIC BEAM

Bir anda kırmızı optic beam gözlükten çıkar. Öncekinden **çok daha kalın**.

**Beam yapısı:**
- Merkez: çok parlak, neredeyse beyaz çekirdek
- Etraf: birkaç farklı kırmızı/turuncu ışık katmanı
- En dış: kırmızı volumetric glow

Tek parça düz texture gibi görünmemeli — içinde enerji hareketi olmalı.
Beam'in çevresindeki sis sürekli ışıkla yanar.

---

## SAHNE 7 — İLK BÜYÜK ÇARPIŞMA

Beam göğüs hizasından çarpar.

**Animasyon sırası:** göğüs geriye → omuzlar geriye açılır → kafa çok hafif
geriye savrulur → iki kol refleks öne çıkar → dirsekler bükülür.

Sonra ayaklar zeminde kaymaya başlar: sağ ayak geriye sürüklenir, sol ayak
takip eder. Dizler hızla kırılır. Karakter neredeyse çömelmiş pozisyona gelir
ama **düşmez**. Kollarını lazerin önüne kaldırıp direnir. Parmaklar tamamen
açık — görünmez bir duvara fiziksel olarak itiyormuş gibi.

**Kamera:** Tam 90° yan profil. Bu açı kritik — lazerin karakteri ne kadar
güçlü ittiğini gösterir. **Kamera karakterle aynı hızda hareket etmez**;
karakter geriye kayarken kamera birkaç saniye sabit kalır, böylece gerçekten
uzaklaştığı hissedilir.

Zeminde kum/toz parçaları hareket eder, ayakların altında parçacıklar geriye
savrulur.

---

## SAHNE 8 — DİRENİŞ

Lazer devam eder, karakter artık savrulmuyordur.

Dizler hâlâ kırık. Omurga öne eğilmiş. İki ayak zemine sağlam basıyor. Kollar
lazerin karşısında ve **titriyor** — özellikle dirsek ve bileklerde küçük
kontrolsüz titreşimler.

Karakter başını aşağı eğmiş, sonra yavaşça kaldırır ve yüzünü lazerin
kaynağına çevirir.

**Kamera:** ~3/4 side shot. Karakter foreground'da. Lazer görüntünün büyük
bölümünü çapraz kaplar, lensi kısmen doldurur. Kırmızı bloom yüzünden
karakterin kenarları hafifçe parlar. Lazerin geçtiği yerde **sis ikiye
ayrılır**.

---

## SAHNE 9 — KARAKTERİN İLERLEMESİ

Karakter sağ ayağını öne atar. Kahramanca bir yürüyüş değil — aşırı güç
altında yapılan zor bir hareket. Ayak yere ağır basar: **BOOM**, küçük toz
bulutu. Sonra sol ayak. Artık geri itilmiyor, hatta bir adım daha ileri atar.

Kollar hâlâ lazeri karşılıyor. Omuzlar gerilmiş, boyun öne uzanmış, çene hafif
yukarı. Karakter saldırının kaynağına doğru **yürümektedir**.

**Kamera:** Karakterin önünde geri çekilir — karakter kameraya doğru yürürken
kamera yavaşça geri gider. Smooth olmalı. Yürüdükçe lens flare ve kırmızı
bloom kamerayı daha fazla doldurur.

---

## SAHNE 10 — CYCLOPS'A DÖNÜŞ

Kamera karakterin yüzünden yavaşça uzaklaşır. Lazerin içinden geçiyormuş gibi
bir transition yapılabilir.

Cyclops'u görürüz, artık daha net. Kadraj göğüs üstü / bel üstü. Vücudu
karanlıkta, arka plan tamamen sis. Beam kameranın yanından geçip
foreground'daki hedefe gider.

> Bu açı sayesinde **Cyclops + gözlük + beam + hedef** aynı sahnede okunur.

Cyclops tamamen hareketsiz. Sadece göğüs nefes alıyormuş gibi çok hafif
hareket eder. Başı hedefe sabit.

---

## SAHNE 11 — GÖZLÜĞE GİDEN EL

Müzik ve ses tasarımı değişir. Lazer devam eder ama Cyclops'un sağ eli yavaşça
yükselir: dirsek bükülür → omuz hareket eder → ön kol göğüs hizasına gelir →
el yüze yaklaşır → parmaklar gözlüğün kenarına gelir.

El gözlüğe dokunduğu anda **beam'in sesi derinleşir**.

**Kamera:** Cyclops'un bel üstü 3/4 frontal açısı. Gözlükteki kırmızı glow
parmaklara vurur, parmak kenarlarında kırmızı **rim light** oluşur.

Cyclops gözlüğü yavaşça aşağı çekmeye başlar ama tamamen çıkarmaz.

---

## SAHNE 12 — MAXIMUM POWER

Gözlüğü çıkarmaya başladığı anda lazerin şiddeti aniden artar:
beam kalınlaşır → bloom büyür → bütün kamera kırmızı ışıkla dolar.

Cyclops gözlüğü tamamen çıkarır.

**Kamera:** Cyclops'un yüzüne doğru **çok hızlı ama kontrollü** push-in.

Gözlerden aşırı parlak kırmızı ışık çıkar; gözler kısa süre neredeyse beyaz
görünür. MAXIMUM POWER aktif olur.

---

## SAHNE 13 — DEVASA BEAM

Beam öncekinin birkaç katı kalınlıkta. Artık sadece göğsü vuran bir ışın değil
— **tüm üst gövdeyi kaplayan** devasa optic beam. Kameranın önünden geçerken
ekranın önemli kısmını kapatır.

**Kamera:** Cyclops'un bel üstü 3/4 yan açısı. Bir omuz kameraya daha yakın.
Kafa hafif öne eğik, gözler hedefe kilitli. Bir kol hafif geride, diğeri
vücudun yanında. **Fiziksel olarak saldırıyı kontrol eden, güçlü ve sakin**
bir duruş.

Beam'in dış katmanları siste devasa kırmızı volumetric ışık oluşturur.

---

## SAHNE 14 — HEDEFİN KIRILMA ANI

**Kamera:** Hafif alttan 3/4 açı.

Karakter hâlâ direniyor: bir ayak önde, bir ayak geride, kollar yukarıda,
dizler kırık.

Sonra beam'in gücü aniden artar: kollar geriye savrulur → dirsekler açılır →
bilekler kırılır → göğüs geriye gider → ayaklar yerden çok hafif ayrılır →
bir an **havada asılı kalır** → sonra devasa itiş.

---

## SAHNE 15 — DEVASA ÇARPIŞMA

Beam karaktere yatay olarak çok büyük bir yüzeyden çarpar:
iki kol geriye açılır · omuzlar geriye zorlanır · gövde beam yönünde bükülür ·
kafa geriye gider · bacaklar yerden kesilir.

Karakter artık beam'in önünde **duran** biri değil, beam tarafından fiziksel
olarak **sürüklenen** bir insan. Kollar havada savrulur, bir kol diğerinden
farklı açıda, parmaklar tamamen açık, bacaklar geriye uzanır. Vücut hafifçe
kendi ekseni etrafında dönmeye başlar.

---

## SAHNE 16 — HIZIN BİRDEN ARTMASI

Buraya kadar kamera ağır ve kontrollü. **Şimdi tempo değişir.**

Kamera önce karakterle aynı yönde hareket eder, sonra **hızına yetişemez**.
Karakter kadrajdan çıkmaya başlar. Kamera **whip pan** ile karakterin fırladığı
yöne döner — ama **kesme yapılmaz**, gerçek kamera hareketi hissedilir.

Kamera birkaç metre boyunca sisin içinden karakteri takip eder. Karakter çok
hızlı uzaklaşır. Beam'in ışığı hâlâ arkasından gelir. Sonra beam kesilir.

---

## SAHNE 17 — SİSE DOĞRU FIRLAMA

Kamera artık karakterin arkasından bakar. Karakter sisin içine fırlar. Önce
tamamen görünür, sonra `baş → gövde → kollar → bacaklar` sırasıyla sis
tarafından yutulur.

Karakter tamamen kaybolduğunda kamera **hemen kesilmez** — aynı yönde hareket
etmeye devam eder. ~1 saniye boyunca **boş sis** görürüz. Kamera yavaşça durur.
Ortam sessizleşir.

---

## SAHNE 18 — SİSİN İÇİNDEKİ PATLAMA

Tam izleyici hiçbir şey olmayacağını düşünürken, sisin çok uzağında küçük bir
kırmızı ışık belirir — 0.2 saniye. Sonra: **BOOOOM**

Patlamanın kendisi doğrudan görünmez (kamera sisin dışında). Bunun yerine
**sisin tamamı patlamanın ışığıyla aydınlanır**:
`kırmızı → turuncu → beyaz-sarı`

Patlama dalgası sis bulutlarını kameraya doğru iter. Kamera hafifçe sarsılır
ama **aşırı screen shake yapılmaz** — patlamanın uzakta olduğu hissedilmeli.

**Ses ışıktan birkaç milisaniye sonra gelir.** Önce ışığı görürüz, sonra çok
ağır bir patlama sesi.

---

## SAHNE 19 — SON KARE

Sis tekrar kapanır. Kamera birkaç saniye patlamanın olduğu yere bakar. Hiçbir
şey görünmez. Sadece havada süzülen küçük kırmızı parçacıklar. Sonra görüntü
yavaşça kararır. Ultimate biter.

---

# UYGULAMA DURUMU

## Hazır olan motor özellikleri
- Sahne uzayı (konumdan bağımsız koreografi)
- Shot/Beat zaman çizelgesi
- Sis mesafesi + rengi (çekim başına)
- Kamera roll, push-in, nefes, FOV, sarsıntı
- Post-processing: bloom + doygunluk + kontrast + ton

## Bu senaryo için EKSİK olanlar
| Eksik | Engellediği sahneler |
|---|---|
| **Kukla katmanı** (gerçek entity yerine sahne aktörü) | 2, 3, 7, 8, 9, 14, 15 |
| Çok eklemli gövde (diz, bel, çift dirsek, bilek) | 7, 8, 9, 14, 15 |
| Tepki zincirleri (uzuv gecikmesi) | 2, 3, 7, 15 |
| Whip pan | 16 |
| Orbit kamera | 3, 4 |
| Beam'in sisi aydınlatması (volumetric) | 3, 6, 8, 13 |
| Sisin içindeki patlama ışığı | 18 |
| Gecikmeli ses | 18 |
| Havada süzülen parçacıklar | 19 |

**Kilit nokta:** Kukla katmanı olmadan sahnelerin yarısı fiziksel olarak
imkânsız — gerçek oyuncu entity'si çömelemez, havada asılı kalamaz, kendi
ekseninde dönemez.
