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

    /** 요구 자원 한 줄. 한 레벨이 여러 개를 가질 수 있다(2026-08-08). */
    public record ReqView(String id, boolean isTag, int need, int have, boolean essence) {
        public boolean ok() { return have >= need; }

        // 클라 전용 — 번역은 여기서 한다.
        //
        // 태그는 아이템 이름을 쓰면 안 된다. `minecraft:logs` 의 첫 원소가 참나무 원목이라
        // 「참나무 원목 128」로 보이는데 실제로는 아무 나무나 받는다 — 정반대로 읽힌다.
        // 그래서 태그는 전용 번역 키(`lstown.req.tag.<path>`)를 쓴다.
        public net.minecraft.network.chat.Component displayName() {
            var rl = net.minecraft.resources.ResourceLocation.tryParse(id);
            if (rl == null) return net.minecraft.network.chat.Component.literal(id);
            if (isTag) {
                return net.minecraft.network.chat.Component.translatable("lstown.req.tag." + rl.getPath());
            }
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl)) {
                return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl).getDescription();
            }
            // 없는 아이템이면 ID 를 그대로 보여준다(원인 추적용).
            return net.minecraft.network.chat.Component.literal(id);
        }

        /** 빈 슬롯에 흐리게 그릴 대표 아이템. */
        public net.minecraft.world.item.ItemStack icon() {
            var rl = net.minecraft.resources.ResourceLocation.tryParse(id);
            if (rl == null) return net.minecraft.world.item.ItemStack.EMPTY;
            if (isTag) {
                var key = net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM, rl);
                var tag = net.minecraft.core.registries.BuiltInRegistries.ITEM.getTag(key);
                if (tag.isPresent()) {
                    for (var holder : tag.get()) return new net.minecraft.world.item.ItemStack(holder.value());
                }
                return net.minecraft.world.item.ItemStack.EMPTY;
            }
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl)) {
                return new net.minecraft.world.item.ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl));
            }
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
    }

    public record TrackView(String key, int level, int max,
                            String nextNameKey, String nextFxKey,
                            int ducat, List<ReqView> reqs, boolean canUpgrade) {
        public boolean isMax() { return nextNameKey.isEmpty(); }

        // 클라 전용 — 번역은 여기서 한다.
        public net.minecraft.network.chat.Component nextName() {
            return net.minecraft.network.chat.Component.translatable(nextNameKey);
        }
        public net.minecraft.network.chat.Component nextFx() {
            return net.minecraft.network.chat.Component.translatable(nextFxKey);
        }

        /** 허브·`/town info` 처럼 한 줄로 줄여야 하는 곳에서 쓴다 — "철괴 12/64 · 금괴 0/96". */
        public String shortCost() {
            StringBuilder sb = new StringBuilder();
            for (ReqView r : reqs) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(r.displayName().getString()).append(' ').append(r.have()).append('/').append(r.need());
            }
            return sb.toString();
        }
    }

    public record Contributor(String name, int points) {}

    private static final StreamCodec<RegistryFriendlyByteBuf, ReqView> REQ_CODEC =
        StreamCodec.of((buf, v) -> {
            buf.writeUtf(v.id());
            buf.writeBoolean(v.isTag());
            buf.writeVarInt(v.need());
            buf.writeVarInt(v.have());
            buf.writeBoolean(v.essence());
        }, buf -> new ReqView(buf.readUtf(), buf.readBoolean(),
            buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

    private static final StreamCodec<RegistryFriendlyByteBuf, TrackView> TRACK_CODEC =
        StreamCodec.of((buf, v) -> {
            buf.writeUtf(v.key());
            buf.writeVarInt(v.level());
            buf.writeVarInt(v.max());
            buf.writeUtf(v.nextNameKey());
            buf.writeUtf(v.nextFxKey());
            buf.writeVarInt(v.ducat());
            REQ_CODEC.apply(ByteBufCodecs.list()).encode(buf, v.reqs());
            buf.writeBoolean(v.canUpgrade());
        }, buf -> new TrackView(buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
            buf.readUtf(), buf.readUtf(), buf.readVarInt(),
            REQ_CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readBoolean()));

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
