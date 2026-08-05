package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

// ── 보스 기믹 공통 뼈대 ──
// 기믹마다 다른 건 「무엇이 일어나는가」뿐이고 나머지(추적·교전 판정·유휴 되감기·시험 소환·
// 전투 기록)는 같다. 다섯 기믹 전부 이 하나를 쓴다.
//
// ── 이벤트를 여기서 구독하지 않는 이유 ──
// @EventBusSubscriber 가 붙은 클래스는 NeoForge 가 찾아서 로드해준다. 반대로 여기서 다 받고
// 기믹은 등록만 하는 구조라면, 그 기믹 클래스를 아무도 로드하지 않아 등록 자체가 안 일어난다.
// 구독은 각 기믹이 한다 — 위임 3줄이면 되고 로딩 순서에 기대지 않는다.
public final class BossFightTracker {

    // 표적은 한두 틱씩 흔히 비어서, null 을 보자마자 초기화하면 시전이 영영 안 온다.
    private static final int IDLE_RESET = 5 * 20;

    public static final class Fight {
        public final Mob boss;
        public int next;
        public int idle = 0;
        public int phase = 1;
        public boolean taught = false;   // 이번 전투에서 어휘를 이미 안내했는가
        public int echo = 0;             // 연속 시전 남은 횟수 (핸들러가 직접 쓴다)

        // 기믹마다 다른 상태를 담는 자리. 뼈대는 안을 들여다보지 않는다 —
        // 강철거인의 「취약 창이 직전 틱에 열려 있었나」처럼 한 기믹에만 있는 것을
        // 여기 공용 클래스에 필드로 늘리면 나머지 넷에게는 영영 안 쓰이는 칸이 된다.
        public Object data;

        // ── 전투 기록계 ──
        int engagedTicks = 0;            // 교전으로 인정된 틱만 센다 (대치·복귀 시간은 뺀다)
        int peakParty = 0;               // 그 전투에서 본 최대 인원

        Fight(Mob boss, int firstDelay) { this.boss = boss; this.next = firstDelay; }
    }

    public interface Handler {
        // 호출 시점에 f.next 는 이미 「그 페이즈의 주기」로 채워져 있다 —
        // 연속 시전처럼 다음 간격을 바꾸려면 이 안에서 f.next 를 덮어쓴다.
        //
        // 기본이 «아무것도 안 함»인 이유: 기믹에 따라서는 - 우리가 일으키는 일이 없다 - .
        // 탐험 보스(ExplorerGimmicks)는 모드가 이미 가진 규칙을 보이게 만들 뿐이라
        // onTickAlways 만 쓰고 여기는 안 쓴다. 그런 트래커는 firstDelay 를 0 이하로 준다.
        default void cast(ServerLevel level, Fight f, List<ServerPlayer> near) {}

        // 페이즈가 올라간 직후 1회. 연출만 하고 실제 시전은 다음 주기에 온다.
        default void onPhaseChange(ServerLevel level, Fight f, List<ServerPlayer> near) {}

        // 교전 중 매 틱. 우리 주기와 무관하게 - 보스 자신의 상태 - 를 읽어 그려야 할 때 쓴다
        // (이그니스의 반격 자세처럼). 여기서 무거운 일을 하면 그대로 틱 비용이 되니
        // 상태 한두 개 읽고 파티클 그리는 정도로만 쓴다.
        default void onTick(ServerLevel level, Fight f, List<ServerPlayer> near) {}

        // 교전 여부와 - 무관하게 - 매 틱. 위 onTick 은 교전 판정을 통과해야 오는데,
        // 그러면 «표적이 없는 보스»에게는 영영 안 온다 — 시험 명령(/lsgimmick window)이
        // 정확히 그 상황이라 창이 안 열린다. 보스 자신의 상태만 읽는 표시는 이쪽에 둔다.
        default void onTickAlways(ServerLevel level, Fight f) {}
    }

    // 보스 체력의 설계 기준(ls_config.js) — 이 인원이 이 시간 안에 잡는 것을 목표로 역산했다.
    public static final int TARGET_SECONDS = 60;
    public static final int TARGET_PARTY   = 4;

    // 이보다 짧으면 전투로 안 본다. /kill 이나 스치듯 죽은 개체까지 보고하면 잡음이 된다.
    private static final int REPORT_MIN_TICKS = 5 * 20;

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    private final ResourceLocation bossId;
    private final String label;             // 표시용 이름 ("이그니스")
    private final String testTag;
    // 비어 있지 않으면 - 이 표식을 단 개체만 - 추적한다. 같은 종류의 몹이 기믹 없이도
    // 돌아다니는 경우가 있어서다 — 공성 선봉이 그렇다(광전사는 4단계 웨이브의 평범한
    // 구성원이기도 하다). 종류만으로 거르면 웨이브 전체에 장판이 깔린다.
    private String requireTag = "";
    private final int firstDelay;
    private final int[] intervalByPhase;    // [0]=1페이즈, [1]=2페이즈 …
    private final float[] phaseHpFrac;      // [0]=2페이즈 진입 체력 비율(0.5 = 50%)
    private final double range;
    private final Handler handler;
    // firstDelay 가 0 이하 = 주기 시전 없음. 탐험 보스처럼 «이미 있는 규칙을 보이게만»
    // 하는 기믹이 그렇다. 시계를 안 돌리므로 status 의 「다음 시전」도 뜨지 않는다.
    private final boolean periodic;

    private final List<Fight> fights = new ArrayList<>();

    public BossFightTracker(ResourceLocation bossId, String label, String testTag,
                            int firstDelay, int[] intervalByPhase, float[] phaseHpFrac,
                            double range, Handler handler) {
        this.bossId = bossId;
        this.label = label;
        this.testTag = testTag;
        this.firstDelay = firstDelay;
        this.intervalByPhase = intervalByPhase;
        this.phaseHpFrac = phaseHpFrac;
        this.range = range;
        this.handler = handler;
        this.periodic = firstDelay > 0;
    }

    // 신호만 내는 트래커를 만드는 짧은 길. 주기·페이즈·시험 태그가 전부 없다 —
    // 그런 기믹은 «우리가 일으키는 일»이 없어서 소환해 볼 것도, 앞당길 것도 없다.
    public static BossFightTracker signalOnly(ResourceLocation bossId, String label,
                                              double range, Handler handler) {
        return new BossFightTracker(bossId, label, "", 0, new int[] { 1 }, new float[] {}, range, handler);
    }

    // 종류에 더해 표식까지 맞아야 추적한다. 생성자가 아니라 별도 메서드인 이유는
    // 이걸 쓰는 트래커가 하나뿐이라, 나머지 다섯의 생성자에 빈 문자열을 늘어놓지 않으려는 것이다.
    public BossFightTracker onlyTagged(String tag) {
        this.requireTag = tag;
        return this;
    }

    public int interval(int phase) {
        int i = Math.min(phase, intervalByPhase.length) - 1;
        return intervalByPhase[Math.max(0, i)];
    }

    // 스폰뿐 아니라 청크 로드에서도 불린다 — 서버를 껐다 켜도 남아 있던 개체가 다시 추적된다.
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        Entity e = event.getEntity();
        if (!(e instanceof Mob mob)) return;
        if (!bossId.equals(EntityType.getKey(e.getType()))) return;
        if (!requireTag.isEmpty() && !mob.getTags().contains(requireTag)) return;
        for (Fight f : fights) if (f.boss == mob) return;   // 중복 등록 방지
        fights.add(new Fight(mob, firstDelay));
    }

    public void tick() {
        if (fights.isEmpty()) return;
        Iterator<Fight> it = fights.iterator();
        while (it.hasNext()) {
            Fight f = it.next();
            // isRemoved 는 죽음뿐 아니라 청크 언로드도 포함한다 — 둘 다 추적을 끊는 게 맞다.
            if (f.boss.isRemoved() || !f.boss.isAlive()) { report(f); it.remove(); continue; }
            if (!(f.boss.level() instanceof ServerLevel level)) continue;

            // 교전 판정보다 - 먼저 - 본다. 보스 자신의 상태를 읽는 표시는 우리 시계와 무관하고,
            // 표적이 없는 개체(시험 소환 직후)에게도 와야 한다.
            handler.onTickAlways(level, f);

            List<ServerPlayer> near = nearby(level, f.boss);
            // 교전 전에는 시계가 흐르지 않는다. 잠든 보스 옆을 지나갔다고 기믹이 떨어지면
            // 그건 예고가 아니라 함정이다.
            if (near.isEmpty() || f.boss.getTarget() == null) {
                if (++f.idle > IDLE_RESET) { f.next = firstDelay; f.phase = 1; f.taught = false; f.echo = 0; }
                continue;
            }
            f.idle = 0;
            f.engagedTicks++;
            if (near.size() > f.peakParty) f.peakParty = near.size();
            handler.onTick(level, f, near);
            if (!periodic) continue;   // 신호만 내는 기믹 — 주기 시전이 없다

            // 페이즈 전환은 시전 주기와 무관하게 즉시 본다 — 체력이 넘어간 순간에 알려야
            // "무엇 때문에 달라졌는지"가 이어진다.
            int want = 1;
            for (int i = 0; i < phaseHpFrac.length; i++) {
                if (f.boss.getHealth() / f.boss.getMaxHealth() <= phaseHpFrac[i]) want = i + 2;
            }
            if (want > f.phase) {
                f.phase = want;
                handler.onPhaseChange(level, f, near);
                // 전환 직후에 바로 때리지 않는다. 연출을 볼 시간을 준다.
                f.next = Math.min(f.next, interval(f.phase));
            }

            if (--f.next > 0) continue;
            f.next = interval(f.phase);
            handler.cast(level, f, near);
        }
    }

    // 서버가 멈추면 목록을 비운다. 죽은 레벨의 엔티티를 붙들고 있으면
    // 싱글(내장 서버)에서 월드를 갈아탈 때 그대로 새 세계로 새어 들어간다.
    public void stop() { fights.clear(); }

    // ── 보스가 죽으면 스스로 보고한다 ──
    //
    // 제단 보스 4종의 체력은 «4명·60초»에서 역산한 값인데(`ls_config.js`), 정작 그 60초는
    // - 사람이 초시계로 - 재야 했다. 재고 → 나누고 → 명령을 손으로 조립한다. 세 단계 전부
    // 틀릴 수 있고, 더 흔한 사고는 «재는 걸 잊어서 보스를 한 번 더 잡는» 쪽이다.
    // 그래서 걸린 시간뿐 아니라 - 그대로 붙여넣을 명령 한 줄 - 까지 여기서 만든다.
    //
    //   새 체력 = 지금 최대 체력 × (60 / 실제 걸린 초)      ← docs 의 보정식 그대로
    //
    // 시간은 벽시계가 아니라 - 교전으로 인정된 틱 - 이다. 죽고 돌아오는 시간, 대치하며
    // 다시 붙는 시간을 세면 체력만 부풀어 그 시간이 통째로 «서 있는» 시간이 된다.
    private void report(Fight f) {
        // 죽은 것만 보고한다. isRemoved 에는 청크 언로드와 discard(`/lsgimmick clear`) 도
        // 섞여 있는데, 그쪽은 체력이 남아 있으므로 여기서 갈린다.
        if (!f.boss.isDeadOrDying()) return;
        if (f.engagedTicks < REPORT_MIN_TICKS) return;

        double secs = f.engagedTicks / 20.0;
        float maxHp = f.boss.getMaxHealth();
        int suggest = (int) Math.round(maxHp * (TARGET_SECONDS / secs));
        ResourceLocation id = EntityType.getKey(f.boss.getType());

        LOG.info("[전투 기록] {} · {}초 · {}명 · maxHp={} · 실측DPS={} · 권장체력={}",
            label, String.format(Locale.ROOT, "%.1f", secs), f.peakParty,
            String.format(Locale.ROOT, "%.0f", maxHp),
            String.format(Locale.ROOT, "%.0f", maxHp / secs), suggest);

        if (!(f.boss.level() instanceof ServerLevel level)) return;
        for (ServerPlayer p : level.players()) {
            // 조율용 계기다. 일반 참가자에게 «권장 체력 4828» 이 뜨면 그건 보스전이 아니라
            // 스프레드시트가 된다 — 운영 권한이 있는 사람에게만 보인다.
            if (!p.hasPermissions(2)) continue;
            p.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§6▣ %s 처치 §8· §e%.1f초 §8· §7%d명 §8· 체력 %.0f (실측 DPS %.0f)",
                label, secs, f.peakParty, maxHp, maxHp / secs)));
            // 권장 체력은 «4명·60초» 목표를 가진 보스에게만 뜻이 있다. 탐험 보스(신호만 내는
            // 트래커)는 그 목표를 안 갖는다 — 거기에 같은 줄을 띄우면 없는 기준을 있는 것처럼 만든다.
            if (!periodic) continue;
            p.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§7목표 %d초 → 권장 체력 §e%d§7  §8/bossdiff abs %s %d",
                TARGET_SECONDS, suggest, id, suggest)));
            if (f.peakParty != TARGET_PARTY) {
                p.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§8※ 설계 기준은 %d명이다 — 이번엔 %d명이라 이 값을 그대로 넣으면 어긋난다.",
                    TARGET_PARTY, f.peakParty)));
            }
        }
    }

    // 교전 범위 안의 플레이어. ※ Telegraph.outside() 는 - 월드 전체 - 를 훑으므로
    // "원 밖이면 피해" 판정에 그대로 쓰면 성역에 있던 사람까지 맞는다. 항상 이 목록을 기준으로 판정할 것.
    public List<ServerPlayer> nearby(ServerLevel level, Mob boss) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (p.distanceToSqr(boss) <= range * range) out.add(p);
        }
        return out;
    }

    public static Vec3 centroid(List<ServerPlayer> players) {
        double x = 0, y = 0, z = 0;
        for (ServerPlayer p : players) { x += p.getX(); y += p.getY(); z += p.getZ(); }
        int n = Math.max(1, players.size());
        return new Vec3(x / n, y / n, z / n);
    }

    // ── 시험용 ──

    public int tracked() { return fights.size(); }

    // 같은 차원에서 가장 가까운 개체. 시험 명령이 "지금 내 앞의 그것"을 집는 유일한 수단이다.
    public Fight nearest(ServerPlayer player) {
        Fight best = null;
        double bestDist = Double.MAX_VALUE;
        for (Fight f : fights) {
            if (f.boss.isRemoved() || !f.boss.isAlive()) continue;
            if (f.boss.level() != player.level()) continue;
            double d = f.boss.distanceToSqr(player);
            if (d < bestDist) { bestDist = d; best = f; }
        }
        return best;
    }

    // 가장 가까운 개체가 지금 시전한다. 주기를 기다리며 검증할 수는 없다.
    public boolean forceNear(ServerPlayer player) {
        Fight best = nearest(player);
        if (best == null || !(best.boss.level() instanceof ServerLevel level)) return false;
        List<ServerPlayer> near = nearby(level, best.boss);
        if (near.isEmpty()) near = List.of(player);   // 사거리 밖에서 불러도 시전자는 본다
        best.next = interval(best.phase);
        handler.cast(level, best, near);
        return true;
    }

    // 시험 소환도 관문과 - 똑같은 경로 - 를 탄다(/summon). 자바에서 직접 스폰하면
    // KubeJS 의 EntityEvents.spawned 를 안 타서 /bossdiff 배율이 빠진 개체로 시험하게 된다.
    public boolean summon(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        Vec3 look = player.getViewVector(1.0f);
        double x = player.getX() + look.x * 8.0;
        double z = player.getZ() + look.z * 8.0;
        // 추적 조건(requireTag)이 따로 있으면 시험 소환분에도 같이 달아야 한다 —
        // 안 그러면 소환은 되는데 기믹이 안 붙어서 «왜 아무 일도 안 일어나지»가 된다.
        // 그렇다고 두 표식을 하나로 합칠 수는 없다: clearTest 가 지우는 건 - 시험 표식 - 이고,
        // 공성 선봉처럼 추적 표식이 실전 개체에도 붙는 경우엔 그 하나로 실전 개체가 지워진다.
        String tags = requireTag.isEmpty() ? "\"" + testTag + "\""
                                           : "\"" + testTag + "\",\"" + requireTag + "\"";
        // Locale.ROOT 고정 — 소수점이 쉼표인 지역 설정에서는 "12,34" 가 되어 명령이 통째로 깨진다.
        String cmd = String.format(Locale.ROOT,
            "summon %s %.2f %.2f %.2f {Tags:[%s],PersistenceRequired:1b}",
            bossId, x, player.getY(), z, tags);
        server.getCommands().performPrefixedCommand(
            player.createCommandSourceStack().withPermission(2).withSuppressedOutput(), cmd);
        return true;
    }

    // 시험 소환분만 지운다. 표식이 없는 개체 — 즉 세계에서 만난 보스 — 는 건드리지 않는다.
    public int clearTest() {
        int n = 0;
        Iterator<Fight> it = fights.iterator();
        while (it.hasNext()) {
            Fight f = it.next();
            if (!f.boss.getTags().contains(testTag)) continue;
            if (f.boss.isAlive()) { f.boss.discard(); n++; }
            it.remove();
        }
        return n;
    }

    public List<Component> status() {
        List<Component> out = new ArrayList<>();
        if (fights.isEmpty()) {
            out.add(Component.literal("§7추적 중인 " + label + "이(가) 없습니다."));
            return out;
        }
        for (Fight f : fights) {
            boolean engaged = f.boss.getTarget() != null;
            // 페이즈가 하나뿐인 보스(강철거인)에게 "1페이즈"는 정보가 아니라 잡음이다.
            String phase = phaseHpFrac.length == 0 ? "" : String.format(Locale.ROOT, " §8· §7%d페이즈", f.phase);
            out.add(Component.literal(String.format(Locale.ROOT,
                "§7%s §8(%.0f, %.0f, %.0f)%s%s §8· %s",
                label, f.boss.getX(), f.boss.getY(), f.boss.getZ(),
                f.boss.getTags().contains(testTag) ? " §8[시험]" : "",
                phase,
                !engaged ? "§8대기(비교전)"
                    : periodic ? String.format(Locale.ROOT, "§c교전 중 §7— 다음 시전 §e%.1f초 §8· 경과 %.0f초",
                                               f.next / 20.0f, f.engagedTicks / 20.0)
                               : String.format(Locale.ROOT, "§c교전 중 §8· 경과 %.0f초", f.engagedTicks / 20.0))));
        }
        return out;
    }
}
