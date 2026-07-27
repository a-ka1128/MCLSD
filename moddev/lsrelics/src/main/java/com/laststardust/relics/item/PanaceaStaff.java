package com.laststardust.relics.item;

import com.laststardust.relics.BoltManager;
import com.laststardust.relics.LSRelics;
import com.laststardust.relics.ShieldManager;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 파나케이아 — 힐 지팡이. 8종 중 유일한 서포터.
//
//  · 좌클릭 = 치유의 빛. 조준 대상에 따라 자동 전환된다.
//      아군 조준 → 회복 2.0 HP (초당 4 HPS · 5성 10 HPS)
//      그 외     → 신성 탄환 4.3 피해 (DPS 8.6)
//
// 회복은 피해와 다른 배율을 쓴다(RelicSkills.healScale, 최종 2.5배). 피해는 보스 체력을 올려
// 보정할 수 있지만 잡몹 공격력은 그럴 수 없어서, 회복까지 3배가 되면 수성전이 성립하지 않는다.
//
// 힐량 기준: 수성전 몹은 4~6을 때리고 3마리가 붙으면 초당 약 6이 들어온다.
// 4 HPS면 "압박받는 아군 하나를 절반쯤 버티게 해주는" 수준 — 파티를 무적으로 만들지 않는다.
//
// 아군 판정은 히트스캔 + 관대한 오차(1.5칸)다. 힐러가 조준을 빗맞히면 그건 재미가 아니라 짜증이라,
// 적을 맞히는 것보다 아군을 맞히는 쪽을 훨씬 쉽게 만들었다.
@EventBusSubscriber(modid = LSRelics.MODID)
//   · 좌클릭 = 치유 광선(아군 조준) / 심판의 빛(적)
//   · R = 심판의 빛 (기본, 1성)   · V = 천사의 발걸음 (이동, 2성)
//   · C = 수호의 성역 (추가, 3성) · X = 소생 (궁극, 4성)
public class PanaceaStaff extends Item implements RelicActions {
    private static final int FIRE_RATE = 10;      // 2발/초
    // 2026-07-26 상향: 4.3 → 6.5 (+51%). 파티전에서는 힐 우선이라 평타가 거의 안 나가지만,
    // 솔로·아군이 사선에 없을 때의 체감이 너무 약했다. 파티 화력 기여는 성역 연소가 맡는다.
    private static final float BOLT_DMG = 6.5f;   // × 2발/초 = DPS 13
    private static final float HEAL = 2.0f;       // × 2발/초 = 4 HPS (5성 10 HPS)
    private static final double RANGE = 20.0;
    private static final double AIM_LENIENCY = 1.5; // 아군 조준 허용 오차(칸)

    private static final Vector3f HOLY = new Vector3f(0.85f, 1.0f, 0.80f); // 연녹백색

    // ── 패시브 ① 자기 재생 ──
    // 평타의 아군 힐(4 HPS)의 1/4. 스스로는 느리게 차서 파티 지원이 여전히 필요하지만,
    // 전투 사이에 밥 먹고 기다릴 일은 없어진다. (평타로는 자신을 조준할 수 없기 때문에 필요한 보완)
    private static final float SELF_REGEN = 1.0f;   // 초당
    private static final int REGEN_INTERVAL = 20;

    // ── 패시브 ② 과잉 치유 ──
    // 만피 아군에게 쏘면 넘친 회복량이 보호막이 된다. 힐러의 "할 일 없는 시간"을 없애고,
    // 저녁(수성 준비시간)에 파티를 한 명씩 둘러주는 행동이 생긴다.
    // 상한은 각성해도 올리지 않는다 — 충전 속도만 빨라진다. 5성에서 전원이 두꺼운 보호막을
    // 두르면 수성전 자체가 성립하지 않는다.
    private static final float SHIELD_CAP = 6.0f;
    private static final int SHIELD_TICKS = 80;     // 4초

    public PanaceaStaff(Properties properties) {
        super(properties);
    }

    @Override
    public boolean firesOnLeftClick() {
        return true;
    }


    // ── V = 천사의 발걸음(이동기) ──
    @Override
    public void doubleSneak(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.angelStep(level, player, stack);
    }

    // ── X = 소생(궁극·4성) ──
    @Override
    public void ultimate(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.resurrect(level, player, stack);
    }

    @Override
    public void leftAttack(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!RelicSkills.shotReady(level, stack, FIRE_RATE)) return;

        Player ally = aimedAlly(level, player);
        if (ally != null) {
            heal(level, player, ally, HEAL * RelicSkills.healScale(stack));
        } else {
            attack(level, player, stack);
        }
    }

    // ── 패시브 ① 자기 재생 (손에 들고 있는 동안) ──
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() != LSRelics.HEALER.get()) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (level.getGameTime() % REGEN_INTERVAL != 0) return;
        if (!player.isAlive() || player.getHealth() >= player.getMaxHealth()) return;
        player.heal(SELF_REGEN * RelicSkills.healScale(stack));
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
            player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(), 1, 0.3, 0.3, 0.3, 0.0);
    }

    // ── 아군 회복 ──
    private static void heal(ServerLevel level, ServerPlayer caster, Player ally, float amount) {
        // 부족한 만큼만 회복하고, 넘치는 몫은 보호막으로 돌린다 (패시브 ② 과잉 치유)
        float missing = Math.max(0, ally.getMaxHealth() - ally.getHealth());
        float healed = Math.min(missing, amount);
        if (healed > 0) {
            ally.heal(healed);
            // 힐도 미움을 산다 — 힐러가 뒤에서 안전하기만 하면 역할 긴장이 없다.
            com.laststardust.relics.ThreatManager.addHealThreat(level, caster, healed);
        }
        float overflow = amount - healed;
        boolean shielded = false;
        if (overflow > 0) {
            ShieldManager.add(ally, overflow, SHIELD_CAP, SHIELD_TICKS);
            shielded = true;
        }

        // 시전자 → 대상으로 이어지는 빛줄기
        Vec3 from = caster.getEyePosition();
        Vec3 to = ally.position().add(0, ally.getBbHeight() * 0.6, 0);
        Vec3 seg = to.subtract(from);
        int steps = Math.max(4, (int) (seg.length() * 2));
        for (int i = 1; i <= steps; i++) {
            Vec3 at = from.add(seg.scale(i / (double) steps));
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        if (shielded) {
            level.sendParticles(ParticleTypes.ENCHANT, to.x, to.y, to.z, 8, 0.4, 0.5, 0.4, 0.4);
            level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 4, 0.4, 0.5, 0.4, 0.0);
            level.playSound(null, ally.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.5f, 1.8f);
        } else {
            level.sendParticles(ParticleTypes.HEART, to.x, to.y, to.z, 2, 0.25, 0.25, 0.25, 0.0);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, to.x, to.y, to.z, 6, 0.35, 0.4, 0.35, 0.0);
            level.playSound(null, ally.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.6f);
        }
    }

    // ── 신성 탄환 ──
    private static void attack(ServerLevel level, ServerPlayer player, ItemStack stack) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        BoltManager.fire(level, player, eye.add(look.scale(0.5)), look,
            RelicSkills.dmg(stack, BOLT_DMG), 26, HOLY);
        level.sendParticles(ParticleTypes.END_ROD,
            eye.x + look.x * 0.7, eye.y + look.y * 0.7, eye.z + look.z * 0.7, 4, 0.04, 0.04, 0.04, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8f, 1.9f);
    }

    // 조준선 위에 있는 아군 중 가장 가까운 하나. 체력이 가득 찬 아군은 건너뛴다
    // (만피 아군을 조준하고 있다는 이유로 공격이 막히면 답답하다).
    private static Player aimedAlly(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 far = eye.add(look.scale(RANGE));
        HitResult clip = level.clip(new ClipContext(eye, far,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = clip.getType() == HitResult.Type.MISS ? RANGE : eye.distanceTo(clip.getLocation());
        // ※ 적이 앞에 있어도 아군이 조준선에 걸리면 **힐이 우선이다.** 의도된 설계다 —
        //   힐러가 "지금 때릴까 살릴까"를 매번 고민하게 만들면 살리는 타이밍을 놓친다.
        //   대신 파티전에서는 아군이 조준선에 늘 걸리므로 평타 화력은 사실상 0에 가깝다.
        //   힐러의 딜 기여는 평타가 아니라 스킬(심판·성역)에서 나와야 한다.

        Player best = null;
        double bestAlong = Double.MAX_VALUE;
        for (Player p : level.players()) {
            if (p == player || !p.isAlive() || p.isSpectator()) continue;
            // 만피 아군도 대상이다 — 과잉 치유가 보호막으로 바꿔주므로 헛되지 않는다.
            // 다만 보호막이 이미 가득 찬 아군은 건너뛰어 뒤쪽의 다친 아군이 가려지지 않게 한다.
            if (p.getHealth() >= p.getMaxHealth() && ShieldManager.grantedTo(p) >= SHIELD_CAP) continue;
            Vec3 rel = p.position().add(0, p.getBbHeight() * 0.5, 0).subtract(eye);
            double along = rel.dot(look);
            if (along < 0 || along > reach) continue;
            if (rel.subtract(look.scale(along)).length() > AIM_LENIENCY) continue;
            if (along < bestAlong) { bestAlong = along; best = p; }
        }
        return best;
    }

    // ── R = 기본 스킬 (1성) ──
    @Override
    public void basicSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.judgmentLight(level, player, stack);
    }

    // ── C = 추가 스킬 (3성) ──
    @Override
    public void extraSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.sanctuary(level, player, stack);
    }

}
