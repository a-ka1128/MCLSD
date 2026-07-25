package com.laststardust.relics.network;

import com.laststardust.relics.LSRelics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 클라 → 서버: 마을 관리 화면을 열고 싶다 (키바인드).
// 내용이 없는 신호 패킷 — 서버가 /town 을 대신 실행해 주고, 실제 데이터는 KubeJS가 만든다.
public record TownOpenPayload() implements CustomPacketPayload {

    public static final TownOpenPayload INSTANCE = new TownOpenPayload();

    public static final Type<TownOpenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "town_open"));

    // 필드가 없으므로 읽지도 쓰지도 않는다.
    public static final StreamCodec<FriendlyByteBuf, TownOpenPayload> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public Type<TownOpenPayload> type() {
        return TYPE;
    }
}
