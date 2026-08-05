package com.laststardust.relics;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// ── 공성 선봉의 「무너지는 땅」 — 어휘를 처음 만나는 자리 ──
//
// docs/RESEARCH.md 「2. 전투·보스전」은 **공성이 관문 기믹의 연습장**이어야 한다고 말한다.
// 그 자리가 여태 비어 있었다. 결과는 이랬다:
//
//   플레이어가 빨강 장판을 - 태어나서 처음 보는 순간 - 이 T1 관문 안이다.
//   즉 연습 없이 시험부터 본다. 그 시험은 실패하면 되돌아가야 하는 시험이다.
//
// 공성은 이 문제를 풀기에 알맞다. 매일 밤 오고, 죽어도 잃는 게 관문보다 적고,
// 무엇보다 - 관문을 열기 전에 - 온다. 여기서 «빨강 = 피해라»를 몸으로 배우고 나면
// T1 은 「배운 것을 확인하는 자리」가 된다.
//
// ── 왜 마지막 웨이브에만인가 ──
// 매 웨이브마다 나오면 그건 기믹이 아니라 지형이다. 마지막 웨이브는 이미 «이제 끝난다»는
// 뜻으로 읽히는 자리라, 거기 하나를 얹으면 별도 설명 없이도 «이번 밤의 마무리»가 된다.
//
// ── 왜 T1 보다 약한가 ──
// 피해 6 은 무방비 3칸이다(T1 의 「대지 가르기」는 10 = 5칸). 공성 시점의 파티는
// 유물이 없거나 1성이고 갑옷도 얇다. 가르치는 기믹은 **틀렸다는 걸 알려주면 되고,
// 죽여서 가르칠 필요는 없다.** 반경과 예고 1.5초는 T1 과 - 똑같이 - 뒀다 —
// 그 둘이 달라지면 여기서 배운 몸의 감각이 관문에서 안 맞는다.
//
// ── 소환 ──
// `ls_siege.js` 의 `spawnWave()` 가 마지막 웨이브에 표식 `ls_siege_boss` 를 달아 소환한다.
// 표식으로 거르는 이유: 광전사는 4단계 웨이브의 - 평범한 구성원이기도 하다 - .
// 종류만 보고 걸면 웨이브 전체에 장판이 깔린다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class SiegeVanguardGimmick {
    private SiegeVanguardGimmick() {}

    public static final ResourceLocation BOSS_ID =
        ResourceLocation.fromNamespaceAndPath("cataclysm", "ignited_berserker");

    // 추적 조건. `ls_siege.js` 가 같은 문자열을 쓴다 — 한쪽만 바꾸면 조용히 아무 일도 안 일어난다.
    public static final String TAG = "ls_siege_boss";

    // 시험 소환분만 가리키는 별도 표식. **위 TAG 를 그대로 쓰면 안 된다** —
    // `clearTest()` 가 지우는 건 이쪽이고, TAG 는 실전 공성 개체에도 붙어 있어서
    // 하나로 합치면 `/lsgimmick clear` 가 진행 중인 공성의 선봉을 지워버린다.
    public static final String TEST_TAG = "ls_vanguard_test";

    // ── 조절 손잡이 ──
    public static final int FIRST_DELAY = 5 * 20;   // 교전 후 첫 시전 (T1 8초보다 이르다 — 밤은 짧다)
    public static final int INTERVAL    = 11 * 20;
    public static final double RADIUS   = 4.5;      // T1 과 같게. 여기서 익힌 크기가 관문에서 맞아야 한다
    public static final double RANGE    = 24.0;
    public static final float DAMAGE    = 6.0f;     // T1 의 10 보다 낮다 — 위 주석

    private static final BossFightTracker TRACKER = new BossFightTracker(
        BOSS_ID, "균열의 선봉", TEST_TAG,
        FIRST_DELAY, new int[] { INTERVAL }, new float[] {},
        RANGE, new BossFightTracker.Handler() {

            @Override
            public void cast(ServerLevel level, BossFightTracker.Fight f, List<ServerPlayer> near) {
                ServerPlayer victim = near.get(level.random.nextInt(near.size()));
                Vec3 center = victim.position();

                if (!f.taught) {
                    f.taught = true;
                    for (ServerPlayer p : near) {
                        p.sendSystemMessage(Component.literal(
                            "§c◆ 무너지는 땅 §7— 발밑의 §c붉은 원§7에서 벗어나라."));
                        p.sendSystemMessage(Component.literal(
                            "§8   빨강은 언제나 '피해라'다. 관문에서 다시 만난다."));
                    }
                }

                level.playSound(null, f.boss.getX(), f.boss.getY(), f.boss.getZ(),
                    SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.7f, 0.8f);

                LivingEntity boss = f.boss;
                Telegraph.danger(level, center, RADIUS, () -> resolve(level, boss, center));
            }
        }).onlyTagged(TAG);

    private static void resolve(ServerLevel level, LivingEntity boss, Vec3 center) {
        if (!boss.isAlive()) return;
        for (ServerPlayer p : Telegraph.inside(level, center, RADIUS)) {
            if (!Telegraph.hittable(p, level)) continue;
            // 예고형 기믹은 결과가 결정적이어야 배울 수 있다 — 무적 프레임에 씹히면
            // «피했다/안 피했다»의 인과가 끊긴다 (WroughtnautGimmick 과 같은 이유).
            p.invulnerableTime = 0;
            p.hurt(level.damageSources().mobAttack(boss), DAMAGE);
            p.push(0.0, 0.42, 0.0);
            p.hurtMarked = true;
        }
    }

    // ── 이벤트 위임 ──

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) { TRACKER.onJoin(event); }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) { TRACKER.tick(); }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { TRACKER.stop(); }

    // ── 시험용 ──
    // 시험 소환분은 표식 둘을 다 단다 — 추적용 TAG 와 정리용 TEST_TAG.
    // `clearTest()` 는 TEST_TAG 만 보므로 실전 공성 중에 불러도 안전하다.
    public static boolean summon(ServerPlayer p) { return TRACKER.summon(p); }
    public static boolean forceNear(ServerPlayer p) { return TRACKER.forceNear(p); }
    public static int clearTest() { return TRACKER.clearTest(); }
    public static List<Component> status() { return TRACKER.status(); }
}
