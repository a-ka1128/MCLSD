package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.joml.Vector3f;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 무저갱(스틱스 궁극) — 8초간 학살 상태.
//   이동속도 +40% · 공격속도 +50% · 모든 공격 방어 무시
// 방어 무시는 RelicEventHandlers 가 이 상태를 조회해 처리한다(피해 계산에 개입해야 하므로).
@EventBusSubscriber(modid = LSRelics.MODID)
public final class AbyssManager {
    private AbyssManager() {}

    private static final ResourceLocation ID =
        ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "abyss");

    private static final Object[][] BUFFS = {
        {Attributes.MOVEMENT_SPEED, 0.40, AttributeModifier.Operation.ADD_MULTIPLIED_BASE},
        {Attributes.ATTACK_SPEED,   0.50, AttributeModifier.Operation.ADD_MULTIPLIED_BASE},
    };

    private static final List<Abyss> ACTIVE = new ArrayList<>();

    private static final class Abyss {
        final ServerPlayer player;
        int ticksLeft;
        Abyss(ServerPlayer player, int ticksLeft) { this.player = player; this.ticksLeft = ticksLeft; }
    }

    // 이 플레이어가 무저갱 상태인가 (방어 무시 판정용).
    public static boolean isActive(ServerPlayer player) {
        for (Abyss a : ACTIVE) if (a.player == player) return true;
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void apply(ServerPlayer p) {
        for (Object[] b : BUFFS) {
            AttributeInstance inst = p.getAttribute((Holder<Attribute>) b[0]);
            if (inst == null) continue;
            inst.removeModifier(ID);
            inst.addTransientModifier(new AttributeModifier(ID, (Double) b[1],
                (AttributeModifier.Operation) b[2]));
        }
    }

    @SuppressWarnings("unchecked")
    private static void remove(ServerPlayer p) {
        for (Object[] b : BUFFS) {
            AttributeInstance inst = p.getAttribute((Holder<Attribute>) b[0]);
            if (inst != null) inst.removeModifier(ID);
        }
    }

    public static void start(ServerLevel level, ServerPlayer player, int ticks) {
        ACTIVE.removeIf(a -> a.player == player);
        apply(player);
        ACTIVE.add(new Abyss(player, ticks));
        burst(level, player, true);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Abyss> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Abyss a = it.next();
            ServerPlayer p = a.player;
            if (p.isRemoved() || !p.isAlive()) { remove(p); it.remove(); continue; }
            if (--a.ticksLeft <= 0) {
                remove(p);
                it.remove();
                if (p.level() instanceof ServerLevel sl) burst(sl, p, false);
                continue;
            }
            if (!(p.level() instanceof ServerLevel lv)) continue;
            // 지속 연출 — 발밑에서 번지는 어둠
            lv.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 0.1, p.getZ(), 2, 0.4, 0.05, 0.4, 0.01);
            DustParticleOptions dark = new DustParticleOptions(new Vector3f(0.35f, 0.15f, 0.55f), 1.4f);
            lv.sendParticles(dark, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.4, 0.6, 0.4, 0.0);
            if (a.ticksLeft % 20 == 0) {
                lv.playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8f, 1.4f);
            }
        }
    }

    private static void burst(ServerLevel lv, ServerPlayer p, boolean on) {
        double x = p.getX(), y = p.getY() + 1.0, z = p.getZ();
        lv.sendParticles(ParticleTypes.FLASH, x, y, z, 2, 0.2, 0.2, 0.2, 0);
        lv.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 60, 0.8, 1.0, 0.8, 0.05);
        lv.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 70, 0.8, 1.0, 0.8, 0.4);
        DustParticleOptions dark = new DustParticleOptions(new Vector3f(0.4f, 0.2f, 0.6f), 2.0f);
        lv.sendParticles(dark, x, y, z, 80, 0.9, 1.1, 0.9, 0.0);
        if (on) {
            lv.playSound(null, x, y, z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.4f, 1.3f);
            lv.playSound(null, x, y, z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2f, 0.6f);
            SoundScheduler.at(lv, p.position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.4f, 1.1f, 4);
            SoundScheduler.at(lv, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.6f, 10);
        } else {
            lv.playSound(null, x, y, z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.2f);
        }
    }
}
