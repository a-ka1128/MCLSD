package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 출혈(게볼그) — 바닐라에 출혈 효과가 없어 직접 구현한 지속 피해.
// 붉은 피 파티클과 함께 주기적으로 소량 피해를 준다. 같은 대상에 다시 걸면 시간만 갱신한다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class BleedManager {
    private BleedManager() {}

    private static final int INTERVAL = 10; // 0.5초마다

    // 이름표 — LsDamage.currentLabel() 로 읽힌다.
    //
    // ⚠️ 상수로 빼둔 이유: 별의 축복 「출혈」이 **자기 자신의 도트에 다시 걸리지 않도록**
    //    이 값을 비교한다(BlessingEffects.onDealt). 문자열로 흩어 두면 한쪽만 고쳤을 때
    //    출혈이 스스로를 갱신해 **영구 지속**이 된다 — 오류도 안 나고 조용히 그렇게 된다.
    public static final String LABEL = "출혈";

    private static final List<Bleed> ACTIVE = new ArrayList<>();

    private static final class Bleed {
        final ServerLevel level;
        final LivingEntity victim;
        ServerPlayer source;
        float perTick;       // INTERVAL 마다 주는 피해
        int ticksLeft;
        Bleed(ServerLevel level, LivingEntity victim, ServerPlayer source, float perTick, int ticks) {
            this.level = level; this.victim = victim; this.source = source;
            this.perTick = perTick; this.ticksLeft = ticks;
        }
    }

    // perSecond: 초당 총 출혈 피해. durationTicks 동안 지속.
    //
    // ── 다시 걸면 «남은 몫 + 새 몫» 을 합쳐 다시 나눈다 (2026-08-11) ──
    //
    // 예전에는 이미 걸려 있으면 **지속시간만 늘리고 새 피해량을 통째로 버렸다.**
    // 「중첩 없음」이 「나중 타격은 아무것도 안 한다」로 구현돼 있었던 셈이다.
    //
    // 스킬로 거는 둘(스틱스 급소 가르기·게볼그 투창)은 시전 간격이 지속시간보다 길어
    // 겹칠 일이 거의 없었고, 그래서 이 결함이 오래 안 보였다. 그런데 별의 축복 「출혈」은
    // **평타마다** 건다 — 초당 1.1회 × 4초 지속이라 상시로 겹치고, 그 결과 **첫 타격의
    // 세기가 60초 내내 고정되고 나머지가 전부 사라졌다.**
    //   2026-08-11 실측: 설계 「준 피해의 16%」 → 1,034 가 나와야 하는데 **114**(1/9).
    //
    // 남은 몫을 합쳐 다시 분배하면 «한 번에 하나»(중첩 없음)는 지키면서 피해는 안 버린다.
    public static void apply(ServerLevel level, LivingEntity victim, ServerPlayer source,
                             float perSecond, int durationTicks) {
        float incoming = perSecond * durationTicks / 20.0f;   // 이번에 넣으려던 총량
        for (Bleed b : ACTIVE) {
            if (b.victim != victim) continue;
            // ⚠️ **정수 나눗셈이어야 한다.** 지급은 `ticksLeft % INTERVAL == 0` 일 때만 일어나므로
            //    남은 횟수는 floor 다. 실수로 나누면(65/10.0 = 6.5, 실제 60·50·40·30·20·10 = 6회)
            //    매번 조금씩 부풀고 그게 누적돼 **설계 16% 가 실측 18.4% 로 나왔다**(2026-08-11).
            int remaining = b.ticksLeft / INTERVAL;
            float leftover = b.perTick * remaining;
            b.perTick = (leftover + incoming) * INTERVAL / (float) durationTicks;
            b.ticksLeft = durationTicks;
            b.source = source;   // 마지막에 건 사람이 위협도·처치를 가져간다
            return;
        }
        ACTIVE.add(new Bleed(level, victim, source, perSecond * INTERVAL / 20.0f, durationTicks));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Bleed> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Bleed b = it.next();
            if (b.victim.isRemoved() || !b.victim.isAlive()) { it.remove(); continue; }
            if (b.ticksLeft % INTERVAL == 0) {
                com.laststardust.relics.LsDamage.hit(b.victim, com.laststardust.relics.item.RelicSkills.relicSource(b.level, b.source), b.perTick, LABEL);
                Vec3 c = b.victim.position();
                b.level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    c.x, c.y + b.victim.getBbHeight() * 0.5, c.z, 3, 0.2, 0.2, 0.2, 0.0);
            }
            if (--b.ticksLeft <= 0) it.remove();
        }
    }
}
