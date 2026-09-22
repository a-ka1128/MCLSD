package com.laststardust.relics.town;

import java.util.Optional;

import com.laststardust.relics.LSRelics;
import com.laststardust.relics.data.LSData;
import com.laststardust.relics.data.TownCatalog;
import com.laststardust.relics.data.TownData;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * <h2>마을 단계가 «세계에서 자란다»</h2>
 *
 * <p>여태 마을 16단계는 화면 속 숫자와 효과만 바꾸고 세계는 그대로였다
 * ({@code LAUNCH.md} B-2 ②: 「이 서버의 서사가 재건인데 재건이 안 보인다」).
 * 여기가 그걸 메꾼다 — 레벨이 오르면 그 트랙의 구조물을 성역 옆에 실제로 세운다.
 *
 * <p>── 구조물은 어디서 오나 ──
 * 데이터팩의 구조물 템플릿이다: {@code data/lsrelics/structure/town/<트랙>_<단계>.nbt}.
 * 게임 안에서 <b>구조물 블록</b>으로 저장한 뒤 그 파일을 이 경로에 넣으면 된다.
 * 없는 단계는 <b>조용히 건너뛴다</b> — 아직 안 지은 단계가 있는 게 정상이기 때문이다.
 * 다만 로그에는 남긴다. 안 남기면 「왜 안 서지」를 알 방법이 없다.
 *
 * <p>── 왜 «성역 기준 상대 좌표» 인가 ──
 * 절대 좌표로 두면 성역을 옮기는 날 건물만 제자리에 남는다. 앵커는
 * {@code /town anchor <트랙>} 으로 그 자리에 서서 잡는다 — 좌표를 손으로 계산하면
 * 한 칸씩 틀리고, 틀린 걸 눈으로 확인할 방법이 없다.
 *
 * <p>⚠️ <b>덮어쓴다.</b> 다음 단계 구조물은 이전 단계 자리에 그대로 얹힌다 —
 * 그래서 각 단계 구조물은 <b>같은 원점</b>으로 떠야 하고, 뒤 단계가 앞 단계를 덮을 만큼
 * 커야 한다. 작아지면 이전 단계의 잔해가 삐져나온다.
 */
public final class TownBuild {
    private TownBuild() {}

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    /** 한 트랙의 구조물 이름 — {@code town/forge_2} 식. */
    private static ResourceLocation templateId(String track, int level) {
        return ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "town/" + track + "_" + level);
    }

    /**
     * 레벨과 «지어진 단계»가 어긋난 트랙을 전부 맞춘다.
     *
     * <p>{@link TownService#reconcile} 이 부른다 — 어느 경로로 레벨이 올랐든
     * ({@code upgrade()} 든 {@code /town level} 이든) 같은 자리를 지나가기 때문이다.
     * 여러 번 불려도 안전하다: 이미 맞으면 아무것도 안 한다.
     */
    public static void reconcile(MinecraftServer server) {
        if (server == null) return;
        LSData data = LSData.get(server);
        if (!data.hasSanctuary()) return;   // 성역이 없으면 기준점이 없다
        TownData town = data.town();
        boolean any = false;
        for (TownCatalog.Track t : TownCatalog.ALL) {
            int want = town.level(t.key());
            if (town.builtLevel(t.key()) == want) continue;
            if (place(server, t.key(), want)) {
                town.setBuiltLevel(t.key(), want);
                any = true;
            }
        }
        if (any) data.dirty();
    }

    /**
     * 그 트랙의 {@code level} 단계 구조물을 세운다.
     *
     * @return 실제로 세웠으면 {@code true}. 앵커가 없거나 템플릿이 없으면 {@code false} —
     *         <b>그때는 «지어진 단계»를 올리지 않는다.</b> 올려 버리면 나중에 파일을 넣어도
     *         「이미 지었다」로 판단해 영영 안 선다.
     */
    public static boolean place(MinecraftServer server, String track, int level) {
        if (server == null || level <= 0) return level <= 0;   // 0단계는 «세울 게 없음»이 정상
        LSData data = LSData.get(server);
        if (!data.hasSanctuary()) return false;
        TownData town = data.town();

        int[] a = town.anchor(track);
        if (a == null) {
            LOG.info("[마을건축] {} — 앵커가 없어 건너뛴다 (/town anchor {})", track, track);
            return false;
        }

        ServerLevel level3 = server.overworld();
        var mgr = server.getStructureManager();
        ResourceLocation id = templateId(track, level);
        Optional<StructureTemplate> tpl = mgr.get(id);
        if (tpl.isEmpty()) {
            // 아직 안 만든 단계가 있는 게 정상이다. 조용히 넘어가되 자국은 남긴다.
            LOG.info("[마을건축] {} {}단계 — 구조물 파일이 없다 ({})", track, level, id);
            return false;
        }

        BlockPos s = data.sanctuary();
        BlockPos at = s.offset(a[0], a[1], a[2]);
        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setRotation(Rotation.NONE)
            .setMirror(Mirror.NONE)
            .setIgnoreEntities(false);
        tpl.get().placeInWorld(level3, at, at, settings, level3.getRandom(), 2);
        LOG.info("[마을건축] {} {}단계 세움 @ {} {} {}", track, level, at.getX(), at.getY(), at.getZ());
        return true;
    }

    /** 지금 지어진 단계를 0 으로 되돌린다 — 다음 reconcile 이 다시 세운다. 파일을 고친 뒤에 쓴다. */
    public static void forget(MinecraftServer server, String track) {
        if (server == null) return;
        LSData data = LSData.get(server);
        data.town().setBuiltLevel(track, 0);
        data.dirty();
    }
}
