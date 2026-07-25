package com.laststardust.relics;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

// KubeJS가 보낸 마을 현황 JSON을 화면이 쓰기 좋은 모양으로 옮겨 담는다.
//
// 관대하게 판다 — 필드가 비거나 모양이 달라도 예외를 던지지 않고 기본값으로 넘어간다.
// KubeJS 쪽(ls_town.js)을 고치는 일이 잦은데, 오타 하나로 화면이 통째로 안 열리면 곤란하다.
public final class TownState {

    public static final class Next {
        public String name = "";
        public String fx = "";
        public int ducat;
        public String item = "";     // 표시용 이름 (예: "균열 정수")
        public int need;
        public int have;
        public boolean essence;      // 균열 정수인가 (보스 처치로만 나오는 재화)
    }

    public static final class Track {
        public String key = "";
        public String icon = "";
        public String name = "";
        public String blurb = "";
        public int lv;
        public int max;
        public Next next;            // null = 최대 단계
        public boolean canUpgrade;   // 자원·Ducat 모두 충족
        public boolean resourceFull; // 자원만 충족 (Ducat 부족 표시용)
    }

    public static final class Contributor {
        public String name = "";
        public int pts;
    }

    public int treasury;
    public int threat;
    public int wallHp;
    public int wallMax;
    public boolean wallBroken;
    public boolean sanctuarySet;
    public final List<Track> tracks = new ArrayList<>();
    public final List<Contributor> board = new ArrayList<>();

    // 파싱 실패 시 null 대신 빈 상태를 돌려준다 — 호출부가 널 검사를 안 해도 되게.
    public static TownState parse(String json) {
        TownState st = new TownState();
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return st;
            JsonObject o = root.getAsJsonObject();
            st.treasury = getInt(o, "treasury");
            st.threat = getInt(o, "threat");
            st.sanctuarySet = getBool(o, "sanctuary");

            JsonObject wall = getObj(o, "wall");
            if (wall != null) {
                st.wallHp = getInt(wall, "hp");
                st.wallMax = getInt(wall, "max");
                st.wallBroken = getBool(wall, "broken");
            }

            JsonArray tracks = getArr(o, "tracks");
            if (tracks != null) {
                for (JsonElement e : tracks) {
                    if (!e.isJsonObject()) continue;
                    JsonObject t = e.getAsJsonObject();
                    Track tr = new Track();
                    tr.key = getStr(t, "key");
                    tr.icon = getStr(t, "icon");
                    tr.name = getStr(t, "name");
                    tr.blurb = getStr(t, "blurb");
                    tr.lv = getInt(t, "lv");
                    tr.max = getInt(t, "max");
                    tr.canUpgrade = getBool(t, "canUpgrade");
                    tr.resourceFull = getBool(t, "resourceFull");
                    JsonObject n = getObj(t, "next");
                    if (n != null) {
                        Next nx = new Next();
                        nx.name = getStr(n, "name");
                        nx.fx = getStr(n, "fx");
                        nx.ducat = getInt(n, "ducat");
                        nx.item = getStr(n, "item");
                        nx.need = getInt(n, "need");
                        nx.have = getInt(n, "have");
                        nx.essence = getBool(n, "essence");
                        tr.next = nx;
                    }
                    st.tracks.add(tr);
                }
            }

            JsonArray board = getArr(o, "board");
            if (board != null) {
                for (JsonElement e : board) {
                    if (!e.isJsonObject()) continue;
                    JsonObject c = e.getAsJsonObject();
                    Contributor ct = new Contributor();
                    ct.name = getStr(c, "name");
                    ct.pts = getInt(c, "pts");
                    st.board.add(ct);
                }
            }
        } catch (Exception ignored) {
            // 깨진 JSON이면 빈 화면이 뜬다. 게임을 멈추는 것보다 낫다.
        }
        return st;
    }

    private static String getStr(JsonObject o, String k) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }
        catch (Exception e) { return ""; }
    }
    private static int getInt(JsonObject o, String k) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : 0; }
        catch (Exception e) { return 0; }
    }
    private static boolean getBool(JsonObject o, String k) {
        try { return o.has(k) && !o.get(k).isJsonNull() && o.get(k).getAsBoolean(); }
        catch (Exception e) { return false; }
    }
    private static JsonObject getObj(JsonObject o, String k) {
        try { return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null; }
        catch (Exception e) { return null; }
    }
    private static JsonArray getArr(JsonObject o, String k) {
        try { return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : null; }
        catch (Exception e) { return null; }
    }
}
