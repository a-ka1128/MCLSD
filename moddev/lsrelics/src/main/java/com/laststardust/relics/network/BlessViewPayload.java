package com.laststardust.relics.network;

import com.laststardust.relics.LSRelics;
import com.laststardust.relics.blessing.BlessView;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 서버 → 클라: 제단 현황. 열려 있는 화면이 알아서 갱신한다.
// view.spun() 이 비어 있지 않으면 그 칸에서 **스핀 연출**을 시작한다 — 결과는 이미 확정된 뒤다.
public record BlessViewPayload(BlessView view) implements CustomPacketPayload {

    public static final Type<BlessViewPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "bless_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlessViewPayload> STREAM_CODEC =
        StreamCodec.composite(BlessView.CODEC, BlessViewPayload::view, BlessViewPayload::new);

    @Override
    public Type<BlessViewPayload> type() { return TYPE; }
}
