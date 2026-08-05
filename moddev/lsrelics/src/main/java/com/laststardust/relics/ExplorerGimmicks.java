package com.laststardust.relics;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// ── 탐험 보스의 숨은 규칙을 말해준다 ──
//
// 관문 보스 넷과 최종은 - 어휘를 가르친다 - . 우리가 장판을 깔고, 플레이어가 그 색을 읽고 움직인다.
// 여기 넷은 다르다. **우리가 일으키는 일이 하나도 없다.** 모드가 이미 가진 규칙이 있는데
// 그걸 알리는 신호가 게임 안에 없을 뿐이다 — 강철거인의 취약 창과 정확히 같은 종류의 문제다.
//
// 그래서 이 파일에는 `cast` 가 없다. `BossFightTracker.signalOnly()` 로 만들어 `onTickAlways`
// 만 쓴다. 시험 소환·주기·페이즈가 없는 것도 같은 이유다 — 앞당길 시전이 애초에 없다.
//
// ── 왜 다섯 색을 다 안 쓰는가 ──
// 초록(지금 쳐라)만 쓴다. 그 색의 뜻이 여기 규칙과 정확히 겹치기 때문이다(히드라의 벌린 입,
// 유령기사의 돌진 구간). 나머지 넷은 «어디에 설 것인가»를 말하는데 여기엔 그런 요구가 없다.
// 뜻이 안 맞는데 색만 갖다 쓰면 관문에서 쌓아 둔 어휘가 그만큼 흐려진다.
// 우르가스트·프로스트모는 색 없이 글자로만 알린다 — 자세가 아니라 - 사실 - 이라서다.
//
// ── 규칙은 전부 javap 로 확인했다 (2026-07-31) ──
// 문서에 없고 설정으로도 안 보이는 것들이라, 바이트코드를 읽는 것 말고는 알 방법이 없었다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class ExplorerGimmicks {
    private ExplorerGimmicks() {}

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    public static final double RANGE = 28.0;   // 교전으로 볼 거리 (히드라가 크다)

    // 안내문을 띄우는 간격. 같은 사실을 매초 띄우면 그건 안내가 아니라 소음이다.
    private static final int HINT_COOLDOWN = 60;   // 3초

    private static boolean hintReady(ServerPlayer p, String key) {
        long now = p.level().getGameTime();
        if (now - p.getPersistentData().getLong(key) < HINT_COOLDOWN) return false;
        p.getPersistentData().putLong(key, now);
        return true;
    }

    // ── 리플렉션 공통 ──
    // 네 모드 전부 컴파일 의존이 아니다. 한 번 실패하면 그 신호만 끄고 나머지는 계속 돈다 —
    // 모드가 업데이트되며 이름이 바뀌었다고 서버가 매 틱 예외를 만들면 안 된다.
    private static final class Ref {
        final String what;
        boolean broken = false;
        Ref(String what) { this.what = what; }
        void fail(Exception e) {
            if (broken) return;
            broken = true;
            LOG.warn("[탐험 보스] {} 를 못 읽는다 — 그 신호만 꺼진다. 모드 버전이 바뀌었나?", what, e);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 1. 프로스트모 — 「화살이 통하지 않는다」
    // ══════════════════════════════════════════════════════════════════
    //
    // EntityFrostmaw.hurt() 가 하는 일:
    //   ① IS_FALL          → return false            (낙하 무효)
    //   ② IS_FIRE          → 피해 × 1.25             (불이 잘 든다)
    //   ③ getDirectEntity() instanceof AbstractArrow → 「깡」 소리 + return false
    //
    // ③ 이 문제다. **화살은 피해가 0이다.** 각도도 타이밍도 아니고 그냥 안 들어간다.
    // 우리 유물 중 시리우스(별빛 사격·유성 사격)와 화살 폭풍이 전부 진짜 Arrow 엔티티라
    // (`LsArrows.create`), 시리우스 한 명은 이 보스에게 **아무것도 못 한다.**
    // 강철거인 때와 같은 구조인데 그보다 나쁘다 — 그쪽은 등 뒤로 돌면 되지만 여기는 답이 없다.
    //
    // 신호가 없으면 «내 딜이 왜 0이지»를 알아낼 방법이 플레이어에게 전혀 없다.
    // 그래서 화살이 맞는 순간 쏜 사람에게 직접 말한다.
    public static final ResourceLocation FROSTMAW =
        ResourceLocation.fromNamespaceAndPath("mowziesmobs", "frostmaw");

    private static final BossFightTracker FROSTMAW_T = BossFightTracker.signalOnly(
        FROSTMAW, "프로스트모", RANGE, new BossFightTracker.Handler() {
            @Override
            public void onTick(ServerLevel level, BossFightTracker.Fight f, List<ServerPlayer> near) {
                if (f.taught) return;
                f.taught = true;
                for (ServerPlayer p : near) {
                    p.sendSystemMessage(Component.literal(
                        "§b◆ 프로스트모 §7— §c불§7이 더 잘 든다(×1.25). §8그리고 화살은 통하지 않는다."));
                }
            }

            // 교전 여부와 무관하게 본다 — 멀리서 쏘는 사람에게도 알려야 한다.
            // 그 사람은 «맞고 있는데 체력이 안 준다»를 보고 있는 중이다.
            @Override
            public void onTickAlways(ServerLevel level, BossFightTracker.Fight f) {
                frostmawArrowHint(level, f.boss);
            }
        });

    // ── 화살이 프로스트모에 닿는 순간 ──
    //
    // 피해 이벤트로는 못 잡는다 — `hurt()` 가 false 를 돌려주면 super 를 안 부르므로
    // NeoForge 의 피해 이벤트가 아예 안 열린다. **그게 바로 알리려는 사실이다**(피해가 0이다).
    //
    // **2026-08-04: ProjectileImpactEvent 를 버렸다.** 그 이벤트는 이 모드팩에서 한 번도
    // 발화하지 않는다(`RelicEventHandlers` 머리말에 증거). 07-31 에 이걸로 만들어 두고
    // 「미검증」으로 넘겼는데, 검증했다면 아무것도 안 뜨는 걸 봤을 것이다.
    //
    // 그래서 이벤트를 기다리지 않고 **보스 곁을 직접 본다.** 트래커가 이 보스를 좇는 동안
    // 매 틱 반경 3칸 안의 화살을 훑는다. 비싸 보이지만 이 보스가 교전 중일 때만 도는 데다
    // 범위가 좁아, 다른 화살 훅처럼 «모든 화살»에 비용을 물리지 않는다.
    private static void frostmawArrowHint(ServerLevel level, Entity boss) {
        for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class,
                boss.getBoundingBox().inflate(1.5))) {
            if (!(arrow.getOwner() instanceof ServerPlayer p)) continue;
            if (!hintReady(p, "lsFmArrow")) continue;
            p.displayClientMessage(Component.literal(
                "§7화살은 이 짐승에게 통하지 않는다. §8불을 쓰거나 붙어라."), true);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. 트와일라잇 히드라 — 「벌린 입만 아프다」
    // ══════════════════════════════════════════════════════════════════
    //
    // Hydra.attackEntityFromPart() 의 판정 순서:
    //   ① 죽은 머리 / 죽은 머리에 붙은 목  → return false      (무피해)
    //   ② 거리² > 400 (20칸)               → return false      (Mortar 직격이면 600)
    //   ③ head.getCurrentMouthOpen() > 0.5 → 피해 그대로
    //   ④ 그 밖                            → round(피해 / 8)   ← 여덟 토막
    //
    // ④ 가 이 보스의 정체다. 입을 안 벌린 머리를 때리면 딜이 8분의 1이 된다. 반올림이라
    // 평타 7이면 1이 된다. 그런데 **그걸 알리는 신호가 없다** — 소리도 색도 같다.
    //
    // 머리가 일곱이고 각자 따로 입을 벌린다. 그래서 초록은 보스 몸이 아니라 - 머리마다 - 뜬다.
    // 「지금 쳐라」의 뜻이 여기서는 «지금 이 머리를»이 된다. 색의 뜻은 그대로다.
    public static final ResourceLocation HYDRA =
        ResourceLocation.fromNamespaceAndPath("twilightforest", "hydra");

    public static final double HYDRA_MAX_DIST = 20.0;   // 거리² 400
    private static final double HEAD_AURA_R = 1.4;
    private static final double HEAD_AURA_H = 1.2;

    private static final Ref HYDRA_REF = new Ref("히드라의 머리 상태");
    private static Field HC_FIELD;          // Hydra.hc          (public final)
    private static Field HEAD_ENTITY;       // HydraHeadContainer.headEntity (public final)
    private static Method MOUTH_OPEN;       // getCurrentMouthOpen()  (protected)
    private static Method HEAD_IS_DEAD;     // isDead()               (public)

    // 지금 «온전한 피해»가 들어가는 머리들의 위치. 못 읽으면 빈 목록 — 신호만 꺼진다.
    private static List<Vec3> openMouths(Entity hydra) {
        List<Vec3> out = new ArrayList<>();
        if (HYDRA_REF.broken) return out;
        try {
            if (HC_FIELD == null) {
                HC_FIELD = hydra.getClass().getField("hc");
                Object[] probe = (Object[]) HC_FIELD.get(hydra);
                Class<?> container = probe.getClass().getComponentType();
                HEAD_ENTITY = container.getField("headEntity");
                MOUTH_OPEN = container.getDeclaredMethod("getCurrentMouthOpen");
                MOUTH_OPEN.setAccessible(true);          // protected 다
                HEAD_IS_DEAD = container.getMethod("isDead");
            }
            for (Object c : (Object[]) HC_FIELD.get(hydra)) {
                if (c == null) continue;
                if ((Boolean) HEAD_IS_DEAD.invoke(c)) continue;
                if ((Float) MOUTH_OPEN.invoke(c) <= 0.5f) continue;
                Object head = HEAD_ENTITY.get(c);
                if (head instanceof Entity e) out.add(e.position());
            }
        } catch (Exception e) {
            HYDRA_REF.fail(e);
            out.clear();
        }
        return out;
    }

    private static final BossFightTracker HYDRA_T = BossFightTracker.signalOnly(
        HYDRA, "히드라", RANGE, new BossFightTracker.Handler() {

            @Override
            public void onTickAlways(ServerLevel level, BossFightTracker.Fight f) {
                // 2틱마다면 충분하다 — 강철거인 띠와 같은 이유.
                if (level.getServer().getTickCount() % 2 != 0) return;
                for (Vec3 mouth : openMouths(f.boss)) {
                    Telegraph.aura(level, mouth, Telegraph.Kind.OPENING, HEAD_AURA_R, HEAD_AURA_H, 3);
                }
            }

            @Override
            public void onTick(ServerLevel level, BossFightTracker.Fight f, List<ServerPlayer> near) {
                if (!f.taught) {
                    f.taught = true;
                    for (ServerPlayer p : near) {
                        p.sendSystemMessage(Component.literal(
                            "§6◆ 히드라 §7— §a초록으로 빛나는 머리§7만 온전히 아프다. §8나머지는 8분의 1이다."));
                    }
                }
                // 20칸 밖은 피해가 통째로 무효다. 원거리 넷에게는 이쪽이 더 중요한 규칙인데,
                // 화면상 «맞고 있는» 것처럼 보여서 스스로는 절대 못 알아챈다.
                for (ServerPlayer p : near) {
                    if (p.distanceToSqr(f.boss) <= HYDRA_MAX_DIST * HYDRA_MAX_DIST) continue;
                    if (!hintReady(p, "lsHydraFar")) continue;
                    p.displayClientMessage(Component.literal(
                        "§7너무 멀다 §8— 20칸 밖에서는 피해가 들어가지 않는다."), true);
                }
            }
        });

    // ══════════════════════════════════════════════════════════════════
    // 3. 트와일라잇 유령기사 — 「돌진할 때만 갑옷이 벗겨진다」
    // ══════════════════════════════════════════════════════════════════
    //
    // KnightPhantom 은 상태에 따라 속성 두 개를 갈아 끼운다:
    //   · 돌진 아님 → ARMOR 에 inactive_armor_boost (+4.0 ADD_MULTIPLIED_TOTAL) = 방어도 5배
    //   · 돌진 중   → 그 방어도 보정을 - 떼고 - ATTACK_DAMAGE 에 +7.0
    //
    // 즉 «달려들 때가 유일하게 딜이 제대로 들어가는 순간이고, 동시에 제일 아픈 순간»이다.
    // 이건 설계가 잘된 규칙인데 - 게임 안에 표시가 없다 - . 갑옷이 5배일 때와 아닐 때
    // 타격음도 이펙트도 똑같아서, 플레이어는 «가끔 딜이 잘 박히네» 정도로만 느낀다.
    //
    // 초록이 정확히 이 뜻이다: 지금 쳐라. 물러설 것인가 맞으면서 칠 것인가는 플레이어가 정한다 —
    // 신호가 있어야 그게 - 선택 - 이 되고, 없으면 그냥 운이다.
    public static final ResourceLocation KNIGHT_PHANTOM =
        ResourceLocation.fromNamespaceAndPath("twilightforest", "knight_phantom");

    private static final Ref KP_REF = new Ref("유령기사의 돌진 상태");
    private static Method IS_CHARGING;

    private static boolean isCharging(Entity knight) {
        if (KP_REF.broken) return false;
        try {
            if (IS_CHARGING == null) IS_CHARGING = knight.getClass().getMethod("isChargingAtPlayer");
            return (Boolean) IS_CHARGING.invoke(knight);
        } catch (Exception e) {
            KP_REF.fail(e);
            return false;
        }
    }

    private static final BossFightTracker KNIGHT_T = BossFightTracker.signalOnly(
        KNIGHT_PHANTOM, "유령기사", RANGE, new BossFightTracker.Handler() {

            @Override
            public void onTickAlways(ServerLevel level, BossFightTracker.Fight f) {
                boolean open = isCharging(f.boss);
                // 열림/닫힘 전이에서만 소리와 글자를 낸다. Fight.data 에 직전 상태를 담는다 —
                // 강철거인의 취약 창과 같은 구조다.
                Boolean was = f.data instanceof Boolean b ? b : Boolean.FALSE;
                if (open != was) {
                    f.data = open;
                    Telegraph.announce(level, f.boss.position(), RANGE, Telegraph.Kind.OPENING, open);
                }
                if (!open || level.getServer().getTickCount() % 2 != 0) return;
                Telegraph.aura(level, f.boss.position(), Telegraph.Kind.OPENING, 1.2, 2.4, 4);
            }

            @Override
            public void onTick(ServerLevel level, BossFightTracker.Fight f, List<ServerPlayer> near) {
                if (f.taught) return;
                f.taught = true;
                for (ServerPlayer p : near) {
                    p.sendSystemMessage(Component.literal(
                        "§6◆ 유령기사 §7— §a달려들 때§7만 갑옷이 벗겨진다. §8그때가 제일 아프기도 하다."));
                }
            }
        });

    // ══════════════════════════════════════════════════════════════════
    // 4. 트와일라잇 우르가스트 — 「발작 중에는 열에 하나만 들어간다」
    // ══════════════════════════════════════════════════════════════════
    //
    // UrGhast.hurt():  isInTantrum() 이면 피해를 그대로 10으로 나눈다.
    // 그리고 damageUntilNextPhase(18.0)만큼 - 실제로 깎인 체력 - 이 쌓이면 페이즈가 넘어간다.
    //
    // 여기서 색을 안 쓰는 이유: 파랑(멈춰라)이 아니다. 발작을 끝내려면 그 18을 채워야 하고,
    // 그건 - 계속 때려야 - 채워진다. «치지 마라»라고 말하면 정반대를 가르치는 셈이다.
    // 자세를 요구하는 게 아니라 사실을 알리는 것이므로 글자로만 낸다.
    //
    // ※ 누적은 hurtTime == hurtDuration 인 히트만 센다 — 무적 프레임을 갓 시작한 타격만이다.
    //   여러 명이 동시에 때려도 프레임당 한 번만 쌓인다는 뜻이라, 인원이 늘어도 발작이
    //   비례해서 짧아지지는 않는다.
    public static final ResourceLocation UR_GHAST =
        ResourceLocation.fromNamespaceAndPath("twilightforest", "ur_ghast");

    private static final Ref UG_REF = new Ref("우르가스트의 발작 상태");
    private static Method IN_TANTRUM;
    private static Field DMG_UNTIL_PHASE;

    private static final BossFightTracker UR_GHAST_T = BossFightTracker.signalOnly(
        UR_GHAST, "우르가스트", RANGE, new BossFightTracker.Handler() {

            @Override
            public void onTick(ServerLevel level, BossFightTracker.Fight f, List<ServerPlayer> near) {
                if (!f.taught) {
                    f.taught = true;
                    for (ServerPlayer p : near) {
                        p.sendSystemMessage(Component.literal(
                            "§6◆ 우르가스트 §7— §8발작 중에는 피해가 §7열에 하나§8만 들어간다. 그래도 때려야 끝난다."));
                    }
                }
                if (UG_REF.broken || level.getServer().getTickCount() % 10 != 0) return;
                try {
                    if (IN_TANTRUM == null) {
                        IN_TANTRUM = f.boss.getClass().getMethod("isInTantrum");
                        DMG_UNTIL_PHASE = f.boss.getClass().getDeclaredField("damageUntilNextPhase");
                        DMG_UNTIL_PHASE.setAccessible(true);
                    }
                    if (!(Boolean) IN_TANTRUM.invoke(f.boss)) return;
                    float left = Math.max(0f, DMG_UNTIL_PHASE.getFloat(f.boss));
                    for (ServerPlayer p : near) {
                        p.displayClientMessage(Component.literal(String.format(
                            "§8발작 §7— 피해 §c1/10§7 · 깨우기까지 §e%.0f", left)), true);
                    }
                } catch (Exception e) {
                    UG_REF.fail(e);
                }
            }
        });

    // ── 이벤트 위임 ──

    private static final BossFightTracker[] ALL = { FROSTMAW_T, HYDRA_T, KNIGHT_T, UR_GHAST_T };

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        for (BossFightTracker t : ALL) t.onJoin(event);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (BossFightTracker t : ALL) t.tick();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (BossFightTracker t : ALL) t.stop();
    }

    // ── 시험용 ──
    // 소환·즉시시전이 없다. 여기 기믹은 «보스가 어떤 상태일 때 무엇을 보여주는가»가 전부라,
    // 실제 개체가 그 상태가 되는 것 말고는 확인할 방법이 없다. status 만 낸다.
    public static List<Component> status() {
        List<Component> out = new ArrayList<>();
        int n = 0;
        for (BossFightTracker t : ALL) n += t.tracked();
        if (n == 0) {
            out.add(Component.literal("§7추적 중인 탐험 보스가 없다."));
            return out;
        }
        for (BossFightTracker t : ALL) if (t.tracked() > 0) out.addAll(t.status());
        return out;
    }
}
