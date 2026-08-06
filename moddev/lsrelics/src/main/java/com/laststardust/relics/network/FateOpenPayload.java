package com.laststardust.relics.network;

import com.laststardust.relics.LSRelics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 서버 → 클라: 가호 선택 화면을 열어라.
//
//   current  이미 선택한 가호 키(없으면 빈 문자열). 화면이 "이미 선택함" 상태를 표시하는 데 쓴다.
//   taken    **남이 이미 가진 가호** — `키:주인,키:주인` 형식. 내 것은 안 들어간다.
//
// ── taken 을 나르는 이유 (2026-08-06) ──
// 중복 방지 판정은 원래도 있었다(`ls_fate.js` 의 `ftChoose` → `LS.fateOwner`). 그런데 화면이
// 그 사실을 모르니 **고르고 확정을 누른 뒤에야** 거절당했다. 화면은 이미 닫힌 뒤라 채팅으로만
// 알게 되고, 8종·8인이라 마지막 사람은 그걸 일곱 번 겪는다.
// 목록에 회색으로 「철수가 받았다」라고 적혀 있으면 그 왕복이 통째로 사라진다.
//
// **판정을 옮기는 게 아니다.** 서버는 여전히 `ftChoose` 에서 막는다 — 이건 표시일 뿐이고,
// 클라가 보낸 값을 믿는 자리는 어디에도 안 생긴다. (열 때의 스냅샷이라 그 사이에 남이 먼저
// 고를 수 있고, 그때는 서버가 거절한 뒤 새 목록으로 화면을 다시 연다.)
//
// CSV 로 나르는 이유: 이관 4단계 노드와 같다. Map 코덱을 새로 짜는 것보다 형식 하나가 낫고,
// 키는 우리가 만드는 8개짜리 화이트리스트라 구분자가 들어갈 일이 없다.
// (사람 이름에도 `,`·`:` 는 못 들어간다 — 마인크래프트 규격이 영숫자와 `_` 뿐이다.)
public record FateOpenPayload(String current, String taken) implements CustomPacketPayload {

    public static final Type<FateOpenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "fate_open"));

    public static final StreamCodec<FriendlyByteBuf, FateOpenPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, FateOpenPayload::current,
            ByteBufCodecs.STRING_UTF8, FateOpenPayload::taken,
            FateOpenPayload::new);

    @Override
    public Type<FateOpenPayload> type() {
        return TYPE;
    }
}
