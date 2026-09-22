package com.laststardust.relics.blessing;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.laststardust.relics.BleedManager;
import com.laststardust.relics.LSRelics;
import com.laststardust.relics.LsDamage;
import com.laststardust.relics.data.BlessingCatalog;
import com.laststardust.relics.data.BlessingData;
import com.laststardust.relics.data.LSData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 별의 축복 18종의 «효과». 정의는 {@link BlessingCatalog}, 저장은 {@link BlessingData}.
 *
 * <p>── 왜 {@code RelicEventHandlers} 가 아니라 따로인가 ──
 * 그쪽은 이미 552줄이고 <b>유물 자체의 규칙</b>(배율·백어택·관통화살)을 담고 있다.
 * 축복은 유물 위에 얹히는 별개 층이라, 섞으면 「이 배율이 유물 것인가 축복 것인가」를
 * 매번 되짚어야 한다. {@code DECISIONS.md} 1-B 가 유물 배율을 한 곳에 모은 이유와 같다.
 *
 * <p>── 스킬 두 개는 여기 없다 ──
 * <b>공명</b>(쿨 감소)과 <b>여운</b>(시전 후 3초 창)은 {@code RelicSkills.ready()} 안에 있다.
 * 거기가 모든 스킬 시전이 지나는 유일한 길목이라, 이벤트로는 「시전했다」를 못 잡는다.
 */
@EventBusSubscriber(modid = LSRelics.MODID)
public final class BlessingEffects {
    private BlessingEffects() {}

    /**
     * 「관통」이 <b>방어도 0 인 적</b>에게 줄 몫. 방어가 삼킬 게 없어 깎을 대상이 없을 때
     * 대신 얹는 고정 추가 피해의 비율이다 — {@code onIncoming} 의 관통 절 참고.
     *
     * <p>⚠️ 0.5 → <b>0.2</b> (2026-08-11, 실측 후 유저 결정). 실측이 뒤집혀 있었다:
     * 방어도 0 에서 <b>+10.0%</b>, 방어도 10 에서 <b>+3.7%</b> — 「방어 무시」인데 방어 없는
     * 적에게 2.7배 셌다. 0.2 면 방어도 0 에서 +4.0% 가 되어 방어도 10 실효와 맞는다.
     */
    private static final float PIERCE_UNARMORED = 0.2f;

    // ══════════════════════════════════════════════════════════════════
    //  조회 — 「이 사람에게 이 축복이 몇 %로 걸려 있나」
    // ══════════════════════════════════════════════════════════════════

    /** 걸려 있으면 <b>비율</b>(0.09 = 9%), 없으면 0. 성급 검사는 {@link BlessingData#active} 안에 있다. */
    public static float v(Player player, String id) {
        if (!(player instanceof ServerPlayer sp) || sp.getServer() == null) return 0f;
        LSData d = LSData.get(sp.getServer());
        String name = sp.getGameProfile().getName();
        return d.blessing().active(name, d.hero().star(name), id) / 100f;
    }

    private static ServerPlayer attacker(DamageSource src) {
        return src.getEntity() instanceof ServerPlayer sp ? sp : null;
    }

    // ══════════════════════════════════════════════════════════════════
    //  상태 — 창(window)이 필요한 축복들
    //
    //  전부 «엔티티 id 또는 UUID → 만료 틱». 월드 시간(getGameTime)을 쓰므로 서버가 꺼졌다
    //  켜져도 자연히 만료된다. 100틱마다 청소한다 — 안 하면 공성 한 판에 수백 개가 쌓인다.
    // ══════════════════════════════════════════════════════════════════

    private record Mark(long expire, float value) {}

    /** 별의 낙인 — 표식은 «맞은 쪽»에 붙는다. 파티 전원의 피해가 이걸 읽는다. */
    private static final Map<Integer, Mark> BRANDED = new HashMap<>();

    /** 원한 — 나를 때린 적. (플레이어 UUID, 적 엔티티 id) → 만료 틱 */
    private static final Map<UUID, Map<Integer, Long>> GRUDGE = new HashMap<>();

    /** 여운 — 스킬 시전 후 3초 창. {@code RelicSkills.ready()} 가 채운다. */
    private static final Map<UUID, Long> AFTERGLOW = new HashMap<>();

    /** 별빛 보호막 — 다음 발동 가능 틱. */
    private static final Map<UUID, Long> BARRIER_CD = new HashMap<>();

    /** 질주 — 마지막 피격 틱. 재생의 「전투 중」 판정도 이걸 같이 본다. */
    private static final Map<UUID, Long> LAST_HIT = new HashMap<>();

    /** 재생 — 마지막 회복 틱. */
    private static final Map<UUID, Long> REGEN_AT = new HashMap<>();

    /** 치유 증폭(주는 쪽) — 지금 회복을 «넣고 있는» 사람. {@code LsDamage.inSkill} 과 같은 수법이다. */
    private static ServerPlayer healingBy;

    /**
     * 연쇄가 «자기가 만든 피해»에 다시 걸리는 걸 막는다.
     *
     * <p>연쇄는 {@code e.hurt(playerAttack(p), …)} 로 진짜 피해를 넣으므로 그 피해가 다시
     * 이 클래스의 이벤트를 지난다. 막지 않으면 <b>주변 적 → 그 주변 적 → …</b> 로 번져
     * 몹이 몰린 곳에서 지수적으로 터진다. 겸사겸사 처형자·관통 같은 배율이 파생 피해에
     * 두 번 곱해지는 것도 여기서 같이 막힌다 — 연쇄는 «이미 계산된 피해의 몇 %»여야 한다.
     */
    private static boolean inSplash;

    /**
     * 파생 피해를 내는 동안만 깃발을 세운다.
     *
     * <p><b>이전 값을 복원한다</b> — 그냥 {@code false} 로 되돌리면 안 된다. 연쇄가 도는 중에
     * 가시 갑주가 겹쳐 발동하면 안쪽 {@code finally} 가 깃발을 내려버리고, 그 뒤로 남은
     * 연쇄 대상들이 다시 축복 처리를 타게 된다. {@code LsDamage.hit} 이 {@code prev} 를
     * 들고 있는 것과 같은 이유다.
     */
    private static void derived(Runnable body) {
        boolean prev = inSplash;
        inSplash = true;
        try { body.run(); } finally { inSplash = prev; }
    }

    /**
     * 이 회복은 누가 준 것인가를 표시한다. {@code LivingHealEvent} 는 시전자를 안 알려주므로
     * (그쪽 한계는 {@code ThreatManager} 주석에도 적혀 있다) 회복을 넣는 동안만 깃발을 세운다.
     *
     * <p>서버 스레드에서 {@code heal()} 은 동기로 끝나므로 static 하나로 충분하다.
     * 다만 회복 안에서 또 회복이 날 수 있어(과포화 → 흡수) 되돌릴 때 이전 값을 복원한다.
     */
    public static void healingBy(ServerPlayer giver, Runnable body) {
        ServerPlayer prev = healingBy;
        healingBy = giver;
        try { body.run(); } finally { healingBy = prev; }
    }

    /**
     * 지금 흐르는 회복이 <b>축복·유물이 일으킨 것</b>인가. 계측기({@code DummyManager})가
     * 바닐라 자연 회복·음식·물약을 걸러내려고 읽는다.
     *
     * <p>「재생」 축복은 케이론 기준 <b>0.34 HPS</b> 라(최대 체력 34 의 4% 를 4초마다),
     * 배경 회복이 조금이라도 섞이면 읽을 수가 없다.
     */
    public static boolean healAttributed() { return healingBy != null; }

    /** 여운 창을 연다 — {@code RelicSkills.ready()} 가 시전 성공 직후에 부른다. */
    public static void markSkillCast(ServerPlayer p) {
        if (v(p, "afterglow") <= 0f) return;
        AFTERGLOW.put(p.getUUID(), p.level().getGameTime() + BlessingCatalog.AFTERGLOW_TICKS);
    }

    /** 공명 — 이 사람의 스킬 쿨다운을 줄인 값. {@code RelicSkills.ready()} 가 부른다. */
    public static int resonance(Player p, int cooldownTicks) {
        float r = v(p, "resonance");
        if (r <= 0f) return cooldownTicks;
        return Math.max(1, Math.round(cooldownTicks * (1f - r)));
    }

    // ══════════════════════════════════════════════════════════════════
    //  ① 내가 «주는» 피해 — 최종 수치가 정해지기 전
    // ══════════════════════════════════════════════════════════════════
    @SubscribeEvent
    public static void onOutgoing(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || inSplash) return;

        float amount = event.getAmount();
        long now = victim.level().getGameTime();

        // 별의 낙인 — 표식은 «누가 때리든» 적용된다. 그래서 공격자 검사보다 먼저 본다.
        Mark mark = BRANDED.get(victim.getId());
        if (mark != null && mark.expire() > now) amount *= (1f + mark.value());

        ServerPlayer p = attacker(event.getSource());
        if (p == null) { event.setAmount(amount); return; }

        // 처형자 — 체력 25% 이하
        float exec = v(p, "executioner");
        if (exec > 0f && victim.getMaxHealth() > 0f
            && victim.getHealth() / victim.getMaxHealth() <= BlessingCatalog.EXECUTIONER_HP) {
            amount *= (1f + exec);
        }

        // 관통 — 방어력 무시.
        //
        // 이 이벤트는 «방어도 계산 전»이라 방어력을 진짜로 깎을 수가 없다. 대신 방어가
        // 삼킬 몫만큼 미리 얹는다. 방어도 0 인 적에겐 얹을 게 없으므로 고정 추가 피해를
        // 준다 — 안 그러면 무방비 적에게 이 축복이 죽은 칸이 된다.
        //
        // ── 그 보정을 «절반» → «1/5» 로 내렸다 (2026-08-11, 실측 후 유저 결정) ──
        // 실측에서 뒤집혀 있었다: 방어도 0 에서 **+10.0%**, 방어도 10 에서 **+3.7%**.
        // 「방어 무시」인데 방어 없는 적에게 2.7배 셌다.
        //   원인은 이 팩의 방어도가 뺄셈처럼 작동하는 것이다(ApothicAttributes) — 방어도 10 이
        //   평타에서 16.8, 일도양단에서 19.4 를 가져간다. 큰 한 방일수록 비율로는 거의 안
        //   깎이고(−2.6%), 그 20% 를 되돌려도 얻는 게 작다.
        // 1/5 로 두면 방어도 0 에서 20% × 0.2 = **+4.0%** 로, 방어도 10 실효(+3.7%)와 맞는다.
        float pierce = v(p, "pierce");
        if (pierce > 0f) {
            float blocked = armorFraction(victim, amount);
            amount *= blocked > 0f ? (1f + blocked * pierce) : (1f + pierce * PIERCE_UNARMORED);
        }

        // 별빛 증폭 — 유물 «스킬» 피해만. 평타/스킬 구분은 LsDamage 가 이미 한다.
        if (LsDamage.inSkill()) {
            float amp = v(p, "amplify");
            if (amp > 0f) amount *= (1f + amp);
        } else {
            // 여운 — 스킬 직후 3초간 «평타»만
            Long glow = AFTERGLOW.get(p.getUUID());
            if (glow != null && glow > now) {
                float g = v(p, "afterglow");
                if (g > 0f) amount *= (1f + g);
            }
        }

        // 원한 — 최근 5초 내 나를 때린 적에게만
        float grudge = v(p, "grudge");
        if (grudge > 0f) {
            Map<Integer, Long> mine = GRUDGE.get(p.getUUID());
            if (mine != null) {
                Long until = mine.get(victim.getId());
                if (until != null && until > now) amount *= (1f + grudge);
            }
        }

        event.setAmount(amount);
    }

    /**
     * 방어도가 삼킬 비율(0~1). 바닐라 공식 그대로:
     * {@code min(20, max(armor/5, armor - dmg/(2 + toughness/4))) / 25}
     */
    private static float armorFraction(LivingEntity victim, float damage) {
        float armor = victim.getArmorValue();
        if (armor <= 0f) return 0f;
        float tough = (float) victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float eff = Math.min(20f, Math.max(armor / 5f, armor - damage / (2f + tough / 4f)));
        return eff / 25f;
    }

    // ══════════════════════════════════════════════════════════════════
    //  ② 피해가 «들어간 뒤» — 실제로 준 양을 알아야 하는 것들
    // ══════════════════════════════════════════════════════════════════
    @SubscribeEvent
    public static void onDealt(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || inSplash) return;
        ServerPlayer p = attacker(event.getSource());
        if (p == null) return;

        float dealt = event.getNewDamage();
        if (dealt <= 0f) return;
        long now = level.getGameTime();

        // 흡혈 — 준 피해만큼. 자기 회복이라 healingBy 를 세운다(치유 증폭이 이걸 증폭한다).
        float steal = v(p, "lifesteal");
        if (steal > 0f) healingBy(p, () -> p.heal(dealt * steal));

        // 별의 낙인 — 표식을 «심는다». 값은 심은 사람 것이고, 그 뒤로는 누가 때리든 적용된다.
        float brand = v(p, "brand");
        if (brand > 0f) {
            BRANDED.put(victim.getId(), new Mark(now + BlessingCatalog.BRAND_TICKS, brand));
        }

        // 출혈 — BleedManager 가 이미 있다(게볼그용). perSecond 로 받으므로 총량을 초로 나눈다.
        //
        // ⚠️ **자기 도트에 다시 걸면 안 된다.** BleedManager.apply 는 이미 걸린 대상에게
        //    시간을 «갱신»하므로, 출혈 틱이 또 출혈을 걸면 지속시간이 영원히 갱신된다
        //    (오류도 안 나고 조용히 영구 출혈이 된다). 이름표로 자기 피해를 알아본다.
        float bleed = v(p, "bleed");
        if (bleed > 0f && !BleedManager.LABEL.equals(LsDamage.currentLabel())) {
            float total = dealt * bleed;
            BleedManager.apply(level, victim, p,
                total / (BlessingCatalog.BLEED_TICKS / 20f), BlessingCatalog.BLEED_TICKS);
        }

        // 연쇄 — 평타 전용. 스킬에도 붙이면 광역 스킬이 제곱으로 커진다.
        float cleave = v(p, "cleave");
        if (cleave > 0f && !LsDamage.inSkill()) {
            splash(level, p, victim, dealt * cleave);
        }
    }

    /**
     * 연쇄 — 주변 적에게 나눠 때린다.
     *
     * <p><b>대상 수 상한이 핵심이다.</b> 상한이 없으면 공성 밀집 구간에서 흡혈과 곱해져
     * 무한 회복이 된다. 그래서 {@link BlessingCatalog#CLEAVE_MAX_TARGETS} 를 넘지 않는다.
     * 플레이어·길들인 동물은 제외한다 — 파티 오폭은 축복이 아니라 재앙이다.
     */
    private static void splash(ServerLevel level, ServerPlayer p, LivingEntity origin, float amount) {
        if (amount <= 0f) return;
        double r = BlessingCatalog.CLEAVE_RADIUS;
        AABB box = origin.getBoundingBox().inflate(r);
        derived(() -> {
            int hit = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (hit >= BlessingCatalog.CLEAVE_MAX_TARGETS) break;
                if (e == origin || e == p || e instanceof Player) continue;
                if (!e.isAlive() || e.isAlliedTo(p)) continue;
                if (e.distanceToSqr(origin) > r * r) continue;
                // ── 이름표를 단다 (2026-08-11) ──
                // 예전엔 맨 `hurt` 라 이름표가 없었고, 계측 리포트에서 **평타 칸에 섞였다.**
                // 그래서 2026-08-11 측정에서 「평타 65타 → 168타」로만 보였고, 실제 값은
                // 기준선을 빼서 역산해야 했다 — 무보정 두 판의 평타 타수가 8% 벌어져
                // 그 오차가 그대로 증폭돼 **16~21% 라는 넓은 답**이 나왔다.
                // 「출혈」은 이름표가 있어 한 줄로 딱 읽혔다. 같은 대접을 해준다.
                //
                // ⚠️ LsDamage.hit 은 `inSkill` 을 세운다. 연쇄는 «평타에서 파생된» 피해라
                //    위협도 평타 배수(ThreatManager)가 안 붙게 되는데, 그게 맞다 —
                //    한 번 휘두른 것으로 어그로를 세 배 끄는 게 오히려 이상하다.
                com.laststardust.relics.LsDamage.hit(
                    e, level.damageSources().playerAttack(p), amount, "연쇄");
                hit++;
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════
    //  ③ 내가 «받는» 피해
    // ══════════════════════════════════════════════════════════════════
    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!(p.level() instanceof ServerLevel level)) return;
        // 파생 피해(가시 반사·연쇄)에는 축복을 다시 태우지 않는다.
        //
        // ⚠️ 이 줄이 없으면 **가시 갑주끼리 무한 왕복**한다. A 가 B 를 때리면 B 의 가시가
        //    A 를 때리고, 그게 다시 A 의 가시를 깨워 B 를 때린다. 몹은 축복이 없어 안 걸리지만
        //    사람끼리는 걸린다 — 그리고 그때는 서버가 그냥 멎는다.
        if (inSplash) return;

        long now = level.getGameTime();
        LAST_HIT.put(p.getUUID(), now);   // 질주 초기화 + 재생의 「전투 중」

        // 원한 — 「나를 때렸다」를 기록. 때린 쪽이 살아 있어야 의미가 있다.
        if (event.getSource().getEntity() instanceof LivingEntity foe && !(foe instanceof Player)) {
            if (v(p, "grudge") > 0f) {
                GRUDGE.computeIfAbsent(p.getUUID(), k -> new HashMap<>())
                      .put(foe.getId(), now + BlessingCatalog.GRUDGE_WINDOW);
            }
        }

        // 반사는 «원본» 기준이다 — 불굴로 깎인 값을 쓰면 둘을 같이 끼울수록 반사가 줄어
        // 두 축복이 서로를 깎는다. 그래서 깎기 «전»에 잡아 둔다.
        final float raw = event.getAmount();
        float amount = raw;

        // 불굴 — 체력 30% 이하에서만
        float resolve = v(p, "resolve");
        if (resolve > 0f && p.getMaxHealth() > 0f
            && p.getHealth() / p.getMaxHealth() <= BlessingCatalog.RESOLVE_HP) {
            amount *= (1f - resolve);
        }
        event.setAmount(amount);

        // 별빛 보호막 — 흡수를 «미리» 깐다. 5초 쿨 + 누적 상한.
        float barrier = v(p, "barrier");
        if (barrier > 0f) {
            Long cd = BARRIER_CD.get(p.getUUID());
            if (cd == null || cd <= now) {
                BARRIER_CD.put(p.getUUID(), now + BlessingCatalog.BARRIER_COOLDOWN);
                addAbsorption(p, p.getMaxHealth() * barrier, BlessingCatalog.BARRIER_CAP);
            }
        }

        // 가시 갑주 — 반사. inSplash 로 감싸는 이유는 연쇄와 같다: 반사 피해가 다시
        // 이 클래스를 지나면 상대의 가시 갑주와 무한 왕복이 된다.
        // ⚠️ 이름표를 단다 — 연쇄와 같은 이유다(2026-08-11). 맨 `hurt` 면 계측 리포트에서
        //    **평타 칸에 섞인다.** 반사 피해원은 플레이어가 주인이라 「내가 준 피해」로 잡히는데,
        //    그러면 「평타가 세진 건지 반사가 붙은 건지」를 못 가른다.
        //
        // ── ⚠️ 2026-08-11: 여기를 한 번 `relicSource()` 로 바꿨다가 **되돌렸다** ──
        // 계측에서 반사가 설계(25%)의 2.2배로 나와 「바닐라 가시 타입이 스킬트리 배수를
        // 탄다」고 진단하고 바꿨는데, **그 진단이 틀렸다.**
        //
        // 진짜 원인은 계측기 쪽이었다. `/dummy hit 20` 이 넣는 피해가 **난이도 Hard 에서
        // ×1.5 로 불어나 30 이 되어 도착**하고 있었다(`mobAttack` 은 damage type 의
        // `scaling: when_caused_by_living_non_player` 을 타므로 난이도 배수가 붙는다).
        // 30 × 25% × relicScale(케이론 1.374) = **10.30** — 실측 10.3 과 소수점까지 맞는다.
        // **가시는 처음부터 정확했다.** 원본을 20 으로 알고 나눈 내 계산만 틀렸다.
        //
        // 그래서 바닐라 타입으로 되돌린다 — 반사가 **방어도를 무시**하는 게 이 축복의 원래
        // 성질이고, 그걸 잘못된 진단 때문에 잃을 이유가 없다.
        float thorns = v(p, "thorns");
        if (thorns > 0f && event.getSource().getEntity() instanceof LivingEntity foe
            && foe != p && foe.isAlive()) {
            derived(() -> com.laststardust.relics.LsDamage.hit(
                foe, level.damageSources().thorns(p), raw * thorns, "가시"));
        }
    }

    /** 흡수 하트를 더한다. 상한은 최대 체력 대비 비율 — 계속 쌓여 무적이 되는 걸 막는다. */
    private static void addAbsorption(Player p, float add, float capFraction) {
        float cap = p.getMaxHealth() * capFraction;
        float before = p.getAbsorptionAmount();
        float after = Math.min(cap, before + add);
        p.setAbsorptionAmount(after);
        noteShield(Math.max(0f, after - before));
    }

    /**
     * 「보호막을 이만큼 깔았다」고 계기에 알린다. <b>축복 밖에서도 부른다.</b>
     *
     * <p>초판은 {@link #addAbsorption} 안에서만 셌다. 그런데 흡수를 까는 통로가 둘이다 —
     * 축복(여기)과 {@link com.laststardust.relics.ShieldManager}(파나케이아 「과잉 치유」·
     * 셀레스티아 5성 「별빛 방벽」). <b>후자는 계기에 전혀 안 잡혔다.</b>
     * 그래서 셀레스티아 2단을 재고 「보호막 0 — 안 도는군」으로 읽을 뻔했다.
     * 실제로는 <b>도는데 안 보였다.</b>
     */
    public static void noteShield(float given) {
        if (given > 0f) shieldGiven += given;
    }

    /**
     * 이번 측정 동안 <b>실제로 깔린</b> 보호막 총량 — <b>축복과 유물을 합쳐서</b> 센다.
     * {@code DummyManager} 가 읽는다.
     *
     * <p>⚠️ <b>흡수량을 밖에서 관찰해서는 못 잰다.</b> 「별빛 보호막」은 피해가 들어오기
     * <b>직전</b>에 흡수를 까는데, 그 흡수가 <b>같은 틱 안에서 그 피해에 바로 소모된다.</b>
     * 틱 끝에 값을 보고 증가분을 세면 순증이 0 이라 아무것도 안 잡힌다 —
     * 2026-08-11 ⑤ 묶음에서 「흡수 0」이 나온 게 그래서였다(축복은 멀쩡히 돌고 있었다).
     * 그래서 <b>까는 자리에서</b> 센다.
     */
    private static float shieldGiven;

    public static float shieldGiven() { return shieldGiven; }
    public static void resetShieldGiven() { shieldGiven = 0f; }

    // ══════════════════════════════════════════════════════════════════
    //  ④ 처치
    // ══════════════════════════════════════════════════════════════════
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        BRANDED.remove(event.getEntity().getId());   // 표식은 대상과 함께 사라진다

        ServerPlayer p = attacker(event.getSource());
        if (p == null) return;

        float harvest = v(p, "harvest");
        if (harvest > 0f) {
            float amt = p.getMaxHealth() * harvest;
            healingBy(p, () -> p.heal(amt));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  ⑤ 회복 — 치유 증폭과 과포화가 여기서 만난다
    // ══════════════════════════════════════════════════════════════════
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer target)) return;

        float amount = event.getAmount();

        // 치유 증폭 — «받는» 쪽
        float recv = v(target, "mend");
        if (recv > 0f) amount *= (1f + recv);

        // 치유 증폭 — «주는» 쪽. 자기 자신이면 두 번 곱하지 않는다.
        if (healingBy != null && healingBy != target) {
            float give = v(healingBy, "mend");
            if (give > 0f) amount *= (1f + give);
        }

        // 별빛 과포화 — 넘칠 몫을 흡수로. 버려질 회복이라 «공짜»가 아니라 «안 버리는» 것이다.
        float over = v(target, "overflow");
        if (over > 0f) {
            float room = target.getMaxHealth() - target.getHealth();
            float spill = amount - room;
            if (spill > 0f) {
                addAbsorption(target, spill * over, BlessingCatalog.OVERFLOW_CAP);
            }
        }

        event.setAmount(amount);
    }

    // ══════════════════════════════════════════════════════════════════
    //  ⑥ 매 틱 — 재생·질주, 그리고 상태 청소
    // ══════════════════════════════════════════════════════════════════
    private static final ResourceLocation SPRINT_ID =
        ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "blessing_sprint");

    private static int tick;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // 20틱(1초)마다면 충분하다. 재생은 4초 간격이고 질주는 3초 창이라 틱마다 볼 이유가 없다.
        if (++tick % 20 != 0) return;

        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            long now = p.level().getGameTime();
            UUID id = p.getUUID();
            Long lastHit = LAST_HIT.get(id);

            // 재생 — «전투 중»에만. 안 그러면 앉아서 도는 자동 회복이 되어
            // 「먹을 것을 챙긴다」는 바닐라 축이 통째로 무의미해진다.
            float regen = v(p, "regen");
            if (regen > 0f && lastHit != null && now - lastHit <= 100L
                && p.getHealth() < p.getMaxHealth()) {
                Long at = REGEN_AT.get(id);
                if (at == null || now - at >= BlessingCatalog.REGEN_INTERVAL) {
                    REGEN_AT.put(id, now);
                    float amt = p.getMaxHealth() * regen;
                    healingBy(p, () -> p.heal(amt));
                }
            }

            // 질주 — 3초간 안 맞으면 붙고, 맞으면 떨어진다.
            float sprint = v(p, "sprint");
            AttributeInstance move = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (move != null) {
                boolean calm = sprint > 0f
                    && (lastHit == null || now - lastHit >= BlessingCatalog.SPRINT_CALM_TICKS);
                boolean on = move.getModifier(SPRINT_ID) != null;
                if (calm && !on) {
                    move.addTransientModifier(new AttributeModifier(
                        SPRINT_ID, sprint, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                } else if (!calm && on) {
                    move.removeModifier(SPRINT_ID);
                }
            }
        }

        // 청소 — 5초마다. 안 하면 공성 한 판에 수백 개가 남는다.
        if (tick % 100 != 0) return;
        long now = event.getServer().overworld().getGameTime();
        BRANDED.entrySet().removeIf(e -> e.getValue().expire() <= now);
        AFTERGLOW.entrySet().removeIf(e -> e.getValue() <= now);
        BARRIER_CD.entrySet().removeIf(e -> e.getValue() <= now);
        GRUDGE.values().forEach(m -> m.entrySet().removeIf(e -> e.getValue() <= now));
        GRUDGE.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /**
     * 접속할 때 현황을 한 번 보낸다.
     *
     * <p>툴팁은 클라 사본({@code BlessCache})을 읽는데, 그건 «바뀔 때» 오는 패킷으로만 채워진다.
     * 이게 없으면 <b>접속 후 한 번도 안 굴린 사람의 툴팁이 비어 있다</b> — 축복은 멀쩡히
     * 걸려 있는데 화면에만 없어서, 「효과가 안 걸렸나?」로 읽힌다.
     */
    @SubscribeEvent
    public static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) BlessGui.sync(p, "");
    }

    /** 서버가 내려갈 때 전부 비운다 — 싱글플레이에서 월드를 바꿔 열면 이전 상태가 남는다. */
    public static void reset() {
        BRANDED.clear(); GRUDGE.clear(); AFTERGLOW.clear();
        BARRIER_CD.clear(); LAST_HIT.clear(); REGEN_AT.clear();
        healingBy = null;
    }
}
