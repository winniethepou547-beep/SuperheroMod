package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.network.packet.IcemanFxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * Where every Iceman effect from the server goes (IcemanFxPacket): the frost on bodies and screens (FrostFx), the brush
 * and its sculptures (IcemanBrushFx), the slides (IcemanSlideFx), the weapons (IcemanWeaponFx), the shell and shattered
 * ground (IcemanShellFx, IcemanGroundFx); a plain break of ice (FX_SHATTER) is the shared break (IceParticles.shatter).
 */
public final class IcemanFx {
    private IcemanFx() {}

    public static void receive(IcemanFxPacket p) {
        if (Minecraft.getInstance().level == null) return;
        switch (p.kind()) {
            case FX_FROST, FX_DEEP_FREEZE, FX_DEEP_BREAK, FX_LENS, FX_BRUSH_FROST -> FrostFx.receive(p);
            case FX_SCULPT_POINT, FX_SCULPT_END, FX_SCULPT_BREAK -> IcemanBrushFx.receive(p);
            case FX_SLIDE_HIT, FX_DASH, FX_DASH_HIT -> IcemanSlideFx.receive(p);
            case FX_HIT, FX_FORM, FX_SLAM, FX_SPEAR, FX_SPEAR_STUCK, FX_SPEAR_SPIKES, FX_SPIN, FX_SWORD_BREAK, FX_SWORD_PLANT, FX_WEAPON_BREAK -> IcemanWeaponFx.receive(p);
            case FX_SHELL_HIT, FX_SHELL_BREAK, FX_SHELL_BURST -> IcemanShellFx.receive(p);
            case FX_GROUND, FX_GROUND_ERUPT -> IcemanGroundFx.receive(p);
            case FX_SHATTER -> shatter(p);
            case FX_AUTO_SLIDE -> IcemanClient.autopilot(p.id());
            default -> {}
        }
    }
    /** A plain break (a weapon let go, a projectile on a sculpture, a stuck spear's end): id says what broke. */
    private static void shatter(IcemanFxPacket p) {
        IceMesh.Mat mat = switch (p.id()) { case 1 -> IceMesh.MILKY; case 3 -> IceMesh.GLACIER; default -> IceMesh.CLEAR; };
        Vec3 at = p.pos();
        IceParticles.shatter(at, p.dir(), Math.max(.2f, p.power()), mat);
    }
}
