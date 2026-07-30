package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// ── T1 관문 기믹: 강철거인의 「대지 가르기」 ──
//
// Telegraph 가 만든 어휘(빨강=피해라)를 실제로 가르치는 **첫 수업**이다.
// T1 이 어휘를 하나씩 가르치고 T3~T4 가 조합만 바꾼다 — docs/RESEARCH.md 「2. 전투·보스전」.
// 그래서 여기서 쓰는 건 DANGER 하나뿐이다. 첫 관문에서 두 개를 가르치면 둘 다 안 남는다.
//
// ── 왜 하필 강철거인에게, 왜 하필 원거리 장판인가 ──
// Mowzie's 의 강철거인은 **근접 전용**이다. 도끼 사거리 밖으로 나가면 아무것도 못 한다.
// 그런데 우리 유물 8종 중 4종(시리우스·솔라리스·셀레스티아·파나케이아)이 원거리라,
// 지금 T1 은 그 절반에게 "가만히 서서 쏘면 이기는 보스"다. 관문이 아니라 과녁이다.
//
// 그래서 장판은 **보스 발밑이 아니라 무작위 플레이어 발밑**에 떨어진다:
//   · 거리로 회피할 수 없다 — 원거리도 발을 뗀다
//   · 근접만 겪는 기믹이면 어휘를 파티 절반만 배운다
//   · 1.5초 예고 + 자기 발밑 = 걷기만 해도 피해진다. T1 에 맞는 난이도다.
//
// ── 자연 생성 개체에도 걸린다 ──
// 균열 제단(ls_rift.js)이 소환한 개체만이 아니라 세계에 있는 모든 강철거인이 대상이다.
// 기믹은 관문의 속성이 아니라 **그 보스의 속성**이어야 한다 — 같은 몹이 장소에 따라
// 다르게 굴면 그것이야말로 "왜 죽었는지 모르겠다"가 된다.
//
// ── 시험 ──
//   /lsgimmick summon · now · test <danger|stack|spread> · clear   (LSCommands)
@EventBusSubscriber(modid = LSRelics.MODID)
public final class WroughtnautGimmick {
    private WroughtnautGimmick() {}

    public static final ResourceLocation BOSS_ID =
        ResourceLocation.fromNamespaceAndPath("mowziesmobs", "ferrous_wroughtnaut");

    // 시험 소환에만 붙는 표식. /lsgimmick clear 가 이것만 지운다 —
    // 실전에서 만난 거인을 명령 한 줄로 날려버리면 안 된다.
    public static final String TEST_TAG = "ls_gimmick_test";

    // ── 조절 손잡이 ──
    public static final int FIRST_DELAY = 8 * 20;   // 교전 시작 후 첫 시전까지
    public static final int INTERVAL    = 14 * 20;  // 이후 주기
    public static final double RADIUS   = 4.5;      // 장판 반경
    public static final double RANGE    = 24.0;     // 표적이 될 수 있는 거리
    public static final float DAMAGE    = 10.0f;    // 감쇄 전 피해
    // T1 시점의 파티는 전원 1성 = 10칸(20HP, ls_ascend.js). 바닐라 감쇄를 통과한 실제 값:
    //   무방비 5칸 · 철(방15) 3칸 · 다이아(방20/견고8) 1.5칸
    // "한 번 맞아도 안 죽지만 두세 번 무시하면 죽는다" — 가르치는 기믹의 세기는 여기다.
    // 무방비 5칸이 과해 보이면 그건 기믹이 아니라 "T1 을 맨몸으로 왔다"는 신호다.

    // 교전이 끊긴 것으로 보고 시계를 되감기까지. 표적은 한두 틱씩 흔히 비어서,
    // null 을 보자마자 초기화하면 시전이 영영 안 온다.
    private static final int IDLE_RESET = 5 * 20;

    private static final class Fight {
        final Mob boss;
        int next = FIRST_DELAY;
        int idle = 0;
        boolean taught = false;   // 이번 전투에서 어휘를 이미 안내했는가
        Fight(Mob boss) { this.boss = boss; }
    }

    private static final List<Fight> FIGHTS = new ArrayList<>();

    // 스폰뿐 아니라 **청크 로드에서도 부른다** — 서버를 껐다 켜도 세계에 남아 있던
    // 거인이 다시 추적된다. 목록을 주기적으로 훑는 방식보다 싸고 빠짐이 없다.
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        Entity e = event.getEntity();
        if (!(e instanceof Mob mob)) return;
        if (!BOSS_ID.equals(EntityType.getKey(e.getType()))) return;
        for (Fight f : FIGHTS) if (f.boss == mob) return;   // 중복 등록 방지
        FIGHTS.add(new Fight(mob));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (FIGHTS.isEmpty()) return;
        Iterator<Fight> it = FIGHTS.iterator();
        while (it.hasNext()) {
            Fight f = it.next();
            // isRemoved 는 죽음뿐 아니라 청크 언로드도 포함한다 — 둘 다 추적을 끊는 게 맞다.
            if (f.boss.isRemoved() || !f.boss.isAlive()) { it.remove(); continue; }
            if (!(f.boss.level() instanceof ServerLevel level)) continue;

            List<ServerPlayer> near = nearby(level, f.boss);
            // 교전 전에는 시계가 흐르지 않는다. 잠든 거인 옆을 지나갔다고 도끼가 떨어지면
            // 그건 예고가 아니라 함정이다.
            if (near.isEmpty() || f.boss.getTarget() == null) {
                if (++f.idle > IDLE_RESET) { f.next = FIRST_DELAY; f.taught = false; }
                continue;
            }
            f.idle = 0;
            if (--f.next > 0) continue;
            f.next = INTERVAL;
            cast(level, f, near);
        }
    }

    // 서버가 멈추면 목록을 비운다. 죽은 레벨의 엔티티를 붙들고 있으면
    // 싱글(내장 서버)에서 월드를 갈아탈 때 그대로 새 세계로 새어 들어간다.
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FIGHTS.clear();
    }

    private static List<ServerPlayer> nearby(ServerLevel level, Mob boss) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (p.distanceToSqr(boss) <= RANGE * RANGE) out.add(p);
        }
        return out;
    }

    private static void cast(ServerLevel level, Fight f, List<ServerPlayer> near) {
        ServerPlayer victim = near.get(level.random.nextInt(near.size()));
        Vec3 center = victim.position();

        if (!f.taught) {
            f.taught = true;
            for (ServerPlayer p : near) {
                p.sendSystemMessage(Component.literal(
                    "§c◆ 대지 가르기 §7— 발밑의 §c붉은 원§7에서 벗어나라. §8(빨강은 언제나 '피해라'다)"));
            }
        }

        // Telegraph 가 장판 자리에서 종을 울리지만, 원인이 거인이라는 걸 알 수 있게
        // 거인 자리에서도 한 번 울린다. 소리의 출처가 곧 "누가 시전했나"다.
        level.playSound(null, f.boss.getX(), f.boss.getY(), f.boss.getZ(),
            SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.7f, 0.6f);

        LivingEntity boss = f.boss;
        Telegraph.danger(level, center, RADIUS, () -> resolve(level, boss, center));
    }

    private static void resolve(ServerLevel level, LivingEntity boss, Vec3 center) {
        // 거인이 먼저 쓰러지면 도끼도 멈춘다. 죽은 보스에게 맞는 건 납득되지 않는다.
        if (!boss.isAlive()) return;
        for (ServerPlayer p : Telegraph.inside(level, center, RADIUS)) {
            if (p.isSpectator()) continue;
            // 1.5초를 줬는데 무적 프레임에 씹히면 "피했다/안 피했다"의 인과가 끊긴다.
            // 예고형 기믹은 결과가 결정적이어야 배울 수 있다. 14초에 한 번이라
            // LsDamage 주석이 경고하는 "지속 장판이 20배가 되는" 경우와는 다르다.
            p.invulnerableTime = 0;
            p.hurt(level.damageSources().mobAttack(boss), DAMAGE);
            p.push(0.0, 0.42, 0.0);   // 솟구치는 지면 — 맞았다는 걸 몸으로 안다
            p.hurtMarked = true;      // 서버가 준 속도를 클라이언트에 반영시킨다
        }
    }

    // ── 시험용 ──

    public static int tracked() { return FIGHTS.size(); }

    // 가장 가까운 강철거인이 지금 시전한다. 8초+14초를 기다리며 검증할 수는 없다.
    public static boolean forceNear(ServerPlayer player) {
        Fight best = null;
        double bestDist = Double.MAX_VALUE;
        for (Fight f : FIGHTS) {
            if (f.boss.isRemoved() || !f.boss.isAlive()) continue;
            if (f.boss.level() != player.level()) continue;
            double d = f.boss.distanceToSqr(player);
            if (d < bestDist) { bestDist = d; best = f; }
        }
        if (best == null || !(best.boss.level() instanceof ServerLevel level)) return false;
        List<ServerPlayer> near = nearby(level, best.boss);
        if (near.isEmpty()) near = List.of(player);   // 사거리 밖에서 불러도 시전자는 본다
        best.next = INTERVAL;
        cast(level, best, near);
        return true;
    }

    // 시험 소환은 **관문과 똑같은 경로**를 탄다(ls_rift.js 의 summon 명령과 같은 형태).
    // 자바에서 직접 스폰하면 KubeJS 의 EntityEvents.spawned 를 타는지가 달라져,
    // /bossdiff 배율이 빠진 개체로 시험하게 된다 — 시험이 실전과 달라지는 전형적인 함정.
    public static boolean summon(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        Vec3 look = player.getViewVector(1.0f);
        double x = player.getX() + look.x * 6.0;
        double z = player.getZ() + look.z * 6.0;
        // Locale.ROOT 고정 — 소수점이 쉼표인 지역 설정에서는 "12,34" 가 되어 명령이 통째로 깨진다.
        String cmd = String.format(java.util.Locale.ROOT,
            "summon %s %.2f %.2f %.2f {Tags:[\"%s\"],PersistenceRequired:1b}",
            BOSS_ID, x, player.getY(), z, TEST_TAG);
        server.getCommands().performPrefixedCommand(
            player.createCommandSourceStack().withPermission(2).withSuppressedOutput(), cmd);
        return true;
    }

    // 시험 소환분만 지운다. 표식이 없는 개체 — 즉 세계에서 만난 거인 — 은 건드리지 않는다.
    public static int clearTest() {
        int n = 0;
        Iterator<Fight> it = FIGHTS.iterator();
        while (it.hasNext()) {
            Fight f = it.next();
            if (!f.boss.getTags().contains(TEST_TAG)) continue;
            if (f.boss.isAlive()) { f.boss.discard(); n++; }
            it.remove();
        }
        return n;
    }

    // 추적 중인 개체마다 "교전 중인가 / 다음 시전까지 몇 초인가"
    public static List<Component> status() {
        List<Component> out = new ArrayList<>();
        if (FIGHTS.isEmpty()) {
            out.add(Component.literal("§7추적 중인 강철거인이 없습니다. §8(/lsgimmick summon)"));
            return out;
        }
        for (Fight f : FIGHTS) {
            boolean engaged = f.boss.getTarget() != null;
            out.add(Component.literal(String.format(
                "§7강철거인 §8(%.0f, %.0f, %.0f)%s §8· %s",
                f.boss.getX(), f.boss.getY(), f.boss.getZ(),
                f.boss.getTags().contains(TEST_TAG) ? " §8[시험]" : "",
                engaged ? String.format("§c교전 중 §7— 다음 시전 §e%.1f초", f.next / 20.0f) : "§8대기(비교전)")));
        }
        return out;
    }
}
