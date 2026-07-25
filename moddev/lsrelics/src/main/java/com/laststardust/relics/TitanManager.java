package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.joml.Vector3f;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 타이탄 강림(도끼 궁극) — 일정 시간 실제로 거대해진다.
// 1.21.1의 generic.scale 속성을 써서 히트박스까지 같이 커지고,
// 사거리·공격력·넉백·넉백저항을 함께 올려 "덩치값"이 실제로 나오게 한다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class TitanManager {
    private TitanManager() {}

    private static final ResourceLocation ID =
        ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "titan_ascension");

    // 속성, 값, 연산 방식
    private static final Object[][] BUFFS = {
        // ADD_MULTIPLIED_BASE 0.6 → 1 + 0.6 = 1.6배. (1.6을 넣으면 2.6배가 돼 실내에서 끼임)
        {Attributes.SCALE,                    0.6,  AttributeModifier.Operation.ADD_MULTIPLIED_BASE},
        {Attributes.ATTACK_DAMAGE,            6.0,  AttributeModifier.Operation.ADD_VALUE},
        {Attributes.ENTITY_INTERACTION_RANGE, 2.5,  AttributeModifier.Operation.ADD_VALUE},
        {Attributes.ATTACK_KNOCKBACK,         2.0,  AttributeModifier.Operation.ADD_VALUE},
        {Attributes.KNOCKBACK_RESISTANCE,     1.0,  AttributeModifier.Operation.ADD_VALUE},
        {Attributes.STEP_HEIGHT,              1.0,  AttributeModifier.Operation.ADD_VALUE},
        {Attributes.MAX_HEALTH,               10.0, AttributeModifier.Operation.ADD_VALUE},
        {Attributes.SAFE_FALL_DISTANCE,       6.0,  AttributeModifier.Operation.ADD_VALUE},
    };

    private static final List<Titan> ACTIVE = new ArrayList<>();

    private static final class Titan {
        final ServerPlayer player;
        int ticksLeft;
        Titan(ServerPlayer player, int ticksLeft) { this.player = player; this.ticksLeft = ticksLeft; }
    }

    @SuppressWarnings("unchecked")
    private static void apply(ServerPlayer p) {
        for (Object[] b : BUFFS) {
            AttributeInstance inst = p.getAttribute((Holder<Attribute>) b[0]);
            if (inst == null) continue;
            inst.removeModifier(ID); // 재시전 시 중첩 방지
            inst.addTransientModifier(new AttributeModifier(ID, (Double) b[1],
                (AttributeModifier.Operation) b[2]));
        }
        p.setHealth(p.getHealth() + 10.0f); // 늘어난 최대 체력만큼 바로 채워줌
        // 히트박스가 1.6배로 커져 훨씬 잘 맞으므로 보호막으로 그만큼 보상한다.
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 260, 3, false, true)); // 흡수 IV = 8칸, 13초
    }

    @SuppressWarnings("unchecked")
    private static void remove(ServerPlayer p) {
        for (Object[] b : BUFFS) {
            AttributeInstance inst = p.getAttribute((Holder<Attribute>) b[0]);
            if (inst != null) inst.removeModifier(ID);
        }
        // 최대 체력이 줄면서 초과분이 잘리는 건 정상 처리됨
    }

    public static void start(ServerLevel level, ServerPlayer player, int ticks) {
        ACTIVE.removeIf(t -> t.player == player);
        apply(player);
        ACTIVE.add(new Titan(player, ticks));
        burst(level, player, true);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Titan> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Titan t = it.next();
            ServerPlayer p = t.player;
            if (p.isRemoved() || !p.isAlive()) { remove(p); it.remove(); continue; }
            if (--t.ticksLeft <= 0) {
                remove(p);
                it.remove();
                if (p.level() instanceof ServerLevel sl) burst(sl, p, false);
                continue;
            }
            if (!(p.level() instanceof ServerLevel lv)) continue;
            // 지속 연출 — 발밑에서 피어오르는 공허 기운
            lv.sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 0.1, p.getZ(),
                2, 0.6, 0.1, 0.6, 0.01);
            DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0f, 0.9f, 0.4f), 1.6f);
            lv.sendParticles(gold, p.getX(), p.getY() + 1.4, p.getZ(), 2, 0.7, 1.0, 0.7, 0.0);
            if (t.ticksLeft % 20 == 0) {
                lv.playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 0.7f);
            }
            // 걸을 때마다 땅이 울리는 연출
            if (t.ticksLeft % 10 == 0 && p.onGround() && p.getDeltaMovement().horizontalDistanceSqr() > 0.005) {
                lv.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.05, p.getZ(), 6, 0.5, 0.02, 0.5, 0.02);
                lv.playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 0.7f, 0.6f);
            }
        }
    }

    // 변신·해제 순간 연출
    private static void burst(ServerLevel lv, ServerPlayer p, boolean on) {
        double x = p.getX(), y = p.getY() + 1.0, z = p.getZ();
        lv.sendParticles(ParticleTypes.FLASH, x, y, z, 2, 0.2, 0.2, 0.2, 0);
        lv.sendParticles(ParticleTypes.SCULK_CHARGE_POP, x, y, z, 60, 1.0, 1.2, 1.0, 0.1);
        lv.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 80, 0.8, 1.0, 0.8, 0.4);
        DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0f, 0.9f, 0.45f), 2.2f);
        lv.sendParticles(gold, x, y, z, 90, 1.0, 1.2, 1.0, 0.0);
        for (int i = 0; i < 40; i++) {
            double a = Math.PI * 2 * i / 40.0;
            double dx = Math.cos(a), dz = Math.sin(a);
            lv.sendParticles(ParticleTypes.END_ROD, x + dx * 3.0, p.getY() + 0.1, z + dz * 3.0,
                0, dx * 0.7, 0.05, dz * 0.7, 0.7);
        }
        if (on) {
            lv.playSound(null, x, y, z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6f, 0.5f);
            lv.playSound(null, x, y, z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2f, 0.5f);
            SoundScheduler.at(lv, p.position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.6f, 0.6f, 3);
            SoundScheduler.at(lv, p.position(), SoundEvents.TRIDENT_THUNDER.value(), 1.8f, 0.6f, 7);
            SoundScheduler.at(lv, p.position(), SoundEvents.BEACON_POWER_SELECT, 1.6f, 0.5f, 13);
        } else {
            lv.playSound(null, x, y, z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2f, 0.7f);
            lv.playSound(null, x, y, z, SoundEvents.SCULK_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
        }
    }
}
