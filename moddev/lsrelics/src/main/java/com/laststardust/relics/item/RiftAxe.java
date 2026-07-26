package com.laststardust.relics.item;

import java.util.function.Consumer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
// 타이탄 브레이커 — 순수 근접 무기(도끼). 채굴 겸용은 뺐고 그만큼 공격력이 높다.
// 타이탄 브레이커 — 도끼. 좌클릭 = 강타(근접). 블록 껍질 벗기기는 바닐라 그대로 동작한다
// (우클릭을 비워둔 덕분에 도끼 본래 기능이 막히지 않는다).
//   · R = 균열 붕괴 (기본, 1성)   · V = 지축 밟기 (이동, 2성)
//   · C = 대지 쪼개기 (추가, 3성) · X = 타이탄 강림 (궁극, 4성)
public class RiftAxe extends AxeItem implements RelicActions {
    public RiftAxe(Tier tier, Properties properties) {
        super(tier, properties);
    }


    // ── 좌클릭은 평범한 근접 공격이므로 패킷 발사 안 함 ──
    @Override
    public boolean firesOnLeftClick() {
        return false;
    }

    // ── 내구도를 소모하지 않는다 (2026-07-27) ──
    // 유물은 첫 공세를 맨몸으로 버텨야 얻는 물건이라 소모품이 아니다. 닳아 없어지면
    // 그 직업을 통째로 잃는다. TieredItem 이 tier(네더라이트) 내구도를 강제로 붙이므로
    // Properties 로는 못 끄고, 피해가 들어오는 지점을 막는 게 유일한 방법이다.
    // 이 훅 하나로 채굴·공격·껍질벗기기 등 모든 경로가 덮인다.
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<Item> onBroken) {
        return 0;
    }

    // ── 채굴 도구가 아니다 ──
    // AxeItem 을 상속한 건 "도끼처럼 생긴 무기"라서지 벌목용이 아니다(LSRelics 주석 참고 —
    // 채굴 겸용을 뺀 대신 공격력을 가장 높게 잡았다). 상속만으로 나무가 빨리 캐져
    // 그 전제가 깨져 있었다. 맨손과 같은 속도로 되돌린다.
    // ※ 껍질 벗기기(우클릭)는 useOn 쪽이라 그대로 동작한다.
    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return 1.0F;
    }

    @Override
    public void leftAttack(ServerLevel level, ServerPlayer player, ItemStack stack) {
        // 미사용
    }

    // ── V = 지축 밟기(이동기) ──
    @Override
    public void doubleSneak(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.earthStomp(level, player, stack);
    }

    // ── X = 타이탄 강림(궁극) ──
    @Override
    public void ultimate(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.titanAscension(level, player, stack);
    }

    // ── R = 기본 스킬 (1성) ──
    @Override
    public void basicSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.riftCollapse(level, player, stack);
    }

    // ── C = 추가 스킬 (3성) ──
    @Override
    public void extraSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.earthSplitter(level, player, stack);
    }

}
