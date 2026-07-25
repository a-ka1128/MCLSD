package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 망각의 안개(스틱스 추가) — 은신 + 재기습.
//   3초 은신(투명 + 주변 몹의 타겟 해제) → 은신이 풀린 뒤 첫 공격이 2배.
// 공격하면 은신이 즉시 풀리며 그 일격이 강화된다("숨었다 튀어나오는" 폭딜 셋업).
// 2배 판정은 RelicEventHandlers 가 consumeAmbush() 로 소비한다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class StealthManager {
    private StealthManager() {}

    private static final int AMBUSH_GRACE = 40; // 은신이 풀린 뒤에도 2초간은 기습 유효

    private static final List<Stealth> ACTIVE = new ArrayList<>();

    private static final class Stealth {
        final ServerPlayer player;
        int stealthLeft;   // 은신 지속
        int ambushLeft;    // 기습(2배) 유효 시간 — 은신 종료 후에도 잠깐 유지
        Stealth(ServerPlayer player, int stealth) {
            this.player = player; this.stealthLeft = stealth; this.ambushLeft = stealth + AMBUSH_GRACE;
        }
    }

    public static void start(ServerLevel level, ServerPlayer player, int ticks) {
        ACTIVE.removeIf(s -> s.player == player);
        ACTIVE.add(new Stealth(player, ticks));
        // 파티클(visible)은 끄고 HUD 아이콘(showIcon)만 켠다 — 소용돌이가 뜨면 은신이 무의미해지지만,
        // 아이콘조차 없으면 "걸린 건지" 알 수가 없다. 실제 은폐는 StealthRenderHandler 가 담당한다.
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true));
        dropAggro(level, player);
    }

    // 첫 공격 시 호출 — 기습이 살아 있으면 소비하고 은신을 해제한다.
    public static boolean consumeAmbush(ServerPlayer player) {
        for (Iterator<Stealth> it = ACTIVE.iterator(); it.hasNext(); ) {
            Stealth s = it.next();
            if (s.player != player) continue;
            if (s.ambushLeft <= 0) return false;
            player.removeEffect(MobEffects.INVISIBILITY); // 튀어나오며 모습을 드러낸다
            it.remove();
            return true;
        }
        return false;
    }

    private static void dropAggro(ServerLevel level, ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(24.0);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) {
            if (mob.getTarget() == player) mob.setTarget(null);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Stealth> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Stealth s = it.next();
            ServerPlayer p = s.player;
            if (p.isRemoved() || !p.isAlive()) { it.remove(); continue; }
            if (!(p.level() instanceof ServerLevel lv)) continue;

            if (s.stealthLeft > 0) {
                s.stealthLeft--;
                if (s.stealthLeft % 10 == 0) dropAggro(lv, p); // 계속 붙는 몹도 떼어낸다
                // 은신 중엔 파티클을 일절 내지 않는다.
                // 예전엔 발밑에 먹물을 옅게 뿌렸는데, 그게 매 틱 위치를 그대로 알려줘서
                // 은신의 의미를 스스로 없앴다. 사라지는 연출은 시전 순간의 연기 폭발로 충분하다.
            }
            if (--s.ambushLeft <= 0) { it.remove(); }
        }
    }
}
