package com.laststardust.relics.relic;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 유물 제단 화면에 뿌릴 내용.
 *
 * <p>── 왜 «자유 항목» 인가 ──
 * 마을 화면({@code TownView})은 필드가 고정이다. 마을 규칙이 자바에 있기 때문이다.
 * 그런데 <b>유물 수치는 KubeJS 에 산다</b> — 성급별 피해 배수·비용·관문 게이트는
 * {@code ls_ascend.js} 에, 이름·조작법·전승은 {@code ls_relic.js} 에 있다.
 *
 * <p>그걸 자바로 <b>복사하면 안 된다.</b> 이 저장소가 반복해서 겪은 실패가 정확히 그거다
 * ({@code ls_siege.js} 머리말 「사본을 두지 않는다」, {@code ls_beacon.js} 「한쪽만 옮겼으면
 * 여기가 조용히 0 을 세고 하한 완화가 사라졌다」). 한쪽만 고치는 날이 반드시 온다.
 *
 * <p>그래서 <b>KubeJS 가 «무엇을 보여줄지» 를 채우고 자바는 «어떻게 그릴지» 만 맡는다.</b>
 * 수치는 지금 사는 자리에 그대로 둔다.
 *
 * <p>⚠️ 예전 방식(JSON 문자열을 명령 인자로)은 안 쓴다 — {@code TownViewPayload} 주석이
 * 이미 경고한다: 「파싱이 실패하면 조용히 빈 화면이 됐고 필드 이름이 어긋나도 아무도
 * 알려주지 않았다」. 여기는 코덱으로 주고받는다.
 */
public record RelicView(String title, String subtitle, String kind,
                        int star, int maxStar,
                        List<Row> rows, List<Ladder> ladder,
                        String lore, String echo,
                        boolean owned, String footer) {

    /** 수치 한 줄 — 「최대 체력   28칸 (+36)」. */
    public record Row(String label, String value, int color) {}

    /**
     * 성급 사다리 한 칸.
     *
     * @param reached 이미 도달한 성급인가 (밝게 그린다)
     * @param cost    이 성급으로 올라가는 데 드는 별의 파편. 0 이면 안 보여준다.
     */
    public record Ladder(int star, String desc, int cost, boolean reached) {}

    private static final StreamCodec<RegistryFriendlyByteBuf, Row> ROW_CODEC =
        StreamCodec.of((buf, v) -> {
            buf.writeUtf(v.label());
            buf.writeUtf(v.value());
            buf.writeVarInt(v.color());
        }, buf -> new Row(buf.readUtf(), buf.readUtf(), buf.readVarInt()));

    private static final StreamCodec<RegistryFriendlyByteBuf, Ladder> LADDER_CODEC =
        StreamCodec.of((buf, v) -> {
            buf.writeVarInt(v.star());
            buf.writeUtf(v.desc());
            buf.writeVarInt(v.cost());
            buf.writeBoolean(v.reached());
        }, buf -> new Ladder(buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readBoolean()));

    public static final StreamCodec<RegistryFriendlyByteBuf, RelicView> CODEC =
        StreamCodec.of((buf, v) -> {
            buf.writeUtf(v.title());
            buf.writeUtf(v.subtitle());
            buf.writeUtf(v.kind());
            buf.writeVarInt(v.star());
            buf.writeVarInt(v.maxStar());
            ROW_CODEC.apply(ByteBufCodecs.list()).encode(buf, v.rows());
            LADDER_CODEC.apply(ByteBufCodecs.list()).encode(buf, v.ladder());
            buf.writeUtf(v.lore());
            buf.writeUtf(v.echo());
            buf.writeBoolean(v.owned());
            buf.writeUtf(v.footer());
        }, buf -> new RelicView(buf.readUtf(), buf.readUtf(), buf.readUtf(),
            buf.readVarInt(), buf.readVarInt(),
            ROW_CODEC.apply(ByteBufCodecs.list()).decode(buf),
            LADDER_CODEC.apply(ByteBufCodecs.list()).decode(buf),
            buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readUtf()));

    public static RelicView empty() {
        return new RelicView("", "", "", 0, 5, new ArrayList<>(), new ArrayList<>(), "", "", false, "");
    }
}
