package com.laststardust.relics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 최근에 쓰러진 플레이어 장부 + 짧은 지연 실행 큐. 파나케이아의 "소생"이 쓴다.
//
// ── 왜 필요한가 ──
// 예전 소생은 level.players() 에서 isDeadOrDying() 인 사람만 찾았다. 즉 대상이 사망 화면에
// 그대로 떠 있어야만 되살릴 수 있었는데, 실제로는 죽자마자 '리스폰'을 눌러버리기 때문에
// 힐러가 X 를 누를 때쯤이면 이미 스폰 지점에서 살아 있다 → "주변에 되살릴 아군이 없다".
// 게다가 사망 화면이 채팅·타이틀·액션바를 전부 가려서 "기다려라"라고 알려줄 방법도 없다.
//
// 그래서 죽은 위치를 장부에 적어두고, 30초 안이면 이미 리스폰했더라도 쓰러진 자리로 되돌린다.
// 되살리는 쪽이 "기다려 달라"고 부탁할 필요가 없어진다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class ReviveManager {
    private ReviveManager() {}

    // 되살릴 수 있는 시간(틱). 90초 쿨다운 스킬이라 30초면 "그 전투 안"이라는 감각과 맞는다.
    public static final int WINDOW_TICKS = 600;

    public static final class Fallen {
        public final ResourceKey<Level> dimension;
        public final Vec3 pos;
        public final long at;
        Fallen(ResourceKey<Level> dimension, Vec3 pos, long at) {
            this.dimension = dimension; this.pos = pos; this.at = at;
        }
    }

    private static final Map<UUID, Fallen> FALLEN = new HashMap<>();

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer dead)) return;
        if (!(dead.level() instanceof ServerLevel level)) return;
        FALLEN.put(dead.getUUID(), new Fallen(level.dimension(), dead.position(), level.getGameTime()));
    }

    // 되살릴 수 있는 대상. 기준점은 '죽은 자리'다 — 이미 리스폰해 멀리 가 있어도
    // 쓰러진 곳이 힐러 근처면 대상이 된다(힐러가 시신 곁에 서서 쓰는 스킬이라는 뜻).
    public static List<ServerPlayer> candidates(ServerLevel level, ServerPlayer caster, double range, int max) {
        List<ServerPlayer> out = new ArrayList<>();
        long now = level.getGameTime();
        Vec3 c = caster.position();
        double r2 = range * range;
        for (Map.Entry<UUID, Fallen> e : FALLEN.entrySet()) {
            Fallen f = e.getValue();
            if (f.dimension != level.dimension()) continue;
            if (now - f.at > WINDOW_TICKS) continue;
            if (f.pos.distanceToSqr(c) > r2) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null || p == caster || p.isSpectator()) continue;
            out.add(p);
            if (out.size() >= max) break;
        }
        return out;
    }

    public static Fallen record(UUID id) { return FALLEN.get(id); }
    public static void consume(UUID id) { FALLEN.remove(id); }

    // ── 소생 시 아이템 회수 ──
    //
    // 죽으면 아이템은 평소대로 떨어진다. 다만 그 드롭을 기억해 두었다가, 30초 안에 되살아나면
    // **바닥에 남아 있는 것만** 걷어서 돌려준다.
    //
    // 떨구지 않고 붙잡아 두는 방식(=되살아나면 인벤에 그대로)도 검토했지만, 되살아나지 않았을 때
    // 시체 자리가 30초간 비어 보여서 "내 아이템이 사라졌다"로 읽힌다. 이쪽은 보이는 흐름이
    // 평소와 같다.
    //
    // 대신 그 사이 남이 주워가면 그건 못 돌려준다 — **"남의 시체는 줍지 않는다"는 규칙으로
    // 다룬다.** 시스템으로 막으면(주인만 줍기) 파티가 서로 챙겨주는 것도 같이 막힌다.
    // 기록한 시각을 함께 들고 있어야 창이 지났을 때 버릴 수 있다.
    // (시각 없이 목록만 담았다가, 아무도 소생시키지 않으면 영영 안 지워지는 상태였다 —
    //  ItemEntity 참조까지 붙잡고 있어서 죽을수록 늘기만 했다)
    private static final class Dropped {
        final List<net.minecraft.world.entity.item.ItemEntity> items;
        final long at;
        Dropped(List<net.minecraft.world.entity.item.ItemEntity> items, long at) {
            this.items = items; this.at = at;
        }
    }

    private static final Map<UUID, Dropped> DROPS = new HashMap<>();

    @SubscribeEvent
    public static void onDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer dead)) return;
        if (!(dead.level() instanceof ServerLevel level)) return;
        DROPS.put(dead.getUUID(), new Dropped(new ArrayList<>(event.getDrops()), level.getGameTime()));
    }

    // 되살아난 사람에게 아직 바닥에 있는 자기 드롭을 돌려준다.
    public static int reclaimDrops(ServerPlayer p) {
        Dropped rec = DROPS.remove(p.getUUID());
        if (rec == null) return 0;
        List<net.minecraft.world.entity.item.ItemEntity> list = rec.items;
        int n = 0;
        for (net.minecraft.world.entity.item.ItemEntity ie : list) {
            if (ie == null || ie.isRemoved()) continue;   // 이미 남이 주웠거나 사라짐
            var stack = ie.getItem();
            if (stack.isEmpty()) continue;
            if (!p.getInventory().add(stack.copy())) p.drop(stack.copy(), false);
            ie.discard();
            n++;
        }
        return n;
    }

    // 예약도 시각을 들고 있어야 한다. **사망 화면에서 그냥 나가버리면** 예약이 영영 남아,
    // 몇 시간 뒤 리스폰했을 때 옛 죽은 자리로 끌려간다.
    private static final class Reserved {
        final Vec3 pos;
        final long at;
        Reserved(Vec3 pos, long at) { this.pos = pos; this.at = at; }
    }

    // ── 예약 소생 ──
    //
    // 아직 사망 화면에 있는 사람은 **서버가 대신 리스폰시키지 않는다.**
    // 예전엔 PlayerList.respawn() 을 직접 불렀는데, 바닐라 리스폰은 클라가 "리스폰 눌렀다"를
    // 보내야 시작되는 흐름이라 서버가 일방적으로 부르면 **클라가 사망 상태에 남아 엔티티
    // 동기화가 깨졌다** — 되살아난 사람이 남에게도, 자기 자신에게도 안 보였다.
    //
    // 그래서 여기에 "예약"만 걸어두고, 본인이 리스폰을 누르면 그 순간(PlayerRespawnEvent)
    // 쓰러진 자리로 끌어온다. 바닐라 흐름을 그대로 타므로 깨질 여지가 없다.
    private static final Map<UUID, Reserved> PENDING = new HashMap<>();

    public static void reserve(UUID id, Vec3 where, long at) { PENDING.put(id, new Reserved(where, at)); }
    public static boolean hasPending(UUID id) { return PENDING.containsKey(id); }

    @SubscribeEvent
    public static void onRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Reserved rec = PENDING.remove(p.getUUID());
        if (rec == null) return;
        Vec3 where = rec.pos;
        if (!(p.level() instanceof ServerLevel level)) return;
        // 리스폰 직후엔 KubeJS 가 5~10틱 뒤 속성을 다시 붙이므로 한 박자 뒤에 마무리한다.
        later(15, () -> com.laststardust.relics.item.RelicSkills.finishRevive(level, p, where));
    }

    // ── 지연 실행 ──
    // 리스폰 직후엔 KubeJS(ls_ascend/ls_fate/ls_revive)가 5~10틱 뒤에 속성을 다시 붙인다.
    // 소생이 체력·위치를 그 전에 정하면 뒤이어 덮어써지므로, 마무리는 한 박자 뒤에 한다.
    private static final List<Task> TASKS = new ArrayList<>();

    private static final class Task {
        final Runnable run;
        int delay;
        Task(Runnable run, int delay) { this.run = run; this.delay = delay; }
    }

    public static void later(int delayTicks, Runnable run) {
        if (delayTicks <= 0) { run.run(); return; }
        TASKS.add(new Task(run, delayTicks));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // 장부 청소 — 창을 넘긴 기록은 버린다 (서버가 오래 켜져 있어도 늘지 않게)
        if (event.getServer().getTickCount() % 200 == 0) {
            long now = event.getServer().overworld().getGameTime();
            FALLEN.entrySet().removeIf(e -> now - e.getValue().at > WINDOW_TICKS);
            // 아래 둘도 같은 창으로 버린다. 안 그러면 죽을 때마다 늘기만 하고,
            // 예약은 몇 시간 뒤 리스폰한 사람을 옛 자리로 끌고 간다.
            DROPS.entrySet().removeIf(e -> now - e.getValue().at > WINDOW_TICKS);
            PENDING.entrySet().removeIf(e -> now - e.getValue().at > WINDOW_TICKS);
        }
        if (TASKS.isEmpty()) return;
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (--t.delay > 0) continue;
            it.remove();
            try { t.run.run(); } catch (Exception ignored) { }
        }
    }

    // 파나케이아를 4성 이상으로 들고 있는 아군이 사거리 안에 있는가.
    // (죽기 '전'에 알려주기 위한 것 — 사망 화면은 채팅·타이틀을 전부 가려서 죽은 뒤엔 못 알린다)
    public static boolean healerNearby(ServerLevel level, Player who, double range) {
        double r2 = range * range;
        for (ServerPlayer p : level.players()) {
            if (p == who || p.isDeadOrDying() || p.isSpectator()) continue;
            if (p.distanceToSqr(who) > r2) continue;
            if (p.getMainHandItem().getItem() != LSRelics.HEALER.get()) continue;
            if (com.laststardust.relics.item.RelicSkills.star(p.getMainHandItem()) >= 4) return true;
        }
        return false;
    }
}
