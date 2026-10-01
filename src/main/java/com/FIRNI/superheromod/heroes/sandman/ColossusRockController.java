package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.combat.raycast.RaycastResult;
import com.FIRNI.superheromod.core.combat.raycast.RaycastSystem;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.ColossusActionPacket;
import com.FIRNI.superheromod.network.packet.ShockwavePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * COLOSSUS'UN KAYA FIRLATMASI — sag tik.
 *
 * Dev, topuz TUTMAYAN eliyle kendi govdesinden bir kaya kutlesi kopariyor ve
 * savurarak firlatiyor. Kaya carptigi yerde parcalanip alan hasari veriyor;
 * yakalananlar sersemliyor.
 *
 * SERSEMLETME uc parcadan olusuyor ve ucu de ayni anda gerekiyor:
 *   yavaslama    -> kacamiyor
 *   saldiri hizi -> karsilik veremiyor
 *   kamera sarsintisi -> nisan alamiyor
 * Sadece yavaslama verilse oyuncu yerinde durup rahatca vurmaya devam
 * ederdi; sarsinti olmadan da "sersemledim" hissi olusmuyor.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class ColossusRockController {

    /** Kolun geriye cekilip savrulmasi — firlatma bu kareden once yok. */
    private static final int WINDUP_TICKS = 9;

    private static final double ROCK_SPEED = 1.35;
    private static final double MAX_TRAVEL = 48.0;
    /**
     * Kayanin gorsel ve carpisma yaricapi.
     *
     * 0.9 blokta kaya "dev bir kaya" gibi degil atilmis bir tas gibi
     * duruyordu. Colossus 10 blok boyunda; elindeki kutle de o olcekte
     * okunmali.
     */
    private static final float ROCK_RADIUS = 2.2f;

    private static final float IMPACT_DAMAGE = 9.0f;
    private static final double BLAST_RADIUS = 6.5;

    /** Sersemleme suresi (tick). */
    private static final int STUN_TICKS = 70;

    /** Zayiflamis kolda hasar ve alan bu oranla carpilir. */
    private static final float WEAK_ARM_FACTOR = 0.6f;

    /** Kolun geri cekilme asamasi. */
    private static final class Windup {
        final int shapeId = nextShapeId++;
        final int targetId = nextShapeId++;
        final UUID player;
        int ticks = 0;
        /** Tus birakildi mi -- birakilinca kaya firlatilir. */
        boolean released = false;
        Windup(UUID player) { this.player = player; }
    }

    /** Havada ilerleyen kaya. */
    private static final class Rock {
        final int shapeId = nextShapeId++;
        final ServerLevel level;
        final UUID owner;
        Vec3 pos;
        final Vec3 velocity;
        final double distance;
        final boolean weak;
        double travelled = 0;

        Rock(ServerLevel level, UUID owner, Vec3 pos, Vec3 velocity, boolean weak, double distance) {
            this.level = level;
            this.owner = owner;
            this.pos = pos;
            this.velocity = velocity;
            this.weak = weak;
            this.distance = distance;
        }
    }

    private static final List<Windup> windups = new ArrayList<>();
    private static int nextShapeId = 700_000;
    private static final List<Rock> rocks = new ArrayList<>();
    private record Recovery(ServerLevel level,long start) {}
    private static final java.util.Map<UUID,Recovery> recoveries = new java.util.HashMap<>();
    public static ColossusPose.Action action(UUID id) {
        for(var w:windups)if(w.player.equals(id))return new ColossusPose.Action(ColossusActionPacket.ROCK_HOLD,w.ticks,MAX_AIM_TICKS+1);
        var r=recoveries.get(id);
        if(r==null)return null;
        int age=(int)(r.level().getGameTime()-r.start());
        if(age>=12){recoveries.remove(id);return null;}
        return new ColossusPose.Action(ColossusActionPacket.ROCK_THROW,age,12);
    }

    private ColossusRockController() {}

    public static boolean isThrowing(UUID playerId) {
        for (Windup w : windups) {
            if (w.player.equals(playerId)) return true;
        }
        return false;
    }

    /** Colossus formunda sag tik BASILINCA buraya gelir. */
    public static void throwRock(ServerPlayer player) {
        if (isThrowing(player.getUUID()) || ColossusSwordController.isActive(player.getUUID())
                || ColossusMaceController.isSwinging(player.getUUID())) return;

        windups.add(new Windup(player.getUUID()));

        // Istemci animasyonu: kol geri cekilip savrulur
        ModNetworking.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new ColossusActionPacket(player.getUUID(),
                        ColossusActionPacket.ROCK_HOLD, MAX_AIM_TICKS + 1));

        // Govdeden kaya kopuyor
        if (player.level() instanceof ServerLevel level) {
            Vec3 hand = handPosition(player);
            level.sendParticles(sand(), hand.x, hand.y, hand.z,
                    40, 0.8, 0.8, 0.8, 0.08);
            level.playSound(null, player.blockPosition(),
                    SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.6f, 0.35f);
        }
    }

    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        recoveries.entrySet().removeIf(e -> e.getValue().level().getGameTime()-e.getValue().start()>=12);
        if (windups.isEmpty() && rocks.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        tickWindups(server);
        tickRocks(server);
    }

    private static void tickWindups(net.minecraft.server.MinecraftServer server) {
        Iterator<Windup> it = windups.iterator();
        while (it.hasNext()) {
            Windup windup = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(windup.player);

            if (player == null || !SandColossusController.isColossus(windup.player)) {
                it.remove();
                continue;
            }

            windup.ticks++;

            if (player.level() instanceof ServerLevel level) {
                // Kol geriye cekilirken elde kaya buyur
                Vec3 hand = handPosition(player);
                level.sendParticles(sand(), hand.x, hand.y, hand.z,
                        5, 0.4, 0.4, 0.4, 0.03);
            }

            // KAYA TUS BIRAKILINCA firlatiliyor.
            //
            // Onceden sabit sure sonunda kendiliginden gidiyordu ve oyuncu
            // nereye attigini goremiyordu. Artik alan gosterilirken nisan
            // alinabiliyor; en az WINDUP_TICKS beklemek gerekiyor cunku
            // kayanin elde OLUSMASI icin zaman lazim.
            if (windup.ticks >= MAX_AIM_TICKS) windup.released = true;

            if (windup.released && windup.ticks >= WINDUP_TICKS) {
                release(player);
                it.remove();
            }
        }
    }

    /** Kaya savrularak firlatilir. */
    private static void release(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();

        // Topuz TUTMAYAN el: sag kol kirilmadiysa topuz sagda, kaya solda
        boolean maceInRight = !SandColossusController.isArmWeakened(player.getUUID(), true);
        boolean weak = SandColossusController.isArmWeakened(player.getUUID(), !maceInRight);

        Vec3 origin = handPosition(player);
        Vec3 delta = aimPoint(level, player).subtract(origin);
        Vec3 dir = delta.normalize();

        rocks.add(new Rock(level, player.getUUID(), origin, dir.scale(ROCK_SPEED), weak, delta.length()));
        recoveries.put(player.getUUID(),new Recovery(level,level.getGameTime()));
        ModNetworking.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new ColossusActionPacket(player.getUUID(), ColossusActionPacket.ROCK_THROW, 12));

        level.playSound(null, player.blockPosition(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, 1.1f);
        level.sendParticles(sand(), origin.x, origin.y, origin.z,
                25, 0.5, 0.5, 0.5, 0.15);
    }

    private static void tickRocks(net.minecraft.server.MinecraftServer server) {
        Iterator<Rock> it = rocks.iterator();
        while (it.hasNext()) {
            Rock rock = it.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(rock.owner);

            if (owner == null || !owner.isAlive() || owner.level() != rock.level) {
                it.remove();
                continue;
            }

            ServerLevel level = (ServerLevel) owner.level();
            Vec3 from = rock.pos;
            Vec3 dir = rock.velocity.normalize();
            double step = Math.min(rock.velocity.length(), Math.max(0,rock.distance-rock.travelled));

            RaycastResult result = RaycastSystem.cast(
                    level, owner, from, dir, step, ROCK_RADIUS, false,
                    e -> e instanceof LivingEntity && e != owner);

            Vec3 to = result.getHitPosition();

            // KAYA TICK BASINA DEGIL, ARA NOKTALARLA ciziliyor.
            //
            // Onceden partikuller sadece VARIS noktasina birakiliyordu;
            // kaya saniyede 20 kez isinlanan bir kume gibi gorunuyor ve
            // hareket "kasiyor" izlenimi veriyordu. Simdi tick icindeki
            // yol boyunca dagitiliyor, yani kaya araligi gercekten
            // katediyormus gibi okunuyor.
            int steps = Math.max(2, (int) (from.distanceTo(to) * 2.5));
            for (int i = 1; i <= steps; i++) {
                double t = i / (double) steps;
                Vec3 p = from.add(to.subtract(from).scale(t));

                level.sendParticles(rockChunk(), p.x, p.y, p.z, 4, 0.3, 0.3, 0.3, 0.0);
                if (i % 2 == 0) {
                    level.sendParticles(sand(), p.x, p.y, p.z, 2, 0.35, 0.35, 0.35, 0.02);
                }
            }

            boolean hit = result.didHitEntity() || result.didHitBlock();

            rock.travelled += from.distanceTo(to);
            rock.pos = to;

            if (hit || rock.travelled >= rock.distance-.001) {
                shatter(level, owner, to, rock.weak);
                it.remove();
            }
        }
    }

    /** Kaya parcalanir: alan hasari + sersemletme. */
    private static void shatter(ServerLevel level, ServerPlayer owner,
                                Vec3 center, boolean weak) {
        float damage = weak ? IMPACT_DAMAGE * WEAK_ARM_FACTOR : IMPACT_DAMAGE;
        double radius = weak ? BLAST_RADIUS * WEAK_ARM_FACTOR : BLAST_RADIUS;
        leaveCrater(level, owner, center);

        level.playSound(null, BlockPos.containing(center),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.8f, 0.55f);
        level.playSound(null, BlockPos.containing(center),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.8f, 0.45f);

        // Parcalanan kaya
        level.sendParticles(sand(), center.x, center.y, center.z,
                80, 1.4, 1.4, 1.4, 0.30);
        level.sendParticles(ParticleTypes.EXPLOSION,
                center.x, center.y, center.z, 3, 0.8, 0.8, 0.8, 0);

        AABB area = new AABB(center, center).inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != owner && e.isAlive())) {

            double dist = target.position().distanceTo(center);
            if (dist > radius) continue;
            double falloff = Math.max(0.3, 1.0 - dist / radius);

            target.hurt(owner.damageSources().playerAttack(owner),
                    (float) (damage * falloff));

            applyStun(target);

            Vec3 push = target.position().subtract(center);
            Vec3 flat = new Vec3(push.x, 0, push.z);
            flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
            target.setDeltaMovement(flat.x * 0.5, 0.35, flat.z * 0.5);
            target.hurtMarked = true;
        }

        // Halka + kamera sarsintisi — sersemlemenin ucuncu parcasi
        ModNetworking.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        center.x, center.y, center.z, radius * 4.0, level.dimension())),
                new ShockwavePacket(center, (float) radius, 20, 0.6f));
    }

    /**
     * SERSEMLETME.
     *
     * Yavaslama kacmayi, kazma yorgunlugu ise SALDIRI HIZINI dusuruyor —
     * vanilla'da Mining Fatigue seviye basina saldiri hizini da azaltiyor.
     * Korluk gibi agir bir etki bilerek kullanilmadi; gormeyi engellemek
     * sersemletmeden ote, oyuncuyu tamamen devre disi birakiyordu.
     */
    private static void applyStun(LivingEntity target) {
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS, 3, false, true));
        target.addEffect(new MobEffectInstance(
                MobEffects.DIG_SLOWDOWN, STUN_TICKS, 2, false, true));
        target.addEffect(new MobEffectInstance(
                MobEffects.CONFUSION, STUN_TICKS, 0, false, false));
    }

    private static void leaveCrater(ServerLevel level, ServerPlayer owner, Vec3 hit) {
        Vec3 ground = SandSpikeController.groundUnder(level, hit.add(0, 2, 0));
        if (ground == null) return;
        BlockPos surface = BlockPos.containing(ground.add(0, -.1, 0));
        int changed = 0;
        for (int x=-5; x<=5; x++) for (int z=-5; z<=5; z++) {
            double distance = Math.sqrt(x*x+z*z);
            if (distance > 5) continue;
            int depth = Math.max(1, (int)Math.ceil(3*(1-distance/5)));
            for (int y=0; y<depth && changed<220; y++) {
                BlockPos p=surface.offset(x,-y,z);
                double dx=p.getX()+.5-owner.getX(), dz=p.getZ()+.5-owner.getZ();
                if (dx*dx+dz*dz<16) continue;
                var state=level.getBlockState(p);
                if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                        || state.getDestroySpeed(level,p)<0) continue;
                level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
                changed++;
            }
        }
        // Find the actual floor after excavation, rather than burying the ball in unbroken terrain.
        Vec3 floor=SandSpikeController.groundUnder(level,ground.add(0,1,0));
        if (floor==null) return;
        var ball=com.FIRNI.superheromod.core.entity.ModEntities.SETTLED_SAND_BALL.get().create(level);
        if (ball!=null) {
            ball.setPos(hit.x,floor.y,hit.z);
            level.addFreshEntity(ball);
        }
    }

    /** Kayanin cikacagi el — devin omuz hizasinda ve yaninda. */
    private static Vec3 handPosition(ServerPlayer player) {
        return ColossusCrystal.handPosition(player,SandColossusController.isArmWeakened(player.getUUID(),true));
    }

    private static BlockParticleOption sand() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    }

    /** Kayalasmis kum — govdeden kopan parca bundan. */
    private static BlockParticleOption rockChunk() {
        return new BlockParticleOption(ParticleTypes.BLOCK,
                Blocks.SANDSTONE.defaultBlockState());
    }

    /**
     * Kayayi ve nisan halkasini cizim listesine ekler.
     *
     * Kaya partikulle ciziliyordu ve GORUNMUYORDU: mermi hizli oldugu icin
     * partikuller bir iki karede geride kaliyor, oyuncuya yere carpma
     * efektinden baska bir sey ulasmiyordu. Artik somut geometri.
     *
     * Nisan halkasi da buradan gidiyor: dev bir kaya firlatiyor ama nereye
     * dustugu ancak carptiktan sonra anlasiliyordu.
     */
    static void collectShapes(java.util.List<
            com.FIRNI.superheromod.network.packet.SandShapeSyncPacket.Shape> out) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (Rock rock : rocks) {
            // Donme acisi konumdan turetiliyor: kaya ucarken yuvarlanmali,
            // sabit acili bir kutu uzayda kaymis gibi duruyordu
            float spin = (float) ((rock.pos.x + rock.pos.z) * 57.0);

            out.add(new com.FIRNI.superheromod.network.packet.SandShapeSyncPacket.Shape(
                    rock.shapeId,
                    com.FIRNI.superheromod.network.packet.SandShapeSyncPacket.TYPE_ROCK,
                    rock.pos.x, rock.pos.y, rock.pos.z,
                    spin, 0f, 1f, (float) ROCK_RADIUS, 0f));
        }

        // Nisan halkasi: SADECE hazirlik sirasinda. Surekli gorunseydi
        // dev formunda ekranda hep bir halka dolasirdi.
        for (Windup windup : windups) {
            ServerPlayer player = server.getPlayerList().getPlayer(windup.player);
            if (player == null) continue;
            if (!(player.level() instanceof ServerLevel level)) continue;

            // KAYA ELDE OLUSUYOR: nisan alinirken oyuncu neyi
            // firlatacagini goruyor. Onceden kaya ancak firladiktan
            // sonra vardi ve hazirlik bos bir bekleme gibi duruyordu.
            Vec3 hand = handPosition(player);
            float grow = Math.min(1f, windup.ticks / (float) WINDUP_TICKS);

            out.add(new com.FIRNI.superheromod.network.packet.SandShapeSyncPacket.Shape(
                    windup.shapeId,
                    com.FIRNI.superheromod.network.packet.SandShapeSyncPacket.TYPE_ROCK,
                    hand.x, hand.y, hand.z,
                    windup.ticks * 6f, 0f, 1f, ROCK_RADIUS * grow, 0f));

            Vec3 spot = aimPoint(level, player);
            if (spot == null) continue;

            out.add(new com.FIRNI.superheromod.network.packet.SandShapeSyncPacket.Shape(
                    windup.targetId,
                    com.FIRNI.superheromod.network.packet.SandShapeSyncPacket.TYPE_TARGET,
                    spot.x, spot.y, spot.z,
                    player.getId(), 0f, 1f, (float) BLAST_RADIUS, 0f));
        }
    }

    /**
     * Imlecin gosterdigi zemin noktasi.
     *
     * Kayanin gercekte carpacagi yer bu; gosterge ile atis ayni kaynaktan
     * hesaplanmali, yoksa halka yalan soyler.
     */
    private static Vec3 aimPoint(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getLookAngle();

        RaycastResult result = RaycastSystem.cast(
                level, player, eye, look, MAX_TRAVEL, 0.6f, false,
                e -> e instanceof LivingEntity && e != player);

        Vec3 point = result.getHitPosition();
        Vec3 ground = SandSpikeController.groundUnder(level, point.add(0, 1.0, 0));
        return ground != null ? ground : point;
    }

    /**
     * Sag tik birakildi — kaya gidiyor.
     *
     * Basili tutulurken alan gosterilip nisan aliniyor; oyuncu nereye
     * attigini gormeden atmak zorunda kalmiyor.
     */
    public static void aimReleased(ServerPlayer player) {
        for (Windup w : windups) {
            if (w.player.equals(player.getUUID())) w.released = true;
        }
    }

    /** Cok uzun tutulursa kendiliginden gitsin; sonsuz nisan olmasin. */
    private static final int MAX_AIM_TICKS = 120;
}
