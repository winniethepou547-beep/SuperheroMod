# Sinematik prova kontrolleri

Güncel Sand Army provası: 330 tick / 16,5 saniye. Hileler açık olmalı.
Yetişkin zombi veya husk, eklemli NPC test rigini kullanır. Diğer mob modelleri
henüz aynı düzeyde animasyon uyarlamasına sahip değildir.

1. 24 blok içindeki hedefe bakıp **H** ile başlat. H tekrar basılırsa durur.
2. Sohbeti açıp `/cinematic seek 2.9` yaz: ilk karşı yumruk anına gider ve durur.
3. `/cinematic step 1` bir oyun tick'i (50 ms), `step -1` geriye gider.
   Kesirli adım da desteklenir: `/cinematic step 0.25` = 12,5 ms.
4. `/cinematic speed 0.25` çeyrek hız seçer; `/cinematic resume` oynatır.
5. `/cinematic pause` durdurur. `/cinematic speed 1` normal hıza döndürür.
6. `/cinematic stop` kamerayı ve katılımcıların kontrolünü geri verir.

Hızlı sahne incelemesi için doğrudan duraklatılmış başlatma:
`/cinematic preview sand_army @e[type=minecraft:husk,sort=nearest,limit=1] at 2.9`
Bu komut 2,9 saniyedeki kareyi açıp bekler; kısa prova bitmeden komut yazmak gerekmez.

## İncelenecek anlar

| Saniye | Olay |
| --- | --- |
| 2,9 | İlk karşı yumruk |
| 4,45 | Yana kaçış / ikinci karşı darbe |
| 6,85 | Savunmanın baskı altında kırılması |
| 9,4 | Yığın oluşumu |
| 11,25 | Toplu yükseliş |
| 12,25 | Kum küresinin kapanması |
| 13 | Kürenin patlaması |
| 14,05 | Hedefin yere inişi |

Komutlarda ondalık ayırıcı nokta olmalıdır. Seek saniye, step tick kullanır.
Hız aralığı 0,1–2 kat. Seek ve step her zaman duraklatır. Son kareye gidip
orada beklenebilir; resume sonlandırır. Bir prova en fazla 10 dakika sürer.

Kontroller yalnızca provayı başlatan operatörün, yan etkili olay içermeyen
provasında çalışır; canlı hasar veren yetenekler geriye sarılamaz. Sunucu
zamanı, hız, revizyon ve oturum kimliğini her iki katılımcıya gönderir.
Sarma sonrasında eski kamera/FOV/sis geçiş geçmişi temizlenir.

Ağ protokolü 4: istemci ve sunucuda aynı güncel mod bulunmalı.
Otomatik kontroller: ağır çekim, donmuş saat, kesirli ileri/geri adım,
zaman sınırları, geçersiz hız, kök hareket/IK ve sahne sözleşmeleri.
Bunlar gerçek FPS ölçümü veya tüm sahnenin görsel kalite onayı değildir.
