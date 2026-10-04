package com.FIRNI.superheromod.client.gui;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The roster on the champion select screen: each hero's name, title, colour, splash art and the skills
 * it introduces (as many as the hero has). New heroes go here; the empty "?" slots fill up in order.
 */
public final class Champions {
    public record Skill(String key, String name, String text) {}
    public record Champion(String id, String name, String title, int accent, List<Skill> skills) {
        public ResourceLocation splash() { return new ResourceLocation(SuperheroMod.MODID, "textures/gui/champions/" + id + ".png"); }
    }

    public static final List<Champion> ALL = List.of(
            new Champion("ghost_rider", "GHOST RIDER", "Johnny Blaze — Cehennemin Süvarisi", 0xFFFF7A1A, List.of(
                    new Skill("SOL", "Zincir Kombosu", "Üç vuruşluk zincir kombosu. Dövüştükçe zincir kızışır, alev alır ve daha çok yakar."),
                    new Skill("SAĞ", "Zincir Fırlat", "Zinciri fırlatır. Bir canlıya tutunursa sağ tık onu çeker, sol tık seni ona götürür."),
                    new Skill("SHIFT", "Hell Cycle", "Alevli motosikleti çağırır, arkasında ateş izi bırakır. Yakıt bitince sürücüsüz gidip ilk çarptığı yerde patlar."),
                    new Skill("R", "Cehennem Nefesi", "Basılı tut: genişleyen bir alev nefesi. Saniye saniye yakar ve yavaşlatır."),
                    new Skill("F", "Cehennem Çukuru", "Zinciri yere vurur; yer yarılır, cehennem ateşi fışkırır."),
                    new Skill("X", "Penance Stare", "Sinematik: kurbanın gözlerinin içine bakar, işlediği her günahı ona yaşatır."))),
            new Champion("cyclops", "CYCLOPS", "Scott Summers — X-Men'in Lideri", 0xFFFF3040, List.of(
                    new Skill("SOL", "Optic Blast", "Tek ve sert bir göz ışını: küçük bir patlama, yolundaki bloğu kırar."),
                    new Skill("SAĞ", "Sarsıcı Işın", "Sürekli ışın; hedef hasar aldıkça ışın güçlenir."),
                    new Skill("R", "Seri Atış", "Art arda kısa ışınlar tarar."),
                    new Skill("C", "Seken Işın", "Yüzeylerden seken ışın; sektiği yerleri kırar."),
                    new Skill("SHIFT", "İtki Patlaması", "Işınla kendini ileri fırlatır."),
                    new Skill("F", "Optik Yükseliş", "Işını yere vererek havaya yükselir."),
                    new Skill("Q", "Ruby Rage", "Ultimate: büyük bir öfke ışını, canın yarısı kadar hasar."),
                    new Skill("X", "MAXIMUM POWER", "Sinematik: vizörü söker, dev ışınla rakibini sise fırlatır."))),
            new Champion("sandman", "SANDMAN", "Flint Marko — Yaşayan Kum", 0xFFE8B45A, List.of(
                    new Skill("SOL", "Kum Yumruğu", "Kolu kumdan uzayıp ileri vurur, yolundaki blokları kırar."),
                    new Skill("SAĞ", "Kum Pençesi", "Bir alanı kumla kavrar ve içindekileri kendine çeker."),
                    new Skill("V", "Dikenli Duvar", "Önüne dikenli bir kum duvarı yükseltir."),
                    new Skill("F", "Kum Yolculuğu", "Kuma dönüşüp akarak yol alır."),
                    new Skill("C", "Kum Askerleri", "Yerden savaşan kum askerleri çıkarır."),
                    new Skill("R", "Kum Sarkıtları", "Bir alana yerden kum mızrakları fışkırtır."),
                    new Skill("G", "Kum Patlaması", "Kum zırhını boşaltıp çevresine patlatır."),
                    new Skill("Z", "Kum Kulesi", "Altında bir kum kulesi yükselir."),
                    new Skill("X", "Dev Kum Askeri", "Dev bir kum askeri çağırır."))),
            new Champion("thor", "THOR", "Odinson — Gök Gürültüsü Tanrısı", 0xFF7FB8FF, List.of(
                    new Skill("SOL", "Mjolnir Kombosu", "Üç vuruş: soldan sağa, sağdan sola, sonra havaya kaldıran bir aparkat."),
                    new Skill("SAĞ", "Çekiç Fırlat", "Mjolnir'i fırlatır; tekrar basınca eline geri döner."),
                    new Skill("SHIFT", "Fırlatılış", "Basılı tut: çekiç yanında döner; bırakınca baktığın yere fırlarsın. Çarptığını çekiçle birlikte sürüklersin."),
                    new Skill("E", "Dönen Kalkan", "Çekici döndürerek korunur; doğru anda basarsan mükemmel savuşturma."),
                    new Skill("R", "Wakanda Darbesi", "Havada şimşekler saçarak yere iner."),
                    new Skill("F", "Gök Işını", "Çekici göğe kaldırır, ardından iki saniyelik bir yıldırım ışını."),
                    new Skill("X", "God of Thunder", "Sinematik: rakibi bulutların üstüne taşır ve yeri yararcasına geri çakar."))),
            new Champion("hulk", "HULK", "Bruce Banner — Durdurulamaz Güç", 0xFF6BE04A, List.of(
                    new Skill("G", "Dönüşüm", "Bruce Banner ile Hulk arasında dönüşür. Yetenekler yalnızca Hulk'ta."),
                    new Skill("SOL", "Patlayıcı Yumruk", "Sağ-sol yumruklar; basılı tutunca yolundaki her şeyi delen şarjlı yumruk."),
                    new Skill("SAĞ", "Gard", "Önden gelen hasarı büyük ölçüde keser; dayanıklılığı var."),
                    new Skill("R", "Thunderclap", "El çırpar: önüne ilerleyen bir hava duvarı, iter ve sersemletir."),
                    new Skill("F", "Yer Sarsan Yumruk", "Yere vurur; önünde derin bir yarık açılır."),
                    new Skill("C", "Kaya Fırlat", "Yerden dev bir kaya söker ve fırlatır."),
                    new Skill("BOŞLUK", "Dev Sıçrama", "Basılı tut: baktığın yöne uzun bir sıçrama, inişte krater."),
                    new Skill("X", "ONE PUNCH", "Sinematik: yumruk yağmuru, sonra dağı ikiye bölen tek yumruk."))),
            new Champion("zed", "ZED", "Gölgelerin Efendisi", 0xFFE0303A, List.of(
                    new Skill("SOL", "Gölge Kesişleri", "Üç vuruşluk kombo: sağ, sol, derin bitiriş. Kanatır; canı az olanı daha derin keser."),
                    new Skill("Q", "Keskin Shuriken", "Kıvrık bıçaklı shuriken fırlatır; gölgen varsa o da kendi yerinden atar."),
                    new Skill("F", "Canlı Gölge", "Gölgeni ileri yollar; tekrar basınca onunla yer değiştirirsin."),
                    new Skill("E", "Gölge Darbesi", "Etrafını keser; gölgen de keser ve yavaşlatır."),
                    new Skill("R", "Ölüm İşareti", "İki gölge kopyan hedefe girer, üstünde X yanar; sen kaybolur, arkasında belirirsin ve X patlar."),
                    new Skill("X", "Gölge İnfazı", "Sinematik: gölge ordusu kurbanı yutar, içinde kırmızı bir ışık patlar."))),
            new Champion("black_panther", "BLACK PANTHER", "T'Challa — Wakanda'nın Kralı", 0xFF9A6BFF, List.of(
                    new Skill("SOL", "Vibranyum Pençeleri", "Önündeki alana sağ, sol, çift pençe ve aparkat. Basılı tut: Wolverine gibi kollarını tam açıp süpüren vahşi pençe fırtınası (bekleme süreli). Vurduğun herkese 5 saniyelik mor çizik işareti."),
                    new Skill("SAĞ", "Pençe Atılışı", "15 blok içindeki işaretli hedefe atılır, çapraz tuttuğu pençelerini içten dışa açarak keser."),
                    new Skill("SHIFT", "Panter Atılışı", "Avına atılır, üstünden takla atıp arkasından tekmeler, rakip uçup sürünür. CTRL basılı: çömelir, 2 saniye sonra 5 saniyelik kamuflaj (2 kat hız, 4 bloktan uzaktan görünmez); hasar alınca bozulur."),
                    new Skill("BOŞLUK", "Çift Zıplama", "Havada bir kez daha zıplar, takla atar."),
                    new Skill("Q", "Dönen Üçlü Tekme", "Havada hiç inmeden dönerek sağ, sol, sağ tekme; sonuncusu fırlatır."),
                    new Skill("E", "Vibranyum Patlaması", "Aldığın hasar takımda enerji olarak birikir; kollarını açıp küre halinde patlatırsın, herkes bulunduğu yere göre fırlar."),
                    new Skill("R", "Panter Refleksi", "Koruma duruşuna geçer; önünden gelen her saldırıyı ön kollarıyla savuşturur (hasar almaz), arkadan gelenlerden kendiliğinden kaçar."),
                    new Skill("X", "Son Kovalamaca", "Sinematik ulti: hedefin sürdüğü arabayı arkadaki SUV'nin tavanından kovalar, kurşunları zırhına emdirir, atlayıp tavanı pençeleriyle söker, biriken enerjiyi tek seferde patlatır; araba takla atıp parçalanır."))),
            new Champion("magneto", "MAGNETO", "Erik Lehnsherr — Manyetizmanın Efendisi", 0xFFE0384A, List.of(
                    new Skill("SHIFT", "Uçuş", "Havalanır ve süzülür (Boşluğa iki kez basmak da olur). Uçarken Boşluk yükseltir, CTRL alçaltır."),
                    new Skill("SOL", "Demir Çubuk", "Metal parçalarından elinde bir çubuk oluşturup fırlatır; saplanır, iter ve yavaşlatır."),
                    new Skill("Q", "Demir Yağmuru", "Nişan aldığı yerin 8 blok üstünden dev demir çubuklar eğik düşüp toprağa saplanır; patlama ve alan hasarı. 3 kullanım hakkı."),
                    new Skill("E", "Hurda Telekinezisi", "Hedefin arkasından gelen hurda metal onu sarar ve havaya kaldırır; 3 saniye boyunca fareyle sürükler, duvara ya da yere çarpar. Sol tık: fırlat."),
                    new Skill("R", "Dev Demir Yumruk", "Önünde metalden dev bir yumruk belirir, nişanını takip eder; her sol tıkta yere iner (5 yumruk)."),
                    new Skill("F", "Manyetik Demir Kalkan", "Etrafından demir sütunlar yükselir, saldırıları durdurur. Tekrar basınca sütunlar parçalanıp her yöne fırlar."),
                    new Skill("X", "Manyetik İnfaz", "Sinematik: kızıl fırtınalı ölü bir dünyada hedefi X şeklinde iki dev sütuna çiviler, sonra metalle birlikte ezip fırlatır."))),
            new Champion("batman", "BATMAN", "Bruce Wayne — Kara Şövalye", 0xFFE8C547, List.of(
                    new Skill("SOL", "Yumruk Kombosu", "Arkham tarzı yakın dövüş: tıkladıkça hızlanan yumruk, kroşe, aparkat ve dirsek; sonunda seri yumruk yağmuru."),
                    new Skill("SAĞ", "Batarang", "Dokun: tek Batarang. Basılı tut: her 0,2 saniyede bir daha, en fazla 5 Batarang'ı iki eliyle birden fırlatır. Kemerinde 5 tane taşır, zamanla dolar."),
                    new Skill("R", "Alet Çarkı", "Basılı tut: çark açılır, fareyle alet seç (Sis Bombası, Flaş Bombası, Elektrikli Muşta, Bilek Topu, Sonik Tuzak). Dokun: seçili aleti kullan (muşta: tak / çıkar). Kendi sisinde termal görüş kendiliğinden açılır."),
                    new Skill("E", "Kancalı Tabanca", "Kancayı eline al, nereye atacağını seç. Sol tık: bloğa çekilirsin; düşmana uçup aparkat, havada tekme (kafasının arkasına yapışkan bomba, 2,5 sn sonra patlar), geriye takla. Sağ tık: düşmanın bacaklarına dolar, sırt üstü düşürüp sürüklersin, sonra ip onu sarar: sol tık spamlayıp kurtulana kadar kımıldayamaz. Çekilirken E: bırakıp havaya sıçrarsın."),
                    new Skill("Q", "Refleks Blok", "1 saniyelik pencere: önünden gelen saldırıları savuşturur. Yakından gelene eldiven dikenleri, uzaktan gelene pelerinini önüne çeker. Arkadan gelene işlemez."),
                    new Skill("X", "Sinematik", "Karanlıktan avlanma: kanca, yapışkan bomba, Batwing ve duvara çivileyen Batarang."),
                    new Skill("SHIFT", "Koşu", "Basılı tut: normal yürüyüşün 1,3 katı hızla koşar."),
                    new Skill("CTRL", "Takla", "İleri dalıp omzunun üstünden uzun bir takla; ortasında hasar almaz. 3 saniyede bir."),
                    new Skill("BOŞLUK", "Pelerinle Süzülme", "Havadayken basılı tut: pelerin kanat gibi açılır, baktığın yöne süzülürsün; aşağı bakınca hızlanır."))));

    /** How many card slots the grid always shows (the rest are "?" for the heroes to come). */
    public static final int MIN_SLOTS = 18;

    private Champions() {}

    public static Champion byId(String id) {
        for (Champion c : ALL) if (c.id.equals(id)) return c;
        return null;
    }
}
