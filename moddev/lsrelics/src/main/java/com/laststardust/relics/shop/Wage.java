package com.laststardust.relics.shop;

import com.laststardust.relics.LSRelics;
import com.laststardust.relics.data.LSData;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * <h2>Ducat 을 «버는» 자리</h2>
 *
 * <p>{@code DESIGN.md} P2: 「PvE 전리품 / 퀘스트 / 관문 보상 → 잔액」.
 * 여기는 그중 <b>PvE 전리품</b> — 몹을 잡으면 조금씩 들어온다.
 * 공성 격퇴({@code ls_siege.js})와 현상금({@code ls_bounty.js})은 각자 자기 자리에서
 * 브릿지({@code LS.walletAdd})로 넣는다 — 보상을 주는 코드는 이미 거기 있고,
 * 그걸 여기로 끌어오면 「보상이 두 곳에서 나가는」 구조가 된다.
 *
 * <p>── 왜 이렇게 적은가 ──
 * 상점이 파는 것은 <b>편의·치장</b>이지 파워가 아니다. 그래서 돈이 넘쳐도 세지지 않는다.
 * 다만 너무 빨리 쌓이면 「살 게 없다」가 되어 상점이 하루 만에 끝난다.
 * 잡몹 1은 공성 한 판(웨이브 5×20~40마리)에 100~200 정도가 돌게 하는 값이다 —
 * 셜커 상자 하나(260)가 <b>공성 두 판</b>쯤 되는 무게다.
 *
 * <p>⚠️ 플레이어가 죽인 것만 센다. 몹끼리 싸워 죽거나 낙사한 것까지 세면
 * 가만히 서서 버는 길이 열린다.
 */
@EventBusSubscriber(modid = LSRelics.MODID)
public final class Wage {
    private Wage() {}

    /** 잡몹 하나. */
    private static final int MOB = 1;
    /** 무거운 모드 몹(공성 정예 등) — 잡는 데 드는 품이 다르다. */
    private static final int ELITE = 3;
    /** 체력이 이 이상이면 정예로 본다. 몹 목록을 손으로 적으면 모드가 늘 때마다 어긋난다. */
    private static final float ELITE_HP = 60.0f;

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) return;
        if (!(dead instanceof Enemy)) return;   // 동물·주민은 안 센다

        // 죽인 주체가 플레이어일 때만. 화살·소환수도 getKillCredit 로 주인이 잡힌다.
        if (!(dead.getKillCredit() instanceof ServerPlayer p)) return;
        var server = p.getServer();
        if (server == null) return;

        int pay = dead.getMaxHealth() >= ELITE_HP ? ELITE : MOB;
        var data = LSData.get(server);
        data.wallet().add(p.getGameProfile().getName(), pay);
        data.dirty();
        // ⚠️ 채팅으로 알리지 않는다. 잡몹마다 한 줄씩 뜨면 공성 중에 채팅이 통째로 묻힌다.
        //    잔액은 /wallet 과 상점 화면에서 본다.
    }
}
