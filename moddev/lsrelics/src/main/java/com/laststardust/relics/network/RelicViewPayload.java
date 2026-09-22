package com.laststardust.relics.network;

import com.laststardust.relics.LSRelics;
import com.laststardust.relics.relic.RelicView;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 서버 → 클라: 유물 제단 화면을 연다.
 *
 * <p>마을과 달리 «갱신» 갈래가 없다 — 제단은 우클릭할 때마다 새로 쌓아 보낸다.
 * 열려 있는 화면을 뒤에서 갈아 끼울 일이 없으므로 {@code openHub} 같은 구분이 필요 없다.
 */
public record RelicViewPayload(RelicView view) implements CustomPacketPayload {

    public static final Type<RelicViewPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "relic_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RelicViewPayload> STREAM_CODEC =
        StreamCodec.composite(RelicView.CODEC, RelicViewPayload::view, RelicViewPayload::new);

    @Override
    public Type<RelicViewPayload> type() { return TYPE; }
}
