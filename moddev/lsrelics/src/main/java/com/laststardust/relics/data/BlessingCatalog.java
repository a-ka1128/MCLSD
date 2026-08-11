package com.laststardust.relics.data;

import java.util.List;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * 별의 축복 18종 정의 — 사양 확정본은 {@code docs/BLESSING.md}.
 *
 * <p>── 왜 KubeJS 가 아니라 여기인가 ──
 * {@code ls_config.js} 는 **서버 스크립트**라 클라이언트가 못 읽는다. 그런데 이 표는 클라도
 * 있어야 한다 — 제단의 스핀 연출이 후보 이름을 훑고, 툴팁이 「6~11 중 상위 20%」를 그린다.
 * 서버에만 두면 그 표를 패킷으로 매번 실어 보내야 하고, 그건 「같은 표가 두 곳에 있다」로 끝난다.
 * {@link TownCatalog} 를 KubeJS 에서 옮겨온 것과 같은 이유다 — 수치를 바꾸면 다시 빌드해야
 * 하지만, 그 대신 오타가 컴파일에서 걸리고 양쪽이 어긋날 수가 없다.
 *
 * <p>── 이 수치는 «책상 계산»이다 ──
 * 「최대 굴림에서 실질 DPS/EHP 몇 %인가」로 전부 환산해 무기 슬롯당 7~8% 선에 맞췄다.
 * {@code DECISIONS.md} 1-B 에서 유물 8종을 ±2.2% 까지 맞춘 건 전부 {@code /dummy} 실측이었다.
 * <b>구현 후 같은 방식으로 재보정할 것</b> — 특히 <b>연쇄·별빛 보호막</b>(조건부 진폭이 제일 크다).
 */
public final class BlessingCatalog {
    private BlessingCatalog() {}

    /** 축. 무기는 유물에, 방어는 상의·하의에 붙는다. 목록이 아예 달라 서로 섞이지 않는다. */
    public enum Axis { WEAPON, DEFENSE }

    /**
     * 슬롯 넷. <b>각성 한 단계마다 하나씩</b> 열린다 — 초판은 3·5성에만 줘서 2·4성이 빈손이었다.
     */
    public enum Slot {
        CHEST   (Axis.DEFENSE, 2),
        WEAPON_1(Axis.WEAPON,  3),
        LEGS    (Axis.DEFENSE, 4),
        WEAPON_2(Axis.WEAPON,  5);

        public final Axis axis;
        /** 이 슬롯이 열리는 각성 성급. */
        public final int star;

        Slot(Axis axis, int star) { this.axis = axis; this.star = star; }

        public boolean unlockedAt(int playerStar) { return playerStar >= star; }

        /**
         * 같은 축의 다른 슬롯. <b>중복 금지</b>가 이 짝 안에서만 걸린다 —
         * 무기 2칸끼리, 방어 2칸끼리. 무기와 방어는 목록이 아예 달라 간섭할 일이 없다.
         */
        public Slot sibling() {
            return switch (this) {
                case WEAPON_1 -> WEAPON_2;
                case WEAPON_2 -> WEAPON_1;
                case CHEST    -> LEGS;
                case LEGS     -> CHEST;
            };
        }

        public String nameKey() { return "lsblessing.slot." + name().toLowerCase(java.util.Locale.ROOT); }
    }

    /**
     * 축복 하나. {@code min}~{@code max} 는 <b>퍼센트</b>다(재생만 「4초당 최대 체력 %」).
     *
     * @param id     저장·명령에 쓰는 안정 키. <b>절대 바꾸지 말 것</b> — 저장된 축복이 이걸로 복원된다.
     */
    public record Blessing(String id, Axis axis, float min, float max) {
        public String nameKey() { return "lsblessing." + id; }
        public String descKey() { return "lsblessing." + id + ".desc"; }

        /** 굴림. 성급이 확률을 정한다 — {@link #curve(int)}. */
        public float roll(RandomSource rnd, int star) {
            return value(percentile(rnd, star));
        }

        /** 백분위(0~1)를 실제 값으로. 표시할 때 역산에 쓰려고 따로 뺐다. */
        public float value(float percentile) {
            return min + (max - min) * Mth.clamp(percentile, 0f, 1f);
        }

        /** 이 값이 범위의 몇 %인가 (0~1). 화면에 「6~11 중 64%」로 띄운다. */
        public float percentileOf(float value) {
            if (max <= min) return 1f;
            return Mth.clamp((value - min) / (max - min), 0f, 1f);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  ⚔️ 무기 10종 — 유물에 붙는다
    // ══════════════════════════════════════════════════════════════════
    public static final List<Blessing> WEAPONS = List.of(
        new Blessing("lifesteal",   Axis.WEAPON,  6f, 11f),  // 준 피해의 그만큼 자가 회복
        new Blessing("executioner", Axis.WEAPON, 20f, 30f),  // 체력 25% 이하 적에게 추가 피해
        new Blessing("pierce",      Axis.WEAPON, 12f, 20f),  // 방어력 무시 (방어도 0이면 절반만큼 추가 피해)
        new Blessing("amplify",     Axis.WEAPON, 12f, 20f),  // 유물 스킬 피해 증가
        new Blessing("resonance",   Axis.WEAPON, 10f, 17f),  // 유물 스킬 쿨다운 감소
        new Blessing("afterglow",   Axis.WEAPON, 15f, 25f),  // 스킬 후 3초간 평타 피해 증가
        new Blessing("mend",        Axis.WEAPON, 12f, 22f),  // 주는 회복 + 받는 회복 증가
        new Blessing("brand",       Axis.WEAPON,  3f,  8f),  // 대상 3초간 받는 피해 증가 (파티 전원)
        new Blessing("cleave",      Axis.WEAPON, 10f, 16f),  // 주변 적 최대 2마리 · 평타 전용
        // ── 16% → 10% (2026-08-11, 실측 후 유저 결정) ──
        // 실측 기여가 **+16% 로 무기 축복 1위**였다. 그런데 값폭(10~16%)이 「연쇄」와 같은데,
        // 연쇄는 **단일 표적에선 0** 이고 출혈은 조건이 아예 없다. 같은 칸에 둘 이유가 없었다.
        // 다른 축복의 실측 기여는 +3.3~10% 대다 — 10% 로 내리면 그 상단(관통·여운)과 나란해진다.
        //   ※ 도트라 잡몹에겐 마지막 틱이 버려지는 손해가 있다. 그만큼은 조건으로 쳐서
        //     상단에 «걸치게» 두고 그 아래로는 안 내렸다.
        new Blessing("bleed",       Axis.WEAPON,  6f, 10f)   // 4초 도트 · 중첩 없음
    );

    // ══════════════════════════════════════════════════════════════════
    //  🛡️ 방어 8종 — 상의·하의에 붙는다
    //
    //  슬롯이 «2칸»이라 초판(부적 1칸)보다 예산이 두 배다. 그래서 셋을 내렸다:
    //    수확 6~11 → 4~8 · 별빛 보호막 10~20 → 4~8 · 원한 12~20 → 8~14(+5초 조건)
    //  특히 보호막이 심했다 — 10~20% 면 30초 교전에서 12~24 HP 로 EHP 가 통째로 두 배였다.
    //  재생(6 HP)과 같은 5~10 HP 선으로 맞췄다.
    // ══════════════════════════════════════════════════════════════════
    public static final List<Blessing> DEFENSE = List.of(
        new Blessing("harvest",  Axis.DEFENSE,  4f,  8f),  // 처치 시 최대 체력의 그만큼 회복
        new Blessing("thorns",   Axis.DEFENSE, 15f, 25f),  // 받은 피해 반사
        new Blessing("resolve",  Axis.DEFENSE, 15f, 25f),  // 체력 30% 이하일 때 받는 피해 감소
        new Blessing("barrier",  Axis.DEFENSE,  4f,  8f),  // 피격 시 흡수 (5초 쿨 · 누적 상한 20%)
        new Blessing("overflow", Axis.DEFENSE, 25f, 45f),  // 초과 회복 → 흡수 (상한 30%)
        new Blessing("sprint",   Axis.DEFENSE, 12f, 22f),  // 3초 무피격 시 이동속도
        new Blessing("regen",    Axis.DEFENSE,  2f,  4f),  // 4초마다 최대 체력의 그만큼 (전투 중)
        new Blessing("grudge",   Axis.DEFENSE,  8f, 14f)   // 최근 5초 내 나를 때린 적에게 추가 피해
    );

    // ── 효과별 부수 상수 ──
    // 범위와 달리 이건 «굴리지 않는» 값이라 축복 레코드에 넣지 않았다. 굴리는 것과 고정인 것이
    // 한 줄에 섞이면 나중에 어느 쪽이 랜덤인지 읽어내기 어려워진다.
    public static final int   CLEAVE_MAX_TARGETS   = 2;
    public static final float CLEAVE_RADIUS        = 3.0f;
    public static final int   BLEED_TICKS          = 80;   // 4초
    public static final int   BRAND_TICKS          = 60;   // 3초
    public static final int   AFTERGLOW_TICKS      = 60;   // 3초
    public static final float EXECUTIONER_HP       = 0.25f;
    public static final float RESOLVE_HP           = 0.30f;
    public static final int   BARRIER_COOLDOWN     = 100;  // 5초
    public static final float BARRIER_CAP          = 0.20f; // 최대 체력 대비 누적 상한
    public static final float OVERFLOW_CAP         = 0.30f;
    public static final int   SPRINT_CALM_TICKS    = 60;   // 3초 무피격
    public static final int   REGEN_INTERVAL       = 80;   // 4초
    public static final int   GRUDGE_WINDOW        = 100;  // 5초

    // ── 마을 해금 플래그 (TownCatalog) ──
    /** 공방 Lv2 — 별의 제단이 열린다. */
    public static final String FLAG_ALTAR   = "forge";
    /** 성소 Lv4 — 제단 강화(수치 리롤 비용 −1). */
    public static final String FLAG_UPGRADE = "blessing";

    public static List<Blessing> pool(Axis axis) {
        return axis == Axis.WEAPON ? WEAPONS : DEFENSE;
    }

    public static Blessing byId(String id) {
        for (Blessing b : WEAPONS) if (b.id().equals(id)) return b;
        for (Blessing b : DEFENSE) if (b.id().equals(id)) return b;
        return null;
    }

    // ══════════════════════════════════════════════════════════════════
    //  수치 굴림 — 성급이 «확률»을 정한다
    //
    //      u = random()^k        값 = 최소 + (최대-최소) × u
    //
    //  | 성급 | k   | 평균 위치 | 하위 20% | 상위 20% |
    //  |------|-----|-----------|----------|----------|
    //  |  3성 | 2.5 |   28.6%   |  52.5%   |   8.5%   |
    //  |  4성 | 1.0 |   50.0%   |  20.0%   |  20.0%   |
    //  |  5성 | 0.4 |   71.4%   |   1.8%   |  42.8%   |
    //
    //  **이게 노가다 브레이크를 겸한다.** 3성에서 최댓값이 뜰 확률이 1/N 이 아니라 1/N² 다.
    //  하한을 막지 않으므로 「운 좋았다」는 순간은 남는다.
    //
    //  ※ 2성은 BLESSING.md 표에 없다(초판이 3·5성만 줬으므로). 상의칸이 2성에 열리게
    //    바뀌면서 필요해져 3.5 로 이었다 — 3성(2.5)보다 한 칸 더 인색한 자리다.
    // ══════════════════════════════════════════════════════════════════
    public static float curve(int star) {
        return switch (Mth.clamp(star, 1, 5)) {
            case 1, 2 -> 3.5f;
            case 3    -> 2.5f;
            case 4    -> 1.0f;
            default   -> 0.4f;
        };
    }

    /** 굴림의 백분위(0~1). 값이 아니라 «범위의 어디»를 먼저 정한다 — 표시와 계산이 같은 수를 쓴다. */
    public static float percentile(RandomSource rnd, int star) {
        return (float) Math.pow(rnd.nextFloat(), curve(star));
    }

    /** 이 성급에서 백분위 {@code p} 이상이 나올 확률. 화면의 「상위 20% 확률 43%」가 이것이다. */
    public static float chanceAbove(int star, float p) {
        if (p <= 0f) return 1f;
        if (p >= 1f) return 0f;
        return 1f - (float) Math.pow(p, 1.0 / curve(star));
    }

    // ══════════════════════════════════════════════════════════════════
    //  다시 뽑기 — 두 축이 서로 다른 플레이를 요구한다 (파편=전투 · 별먼지=탐험)
    //  값은 성급별 «고정»이다. 반복해도 안 오른다 — 「점점 비싸지는」 구조는 노가다를 부른다.
    // ══════════════════════════════════════════════════════════════════

    /** 종류 리롤 — 별의 파편. 정체성을 바꾸는 거라 드물고 무겁다. */
    public static int kindCost(int star) {
        return switch (Mth.clamp(star, 1, 5)) {
            case 1, 2, 3 -> 1;
            case 4       -> 2;
            default      -> 3;
        };
    }

    /**
     * 수치 리롤 — 별먼지. 자주 굴리므로 가볍다.
     *
     * @param altarUpgraded 성소 Lv4「성역의 가호」 달성 여부 — 달성 시 1 깎인다.
     */
    public static int valueCost(int star, boolean altarUpgraded) {
        int base = switch (Mth.clamp(star, 1, 5)) {
            case 1, 2, 3 -> 3;
            case 4       -> 5;
            default      -> 8;
        };
        return altarUpgraded ? Math.max(1, base - 1) : base;
    }
}
