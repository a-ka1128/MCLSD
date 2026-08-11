package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Locale;

import net.minecraft.network.chat.Component;
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

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    public static final class Meter {
        public double raw;      // 감쇄 전 총합 (방어도·저항이 깎기 전)
        public double taken;    // 실제 총합
        public double maxHit;   // 실제 기준 한 방 최대
        public int hits;
        public int deaths;
        public String label = "보스";

        /**
         * 피해 «종류»별로 따로 센다. 총합만으로는 답할 수 없는 질문이 하나 있어서다 —
         * <b>「평균 18.7 인데 최대가 87 이면, 그 87 은 무엇인가?」</b>
         *
         * <p>이걸 모르면 전역 배수를 못 올린다. 87 이 예고 있는 큰 기술이면 원콤이 설계일 수
         * 있지만 평타에 섞인 것이면 그건 버그에 가깝고, 둘의 처방이 정반대다.
         * (별의 축복 때 얻은 것과 같은 교훈이다 — <b>파생 피해는 이름표를 달아 같은 판 안에서
         * 비교한다.</b> 따로 재서 빼면 판간 흔들림이 값을 통째로 삼킨다.)
         */
        public final Map<String, Bucket> byType = new HashMap<>();

        Bucket bucket(String key) { return byType.computeIfAbsent(key, k -> new Bucket()); }
    }

    public static final class Bucket {
        public double raw;
        public double taken;
        public double maxHit;
        public int hits;
    }

    private static final Map<Integer, Meter> METERS = new HashMap<>();

    /**
     * 이 개체의 피해를 세기 시작한다. {@link BossFightTracker}가 보스를 등록할 때 부른다.
     * 이름을 같이 받는 이유는 «플레이어가 죽는 순간»에도 보고를 띄우기 때문이다 — 그때는
     * 트래커를 거치지 않으므로 여기가 이름을 알아야 한다.
     */
    public static void watch(int id, String label) {
        Meter m = METERS.computeIfAbsent(id, k -> new Meter());
        m.label = label;
    }

    /** 지켜보는 개체가 아니면 null — 호출부가 조용히 빠진다. */
    private static Meter of(int id) { return METERS.get(id); }

    /** 그 보스의 기록을 꺼내고 지운다. 없으면 null. */
    public static Meter take(int bossId) { return METERS.remove(bossId); }

    /**
     * 때린 «놈»을 찾는다. 화살·마법은 {@code getDirectEntity} 가 투사체라 그걸 세면
     * 보스마다 id 가 흩어진다 — 주인을 본다.
     */
    /**
     * 피해 종류의 등록 id. 없으면 메시지 id 로 떨어진다 —
     * 모드 damage type 중에는 레지스트리에 안 올라온 즉석 소스가 있다.
     */
    private static String typeKey(DamageSource src) {
        return src.typeHolder().unwrapKey()
            .map(k -> k.location().toString())
            .orElseGet(src::getMsgId);
    }

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
        m.bucket(typeKey(event.getSource())).raw += event.getAmount();
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
        Bucket b = m.bucket(typeKey(event.getSource()));
        b.taken += a;
        b.hits++;
        if (a > b.maxHit) b.maxHit = a;
    }

    // ── 죽은 «순간»에 보고한다 ──
    // 초판은 보고가 BossFightTracker.report 한 곳뿐이었고, 그건 **보스가 죽어야** 돈다.
    // 그런데 이 계기의 용도는 「보스가 얼마나 아픈가」다 — 아픈지 재려면 맞아야 하고,
    // 맞다 보면 **플레이어가 먼저 죽는다.** 실제로 첫 시험에서 그렇게 됐고, 한 대도
    // 못 건진 채 판이 끝났다. 알고 싶은 바로 그 순간에 아무것도 안 나오는 계기였다.
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Meter m = of(attackerId(event.getSource()));
        if (m == null) return;
        m.deaths++;
        log(m, "사망");
        if (p.hasPermissions(2)) print(p, m, false);
    }

    /**
     * 로그에 한 줄. <b>화면 줄만으로는 부족하다</b> — 조율은 대개 판이 끝난 «뒤에»
     * 로그를 뒤져서 하는데(`docs/BLESSING.md` 의 교훈), 죽는 순간의 채팅은 리스폰과
     * 사망 메시지에 곧바로 밀린다. 실제로 그래서 한 번 값을 놓쳤다.
     */
    public static void log(Meter m, String when) {
        if (m == null || m.hits <= 0) return;
        LOG.info("[전투 피해] {} ({}) · {}대 · 감쇄전={} 실제={} · 한대평균={} 최대={} · 사망={}",
            m.label, when, m.hits,
            String.format(Locale.ROOT, "%.0f", m.raw),
            String.format(Locale.ROOT, "%.0f", m.taken),
            String.format(Locale.ROOT, "%.1f", m.taken / m.hits),
            String.format(Locale.ROOT, "%.1f", m.maxHit), m.deaths);
        // 종류별도 로그에. 화면 줄이 길어 스크롤에 밀리는 판일수록 여기가 유일한 기록이 된다.
        List<Map.Entry<String, Bucket>> rows = new ArrayList<>(m.byType.entrySet());
        rows.sort(Comparator.comparingDouble((Map.Entry<String, Bucket> e) -> -e.getValue().taken));
        for (Map.Entry<String, Bucket> e : rows) {
            Bucket b = e.getValue();
            if (b.hits <= 0) continue;
            LOG.info("[전투 피해]   {} · {}대 · 실제={} 평균={} 최대={}",
                e.getKey(), b.hits,
                String.format(Locale.ROOT, "%.0f", b.taken),
                String.format(Locale.ROOT, "%.1f", b.taken / b.hits),
                String.format(Locale.ROOT, "%.1f", b.maxHit));
        }
    }

    /**
     * 지금까지의 기록을 화면에 뿌린다. 보스 처치({@link BossFightTracker#report})와
     * 플레이어 사망, 그리고 {@code /lsgimmick dmg} 가 같은 것을 쓴다 —
     * <b>같은 값을 세 곳이 각자 포맷하면 셋이 조금씩 다른 말을 하게 된다.</b>
     *
     * @param done 판이 끝나서 내는 보고인가(true) — 중간 조회면 그렇게 표시한다
     */
    public static void print(ServerPlayer p, Meter m, boolean done) {
        if (m == null || m.hits <= 0) {
            p.sendSystemMessage(Component.literal("§8  받은 피해 기록 없음 — 아직 한 대도 안 맞았다."));
            return;
        }
        double avgRaw = m.raw / m.hits;
        double avgTaken = m.taken / m.hits;
        double cut = avgRaw - avgTaken;   // 방어도·저항이 «한 대에서 빼는 몫»

        p.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
            "§6▣ %s §7받은 피해%s — §f%d대 §7· 감쇄 전 §f%.0f §7→ 실제 §c%.0f §8(한 대 평균 %.1f · 최대 %.1f)",
            m.label, done ? "" : " §8(진행 중)", m.hits, m.raw, m.taken, avgTaken, m.maxHit)));
        if (m.deaths > 0) {
            p.sendSystemMessage(Component.literal(
                String.format(Locale.ROOT, "§c  사망 %d회", m.deaths)));
        }
        double hp = p.getMaxHealth();
        if (avgTaken > 0.01) {
            p.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§7  내 최대 체력 §f%.0f §7→ §e%.1f§7대에 죽는다 §8(한 대 = 최대 체력의 %.0f%%)",
                hp, hp / avgTaken, avgTaken / hp * 100)));
        }

        // ── 종류별 ──
        // 「87 은 무엇인가」에 답하는 자리다. 실제 피해가 큰 순으로, 흔적만 남긴 종류까지 전부.
        // 상위 몇 개로 자르지 않는다 — 잘라 놓고 「나머지」로 뭉치면 그 안에 답이 숨는다.
        if (m.byType.size() > 1) {
            List<Map.Entry<String, Bucket>> rows = new ArrayList<>(m.byType.entrySet());
            rows.sort(Comparator.comparingDouble((Map.Entry<String, Bucket> e) -> -e.getValue().taken));
            p.sendSystemMessage(Component.literal("§7  종류별 §8(실제 피해 순)"));
            for (int i = 0; i < rows.size(); i++) {
                Bucket b = rows.get(i).getValue();
                if (b.hits <= 0) continue;
                p.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§8   %s §f%-28s §7%3d대 · 실제 §c%.0f §8(평균 %.1f · 최대 §f%.1f§8) · %.0f%%",
                    i == rows.size() - 1 ? "└" : "├", rows.get(i).getKey(), b.hits, b.taken,
                    b.taken / b.hits, b.maxHit, b.taken / m.taken * 100)));
            }
        }

        // ── 여기가 이 계기를 만든 이유다 ──
        // 방어도가 «빼는 몫»처럼 굴어서 dmg% 배수와 실제 피해 배수가 다르다.
        // 그 어긋남은 계산이 아니라 이 판에서 실제로 빠진 양으로만 알 수 있다.
        if (cut > 0.5 && avgTaken > 0.01) {
            p.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§8  방어도가 빼는 몫 §7%.1f§8/대 — §cdmg%% 를 올리면 실제 피해는 그보다 크게 는다", cut)));
            StringBuilder sb = new StringBuilder("§8  ");
            for (int pct : new int[]{120, 130, 140, 150}) {
                double t = avgRaw * (pct / 100.0) - cut;
                sb.append(String.format(Locale.ROOT, "§7%d%%§8→§f×%.2f  ", pct, t / avgTaken));
            }
            p.sendSystemMessage(Component.literal(sb.toString().trim()));
        }
    }

    /** 가장 많이 때린 개체의 기록. {@code /lsgimmick dmg} 가 쓴다 — 보통 지금 싸우는 보스다. */
    public static Meter busiest() {
        Meter best = null;
        for (Meter m : METERS.values()) if (best == null || m.hits > best.hits) best = m;
        return best;
    }

    /** 판이 끝나면 반드시 부른다 — 죽든, 청크 언로드로 사라지든. */
    public static void forget(int id) { METERS.remove(id); }

    public static int tracked() { return METERS.size(); }
}
