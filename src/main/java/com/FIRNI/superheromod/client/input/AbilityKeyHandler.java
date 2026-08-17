package com.FIRNI.superheromod.client.input;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.hud.ClientUltimateState;
import com.FIRNI.superheromod.client.render.ClientSandWallData;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.AbilityInputPacket;
import com.FIRNI.superheromod.network.packet.SandWallActionPacket;
import com.FIRNI.superheromod.network.packet.UltimateConfirmPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

public class AbilityKeyHandler {

    // NOT: Tus kimlikleri bilerek yeniden adlandirildi. Minecraft atamalari
    // options.txt'ye kaydediyor; eski kimlikler eski tuslara bagli kaliyordu.
    // Yeni kimlik = kayit yok = koddaki varsayilan uygulanir.

    public static final KeyMapping KEY_RAPID_FIRE = new KeyMapping(
            "key.superheromod.rapid_fire",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.superheromod"
    );

    public static final KeyMapping KEY_SKILL_F = new KeyMapping(
            "key.superheromod.optic_ascent",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            "key.categories.superheromod"
    );

    public static final KeyMapping KEY_RICOCHET = new KeyMapping(
            "key.superheromod.ricochet",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.superheromod"
    );

    public static final KeyMapping KEY_XRAY = new KeyMapping(
            "key.superheromod.xray",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "key.categories.superheromod"
    );

    public static final KeyMapping KEY_SKILL_G = new KeyMapping(
            "key.superheromod.skill_g",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.superheromod"
    );

    public static final KeyMapping KEY_ULTIMATE = new KeyMapping(
            "key.superheromod.ruby_rage",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Q,
            "key.categories.superheromod"
    );

    private static final Map<AbilitySlot, Boolean> previousState = new EnumMap<>(AbilitySlot.class);

    static {
        for (AbilitySlot slot : AbilitySlot.values()) {
            previousState.put(slot, false);
        }
    }

    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class Registration {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(KEY_RAPID_FIRE);
            event.register(KEY_SKILL_F);
            event.register(KEY_RICOCHET);
            event.register(KEY_XRAY);
            event.register(KEY_SKILL_G);
            event.register(KEY_ULTIMATE);
        }
    }

    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
    public static class Handler {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;

            // Kum duvari onizlemesi acikken LMB onaylar, RMB iptal eder
            if (ClientSandWallData.hasPreview()) {
                boolean lmb = mc.options.keyAttack.isDown();
                boolean rmb = mc.options.keyUse.isDown();

                if (lmb && !previousState.get(AbilitySlot.LMB)) {
                    ModNetworking.CHANNEL.sendToServer(
                            new SandWallActionPacket(SandWallActionPacket.CONFIRM));
                } else if (rmb && !previousState.get(AbilitySlot.RMB)) {
                    ModNetworking.CHANNEL.sendToServer(
                            new SandWallActionPacket(SandWallActionPacket.CANCEL));
                }

                previousState.put(AbilitySlot.LMB, lmb);
                previousState.put(AbilitySlot.RMB, rmb);

                // SHIFT onizleme sirasinda tekrar tetiklenmesin
                previousState.put(AbilitySlot.SHIFT, mc.options.keyShift.isDown());
                return;
            }

            // Ulti nisan modunda LMB/RMB normal yeteneklere degil, Confirm/Cancel'a gider
            if (ClientUltimateState.isAiming()) {
                boolean lmb = mc.options.keyAttack.isDown();
                boolean rmb = mc.options.keyUse.isDown();

                if (lmb && !previousState.get(AbilitySlot.LMB)) {
                    ModNetworking.CHANNEL.sendToServer(new UltimateConfirmPacket(true));
                } else if (rmb && !previousState.get(AbilitySlot.RMB)) {
                    ModNetworking.CHANNEL.sendToServer(new UltimateConfirmPacket(false));
                }

                previousState.put(AbilitySlot.LMB, lmb);
                previousState.put(AbilitySlot.RMB, rmb);
                return;
            }

            checkSlot(AbilitySlot.LMB, mc.options.keyAttack.isDown());
            checkSlot(AbilitySlot.RMB, mc.options.keyUse.isDown());
            // Itis SHIFT'te (egilme kaldirildi), sprint CTRL'de kaliyor
            checkSlot(AbilitySlot.SHIFT, mc.options.keyShift.isDown());
            checkSlot(AbilitySlot.SKILL_E, KEY_RAPID_FIRE.isDown());
            checkSlot(AbilitySlot.SKILL_F, KEY_SKILL_F.isDown());
            checkSlot(AbilitySlot.SKILL_C, KEY_RICOCHET.isDown());
            checkSlot(AbilitySlot.SKILL_X, KEY_XRAY.isDown());
            checkSlot(AbilitySlot.SKILL_G, KEY_SKILL_G.isDown());
            checkSlot(AbilitySlot.ULTIMATE, KEY_ULTIMATE.isDown());
        }

        private static void checkSlot(AbilitySlot slot, boolean currentlyDown) {
            boolean wasDown = previousState.get(slot);

            if (currentlyDown && !wasDown) {
                ModNetworking.CHANNEL.sendToServer(new AbilityInputPacket(slot, true));
            } else if (!currentlyDown && wasDown) {
                ModNetworking.CHANNEL.sendToServer(new AbilityInputPacket(slot, false));
            }

            previousState.put(slot, currentlyDown);
        }
    }
}
