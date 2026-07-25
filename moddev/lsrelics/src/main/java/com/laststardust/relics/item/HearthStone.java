package com.laststardust.relics.item;

import com.laststardust.relics.LSRelics;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

// 귀환석 — 공방 Lv4「귀환의 요람」으로 얻는 아이템. 성역으로 돌아간다.
//
// ── 쿨다운이 아이템에 붙는다 ──
// "1시간에 하나씩 지급"이 아니라 **아이템 하나의 쿨다운이 1시간**이다.
// 그래서 여러 개를 모아도 소용이 없고(같은 아이템이라 쿨다운을 공유한다),
// 원정 중 "지금 쓸까, 아껴둘까"를 한 번은 고민하게 된다.
public class HearthStone extends Item {

    public static final int COOLDOWN_TICKS = 72000;   // 1시간
    private static final int CHANNEL_TICKS = 60;      // 3초 — 전투 중 즉시 탈출은 막는다

    public HearthStone(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            if (sanctuary(sl) == null) {
                sp.displayClientMessage(Component.translatable("lsrelics.hearth.no_sanctuary"), true);
                return InteractionResultHolder.fail(stack);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.7f, 1.4f);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        return CHANNEL_TICKS;
    }

    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.UseAnim.BOW;
    }

    // 채널링 중 연출 — 발밑에서 별빛이 감긴다
    @Override
    public void onUseTick(Level level, net.minecraft.world.entity.LivingEntity entity, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel sl)) return;
        int used = CHANNEL_TICKS - remaining;
        double a = used * 0.5;
        double r = 1.2 - used * 0.015;
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
            entity.getX() + Math.cos(a) * r, entity.getY() + 0.1, entity.getZ() + Math.sin(a) * r,
            1, 0, 0.02, 0, 0.0);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity entity) {
        if (!(level instanceof ServerLevel sl) || !(entity instanceof ServerPlayer sp)) return stack;
        BlockPos to = sanctuary(sl);
        if (to == null) return stack;

        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
            sp.getX(), sp.getY() + 1, sp.getZ(), 60, 0.4, 0.8, 0.4, 0.4);
        sl.playSound(null, sp.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9f, 1.2f);

        ServerLevel overworld = sl.getServer().overworld();
        sp.teleportTo(overworld, to.getX() + 0.5, to.getY() + 1, to.getZ() + 0.5, sp.getYRot(), sp.getXRot());

        overworld.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
            to.getX() + 0.5, to.getY() + 1.2, to.getZ() + 0.5, 50, 0.5, 0.8, 0.5, 0.05);
        overworld.playSound(null, to, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9f, 0.9f);
        overworld.playSound(null, to, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.5f);

        sp.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        sp.displayClientMessage(Component.translatable("lsrelics.hearth.returned"), true);
        return stack;
    }

    // 성역 좌표는 LSData 가 갖고 있다 (스크립트가 /sanctuary here 로 정하면 브릿지로 밀어 넣는다).
    private static BlockPos sanctuary(ServerLevel level) {
        var server = level.getServer();
        if (server == null) return null;
        var data = com.laststardust.relics.data.LSData.get(server);
        return data.hasSanctuary() ? data.sanctuary() : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("lsrelics.hearth.desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("lsrelics.hearth.cooldown").withStyle(ChatFormatting.DARK_GRAY));
    }
}
