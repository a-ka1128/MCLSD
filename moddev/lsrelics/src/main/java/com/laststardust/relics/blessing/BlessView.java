package com.laststardust.relics.blessing;

import java.util.ArrayList;
import java.util.List;

import com.laststardust.relics.data.BlessingCatalog;
import com.laststardust.relics.data.BlessingCatalog.Slot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 서버 → 클라로 넘기는 제단 현황.
 *
 * <p>{@link com.laststardust.relics.town.TownView} 와 같은 규칙이다 — 클라는 {@code LSData} 를
 * 못 보므로 화면에 필요한 값만 추려 코덱으로 보낸다. 필드가 어긋나면 컴파일이 막는다.
 *
 * <p>이름·설명은 <b>번역 키</b>가 아니라 <b>축복 id</b> 로 보낸다. 클라도
 * {@link BlessingCatalog} 를 그대로 갖고 있어서(공용 코드다) id 하나면 이름·범위·설명이 전부 나온다.
 * 그래서 스핀 연출이 후보 목록을 서버에 묻지 않고 혼자 돌 수 있다.
 */
public record BlessView(int star, boolean altarUnlocked, boolean altarUpgraded,
                        int kindCost, int valueCost,
                        List<SlotView> slots, String spun) {

    /** @param id 비어 있으면 «아직 축복 없음». */
    public record SlotView(String slot, boolean unlocked, String id, float value) {
        public Slot def() {
            try { return Slot.valueOf(slot); } catch (IllegalArgumentException e) { return null; }
        }
        public boolean empty() { return id.isEmpty(); }
        public BlessingCatalog.Blessing blessing() {
            return id.isEmpty() ? null : BlessingCatalog.byId(id);
        }
        /** 범위의 몇 %인가 (0~1). 저장하지 않고 역산한다 — 값과 표시가 어긋날 수가 없다. */
        public float percentile() {
            BlessingCatalog.Blessing b = blessing();
            return b == null ? 0f : b.percentileOf(value);
        }
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, SlotView> SLOT_CODEC =
        StreamCodec.of((buf, v) -> {
            buf.writeUtf(v.slot());
            buf.writeBoolean(v.unlocked());
            buf.writeUtf(v.id());
            buf.writeFloat(v.value());
        }, buf -> new SlotView(buf.readUtf(), buf.readBoolean(), buf.readUtf(), buf.readFloat()));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlessView> CODEC =
        StreamCodec.of((buf, v) -> {
            buf.writeVarInt(v.star());
            buf.writeBoolean(v.altarUnlocked());
            buf.writeBoolean(v.altarUpgraded());
            buf.writeVarInt(v.kindCost());
            buf.writeVarInt(v.valueCost());
            SLOT_CODEC.apply(ByteBufCodecs.list()).encode(buf, v.slots());
            buf.writeUtf(v.spun());
        }, buf -> new BlessView(buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
            buf.readVarInt(), buf.readVarInt(),
            SLOT_CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readUtf()));

    public static BlessView empty() {
        return new BlessView(0, false, false, 0, 0, new ArrayList<>(), "");
    }

    public SlotView slot(Slot s) {
        for (SlotView v : slots) if (v.slot().equals(s.name())) return v;
        return null;
    }
}
