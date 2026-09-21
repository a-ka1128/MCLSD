package com.laststardust.relics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import com.laststardust.relics.data.LSData;

/**
 * <h2>첫 이동 — 비행선에서 성역으로</h2>
 *
 * <p>스폰 근처에 자연 생성된 <b>비행선</b>이 있고, 거기 선 NPC(Easy NPC)와 대화를 마치면
 * 성역으로 보내진다. NPC 대화의 마지막 버튼이 {@code /lsonboard <이름>} 을 부른다.
 *
 * <p>── 왜 명령을 따로 두나 ──
 * Easy NPC 의 대화 버튼은 <b>커맨드 한 줄</b>씩만 부른다({@code Actions[].Type:"COMMAND"}).
 * 그런데 이 순간에 일어나야 할 일은 넷이다 — <b>보내고 · 떠나는 자리를 지우고 · 도착을 연출하고 ·
 * 가호 화면을 연다.</b> 넷을 커맨드로 늘어놓으면 절차가 <b>데이터 파일(NPC 프리셋) 안에</b>
 * 흩어지고, 순서 하나 바꾸려면 게임 안에서 NPC 를 편집해야 한다.
 * <b>절차는 코드에 두고 NPC 는 한 번만 부른다.</b>
 *
 * <p>── 왜 도착지가 «성역»인가 ──
 * 성역 좌표는 {@link LSData} 가 유일하게 갖는다({@code /sanctuary here}).
 * 귀환석({@code HearthStone})이 쓰는 것과 <b>같은 좌표</b>라, 첫 이동과 이후의 모든 귀환이
 * 자동으로 같은 자리를 가리킨다 — 여기 좌표를 따로 적어 두면 그 둘이 언젠가 갈라진다.
 */
public final class Onboarding {
    private Onboarding() {}

    /**
     * 도착하고 나서 가호 화면이 열리기까지의 틈.
     * 동시에 열면 <b>성역을 한 번도 못 보고 UI 부터 본다.</b>
     * 3초는 「어디에 왔는지」를 눈에 담기엔 충분하고 기다린다고 느끼기엔 짧다.
     */
    private static final int SETTLE_TICKS = 60;

    /**
     * 눈을 감는 시간. 「잠시 눈을 감는다」를 누르고 나서 실제로 옮겨지기까지.
     *
     * <p>── 화면을 어떻게 어둡게 하나 ──
     * 바닐라에는 «페이드 아웃» 이 없다. {@code title} 의 fade 는 <b>글자만</b> 흐리게 하지
     * 화면을 덮지 않는다. 그래서 <b>실명(blindness)</b> 을 쓴다 — 걸면 시야가 서서히 닫히고,
     * 풀면 서서히 열린다. 그 두 번의 전환이 우리가 원하는 페이드 아웃/인이다.
     *
     * <p>입자와 아이콘은 끈다. 안 끄면 「눈을 감는」 연출 중에 상태이상 아이콘이 떠서
     * <b>연출이 아니라 디버프로 읽힌다.</b>
     */
    private static final int FADE_TICKS = 40;

    /** 도착하고 나서 시야가 다시 열리기까지. 눈을 뜨는 쪽이 감는 쪽보다 조금 느린 게 자연스럽다. */
    private static final int OPEN_EYES_TICKS = 30;

    /**
     * 비행선 → 성역.
     *
     * @return 실패 사유 (성공이면 {@code null}) — 부른 쪽이 그대로 화면에 띄운다.
     *         <b>조용히 실패하지 않는다</b> — 첫 세션에 「대화는 끝났는데 아무 일도
     *         안 일어났다」가 제일 나쁘다.
     */
    public static String send(ServerPlayer p) {
        if (p == null) return "플레이어를 찾을 수 없다.";
        MinecraftServer server = p.getServer();
        if (server == null) return "서버를 찾을 수 없다.";

        LSData data = LSData.get(server);
        if (!data.hasSanctuary()) {
            // 첫 세션 직전에 제일 흔한 실수다. 「어디로 보낼지 아무도 안 정했다」를 그대로 말한다.
            return "성역이 아직 정해지지 않았다 — 먼저 §e/sanctuary here";
        }

        // ── ① 눈을 감는다 ──
        // 실명 지속시간은 «페이드 + 이동 + 눈 뜨는 시간» 보다 넉넉히 길게 준다.
        // 짧게 잡아 중간에 풀리면 이동하는 순간이 그대로 보인다.
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
            FADE_TICKS + OPEN_EYES_TICKS + 40, 0, false, false, false));
        if (p.level() instanceof ServerLevel from) {
            from.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.7f, 0.5f);
        }

        // ── ② 2초 뒤에 실제로 옮긴다 ──
        // 화면이 이미 어두워진 뒤라 «순간이동하는 장면» 이 안 보인다. 그게 이 지연의 전부다.
        Later.run(p, FADE_TICKS, who -> arrive(who, data.sanctuary(), server.overworld()));
        return null;
    }

    /** 도착 — 어두운 화면 뒤에서 벌어지는 일. */
    private static void arrive(ServerPlayer p, BlockPos to, ServerLevel overworld) {
        // 떠나는 자리에만 남는 흔적. 본인은 눈을 감고 있어서 못 보지만,
        // 비행선에 남아 있는 다른 사람에게는 「사라졌다」가 보여야 한다.
        if (p.level() instanceof ServerLevel from) {
            from.sendParticles(ParticleTypes.PORTAL,
                p.getX(), p.getY() + 1, p.getZ(), 80, 0.4, 0.9, 0.4, 0.5);
            from.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0f, 0.8f);
        }

        p.teleportTo(overworld, to.getX() + 0.5, to.getY() + 1, to.getZ() + 0.5,
            p.getYRot(), p.getXRot());

        overworld.sendParticles(ParticleTypes.END_ROD,
            to.getX() + 0.5, to.getY() + 1.4, to.getZ() + 0.5, 90, 0.6, 1.0, 0.6, 0.06);
        overworld.sendParticles(ParticleTypes.FLASH,
            to.getX() + 0.5, to.getY() + 1.5, to.getZ() + 0.5, 1, 0, 0, 0, 0);
        overworld.playSound(null, to, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);

        // ── ③ 눈을 뜬다 ──
        // 실명을 풀면 시야가 서서히 열린다. 타이틀은 그 «열리는 동안» 떠 있어야
        // 「눈을 뜨니 성역이었다」가 된다 — 다 열린 뒤에 띄우면 두 장면이 따로 논다.
        Later.run(p, OPEN_EYES_TICKS, who -> {
            who.removeEffect(MobEffects.BLINDNESS);
            // ⚠️ 자막을 먼저 보낸다. 자막은 «보관»만 되고 타이틀이 올 때 같이 뜬다 —
            //    반대로 보내면 그 판엔 자막이 없고 다음번에 뒤늦게 따라붙는다
            //    (ls_siege.js 와 airship_enter.mcfunction 에서 같은 것을 겪었다).
            who.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal("§7부서진 별이 떨어진 자리")));
            who.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§b성역")));
            if (who.level() instanceof ServerLevel lv) {
                lv.playSound(null, who.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 1.0f, 0.7f);
                SoundScheduler.at(lv, who.position(), SoundEvents.BEACON_ACTIVATE, 1.2f, 0.8f, 20);
            }

            // ── ④ 가호 선택 ──
            // ⚠️ 이미 가호가 있으면 openScreen 이 스스로 건너뛴다 — NPC 와 두 번째로
            //    대화하는 경우가 그렇다. 다시 못 고르는데 창만 열리면 「고를 수 있나 보다」로 읽힌다.
            FateAutoOpen.openLater(who, SETTLE_TICKS);
        });
    }

    /**
     * N틱 뒤에 그 플레이어에게 무언가를 한다.
     *
     * <p>{@link SoundScheduler} 는 «자리»에 소리를 놓는 물건이라 사람을 못 따라간다.
     * 여기서 필요한 건 <b>그 사람에게</b> 하는 일이라 따로 둔다.
     *
     * <p>⚠️ 나간 사람은 버린다. 안 그러면 접속이 끊긴 뒤에 패킷을 쏜다.
     */
    @net.neoforged.fml.common.EventBusSubscriber(modid = LSRelics.MODID)
    public static final class Later {
        private Later() {}

        private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

        private interface Step { void run(ServerPlayer p); }

        private static final class Task {
            final ServerPlayer player;
            final Step step;
            int delay;
            Task(ServerPlayer player, int delay, Step step) {
                this.player = player; this.delay = delay; this.step = step;
            }
        }

        private static final java.util.List<Task> QUEUE = new java.util.ArrayList<>();

        static void run(ServerPlayer p, int ticks, Step step) {
            if (ticks <= 0) { step.run(p); return; }
            QUEUE.add(new Task(p, ticks, step));
        }

        /**
         * ⚠️ <b>순회를 먼저 끝내고, 실행은 그 밖에서 한다.</b>
         *
         * <p>처음엔 {@code QUEUE.removeIf(t -> { …; t.step.run(); … })} 한 줄이었다.
         * 그런데 <b>이 대본은 스스로 다음 걸음을 예약한다</b> — 「이동」 단계가 「눈을 뜬다」를
         * 다시 {@link #run} 으로 건다. 그 {@code QUEUE.add} 가 <b>removeIf 가 순회하는
         * 도중에</b> 일어나 {@code ConcurrentModificationException} 이 났다.
         *
         * <p>그리고 여기는 <b>서버 틱 핸들러</b>다 — 여기서 던진 예외는 그 틱을 죽이는 게 아니라
         * <b>서버를 통째로 내린다.</b> 실제로 그렇게 됐다(2026-08-12 23:45 크래시).
         * 그래서 걸음마다 {@code try/catch} 도 함께 둔다. 연출 하나가 잘못돼도
         * 서버가 죽을 이유는 없다.
         */
        @net.neoforged.bus.api.SubscribeEvent
        public static void onTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
            if (QUEUE.isEmpty()) return;

            java.util.List<Task> due = null;
            for (java.util.Iterator<Task> it = QUEUE.iterator(); it.hasNext(); ) {
                Task t = it.next();
                if (t.player.hasDisconnected() || t.player.getServer() == null) { it.remove(); continue; }
                if (--t.delay > 0) continue;
                it.remove();
                if (due == null) due = new java.util.ArrayList<>();
                due.add(t);
            }
            if (due == null) return;

            // 여기서부터는 QUEUE 를 순회하지 않는다 — 걸음이 새 걸음을 예약해도 안전하다.
            for (Task t : due) {
                try {
                    t.step.run(t.player);
                } catch (Exception e) {
                    LOG.error("[온보딩] 지연 걸음 실패 — {}",
                        t.player.getGameProfile().getName(), e);
                }
            }
        }
    }
}
