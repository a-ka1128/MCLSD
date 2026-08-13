package com.laststardust.relics.shop;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 상점 화면에 뿌릴 내용 — 잔액과 목록.
 *
 * <p>목록을 «서버가 정한 그대로» 보낸다. 클라가 카탈로그를 따로 갖고 있으면
 * 값을 고쳤을 때 둘이 갈라지고, 갈라진 걸 아무도 모른 채 「샀는데 값이 다르다」가 된다.
 */
public record ShopView(int balance, List<Row> rows) {

    /**
     * @param index  서버 목록에서의 번호. 살 때 이 번호를 보낸다 —
     *               아이템 id 를 보내면 클라가 아무거나 적어 보낼 수 있다.
     * @param afford 지금 살 수 있는가. 판정은 서버가 하고 클라는 그리기만 한다.
     */
    public record Row(int index, String id, int count, int price, String group, boolean afford) {
        public net.minecraft.world.item.ItemStack stack() {
            var rl = net.minecraft.resources.ResourceLocation.tryParse(id);
            if (rl == null) return net.minecraft.world.item.ItemStack.EMPTY;
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
            if (item == null) return net.minecraft.world.item.ItemStack.EMPTY;
            return new net.minecraft.world.item.ItemStack(item, count);
        }
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, Row> ROW = StreamCodec.of(
        (buf, v) -> {
            buf.writeVarInt(v.index());
            buf.writeUtf(v.id());
            buf.writeVarInt(v.count());
            buf.writeVarInt(v.price());
            buf.writeUtf(v.group());
            buf.writeBoolean(v.afford());
        },
        buf -> new Row(buf.readVarInt(), buf.readUtf(), buf.readVarInt(),
            buf.readVarInt(), buf.readUtf(), buf.readBoolean()));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopView> CODEC = StreamCodec.of(
        (buf, v) -> {
            buf.writeVarInt(v.balance());
            ROW.apply(ByteBufCodecs.list()).encode(buf, v.rows());
        },
        buf -> new ShopView(buf.readVarInt(), ROW.apply(ByteBufCodecs.list()).decode(buf)));

    public static ShopView empty() { return new ShopView(0, new ArrayList<>()); }
}
