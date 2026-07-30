package com.laststardust.relics;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
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
//   /lsgimmick summon · now · window · test <danger|stack|spread> · clear   (LSCommands)
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

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    // ── 취약 창: 이 보스는 «언제·어디서»가 전부다 ──
    //
    // Mowzie's 의 EntityWroughtnaut.hurt() 에는 게이트가 셋 있다:
    //   ① public boolean vulnerable — 세로 내려찍기 애니메이션 27~84틱(2.9초) 동안만 true.
    //      그 밖에는 근접·원거리·스킬 전부 hurt() 가 false 를 돌려주고 «깡» 소리만 난다.
    //   ② 각도 — 정면 220° 는 갑옷이 막고 뒤쪽 140° 만 유효.
    //      기준은 - 공격자 엔티티의 위치 - 다(화살이 아니라 쏜 사람). 그래서 원거리도 등 뒤로 가야 한다.
    //   ③ 공격자 엔티티가 없는 피해는 BYPASSES_INVULNERABILITY 태그가 없으면 통째로 무효.
    //
    // 즉 «도끼를 땅에 박은 직후 2.9초, 등 뒤에서»만 맞는 패링형 보스인데,
    // - 그걸 알려주는 신호가 게임 안에 하나도 없었다 - . 막혔을 때 나는 소리만 있고 열렸다는
    // 신호가 없으니 플레이어에겐 그냥 «안 들어가는 보스»다. 여기서 그 창을 눈에 보이게 만든다.
    //
    // ※ ls_config.js 의 T1 절대 체력은 딜 유지율 0.45 를 가정한 값이다.
    //   창이 안 보이면 그 가정이 안 지켜지므로, 이 표시는 체력 수치의 전제이기도 하다.
    private static final double BAND_INNER = 1.8;
    private static final double BAND_OUTER = 3.6;

    private static java.lang.reflect.Field VULNERABLE;
    private static boolean vulnBroken = false;

    // 애니메이션 틱을 세는 대신 필드를 직접 읽는다 — 모드가 틱 번호를 바꿔도 따라간다.
    // 리플렉션인 이유는 Mowzie's 가 컴파일 의존이 아니기 때문이다.
    private static boolean isOpen(Mob boss) {
        if (vulnBroken) return false;
        try {
            if (VULNERABLE == null) VULNERABLE = boss.getClass().getField("vulnerable");
            return VULNERABLE.getBoolean(boss);
        } catch (Exception e) {
            // 한 번 실패하면 매 틱 예외를 만들지 않는다. 표시만 꺼지고 기믹은 계속 돈다.
            vulnBroken = true;
            LOG.warn("[강철거인] vulnerable 필드를 못 읽는다 — 취약 창 표시가 꺼진다. Mowzie's 버전이 바뀌었나?", e);
            return false;
        }
    }

    // ── 실제로 딜이 들어가는 상대 각도 ──
    // 그냥 «뒤쪽 110~250°»가 아니다. Mowzie's 의 각도 비교에 랩어라운드 처리가 빠져 있어서,
    // - 보스가 어느 방향을 보고 있느냐에 따라 뒤쪽 140° 중 일부가 실제로는 안 들어간다 - .
    // (attackerAngle < bodyAngle 로 차가 음수가 되는 구간에서 판정식이 달라진다)
    //
    // 그 구멍까지 그대로 그린다. 편한 쪽으로 뭉뚱그려 «뒤쪽 전부»를 초록으로 칠하면
    // «초록에 서 있는데 안 들어간다»가 되는데, 그건 신호가 없는 것보다 나쁘다.
    private static float[][] damageArcs(float bodyRot) {
        float b = ((bodyRot % 360f) + 360f) % 360f;
        float t = 360f - b;                                   // 여기서부터 모드 계산이 음수로 넘어간다
        // 부등호가 t > 185 인 이유: t == 185 일 때 상대각 185° 는 모드 쪽에서 d == -175 가 되어
        // - 딱 그 점만 - 배제된다(그쪽 조건이 d > -175). >= 로 두면 그 한 점을 초록으로 칠한다.
        // 실제로 밟을 일은 거의 없지만, 아래 canHit 과 집합이 정확히 같아야 한 규칙이라고 말할 수 있다.
        if (t > 185f)  return new float[][] { { 110f, 250f } };
        if (t <= 110f) return new float[][] { { 185f, 250f } };
        return new float[][] { { 110f, t }, { 185f, 250f } };  // 가운데가 뚫린다
    }

    // 위 판정을 그대로 옮긴 것. 그리는 각도와 안내 문구가 갈리면 안 되므로 한 규칙만 쓴다.
    private static boolean canHit(Mob boss, ServerPlayer p) {
        double a = Math.toDegrees(Math.atan2(p.getZ() - boss.getZ(), p.getX() - boss.getX())) - 90.0;
        float att = (float) (((a % 360) + 360) % 360);
        float body = ((boss.yBodyRot % 360f) + 360f) % 360f;
        float d = att - body;
        return d >= 0 ? (d > 110f && d < 250f) : (d > -175f && d < -110f);
    }

    // ── 이 기믹만 쓰는 상태 ──
    // 「직전 틱에 창이 열려 있었나」와 「시험 명령으로 강제로 켜둔 시각」은 나머지 네 보스에게
    // 없는 개념이라 Fight 의 공용 필드로 올리지 않는다. BossFightTracker.Fight.data 에 담는다.
    private static final class Window {
        boolean wasOpen = false;
        long forcedUntil = 0;
    }

    private static Window win(BossFightTracker.Fight f) {
        if (f.data instanceof Window w) return w;
        Window w = new Window();
        f.data = w;
        return w;
    }

    private static final BossFightTracker TRACKER = new BossFightTracker(
        BOSS_ID, "강철거인", TEST_TAG,
        FIRST_DELAY, new int[] { INTERVAL }, new float[] {},   // 페이즈 없음 — T1 은 한 어휘만 가르친다
        RANGE, new BossFightTracker.Handler() {

            // 취약 창은 교전 여부와 무관하다 — 그래서 onTick 이 아니라 onTickAlways 다.
            // 시험 소환 직후(표적 없음)에 /lsgimmick window 로 띠를 확인하는 게 정확히 이 경우다.
            @Override
            public void onTickAlways(ServerLevel level, BossFightTracker.Fight f) {
                Window w = win(f);
                long tick = level.getServer().getTickCount();
                boolean open = isOpen(f.boss) || tick < w.forcedUntil;
                if (open != w.wasOpen) {
                    w.wasOpen = open;
                    Telegraph.announce(level, f.boss.position(), RANGE, Telegraph.Kind.OPENING, open);
                }
                // 2틱마다면 충분하다. 매 틱 그리면 파티클만 두 배가 되고 눈에 보이는 차이는 없다.
                if (!open || tick % 2 != 0) return;
                float body = f.boss.yBodyRot;
                for (float[] arc : damageArcs(body)) {
                    Telegraph.band(level, f.boss.position(), Telegraph.Kind.OPENING,
                        body + arc[0], body + arc[1], BAND_INNER, BAND_OUTER, 20);
                }
            }

            @Override
            public void cast(ServerLevel level, BossFightTracker.Fight f, List<ServerPlayer> near) {
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
        });

    private static void resolve(ServerLevel level, LivingEntity boss, Vec3 center) {
        // 거인이 먼저 쓰러지면 도끼도 멈춘다. 죽은 보스에게 맞는 건 납득되지 않는다.
        if (!boss.isAlive()) return;
        for (ServerPlayer p : Telegraph.inside(level, center, RADIUS)) {
            if (!Telegraph.hittable(p, level)) continue;
            // 1.5초를 줬는데 무적 프레임에 씹히면 "피했다/안 피했다"의 인과가 끊긴다.
            // 예고형 기믹은 결과가 결정적이어야 배울 수 있다. 14초에 한 번이라
            // LsDamage 주석이 경고하는 "지속 장판이 20배가 되는" 경우와는 다르다.
            p.invulnerableTime = 0;
            p.hurt(level.damageSources().mobAttack(boss), DAMAGE);
            p.push(0.0, 0.42, 0.0);   // 솟구치는 지면 — 맞았다는 걸 몸으로 안다
            p.hurtMarked = true;      // 서버가 준 속도를 클라이언트에 반영시킨다
        }
    }

    // ── 왜 안 들어가는지 말해준다 ──
    // hurt() 가 false 를 돌려줄 때는 super 를 안 부르므로 NeoForge 의 피해 이벤트가 아예 안 열린다.
    // «막혔다»를 피해 쪽에서 잡을 방법이 없다는 뜻이다. 대신 - 때리려는 순간 - 을 잡는다.
    // 초록 띠가 «어디»는 이미 말해주지만, 창이 닫혀 있을 때 헛스윙하는 사람에게는
    // 그게 타이밍 문제라는 걸 알려줄 것이 아무것도 없다.
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof Mob mob)) return;
        if (!BOSS_ID.equals(EntityType.getKey(mob.getType()))) return;
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        boolean rear = canHit(mob, p);
        if (rear && isOpen(mob)) return;   // 제대로 치고 있다 — 방해하지 않는다

        // 휘두를 때마다 띄우면 소음이 된다. 2초에 한 번.
        long now = p.level().getGameTime();
        if (now - p.getPersistentData().getLong("lsWnHint") < 40) return;
        p.getPersistentData().putLong("lsWnHint", now);
        p.displayClientMessage(Component.literal(rear
            ? "§7도끼가 땅에 박히기 전에는 튕긴다. §8초록이 뜨면 그때."
            : "§7정면은 갑옷이 막는다. §8등 뒤로 돌아라."), true);
    }

    // ── 이벤트 위임 (BossFightTracker 주석 참고: 구독은 기믹이 한다) ──

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) { TRACKER.onJoin(event); }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) { TRACKER.tick(); }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { TRACKER.stop(); }

    // ── 시험용 ──

    public static boolean summon(ServerPlayer p) { return TRACKER.summon(p); }
    public static boolean forceNear(ServerPlayer p) { return TRACKER.forceNear(p); }
    public static int clearTest() { return TRACKER.clearTest(); }
    public static int tracked() { return TRACKER.tracked(); }
    public static List<Component> status() { return TRACKER.status(); }

    // 취약 창을 2.9초 동안 강제로 켠다 (표시만 — 실제 무적은 모드 소관이라 안 건드린다).
    // 진짜 창은 보스가 내려찍을 때만 열리는데, 그걸 기다려서는 «띠가 제대로 보이는가»를
    // 검증할 수 없다. 색·위치·소리는 이걸로 보고, 타이밍은 실전에서 본다.
    public static boolean forceWindow(ServerPlayer player) {
        BossFightTracker.Fight f = TRACKER.nearest(player);
        if (f == null || player.getServer() == null) return false;
        // ※ 반드시 onTickAlways 와 - 같은 시계 - 를 써야 한다. getGameTime()(월드 시간)과
        //   getTickCount()(서버 기동 후 틱)는 서로 다른 수라, 섞으면 창이 영영 안 열리거나
        //   영영 안 닫힌다.
        win(f).forcedUntil = player.getServer().getTickCount() + 58;   // 27~84틱 = 2.9초
        return true;
    }
}
