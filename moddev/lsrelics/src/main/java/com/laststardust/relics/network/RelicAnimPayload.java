package com.laststardust.relics.network;

import com.laststardust.relics.LSRelics;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 서버 → 클라: 「이 플레이어가 지금 이 동작을 한다」.
 *
 * <p>플레이어 애니메이션은 <b>클라이언트에만</b> 존재한다(PlayerAnimator 는 클라 라이브러리다).
 * 그래서 서버가 스킬을 시작할 때 보는 사람들에게 알려야 한다 — 자기 자신 포함
 * ({@code sendToPlayersTrackingEntityAndSelf}). 자신을 빼면 1인칭에서 아무 일도 안 일어난다.
 *
 * <p>{@code anim} 을 정수로 둔 이유: 앞으로 다른 유물도 자세를 갖게 되면 여기에 번호만 늘린다.
 * 문자열로 두면 오타가 런타임까지 살아남는다.
 */
public record RelicAnimPayload(int entityId, int anim) implements CustomPacketPayload {

    /** X「일도양단」 — 치켜들었다 내려찍는다. */
    public static final int SUNDER = 0;

    public static final Type<RelicAnimPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "relic_anim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RelicAnimPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RelicAnimPayload::entityId,
            ByteBufCodecs.VAR_INT, RelicAnimPayload::anim,
            RelicAnimPayload::new);

    @Override
    public Type<RelicAnimPayload> type() { return TYPE; }
}
