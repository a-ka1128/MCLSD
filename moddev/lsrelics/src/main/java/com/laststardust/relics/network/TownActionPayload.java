package com.laststardust.relics.network;

import com.laststardust.relics.LSRelics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 클라 → 서버: 마을 화면에서 버튼을 눌렀다.
//   action = "contribute" | "upgrade" | "repair"
//   arg    = 트랙 키(contribute/upgrade) 또는 수리량(repair)
//
// ※ 둘 다 클라가 보낸 값이므로 서버에서 화이트리스트로 반드시 거른 뒤 명령에 넣는다.
//   (FateChoosePayload 와 같은 원칙 — 클라 입력을 그대로 명령 문자열에 이어붙이면 안 된다)
public record TownActionPayload(String action, String arg) implements CustomPacketPayload {

    public static final Type<TownActionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "town_action"));

    public static final StreamCodec<FriendlyByteBuf, TownActionPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TownActionPayload::action,
            ByteBufCodecs.STRING_UTF8, TownActionPayload::arg,
            TownActionPayload::new);

    @Override
    public Type<TownActionPayload> type() {
        return TYPE;
    }
}
