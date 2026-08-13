package com.laststardust.relics.network;

import com.laststardust.relics.LSRelics;
import com.laststardust.relics.shop.ShopView;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 서버 → 클라: 상점 화면을 열거나 갱신한다. */
public record ShopViewPayload(ShopView view) implements CustomPacketPayload {

    public static final Type<ShopViewPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "shop_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopViewPayload> STREAM_CODEC =
        StreamCodec.composite(ShopView.CODEC, ShopViewPayload::view, ShopViewPayload::new);

    @Override
    public Type<ShopViewPayload> type() { return TYPE; }
}
