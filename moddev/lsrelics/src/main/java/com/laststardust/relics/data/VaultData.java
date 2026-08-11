package com.laststardust.relics.data;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * <h2>주간 별빛 금고 — 재클리어의 이유</h2>
 *
 * <p>세 목표를 채우면 칸이 하나씩 열리고, <b>열린 칸 중 딱 하나</b>에서만 받는다.
 * 많이 할수록 «양»이 아니라 «고를 수 있는 폭»이 는다 — WoW 의 Great Vault 가
 * 반복 콘텐츠를 붙잡아 두는 방식이 그거다. 셋 다 채우고 파편을 고르면 열쇠는 못 받는다.
 *
 * <p>── 왜 실시간 주인가 ──
 * 게임 내 하루가 실시간 18분이라, 게임 내 「일주일」은 두 시간이 조금 넘는다.
 * 현상금이 <b>3일 주기</b>(54분)인 것도 같은 이유로 정해졌다. 주간 금고까지 게임 내
 * 시간으로 재면 한 세션 안에 두세 번 리셋되어 <b>「주간」이라는 말이 뜻을 잃는다.</b>
 * 이건 세션을 가로지르는 장치라 실시간이어야 한다 — <b>월요일 00시</b>에 리셋된다.
 *
 * <p>── 진행도는 사람마다 따로다 ──
 * 공동 금고(현상금·공성 보상)와 반대다. 이 장치의 목적은 «각자 이번 주에 뭘 했나»이고,
 * 공동으로 세면 한 명이 다 채운 주에 나머지가 아무것도 안 해도 받는다.
 * 그러면 재클리어의 이유가 정확히 사라진다.
 */
public class VaultData {

    /** 목표 셋. 순서가 곧 칸 번호(1·2·3)다. */
    public static final int SLOTS = 3;

    /** 공성 방어 성공 — 3일에 한 번 오므로 한 주에 3회면 «거의 다 나왔다»가 된다. */
    public static final int NEED_SIEGE  = 3;
    /** 관문 원정(열쇠 던전) 클리어 — 열쇠 값이 있어서 2회가 적당하다. */
    public static final int NEED_GATE   = 2;
    /** 현상금 완료 — 3일마다 3건 걸리므로 한 주(≈2.3주기)면 5건이 자연스럽다. */
    public static final int NEED_BOUNTY = 5;

    public static int need(int slot) {
        return switch (slot) {
            case 1 -> NEED_SIEGE;
            case 2 -> NEED_GATE;
            case 3 -> NEED_BOUNTY;
            default -> 0;
        };
    }

    public static final class Row {
        public int siege;
        public int gate;
        public int bounty;
        /** 이번 주에 이미 받았는가. 칸이 셋이어도 <b>수령은 한 번</b>이다. */
        public boolean claimed;

        public int have(int slot) {
            return switch (slot) {
                case 1 -> siege;
                case 2 -> gate;
                case 3 -> bounty;
                default -> 0;
            };
        }
    }

    private final Map<String, Row> rows = new HashMap<>();

    /**
     * 지금이 몇 번째 주인가. {@code 연도 × 100 + ISO 주차}.
     *
     * <p>연도를 섞는 이유: 주차만 쓰면 <b>52주 뒤에 같은 번호가 돌아와</b> 리셋이 안 된다.
     * 서버가 해를 넘겨 도는 물건이라 언젠가는 반드시 겪는다.
     */
    public static int currentWeek() {
        LocalDate d = LocalDate.now();
        WeekFields wf = WeekFields.of(Locale.KOREA);   // 월요일 시작
        return d.get(wf.weekBasedYear()) * 100 + d.get(wf.weekOfWeekBasedYear());
    }

    private int week = currentWeek();

    public int week() { return week; }

    /**
     * 주가 바뀌었으면 전원의 진행도를 지운다. <b>읽기 전에 반드시 부른다.</b>
     *
     * @return 실제로 리셋했으면 true (스크립트가 그때만 안내를 띄운다)
     */
    public boolean rollover() {
        int now = currentWeek();
        if (now == week) return false;
        week = now;
        rows.clear();   // 안 받은 보상은 사라진다 — 그게 「주간」의 긴장이다
        return true;
    }

    private Row row(String name) {
        return rows.computeIfAbsent(name == null ? "" : name, k -> new Row());
    }

    public int have(String name, int slot)  { return row(name).have(slot); }
    public boolean claimed(String name)     { return row(name).claimed; }

    /** 그 칸이 채워졌는가(= 열렸는가). */
    public boolean open(String name, int slot) {
        int n = need(slot);
        return n > 0 && have(name, slot) >= n;
    }

    /** 열린 칸 수. 표시용 — 보상 «개수»가 아니다(수령은 언제나 한 번). */
    public int openCount(String name) {
        int c = 0;
        for (int i = 1; i <= SLOTS; i++) if (open(name, i)) c++;
        return c;
    }

    /**
     * 진행도를 올린다. 필요치를 넘겨도 그대로 쌓아 둔다 —
     * 상한을 걸면 {@code 5/5} 에서 멈춰 「이번 주에 얼마나 더 했나」가 안 보인다.
     */
    public void add(String name, int slot, int n) {
        Row r = row(name);
        switch (slot) {
            case 1 -> r.siege  += n;
            case 2 -> r.gate   += n;
            case 3 -> r.bounty += n;
            default -> { }
        }
    }

    /**
     * 수령 처리. 이미 받았거나 그 칸이 안 열렸으면 false —
     * <b>판정이 여기 하나뿐</b>이라야 스크립트 두 곳에서 각자 검사하다 어긋나지 않는다.
     */
    public boolean claim(String name, int slot) {
        Row r = row(name);
        if (r.claimed || !open(name, slot)) return false;
        r.claimed = true;
        return true;
    }

    /** 관리자 초기화 — 시험용. */
    public void reset(String name) {
        if (name == null || name.isEmpty()) rows.clear();
        else rows.remove(name);
    }

    // ── 저장 ──
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        rows.clear();
        // 없으면 «이번 주»로 시작한다. 0 으로 두면 첫 로드가 곧바로 리셋으로 잡혀
        // 새 월드에서 안내문이 한 번 헛나온다.
        week = tag.contains("week") ? tag.getInt("week") : currentWeek();
        CompoundTag p = tag.getCompound("players");
        for (String name : p.getAllKeys()) {
            CompoundTag t = p.getCompound(name);
            Row r = new Row();
            r.siege   = t.getInt("siege");
            r.gate    = t.getInt("gate");
            r.bounty  = t.getInt("bounty");
            r.claimed = t.getBoolean("claimed");
            rows.put(name, r);
        }
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("week", week);
        CompoundTag p = new CompoundTag();
        rows.forEach((name, r) -> {
            CompoundTag t = new CompoundTag();
            t.putInt("siege", r.siege);
            t.putInt("gate", r.gate);
            t.putInt("bounty", r.bounty);
            t.putBoolean("claimed", r.claimed);
            p.put(name, t);
        });
        tag.put("players", p);
        return tag;
    }
}
