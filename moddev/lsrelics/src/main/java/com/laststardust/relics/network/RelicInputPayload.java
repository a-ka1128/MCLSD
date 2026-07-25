package com.laststardust.relics.network;

import com.laststardust.relics.LSRelics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 클라 → 서버: 유물 입력.
//
// 스킬은 전부 전용 키로 뺐다. 예전엔 웅크림+좌/우클릭 조합이었는데 바닐라 조작과 부딪혔다:
//   · 절벽에서 웅크린 채 공격 → 60초 궁극기가 헛발
//   · 웅크리고 상자 열기 → 상자는 안 열리고 스킬이 나감(웅크림이 블록 상호작용을 막는다)
//   · 절벽을 내려다보려 쉬프트 두 번 → 밖으로 돌진
// 이제 좌클릭은 평타 전용, 우클릭은 비워둔다(거너 스코프만 예외).
public record RelicInputPayload(int action) implements CustomPacketPayload {
    public static final int ATTACK = 0;   // 좌클릭 홀드(평타)
    public static final int ULTIMATE = 1; // X (궁극·4성)
    public static final int DODGE = 2;    // 쉬프트 두 번(이동기·2성)
    public static final int BASIC = 3;    // R (기본·1성 / 거너는 수동 재장전)
    public static final int EXTRA = 4;    // C (추가·3성)

    public static final Type<RelicInputPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "relic_input"));

    public static final StreamCodec<FriendlyByteBuf, RelicInputPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, RelicInputPayload::action, RelicInputPayload::new);

    @Override
    public Type<RelicInputPayload> type() {
        return TYPE;
    }
}
