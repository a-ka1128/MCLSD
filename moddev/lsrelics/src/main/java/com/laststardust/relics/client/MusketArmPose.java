package com.laststardust.relics.client;


import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

// 마총 연사 모드일 때 총을 두 손으로 잡는 자세.
//
// ── 무엇이 되고 무엇이 안 되나 ──
// 되는 것: 3인칭(F5, 그리고 남들이 보는 내 모습). 바닐라 ArmPose.CROSSBOW_HOLD 를 돌려주면
//   양팔이 앞으로 모여 총을 받쳐 든 자세가 된다. 석궁을 들었을 때와 같은 그 자세다.
//
// 안 되는 것: 1인칭. 바닐라는 1인칭에서 **주손 아이템 하나만** 그린다 — 보조손 팔을 총에
//   얹으려면 RenderHandEvent 를 가로채 팔을 직접 배치해야 하고, 그건 눈으로 보면서
//   좌표를 맞춰야 하는 작업이라 여기서 손대면 십중팔구 어색해진다.
//   (총기 모드들이 1인칭 두 손을 보여주는 건 대부분 전용 애니메이션 모델을 쓰기 때문이다)
//
// 자세를 연사 모드에만 거는 이유: 전환이 눈에 보여야 하기 때문이다. 늘 두 손으로 들면
// C 를 눌렀을 때 달라지는 게 모델 크기뿐이라 티가 안 난다.
// 항상 두 손으로 들게 하려면 아래 rifle 검사만 빼면 된다.
public final class MusketArmPose implements IClientItemExtensions {

    public static final MusketArmPose INSTANCE = new MusketArmPose();

    private MusketArmPose() {}

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        if (stack.getItem() != com.laststardust.relics.LSRelics.GUNNER.get()) return null;
        boolean rifle = stack
            .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag().getBoolean("rifle");
        // 연사 중엔 조준하고 있어도 두 손을 유지한다 — 라이플을 한 손으로 드는 그림이 안 된다.
        if (rifle) return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        // 저격 스코프 중엔 바닐라 SPYGLASS 자세를 덮지 않는다.
        return null;
    }
}
