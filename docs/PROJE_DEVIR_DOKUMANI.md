# SuperheroMod — Tam Proje Devir Dokümanı

> Bu belge, projeyi hiç görmemiş bir yapay zekâya (ChatGPT vb.) tek seferde
> eksiksiz bağlam vermek için yazıldı. Mimari kararlar, **neden** öyle
> yapıldıkları, denenip **reddedilen** yaklaşımlar, tüm sayısal değerler ve
> açık işler burada.

---

## 1. Proje Nedir

**Minecraft Forge 1.20.1** için yazılmış, süper kahraman temalı **PvP dövüş
modu**. Overwatch/League of Legends tarzı yetenek setleri olan kahramanlar,
eşleşme (matchmaking) sistemi, hub/arena döngüsü ve ilerleme (level, rank,
altın) sistemi içeriyor.

### Teknik künye

| | |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.10 |
| Java | 17 |
| Mod ID | `superheromod` |
| Kök paket | `com.FIRNI.superheromod` |
| Mixin | SpongePowered, `superheromod.mixins.json` |
| Proje yolu | `C:\Users\user\Desktop\SuperheroMod` |
| Kod hacmi | ~29.400 satır Java, ~200 sınıf |
| Commit sayısı | 53 |
| Uzak depo | **Kullanılmıyor.** Sadece yerel commit atılıyor, `git push` YASAK. |

### Aktif kahramanlar

1. **Cyclops** — menzilli ışın karakteri. Mekanik olarak bitti, **sesleri eksik**.
2. **Sandman** — yakın dövüş / alan kontrolü / summon / dönüşüm. **Aktif geliştirme burada.**

---

## 2. Çalışma Şekli (çok önemli)

Bu proje, teknik olmayan bir kullanıcıyla **adım adım** yürütülüyor:

- Kullanıcı Türkçe konuşuyor, cevaplar Türkçe olmalı.
- Kullanıcı **ekran görüntüsü ve elle çizim** ile geri bildirim veriyor.
- Aynı anda tek bir özellik üzerinde çalışılıyor; kullanıcı oyunda test edip
  sonucu bildiriyor.
- Kullanıcı **kod okumuyor** — açıklamalar davranış düzeyinde olmalı.
- **Commit mesajları Türkçe** ve "ne değişti + NEDEN" formatında, ASCII
  karakterlerle (Türkçe karakter kullanılmıyor, PowerShell bozuyor).
- Kod içi yorumlar Türkçe ve **niyet/gerekçe** anlatıyor, işlem anlatmıyor.

### Kalite çıtası: "Fisk's Superheroes"

Kullanıcı bu modu referans aldı. Her yetenekten beklenen:

- Ses
- Kamera sarsıntısı + darbe anında FOV değişimi
- Efektlerin **vücut parçalarına** bağlı olması (entity merkezine değil)
- Güç durumu arttıkça efektlerin de büyümesi
- 1. ve 3. şahısta tutarlılık
- Sabit kodlanmış değil, **veri odaklı** yapı

> Not: Fisk'in JS tabanlı render motoru **kopyalanmıyor**, sadece kalite
> anlayışı benimseniyor.

---

## 3. Dizin Yapısı

```
src/main/java/com/FIRNI/superheromod/
├── SuperheroMod.java, Config.java
├── client/
│   ├── gui/           (ClanScreen, MatchFoundScreen, VsScreen, PoseStudioScreen...)
│   ├── hud/           (HeatBarOverlay, SandArmorOverlay, UltimatePromptOverlay...)
│   ├── input/         (AbilityKeyHandler, HeroSneakSuppressor)
│   └── render/
│       ├── arm/       (BendableArm*, SandArmLayer, SandArmorLayer)
│       ├── colossus/  (ColossusModel, ColossusRenderer, ColossusCrystalModel)
│       ├── entity/    (SandSoldierModel, SandSoldierRenderer, SandAimLineRenderer)
│       ├── puppet/    (sinematik kuklaları)
│       ├── anim/      (PoseStudio — canlı poz ayarlama aracı)
│       ├── util/      (SandGeometry — ortak ışıklı kum geometrisi)
│       └── Client*Data.java  (istemci tarafı durum aynaları)
├── core/
│   ├── ability/       (Ability, AbilityManager, AbilitySlot, AbilityType, AbilityConfig)
│   ├── character/     (SuperCharacter, CharacterRegistry)
│   ├── cinematic/     (CinematicDirector, Shot, ActorPose, StageFrame...)
│   ├── combat/raycast/(RaycastSystem, RaycastResult)
│   ├── matchmaking/   (QueueManager, MatchManager, ActiveMatch, MapRegistry...)
│   ├── progression/   (LevelSystem, RankSystem, GoldSystem, Capability)
│   ├── social/        (Clan, Party, MessageManager)
│   ├── region/        (Region, RegionManager — hub/arena bölgeleri)
│   ├── world/         (HubBuilder, PvpMapBuilder, ArenaLocations, koruma handler'ları)
│   └── entity/        (ModEntities, PlayerSummoned)
├── heroes/
│   ├── cyclops/       (8 yetenek + ulti sineması)
│   └── sandman/       (9 yetenek + Colossus alt sistemi)
├── mixin/             (HumanoidModelMixin, AbstractClientPlayerMixin)
└── network/           (ModNetworking + 28 paket)

src/main/resources/
├── META-INF/mods.toml
├── superheromod.mixins.json
├── assets/superheromod/lang/en_us.json
├── assets/superheromod/textures/entity/cyclops.png
├── assets/superheromod/shaders/post/cinematic.json
├── assets/superheromod/shaders/program/cinematic_grade.{fsh,json}
└── data/superheromod/dimension/pvp_arena.json  (+ dimension_type)

docs/
├── BLOCKBENCH_REHBERI.md      (kullanıcı için model/animasyon rehberi)
├── MAXIMUM_POWER_SENARYO.md   (Cyclops ulti sinematik senaryosu)
└── PROJE_DEVIR_DOKUMANI.md    (bu dosya)
```

---

## 4. Çekirdek Mimari

### 4.1 Yetenek sistemi

```
SuperCharacter (kahraman)
  └── registerAbility(Ability)  →  AbilitySlot ile eşlenir
Ability
  ├── AbilityType: INSTANT | CHANNELED | TOGGLE | PASSIVE
  ├── AbilityState: IDLE | ACTIVE | CHANNELING | COOLDOWN
  ├── AbilityConfig: isim→değer sözlüğü (tüm sayılar burada, sabit kodlanmıyor)
  └── Kancalar: onActivate / onTick / onChannelStop / onFinish
```

**AbilitySlot enum sırası** (ordinal ağda kullanılıyor, sıra değiştirmek
protokolü bozar):
```java
LMB, RMB, SHIFT, SKILL_E, SKILL_F, SKILL_X, SKILL_C, SKILL_G, SKILL_V, ULTIMATE
```

**CHANNELED tipi** basılı tutma davranışı sağlar: basınca `onActivate`,
bırakınca `onChannelStop`. Sandman'ın sol ve sağ tıkı bunu kullanıyor.

### 4.2 Tuş yönlendirme (`AbilityKeyHandler`)

| Tuş | Slot |
|---|---|
| Sol tık | `LMB` |
| Sağ tık | `RMB` |
| Shift | `SHIFT` (eğilme kaldırıldı, itiş için) |
| R | `SKILL_E` |
| F | `SKILL_F` |
| C | `SKILL_C` |
| X | `SKILL_X` |
| G | `SKILL_G` |
| V | `SKILL_V` |
| Z | Kum kulesi (özel paket, slot değil) |
| Q | `ULTIMATE` |

**Önizleme yönlendirmesi:** Sand Wall veya Sand Grasp önizlemesi açıkken sol
tık *onay*, sağ tık *iptal* anlamına gelir; normal yeteneklere gitmez.

### 4.3 Ağ katmanı

`ModNetworking` tek `SimpleChannel`, paketler **sırayla** kaydediliyor —
kayıt sırası protokol kimliği demek, araya paket eklemek eski istemcileri
bozar.

Sandman'a ait paketler:

| Paket | Yön | İş |
|---|---|---|
| `SandWallSyncPacket` | S→C | Duvarların konum/durum/dağılma oranı |
| `SandWallActionPacket` | C→S | Onayla / iptal / fırlat |
| `SandGraspPreviewPacket` | S→C | Önizleme açık mı (tuş yönlendirmesi için) |
| `SandGraspActionPacket` | C→S | Onayla / iptal |
| `SandShapeSyncPacket` | S→C | Dünya şekilleri (el, kaya, göstergeler) |
| `SandArmSyncPacket` | S→C | Uzayan kolun **sadece uzunluğu** |
| `SandPillarPacket` | C→S | Z tuşu basıldı |
| `SandArmorPacket` | S→C | Zırh barı |
| `ColossusSyncPacket` / `ColossusActionPacket` | çift yönlü | Dev form durumu |
| `ShockwavePacket` | S→C | Şok dalgası halkaları |

---

## 5. Yerleşik Kurallar (bunlara uyulmalı)

Bu kurallar acı deneyimle oluştu; ihlal etmek daha önce çözülmüş hataları
geri getirir.

### 5.1 Blok yazma politikası

Varsayılan: **haritaya blok yazma.** PvP haritasında kalıcı tahribat kabul
edilemez. İstisnalar bilinçli ve sınırlı:

| Yetenek | Blok yazıyor mu | Gerekçe |
|---|---|---|
| Sand Wall | ❌ | Çarpışma elle itme ile çözülüyor |
| Sand Spike | ❌ | Aynı |
| Kum kulesi (Z) | ✅ **kalıcı** | Kullanıcı açıkça istedi; yeteneğin *ürünü* |
| Kum izi (patch) | ✅ **kalıcı** | Sadece en üst katman, sadece sağlam zemin |
| Kum sarkıtları (R) | ✅ **geçici** | Yetenek bitince geri alınıyor |
| Uzayan kol (LMB) | ✅ **kırıyor** | Kırılmaz blok + blok varlığı korunuyor |

**Her blok yazımında korunanlar:** `getDestroySpeed < 0` (bedrock, barrier)
ve `hasBlockEntity()` (sandık, fırın, tabela). Aksi hâlde yetenek arazi
aracı değil yıkım aracı olur.

### 5.2 Görsel çizim kuralları

- **Şekiller partikül değil GEOMETRİ.** Partikül kuvvet anlatır, kütle
  anlatmaz.
- **Göstergeler de partikül değil, çizim.** Partikül gösterge seyrek kalıyor,
  hedef hareket edince dağılıyor, kum zemininde kayboluyor.
- **Kum dokusu oyunun blok atlasından** alınır (`SandGeometry.sandSprite()`),
  kendi PNG'miz yok. Böylece kaynak paketi değiştiren oyuncuda şekiller de
  değişir ve araziyle ton tutar.
- **Aydınlatma zorunlu.** `POSITION_TEX_COLOR` shader'ı ne dünya ışığı ne yüz
  gölgelemesi uygular → gece yanan, gündüz aşırı parlak kutular. Doğru yol:
  `RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)` + gerçek ışık
  değeri + vanilla yüz gölgeleme oranları (üst 1.0, yan 0.8/0.6, alt 0.5).
  Ortak uygulama: `client/render/util/SandGeometry.java`.
- **Rastgelelik sabit tohumdan** türetilir. `random.nextDouble()` her karede
  farklı sonuç verir → şekil "kaynayan kütle" gibi görünür.

### 5.3 Ağ / senkron kuralları

- Sunucu 20/s, ekran 60+/s. Ham değer çizmek "takılma" hissi yaratır.
  **Ara değer zorunlu** ve `Minecraft.getFrameTime()` ile yapılmalı
  (duvar saati değil — istemci tick'iyle senkron olmaz).
- Ara değer için şekillere **kimlik** gerekiyor; kimliksiz eşleştirme
  yapılamaz.
- Açı ara değeri **kısa yoldan** olmalı; düz lerp 179°→−179° geçişinde tam
  tur döndürür.
- **Liste boşalınca son bir boş paket gönderilmeli.** Aksi hâlde istemci son
  kareyi zaman aşımına kadar (1.5 sn) ekranda tutar. (Duvar parçalarının
  havada kalması tam olarak buydu.)

### 5.4 Model / animasyon kuralları

- `ModelPart` kutu koordinatlarını **içinde 16'ya böler**. Üstüne 1/16 ölçek
  uygulamak tekrar eden bir hata kaynağı.
- Entity model uzayında **y aşağı doğru artar**; 2 bloklu mob için y=24 ayak.
  Vanilla sırası: `scale(-1,-1,1)` sonra `translate(0, -1.501, 0)`.
- Aşağı sarkan bir kolda **`yRot` görünmez** (kol kendi ekseninde döner).
  Yatay savurma için önce `xRot` ile kol yatay duruma getirilmeli.
- Vanilla `netHeadYaw` = bakış − gövde, **pozitif** olarak `yRot`'a verilir.
  İşareti ters vermek hatayı iki katına çıkarır.
- Ölçek çarpımı yapılıyorsa **her karede sıfırlanmalı**, yoksa sonsuza büyür.
- Vanilla `attackAnim` sayacı güvenilmez (6 tick, senkronu zayıf).
  Kendi senkronize ilerleme float'ımızı kullanıyoruz.

---

## 6. Sandman — Tam Yetenek Dökümü

Kayıt sırası: `heroes/sandman/SandmanCharacter.java`

### LMB — Sand Fist (uzayan kum kolu) · `CHANNELED`

`SandFistAbility` + `SandFistController` + `client/render/arm/SandArmLayer`

**Davranış:** Basılı tutulduğu sürece kol uzar ve uzunlukta kalır; bırakınca
geri toplanır. Tam uzunlukta tutulmaya devam edilirse ucunda **balyoz**
oluşur.

| Değer | |
|---|---|
| Şarj | 4 tick (0.2 sn) |
| Uzama hızı | 1.15 blok/tick |
| Geri toplanma | 0.85 blok/tick |
| Menzil | 8 blok |
| Hasar | 6.0 (balyozla ×1.6'ya kadar) |
| Savurma | 1.8 yatay / 0.55 dikey (balyozla artar) |
| Aynı hedefe tekrar vuruş | en az 12 tick arayla |
| Balyoz oluşumu | 12 tick |
| Maksimum tutma | 160 tick |
| Bekleme | 20 tick |

**Çizim:** Kol **oyuncu modelinin katmanı** olarak çiziliyor
(`model.rightArm.translateAndRotate` içine girilerek). Dünya uzayında
çizilirse oyuncu kıpırdadığında gövdeden kopar — bu yaşandı ve düzeltildi.

Boğumlar 6 piksel (vanilla ön kolla aynı), kalınlaşma **küp eğri** ile uca
toplanıyor, her boğumdan farklı açılarda kaya parçaları taşıyor. Balyoz kola
dik, uç kapaklı ve boyunlu.

**Poz:** Yetenek boyunca kol ileri bakar, kameranın **dikey ve yatay**
açısını takip eder, dirsek düz tutulur.

**Blok kırma:** Omuzdan uca kadar tüm hat, uca doğru genişleyen yarıçapla.
Adım boyu blok boyutundan küçük, yoksa hızlı kol delik deşik iz bırakır.

### RMB — Sand Grasp (çekme eli) · `CHANNELED`

`SandGraspAbility` + `SandGraspController`

Önünde dikdörtgen alan çizilir (kırmızı oklar, ucu Sandman'a bakar).
Sol tık onaylar, sağ tık iptal eder. Onaylanınca **uzak uçtan** kum eli
fırlar ve **Sandman'a doğru** sürüklenerek herkesi kucağına çeker.

| Değer | |
|---|---|
| Bekleme | 90 tick |
| Hasar | 6.0 |
| Alan | 9 uzunluk × 3 genişlik, 1.2 blok boşluk |
| Çekiş gücü | 2.3 |
| Yükseliş | 10 tick |
| Çekiş | 30 tick |

Elin geçtiği yer kuma dönüşür. Son çeyrekte el yüzeye gömülüp kaybolur.
Parmaklar çekiş boyunca yumruk sıkar gibi kapanır.

**Colossus formunda** sağ tık bunun yerine **kaya fırlatır** (aşağıda).

### V — Sand Wall (dikenli duvar) · `INSTANT`

`SandWallAbility` + `SandWallController` + `client/render/SandWallRenderer`

İlk basış hayali duvarı açar (sol tık onay / sağ tık iptal). Duvar
ayaktayken ikinci basış dikenleri çıkarıp duvarı ileri fırlatır.

| Değer | |
|---|---|
| Bekleme | 40 tick |
| Ölçü | 5 × 3.4 × 0.8 blok |
| Mesafe | 3.2 blok |
| Dayanıklılık | 60 |
| Ömür | **600 tick (30 sn)** |
| Uçuş hasarı | 7.0 |

Yüzeyinde **190 minik kum küpü** var; duvarın *kendi eksenlerinde*
konumlanıyorlar, böylece fırlatıldığında birlikte uçuyorlar. Ömrü dolunca
veya kırılınca **dağılma animasyonu** oynatılıyor: gerçek kum blokları
dökülürken gövde solup küçülüyor.

### F — Sand Travel · `INSTANT`

Menzil 28 blok, çapa yakalama 6 blok, bekleme 70 tick. Duvarları çapa olarak
kullanabiliyor.

### C — Sand Soldiers · `INSTANT`

`SandSoldiersAbility` + `SandSoldierEntity`

4 asker çağırır (en fazla 6 canlı), yarıçap 2.6, bekleme 200 tick.

**Asker türleri (`Variant`):**
- `BLADE` — ince kollar, hızlı
- `BREAKER` — iri kollar, ağır
- `RANGED` — omuz dikenleri, kum mermisi atar (`SandBoltController`)

**Yaşam döngüsü:** 24 tick'lik 8 aşamalı kumdan oluşma → aktif → dağılarak
ölme (gerçek `FallingBlockEntity` kum blokları saçılır).

**Savurma animasyonu (uzun süren hata):** Tetikleme `MeleeAttackGoal`'dan
çıkarıldı, doğrudan **mesafeye** bağlandı (2.6 blok, 18 tick arayla).
Animasyon üç aşamalı: kurulum → savurma → toparlanma. Kol önce yatay duruma
getirilir, savurma ondan sonra yapılır. Diğer kol neredeyse hareketsiz.

### X — Giant Sand Soldier · `INSTANT`

Dev asker (`GiantSandSoldierEntity`), aynı modeli farklı oranlarla kullanır.
İki saldırısı var:
- **Ağır vuruş** (`GiantSlamGoal`): iki eli havaya kalkar, tepede kum kütlesi
  oluşur, öne-aşağı iner. Hasar 9, patlama yarıçapı 7, bekleme 110 tick.
- **Düz oto saldırı**: asker savurmasının aynısı, ağır vuruş beklemesinde
  devreye girer. Ağır vuruş *sırasında* devre dışı.

### R — Sand Spears (kum sarkıtları) · `INSTANT`

`SandSpearFieldAbility` + `SandSpearFieldController` — **en yeni yetenek**

Önünde dikdörtgen alan işaretlenir (ince kırmızı çerçeve). **Her aşama
kademeli:**

| Aşama | Süre | Ne oluyor |
|---|---|---|
| Döşeme | 16 tick | Zemin **önden arkaya** sıra sıra kuma dönüyor |
| Yükseliş | 12 tick dalga + 6 tick | Sarkıtlar dalga hâlinde çıkıyor, **tepeleri önde** |
| Bekleme | 20 tick | |
| İniş | 12 tick dalga + 8 tick | Sütun kısalıyor, **en son kalan tepe** |

Sarkıtlar **gerçek `POINTED_DRIPSTONE`** blokları, kalınlık yukarıdan aşağı
inceliyor (TIP → FRUSTUM → MIDDLE → BASE). 1.25 blok aralıklı sıkı ızgaraya
küçük kayma ile yerleştiriliyor. Yetenek bitince tamamı kaldırılıyor.

| Değer | |
|---|---|
| Bekleme | 200 tick |
| Hasar | 8.0 (**hedef başına tek**) |
| Alan | 10 × 5 blok |
| Yavaşlatma | Slowness II, sürekli tazeleniyor |
| Kum izi ömrü | 220 tick |

### G — Sand Burst · `INSTANT`

Zırh barını boşaltır. Barın doluluğuna göre 3–14 diken, 2.0–9.0 hasar,
iç/dış yarıçap 2.0/5.5.

### Q — Sand Colossus (ULTIMATE)

`SandColossusAbility` + `SandColossusController` + `ColossusForm` +
`client/render/colossus/*`

Oyuncuyu **10 blok boyunda** dev bir kum kolossusuna dönüştürür. Bu bir
oyuncu **dönüşümü**, ayrı entity değil — kontrol oyuncuda kalıyor ve kristal
isabetleri saldıranın nişan ışınıyla çözülüyor.

- **Süre:** 420 tick
- **Gövde küçültme:** 0.86, kristal çarpanı 2.0
- **Oluşma:** `ColossusForm.FORM_TICKS` = 40, kademeli büyüme
- **Kristaller:** 5 adet (`ColossusCrystal` enum) — CHEST, RIGHT_SHOULDER,
  LEFT_SHOULDER, HEAD, BACK. Vurulunca kırılıyor.
- **Sol tık:** `ColossusMaceController` — topuz, menzil 9.0, şok dalgası 16.0
- **Sağ tık:** `ColossusRockController` — **basılı tutunca nişan alınır**
  (nişan halkası + kaya elde oluşur), bırakınca fırlatılır. Kaya yarıçapı
  2.2, menzil 48, patlama 6.5. Çarpanlar stun yiyor (Slowness IV + Mining
  Fatigue III + Confusion).

---

## 7. Cyclops — Özet

`heroes/cyclops/CyclopsCharacter.java`

| Tuş | Yetenek |
|---|---|
| LMB | Optic Blast (tek atış, 1 kalp, mini patlama + blok kırma) |
| RMB | Concussive Beam (0.5 kalpten başlar, hasar aldıkça 1.5'e çıkar) |
| R | Rapid Fire (taramalı, yarım kalp/atış) |
| C | Ricochet (1 kalp, sektiği yüzeyleri kırar) |
| SHIFT | Propulsion Burst (yarım kalp) |
| F | Optic Ascent |
| X | X-Ray |
| Q | Ruby Rage (ulti — yarım can hasar) + `MaximumPowerCinematic` |

**Isı sistemi:** `CyclopsHeatSync` + `HeatBarOverlay`.

**Eksik:** Sesler. Uzun süredir bekliyor.

---

## 8. Sinematik Motoru

`core/cinematic/` + `client/render/cinematic/` + `client/render/puppet/`

Kullanıcı buradan "film gibi olsun, oyun içi kamera gezintisi gibi değil"
istedi. Referanslar: **Marvel Tokon**, **Alex and Steve Life**.

- `Shot` — roll, pushIn (yumuşak yaklaşma), breath, sis yakın/uzak/renk
- `CinematicDefinition` — temel atmosfer + `PoseKey` izi, `Actor` enum
- `ActorPose` — 11 eklem, `noticeChain` / `impactChain` / `launchChain`
  (kuvvetin vücutta gecikmeli yayılması)
- `CinematicPostFx` — Minecraft post-process zinciri
  (`shaders/post/cinematic.json` + `cinematic_grade.fsh`)
- Kamera: `ViewportEvent.ComputeCameraAngles` (yaw/pitch/**roll**),
  `RenderFog`, `ComputeFogColor`; konum `Camera.position` reflection ile

**Kullanıcının açık reddettikleri:**
- Ekran karartma katmanı ❌
- Letterbox (sinema bandı) ❌
- Yerine: sis / renk / odak kullanılacak

`MaximumPowerCinematic` = 408 tick (~20 sn), 19 sahne.
Senaryo: `docs/MAXIMUM_POWER_SENARYO.md`

---

## 9. Sunucu / Meta Sistemler

| Alan | Sınıflar |
|---|---|
| Eşleşme | `QueueManager`, `MatchManager`, `ActiveMatch`, `ReadyManager`, `MapRegistry` |
| İlerleme | `LevelSystem`, `RankSystem` (`RankTier`), `GoldSystem`, capability tabanlı |
| Sosyal | `Clan`/`ClanManager`, `Party`/`PartyManager`, `MessageManager` |
| Dünya | `HubBuilder`, `PvpMapBuilder`, `CanyonTerrainGenerator`, `ArenaLocations` |
| Koruma | `HubProtectionHandler`, `BlockProtectionHandler`, `MapBlockTracker` |
| Boyut | `data/superheromod/dimension/pvp_arena.json` |
| Ekranlar | ModeSelect → MatchFound → MapVote → VS → Countdown → Scoreboard |

**Kritik ders — `HubProtectionHandler`:** Önceden `EntityJoinLevelEvent`
üzerinden *her* mobu kesiyordu ve doğuş sebebini bilmediği için doğuş
yumurtalarını, komutları ve modun kendi summonlarını da engelliyordu.
İptal edilen olay hiçbir uyarı basmadığı için hata **görünmezdi**.
Çözüm: `MobSpawnEvent.FinalizeSpawn` (doğuş sebebini taşır) + sadece doğal
doğuş türleri engelleniyor + `PlayerSummoned` arayüzü muaf.

---

## 10. Denenip Reddedilenler (tekrarlamayın)

| Deneme | Neden reddedildi |
|---|---|
| Askerlerin elinde **çekiç** | "Çekiç olduğu anlaşılmıyor" — iki kez genişletildi, yine olmadı. Kumdan bir yaratığın alet tutması zaten yanlış fikirdi. |
| Kola takılan **ayrı el kutusu** | Dev askerde kolla el arasında görünür boşluk bıraktı (ayrı ölçekleniyorlardı). |
| Uzayan kolun **partikülle** çizilmesi | "Kolumun devamı" gibi okunmuyordu. |
| Uzayan kolun **dünya uzayında** çizilmesi | Oyuncu kıpırdayınca gövdeden koptu. |
| Uzayan kolda **kum bloğu küpleri** | "Havada duran bloklar" gibi duruyordu. |
| Kum izinin **çizim katmanı** olması | "Üzerine yatırılmış saydam doku" gibi duruyordu, arazi değişmiyordu. |
| Göstergelerin **partikülle** çizilmesi | Seyrek kalıyor, dönünce dağılıyor, kum zemininde kayboluyor. |
| Kum çerçevesi göstergesi | Kumun üstüne kum → görünmüyor. Kırmızı gerekti. |
| **Ok** göstergesi (R'de) | Ok *yön* anlatır; R'de yön yok, *sınır* önemli → dikdörtgen çerçeve. |
| Colossus **piramit** alt gövde | "Kum dediğin dağınık olur" — hizalı kutular reddedildi. |
| Kristallerin **hepsi yukarı** bakması | Farklı açılarda küme istendi. |
| Kristallerin `FULL_BRIGHT` çizilmesi | Gece karanlıkta yanan lambalar gibi duruyordu. |
| Krater zemininde **hash tabanlı** blok seçimi | Magma ağırlıklı, obsidyen yok. Mesafe tabanlı bölgeleme ile değişti. |
| Sinematiğin **114 tick / 24 sahne** olması | "Çok hızlı, geçişler anlamsız". 623 → 408'e ayarlandı. |
| Namlu partiküllerinin **küp** dağılımı | Kafayı sarıyordu; öne kaydırma + disk dağılım gerekti. |
| Kum kulesinin **düz konik** daralması | "Gökdelen değil kum tepesi" → kademeli daralma. |
| Kum kulesinin oyuncunun **etrafına** kurulması | Oyuncu içinde sıkışıyordu → tepeye taşınıyor. |
| Kum kulesinin **çift boşlukla** tetiklenmesi | Boşluk zaten zıplama; her zıplayışta kule kuruluyordu → Z tuşu. |

---

## 11. Açık İşler

### Öncelikli
1. **Colossus yeniden tasarımı** — kullanıcı düzenli, kademeli bloklardan
   oluşan, golem oranlarında, bel altı kuma gömülü, **daha iri** bir tasarım
   çizdi. "Animasyon kolaylığına dikkat et, düzenli ve anlaşılır dursun."
   Parça hiyerarşisi (gövde → omuz → kol) doğru kurulmalı.
2. **Colossus kristallerinin uzuvlara tam bağlanması** — şu an görsel, vücut
   hareketinden *küçük* pay alıyor. Tam bağlamak için **sunucudaki isabet
   kontrolünün de** taşınması gerekiyor, yoksa oyuncu gördüğü yere nişan alıp
   ıskalar.
3. **Cyclops sesleri** — uzun süredir bekliyor.

### Orta
4. Mod ışınlarına **kendi damage type**'ı verilmeli ki Sand Body ışını yakın
   dövüşten ayırt edebilsin.
5. Kolun çizimdeki **2×2 kesit** yapısına geçmesi (şu an tek kutu, belirgin
   kalınlaştırıldı ama kesit yapısı yok).

### Beklenen dış girdi
6. Kullanıcı **Blockbench** ile model/animasyon yapacak. Hedef klasörler:
   `assets/superheromod/geo/`, `animations/`, `textures/entity/`.
   Muhtemel **GeckoLib** geçişi. Rehber: `docs/BLOCKBENCH_REHBERI.md`.

---

## 12. Kaynaklar ve Referanslar

### Kod içi belgeler
- `docs/BLOCKBENCH_REHBERI.md` — kullanıcının model/animasyon yapması için
  adım adım rehber
- `docs/MAXIMUM_POWER_SENARYO.md` — Cyclops ulti sinematik senaryosu
- Kod yorumları — **her önemli kararın gerekçesi kodda yazılı**, en iyi
  kaynak bunlar

### İlham alınan oyunlar / modlar
| Kaynak | Ne için |
|---|---|
| **Fisk's Superheroes** (Minecraft modu) | Genel kalite çıtası: ses, kamera sarsıntısı, uzuvlara bağlı efektler |
| **Marvel Tokon** | Sinematik kamera dili |
| **Alex and Steve Life** (animasyon serisi) | Uzuvların bölünmemiş görünmesi, buz kırılması |
| **League of Legends — Mordekaiser E** | Sand Grasp'in dikdörtgen alan + çekme mantığı |
| **Overwatch — Mei (ice boost)** | Kum kulesi yükselişi |
| **Overwatch / LoL** | Genel yetenek tasarımı, gösterge dili |

### Teknik referanslar (Forge 1.20.1)
- `RenderLevelStageEvent` — dünya çizimi
- `RenderLayer<AbstractClientPlayer, PlayerModel<...>>` — oyuncu modeli katmanı
- `EntityRenderersEvent.AddLayers` / `RegisterLayerDefinitions`
- `MobSpawnEvent.FinalizeSpawn` (doğuş sebebini taşır),
  `EntityJoinLevelEvent` (taşımaz)
- `ViewportEvent.ComputeCameraAngles` / `RenderFog` / `ComputeFogColor`
- `LivingFallEvent`, `LivingHurtEvent`
- `DeferredRegister<EntityType<?>>`, `SynchedEntityData` / `EntityDataAccessor`
- `LevelRenderer.getLightColor` — ışık örneklemesi
- `InventoryMenu.BLOCK_ATLAS` + `getParticleIcon` — blok dokusu erişimi

---

## 13. Bir Sonraki Kişiye Notlar

1. **Sessiz hatalara dikkat.** Bu projede iki ayrı "hiçbir şey olmuyor"
   hatası, hiçbir log basmayan iptal edilmiş Forge olaylarından çıktı.
   Çözüm yöntemi: kullanıcıya görünen durum mesajları ekleyip
   "çalışmıyor"u belirli bir hata dizesine çevirmek.

2. **Kullanıcının çizimleri şartname sayılır.** Elle çizilmiş görseller
   birebir uygulanmayı bekliyor; atlanan bir madde bir sonraki turda geri
   geliyor.

3. **Sayılar `AbilityConfig`'te**, sabit kodlanmıyor. Denge ayarı kod
   değişikliği gerektirmemeli.

4. **Yorumlar niyeti anlatır**, işlemi değil. "Şunu yapar" değil,
   "şu yüzden böyle, alternatifi şu sebeple olmadı".

5. **Asla `git push` yapma.** Uzak depo yapılandırılmış ama kullanıcı
   2026-08-18'de GitHub kullanımını kapattı.
