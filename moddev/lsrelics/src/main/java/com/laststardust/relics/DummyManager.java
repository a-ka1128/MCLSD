package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.Vec3;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 훈련 더미 — 파티의 "실효 DPS"를 실측한다.
//
// 왜 필요한가: 모드에 적어둔 평타 DPS(12.0 등)는 가만히 서서 100% 명중으로 쉬지 않고
// 때리는 이론 최대치라, 실전(회피·접근·헛스윙·쿨다운)과는 크게 다르다.
// 보스 체력을 "파티 DPS × 목표 전투 시간"으로 설계하려면 이론값이 아니라 실측값이 필요하다.
//
// 집계는 LivingDamageEvent.Post 에서 한다 — 방어구·저항·흡수까지 전부 지난 "실제로 박힌" 값이라
// LivingIncomingDamageEvent(감쇄 전)보다 정확하다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class DummyManager {
    private DummyManager() {}

    private static final Logger LOG = LogUtils.getLogger();

    private static final double DUMMY_HP_DEFAULT = 1_000_000.0; // AttributeCaps 가 상한을 풀어둬서 가능
    private static final double REFILL_BELOW = 0.2;     // 20% 밑으로 떨어지면 다시 채운다

    // ── 더미 체력을 바꿀 수 있다 (2026-08-11) ──
    // 기본 100만은 「60초 동안 안 죽는 과녁」이라는 목적에는 맞았는데, **체력 비율에 걸리는
    // 축복을 영원히 못 재는** 부작용이 있었다. `executioner`(체력 25% 이하 추가 피해)는
    // 100만짜리를 60초에 25% 까지 못 깎으니 한 번도 발동하지 않고, `harvest`(처치 시 회복)는
    // 더미가 안 죽으니 마찬가지다. 재려면 체력을 내릴 수 있어야 한다.
    //   · 낮게 잡을 때 REFILL_BELOW(20%) 자동 보충이 executioner 구간을 지워버리므로,
    //     체력을 직접 정한 동안에는 보충을 끈다(아래 dummyHpFixed).
    private static double dummyHp = DUMMY_HP_DEFAULT;
    private static boolean dummyHpFixed = false;

    public static double hp() { return dummyHp; }
    public static boolean hpFixed() { return dummyHpFixed; }

    /** 지금 실제로 남아 있는 체력(첫 더미 기준). 「깎았는데 다시 찼나」를 눈으로 보려고 있다. */
    public static double currentHp() {
        prune();
        return DUMMIES.isEmpty() ? 0 : DUMMIES.get(0).getHealth();
    }

    /** 값이 0 이하면 기본값(100만)으로 되돌리고 자동 보충도 다시 켠다. */
    public static void setHp(double v) {
        dummyHpFixed = v > 0;
        dummyHp = dummyHpFixed ? v : DUMMY_HP_DEFAULT;
        prune();
        for (LivingEntity d : DUMMIES) applyHp(d);
    }

    private static void applyHp(LivingEntity d) {
        AttributeInstance a = d.getAttribute(Attributes.MAX_HEALTH);
        if (a != null) a.setBaseValue(dummyHp);
        d.setHealth((float) dummyHp);
    }

    // ── 더미가 되받아친다 (2026-08-11) ──
    // 더미는 `setNoAi(true)` 라 절대 반격하지 않는다. 측정이 흐려지지 않게 일부러 그렇게
    // 만든 것인데, 그 때문에 **맞아야 발동하는 축복 5종을 아예 못 쟀다**
    // (`barrier`·`thorns`·`resolve`·`grudge`·`sprint`).
    //
    // AI 를 주는 대신 **정해진 피해를 정해진 주기로** 넣는다. 실제 몹보다 오히려 낫다 —
    // 「1.5초마다 20」처럼 고정하면 보호막이 몇 번 터졌는지가 산수로 떨어진다. AI 를 주면
    // 이동·쿨·명중이 섞여 그 계산이 다시 추정이 된다.
    //
    // ⚠️ 피해원은 더미(공격자 엔티티)를 그대로 쓴다. `thorns`·`grudge` 는 「누가 때렸나」를
    //    보므로 공격자가 없는 피해로 넣으면 그 둘이 안 돈다.
    private static float retaliateDmg = 0f;      // 0 = 꺼짐
    private static int retaliateEvery = 30;      // 기본 1.5초
    private static long retaliateNext = 0;

    public static float retaliateDamage() { return retaliateDmg; }
    public static int retaliateInterval() { return retaliateEvery; }

    public static void setRetaliate(float dmg, int everyTicks) {
        retaliateDmg = Math.max(0f, dmg);
        retaliateEvery = Math.max(1, everyTicks);
        retaliateNext = tick + retaliateEvery;
    }

    private static final List<LivingEntity> DUMMIES = new ArrayList<>();

    // 엔티티에 저장되는 표식. 재시작을 넘어 살아남는 유일한 판별 근거다.
    private static final String DUMMY_TAG = "lsDummy";

    // 월드를 훑으려면 서버 참조가 필요한데, 명령어 쪽에는 없다. 틱 이벤트에서 받아 둔다.
    private static MinecraftServer server;

    // 표식이 붙기 전에 소환된 더미를 알아보기 위한 이름. 아래 주석 참고.
    private static final String DUMMY_NAME = "훈련 더미";

    // 월드에 있는 더미를 리스트로 다시 모은다.
    // 명령어에서만 부르므로(초당이 아니라 사람이 칠 때) 전 엔티티 순회 비용은 문제되지 않는다.
    //
    // ── 표식이 없는 옛 더미도 주워야 한다 ──
    // 표식은 소환 시점에 박히므로, 표식 도입 - 전에 - 소환돼 월드에 남아 있는 더미는
    // 표식이 없다. 그것만으로 판별하면 이미 서 있던 더미는 영원히 안 지워진다
    // (실제로 그랬다 — 수정을 배포하고 나서도 /dummy clear 가 0기를 돌려줬다).
    // 그래서 커스텀 이름으로도 알아보고, 찾으면 그 자리에서 표식을 박아 이후엔 정상 경로를 타게 한다.
    private static void rescan() {
        if (server == null) return;
        for (ServerLevel lv : server.getAllLevels()) {
            for (Entity e : lv.getAllEntities()) {
                if (!(e instanceof LivingEntity le)) continue;
                boolean tagged = e.getPersistentData().getBoolean(DUMMY_TAG);
                if (!tagged) {
                    // 옛 더미 구제 — 아이언골렘 + 그 이름이면 우리 더미다
                    if (!(e instanceof IronGolem)) continue;
                    Component name = e.getCustomName();
                    if (name == null || !name.getString().contains(DUMMY_NAME)) continue;
                    e.getPersistentData().putBoolean(DUMMY_TAG, true);   // 표식 소급 부여
                }
                if (!DUMMIES.contains(le)) DUMMIES.add(le);
            }
        }
    }

    // 더미의 방어도 — 0이면 "순수 화력"을, 보스의 실제 방어도를 넣으면 "체감 화력"을 잰다.
    // 바닐라 감쇄식은 최대 80%까지 깎으므로, 방어도를 무시한 측정값으로 보스 체력을 잡으면 크게 빗나간다.
    private static double dummyArmor = 0.0;
    private static double dummyToughness = 0.0;

    private static boolean measuring = false;
    private static long tick = 0;
    private static long startTick = 0;
    private static final Map<UUID, Float> DAMAGE = new HashMap<>();
    private static final Map<UUID, String> NAMES = new HashMap<>();

    // 스킬 이름표별 누계 (LsDamage.currentLabel()). 이름표 없는 피해는 UNLABELED 로 모인다.
    //
    // 왜: 총합만으로는 배분을 못 본다. 마총을 목표 54 에 맞추면서 세 번 빗나갔는데,
    // 매번 "총량은 맞는데 어느 스킬이 얼마를 먹었는지"를 손계산으로 추정하다 틀렸다.
    // 계측기가 이걸 직접 말해주면 그 추정 자체가 필요 없어진다.
    private static final Map<String, Float> BY_SKILL = new HashMap<>();
    private static final String UNLABELED = "평타";

    // 이름표별 타격 횟수. 피해 총합만 있으면 "한 방이 세졌다"와 "더 많이 때렸다"를 구분할 수 없다.
    // 실제로 시리우스에서 값을 x1.077 했는데 평타가 x1.42 로 뛰어 원인을 못 짚었다 —
    // 횟수가 있으면 발사 수가 늘었는지 한 발이 세졌는지 즉시 갈라진다.
    private static final Map<String, Integer> HITS = new HashMap<>();

    // 누가 어떤 유물로 쟀는지. 로그에는 스킬 이름만 남아서, 나중에 기록을 다시 볼 때
    // 어느 유물의 측정이었는지 사람에게 물어봐야 했다. 그 왕복을 없앤다.
    private static final Map<UUID, String> RELICS = new HashMap<>();

    // 시전자에게 걸려 있던 버프. 표적 방어도는 남기는데 시전자 조건은 안 남겨서,
    // 축복 안/밖에서 잰 기록이 섞이고도 사후에 구분할 수가 없었다.
    //
    // 성소 Lv4「별빛 축복」은 성역 64칸 안에서 자동으로 붙는다(ls_towneffect.js):
    //   Strength I -> 근접 공격력 +3.0 · projectile_damage +25% -> 활·총·지팡이 전부
    // 이 차이 때문에 같은 유물의 두 측정이 x1.25 넘게 벌어졌고, 원인을 찾는 데 오래 걸렸다.
    private static final Map<UUID, String> BUFFS = new HashMap<>();

    // apothic_attributes 는 외부 모드라 없을 수도 있다 — 없으면 조용히 건너뛴다.
    private static final net.minecraft.resources.ResourceLocation PROJ_ATTR =
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("apothic_attributes", "projectile_damage");

    private static String buffsOf(ServerPlayer p) {
        StringBuilder sb = new StringBuilder();
        if (p.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST)) sb.append("힘");
        var attr = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.get(PROJ_ATTR);
        if (attr != null) {
            var inst = p.getAttribute(net.minecraft.core.Holder.direct(attr));
            if (inst != null && inst.getValue() > 1.0001) {
                if (sb.length() > 0) sb.append('·');
                sb.append(String.format("투사체+%.0f%%", (inst.getValue() - 1.0) * 100));
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    // 유물 이름은 게임 내 표기(한글)를 쓴다. getHoverName() 은 서버 언어로 풀려서
    // 번역 키나 영문이 나올 수 있어, 12종을 직접 적는다.
    //
    // ⚠️ **유물을 새로 만들면 여기도 같이 추가할 것.** 빠뜨리면 null 이 되고 리포트에
    //    이름이 빈칸으로 찍힌다 — 오류가 아니라 조용히 비어서 측정 로그를 나중에 읽을 때
    //    어느 유물이었는지 알 수 없게 된다(2026-08-10 케이론 15판 측정에서 실제로 겪음).
    private static String relicName(net.minecraft.world.item.Item item) {
        if (item == LSRelics.GUNNER.get())   return "솔라리스";
        if (item == LSRelics.HUNTER.get())   return "시리우스";
        if (item == LSRelics.ASSASSIN.get()) return "스틱스";
        if (item == LSRelics.LANCER.get())   return "게볼그";
        if (item == LSRelics.HECATE.get())   return "헤스페로스";
        if (item == LSRelics.HARMONIA.get()) return "바르비톤";
        if (item == LSRelics.NEMESIS.get())  return "아드라스테이아";
        if (item == LSRelics.PIONEER.get())  return "타이탄";
        if (item == LSRelics.GUARDIAN.get()) return "이지스";
        if (item == LSRelics.SAGE.get())     return "셀레스티아";
        if (item == LSRelics.HEALER.get())   return "파나케이아";
        if (item == LSRelics.CHIRON.get())   return "펠리온";
        return null;
    }

    // ── 소환 ──
    public static int spawn(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 pos = new Vec3(player.getX() + look.x * 4.0, player.getY(), player.getZ() + look.z * 4.0);

        IronGolem dummy = new IronGolem(EntityType.IRON_GOLEM, level);
        dummy.setPos(pos.x, pos.y, pos.z);
        dummy.setNoAi(true);              // 반격·이동 없음 (골렘이 때리러 오면 측정이 망가진다)
        dummy.setSilent(true);
        dummy.setPersistenceRequired();   // 청크 언로드로 사라지지 않게
        dummy.setCustomName(Component.literal("§e훈련 더미").withStyle(ChatFormatting.YELLOW));
        dummy.setCustomNameVisible(true);

        AttributeInstance hp = dummy.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(dummyHp);
        AttributeInstance kb = dummy.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(1.0); // 밀려나면 근접이 계속 쫓아가야 해서 측정이 흐려진다
        dummy.setHealth((float) dummyHp);
        applyArmor(dummy);

        // ── 엔티티 자체에 표식을 박는다 ──
        // DUMMIES 는 메모리 리스트라 서버를 껐다 켜면 비지만, 더미는 setPersistenceRequired 라
        // 월드에 남는다. 그러면 리스트로만 판별할 때 그 더미가 통째로 투명해진다 —
        // /dummy clear 가 안 먹고, 때려도 측정에 안 잡히고, 방어도 설정도 안 먹었다.
        // persistentData 는 엔티티와 함께 저장되므로 재시작 후에도 살아 있다.
        dummy.getPersistentData().putBoolean(DUMMY_TAG, true);
        level.addFreshEntity(dummy);
        DUMMIES.add(dummy);
        return DUMMIES.size();
    }

    // ── 방어도 ──
    public static void setArmor(double armor, double toughness) {
        dummyArmor = Math.max(0, armor);
        dummyToughness = Math.max(0, toughness);
        prune();
        for (LivingEntity d : DUMMIES) applyArmor(d);
    }

    public static double armor() { return dummyArmor; }
    public static double toughness() { return dummyToughness; }

    private static void applyArmor(LivingEntity d) {
        AttributeInstance a = d.getAttribute(Attributes.ARMOR);
        if (a != null) a.setBaseValue(dummyArmor);
        AttributeInstance t = d.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (t != null) t.setBaseValue(dummyToughness);
    }

    public static int clear() {
        rescan();   // 재시작으로 리스트가 비었어도 월드에 남은 더미를 잡아낸다
        int n = 0;
        for (LivingEntity d : DUMMIES) {
            if (d != null && d.isAlive()) { d.discard(); n++; }
        }
        DUMMIES.clear();
        return n;
    }

    public static int count() {
        prune();
        return DUMMIES.size();
    }

    // ── 측정 ──
    //
    // 실시간 수치는 **보스바**에 띄운다. 예전엔 액션바를 썼는데, 액션바는 한 줄뿐이라
    // 스킬 쿨다운 표시(CooldownDisplay)를 매 틱 밀어내야 했다 — 측정 중에 정작 스킬 쿨을
    // 못 보는 게 말이 안 된다(언제 스킬을 쓸지가 DPS 측정의 핵심이다). 보스바는 상단에
    // 따로 뜨므로 둘이 겹치지 않는다.
    private static final net.minecraft.server.level.ServerBossEvent BAR =
        new net.minecraft.server.level.ServerBossEvent(Component.literal("DPS"),
            net.minecraft.world.BossEvent.BossBarColor.YELLOW,
            net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);

    // 자동 종료까지 남은 틱. 0 이면 수동 종료(기존 동작).
    private static int autoStopTicks = 0;
    private static int plannedTicks = 0;

    public static boolean start(int seconds) {
        prune();
        if (DUMMIES.isEmpty()) return false;
        DAMAGE.clear();
        NAMES.clear();
        BY_SKILL.clear();
        HITS.clear();
        RELICS.clear();
        BUFFS.clear();
        resetVitals();
        retaliateNext = tick + retaliateEvery;
        // ⚠️ **체력을 손으로 정한 동안에는 안 채운다** (2026-08-11).
        //    `executioner`(체력 25% 이하)를 재려면 «이미 깎인 상태»에서 시작할 수 있어야 한다.
        //    매번 꽉 채우면 판의 3/4 이 조건 밖이라 30% 효과가 평균 +7.5% 로 묽어지고,
        //    평타 노이즈(±4%)에 묻힌다. 다시 채우고 싶으면 `/dummy hp <값>` 을 한 번 더 친다.
        if (!dummyHpFixed) {
            for (LivingEntity d : DUMMIES) d.setHealth(d.getMaxHealth());
        }
        startTick = tick;
        measuring = true;
        plannedTicks = Math.max(0, seconds) * 20;
        autoStopTicks = plannedTicks;
        BAR.setProgress(1.0f);
        return true;
    }

    public static boolean start() { return start(0); }


    public static boolean isMeasuring() { return measuring; }

    public static float elapsedSeconds() {
        return Math.max(1, tick - startTick) / 20.0f;
    }

    public static float totalDamage() {
        float sum = 0;
        for (float v : DAMAGE.values()) sum += v;
        return sum;
    }

    // 결과 리포트 — 총합 · 실효 DPS · 인원별 기여
    public static List<Component> stop() {
        measuring = false;
        autoStopTicks = 0;
        BAR.removeAllPlayers();
        List<Component> out = new ArrayList<>();
        float secs = elapsedSeconds();
        float total = totalDamage();

        out.add(Component.literal("§6═══ ✦ DPS 측정 결과 ═══"));
        if (total <= 0) {
            out.add(Component.literal("§7기록된 피해가 없다. §8(/dummy start 후 더미를 때려야 한다)"));
            return out;
        }
        out.add(Component.literal(String.format("§7경과 §e%.1f초 §7· 총 피해 §e%,.0f", secs, total)));
        out.add(Component.literal(String.format("§7파티 실효 DPS §a§l%.1f", total / secs)));
        // 방어도 조건을 같이 남긴다 — 나중에 이 숫자가 어떤 표적 기준이었는지 알 수 있어야 한다
        out.add(Component.literal(dummyArmor <= 0
            ? "§8표적 방어도 0 §7(순수 화력 — 방어도 있는 보스엔 이보다 덜 들어간다)"
            : String.format("§8표적 방어도 %.0f · 견고함 %.0f §7(감쇄 반영된 체감 화력)", dummyArmor, dummyToughness)));
        out.add(Component.literal("§8──────────────"));

        List<Map.Entry<UUID, Float>> rows = new ArrayList<>(DAMAGE.entrySet());
        rows.sort(Comparator.<Map.Entry<UUID, Float>>comparingDouble(Map.Entry::getValue).reversed());
        for (Map.Entry<UUID, Float> e : rows) {
            String name = NAMES.getOrDefault(e.getKey(), "?");
            String relic = RELICS.get(e.getKey());
            String buffs = BUFFS.get(e.getKey());
            float dmg = e.getValue();
            out.add(Component.literal(String.format(
                "§f%-12s §d%-6s §7%,.0f §8· §e%.1f DPS §8· %.0f%%%s",
                name, relic == null ? "" : relic, dmg, dmg / secs, dmg * 100f / total,
                buffs == null ? "" : " §6[" + buffs + "]")));
        }
        // ── 스킬별 배분 ──
        //
        // 이름표가 하나뿐이면(=전부 평타) 「스킬별」이라는 제목만 군더더기가 되므로 제목은
        // 건너뛴다. **줄 자체는 반드시 낸다.**
        //   원래는 `size() > 1` 로 절을 통째로 생략했다. 그런데 콤보 배율을 보려고
        //   «스킬 없이 평타만» 재는 판이 바로 그 경우라, 제일 알고 싶은 타수·단타 평균이
        //   통째로 사라졌다(2026-08-10 네메시스·스틱스 평타 측정에서 실제로 겪음).
        //   총 DPS 만 남으면 «타수가 모자란 건지 한 대가 약한 건지»를 못 가른다.
        if (!BY_SKILL.isEmpty()) {
            if (BY_SKILL.size() > 1) out.add(Component.literal("§8──── 스킬별 ────"));
            List<Map.Entry<String, Float>> skills = new ArrayList<>(BY_SKILL.entrySet());
            skills.sort(Comparator.<Map.Entry<String, Float>>comparingDouble(Map.Entry::getValue).reversed());
            for (Map.Entry<String, Float> e : skills) {
                float dmg = e.getValue();
                int hits = HITS.getOrDefault(e.getKey(), 0);
                // 타격 수와 1회 평균을 같이 낸다 — 총합만 보면 원인을 못 짚는다
                out.add(Component.literal(String.format(
                    "§b%-8s §7%,.0f §8· §e%.1f DPS §8· %.0f%% §8· %d타 평균 %.1f",
                    e.getKey(), dmg, dmg / secs, dmg * 100f / total,
                    hits, hits > 0 ? dmg / hits : 0f)));
            }
        }

        // ── 회복·흡수 ── 축복 넷(생명흡수·치유·범람·재생)은 여기서만 보인다.
        // 0 이면 줄을 안 낸다 — 회복이 없는 유물이 대부분이라 늘 0 이면 눈이 무시하게 된다.
        float shielded = com.laststardust.relics.blessing.BlessingEffects.shieldGiven();
        if (healed > 0.05f || shielded > 0.05f || natural > 0.05f || taken > 0.05f) {
            out.add(Component.literal("§8──────────────"));
            out.add(Component.literal(String.format(
                "§d회복 §f%,.0f §8(%.1f HPS)§7 · 보호막 §f%,.0f §8(%.1f/s)",
                healed, healed / secs, shielded, shielded / secs)));
            // 자연 회복은 «우리 것이 아니다». 따로 내되 지우지는 않는다 —
            // 0 이면 「되받아치기가 안 맞고 있다」는 신호라 그것대로 값어치가 있다.
            if (natural > 0.05f) {
                out.add(Component.literal(String.format(
                    "§8자연 회복 %,.0f (%.1f HPS) — 축복 아님, 위 회복에서 제외됨",
                    natural, natural / secs)));
            }
        }
        // 되받아친 판이면 조건을 같이 남긴다 — 안 남기면 나중에 「왜 이 판만 보호막이 떴지」가 된다.
        if (retaliateDmg > 0) {
            out.add(Component.literal(String.format(
                "§8되받아치기 %.0f × %.1f초 주기 §7— 실제로 받은 피해 §f%,.0f §7· 내 최대 체력 §f%,.0f",
                retaliateDmg, retaliateEvery / 20.0f, taken, maxHpOfFirstPlayer())));
        }

        out.add(Component.literal("§8──────────────"));
        out.add(Component.literal(String.format(
            "§7보스 체력 환산 §8— 60초 §f%,.0f §8· 90초 §f%,.0f §8· 120초 §f%,.0f",
            total / secs * 60, total / secs * 90, total / secs * 120)));
        return out;
    }

    // ── 집계 ──
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!measuring) return;
        LivingEntity victim = event.getEntity();
        if (!isDummy(victim)) return;
        Entity src = event.getSource().getEntity();   // 투사체면 발사자가 잡힌다
        if (!(src instanceof ServerPlayer p)) return;
        DAMAGE.merge(p.getUUID(), event.getNewDamage(), Float::sum);
        NAMES.put(p.getUUID(), p.getGameProfile().getName());
        // 첫 타격 시점의 주손 유물로 고정한다. 마지막 것으로 덮으면, 측정이 끝나고 무기를
        // 바꾸는 순간 남아 있던 지속 피해(출혈 등)가 엉뚱한 유물 이름을 남긴다.
        if (!RELICS.containsKey(p.getUUID())) {
            String relic = relicName(p.getMainHandItem().getItem());
            if (relic != null) RELICS.put(p.getUUID(), relic);
            String buffs = buffsOf(p);
            if (buffs != null) BUFFS.put(p.getUUID(), buffs);
        }
        // 이름표는 두 곳에서 온다.
        //   1) LsDamage — 우리 코드가 직접 넣는 피해(스킬 대부분)
        //   2) 투사체에 실린 lsLabel — 화살은 바닐라 피해라 LsDamage 를 거치지 않는다.
        //      안 읽으면 별빛 폭풍·유성 사격의 직격이 통째로 평타로 잡힌다.
        // 둘 다 없으면 평타다 (근접 평타, 시리우스 별빛 화살, 법사·힐러의 별빛 탄).
        String label = LsDamage.currentLabel();
        if (label == null) {
            Entity direct = event.getSource().getDirectEntity();
            if (direct != null) {
                String tagged = direct.getPersistentData().getString("lsLabel");
                if (!tagged.isEmpty()) label = tagged;
            }
        }
        String key = label == null ? UNLABELED : label;
        BY_SKILL.merge(key, event.getNewDamage(), Float::sum);
        HITS.merge(key, 1, Integer::sum);
    }

    // ── 회복·흡수 집계 (2026-08-11) ──
    //
    // 왜 필요한가: 축복 18종 중 넷(`lifesteal`·`mend`·`overflow`·`regen`)은 **피해가 아니라
    // 회복**으로 나타난다. 리포트가 피해만 세니 그 넷은 재도 화면에 아무 변화가 없었다 —
    // 「돌긴 도는데 얼마인지 모르는」 상태였다.
    //
    // 흡수(`overflow`·`barrier`)를 따로 세는 이유: 흡수는 최대 체력을 안 넘으므로 heal 로는
    // 안 잡힌다. 둘을 합쳐 한 줄로 내면 「보호막이 안 도는」 것과 「보호막이 도는데 작은」
    // 것이 구분이 안 된다.
    private static float healed = 0f;
    private static float natural = 0f;
    private static float taken = 0f;

    static void resetVitals() {
        healed = 0f; natural = 0f; taken = 0f;
        com.laststardust.relics.blessing.BlessingEffects.resetShieldGiven();
    }

    /**
     * <b>내가 실제로 받은 피해.</b> 「가시 갑주」처럼 «받은 피해의 몇 %» 인 축복은 이게 없으면
     * 못 읽는다 — 2026-08-11 ⑤ 묶음에서 가시 10.3 이 나왔는데 분모를 몰라 해석이 막혔다.
     * 그때 리포트가 찍던 「받은 피해 120」은 <b>되받아치기 설정값으로 계산한 추정</b>이었지
     * 실측이 아니었다.
     */
    @SubscribeEvent
    public static void onPlayerHurt(LivingDamageEvent.Post event) {
        if (!measuring) return;
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        taken += event.getNewDamage();
    }

    /**
     * <b>축복·유물이 넣은 회복</b>과 <b>그 밖의 회복</b>을 갈라 센다 (2026-08-11).
     *
     * <p>가르는 기준은 {@code BlessingEffects.healingBy} 다 — 축복·유물이 넣는 회복은 전부
     * 그 안을 지나고, 바닐라 자연 회복·음식·물약은 안 지난다.
     *
     * <p><b>왜 필요한가</b>: 「재생」 축복은 케이론 기준 <b>0.34 HPS</b> 다(최대 체력 34 의 4% 를
     * 4초마다). 배경 회복이 조금이라도 섞이면 그 값은 읽을 수가 없다.
     *
     * <p>⚠️ <b>이 기능을 만든 계기는 오진이었다.</b> ⑤ 묶음 판 0 에서 회복 184 가 잡혔길래
     * 「되받아치기로 깎이니 자연 회복이 돌았구나」로 읽었는데, 실제로는 <b>지우지 않고 남아
     * 있던 생명 흡수·치유</b>였다({@code 1,371 × 0.11 × 1.22 = 184.0} — 소수점까지 맞는다).
     * 기능 자체는 옳아서 남기지만, <b>회복이 이상하면 자연 회복부터 의심하지 말고
     * {@code /bless status} 로 남은 축복부터 볼 것.</b>
     */
    @SubscribeEvent
    public static void onHeal(net.neoforged.neoforge.event.entity.living.LivingHealEvent event) {
        if (!measuring) return;
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        if (com.laststardust.relics.blessing.BlessingEffects.healAttributed()) healed += event.getAmount();
        else natural += event.getAmount();
    }


    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tick++;
        server = event.getServer();   // rescan() 이 쓸 유일한 서버 참조
        if (DUMMIES.isEmpty()) return;
        prune();
        // 더미가 죽어서 측정이 끊기지 않게 체력을 다시 채운다.
        // ⚠️ 체력을 손으로 정해둔 동안에는 보충하지 않는다 — `executioner`(체력 25% 이하)를
        //    재려고 낮게 잡았는데 20% 에서 자동으로 꽉 채우면 그 구간이 통째로 사라진다.
        if (!dummyHpFixed) {
            for (LivingEntity d : DUMMIES) {
                if (d.getHealth() < d.getMaxHealth() * REFILL_BELOW) d.setHealth(d.getMaxHealth());
            }
        }

        // ── 되받아치기 ── 측정 중에만 돈다. 밖에서 맞으면 「가만히 서 있는 과녁」이 아니게 된다.
        if (measuring && retaliateDmg > 0 && tick >= retaliateNext) {
            retaliateNext = tick + retaliateEvery;
            for (LivingEntity d : DUMMIES) {
                if (!d.isAlive()) continue;
                for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                    if (p.isSpectator() || p.isCreative()) continue;
                    if (p.distanceToSqr(d) > 256.0) continue;   // 16칸 — 재는 사람만
                    // 무적 프레임을 지운다. 1.5초 주기라 원래도 안 겹치지만, 주기를 짧게 잡고
                    // 재는 경우(보호막 재충전 확인 등)에 «가끔 안 들어오는» 게 섞이면 안 된다.
                    p.invulnerableTime = 0;
                    p.hurt(d.level().damageSources().mobAttack(d), retaliateDmg);
                }
            }
        }

        if (!measuring) return;
        MinecraftServer server = event.getServer();

        // 정해둔 시간이 되면 스스로 멈추고 결과를 전원에게 알린다.
        // 손으로 stop 을 치면 그 반응 시간이 그대로 측정 창에 섞여서, 매번 다른 길이로 재게 된다
        // (실제로 12~20초로 들쭉날쭉해 유물 간 비교가 불가능했다).
        if (autoStopTicks > 0 && --autoStopTicks <= 0) {
            for (Component line : stop()) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(line);
                // ※ 콘솔에도 남긴다. sendSystemMessage 는 플레이어 채팅으로만 가서,
                //   자동 종료로 잰 결과가 서버 로그에 한 줄도 안 남았다 — 나중에 비교하려고
                //   클라 로그를 뒤져야 했다. 측정 도구의 결과는 서버가 갖고 있어야 한다.
                LOG.info("[DPS] {}", line.getString());
            }
            return;
        }

        if (tick % 10 != 0) return;
        float secs = elapsedSeconds();
        float total = totalDamage();

        // 보스바로 띄운다 — 액션바를 비우니 스킬 쿨다운이 측정 중에도 그대로 보인다.
        BAR.setName(Component.literal(plannedTicks > 0
            ? String.format("§c● 측정 §7%.0f/%d초 §8· §7총 §e%,.0f §8· §7DPS §a%.1f",
                secs, plannedTicks / 20, total, total / secs)
            : String.format("§c● 측정 §7%.0f초 §8· §7총 §e%,.0f §8· §7DPS §a%.1f",
                secs, total, total / secs)));
        // 시간을 정했으면 남은 시간을 게이지로, 아니면 가득 채워 둔다.
        BAR.setProgress(plannedTicks > 0
            ? Math.max(0f, Math.min(1f, (float) autoStopTicks / plannedTicks))
            : 1.0f);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) BAR.addPlayer(p);
    }

    // ── 바닐라 방어 감쇄식 ──
    // 최종 = 피해 × (1 − min(20, max(방어도/5, 방어도 − 피해/(2 + 견고함/4))) / 25)
    // 감쇄는 최대 80%. 방어도를 빼고 밸런스를 잡으면 양쪽 다 크게 빗나간다.
    public static float afterArmor(float damage, double armor, double toughness) {
        if (armor <= 0) return damage;
        double reduce = Math.max(armor / 5.0, armor - damage / (2.0 + toughness / 4.0));
        reduce = Math.min(20.0, reduce);
        return (float) (damage * (1.0 - reduce / 25.0));
    }

    // 몹 공격력을 설계할 때 쓰는 표 — 같은 피해가 장비별로 얼마나 들어가는지 한눈에 본다
    public static List<Component> armorTable(float rawDamage) {
        List<Component> out = new ArrayList<>();
        out.add(Component.literal(String.format("§6═══ 피해 %.1f 이 실제로 들어가는 값 ═══", rawDamage)));
        double[][] sets = {
            {0, 0}, {7, 0}, {15, 0}, {20, 8}, {25, 10}
        };
        String[] names = {"맨몸", "가죽(스타터킷)", "철", "다이아", "네더라이트+"};
        for (int i = 0; i < sets.length; i++) {
            float f = afterArmor(rawDamage, sets[i][0], sets[i][1]);
            out.add(Component.literal(String.format(
                "§7%-16s §8방어 %.0f §7→ §e%.1f §8(−%.0f%%)",
                names[i], sets[i][0], f, (1 - f / rawDamage) * 100)));
        }
        return out;
    }

    // 표식만 본다 — 리스트를 뒤지면 재시작 후 남아 있는 더미의 피해가 통째로 누락된다.
    private static boolean isDummy(LivingEntity e) {
        return e.getPersistentData().getBoolean(DUMMY_TAG);
    }

    // 죽은 참조를 걷어내고, 월드에 남아 있는 더미를 다시 주워 온다.
    // 재시작 후에는 리스트가 비어 있으므로 이 rescan 이 유일한 복구 경로다.

    /**
     * 재는 사람의 최대 체력. 「별빛 보호막(최대 체력 8%)」·「재생(최대 체력 4%)」처럼
     * <b>최대 체력 비례</b> 인 축복은 이 값이 없으면 기대치를 못 세운다 — 2026-08-11 ⑤ 묶음에서
     * 재생 27 이 나왔는데 최대 체력을 몰라 「4배 빠른 버그인가」로 헛짚을 뻔했다.
     */
    private static float maxHpOfFirstPlayer() {
        if (server == null) return 0f;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isSpectator()) return p.getMaxHealth();
        }
        return 0f;
    }

    private static void prune() {
        Iterator<LivingEntity> it = DUMMIES.iterator();
        while (it.hasNext()) {
            LivingEntity d = it.next();
            if (d == null || d.isRemoved() || !d.isAlive()) it.remove();
        }
        rescan();
    }
}
