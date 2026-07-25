package com.laststardust.relics;

import java.util.List;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

// 가호 8종의 "표시 정보"만 갖는다.
// 실제 가호 로직(패시브 부여·시작 키트·저장·중복 방지)은 전부 KubeJS(ls_fate.js)에 있고,
// 선택 화면은 그 로직을 /fate choose <key> 명령으로 호출하기만 한다 — 규칙이 두 곳에 생기지 않게.
//
// 문구는 lang 파일(assets/lsrelics/lang/ko_kr.json)에 둔다. 화면에 그릴 아이콘은 그 가호의 유물이다.
public final class FateCatalog {
    private FateCatalog() {}

    public record Fate(String key, DeferredItem<? extends Item> relic, int color) {
        public String nameKey()    { return "lsfate." + key + ".name"; }
        public String epithetKey() { return "lsfate." + key + ".epithet"; }
        public String roleKey()    { return "lsfate." + key + ".role"; }
        public String passiveKey() { return "lsfate." + key + ".passive"; }
        public String hookKey()    { return "lsfate." + key + ".hook"; }
        public String loreKey()    { return "lsfate." + key + ".lore"; }
    }

    // 순서 = 선택 화면에 표시되는 순서 (ls_fate.js 의 FATE_KEYS 와 같은 순서로 맞춘다)
    // 색은 디스코드 클래스 카드(discord/post_*.py)와 같은 값을 쓴다 — 두 곳의 인상이 어긋나지 않게.
    public static final List<Fate> ALL = List.of(
        new Fate("guardian", LSRelics.GUARDIAN, 0xC9D6E8), // 은백
        new Fate("hunter",   LSRelics.HUNTER,   0x2D6FD9), // 사파이어 블루
        new Fate("sage",     LSRelics.SAGE,     0xB57EDC), // 라벤더
        new Fate("pioneer",  LSRelics.PIONEER,  0xE8B24A), // 골드
        new Fate("gunner",   LSRelics.GUNNER,   0xF7931E), // 태양 오렌지
        new Fate("healer",   LSRelics.HEALER,   0x2ECC71), // 에메랄드
        new Fate("assassin", LSRelics.ASSASSIN, 0x9400D3), // 다크 바이올렛
        new Fate("lancer",   LSRelics.LANCER,   0xDC143C)  // 크림슨 레드
    );

    // 클라가 보낸 문자열을 그대로 명령에 넣지 않기 위한 화이트리스트.
    public static boolean isValid(String key) {
        for (Fate f : ALL) {
            if (f.key().equals(key)) return true;
        }
        return false;
    }
}
