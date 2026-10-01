# Ghost Rider — 1 Ekim motor ve halka revizyonu

- Şarjda eller aşağı/öne, dirsekler aşağıya alındı. %100 (40 tick) şarjda iki halka otomatik fırlatılır; erken bırakma kombo yapar.
- Halka menzili 48 blok; temas 7 HP ve 4 saniye yanma. Aynı hedef aynı atışta tekrar vurulmaz.
- İzlerde her parçada yeniden başlayan desen yerine ortak zaman koordinatı ve yumuşak uçlar kullanılır.
- Alev paleti dışta koyu kırmızı, içte kırmızı/turuncu ve sıcak merkezde sarı.
- Alevli zincir, şarj, mermi, motor ve slam zemin temasları ortak yanık izine bağlandı. Tick başına 64 temas taraması, toplam 512 iz sınırı var.
- Farın arkasına siyah bağlantı kasası eklendi; ön teker alevi kısaltılıp yukarı taşındı.
- Motor son hızı 2.2 blok/tick; dönüş 6–7.5 derece/tick. Duvar çıkışı hızla güçlenir; üçüncü şahıs kamera eğime uyar ve duvar kontrolü yapar.
- Sağ altta dairesel ibreli gösterge, hız çubuğu ve yüksek hızda beliren alevler. FOV en fazla 12 derece genişler; kenarlarda hafif hız çizgileri oluşur.
- Motorda zincir atma menzili yaklaşık 48 blok. Bağ gerilince hedef sürüklenir; motor hızı 0.8 üzerindeyken yarım saniyelik aralıklarla sürükleme hasarı uygulanır.
- Bağ sonrası sol tık: yaya atılma 4 HP; motorla hız artışı ve hedefe atılma 6 HP.
- Motorda ikinci sağ tık: çekme noktası hareket eden motorun 12 blok önünü takip eder. Engellerden teleport yapılmaz; ulaşılamayan hedefte süre sınırı vardır.
- Zincir çekme/atılma sonunda 12 tick boyunca dinamik biçimde geri toplanır.
- Ağ protokolü 10: istemci ve sunucu aynı yeni jarı kullanmalı.

## Kontrol kapsamı

Derleme, GhostMotionCheck ve GhostChainPhysicsCheck çalıştırıldı. Yeni kontroller hızlanma sınırı, fren, dönüş ve tüm yönlerde 12 blokluk çekme noktası içindir. Shader ayrıca GPU üzerinde görüntülendi.

Canlı Minecraft sürüşü, duvara geçiş, hız göstergesinin oyun içi görünüşü ve çok oyunculu sürükleme henüz doğrulanmadı. Matematik testleri bunların görsel doğrulaması yerine geçmez.
