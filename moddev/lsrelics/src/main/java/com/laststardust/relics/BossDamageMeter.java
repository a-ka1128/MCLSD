package com.laststardust.relics;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * <h2>보스가 «얼마나 아픈가»를 재는 계기</h2>
 *
 * <p>── 왜 필요했나 (2026-08-11) ──
 * {@link BossFightTracker} 의 전투 기록계는 <b>보스 체력만</b> 잰다 — 몇 초에 죽었나,
 * 그래서 체력을 얼마로 하면 60초가 되나. <b>공격력 쪽에는 계기가 통째로 없었다.</b>
 * 그래서 각성 체력을 40~60칸으로 올린 뒤 「보스를 얼마나 세게 해야 하나」를 물었을 때
 * 답할 근거가 아무것도 없었다.
 *
 * <p>── 왜 «체력 배수 = 공격력 배수» 가 아닌가 ──
 * ApothicAttributes 아래에서 방어도는 <b>비율이 아니라 «한 대에서 빼는 몫»</b>처럼 군다
 * (방어도 10 이면 한 방 크기와 무관하게 17~19 쯤 사라진다 — `docs/BLESSING.md`).
 * 그래서 공격력을 ×1.4 하면 <b>실제로 들어오는 피해는 ×1.4 가 아니다.</b>
 *
 * <pre>
 *   감쇄 전 50, 방어도가 20 을 뺀다  →  실제 30
 *   공격력 ×1.4  →  감쇄 전 70, 여전히 20 을 뺀다  →  실제 50  =  ×1.67
 * </pre>
 *
 * 빼는 몫이 그대로라 <b>남는 쪽만 곱해진다.</b> 이 어긋남은 원본이 작을수록 커진다 —
 * 눈으로 짐작하면 반드시 과하게 올리게 된다. 그래서 그 «빼는 몫»을 실측해서 찍는다.
 *
 * <p>기록은 <b>때린 놈의 엔티티 id</b>로 모으되, {@link #watch}로 <b>지켜보라고 한 개체만</b>
 * 센다. 초판은 아무 몹이나 다 받았는데 — 이 클래스가 보스 목록을 알 필요가 없다는 게 그 이유였다 —
 * 그러면 <b>플레이어를 때린 모든 몹이 맵에 남아 서버 수명 내내 자란다.</b> 좀비 한 마리가
 * 한 대 치고 사라져도 칸 하나가 영영 남는 셈이다.
 * 등록은 {@link BossFightTracker}가 한다 — 거기가 보스 목록을 아는 유일한 자리다.
 */
@EventBusSubscriber(modid = LSRelics.MODID)
public final class BossDamageMeter {
    private BossDamageMeter() {}

    public static final class Meter {
        public double raw;      // 감쇄 전 총합 (방어도·저항이 깎기 전)
        public double taken;    // 실제 총합
        public double maxHit;   // 실제 기준 한 방 최대
        public int hits;
        public int deaths;
    }

    private static final Map<Integer, Meter> METERS = new HashMap<>();

    /** 이 개체의 피해를 세기 시작한다. {@link BossFightTracker}가 보스를 등록할 때 부른다. */
    public static void watch(int id) { METERS.computeIfAbsent(id, k -> new Meter()); }

    /** 지켜보는 개체가 아니면 null — 호출부가 조용히 빠진다. */
    private static Meter of(int id) { return METERS.get(id); }

    /** 그 보스의 기록을 꺼내고 지운다. 없으면 null. */
    public static Meter take(int bossId) { return METERS.remove(bossId); }

    /**
     * 때린 «놈»을 찾는다. 화살·마법은 {@code getDirectEntity} 가 투사체라 그걸 세면
     * 보스마다 id 가 흩어진다 — 주인을 본다.
     */
    private static int attackerId(DamageSource src) {
        Entity e = src.getEntity();
        if (!(e instanceof LivingEntity) || e instanceof Player) return -1;
        return e.getId();
    }

    // ⚠️ 두 우선순위로 같은 이벤트를 두 번 받는다. HIGHEST 는 아무도 손대기 «전»,
    //    LOWEST 는 전부 손댄 «뒤». 그 차이가 곧 방어도·저항·축복이 먹은 몫이다.
    //    (`DummyManager` 의 onPlayerIncomingFirst/Last 와 같은 수법이다.)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFirst(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        Meter m = of(attackerId(event.getSource()));
        if (m == null) return;
        m.raw += event.getAmount();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLast(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        Meter m = of(attackerId(event.getSource()));
        if (m == null) return;
        float a = event.getAmount();
        m.taken += a;
        m.hits++;
        if (a > m.maxHit) m.maxHit = a;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        Meter m = of(attackerId(event.getSource()));
        if (m == null) return;
        m.deaths++;
    }

    /** 판이 끝나면 반드시 부른다 — 죽든, 청크 언로드로 사라지든. */
    public static void forget(int id) { METERS.remove(id); }

    public static int tracked() { return METERS.size(); }
}
