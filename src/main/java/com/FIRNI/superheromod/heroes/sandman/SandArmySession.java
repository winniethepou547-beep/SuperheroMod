package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.core.film.FilmSessions;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.ShockwavePacket;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * Sand Army, what happens in the world while the film plays. Still a rehearsal (H key or
 * /cinematic preview sand_army): no damage, no summons, no terrain edits; when the film hands
 * back, the world only sees the sand burst where the slam landed.
 */
public final class SandArmySession implements FilmSessions.Script {
    public static final String ID = "sandman:sand_army";
    public static final int SLAM = 330, RELEASE = 380, TOTAL = 400;
    public static final SandArmySession INSTANCE = new SandArmySession();

    public static boolean start(ServerPlayer player, LivingEntity target) { return FilmSessions.start(player, target, INSTANCE); }

    @Override public String film() { return ID; }
    @Override public int total() { return TOTAL; }
    @Override public int release() { return RELEASE; }
    @Override public void tick(ServerPlayer p, LivingEntity target, int age, Vec3 forward) {
        if (age != RELEASE) return;
        var level = p.serverLevel();
        Vec3 at = target.position();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.4f, .6f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, .6f, .8f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), at.x, at.y + .3, at.z, 120, 1.4, .3, 1.4, .25);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + .2, at.z, 20, 1.2, .1, 1.2, .05);
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new ShockwavePacket(at, 4, 14, .5f));
    }
}
