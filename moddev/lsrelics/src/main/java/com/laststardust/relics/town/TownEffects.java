package com.laststardust.relics.town;

import com.laststardust.relics.LSRelics;
import com.laststardust.relics.data.LSData;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 마을 발전 효과 중 **모드가 직접 처리하는** 것들.
//
// 성역 좌표가 필요한 효과(별빛 축복 범위 판정 등)는 아직 KubeJS 소유라 ls_towneffect.js 에 있다.
// 여기 있는 건 좌표와 무관한 것들 — 제련/수확 보너스, 상시 속성 보정.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class TownEffects {
    private TownEffects() {}

    // ── 성소 Lv3 「관문 안정기」 — 최대 체력 +10 (하트 5칸) ──
    // 각성(ls_ascend)·가호(ls_fate)와 다른 ID 라 겹치지 않고 함께 적용된다.
    private static final ResourceLocation HEALTH_ID =
        ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "town_sanctum_health");
    private static final double SANCTUM_HEALTH = 10.0;

    // ── 공방 Lv1 「공용 작업대」 — 제련·수확 시 확률로 최대 3개 추가 ──
    //
    // **최소가 0 이다.** 3분의 1은 그냥 꽝이어야 "가끔 더 나온다"는 보너스로 남는다.
    // 개수가 커질수록 확률이 낮아진다 — 큰 수가 나왔을 때 기분이 좋으려면 그게 흔하면 안 된다.
    //   0개 33% · 1개 24% · 2개 24% · 3개 19%   (기댓값 +1.29 → 실배율 2.29배)
    //
    // ── 2.29배는 알고 고른 값이다 (유저 결정 2026-07-25 · docs/DECISIONS.md) ──
    // 여기 «무조건 +1 이면 2배가 되어 채산성이 흔들린다»며 2배를 거부하는 문장이 있었다.
    // 그런데 실제 기댓값은 그보다 높은 2.29배다 — 읽는 사람마다 «버그다»로 판단하게 만들었고,
    // 실제로 자가진단이 두 번이나 이걸 «자기모순»으로 다시 올렸다.
    //
    // 값이 아니라 그 문장이 틀렸다. 33% 꽝이 섞여 있어 체감이 «가끔 나오는 보너스»로 남는 것이
    // 이 설계의 핵심이고, 기댓값이 2배를 넘는 건 그 대가로 받아들인 것이다.
    // 다시 열 조건은 하나뿐이다 — **채산성이 실제로 문제로 느껴질 때.**
    // 그때 쓸 후보: {50,30,15,5} → 1.75배 · {60,30,8,2} → 1.52배
    private static final int[] BONUS_WEIGHTS = { 33, 24, 24, 19 };

    // 가중 추첨 — 0~3 중 하나
    private static int rollBonus(net.minecraft.util.RandomSource rng) {
        int total = 0;
        for (int w : BONUS_WEIGHTS) total += w;
        int r = rng.nextInt(total);
        for (int i = 0; i < BONUS_WEIGHTS.length; i++) {
            r -= BONUS_WEIGHTS[i];
            if (r < 0) return i;
        }
        return 0;
    }

    private static int level(ServerLevel level, String track) {
        var server = level.getServer();
        return server == null ? 0 : LSData.get(server).town().level(track);
    }

    // ── 제련 보너스 ──
    // 광물 계열만 대상 — 음식까지 불리면 굶주림이 사실상 사라져 초반 긴장이 통째로 빠진다.
    //
    // ── 이 이벤트는 "제련될 때"가 아니라 "꺼낼 때" 온다 ──
    // ItemSmeltedEvent 는 화로에서 결과물을 **집어낼 때** 한 번 발생하고,
    // getSmelting() 은 그때 집어낸 스택 전체다. 그래서 추첨을 한 번만 굴리면
    // 꺼내는 방식에 따라 보상이 갈렸다:
    //     한 개씩 40번 꺼냄 → 40회 추첨 (≈ +51)
    //     한 번에 40개 꺼냄 →  1회 추첨 (0~3)
    // 20배 차이인데다 하필 "한 개씩 빼는 손해 보는 조작"이 이득인 방향이라,
    // 알고 나면 아무도 시프트클릭을 안 쓰게 된다. 개수만큼 굴려야 맞다.
    @SubscribeEvent
    public static void onSmelt(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (level(level, "workshop") < 1) return;

        ItemStack out = event.getSmelting();
        if (out.isEmpty()) return;
        // 주괴·덩어리 계열만 (구리·철·금·네더라이트 조각 등)
        if (!out.is(ItemTags.TRIM_MATERIALS) && !isMetal(out)) return;

        int bonus = 0;
        for (int i = 0, n = out.getCount(); i < n; i++) bonus += rollBonus(level.getRandom());
        if (bonus <= 0) return;   // 3분의 1은 꽝 — 이게 있어야 보너스가 보너스로 남는다
        giveStacked(player, out, bonus);
    }

    // 스택 상한을 넘겨 지급하지 않는다.
    // 40개를 한 번에 꺼내면 보너스가 100개를 넘을 수 있는데, copyWithCount(100) 은
    // 상한 64 를 넘은 비정상 스택이라 인벤토리에 넣는 순간 조용히 잘려나간다.
    private static void giveStacked(ServerPlayer player, ItemStack proto, int amount) {
        int max = Math.max(1, proto.getMaxStackSize());
        while (amount > 0) {
            int n = Math.min(max, amount);
            ItemStack extra = proto.copyWithCount(n);
            if (!player.getInventory().add(extra)) player.drop(extra, false);
            amount -= n;
        }
    }

    private static boolean isMetal(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();
        return path.endsWith("_ingot") || path.endsWith("_nugget")
            || path.endsWith("_scrap") || path.equals("copper_ingot");
    }

    // ── 수확 보너스 ──
    // 다 자란 농작물을 캘 때만. 덜 자란 걸 캐도 주면 심고 바로 캐는 반복이 이득이 된다.
    @SubscribeEvent
    public static void onHarvest(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level(level, "workshop") < 1) return;

        BlockState state = event.getState();
        if (!(state.getBlock() instanceof CropBlock crop)) return;
        if (!crop.isMaxAge(state)) return;

        ItemStack seed = new ItemStack(state.getBlock().asItem());
        if (seed.isEmpty()) return;
        int bonus = rollBonus(level.getRandom());
        if (bonus <= 0) return;
        ItemStack extra = seed.copyWithCount(bonus);
        if (!player.getInventory().add(extra)) player.drop(extra, false);
    }

    // ── 접속 시 귀환석 보충 ──
    // 공방 Lv4 완성 당시 접속 중이 아니었던 사람도 받아야 한다.
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        var server = p.getServer();
        if (server == null) return;
        if (LSData.get(server).town().level("workshop") >= 4) {
            TownService.giveHearthstone(p);
        }
    }

    // ── 상시 속성 보정 (성소 Lv3) ──
    // 접속 중인 사람에게 주기적으로 맞춰준다. 리스폰·재접속에서 모디파이어가 살아남지 않으므로
    // "한 번 걸고 끝"이 아니라 계속 확인해야 한다(각성·가호가 같은 이유로 그렇게 한다).
    private static int tick = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tick % 40 != 0) return;   // 2초마다
        var server = event.getServer();
        boolean want = LSData.get(server).town().level("sanctum") >= 3;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            AttributeInstance inst = p.getAttribute(Attributes.MAX_HEALTH);
            if (inst == null) continue;
            boolean has = inst.getModifier(HEALTH_ID) != null;
            if (want && !has) {
                inst.addPermanentModifier(new AttributeModifier(
                    HEALTH_ID, SANCTUM_HEALTH, AttributeModifier.Operation.ADD_VALUE));
            } else if (!want && has) {
                inst.removeModifier(HEALTH_ID);
                if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
            }
        }
    }
}
