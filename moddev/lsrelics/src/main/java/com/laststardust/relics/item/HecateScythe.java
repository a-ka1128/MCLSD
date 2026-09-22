package com.laststardust.relics.item;

import com.laststardust.relics.CurseManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 헤스페로스 — 낫(헤카테의 가호). 중거리 약화·지원.
//   · 좌클릭 = 평타 (Better Combat 무브셋) — 맞을 때마다 저주 1중첩 (RelicEventHandlers 패시브)
//   · R = 재의 채찍 (기본, 1성)     · V = 재의 결계 (이동, 2성)
//   · C = 연좌 (추가, 3성)          · X = 헤카테의 밤 (궁극, 4성)
//   · 패시브(1성) = 저주의 각인 — 중첩당 적이 받는 피해 +3%, 보스에겐 상한 8 (CurseManager)
//
// ── 이 유물만 다른 점 ──
// 나머지 여덟은 «자기 화력»을 올린다. 헤카테는 적을 약하게 만들어 **파티 전원의 딜을 올린다.**
// 그래서 /dummy 단독 측정값이 낮게 나오는 게 정상이다 — 파나케이아(87.8)를 그대로 둔 것과 같다.
public class HecateScythe extends Item implements RelicActions {

    public HecateScythe(Properties properties) {
        super(properties);
    }

    // ── R = 재의 채찍 (기본·1성) ──
    @Override
    public void basicSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.ashWhip(level, player, stack);
    }

    // ── V = 이동기 · 재의 결계 ──
    @Override
    public void doubleSneak(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.ashWard(level, player, stack);
    }

    // ── C = 연좌 (추가·3성) ──
    @Override
    public void extraSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.guiltByAssociation(level, player, stack);
    }

    // ── X = 헤카테의 밤 (궁극·4성) ──
    @Override
    public void ultimate(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.nightOfHecate(level, player, stack);
    }

    // ── 액션바: 지금 보고 있는 적의 저주 중첩 ──
    //
    // 이게 없으면 헤카테는 «자기가 뭘 하고 있는지 안 보이는» 유물이 된다. 다른 여덟은 피해
    // 숫자가 뜨지만 저주는 남의 딜로 나가므로 화면에 아무 흔적이 없다 — 「걸리고 있나?」를
    // 확인할 방법이 필요하다. 연좌(C)가 대상에 저주가 있어야 나가는 것도 여기서 미리 보인다.
    //
    // 액션바는 한 줄뿐이라 쿨다운 표시가 주인이고(CooldownDisplay) 여기는 앞머리만 빌린다.
    @Override
    public String hudStatus(ServerPlayer player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        LivingEntity t = lookedAt(level, player, 18.0);
        if (t == null) return null;
        int n = CurseManager.stacks(t);
        int cap = CurseManager.cap(t);
        if (n <= 0) return null;
        // 최대면 금색으로 — 연좌를 지금 쓰라는 신호다
        String color = n >= cap ? "§6" : "§3";
        return color + "☽ " + n + "/" + cap;
    }

    /** 시선 원뿔 안에서 제일 가까운 적. 연좌(RelicSkills.guiltByAssociation)와 같은 판정이다. */
    private static LivingEntity lookedAt(ServerLevel level, ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        AABB scan = new AABB(eye, eye.add(look.scale(range))).inflate(2.0);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, scan,
                en -> en != player && en.isAlive() && !(en instanceof Player))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > range + 1.5) continue;
            if (d > 0.01 && to.normalize().dot(look) < 0.93) continue;
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    // ── 인챈트 테이블에서도 걸리게 (2026-08-18) ──
    // 바닐라 기본값은 «스택1 && 내구도 있음»이라, 내구도가 없는 유물 9종은
    // 인챈트 테이블도 모루도 통째로 거부했다. 유물은 닳아 없어지면 안 되는 물건이라
    // 내구도를 주는 대신 여기만 연다.
    //
    // ⚠️ **무엇이 붙을지는 여기서 안 정한다.** 그건 데이터팩(`tools/gen_relic_enchants.py`)이
    //    인챈트의 `supported_items` 로 정한다 — 데미지 계열 17종은 거기서 막힌다.
    //    여기서 true 만 돌려주면 「테이블에 올라갈 자격」이 생길 뿐이다.
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return stack.getCount() == 1;
    }

    // 인챈트 «잘 걸리는» 정도. 네더라이트와 같은 15 — 금(22)은 운이 과하고 돌(5)은 답답하다.
    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 15;
    }
}
