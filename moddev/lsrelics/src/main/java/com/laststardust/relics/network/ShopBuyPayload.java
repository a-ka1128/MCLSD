package com.laststardust.relics.network;

import com.laststardust.relics.LSRelics;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 클라 → 서버: 「몇 번째를 사겠다」.
 *
 * <p>⚠️ <b>번호만</b> 보낸다. 아이템과 값은 서버가 카탈로그에서 다시 푼다 —
 * 클라가 실어 보내게 두면 1 Ducat 짜리 다이아 요청을 막을 방법이 없다.
 */
public record ShopBuyPayload(int index) implements CustomPacketPayload {

    public static final Type<ShopBuyPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "shop_buy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopBuyPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, ShopBuyPayload::index, ShopBuyPayload::new);

    @Override
    public Type<ShopBuyPayload> type() { return TYPE; }
}
