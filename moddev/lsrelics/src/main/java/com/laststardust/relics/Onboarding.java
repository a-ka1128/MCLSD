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
 * 성역 좌표는 {@link LSData} 가 유일하게 갖는다({@code /sanctuary set}).
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
            return "성역이 아직 정해지지 않았다 — 먼저 §e/sanctuary set";
        }
        BlockPos to = data.sanctuary();
        ServerLevel overworld = server.overworld();

        // ── 떠나는 자리 ──
        if (p.level() instanceof ServerLevel from) {
            from.sendParticles(ParticleTypes.PORTAL,
                p.getX(), p.getY() + 1, p.getZ(), 80, 0.4, 0.9, 0.4, 0.5);
            from.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0f, 0.8f);
        }

        p.teleportTo(overworld, to.getX() + 0.5, to.getY() + 1, to.getZ() + 0.5,
            p.getYRot(), p.getXRot());

        // ── 도착한 자리 ──
        overworld.sendParticles(ParticleTypes.END_ROD,
            to.getX() + 0.5, to.getY() + 1.4, to.getZ() + 0.5, 90, 0.6, 1.0, 0.6, 0.06);
        overworld.sendParticles(ParticleTypes.FLASH,
            to.getX() + 0.5, to.getY() + 1.5, to.getZ() + 0.5, 1, 0, 0, 0, 0);
        overworld.playSound(null, to, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
        overworld.playSound(null, to, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.7f);
        SoundScheduler.at(overworld, p.position(), SoundEvents.BEACON_ACTIVATE, 1.2f, 0.8f, 20);

        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§b성역")));
        p.connection.send(new ClientboundSetSubtitleTextPacket(
            Component.literal("§7부서진 별이 떨어진 자리")));

        // ── 가호 선택은 조금 늦게 ──
        // ⚠️ 이미 가호가 있으면 {@code openScreen} 이 스스로 건너뛴다 — NPC 와 두 번째로
        //    대화하는 경우가 그렇다. 다시 못 고르는데 창만 열리면 「고를 수 있나 보다」로 읽힌다.
        FateAutoOpen.openLater(p, SETTLE_TICKS);
        return null;
    }
}
