package net.minecraft.client.resources.sounds;
public abstract class AbstractSoundInstance implements SoundInstance {
    protected float volume = 1, pitch = 1; protected double x, y, z; protected boolean looping; protected int delay;
    protected SoundInstance.Attenuation attenuation; protected boolean relative;
    protected AbstractSoundInstance(net.minecraft.sounds.SoundEvent e, net.minecraft.sounds.SoundSource s, net.minecraft.util.RandomSource r) {}
}
