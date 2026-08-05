package com.laststardust.relics.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// Last Stardust 서버 상태의 단일 보관소.
//
// ── 왜 모드로 옮기는가 ──
// 지금까지 상태는 전부 KubeJS 의 level.persistentData 에 있었다. 스크립트라 빠르게 붙일 수 있었지만
// 대가가 컸다 — 실제로 겪은 것만:
//   · runCommandSilent() 가 void 라 개수 세기가 5개 시스템에서 조용히 죽어 있었다
//   · Rhino 는 try 블록 안의 const 를 상위 스코프로 흘려 'redeclaration' 으로 터진다
//   · 서버 스크립트가 전역 스코프를 공유해 파일이 달라도 이름이 부딪힌다
//   · NaN 은 모든 비교가 false 라 부족 검사를 공짜로 통과한다
// 넷 다 **문법 검사로는 안 잡히고 런타임에만 터진다.** 컴파일러가 있으면 넷 다 빌드에서 걸린다.
//
// ── 구조 ──
// 하나의 SavedData 아래에 기능별 섹션(TownData, ...)을 둔다. 새 기능은 섹션 클래스 하나를 만들고
// 여기에 필드로 걸면 끝 — 저장·로드는 자동으로 따라온다.
// 데이터는 오버월드에 붙는다(차원과 무관한 서버 전역 상태이므로 기준 차원 하나로 통일).
public class LSData extends SavedData {

    public static final String FILE_ID = "laststardust";

    private final TownData town = new TownData();
    private final HeroData hero = new HeroData();
    private final SiegeData siege = new SiegeData();

    // 성역 좌표 — 여러 시스템이 공유하는 가장 넓게 퍼진 상태다.
    // (공성·관문·구출·통계·귀환석이 전부 이걸 본다)
    private int sancX, sancY, sancZ;
    private boolean sancSet;

    public TownData town() {
        return town;
    }

    // 가호·유물·각성 — 사람에 붙는 성장 상태 (이관 3단계).
    public HeroData hero() {
        return hero;
    }

    // 공성 — 위협도·성벽·노드·최종장 (이관 4단계).
    // 위협도는 성역 좌표만큼 넓게 퍼져 있다: 희망 게이지·호데고스 대사·몹 스케일링이 전부 이걸 본다.
    public SiegeData siege() {
        return siege;
    }

    // 관문 진행도 0~4 — 클리어한 봉인 수.
    // 성역 좌표만큼 넓게 퍼져 있다: 몹 스케일링 티어·각성 관문 조건·최종장 발동·통계 표시가
    // 전부 이 값 하나를 본다. 그래서 여기 한 곳에만 둔다.
    // 상한을 여기서 걸어둔다 — 호출부(스크립트)에 맡기면 한 곳이 빠뜨렸을 때 조용히 어긋난다.
    public static final int MAX_PROGRESS = 4;
    private int progress;

    public boolean hasSanctuary() { return sancSet; }
    public net.minecraft.core.BlockPos sanctuary() {
        return new net.minecraft.core.BlockPos(sancX, sancY, sancZ);
    }
    public void setSanctuary(int x, int y, int z) {
        sancX = x; sancY = y; sancZ = z; sancSet = true;
    }

    public int progress() { return progress; }
    public void setProgress(int n) {
        progress = Math.max(0, Math.min(MAX_PROGRESS, n));
    }

    // ── 접근 ──
    // 항상 오버월드에 붙은 인스턴스를 돌려준다. 어느 차원에서 불러도 같은 데이터를 본다.
    public static LSData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public static LSData get(ServerLevel anyLevel) {
        return get(anyLevel.getServer());
    }

    private static SavedData.Factory<LSData> factory() {
        return new SavedData.Factory<>(LSData::new, LSData::load, null);
    }

    private static LSData load(CompoundTag tag, HolderLookup.Provider registries) {
        LSData data = new LSData();
        data.town.load(tag.getCompound("town"), registries);
        data.hero.load(tag.getCompound("hero"), registries);
        data.siege.load(tag.getCompound("siege"), registries);
        CompoundTag s = tag.getCompound("sanctuary");
        data.sancSet = s.getBoolean("set");
        data.sancX = s.getInt("x");
        data.sancY = s.getInt("y");
        data.sancZ = s.getInt("z");
        data.setProgress(tag.getInt("progress"));   // 없으면 0 — 새 월드의 정상값이다
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("town", town.save(registries));
        tag.put("hero", hero.save(registries));
        tag.put("siege", siege.save(registries));
        CompoundTag s = new CompoundTag();
        s.putBoolean("set", sancSet);
        s.putInt("x", sancX);
        s.putInt("y", sancY);
        s.putInt("z", sancZ);
        tag.put("sanctuary", s);
        tag.putInt("progress", progress);
        return tag;
    }

    // 섹션이 값을 바꾼 뒤 반드시 부른다. 안 부르면 서버가 꺼질 때 저장되지 않는다.
    public void dirty() {
        setDirty();
    }
}
