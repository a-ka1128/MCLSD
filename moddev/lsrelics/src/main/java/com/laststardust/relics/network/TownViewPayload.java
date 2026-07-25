package com.laststardust.relics.network;

import com.laststardust.relics.LSRelics;
import com.laststardust.relics.town.TownView;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 서버 → 클라: 마을 현황 스냅샷. 허브를 열거나, 이미 열려 있으면 갱신한다.
//
// 예전엔 JSON 문자열을 명령 인자로 실어 보냈다. 파싱이 실패하면 조용히 빈 화면이 됐고
// 필드 이름이 어긋나도 아무도 알려주지 않았다. 이제 코덱이라 어긋나면 컴파일이 막는다.
// openHub = true 면 **무조건 허브를 연다**. false 면 지금 열려 있는 화면을 갱신만 한다.
//
// 이 구분이 없으면 트랙 창에서 '뒤로'를 눌러도 클라가 "열려 있는 트랙 창을 갱신"으로 처리해
// 화면이 그대로 남는다(개수만 새로 뜬다). 서버의 의도를 값으로 실어야 한다.
public record TownViewPayload(TownView view, boolean openHub) implements CustomPacketPayload {

    public static final Type<TownViewPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "town_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TownViewPayload> STREAM_CODEC =
        StreamCodec.composite(
            TownView.CODEC, TownViewPayload::view,
            net.minecraft.network.codec.ByteBufCodecs.BOOL, TownViewPayload::openHub,
            TownViewPayload::new);

    @Override
    public Type<TownViewPayload> type() { return TYPE; }
}
