package com.laststardust.relics.client;

import com.laststardust.relics.LSRelics;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 스틱스(에레보스) — <b>왼손에 같은 칼을 한 자루 더 그린다.</b>
 *
 * <p>── 왜 필요한가 ──
 * 스틱스는 «쌍단검»으로 설계됐는데, 마인크래프트는 아이템 하나를 <b>한 손에만</b> 그린다.
 * 그래서 이름과 로어만 쌍단검이고 화면에는 한 자루였다. 베터컴뱃의
 * {@code dual_handed_slash_cross}/{@code uncross} 애니메이션이 <b>두 팔을 다 움직이는데</b>
 * 왼팔이 빈손이라 «허공을 휘두르는» 그림이 나왔다.
 *
 * <p>── 모델을 두 자루로 만들지 않은 이유 ──
 * 그러면 <b>한 손에 두 자루를 쥔</b> 꼴이 된다. 원하는 건 양손에 한 자루씩이다.
 * 그건 모델이 아니라 <b>렌더링</b>의 문제라 여기서 푼다.
 *
 * <p>── 보조손이 늘 비어 있는 게 전제다 ──
 * {@code weapon_attributes/assassin.json} 이 {@code two_handed: true} 라 베터컴뱃이
 * 보조손 사용을 막는다. 그래서 이 자리에 우리 칼을 그려도 <b>다른 아이템과 겹칠 일이 없다.</b>
 * 그래도 만약을 위해 «보조손이 비었을 때만» 그린다 — 겹쳐 그리는 건 어떤 이유로든 사고다.
 *
 * <p>이 계산은 바닐라 {@code ItemInHandLayer.renderArmWithItem} 과 같다. 손맛이 바닐라와
 * 어긋나면 「왜 이 칼만 손에서 뜨지」가 되고, 그건 눈에 띄는데 원인은 안 보이는 종류의 어긋남이다.
 */
public class DualWieldLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public DualWieldLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() != LSRelics.ASSASSIN.get()) return;
        if (!player.getOffhandItem().isEmpty()) return;
        if (player.isSpectator()) return;

        pose.pushPose();
        // 왼팔의 «손» 위치·회전으로 좌표계를 옮긴다. 팔이 움직이면 칼도 같이 움직인다 —
        // 쌍수 애니메이션이 두 팔을 다 쓰기 때문에 이게 되어야 뜻이 산다.
        getParentModel().translateToHand(HumanoidArm.LEFT, pose);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate(-1.0F / 16.0F, 0.125F, -0.625F);
        // ⚠️ 바닐라 ItemInHandLayer 는 `Minecraft.getItemInHandRenderer()` 를 쓰는데
        //    1.21.1 의 Minecraft 에는 그 접근자가 없다. 결과가 같은 ItemRenderer 로 그린다.
        Minecraft.getInstance().getItemRenderer().renderStatic(
            player, main, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true, pose, buffer,
            player.level(), light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
            player.getId());
        pose.popPose();
    }
}
