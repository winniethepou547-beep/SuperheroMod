# Ghost Rider — yüzey zinciri ve ön kaldırma

- Motorda sağ tık blok yüzeyine tutunabilir. Ardından sol tık motoru bağlantı noktasına hızlandırır. İkinci sağ tık yüzey bağını bırakır. Blokların içinden teleport yapılmaz.
- Space basılıyken yerde ön kaldırma 45 dereceyle sınırlı. Bırakınca görünen açıdan ileri sıçrama, ardından yerçekimi uygulanır. Havada tekrar sıçrama yok; bağlantı kesilmesi/odak kaybı nedeniyle geciken giriş otomatik sıçrama yapmaz.
- Arka teker dönüş merkezi için model ve yolcu yüksekliği birlikte düzeltildi.
- Motorda yakın zincir saldırısı 1.65 kat uzar; temas toleransı da genişler. Sunucu ve görsel aynı uzatmayı kullanır.
- Isınmış zincirde hasar çarpanı 1.6 ve ısı başına 0.45 HP ilave hasar.
- Ghost Rider seçiliyken ateş etiketli hasar iptal edilir, normal yanma durumu temizlenir. Diğer hasar türleri etkilenmez.
- Alev çıkışları kafatasının yanları, çene, şakaklar ve arkasını da kapsar. Saydam kafa/motor alevleri opak moblardan sonra çizilir; böylece sonraki mob çiziminin önceden çizilmiş alevi silmesi önlenir.
- Isı/şarj düz yazısı yerine bölmeli ısı göstergesi, ayrı dolum şeridi, durum başlığı ve ön kaldırma açısı paneli eklendi. Paneller çakışmayacak yüksekliklere ayrıldı.
- Protokol 11: istemci ve sunucu aynı sürüm olmalı.

Kontrol: derleme, GhostMotionCheck, GhostChainPhysicsCheck geçti. 0/15/30/45/90 derece taleplerinde sıçramanın en fazla 45 derece olması, tutuş/koltuk koordinatları ve sıcak zincir hasarı kontrol edildi. Canlı oyun içindeki yeni alev sıralaması, panel görünüşü ve duvara zincirle atılma bu turda gözle doğrulanmadı.
