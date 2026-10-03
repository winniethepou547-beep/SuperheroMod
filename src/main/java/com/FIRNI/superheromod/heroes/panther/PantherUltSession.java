package com.FIRNI.superheromod.heroes.panther;

import com.FIRNI.superheromod.core.film.FilmSessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static com.FIRNI.superheromod.heroes.panther.PantherAction.*;

/**
 * THE FINAL PURSUIT (X), what happens in the world while the film plays for him alone: he is held where he
 * stood (and cannot be hurt, see PantherController.attacked); when the film's release goes off, the real kinetic
 * blast goes off round him (those near are thrown outward); when it ends he is back where he was.
 */
public final class PantherUltSession implements FilmSessions.Script {
    public static final String ID = "panther:final_pursuit";
    public static final PantherUltSession INSTANCE = new PantherUltSession();

    public static boolean start(ServerPlayer player) { return FilmSessions.startSolo(player, INSTANCE); }

    @Override public String film() { return ID; }
    @Override public int total() { return ULT_TOTAL; }
    @Override public int release() { return ULT_TOTAL; }
    @Override public boolean outlivesTarget() { return true; }
    @Override public void tick(ServerPlayer p, LivingEntity target, int age, Vec3 forward) {
        if (age == ULT_BOOM) PantherController.ultBlast(p);
    }
}
