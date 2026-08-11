package com.laststardust.relics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * <h2>5성 패시브 2단 — 각성의 마지막 계단</h2>
 *
 * <p>── 왜 5성인가 (2026-08-11) ──
 * 재분배 이후 사다리는 <b>1성 패시브+기본 · 2성 이동기 · 3성 추가 · 4성 궁극 · 5성 —</b> 이었다.
 * {@code ls_ascend.js} 머리 주석이 「매 단계에 새 버튼이 생긴다 — <i>4성까지는</i>」이라고
 * 스스로 자백하고 있었고, 5성 설명은 「모든 힘의 완성」인데 실제로는 배율과 체력만 올랐다.
 * <b>제일 오래 걸려 도착하는 계단이 제일 심심했다.</b>
 * {@code docs/TODO.md} 의 「4성 패시브 2단」은 재분배 <i>이전</i>(1·3·5성 해금)의 기록이라,
 * 그대로 4성에 넣으면 4성이 둘이 되고 5성은 그대로 빈다.
 *
 * <p>── 왜 전부 딜이 아닌가 ──
 * <b>5성은 이미 {@code 피해 ×3.0} 으로 숫자 보상을 다 준다.</b> 여기서 딜을 또 올리면
 * 2026-08-11 에 12종을 90~114 로 맞춘 목표선({@code RelicEventHandlers.relicScale})이
 * 통째로 깨지고, 열두 판을 다시 재야 한다. 그래서 2단은 전부
 * <b>생존 · 유틸 · 난전</b> 축이다 — 세지는 게 아니라 달라진다.
 *
 * <p>단일 표적 지속 DPS 기여가 <b>전부 0</b> 이다. {@code /dummy} 로 재도 12종 값이 안 움직인다.
 * 그게 이 설계의 합격 조건이다.
 *
 * <p>── 검증 ──
 * 12종 중 일곱이 「받는 피해」 축이라 {@code /dummy hit} 으로 정확히 잰다
 * (별의 축복 재보정 때 만든 계측기가 그대로 쓰인다 — {@code docs/BLESSING.md}).
 * 나머지 다섯은 파티(파나케이아·바르비톤·펠리온)이거나 눈으로 보는 것(솔라리스 넉백·시리우스 도약).
 */
@EventBusSubscriber(modid = LSRelics.MODID)
public final class Passive2 {
    private Passive2() {}

    /** 2단이 열리는 각성 단계. */
    public static final int STAR = 5;

    // ══════════════════════════════════════════════════════════════════
    //  수치 — 12종 (docs/CLASSES.md 「5성 패시브 2단」)
    // ══════════════════════════════════════════════════════════════════

    /** 이지스 — 아군 받는 피해 감소. 1단 5% → 2단 8%, 내가 «막는 중»이면 16%. */
    public static final float AEGIS_AURA_DR    = 0.08f;
    public static final float AEGIS_GUARD_DR   = 0.16f;

    /** 타이탄 — 거인(최대 체력 40+)을 때리면 자신이 잠깐 단단해진다. */
    public static final float TITAN_DR         = 0.12f;
    public static final int   TITAN_TICKS      = 60;   // 3초

    /** 게볼그 — 창 길이만큼 떨어져서 맞힌 적을 둔화시킨다. */
    public static final double LANCER_MIN_DIST = 3.0;
    public static final int    LANCER_SLOW_LV  = 1;    // 둔화 II = −30%
    public static final int    LANCER_SLOW_TCK = 40;   // 2초

    /** 스틱스 — 연쇄 살상 중첩당 받는 피해 감소 (최대 3중첩 = 24%). */
    public static final float  STYX_DR_PER     = 0.08f;

    /** 헤스페로스 — 저주받은 적이 죽으면 주변에 중첩 절반이 옮겨간다. */
    public static final double HECATE_SPREAD_R = 5.0;

    /** 바르비톤 — 공명 오라가 넓고 빨라진다 (8칸 +10% → 11칸 +15%). */
    public static final double HARMONIA_RANGE  = 11.0;
    public static final float  HARMONIA_SPEED  = 0.15f;

    /** 아드라스테이아 — 패링에 성공한 뒤 잠깐 단단해진다. */
    public static final float  NEMESIS_DR      = 0.15f;
    public static final int    NEMESIS_TICKS   = 60;   // 3초

    /** 펠리온 — 자기 체력이 이 아래로 떨어지면 전이 회복이 자신도 대상으로 삼는다. */
    public static final float  CHIRON_SELF_HP  = 0.30f;

    /** 솔라리스 — 저격 거리에 비례한 넉백 (30칸에서 최대). */
    public static final double GUNNER_KB_MAX   = 1.1;

    /** 시리우스 — 처치 시 도약. 바람의 발걸음(이속 +20%)에 얹힌다. */
    public static final int    HUNTER_JUMP_LV  = 1;    // 점프 강화 II
    public static final int    HUNTER_JUMP_TCK = 80;   // 4초 (이속과 같은 길이)

    /** 셀레스티아 — 별빛 충전이 돌 때마다 얇은 막이 선다. */
    public static final float  SAGE_SHIELD     = 2.0f;
    public static final float  SAGE_SHIELD_CAP = 8.0f;
    public static final int    SAGE_SHIELD_TCK = 80;   // 4초

    // ══════════════════════════════════════════════════════════════════
    //  판정
    // ══════════════════════════════════════════════════════════════════

    /** 이 무기가 그 유물이고 5성인가. */
    public static boolean is(ItemStack stack, Item relic) {
        return !stack.isEmpty() && stack.getItem() == relic
            && com.laststardust.relics.item.RelicSkills.star(stack) >= STAR;
    }

    /** 주손에 그 유물을 5성으로 들고 있는가. */
    public static boolean on(Player p, Item relic) {
        return p != null && is(p.getMainHandItem(), relic);
    }

    // ══════════════════════════════════════════════════════════════════
    //  공용 — 시한부 피해 감소 창
    //
    //  타이탄·아드라스테이아가 같은 모양을 쓴다. 각자 필드를 두면 감소가
    //  «어디서 왔는지» 추적할 데가 둘로 갈리고, 겹칠 때 곱해지는지 더해지는지가
    //  두 곳의 사정으로 정해진다. 한 곳에 모아 **항상 곱해진다**로 고정한다.
    // ══════════════════════════════════════════════════════════════════
    private record Window(float dr, long until) {}
    private static final Map<UUID, Window> DR = new HashMap<>();

    /** {@code ticks} 동안 받는 피해를 {@code dr} 만큼 깎는다. 더 센 창이 살아 있으면 덮어쓰지 않는다. */
    public static void guard(ServerPlayer p, float dr, int ticks) {
        long until = p.level().getGameTime() + ticks;
        Window cur = DR.get(p.getUUID());
        if (cur != null && cur.until() > until && cur.dr() >= dr) return;
        DR.put(p.getUUID(), new Window(dr, until));
    }

    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;

        float mult = 1f;

        Window w = DR.get(p.getUUID());
        if (w != null) {
            if (w.until() <= p.level().getGameTime()) DR.remove(p.getUUID());
            else mult *= (1f - w.dr());
        }

        // ── 스틱스 「망자의 발걸음」 — 처치를 이어가면 단단해진다 ──
        // 속성 모디파이어를 «중첩 카운터»로 되읽는다. 별도 장부를 두면 연쇄 살상의
        // 만료(ChainFrenzy)와 어긋나서, 버프는 끝났는데 감소만 남는 그림이 나온다.
        if (on(p, LSRelics.ASSASSIN.get())) {
            int stacks = RelicEventHandlers.frenzyStacks(p);
            if (stacks > 0) mult *= (1f - STYX_DR_PER * stacks);
        }

        if (mult < 1f) event.setAmount(event.getAmount() * mult);
    }

    // 청소 — 5초마다. 안 하면 죽은 플레이어의 만료된 창이 계속 남는다.
    private static int tick;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tick % 100 != 0) return;
        long now = event.getServer().overworld().getGameTime();
        DR.entrySet().removeIf(e -> e.getValue().until() <= now);
    }

    // ══════════════════════════════════════════════════════════════════
    //  헤스페로스 「저주의 전이」 — 죽은 자의 낙인이 옮겨간다
    //
    //  단일 표적 DPS 는 **정확히 0** 이다. 표적이 죽어야 발동하니까.
    //  난전에서만 값이 나오는데, 그게 저주를 쌓는 유물의 결에 맞는다 —
    //  하나를 오래 갈아 죽이면 그 다음이 이미 절반 익어 있다.
    // ══════════════════════════════════════════════════════════════════
    public static void spreadCurse(ServerLevel level, LivingEntity dead, ServerPlayer killer) {
        if (!on(killer, LSRelics.HECATE.get())) return;
        int stacks = CurseManager.stacks(dead);
        if (stacks < 2) return;               // 1중첩은 절반이 0 이라 옮길 게 없다
        int give = stacks / 2;

        int spread = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                dead.getBoundingBox().inflate(HECATE_SPREAD_R),
                x -> x.isAlive() && x != dead && !(x instanceof Player))) {
            CurseManager.add(e, give);
            level.sendParticles(ParticleTypes.WITCH,
                e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
            spread++;
        }
        if (spread > 0) {
            level.sendParticles(ParticleTypes.SOUL,
                dead.getX(), dead.getY() + 0.6, dead.getZ(), 12, 0.4, 0.3, 0.4, 0.03);
        }
    }
}
