package com.laststardust.relics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// ── 부활 규칙: 무적 창과 「별빛 쇠약」의 피해 감소 ──
//
// ls_revive.js 가 부활 시점·최대 체력 감소·안내문을 맡고, 여기는 - 전투 판정 - 만 맡는다.
// 규칙 자체는 docs/RESEARCH.md 「2. 전투·보스전」에서 왔다.
//
// ── 왜 자바로 옮겼나 ──
// 원래 쇠약의 공격력 감소는 attack_damage 속성 모디파이어였다. 그런데 그 속성을 보는 건
// - 근접 평타뿐 - 이다:
//   · 원거리 4종(시리우스·솔라리스·셀레스티아·파나케이아)은 투사체 피해를 직접 계산한다
//     (arrow.setBaseDamage / BulletManager / BoltManager) — 속성을 안 본다
//   · 8유물의 스킬 전부가 LsDamage 로 고정 수치를 넣는다 — 역시 안 본다
// 그래서 죽음의 대가를 근접 평타만 치르고 있었다. 파티 화력의 대부분이 면제였다.
// 피해가 나가는 지점에서 한 번에 깎으면 그 구멍이 사라진다.
//
// ── 두 장치를 같이 거는 이유 ──
//   · 부활 직후 무적 — 시체 위에 몹이 쌓여 있어도 일어설 틈은 준다.
//     - 공격하면 즉시 풀린다 - . 무적으로 딜을 넣는 순간 전멸 관리가 아니라 면죄부가 된다.
//   · 쇠약은 - 받는 피해가 아니라 주는 피해 - 를 깎는다. 받는 쪽을 깎으면 또 죽어서
//     악순환이 되고, 주는 쪽을 깎으면 "빨리 못 끝낸다"는 대가만 남는다.
//
// ※ 바닐라 무적 프레임과 무관하다. 그쪽은 LsDamage 가 다루고, 여기는 부활 직후 창만 본다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class ReviveRules {
    private ReviveRules() {}

    public static final int GRACE_TICKS = 100;    // 5초 무적
    public static final int FRAILTY_TICKS = 1200; // 60초 쇠약
    public static final float FRAILTY_MULT = 0.75f; // 주는 피해 -25%

    private static final Map<UUID, Long> GRACE = new HashMap<>();
    private static final Map<UUID, Long> FRAILTY = new HashMap<>();

    private static long now = 0;

    // 리스폰 직후 ls_revive.js 가 부른다 (LS.reviveRule). 안내문은 그쪽이 띄운다.
    public static void begin(ServerPlayer p) {
        GRACE.put(p.getUUID(), now + GRACE_TICKS);
        FRAILTY.put(p.getUUID(), now + FRAILTY_TICKS);
    }

    // 소생(파나케이아 궁)으로 되살아나면 대가를 면제한다 — 안 그러면 궁이 그냥 순간이동이 된다.
    public static void clear(ServerPlayer p) {
        GRACE.remove(p.getUUID());
        FRAILTY.remove(p.getUUID());
    }

    public static boolean hasGrace(Player p) {
        Long until = GRACE.get(p.getUUID());
        return until != null && now < until;
    }

    public static boolean isFrail(Player p) {
        Long until = FRAILTY.get(p.getUUID());
        return until != null && now < until;
    }

    // 무적을 스스로 깬다. "때렸으면 보호도 끝"이라는 규칙을 한 곳에서만 집행한다.
    private static void breakGrace(Player p) {
        if (GRACE.remove(p.getUUID()) != null) {
            p.displayClientMessage(Component.literal("§7✦ 무적이 풀렸다."), true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        now = event.getServer().getTickCount();
        // 만료된 것만 걷어낸다. 사람 수만큼이라 비용은 없다.
        GRACE.values().removeIf(t -> now >= t);
        FRAILTY.values().removeIf(t -> now >= t);
    }

    // 피해가 오갈 때 양쪽을 함께 본다 — 한 이벤트에서 처리해야 순서 문제가 안 생긴다.
    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;

        // 1) 부활 직후 5초 — 피해를 통째로 막는다
        if (victim instanceof ServerPlayer p && hasGrace(p)) {
            event.setCanceled(true);
            if (victim.level() instanceof ServerLevel sl) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.4, 0.6, 0.4, 0.02);
            }
            return;
        }

        // 2) 쇠약 중인 사람이 때렸으면 피해를 깎는다.
        //    투사체도 잡으려고 getEntity() 를 쓴다 — 화살·총알은 발사자가 여기로 들어온다.
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            breakGrace(attacker);   // 때린 순간 자기 무적은 끝
            if (isFrail(attacker)) event.setAmount(event.getAmount() * FRAILTY_MULT);
        }
    }
}
