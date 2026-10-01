# Model ve sinematik incelemesi — 2026-09-07

## Dogrulanan durum
- Forge 1.20.1 projesi okunabiliyor. Ilk git status temiz.
- Projede rg ile .bbmodel bulunamadi. Modeller Java ModelPart olarak tanimli.
- SandSoldierModel: body ve right_arm/left_arm root altinda kardes. Govde donusu kollara miras kalmiyor.
- setupAnim tum parca pozlarini sifirlamiyor; swing sonrasi arm yRot kalabiliyor.
- Spawn, swing, slam ve aim ham senkron ilerlemeleriyle ornekleniyor. Render-frame ara degeri gerekli.
- CinematicClient.updatePuppets pozisyonu source.getPosition(partial) ile gercek varliktan aliyor.
- Kamera blend += 0.12 ve FOV/roll/fog sabit kare katsayilari FPS bagimli.
- CinematicClient.addPuppet yalniz AbstractClientPlayer kabul ediyor; mob hedefi icin kukla olusturmuyor.

## Uygulama sirasi
1. Sand soldier mevcut gorunumunu koruyan, govde-altinda omuz ve eklemli kol/bacak hiyerarsisi. Blockbench kaynak dosyasi ve oyun modelinin ayni veriden uretilmesi.
2. Olusma, idle, yuru, sag/sol vurus, menzilli atis, giant slam ve olum klipleri. Hasar ani animasyonun temas aniyla ortak zaman cizgisine baglanmali.
3. Colossus duzenli golem silueti, kuma gomulu alt govde. Kristaller ilgili kemigin cocugu; gorsel ve isabet konumlari uyumlu. Kaya elde olusma, tutma ve firlatma.
4. Grasp ve wall modellerini vanilla kum dokusu ve dunya isigi ile koru. Onizleme parlakligi ile gercek model isigi ayrilsin.
5. Sinematikte kamera ve aktorleri ayni kesirli zaman cizgisinden ornekle. Kare-sayisina bagli takip kaldirilsin.
6. Gercek varlik hareketinden bagimsiz sahne aktorleri, kamera raylari ve temas noktalarina bagli beam/prop soketleri.
7. Sahne sisi/aydinlatmasi; katilimcilara ozel render ve kesilme/olum/cikis geri-yukleme guvencesi.

## Dogrulama ve sinirlar
Bu bir ilk incelemedir, tum kodun denetlendigini veya oyun ici sorunun cozuldugunu iddia etmez. Henuz model veya oyun kodu degistirilmedi. Blockbench dosyasinin acilmasi, runtime aktarimi, derleme ve oyun ici goruntu ayri ayri dogrulanmali. Onceki sohbetin erisilemeyen ekran goruntuleri incelenmis sayilmaz. Github kullanilmayacak.