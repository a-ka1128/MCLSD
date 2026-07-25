package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 지연 사운드 재생 — 궁극기 사운드를 한 프레임에 몰지 않고 시간차로 겹쳐
// "쿵… 우웅… 차앙" 같은 층을 만든다. (한 틱에 다 재생하면 서로 묻힘)
@EventBusSubscriber(modid = LSRelics.MODID)
public final class SoundScheduler {
    private SoundScheduler() {}

    private static final List<Cue> QUEUE = new ArrayList<>();

    private static final class Cue {
        final ServerLevel level;
        final Vec3 at;
        final SoundEvent sound;
        final float vol, pitch;
        int delay;
        Cue(ServerLevel level, Vec3 at, SoundEvent sound, float vol, float pitch, int delay) {
            this.level = level; this.at = at; this.sound = sound;
            this.vol = vol; this.pitch = pitch; this.delay = delay;
        }
    }

    public static void at(ServerLevel level, Vec3 pos, SoundEvent sound, float vol, float pitch, int delayTicks) {
        if (delayTicks <= 0) {
            level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, vol, pitch);
            return;
        }
        QUEUE.add(new Cue(level, pos, sound, vol, pitch, delayTicks));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (QUEUE.isEmpty()) return;
        Iterator<Cue> it = QUEUE.iterator();
        while (it.hasNext()) {
            Cue c = it.next();
            if (--c.delay > 0) continue;
            c.level.playSound(null, c.at.x, c.at.y, c.at.z, c.sound, SoundSource.PLAYERS, c.vol, c.pitch);
            it.remove();
        }
    }
}
