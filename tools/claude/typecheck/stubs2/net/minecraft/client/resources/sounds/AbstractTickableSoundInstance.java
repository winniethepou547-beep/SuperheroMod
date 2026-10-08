package net.minecraft.client.resources.sounds;
public abstract class AbstractTickableSoundInstance extends AbstractSoundInstance implements TickableSoundInstance {
    private boolean stopped;
    protected AbstractTickableSoundInstance(net.minecraft.sounds.SoundEvent e, net.minecraft.sounds.SoundSource s, net.minecraft.util.RandomSource r) { super(e, s, r); }
    public boolean isStopped() { return stopped; }
    protected final void stop() { stopped = true; }
}
