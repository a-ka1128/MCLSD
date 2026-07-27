package com.laststardust.relics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// ── 위협도(어그로) 시스템 ──
//
// WoW 식 위협도 테이블. 몹마다 "누가 얼마나 미웠는지"를 쌓아두고, 주기적으로 1위를
// 타겟으로 강제한다. 이게 있어야 이지스가 "버티는 무기"에서 "적을 붙잡는 탱커"가 된다.
//
// ── 왜 강제가 필요한가 ──
// 바닐라 AI(NearestAttackableTargetGoal)는 매 틱 타겟을 재평가해서 제일 가까운 사람을
// 문다. setTarget 을 한 번 부르는 것으로는 다음 틱에 덮어써진다.
// TauntManager 가 같은 문제를 "지속시간 동안 매 틱 재적용"으로 풀었고, 여기서는
// RETARGET_INTERVAL 마다 재적용한다 (매 틱은 낭비다 — 몹 AI 자체가 그렇게 민감하지 않다).
//
// ── 범위 ──
// 플레이어에게 맞은 적대 몹만 장부에 오른다. 세계의 모든 몹을 훑지 않으므로
// 비용은 "실제로 싸우는 중인 몹 수"에 비례한다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class ThreatManager {
    private ThreatManager() {}

    // 타겟 재적용 주기. 10틱(0.5초)이면 사람 눈에는 즉각적이고 비용은 20분의 1이다.
    private static final int RETARGET_INTERVAL = 10;

    // 마지막 갱신 후 이만큼 지나면 장부에서 내린다 = "전투 종료".
    // 마크에는 전투 상태라는 게 없어서 시간으로 근사한다.
    private static final int FORGET_TICKS = 300; // 15초

    // 도발은 1위보다 이만큼 위로 올려준다. 도발이 끝나도 잠시 유지되는 게 핵심이다 —
    // 지속시간만 붙잡는 도발은 끝나는 순간 어그로가 튀어 탱커가 늘 쫓아다니게 된다.
    private static final float TAUNT_OVER = 1.25f;

    // 힐 위협도 계수. 힐량 전부를 넣으면 힐러가 탱커를 이긴다.
    public static final float HEAL_FACTOR = 0.5f;

    // 이지스 평타 위협도 계수 (유저 결정: 평타만 ×2, 스킬은 그대로).
    private static final float GUARDIAN_AUTO = 2.0f;

    // 힐 위협도를 뿌릴 반경 — 이 안의 교전 중인 몹이 힐러를 미워한다.
    private static final double HEAL_RADIUS = 24.0;

    private static final class Table {
        final Map<UUID, Float> byPlayer = new HashMap<>();
        long lastUpdate;
    }

    // 몹 UUID -> 위협도 장부
    private static final Map<UUID, Table> TABLES = new HashMap<>();

    // ── 위협도 적립 ──

    public static void add(Mob mob, Player player, float amount) {
        if (mob == null || player == null || amount <= 0) return;
        if (!(mob instanceof Enemy)) return;   // 주민·골렘 등은 대상 밖
        Table t = TABLES.computeIfAbsent(mob.getUUID(), k -> new Table());
        t.byPlayer.merge(player.getUUID(), amount, Float::sum);
        t.lastUpdate = mob.level().getGameTime();
    }

    // 힐한 사람에게 주변 교전 중인 몹들의 미움을 얹는다.
    //
    // 마크의 LivingHealEvent 는 "누가 힐했는지"를 알려주지 않는다. 그래서 이 메서드를
    // 파나케이아 코드가 직접 부른다 — 우리 코드라 가능한 방식이다.
    public static void addHealThreat(ServerLevel level, Player healer, float healed) {
        if (healer == null || healed <= 0) return;
        float amount = healed * HEAL_FACTOR;
        AABB box = healer.getBoundingBox().inflate(HEAL_RADIUS);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy && m.isAlive())) {
            // 이미 싸우고 있는 몹에게만 — 힐 한 번으로 온 동네를 깨우면 안 된다
            if (!TABLES.containsKey(mob.getUUID())) continue;
            add(mob, healer, amount);
        }
    }

    // 도발 — 그 몹의 1위보다 위로 올린다. TauntManager 가 부른다.
    public static void taunt(Mob mob, Player player) {
        if (mob == null || player == null) return;
        Table t = TABLES.computeIfAbsent(mob.getUUID(), k -> new Table());
        float top = 0;
        for (float v : t.byPlayer.values()) if (v > top) top = v;
        float mine = t.byPlayer.getOrDefault(player.getUUID(), 0.0f);
        t.byPlayer.put(player.getUUID(), Math.max(mine, Math.max(top * TAUNT_OVER, 1.0f)));
        t.lastUpdate = mob.level().getGameTime();
    }

    // 은신 — 자기 위협도를 지운다. StealthManager 가 부른다.
    public static void wipe(Player player) {
        UUID id = player.getUUID();
        for (Table t : TABLES.values()) t.byPlayer.remove(id);
    }

    // 위협도 1위 (없으면 null)
    private static UUID topOf(Table t) {
        UUID best = null;
        float bestV = 0;
        for (Map.Entry<UUID, Float> e : t.byPlayer.entrySet()) {
            if (e.getValue() > bestV) { bestV = e.getValue(); best = e.getKey(); }
        }
        return best;
    }


    // ── 피해 -> 위협도 ──
    //
    // LivingDamageEvent.Post 를 쓰는 이유: 방어구·저항·흡수를 전부 지난 "실제로 박힌" 값이다.
    // 방어도 높은 몹을 때렸는데 위협도만 원래 수치로 오르면 이상하다.
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (mob.level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;

        float amount = event.getNewDamage();
        // 이지스는 평타에만 ×2. 스킬 피해는 전부 LsDamage 를 거치므로 그걸로 구분한다
        // (근접 유물은 스킬도 바닐라 피해원을 쓰기 때문에 DamageSource 로는 구분이 안 된다).
        if (!LsDamage.inSkill()
            && attacker.getMainHandItem().getItem() == LSRelics.GUARDIAN.get()) {
            amount *= GUARDIAN_AUTO;
        }
        add(mob, attacker, amount);
    }

    // ── 타겟 강제 ──
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TABLES.isEmpty()) return;
        var server = event.getServer();
        if (server.getTickCount() % RETARGET_INTERVAL != 0) return;

        Iterator<Map.Entry<UUID, Table>> it = TABLES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Table> entry = it.next();
            Mob mob = findMob(server, entry.getKey());
            if (mob == null || !mob.isAlive()) { it.remove(); continue; }
            if (mob.level().getGameTime() - entry.getValue().lastUpdate > FORGET_TICKS) {
                it.remove(); continue;   // 전투 종료
            }
            UUID topId = topOf(entry.getValue());
            if (topId == null) { it.remove(); continue; }
            ServerPlayer top = server.getPlayerList().getPlayer(topId);
            // 죽었거나 너무 멀면 장부에서 빼고 AI 에 맡긴다 — 도달 못 하는 대상을
            // 붙잡아두면 몹이 허공을 보고 서 있게 된다.
            if (top == null || !top.isAlive() || top.level() != mob.level()
                || mob.distanceToSqr(top) > 48.0 * 48.0) {
                entry.getValue().byPlayer.remove(topId);
                continue;
            }
            if (mob.getTarget() != top) mob.setTarget(top);
        }
    }

    // 서버의 모든 차원에서 UUID 로 찾는다. 장부 크기가 교전 중인 몹 수라 비용은 작다.
    private static Mob findMob(net.minecraft.server.MinecraftServer server, UUID id) {
        for (ServerLevel lv : server.getAllLevels()) {
            if (lv.getEntity(id) instanceof Mob m) return m;
        }
        return null;
    }
}
