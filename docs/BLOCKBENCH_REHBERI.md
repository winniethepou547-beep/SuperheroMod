# Blockbench Rehberi — Sandman Model ve Animasyonları

Bu dosya parça parça okunmak için yazıldı. Sırayla git, her bölüm bir öncekinin
üstüne kuruluyor. Acelen yoksa **Bölüm 0 → 1 → 2 → 3** sırasıyla oku, sonra
modellemeye başla; **Bölüm 4** senin tasarım defterin, **Bölüm 5** animasyon,
**Bölüm 6** bana nasıl teslim edeceğin.

---

## İÇİNDEKİLER

- [Bölüm 0 — Önce şunu bilmen lazım (ÖNEMLİ)](#bölüm-0)
- [Bölüm 1 — Blockbench kurulumu ve ilk proje](#bölüm-1)
- [Bölüm 2 — Temel modelleme (cube, bone, pivot, UV)](#bölüm-2)
- [Bölüm 3 — İsimlendirme kuralları (ZORUNLU)](#bölüm-3)
- [Bölüm 4 — Model tasarımları (ölçülerle)](#bölüm-4)
- [Bölüm 5 — Animasyon yapımı](#bölüm-5)
- [Bölüm 6 — Bana nasıl vereceksin](#bölüm-6)
- [Bölüm 7 — Sandman'in kendi bedeni (Blockbench gerekmiyor)](#bölüm-7)
- [Bölüm 8 — Kontrol listesi](#bölüm-8)

---

<a name="bölüm-0"></a>
## BÖLÜM 0 — Önce şunu bilmen lazım (ÖNEMLİ)

### 0.1 Kötü haber: Blockbench animasyonu vanilla'da kendiliğinden çalışmaz

Blockbench'te animasyon yaparsın, `.animation.json` çıkar. Ama Minecraft'ın
kendi entity sistemi bu dosyayı **okumaz**. Vanilla modellerde animasyon Java
kodunda yazılır (şu an kum askerinin yürümesi böyle çalışıyor).

Yani hiçbir şey yapmazsak: senin model çalışır, **animasyonun çöpe gider**.

### 0.2 Çözüm: GeckoLib

GeckoLib, Blockbench animasyonlarını doğrudan oynatan bir kütüphane. Modlarda
standart çözüm bu. Eklersek:

- Blockbench'te yaptığın animasyon **birebir** oyunda oynar
- Yeni animasyon eklemek için bana kod yazdırman gerekmez, dosyayı atarsın yeter
- Sen kendi başına animasyon üzerinde çalışabilirsin

Bedeli: mevcut kum askeri model/çizici kodunu GeckoLib'e taşımam gerekiyor.
Bu benim işim, senin tarafında bir şey değişmiyor.

**Önerim: GeckoLib'e geçelim.** Sen animasyon yapacaksan başka mantıklı yol yok.

### 0.3 Ama şimdi karar vermene gerek yok

Blockbench'te **"Bedrock Model"** formatında çalış. Bu format:

- GeckoLib'in istediği format (`.geo.json`)
- GeckoLib'e geçmezsek bile ben bu dosyayı okuyup vanilla koda çevirebilirim

Yani **modelleme emeğin her durumda güvende**. Sadece "Bedrock Model" seç,
gerisini sonra konuşuruz.

### 0.4 Hangisi Blockbench istiyor, hangisi istemiyor

| Varlık | Blockbench gerekli mi? | Neden |
|---|---|---|
| Sand Soldier | ✅ Evet | Kendi entity'si |
| Giant Sand Soldier | ✅ Evet | Kendi entity'si |
| Sand Colossus (+ topuz, kristaller) | ✅ Evet | Ultinin görsel zirvesi |
| Sand Wall | ✅ Evet | Şu an kaba kutu geometri |
| Sand Spike | ✅ Evet | Şu an partikül |
| Sand Fist (yumruk eklentisi) | ✅ Evet | Kolun üstüne geçen parça |
| Sand Shockwave (halka) | ✅ Evet | Topuz vuruşu için |
| **Sandman'in kendisi** | ❌ Hayır | Vanilla oyuncu modeli — Bölüm 7 |
| Sand Travel | ❌ Hayır | Sadece partikül/efekt |
| Sand Body | ❌ Hayır | Sadece efekt + hasar azaltma |
| Sand Armor katmanları | ⚠️ İsteğe bağlı | Şu an kodla çiziliyor, çalışıyor |

---

<a name="bölüm-1"></a>
## BÖLÜM 1 — Blockbench kurulumu ve ilk proje

### 1.1 Kurulum

1. https://www.blockbench.net/ adresine git
2. **Download** → Windows sürümünü indir (`.exe`)
3. Kur ve aç
4. Sürüm 4.x olsun (alt taraftaki sürüm numarasından bakabilirsin)

### 1.2 İlk projeyi açma

1. Blockbench açılınca ortada **"New Project"** kutusu gelir
   (gelmezse: sol üst **File → New**)
2. Karşına format listesi çıkar. **"Bedrock Model"** seç
   - ⚠️ "Java Block/Item" veya "Modded Entity" SEÇME. Bedrock Model.
3. Açılan pencerede:
   - **Project Name**: `sand_soldier` (küçük harf, boşluk yok)
   - **Texture Width**: `64`
   - **Texture Height**: `64`
4. **Confirm**

### 1.3 Ekranı tanıyalım

Ekran kabaca 4 bölge:

```
┌──────────────┬────────────────────────┬──────────────┐
│              │                        │              │
│  SOL PANEL   │      3B GÖRÜNÜM        │  SAĞ PANEL   │
│              │                        │              │
│ (bazı        │   modelin burada       │  OUTLINER    │
│  sürümlerde  │   görünür              │  (parça      │
│  boş)        │                        │   ağacı)     │
│              │                        │              │
│              │                        │  ELEMENT     │
│              │                        │  (seçili     │
│              │                        │   parçanın   │
│              │                        │   sayıları)  │
└──────────────┴────────────────────────┴──────────────┘
              ALT: TIMELINE (animasyon modunda)
```

**En çok kullanacakların:**

- **Outliner** (sağ üst): parçaların listesi. Klasör = bone, küp = cube
- **Element** (sağ, Outliner'ın altı): seçili küpün Position / Size / Pivot /
  Rotation sayıları. **Asıl iş burada.**
- **Üst orta**: mod sekmeleri → `Edit` / `Paint` / `Animate` / `Display`

### 1.4 3B görünümde gezinme

| Ne yapmak istiyorsun | Nasıl |
|---|---|
| Etrafında dönmek | Sol tık basılı + sürükle |
| Kaydırmak | Orta tık (tekerlek) basılı + sürükle |
| Yakınlaş/uzaklaş | Tekerleği çevir |
| Seçili parçaya odaklan | Parçayı seç, sonra `F` |

---

<a name="bölüm-2"></a>
## BÖLÜM 2 — Temel modelleme

### 2.1 Ölçü birimi: piksel

Minecraft'ta **1 blok = 16 piksel**. Blockbench'teki tüm sayılar piksel.

- Steve boyu: 32 piksel (2 blok)
- Steve kafası: 8×8×8 piksel
- Kum askeri boyu: 32 piksel

Bedrock formatında **Y=0 yerdir**, yukarı doğru artar. Yani ayaklar Y=0'da,
kafa Y=32'de.

### 2.2 Bone (kemik) oluşturma

Bone = hareket eden parça. Kol bir bone, kafa bir bone. Animasyon **bone'ları**
oynatır, küpleri değil.

1. Outliner panelinin **üstündeki klasör ikonuna** tıkla ("Add Group")
   - Kısayol: `Ctrl+G`
2. Yeni klasör çıkar, ismi seçili gelir → ismini yaz → `Enter`
3. Bone'u başka bone'un içine koymak için: Outliner'da **sürükleyip bırak**

### 2.3 Cube (küp) ekleme

1. Önce hangi bone'un içine gireceğini **Outliner'da seç**
2. Üst araç çubuğundaki **küp+ ikonuna** tıkla ("Add Cube")
   - Kısayol: `Ctrl+N`
3. Küp o bone'un içinde oluşur

### 2.4 Element panelindeki sayılar (EN ÖNEMLİ KISIM)

Bir küp seçince sağda şunlar çıkar:

**Position (X, Y, Z)**
Küpün **köşesinin** konumu. Merkez değil, köşe.

**Size (X, Y, Z)**
Küpün boyutu. Genişlik, yükseklik, derinlik.

> Örnek: Kafa yapmak istiyorsun. 8×8×8 olacak ve ortalanacak.
> Size = `8, 8, 8`, Position = `-4, 24, -4`
> (X'te -4'ten başlayıp 8 gidince +4'te biter → ortalanmış olur)

**Pivot (X, Y, Z)** — bone'u seçince çıkar
Dönme merkezi. **Animasyonun kalbi burası.**

> Kol omuzdan dönmeli. O yüzden kolun pivotu **omuzda** olmalı, kolun
> ortasında değil. Pivot yanlışsa kol havada garip döner.

**Rotation (X, Y, Z)**
Derece cinsinden dönüş.

### 2.5 Pivot'u ayarlama (görsel yöntem)

Sayıyla uğraşmak istemezsen:

1. Bone'u Outliner'dan seç
2. Sol araç çubuğundan **"Pivot Tool"** seç (nokta+ok ikonu)
3. 3B görünümde turuncu noktayı sürükle

### 2.6 Doku (texture)

**En kolay yol — şablon üret:**

1. Modelin tamamını bitir
2. Üst menü: **File → Export → (veya) Textures panelinde "+" → Create Texture**
3. Açılan pencerede **"Template"** kutusunu işaretle
4. Size: 64×64
5. **Confirm**

Blockbench modelinin açılımını (UV) otomatik çizer. Artık:

1. Üstteki **"Paint"** sekmesine geç
2. Fırça seç, renk seç, doğrudan model üzerine boya

**Kum rengi paleti** (kopyala yapıştır):

| Ne | Kod |
|---|---|
| Açık kum | `#DBC58A` |
| Orta kum | `#C4A96B` |
| Koyu kum | `#9A7E42` |
| Gölge/çatlak | `#6B5528` |
| Islak/sıkışmış kum | `#8A7038` |

> **Not:** Kum askeri ve devi şu an vanilla kum dokusunu kullanıyor ve fena
> durmuyor. Kendi dokunu yaparsan çok daha iyi olur ama acil değil — önce
> modeli ve animasyonu bitir.

### 2.7 Kaydetme

- **Ctrl+S** → `.bbmodel` olarak kaydeder (senin çalışma dosyan)
- Bunu **sık sık** yap. `.bbmodel` senin kaynak dosyan, onu kaybetme.

---

<a name="bölüm-3"></a>
## BÖLÜM 3 — İsimlendirme kuralları (ZORUNLU)

Kod bone isimlerini **doğrudan** referans ediyor. İsim tutmazsa parça hareket
etmez veya oyun çöker.

### Kurallar

1. **Sadece küçük harf**
2. **Boşluk yok** — alt çizgi kullan: `right_arm`
3. **Türkçe karakter yok** — `sağ_kol` ❌ , `right_arm` ✅
4. Aşağıdaki listedeki isimleri **birebir** kullan
5. Fazladan detay küpü ekleyebilirsin (isim serbest), ama **ana bone isimleri
   değişmemeli**

### Model başına zorunlu bone isimleri

**sand_soldier** ve **giant_sand_soldier** (ikisi aynı iskelet):
```
root
├── body
│   └── head
├── right_arm
├── left_arm
├── right_leg
└── left_leg
```

**sand_colossus**:
```
colossus_root
├── lower_sand_mass
├── torso
│   ├── head
│   │   └── head_crystal
│   ├── chest_crystal
│   ├── back_crystal
│   ├── right_arm
│   │   ├── right_shoulder_crystal
│   │   └── right_hand
│   │       └── mace
│   │           ├── mace_handle
│   │           └── mace_head
│   └── left_arm
│       ├── left_shoulder_crystal
│       └── left_hand
```

**sand_wall**:
```
wall_root
├── wall_body
└── spikes          ← dikenler AYRI bone (fırlatmadan önce çıkacaklar)
```

**sand_spike**:
```
spike_root
├── base
├── spike_main
├── spike_left
└── spike_right
```

**sand_fist** (yumruk eklentisi):
```
fist_root
├── forearm
└── fist
```

**sand_shockwave**:
```
shockwave_root
└── ring
```

---

<a name="bölüm-4"></a>
## BÖLÜM 4 — Model tasarımları

Aşağıdaki ölçüler **başlangıç noktası**. Üzerinde oyna, orantıyı bozmadığın
sürece sorun yok. Sayılar piksel, Y=0 zemin.

### 4.1 SAND SOLDIER — 32 px (2 blok)

İnsan oranı ama Steve değil: **omuzlar geniş, uzuvlar kalın, yüzey düzensiz.**

| Bone | Pivot (X,Y,Z) | Küp Position | Küp Size |
|---|---|---|---|
| `body` | 0, 12, 0 | -4.5, 12, -2.5 | 9 × 12 × 5 |
| `head` | 0, 24, 0 | -4, 24, -4 | 8 × 8 × 8 |
| `right_arm` | -5.5, 22, 0 | -9.5, 10, -2.5 | 5 × 12 × 5 |
| `left_arm` | 5.5, 22, 0 | 4.5, 10, -2.5 | 5 × 12 × 5 |
| `right_leg` | -2.2, 12, 0 | -4.7, 0, -2.5 | 5 × 12 × 5 |
| `left_leg` | 2.2, 12, 0 | -0.3, 0, -2.5 | 5 × 12 × 5 |

**Kum hissi için ek küpler** (aynı bone'un içine, isim serbest):

- Omuzlarda: `right_arm` içine 7×4×7 küp, omuz hizasında (Y≈20)
- Göğüste: `body` içine 6×5×2 küp, biraz önde (Z ≈ -4)
- Sırtta: `body` içine 4×6×2 küp, arkada (Z ≈ +2.5)
- Kafada: `head` içine 4×2×4 küp, tepede (Y≈32) — kırık taç gibi
- Şurada burada 2×2×2 küçük yumrular

**Tasarım notları:**
- Yüzeyi düz bırakma, 1-2 piksel taşan küçük küpler ekle
- Simetrik olmasın, sağ ve sol biraz farklı olsun
- Yüz detayı: göz yerine iki küçük çukur/koyu leke yeter

### 4.1b ASKER TÜRLERİ — iki farklı saldırı tipi

Askerlerin **iki türü** var, her birinin kendi saldırı deseni var. İkisi de
**aynı iskeleti** kullanıyor (yukarıdaki tablo), sadece kollar ve gövde farklı.

Tek bir `.bbmodel` içinde iki tür yapabilirsin (aşağıda anlatıyorum) veya iki
ayrı dosya. **Tek dosya daha kolay.**

#### BLADE (bıçak) — hızlı, seri, düşük hasar

Kolları kum bıçağına dönüşmüş. İnce, çevik, hızlı iki vuruş yapar.

| Değişiklik | Nasıl |
|---|---|
| Kollar **ince** | `right_arm` / `left_arm` küpünü 5×12×5 yerine **4×13×4** yap |
| Ön kolda bıçak | Kolun ucuna ekle: 2×14×7 küp, öne doğru uzanan (bıçak ağzı) |
| Gövde ince | `body` 9×12×5 yerine **8×12×4** |
| Omuz küçük | Omuz yumrusunu 7×4×7 yerine 5×3×5 yap |
| Kafa sivri | Kafanın üstüne 3×4×3 sivri küp |

Bıçağı **keskin** göstermek için: bıçak küpünü uca doğru daralt (2-3 küp üst
üste, her biri daha ince: 7 → 5 → 3 → 1 genişlik).

#### BREAKER (kırıcı) — yavaş, ağır, yüksek hasar

İri kollar, yumruklar. Yukarıdan aşağı ağır darbe indirir.

| Değişiklik | Nasıl |
|---|---|
| Kollar **kalın** | `right_arm` / `left_arm` küpünü **7×12×7** yap |
| Dev yumruk | Kolun ucuna 9×7×9 küp (kolu aşan yumruk) |
| Gövde geniş | `body` 9×12×5 yerine **11×12×6** |
| Omuz büyük | Omuz yumrusu 9×5×9 |
| Kafa gövdeye gömük | `head` pivotunu 1 px aşağı al (Y=23) — boynu yok gibi |

Yumruğun üstüne 2-3 tane 3×3×3 kum yumrusu ekle — ağırlık hissi versin.

#### Tek dosyada iki tür nasıl yapılır

1. `sand_soldier.bbmodel`'i aç
2. Outliner'da `right_arm` bone'unun **içine iki grup** aç:
   - `blade_parts`
   - `breaker_parts`
3. Bıçak küplerini `blade_parts` içine, yumruk küplerini `breaker_parts` içine koy
4. Aynısını `left_arm` ve `body` için de yap
5. Kod hangi türse o grubu gösterir, diğerini gizler

> Bu isimler de **birebir** olmalı: `blade_parts`, `breaker_parts`.

Karışık gelirse iki ayrı dosya yap, isimleri:
`sand_soldier_blade.bbmodel` ve `sand_soldier_breaker.bbmodel`. İkisi de olur,
bana hangisini seçtiğini söyle yeter.

### 4.2 GIANT SAND SOLDIER — 64 px (4 blok)

Aynı iskelet, **2 katı** ölçek + orantı değişikliği.

| Bone | Pivot (X,Y,Z) | Küp Position | Küp Size |
|---|---|---|---|
| `body` | 0, 24, 0 | -11, 24, -6 | 22 × 24 × 12 |
| `head` | 0, 48, 0 | -6, 48, -6 | 12 × 12 × 12 |
| `right_arm` | -13, 45, 0 | -23, 21, -6 | 12 × 24 × 12 |
| `left_arm` | 13, 45, 0 | 11, 21, -6 | 12 × 24 × 12 |
| `right_leg` | -5, 24, 0 | -11, 0, -6 | 12 × 24 × 12 |
| `left_leg` | 5, 24, 0 | -1, 0, -6 | 12 × 24 × 12 |

**Farklar (önemli):**
- **Kafa küçük**: normal askerde kafa/gövde oranı büyük, devde küçük.
  12 px kafa, 24 px gövdeye göre orantısız küçük durmalı — ağırlık hissi bundan
- **Omuzlar çok geniş**: gövdeden 4-5 px taşan omuz küpleri ekle
- **Kollar uzun ve kalın**: dizlere kadar insin
- **Bacaklar kısa ve kalın**: dev, hantal görünmeli
- Yüzeyde daha büyük kum blokları (4×4×4'e kadar)

### 4.3 SAND COLOSSUS — ~96 px (6 blok) — BACAKSIZ

Ultinin görsel zirvesi. **Bacak yok.** Alt kısım sürekli akan kum kütlesi.

| Bone | Pivot (X,Y,Z) | Küp Position | Küp Size |
|---|---|---|---|
| `lower_sand_mass` | 0, 0, 0 | -18, 0, -18 | 36 × 26 × 36 |
| `torso` | 0, 26, 0 | -16, 26, -9 | 32 × 34 × 18 |
| `head` | 0, 60, 0 | -8, 60, -8 | 16 × 16 × 16 |
| `right_arm` | -18, 56, 0 | -32, 24, -8 | 16 × 32 × 16 |
| `left_arm` | 18, 56, 0 | 16, 24, -8 | 16 × 32 × 16 |
| `right_hand` | -24, 24, 0 | -34, 14, -10 | 20 × 12 × 20 |
| `left_hand` | 24, 24, 0 | 14, 14, -10 | 20 × 12 × 20 |

**lower_sand_mass tasarımı** — bacak yerine geçen kütle:
- Aşağı doğru genişleyen bir yığın (üstte dar, altta geniş)
- Düz koni yapma; kenarları düzensiz, dalgalı olsun
- 3-4 katman halinde üst üste küpler, her katman biraz kaydırılmış
- En altta zemine yayılan ince bir tabaka (36×2×36 gibi)

**KRİSTALLER** — ayrı bone, ayrı küp (kod bunlara ayrı hasar uygulayacak):

| Bone | Konum | Size |
|---|---|---|
| `chest_crystal` | Göğüs ortası, Z ≈ -10 (öne çıkık) | 8 × 10 × 5 |
| `right_shoulder_crystal` | Sağ omuz üstü | 7 × 7 × 7 |
| `left_shoulder_crystal` | Sol omuz üstü | 7 × 7 × 7 |
| `head_crystal` | Alnın ortası | 6 × 8 × 4 |
| `back_crystal` | Sırt ortası | 8 × 12 × 5 |

Kristal rengi kumdan **belirgin farklı** olmalı — oyuncu nereye vuracağını
anlamalı:
- Kristal ana renk: `#E8A33D` (parlak amber)
- Kristal iç/parlak: `#FFD98A`
- Kristal kenar: `#8A5A15`

Kristalleri sivri yap — küpü döndürerek (Rotation 45°) elmas görünümü verebilirsin.

**TOPUZ** (`mace`, sağ elin içinde):

| Bone | Küp Size | Not |
|---|---|---|
| `mace_handle` | 6 × 30 × 6 | Sap, elden yukarı uzanır |
| `mace_head` | 20 × 20 × 20 | Devasa baş, sapın ucunda |

Topuz başına 4-6 tane 4×8×4 diken ekle (dışa doğru). Topuz **abartılı büyük**
olmalı — dokümandaki "çok büyük olmalı" notu.

### 4.4 SAND WALL — 80 × 54 × 13 px (5 × 3.4 × 0.8 blok)

| Bone | Küp Position | Küp Size |
|---|---|---|
| `wall_body` | -40, 0, -6 | 80 × 54 × 13 |

**Tasarım:**
- Düz dikdörtgen **yapma**. Üst kenarı düzensiz olsun:
  gövdenin üstüne farklı yüksekliklerde 6-8 tane küp ekle (8×4×13, 12×7×13...)
- Yüzeyde 2-3 px çıkıntılar, kum katmanları
- Alt kenarda dökülmüş kum yığınları

**`spikes` bone'u** (ayrı tutuyoruz, çünkü fırlatmadan önce çıkacaklar):
- Dış yüzeye (Z negatif taraf) 12-15 tane diken
- Her diken: 5×5×14 küp, uca doğru daralan (2-3 küp üst üste, küçülerek)
- Düzenli ızgara yapma, dağınık yerleştir

### 4.5 SAND SPIKE — 48 px (3 blok)

| Bone | Küp Position | Küp Size |
|---|---|---|
| `base` | -8, 0, -8 | 16 × 6 × 16 |
| `spike_main` | -5, 4, -5 | 10 × 40 × 10 |
| `spike_left` | -11, 2, -4 | 7 × 24 × 7 |
| `spike_right` | 4, 2, -4 | 7 × 22 × 7 |

**Tasarım:**
- `spike_main` yukarı doğru **daralmalı**: tek küp yerine 3-4 küp üst üste,
  her biri daha ince (10 → 8 → 5 → 3)
- Yan dikenler ana dikenin yarısı kadar, hafif dışa eğik (Rotation X/Z ≈ 15°)
- Taban düzensiz, kırık kum

**Varyasyon:** Aynı dosyada 3 farklı diken yaparsan (`spike_a`, `spike_b`,
`spike_c`) oyunda rastgele seçebiliriz, tekrar hissi olmaz. İstersen.

### 4.6 SAND FIST — yumruk eklentisi

Oyuncunun sağ kolunun üstüne geçer. Kol 4×12×4, bu onu **kaplamalı**.

| Bone | Küp Position | Küp Size |
|---|---|---|
| `forearm` | -5, 0, -5 | 10 × 14 × 10 |
| `fist` | -8, -12, -8 | 16 × 14 × 16 |

**Tasarım:**
- `fist` abartılı büyük — normal elin 3-4 katı
- Parmak eklemleri: yumruğun ön yüzüne 4 tane 3×3×3 küp
- Yüzeyden taşan kum parçaları, çatlaklar
- Pivot'u **dirsekte** tut (Y=0 civarı)

### 4.7 SAND SHOCKWAVE — halka

Topuz yere vurunca dışa açılan halka.

| Bone | Not |
|---|---|
| `ring` | Yassı, geniş halka |

**Tasarım:**
- 16 tane ince küp (6×3×3), daire üzerine dizilmiş, merkeze bakacak şekilde
  döndürülmüş
- Çap ≈ 48 px (3 blok) — oyunda büyütülecek
- Çok ince olsun, yerden 1-2 px yukarıda

---

<a name="bölüm-5"></a>
## BÖLÜM 5 — Animasyon yapımı

### 5.1 Animate moduna geçme

1. Üst ortadaki sekmelerden **"Animate"**e tıkla
2. Sol tarafta **"Animations"** paneli çıkar
3. Alt tarafta **Timeline** (zaman çizelgesi) açılır

### 5.2 Yeni animasyon oluşturma

1. Animations panelinde **"+"** butonuna tıkla
2. İsim yaz (aşağıdaki listeden **birebir**), `Enter`
3. Animasyon seçiliyken sağda ayarlar:
   - **Loop**: `idle`, `walk`, `move` için → **"Loop"** seç
   - Tek seferlik olanlar (`attack`, `spawn`, `death`) için → **"Once"**
   - **Length**: süre (saniye)

### 5.3 Keyframe (anahtar kare) koyma

Temel mantık: **"şu anda şu poz"** dersin, Blockbench aradakini kendi doldurur.

1. Timeline'da **playhead**'i (kırmızı çizgi) istediğin saniyeye sürükle
2. **Outliner'dan bir bone seç**
3. 3B görünümde bone'u döndür/taşı
   - Döndürmek için: sol araç çubuğundan **Rotate** aracı (dairesel oklar)
   - Ya da sağdaki Element panelinde Rotation sayılarını yaz
4. Değişiklik yaptığın an timeline'da **otomatik keyframe** oluşur (elmas şekli)

**Tekrarla:** playhead'i ilerlet → pozu değiştir → yeni keyframe.

### 5.4 Önemli püf noktalar

- **0. saniyeye mutlaka keyframe koy.** Yoksa animasyon garip başlar
- **Son kareyi ilk kareyle aynı yap** (loop animasyonlarda) — yoksa zıplar
- Rotation kullan, Position'ı az kullan. Uzuvlar döner, kaymaz
- Bir animasyonda **her bone'a dokunmak zorunda değilsin**. Sadece hareket
  edenlere keyframe koy
- Önizleme: Timeline'ın altındaki **play** butonu

### 5.5 Hangi model için hangi animasyonlar

İsimleri **birebir** bu şekilde yaz.

#### sand_soldier
| İsim | Süre | Loop? | Ne olacak |
|---|---|---|---|
| `spawn` | 1.2s | Once | Kumdan oluşma (aşağıda ayrıntı) |
| `idle` | 3.0s | Loop | Hafif nefes, omuz oynaması |
| `walk` | 1.0s | Loop | Kol/bacak salınımı |
| `attack_slash` | 0.5s | Once | **BLADE türü** — çift savurma |
| `attack_slam` | 0.9s | Once | **BREAKER türü** — yukarıdan ağır darbe |
| `hurt` | 0.3s | Once | Gövde geri sarsılır |
| `death` | 0.8s | Once | Çökerek dağılma |

**`attack_slash` (BLADE — hızlı çift savurma):**

| Saniye | Ne olacak |
|---|---|
| 0.0 | Normal duruş |
| 0.08 | Sağ kol geri ve yukarı (X: -70°), gövde hafif sağa döner (Y: 20°) |
| 0.18 | Sağ kol hızla sola savrulur (X: 20°, Y: -40°) — **1. vuruş** |
| 0.28 | Sol kol geri hazırlanır (X: -60°) |
| 0.38 | Sol kol savrulur (X: 20°, Y: 40°) — **2. vuruş** |
| 0.50 | Normal duruşa dön |

Anahtar: **keskin ve hızlı** olsun. Kollar arasında bekleme olmasın, biri
biterken diğeri başlasın.

**`attack_slam` (BREAKER — ağır darbe):**

| Saniye | Ne olacak |
|---|---|
| 0.0 | Normal duruş |
| 0.30 | İki kol yukarı kalkar (X: -140°), gövde geri yaslanır (X: -15°) |
| 0.40 | Tepede **bekleme** (aynı poz tekrar keyframe) — ağırlık hissi |
| 0.55 | Kollar hızla aşağı iner (X: 30°), gövde öne eğilir (X: 25°) |
| 0.62 | Bacaklar hafif çöker (Y: -1) — darbe sarsıntısı |
| 0.90 | Normal duruşa dön |

Anahtar: **0.30 → 0.40 arasındaki bekleme**. Bu duraklama darbeyi ağır
hissettiren şey. Atlarsan vuruş hafif kalır.

#### giant_sand_soldier
| İsim | Süre | Loop? | Ne olacak |
|---|---|---|---|
| `spawn` | 2.0s | Once | Ağır oluşma + yere iniş |
| `idle` | 4.0s | Loop | Çok yavaş nefes |
| `walk` | 1.6s | Loop | Ağır, yavaş adım |
| `heavy_attack` | 1.2s | Once | İki kol yukarı → aşağı vuruş |
| `hurt` | 0.4s | Once | Hafif sarsıntı (dev az sarsılır) |
| `death` | 1.5s | Once | Dizüstü çöküş → dağılma |
| `roar` | 1.0s | Once | Kafa yukarı, kollar açılır |

#### sand_colossus
| İsim | Süre | Loop? | Ne olacak |
|---|---|---|---|
| `transformation` | 4.0s | Once | Kumdan yükselip oluşma |
| `idle` | 5.0s | Loop | Kum kütlesi nefes alır gibi |
| `move` | 2.0s | Loop | Alt kütle akış yönünde uzar |
| `mace_raise` | 1.0s | Once | Topuz yukarı kalkar |
| `mace_smash` | 0.8s | Once | Topuz aşağı iner |
| `stagger` | 1.2s | Once | Sendeleme (kafa kristali kırılınca) |
| `crystal_break` | 0.5s | Once | Kısa sarsıntı |
| `death` | 3.0s | Once | Kütle dağılır |

#### sand_wall
| İsim | Süre | Loop? | Ne olacak |
|---|---|---|---|
| `spawn` | 0.5s | Once | Zeminden yukarı yükselir |
| `idle` | 3.0s | Loop | Kenarlardan kum dökülür (hafif titreşim) |
| `spike_emerge` | 0.45s | Once | **Dikenler dış yüzeyden çıkar** |
| `death` | 0.6s | Once | Üstten aşağı çöker |

#### sand_spike
| İsim | Süre | Loop? | Ne olacak |
|---|---|---|---|
| `emerge` | 0.4s | Once | Yerden fırlar |
| `idle` | 2.0s | Loop | Hafif titreşim |
| `sink` | 0.4s | Once | Çökerek kaybolur |

#### sand_fist
| İsim | Süre | Loop? | Ne olacak |
|---|---|---|---|
| `windup` | 0.25s | Once | Kol geri, yumruk büyümeye başlar |
| `expand` | 0.2s | Once | Yumruk tam boyuta ulaşır |
| `strike` | 0.15s | Once | İleri savrulur |
| `recovery` | 0.35s | Once | Küçülerek geri döner |

#### sand_shockwave
| İsim | Süre | Loop? | Ne olacak |
|---|---|---|---|
| `expand` | 0.8s | Once | Halka küçükten büyüğe açılır |

### 5.6 `spawn` animasyonu nasıl yapılır (en zoru, adım adım)

Kum askerinin yerden oluşması. Mantık: **parçaları küçükten büyüğe getir.**

1. `spawn` animasyonu oluştur, Length = `1.2`, Once
2. Playhead'i **0.0**'a al
3. Outliner'dan **tüm bone'ları tek tek seç** ve her birinin **Scale**'ini
   `0` yap (Element panelinde Scale alanı)
   - Scale alanı görünmüyorsa: Timeline'ın solundaki bone satırında
     **"Scale"** kanalını aç
4. Şimdi sırayla büyüt:

| Saniye | Ne yap |
|---|---|
| 0.0 | Hepsi Scale = 0 |
| 0.15 | `right_leg` + `left_leg` Scale = 0.3 |
| 0.35 | `right_leg` + `left_leg` Scale = 1 |
| 0.50 | `body` Scale = 0.5 |
| 0.65 | `body` Scale = 1 |
| 0.80 | `right_arm` + `left_arm` Scale = 1 |
| 0.95 | `head` Scale = 1 |
| 1.20 | Hepsi Scale = 1, hafif "oturma" (body Y -1 sonra 0) |

5. Play tuşuyla izle, beğenmezsen keyframe'leri sürükleyerek zamanlamayı ayarla

> Aynı mantık `transformation` (Colossus) ve `spawn` (Giant) için de geçerli,
> sadece daha yavaş ve daha ağır.

### 5.7 `walk` animasyonu (en çok kullanılan)

1. `walk` oluştur, Length = `1.0`, **Loop**
2. Sadece 3 keyframe yeter:

| Saniye | right_arm | left_arm | right_leg | left_leg |
|---|---|---|---|---|
| 0.0 | X: +25° | X: -25° | X: -25° | X: +25° |
| 0.5 | X: -25° | X: +25° | X: +25° | X: -25° |
| 1.0 | X: +25° | X: -25° | X: -25° | X: +25° |

(0.0 ve 1.0 aynı → döngü pürüzsüz olur)

Dev için açıları küçült (±15°) ve süreyi uzat → ağır yürüyüş.

---

<a name="bölüm-6"></a>
## BÖLÜM 6 — Bana nasıl vereceksin

### 6.1 Çıkarman gereken 3 dosya türü

Her model için:

| Dosya | Ne | Nasıl çıkarılır |
|---|---|---|
| `.bbmodel` | Senin kaynak dosyan | `Ctrl+S` |
| `.geo.json` | Modelin oyun formatı | File → Export → **Export Bedrock Geometry** |
| `.animation.json` | Animasyonlar | Animate modunda → File → Export → **Export Bedrock Animation** |
| `.png` | Doku | Textures panelinde dokuya sağ tık → **Save As** |

### 6.2 Klasör yapısı — dosyaları TAM olarak buraya koy

Proje klasörün: `C:\Users\user\Desktop\SuperheroMod`

```
SuperheroMod\
└── src\main\resources\assets\superheromod\
    ├── geo\                      ← .geo.json dosyaları
    │   ├── sand_soldier.geo.json
    │   ├── giant_sand_soldier.geo.json
    │   ├── sand_colossus.geo.json
    │   ├── sand_wall.geo.json
    │   ├── sand_spike.geo.json
    │   ├── sand_fist.geo.json
    │   └── sand_shockwave.geo.json
    │
    ├── animations\               ← .animation.json dosyaları
    │   ├── sand_soldier.animation.json
    │   ├── giant_sand_soldier.animation.json
    │   ├── sand_colossus.animation.json
    │   ├── sand_wall.animation.json
    │   ├── sand_spike.animation.json
    │   └── sand_fist.animation.json
    │
    └── textures\entity\          ← .png dosyaları (bu klasör ZATEN VAR)
        ├── sand_soldier.png
        ├── giant_sand_soldier.png
        ├── sand_colossus.png
        ├── sand_wall.png
        ├── sand_spike.png
        └── sandman.png           ← Sandman'in kendi skini (Bölüm 7)
```

**`geo` ve `animations` klasörleri henüz yok — sen oluşturacaksın.**
Sağ tık → Yeni → Klasör, isimleri **küçük harf** yaz.

### 6.3 `.bbmodel` kaynak dosyaların nereye?

Bunlar oyuna girmez ama kaybolmasın:

```
SuperheroMod\
└── blockbench\                   ← bu klasörü de sen oluştur
    ├── sand_soldier.bbmodel
    ├── giant_sand_soldier.bbmodel
    └── ...
```

### 6.4 Dosya isimlendirme kuralları

- **Hepsi küçük harf**
- **Boşluk yok**, alt çizgi kullan
- Uzantı dahil **tam** yukarıdaki gibi olmalı
- `sand_soldier.geo.json` ✅
- `Sand Soldier.geo.json` ❌
- `sandsoldier.geo.json` ❌

### 6.5 Bana haber verme

Dosyaları koyduktan sonra bana şunu yaz:

> "sand_soldier modelini koydum"

Ben gerisini hallederim: GeckoLib'i kurarım, entity'yi modele bağlarım,
animasyonları tetikleyecek kodu yazarım.

**Hepsini bir anda bitirmen gerekmiyor.** Bir model bitir, haber ver, ben
bağlayayım, oyunda gör, sonra diğerine geç. Böylece yanlış giden bir şey varsa
ilk modelde yakalarız.

### 6.6 Hangi sırayla yapmanı öneririm

1. **`sand_spike`** — en basit, tek parça, 3 kısa animasyon. Isınma turu.
2. **`sand_soldier`** — asıl iskelet mantığını burada öğreneceksin
3. **`sand_wall`** — kolay ama diken bone'u mantığını kuruyor
4. **`giant_sand_soldier`** — askerin kopyası, ölçek ve orantı değişikliği
   (Blockbench'te `sand_soldier.bbmodel`'i aç → File → Save As → yeni isim →
   üzerinde çalış)
5. **`sand_fist`** — küçük ama oyuncu koluna oturması ayar ister
6. **`sand_shockwave`** — çok basit
7. **`sand_colossus`** — en büyük iş, en sona

---

<a name="bölüm-7"></a>
## BÖLÜM 7 — Sandman'in kendi bedeni (Blockbench gerekmiyor)

Şu an Sandman **Cyclops'un görünümünde** çünkü ona ait bir skin yok ve
büyütme kodu yazılmadı.

### 7.1 Ne istiyorsun

"Normal Steve'den azıcık büyük, yine insani ama tank olduğu belli."

### 7.2 Bunun için Blockbench GEREKMİYOR

İki şey lazım, ikisi de kolay:

**1) Skin dosyası (sen yapacaksın)**

- 64×64 PNG, standart Minecraft skin düzeni
- Şuraya koy: `src\main\resources\assets\superheromod\textures\entity\sandman.png`
- Cyclops'ta yaptığımızın aynısı — sistem zaten kurulu, dosyayı koyman yeterli
- Kum rengi tonları, çatlaklı doku, sarı-kahve palet

**2) Büyütme (ben yapacağım)**

Vanilla oyuncu modelini `%12` büyüteceğim. Bu:
- Steve'den belirgin ama abartısız büyük
- Hâlâ insan oranında
- Kapılardan geçebilir, mevcut haritalarda sorun çıkarmaz

> **Neden %12?** %20+ büyütünce oyuncu blok yüksekliğini aşıyor ve tavanlara
> takılmaya başlıyor. %12 "iri adam" hissi veriyor ama fizik sorunları
> çıkarmıyor. İstersen sonra oynarız, tek sayı değiştirmek yeterli.

Ayrıca Sand Armor seviyesi yükseldikçe zaten üstüne kum katmanları biniyor —
zırh doldukça daha da irileşmiş görünecek.

### 7.3 Sandman kolunun animasyonu

Cyclops için yaptığım **Poz Stüdyosu** (`P` tuşu) Sandman için de çalışıyor.
Sand Fist vuruşunun kol pozunu orada hazırlayıp bana verebilirsin:

1. Oyunda `P` → stüdyo açılır
2. Kol pozunu ayarla → **Kare Ekle**
3. Birkaç kare yap → **Oynat** ile izle
4. **Java Ver** → `run\pose_animation.txt` dosyası oluşur
5. O dosyayı bana ver

Bu, Sand Fist'in kol animasyonunu Blockbench'siz halleder.

---

<a name="bölüm-8"></a>
## BÖLÜM 8 — Kontrol listesi

Her modeli bitirdiğinde bunları kontrol et:

### Model
- [ ] Format **"Bedrock Model"** mi? (Java/Modded Entity değil)
- [ ] Bone isimleri Bölüm 3'teki listeyle **birebir** aynı mı?
- [ ] Hepsi küçük harf, boşluksuz, Türkçe karaktersiz mi?
- [ ] Ayaklar Y=0'da mı? (model havada asılı değil)
- [ ] Pivotlar doğru yerde mi? (kol omuzda, bacak kalçada döner)
- [ ] Model 3B görünümde döndürünce her açıdan düzgün mü?

### Doku
- [ ] Doku 64×64 mü?
- [ ] Modelin her yüzü boyalı mı? (boşluk kalmamış)

### Animasyon
- [ ] İsimler Bölüm 5.5'teki listeyle birebir aynı mı?
- [ ] Loop olması gerekenler Loop, olmayanlar Once mu?
- [ ] 0. saniyede keyframe var mı?
- [ ] Loop animasyonlarda ilk ve son kare aynı mı?
- [ ] Play ile izledin mi, takılma/zıplama var mı?

### Dosyalar
- [ ] `.geo.json` → `assets\superheromod\geo\` içinde mi?
- [ ] `.animation.json` → `assets\superheromod\animations\` içinde mi?
- [ ] `.png` → `assets\superheromod\textures\entity\` içinde mi?
- [ ] `.bbmodel` → `blockbench\` klasöründe yedekli mi?
- [ ] Dosya isimleri tam olarak Bölüm 6.2'deki gibi mi?

---

## TAKILDIĞINDA

Bana şunları söyle, çözerim:

- Ekran görüntüsü at (Blockbench'ten veya oyundan)
- Hangi bölümde takıldığını yaz
- Hata mesajı varsa aynen kopyala

Model oyunda görünmüyorsa / garip duruyorsa sorun genelde şunlardan biri:
bone ismi tutmuyor, pivot yanlış yerde, ya da dosya yanlış klasörde.
Üçünü de ben kontrol edebilirim.
