package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.core.entity.ModEntities;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * SAND SOLDIER — Sandman'in kumdan urettigi insan boyutunda savasci.
 *
 * Neden gercek entity: asker yol buluyor, hedef seciyor ve saldiriyor.
 * Duvar/diken gibi sunucu tarafi kontrollu nesne olsaydi navigasyon ve dovus
 * mantigini sifirdan yazmak gerekirdi; PathfinderMob bunlari hazir veriyor.
 *
 * Yasam dongusu:
 *   SPAWN    kumdan asamali olarak olusur (bu sirada hareketsiz ve hedefsiz)
 *   ACTIVE   sahibini takip eder, dusman bulur, saldirir
 *   CRUMBLE  suresi dolunca veya olunce dagilarak yok olur
 */
public class SandSoldierEntity extends PathfinderMob {

    /** Olusma ilerlemesi 0..1 — model bunu okuyup parcalari sirayla aciyor. */
    private static final EntityDataAccessor<Float> SPAWN_PROGRESS =
            SynchedEntityData.defineId(SandSoldierEntity.class, EntityDataSerializers.FLOAT);

    /** Dagilma ilerlemesi 0..1 — 1 olunca entity siliniyor. */
    private static final EntityDataAccessor<Float> CRUMBLE_PROGRESS =
            SynchedEntityData.defineId(SandSoldierEntity.class, EntityDataSerializers.FLOAT);

    /** Asker turu — model ve saldiri deseni buna gore degisiyor. */
    private static final EntityDataAccessor<Byte> VARIANT =
            SynchedEntityData.defineId(SandSoldierEntity.class, EntityDataSerializers.BYTE);

    /**
     * Asker turleri. Ikisi de ayni iskeleti kullanir; fark kollarda,
     * dayaniklilikta ve SALDIRI DESENINDE.
     */
    public enum Variant {
        /** Ince kollar, kum bicaklari. Hizli, seri cift vurus, dusuk hasar. */
        BLADE,
        /** Iri kollar, dev yumruklar. Yavas, agir tek darbe, alan savurmasi. */
        BREAKER;

        public static Variant byId(byte id) {
            Variant[] all = values();
            return id >= 0 && id < all.length ? all[id] : BLADE;
        }
    }

    /** Kumdan olusma suresi (tick). Dokumandaki 8 asama buna bolunuyor. */
    public static final int SPAWN_TICKS = 24;
    private static final int CRUMBLE_TICKS = 12;

    /** Sahibinden bu kadar uzaklasirsa geri doner. */
    private static final double FOLLOW_START = 12.0;
    private static final double FOLLOW_STOP = 4.0;

    @Nullable
    private UUID ownerId;
    private int lifetime = 400;
    private boolean crumbling;

    public SandSoldierEntity(EntityType<? extends SandSoldierEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.29)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.35);
    }

    // ------------------------------------------------------------------
    // Kurulum
    // ------------------------------------------------------------------

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SPAWN_PROGRESS, 0f);
        this.entityData.define(CRUMBLE_PROGRESS, 0f);
        this.entityData.define(VARIANT, (byte) 0);
    }

    public Variant getVariant() {
        return Variant.byId(this.entityData.get(VARIANT));
    }

    /**
     * Turu belirler ve o ture ait ozellikleri uygular.
     *
     * Ozellikler burada ayarlaniyor cunku iki tur ayri EntityType degil —
     * carpisma kutulari ayni oldugu icin tek tur yeterli, sadece degerler
     * ve gorunum degisiyor.
     */
    public void setVariant(Variant variant) {
        setVariantIdOnly(variant);

        switch (variant) {
            case BLADE -> {
                setAttr(Attributes.MAX_HEALTH, 16.0);
                setAttr(Attributes.MOVEMENT_SPEED, 0.34);
                setAttr(Attributes.ATTACK_DAMAGE, 2.5);
                setAttr(Attributes.KNOCKBACK_RESISTANCE, 0.15);
            }
            case BREAKER -> {
                setAttr(Attributes.MAX_HEALTH, 26.0);
                setAttr(Attributes.MOVEMENT_SPEED, 0.23);
                setAttr(Attributes.ATTACK_DAMAGE, 5.0);
                setAttr(Attributes.KNOCKBACK_RESISTANCE, 0.55);
            }
        }

        setHealth(getMaxHealth());
    }

    /** Sadece tur kimligini yazar, ozelliklere dokunmaz. */
    protected void setVariantIdOnly(Variant variant) {
        this.entityData.set(VARIANT, (byte) variant.ordinal());
    }

    private void setAttr(net.minecraft.world.entity.ai.attributes.Attribute attribute, double value) {
        var instance = getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PatternAttackGoal(this));
        this.goalSelector.addGoal(2, new FollowOwnerGoal(this));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // mustSee = false: gorus hatti olmasa da kilitlenirler, arkalarindaki
        // veya kose donmus dusmani da hedef alirlar
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, LivingEntity.class, 5, true, false, this::isValidTarget));
    }

    /**
     * SAHIBI DISINDA HER CANLI hedeftir.
     *
     * Tek istisna sahibin kendisi ve ayni sahibin diger askerleri; onlarin
     * disinda ayirt etmiyoruz — asker Sandman'in yanindaki her seye saldirir.
     */
    boolean isValidTarget(LivingEntity candidate) {
        if (candidate == null || !candidate.isAlive()) return false;
        if (candidate == this) return false;
        if (ownerId != null && candidate.getUUID().equals(ownerId)) return false;

        // Ayni sahibin askerleri birbirine vurmaz
        if (candidate instanceof SandSoldierEntity other) {
            return ownerId == null || !ownerId.equals(other.ownerId);
        }

        return true;
    }

    public void setOwner(UUID owner) {
        this.ownerId = owner;
    }

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    @Nullable
    public Player getOwner() {
        return ownerId == null ? null : level().getPlayerByUUID(ownerId);
    }

    public void setLifetime(int ticks) {
        this.lifetime = ticks;
    }

    // ------------------------------------------------------------------
    // Durum
    // ------------------------------------------------------------------

    public float getSpawnProgress() {
        return this.entityData.get(SPAWN_PROGRESS);
    }

    public float getCrumbleProgress() {
        return this.entityData.get(CRUMBLE_PROGRESS);
    }

    public boolean isForming() {
        return getSpawnProgress() < 1.0f;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            spawnAmbientSand();
            return;
        }

        if (crumbling) {
            tickCrumble();
            return;
        }

        if (isForming()) {
            tickForming();
            return;
        }

        if (--lifetime <= 0 || getOwner() == null) {
            beginCrumble();
        }
    }

    /**
     * Olusma sirasinda asker hareketsiz ve hedefsiz kalir.
     *
     * NoAI kullanmiyoruz cunku o yercekimini de kesiyor ve asker havada
     * asili kaliyor; bunun yerine her tick navigasyon durduruluyor ve yatay
     * hiz sifirlaniyor. Boylece yere basmaya devam ediyor.
     */
    private void tickForming() {
        float progress = Math.min(1.0f, getSpawnProgress() + 1.0f / SPAWN_TICKS);
        this.entityData.set(SPAWN_PROGRESS, progress);

        setTarget(null);
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);

        if (level() instanceof ServerLevel server && tickCount % 3 == 0) {
            server.sendParticles(sandParticle(),
                    getX(), getY() + 0.1, getZ(),
                    5, 0.28, 0.15, 0.28, 0.04);
        }

        if (progress >= 1.0f) {
            level().playSound(null, blockPosition(),
                    SoundEvents.SAND_PLACE, SoundSource.HOSTILE, 1.0f, 0.8f);
            onFormed();
        }
    }

    /** Olusma bittigi anda bir kez cagrilir. Dev asker burada yere cakiliyor. */
    protected void onFormed() {}

    private void tickCrumble() {
        float progress = Math.min(1.0f, getCrumbleProgress() + 1.0f / CRUMBLE_TICKS);
        this.entityData.set(CRUMBLE_PROGRESS, progress);

        setTarget(null);
        getNavigation().stop();

        if (level() instanceof ServerLevel server) {
            server.sendParticles(sandParticle(),
                    getX(), getY() + 0.9, getZ(),
                    6, 0.3, 0.5, 0.3, 0.06);
        }

        if (progress >= 1.0f) discard();
    }

    private void beginCrumble() {
        if (crumbling) return;
        crumbling = true;

        level().playSound(null, blockPosition(),
                SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1.0f, 0.7f);
    }

    /** Askerin uzerinden surekli dokulen ince kum. */
    private void spawnAmbientSand() {
        if (isForming() || random.nextFloat() > 0.25f) return;

        level().addParticle(sandParticle(),
                getX() + (random.nextDouble() - 0.5) * 0.6,
                getY() + random.nextDouble() * 1.7,
                getZ() + (random.nextDouble() - 0.5) * 0.6,
                0, -0.02, 0);
    }

    private static BlockParticleOption sandParticle() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    }

    // ------------------------------------------------------------------
    // Dovus / dayaniklilik
    // ------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Olusurken ve dagilirken vurulamaz
        if (isForming() || crumbling) return false;

        // Sahibi kendi askerini yanlislikla oldurmesin
        if (source.getEntity() instanceof Player player
                && ownerId != null && player.getUUID().equals(ownerId)) {
            return false;
        }

        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        // Olurken ceset birakmaz, dagilir
        beginCrumble();
        setHealth(1.0f);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    /** Kum askeri bogulmaz, yanmaz — o bir kum yigini. */
    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    // ------------------------------------------------------------------
    // Kayit / ag
    // ------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("SandOwner", ownerId);
        tag.putInt("SandLifetime", lifetime);
        tag.putFloat("SandSpawn", getSpawnProgress());
        tag.putByte("SandVariant", (byte) getVariant().ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("SandOwner")) ownerId = tag.getUUID("SandOwner");
        lifetime = tag.getInt("SandLifetime");
        this.entityData.set(SPAWN_PROGRESS, tag.getFloat("SandSpawn"));
        this.entityData.set(VARIANT, tag.getByte("SandVariant"));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    public static SandSoldierEntity create(ServerLevel level, Player owner, double x, double y, double z) {
        SandSoldierEntity soldier = ModEntities.SAND_SOLDIER.get().create(level);
        if (soldier == null) return null;

        soldier.moveTo(x, y, z, owner.getYRot(), 0f);
        soldier.setOwner(owner.getUUID());
        return soldier;
    }

    // ------------------------------------------------------------------

    /**
     * Ture gore degisen saldiri deseni.
     *
     * Vanilla MeleeAttackGoal'un kendi bekleme sayaci sabit 20 tick ve alani
     * private; o yuzden kendi sayacimizi tutuyoruz ve vurusu tamamen burada
     * uyguluyoruz. Yol bulma kismi vanilla'dan miras kaliyor.
     *
     *   BLADE   : iki hizli savurma (ikincisi birkac tick sonra), kisa bekleme
     *   BREAKER : tek agir darbe + hedefin cevresine savurma, uzun bekleme
     */
    private static class PatternAttackGoal extends MeleeAttackGoal {

        private static final int BLADE_COOLDOWN = 16;
        private static final int BREAKER_COOLDOWN = 34;
        /** BLADE'in ikinci vurusunun ilkinden kac tick sonra gelecegi. */
        private static final int SECOND_STRIKE_DELAY = 5;

        private final SandSoldierEntity soldier;
        private int cooldown;
        private int pendingSecondStrike;

        PatternAttackGoal(SandSoldierEntity soldier) {
            super(soldier, 1.15, true);
            this.soldier = soldier;
        }

        @Override
        public boolean canUse() {
            return !soldier.isForming() && super.canUse();
        }

        @Override
        public void tick() {
            super.tick();

            if (pendingSecondStrike > 0 && --pendingSecondStrike == 0) {
                LivingEntity target = soldier.getTarget();
                if (target != null && soldier.distanceToSqr(target) <= getAttackReachSqr(target)) {
                    soldier.swing(InteractionHand.OFF_HAND);
                    soldier.doHurtTarget(target);
                }
            }
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target, double distSqr) {
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            if (distSqr > getAttackReachSqr(target)) return;

            soldier.swing(InteractionHand.MAIN_HAND);

            if (soldier.getVariant() == Variant.BLADE) {
                cooldown = BLADE_COOLDOWN;
                soldier.doHurtTarget(target);
                pendingSecondStrike = SECOND_STRIKE_DELAY;
            } else {
                cooldown = BREAKER_COOLDOWN;
                soldier.doHurtTarget(target);
                slam(target);
            }
        }

        /** BREAKER darbesi hedefin cevresindekileri de savurur. */
        private void slam(LivingEntity target) {
            AABB area = new AABB(target.position(), target.position()).inflate(2.2);

            for (LivingEntity nearby : soldier.level().getEntitiesOfClass(
                    LivingEntity.class, area,
                    e -> e != soldier && e.isAlive() && soldier.isValidTarget(e))) {

                Vec3 push = nearby.position().subtract(soldier.position());
                Vec3 flat = new Vec3(push.x, 0, push.z);
                flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();

                nearby.setDeltaMovement(flat.x * 0.55, 0.42, flat.z * 0.55);
                nearby.hurtMarked = true;
            }

            if (soldier.level() instanceof ServerLevel server) {
                server.sendParticles(sandParticle(),
                        target.getX(), target.getY() + 0.1, target.getZ(),
                        16, 0.6, 0.1, 0.6, 0.12);
            }

            soldier.level().playSound(null, soldier.blockPosition(),
                    SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1.1f, 0.55f);
        }
    }

    /** Sahibi cok uzaklasirsa yanina doner. */
    private static class FollowOwnerGoal extends Goal {

        private final SandSoldierEntity soldier;
        private Player owner;
        private int recalcCooldown;

        FollowOwnerGoal(SandSoldierEntity soldier) {
            this.soldier = soldier;
            setFlags(java.util.EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (soldier.isForming() || soldier.getTarget() != null) return false;

            Player candidate = soldier.getOwner();
            if (candidate == null) return false;
            if (soldier.distanceToSqr(candidate) < FOLLOW_START * FOLLOW_START) return false;

            this.owner = candidate;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null
                    && soldier.getTarget() == null
                    && soldier.distanceToSqr(owner) > FOLLOW_STOP * FOLLOW_STOP;
        }

        @Override
        public void stop() {
            owner = null;
            soldier.getNavigation().stop();
        }

        @Override
        public void tick() {
            soldier.getLookControl().setLookAt(owner, 10f, soldier.getMaxHeadXRot());

            if (--recalcCooldown > 0) return;
            recalcCooldown = 10;

            soldier.getNavigation().moveTo(owner, 1.1);
        }
    }
}
