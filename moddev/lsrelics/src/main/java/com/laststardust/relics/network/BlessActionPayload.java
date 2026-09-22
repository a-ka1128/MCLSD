package com.laststardust.relics.network;

import com.laststardust.relics.LSRelics;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 클라 → 서버: 제단에서 누른 버튼.
//   slot   = BlessingCatalog.Slot 의 이름 (WEAPON_1 …)
//   action = "bless" | "kind" | "value"
//
// 둘 다 **문자열 그대로 믿지 않는다** — 서버가 enum 과 대조해 거른다(LSNetwork).
// 슬롯이 열렸는지·재료가 있는지 같은 판정은 전부 BlessingService 안에 있다.
public record BlessActionPayload(String slot, String action) implements CustomPacketPayload {

    public static final Type<BlessActionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "bless_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlessActionPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BlessActionPayload::slot,
            ByteBufCodecs.STRING_UTF8, BlessActionPayload::action,
            BlessActionPayload::new);

    @Override
    public Type<BlessActionPayload> type() { return TYPE; }
}
