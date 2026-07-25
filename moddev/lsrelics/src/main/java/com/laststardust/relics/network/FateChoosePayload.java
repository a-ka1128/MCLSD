package com.laststardust.relics.network;

import com.laststardust.relics.LSRelics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 클라 → 서버: 이 가호를 선택하겠다.
// ※ key 는 클라가 보낸 값이므로 서버에서 FateCatalog 화이트리스트로 반드시 검사한 뒤 쓴다.
public record FateChoosePayload(String key) implements CustomPacketPayload {

    public static final Type<FateChoosePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "fate_choose"));

    public static final StreamCodec<FriendlyByteBuf, FateChoosePayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8, FateChoosePayload::key, FateChoosePayload::new);

    @Override
    public Type<FateChoosePayload> type() {
        return TYPE;
    }
}
