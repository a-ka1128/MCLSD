package com.laststardust.relics.town;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// 서버 → 클라로 넘기는 마을 현황 스냅샷.
//
// 클라는 LSData 를 볼 수 없으므로 화면에 필요한 값만 추려 보낸다.
// KubeJS 시절엔 JSON 문자열을 명령 인자로 실어 보냈는데(파싱 실패가 조용히 빈 화면이 됐다),
// 이제는 코덱으로 주고받아 필드가 어긋나면 컴파일이 막는다.
//
// ※ 이름·효과는 **해석된 문자열이 아니라 번역 키**로 실어 보낸다.
//   서버에서 Component.translatable(...).getString() 을 부르면 **서버의 언어**(보통 en_us)로
//   해석되어, 한국어 클라에도 영문이 뜬다. 번역은 반드시 클라에서 해야 한다.
public record TownView(int treasury, int threat, int wallHp, int wallMax,
                       List<TrackView> tracks, List<Contributor> board) {

    public record TrackView(String key, int level, int max,
                            String nextNameKey, String nextFxKey,
                            int ducat, String itemId, int need, int have,
                            boolean essence, boolean canUpgrade) {
        public boolean isMax() { return nextNameKey.isEmpty(); }

        // 클라 전용 — 번역은 여기서 한다.
        public net.minecraft.network.chat.Component nextName() {
            return net.minecraft.network.chat.Component.translatable(nextNameKey);
        }
        public net.minecraft.network.chat.Component nextFx() {
            return net.minecraft.network.chat.Component.translatable(nextFxKey);
        }
        // 아이템 이름도 클라의 언어로. 없는 아이템이면 ID 를 그대로 보여준다(원인 추적용).
        public net.minecraft.network.chat.Component itemName() {
            var rl = net.minecraft.resources.ResourceLocation.tryParse(itemId);
            if (rl != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl)) {
                return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl).getDescription();
            }
            return net.minecraft.network.chat.Component.literal(itemId);
        }
    }

    public record Contributor(String name, int points) {}

    private static final StreamCodec<RegistryFriendlyByteBuf, TrackView> TRACK_CODEC =
        StreamCodec.of((buf, v) -> {
            buf.writeUtf(v.key());
            buf.writeVarInt(v.level());
            buf.writeVarInt(v.max());
            buf.writeUtf(v.nextNameKey());
            buf.writeUtf(v.nextFxKey());
            buf.writeVarInt(v.ducat());
            buf.writeUtf(v.itemId());
            buf.writeVarInt(v.need());
            buf.writeVarInt(v.have());
            buf.writeBoolean(v.essence());
            buf.writeBoolean(v.canUpgrade());
        }, buf -> new TrackView(buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
            buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readUtf(),
            buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));

    private static final StreamCodec<RegistryFriendlyByteBuf, Contributor> CONTRIB_CODEC =
        StreamCodec.of((buf, v) -> { buf.writeUtf(v.name()); buf.writeVarInt(v.points()); },
            buf -> new Contributor(buf.readUtf(), buf.readVarInt()));

    public static final StreamCodec<RegistryFriendlyByteBuf, TownView> CODEC =
        StreamCodec.of((buf, v) -> {
            buf.writeVarInt(v.treasury());
            buf.writeVarInt(v.threat());
            buf.writeVarInt(v.wallHp());
            buf.writeVarInt(v.wallMax());
            TRACK_CODEC.apply(ByteBufCodecs.list()).encode(buf, v.tracks());
            CONTRIB_CODEC.apply(ByteBufCodecs.list()).encode(buf, v.board());
        }, buf -> new TownView(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            TRACK_CODEC.apply(ByteBufCodecs.list()).decode(buf),
            CONTRIB_CODEC.apply(ByteBufCodecs.list()).decode(buf)));

    public static TownView empty() {
        return new TownView(0, 0, 0, 0, new ArrayList<>(), new ArrayList<>());
    }

    public TrackView track(String key) {
        for (TrackView t : tracks) if (t.key().equals(key)) return t;
        return null;
    }
}
