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

    private static final double DUMMY_HP = 1_000_000.0; // AttributeCaps 가 상한을 풀어둬서 가능
    private static final double REFILL_BELOW = 0.2;     // 20% 밑으로 떨어지면 다시 채운다

    private static final List<LivingEntity> DUMMIES = new ArrayList<>();

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
        if (hp != null) hp.setBaseValue(DUMMY_HP);
        AttributeInstance kb = dummy.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(1.0); // 밀려나면 근접이 계속 쫓아가야 해서 측정이 흐려진다
        dummy.setHealth((float) DUMMY_HP);
        applyArmor(dummy);

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
        for (LivingEntity d : DUMMIES) d.setHealth(d.getMaxHealth());
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
            float dmg = e.getValue();
            out.add(Component.literal(String.format(
                "§f%-12s §7%,.0f §8· §e%.1f DPS §8· %.0f%%",
                name, dmg, dmg / secs, dmg * 100f / total)));
        }
        // ── 스킬별 배분 ──
        // 이름표가 하나뿐이면(=전부 평타) 줄만 늘어나므로 생략한다.
        if (BY_SKILL.size() > 1) {
            out.add(Component.literal("§8──── 스킬별 ────"));
            List<Map.Entry<String, Float>> skills = new ArrayList<>(BY_SKILL.entrySet());
            skills.sort(Comparator.<Map.Entry<String, Float>>comparingDouble(Map.Entry::getValue).reversed());
            for (Map.Entry<String, Float> e : skills) {
                float dmg = e.getValue();
                out.add(Component.literal(String.format(
                    "§b%-8s §7%,.0f §8· §e%.1f DPS §8· %.0f%%",
                    e.getKey(), dmg, dmg / secs, dmg * 100f / total)));
            }
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
        // 이름표가 없으면 평타다 — 바닐라 근접 공격은 LsDamage 를 거치지 않고, 아직
        // 이름표를 안 붙인 스킬도 여기로 모인다(마총 외 유물은 대부분 아직 그렇다).
        String label = LsDamage.currentLabel();
        BY_SKILL.merge(label == null ? UNLABELED : label, event.getNewDamage(), Float::sum);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tick++;
        if (DUMMIES.isEmpty()) return;
        prune();
        // 더미가 죽어서 측정이 끊기지 않게 체력을 다시 채운다
        for (LivingEntity d : DUMMIES) {
            if (d.getHealth() < d.getMaxHealth() * REFILL_BELOW) d.setHealth(d.getMaxHealth());
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

    private static boolean isDummy(LivingEntity e) {
        for (LivingEntity d : DUMMIES) {
            if (d == e) return true;
        }
        return false;
    }

    private static void prune() {
        Iterator<LivingEntity> it = DUMMIES.iterator();
        while (it.hasNext()) {
            LivingEntity d = it.next();
            if (d == null || d.isRemoved() || !d.isAlive()) it.remove();
        }
    }
}
