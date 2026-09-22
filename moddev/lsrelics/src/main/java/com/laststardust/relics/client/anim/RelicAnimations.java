package com.laststardust.relics.client.anim;

import com.laststardust.relics.LSRelics;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * 유물 동작을 플레이어 뼈대에 얹는 곳. <b>클라이언트에만 존재한다.</b>
 *
 * <p>── 구조 ──
 * PlayerAnimator 는 플레이어마다 «레이어 더미»를 갖고 있고, 우리는 거기에 빈 칸
 * ({@link ModifierLayer}) 하나를 미리 예약해 둔다({@link #register}). 재생은 그 칸에 애니메이션을
 * 꽂는 것뿐이다({@link #playSunder}). 칸을 미리 잡아두는 이유는 <b>플레이어가 만들어지는
 * 시점에만</b> 레이어를 붙일 수 있어서다 — 나중에 붙일 방법이 없다.
 *
 * <p>── 우선순위 42 ──
 * 베터컴뱃의 공격 모션이 이 근처에서 논다. 숫자가 클수록 위에 얹힌다. 42 는 «평타 모션보다는
 * 위, 하지만 특별히 높지는 않게»를 노린 값이고, <b>인게임에서 평타와 겹쳐 보이면 올린다.</b>
 */
public final class RelicAnimations {
    private RelicAnimations() {}

    /** 우리 칸의 이름. 이걸로 다시 찾아온다. */
    private static final ResourceLocation SUNDER =
        ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "sunder");

    /** 클라 셋업에서 한 번. {@code LSRelicsClient.onClientSetup} 이 부른다. */
    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
            SUNDER, 42, player -> new ModifierLayer<>());
    }

    /**
     * 서버가 「지금 치켜든다」고 알려온 것을 받아 재생한다.
     *
     * <p>남의 플레이어도 여기로 온다 — {@code entityId} 로 찾는다. 화면에 없는(청크 밖) 플레이어면
     * 그냥 아무 일도 안 일어난다.
     */
    @SuppressWarnings("unchecked")
    public static void playSunder(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(entityId);
        if (!(e instanceof AbstractClientPlayer player)) return;

        IAnimation slot = PlayerAnimationAccess.getPlayerAssociatedData(player).get(SUNDER);
        if (!(slot instanceof ModifierLayer)) return;
        ((ModifierLayer<IAnimation>) slot).setAnimation(new SunderAnimation());
    }
}
