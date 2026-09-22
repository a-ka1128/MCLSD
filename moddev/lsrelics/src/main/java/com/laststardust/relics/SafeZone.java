package com.laststardust.relics;

import com.laststardust.relics.data.LSData;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * <h2>성벽 안에는 몬스터가 «저절로» 생기지 않는다</h2>
 *
 * <p>성역 안이 어두우면 구석마다 좀비가 솟는다. 그러면 <b>공성이 무의미해진다</b> —
 * 밤마다 벽 밖에서 오는 것들을 막는 게 이 서버의 중심 루프인데,
 * 정작 안쪽에서 계속 생기면 「막는다」는 행위 자체가 성립하지 않는다.
 * 횃불 도배로 해결하라고 두면 그건 건축을 망가뜨리는 쪽으로 압박하는 것이다.
 *
 * <p>── 무엇을 막고 무엇을 안 막나 ──
 * <b>{@link MobSpawnType#NATURAL} 만</b> 막는다. 공성 몹은 {@code /summon}(COMMAND)으로,
 * 관문 몹은 그 모드가 직접 넣는다 — 여기서 종류를 안 가리고 막으면
 * <b>공성이 통째로 안 열린다.</b> 스포너(SPAWNER)도 살려 둔다: 성 안에 던전 스포너를
 * 일부러 놓아 두는 쓰임이 남는다.
 *
 * <p>── 범위 ──
 * 성벽 반경 기준 <b>정사각</b>(체비쇼프 거리)이다. 실제 성벽이 정사각이라 그대로 맞고,
 * 나중에 원형으로 바꾸더라도 원을 감싸는 정사각이라 «덜 막는» 일은 안 생긴다.
 * 세로는 안 본다 — 지하실에서 솟는 것도 성 안이다.
 *
 * <p>⚠️ 이미 있는 몹은 안 건드린다. 이건 «생기는 것»만 막는다.
 * 안에 남은 것을 치우려면 {@code /kill} 을 쓴다.
 */
@EventBusSubscriber(modid = LSRelics.MODID)
public final class SafeZone {
    private SafeZone() {}

    /** 성벽이 «닿는 끝»보다 조금 넉넉히 — 벽 바로 안쪽 한 줄에서 솟는 것까지 막는다. */
    private static final int MARGIN = 2;

    @SubscribeEvent
    public static void onSpawn(FinalizeSpawnEvent event) {
        // 자연 발생만 막는다. 공성(COMMAND)·관문·스포너는 그대로 둔다.
        if (event.getSpawnType() != MobSpawnType.NATURAL) return;

        Mob mob = event.getEntity();
        if (!(mob instanceof Enemy)) return;   // 동물·주민은 안 막는다

        var level = mob.level();
        if (level.isClientSide() || level.getServer() == null) return;
        // 오버월드만. 네더·엔드에 성역은 없다.
        if (level.dimension() != level.getServer().overworld().dimension()) return;

        LSData data = LSData.get(level.getServer());
        if (!data.hasSanctuary()) return;
        int r = data.siege().wallRadius();
        if (r <= 0) return;

        BlockPos s = data.sanctuary();
        // ⚠️ `(int)` 캐스팅이 아니라 floor 다. 음수 좌표에서 `(int)(-0.5)` 는 0 이 되어
        //    한 칸 안쪽으로 밀린다 — 성역이 음수 구역에 있으면 경계가 조용히 어긋난다.
        int dx = Math.abs(Mth.floor(event.getX()) - s.getX());
        int dz = Math.abs(Mth.floor(event.getZ()) - s.getZ());
        if (Math.max(dx, dz) > r + MARGIN) return;

        event.setSpawnCancelled(true);
    }
}
