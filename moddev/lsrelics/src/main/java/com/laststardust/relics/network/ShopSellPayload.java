package com.laststardust.relics.network;

import com.laststardust.relics.LSRelics;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 클라 → 서버: 「몇 번째 물건을 팔겠다」. 가진 만큼 전부 판다.
 *
 * <p>⚠️ 개수를 클라가 보내지 않는다. 서버가 가방을 직접 세서 그만큼만 가져간다 —
 * 개수를 실어 보내게 두면 «안 가진 만큼» 팔 수 있다.
 */
public record ShopSellPayload(int index) implements CustomPacketPayload {

    public static final Type<ShopSellPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "shop_sell"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopSellPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, ShopSellPayload::index, ShopSellPayload::new);

    @Override
    public Type<ShopSellPayload> type() { return TYPE; }
}
