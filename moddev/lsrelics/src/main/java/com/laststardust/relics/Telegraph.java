package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

// ── 기믹 어휘 (docs/RESEARCH.md 「2. 전투·보스전」) ──
//
// FF14 에서 가져온 원칙: 텔레그래프는 - 재사용되는 시각 언어 - 다.
// 같은 모양·색·소리는 언제나 같은 의미여야 한다. T1 에서 어휘를 하나씩 가르치고
// T3~T4 는 조합만 바꾼다. 그래야 처음 보는 보스에서도 "저건 산개다"가 즉시 읽힌다.
//
// 반대로 매 보스가 자기만의 연출을 쓰면 "왜 죽었는지 모르겠다"가 되고, 공략 생태계가 없는
// 자체 팩에서 그건 그냥 이탈 요인이다(RLCraft 반면교사).
//
//   빨강  DANGER  이 자리에서 벗어나라
//   노랑  STACK   여기로 뭉쳐라
//   보라  SPREAD  서로 떨어져라
//
// 예고 시간은 - 전 관문 공통 1.5초 - 다. 보스마다 다르면 어휘가 아니라 개별 암기가 된다.
//
// ── 쓰는 법 ──
//   Telegraph.danger(level, center, 5.0, () -> hurtAround(...));
// 예고를 그리는 동안 파티클·소리가 나가고, 1.5초 뒤 넘긴 동작이 실행된다.
// 판정 자체는 호출한 쪽이 정한다 — 이 클래스는 "언제·어떻게 알릴지"만 책임진다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class Telegraph {
    private Telegraph() {}

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    // 전 관문 공통. 이 값을 보스마다 다르게 두면 어휘가 무너진다.
    public static final int WARN_TICKS = 30;   // 1.5초

    public enum Kind {
        DANGER (new Vector3f(1.00f, 0.15f, 0.15f), "피해라"),
        STACK  (new Vector3f(1.00f, 0.85f, 0.20f), "뭉쳐라"),
        SPREAD (new Vector3f(0.70f, 0.30f, 1.00f), "흩어져라"),
        // ── 초록만 성격이 다르다 ──
        // 위 셋은 «곧 온다»는 예고고, 이건 «지금 열려 있다»는 상태다. 그래서 cast() 가 아니라
        // band() 로 그린다 — 예고 시간도 판정도 없고, 열려 있는 동안 호출한 쪽이 매 틱 부른다.
        // 어휘를 한 파일에 모아 두는 게 목적이라 여기 둔다. 색 하나가 두 뜻을 가지면 안 된다.
        OPENING(new Vector3f(0.25f, 1.00f, 0.35f), "지금 쳐라"),
        // 초록의 짝. 초록이 «지금 쳐라»면 파랑은 «치지 마라»다.
        // 앞의 넷은 - 어디에 설 것인가 - 를 말하고, 이 둘만 - 언제 때릴 것인가 - 를 말한다.
        // 그래서 바닥이 아니라 보스 몸을 감싸는 형태(aura)로 그린다 — 자리 이동으로 오독되면 안 된다.
        HOLD   (new Vector3f(0.30f, 0.55f, 1.00f), "멈춰라");

        final Vector3f color;
        final String label;
        Kind(Vector3f c, String l) { this.color = c; this.label = l; }
    }

    private static final class Pending {
        final ServerLevel level;
        final Kind kind;
        final Vec3 center;
        final double radius;
        final Runnable resolve;
        int left;
        Pending(ServerLevel level, Kind kind, Vec3 center, double radius, Runnable resolve) {
            this.level = level; this.kind = kind; this.center = center;
            this.radius = radius; this.resolve = resolve; this.left = WARN_TICKS;
        }
    }

    private static final List<Pending> ACTIVE = new ArrayList<>();

    public static void danger(ServerLevel l, Vec3 c, double r, Runnable resolve) { cast(l, Kind.DANGER, c, r, resolve); }
    public static void stack (ServerLevel l, Vec3 c, double r, Runnable resolve) { cast(l, Kind.STACK,  c, r, resolve); }
    public static void spread(ServerLevel l, Vec3 c, double r, Runnable resolve) { cast(l, Kind.SPREAD, c, r, resolve); }

    public static void cast(ServerLevel level, Kind kind, Vec3 center, double radius, Runnable resolve) {
        ACTIVE.add(new Pending(level, kind, center, radius, resolve));
        // 시작 신호 — 소리도 어휘의 일부다. 파티클을 못 본 사람도 "뭔가 온다"를 안다.
        level.playSound(null, center.x, center.y, center.z, SoundEvents.NOTE_BLOCK_BELL.value(),
            SoundSource.HOSTILE, 1.2f, kind == Kind.DANGER ? 0.7f : (kind == Kind.STACK ? 1.2f : 1.6f));
        // 반경 안의 사람에게 글자로도 알린다 — 파티클이 가려지는 상황(밤·셰이더·군중)이 흔하다.
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(center) <= (radius + 8) * (radius + 8)) {
                p.displayClientMessage(Component.literal(colorCode(kind) + "◆ " + kind.label), true);
            }
        }
    }

    private static String colorCode(Kind k) {
        switch (k) {
            case DANGER:  return "§c";
            case STACK:   return "§e";
            case OPENING: return "§a";
            case HOLD:    return "§9";
            default:      return "§d";
        }
    }

    // ── 지속 상태 표시 ──
    // cast() 가 «곧 온다»를 그린다면 이쪽은 «지금 여기다»를 그린다. 판정도 예고 시간도 없고,
    // 상태가 유지되는 동안 호출한 쪽이 계속 부른다.
    //
    // 부채꼴을 채우지 않고 - 안쪽·바깥쪽 두 호 + 양 끝 빗금 - 으로 테두리만 두른다.
    // 채우면 그 위에 선 보스가 안 보이는데, 이 표시는 «보스 뒤로 가라»는 뜻이라
    // 정작 보스를 가리면 말이 안 된다.
    //
    // fromYaw/toYaw 는 마인크래프트 야우(0=남쪽/+Z, 시계 반대로 증가). 보스 몸통 각도에
    // 상대 오프셋을 더해 넘기면 보스가 돌 때 표시도 같이 돈다.
    public static void band(ServerLevel level, Vec3 center, Kind kind,
                            float fromYaw, float toYaw, double rInner, double rOuter, int steps) {
        ParticleOptions dust = new DustParticleOptions(kind.color, 1.3f);
        for (int i = 0; i <= steps; i++) {
            double yaw = Math.toRadians(fromYaw + (toYaw - fromYaw) * ((double) i / steps));
            double dx = -Math.sin(yaw), dz = Math.cos(yaw);
            level.sendParticles(dust, center.x + dx * rInner, center.y + 0.15, center.z + dz * rInner, 1, 0, 0, 0, 0);
            level.sendParticles(dust, center.x + dx * rOuter, center.y + 0.15, center.z + dz * rOuter, 1, 0, 0, 0, 0);
        }
        // 양 끝을 이어 닫는다 — 안 그으면 부채꼴이 어디서 끝나는지 안 읽힌다.
        for (float edge : new float[] { fromYaw, toYaw }) {
            double yaw = Math.toRadians(edge);
            double dx = -Math.sin(yaw), dz = Math.cos(yaw);
            for (int i = 0; i <= 4; i++) {
                double r = rInner + (rOuter - rInner) * (i / 4.0);
                level.sendParticles(dust, center.x + dx * r, center.y + 0.15, center.z + dz * r, 1, 0, 0, 0, 0);
            }
        }
    }

    // ── 몸을 감싸는 표시 ──
    // band() 가 «바닥의 어디»를 말한다면 이쪽은 «저 몸에 무슨 일이 있다»를 말한다.
    // 링을 높이 방향으로 쌓아 기둥을 만든다 — 바닥에만 그리면 «저 자리를 피해라»로 오독된다.
    public static void aura(ServerLevel level, Vec3 base, Kind kind, double radius, double height, int rings) {
        ParticleOptions dust = new DustParticleOptions(kind.color, 1.2f);
        int steps = 14;
        for (int r = 0; r < rings; r++) {
            double y = base.y + height * ((double) r / Math.max(1, rings - 1));
            for (int i = 0; i < steps; i++) {
                double a = Math.PI * 2 * i / steps + r * 0.22;   // 층마다 살짝 비틀어 나선처럼 보이게
                level.sendParticles(dust, base.x + Math.cos(a) * radius, y, base.z + Math.sin(a) * radius,
                    1, 0, 0, 0, 0);
            }
        }
    }

    // 상태가 - 바뀐 순간 - 만 알린다. 매 틱 부르면 소리가 겹쳐 소음이 된다.
    // 열릴 때는 올라가는 음, 닫힐 때는 내려가는 음 — 화면을 안 보고 있어도 창을 안다.
    public static void announce(ServerLevel level, Vec3 center, double radius, Kind kind, boolean opening) {
        // 소리도 어휘의 일부다 — 색마다 다른 소리를 줘야 화면을 안 보고도 무엇이 켜졌는지 안다.
        // 초록은 맑은 종(열렸다), 파랑은 쇳소리(막혔다). 닫힘은 둘 다 낮은 음.
        var snd = !opening ? SoundEvents.NOTE_BLOCK_BASS.value()
                : kind == Kind.HOLD ? SoundEvents.ANVIL_LAND
                : SoundEvents.NOTE_BLOCK_CHIME.value();
        level.playSound(null, center.x, center.y, center.z, snd, SoundSource.HOSTILE,
            1.0f, !opening ? 0.8f : (kind == Kind.HOLD ? 1.4f : 1.8f));
        if (!opening) return;   // 닫힘은 소리로 충분하다. 글자까지 띄우면 «놓쳤다»만 강조된다.
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(center) <= radius * radius) {
                p.displayClientMessage(Component.literal(colorCode(kind) + "◆ " + kind.label), true);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Pending> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            draw(p);
            if (--p.left > 0) continue;
            it.remove();
            try {
                p.resolve.run();
            } catch (Exception e) {
                // 판정에서 터져도 다른 기믹까지 멈추면 안 된다
                LOG.error("[Telegraph] resolve 실패", e);
            }
            impact(p);
        }
    }

    // 테두리를 그린다. 채우지 않는 이유: 바닥을 가득 칠하면 그 위에 선 몹·아이템이 안 보이고,
    // 셰이더에서 지면과 뭉개진다. 테두리는 어느 조명에서도 형태가 남는다.
    private static void draw(Pending p) {
        // 남은 시간이 짧아질수록 촘촘해진다 — 초읽기가 눈에 보인다.
        int steps = 24 + (WARN_TICKS - p.left);
        ParticleOptions dust = new DustParticleOptions(p.kind.color, 1.4f);
        for (int i = 0; i < steps; i++) {
            double a = Math.PI * 2 * i / steps;
            double x = p.center.x + Math.cos(a) * p.radius;
            double z = p.center.z + Math.sin(a) * p.radius;
            p.level.sendParticles(dust, x, p.center.y + 0.15, z, 1, 0, 0, 0, 0);
        }
        // 뭉쳐라는 중심도 찍는다 — 어디로 모일지가 테두리만으로는 안 보인다.
        if (p.kind == Kind.STACK) {
            p.level.sendParticles(dust, p.center.x, p.center.y + 0.6, p.center.z, 4, 0.25, 0.4, 0.25, 0.0);
        }
    }

    private static void impact(Pending p) {
        p.level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,
            p.center.x, p.center.y + 0.5, p.center.z, 1, 0, 0, 0, 0);
        p.level.playSound(null, p.center.x, p.center.y, p.center.z,
            SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.9f, 1.3f);
    }

    // ── 판정 도우미 ──
    // 기믹마다 "안에 있었나 / 밖에 있었나"를 매번 새로 쓰면 어휘가 흔들린다.
    public static List<ServerPlayer> inside(ServerLevel level, Vec3 center, double radius) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(center) <= radius * radius) out.add(p);
        }
        return out;
    }

    // 기믹 피해를 넣기 전에 매번 확인해야 하는 것. 다섯 기믹이 각자 조금씩 다르게 쓰고 있었다
    // (누구는 관전자만, 누구는 차원까지). 빠뜨리면 관전자가 맞거나, 차원을 옮긴 사람이
    // 옛 차원의 판정에 맞는다 — 둘 다 «가끔 이상하다»로만 보여서 원인을 못 찾는 종류다.
    public static boolean hittable(ServerPlayer p, ServerLevel level) {
        return !p.isSpectator() && p.isAlive() && p.level() == level;
    }

    public static List<ServerPlayer> outside(ServerLevel level, Vec3 center, double radius) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(center) > radius * radius) out.add(p);
        }
        return out;
    }
}
