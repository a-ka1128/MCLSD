package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 12번째 가호(소환사) <b>프로토타입</b> — 바닐라 벡스를 빌려 「별의 잔영」을 만든다.
 *
 * <p>── 왜 커스텀 엔티티가 아니라 벡스인가 ──
 * 이 프로젝트는 <b>커스텀 엔티티를 한 번도 등록해본 적이 없다.</b> 지금까지 만든 건 전부
 * 아이템·스킬·매니저다. GeckoLib 은 깔려 있지만 진짜 벽은 코드가 아니라 <b>에셋</b>이다 —
 * 자유 라이선스 대검 하나 찾는 데 검색을 여섯 번 돌렸는데, 모델 + 애니메이션이 딸린
 * 엔티티는 그보다 훨씬 드물다.
 *
 * <p>벡스는 원래 <b>날아다니는 유령</b>이라 「꺼진 별이 남긴 잔영」에 그대로 맞고,
 * 비행·검 공격·수명 제한이 이미 바닐라에 있다. 손맛을 먼저 확인한 뒤에 외형만 갈아끼우면 된다
 * (헤카테·하르모니아에서 모델 교체는 이미 두 번 해봤다).
 *
 * <p>── 바닐라 벡스를 그냥 소환하면 안 되는 이유 둘 ──
 * <ol>
 *   <li><b>벡스는 플레이어를 노린다.</b> {@code Vex.registerGoals} 의 3순위가
 *       {@code NearestAttackableTargetGoal<Player>} 다. 소환하자마자 주인을 때린다.</li>
 *   <li><b>몹 스케일링이 우리 소환수를 강화한다.</b> 벡스는 {@code MobCategory.MONSTER} 라
 *       {@code ls_mobscale.js} 를 그대로 통과한다 — krip_turrets 포탑이 정확히 그래서
 *       「관문을 깰수록 내 포탑이 세지는」 통로가 났었다({@code TODO.md} D절 10).
 *       그래서 {@link LSKubeBridge#isMonster} 가 이 표식을 보고 빼도록 고쳤다.</li>
 * </ol>
 *
 * <p>── 표적은 목표(Goal)가 아니라 여기서 직접 정한다 ──
 * {@code NearestAttackableTargetGoal} 의 생성자 시그니처가 버전마다 갈려서, 거기 맞추다
 * 틀리면 조용히 «아무도 안 때리는» 소환수가 된다. 틱에서 {@code setTarget} 을 직접 부르면
 * 버전에 안 흔들리고 「누구를 때릴지」가 한 곳에 모인다.
 */
@EventBusSubscriber(modid = LSRelics.MODID)
public final class SummonManager {
    private SummonManager() {}

    /** 엔티티에 박는 표식. persistentData 는 엔티티와 함께 저장돼 재시작 후에도 남는다. */
    public static final String TAG = "lsSummon";
    private static final String TAG_OWNER = "lsSummonOwner";

    // ── 프로토타입 수치 (손맛 확인용 — 직업이 확정되면 각성 배율에 묶는다) ──
    //
    // ⚠️ 첫 시험(2026-08-09)에서 «너무 약하다» 는 결과가 나와 올렸다.
    //    소환사는 **소환수가 곧 화력**이라 유물 평타처럼 취급하면 안 된다. 5성 유물이 90~100
    //    DPS 인데 잔영 3기 × 5 피해 ≈ 15 DPS 면 15% 다 — 있으나 마나였다.
    //    지금 값은 3기 기준 대략 40 DPS 를 겨눈 것이고, 여전히 «감»이다.
    public static final int LIFE_TICKS = 600;     // 30초 — 한 판을 지켜볼 수 있게 늘렸다
    private static final double LEASH = 24.0;     // 이보다 멀어지면 주인 곁으로 당긴다
    private static final double SEEK = 16.0;      // 표적을 찾는 반경
    private static final int RETARGET = 10;       // 0.5초마다 표적 갱신
    public static final double HP = 30.0;         // 14 → 30. 한 대 맞고 사라지면 뭘 봤는지 모른다
    public static final double DAMAGE = 14.0;     // 5 → 14

    private static final List<Vex> ACTIVE = new ArrayList<>();

    public static boolean isSummon(Entity e) {
        return e != null && e.getPersistentData().getBoolean(TAG);
    }

    public static int count(ServerPlayer owner) {
        int n = 0;
        for (Vex v : ACTIVE) {
            if (v.isAlive() && owner.getUUID().equals(ownerOf(v))) n++;
        }
        return n;
    }

    private static UUID ownerOf(Vex v) {
        if (!v.getPersistentData().hasUUID(TAG_OWNER)) return null;
        return v.getPersistentData().getUUID(TAG_OWNER);
    }

    /** 한 기 소환. 소환된 개체를 돌려준다(실패하면 null). */
    public static Vex summon(ServerLevel level, ServerPlayer owner, Vec3 at, int lifeTicks) {
        Vex v = new Vex(EntityType.VEX, level);
        v.moveTo(at.x, at.y, at.z, owner.getYRot(), 0);

        // ── 표적 목표를 통째로 비운다 ──
        // 바닐라 벡스의 targetSelector 는 ①주인(Raider) 피격 반응 ②소환주 표적 복사
        // ③**가장 가까운 플레이어** 다. 셋 다 우리에게 맞지 않고, 특히 ③은 주인을 때린다.
        v.targetSelector.removeAllGoals(g -> true);

        v.setLimitedLife(lifeTicks);          // 바닐라 수명 (다 되면 스스로 스러진다)
        v.setBoundOrigin(BlockPos.containing(owner.position()));
        v.setPersistenceRequired();           // 청크 경계에서 조용히 사라지지 않게
        v.setCustomName(Component.literal("§b별의 잔영").withStyle(ChatFormatting.AQUA));
        v.setCustomNameVisible(false);        // 여러 기라 이름표가 겹치면 화면이 지저분하다

        AttributeInstance hp = v.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(HP);
        AttributeInstance dmg = v.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(DAMAGE);
        v.setHealth((float) HP);

        v.getPersistentData().putBoolean(TAG, true);
        v.getPersistentData().putUUID(TAG_OWNER, owner.getUUID());

        level.addFreshEntity(v);
        ACTIVE.add(v);

        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.5, at.z, 20, 0.3, 0.5, 0.3, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 12, 0.3, 0.5, 0.3, 0.03);
        level.playSound(null, BlockPos.containing(at), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 1.4f);
        return v;
    }

    public static int dismiss(ServerPlayer owner) {
        int n = 0;
        for (Vex v : ACTIVE) {
            if (!owner.getUUID().equals(ownerOf(v))) continue;
            if (v.isAlive()) {
                if (v.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SOUL, v.getX(), v.getY() + 0.5, v.getZ(),
                        14, 0.3, 0.4, 0.3, 0.02);
                }
                v.discard();
                n++;
            }
        }
        ACTIVE.removeIf(v -> !v.isAlive());
        return n;
    }

    // ══════════════════════════════════════════════════════════════════
    //  틱 — 표적·거리·정리
    // ══════════════════════════════════════════════════════════════════
    private static int tick;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        boolean retarget = (++tick % RETARGET) == 0;

        Iterator<Vex> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Vex v = it.next();
            if (!v.isAlive() || v.isRemoved()) { it.remove(); continue; }
            if (!(v.level() instanceof ServerLevel sl)) { it.remove(); continue; }

            UUID id = ownerOf(v);
            ServerPlayer owner = id == null ? null : sl.getServer().getPlayerList().getPlayer(id);
            // 주인이 나가면 잔영도 사라진다 — 깔아두고 로그아웃해 남의 몹을 대신 잡는 걸 막는다
            // (DamageZoneManager 가 같은 규칙이다).
            if (owner == null || !owner.isAlive() || owner.level() != sl) {
                v.discard(); it.remove(); continue;
            }

            // 너무 멀어지면 주인 곁으로. 비행이라 길찾기로는 못 따라오는 지형이 많다.
            double d2 = v.distanceToSqr(owner);
            v.setBoundOrigin(BlockPos.containing(owner.position()));
            if (d2 > LEASH * LEASH) {
                Vec3 back = owner.position().add((Math.random() - 0.5) * 2, 1.6, (Math.random() - 0.5) * 2);
                v.teleportTo(back.x, back.y, back.z);
                v.setTarget(null);
                continue;
            }

            if (!retarget) continue;

            // 지금 표적이 아직 쓸 만하면 그대로 둔다 — 매번 새로 고르면 몹 사이를 왔다갔다 한다.
            LivingEntity cur = v.getTarget();
            if (cur != null && cur.isAlive() && cur.distanceToSqr(owner) <= SEEK * SEEK
                    && !(cur instanceof Player) && !isSummon(cur)) {
                continue;
            }
            v.setTarget(pick(sl, owner));
        }
    }

    /**
     * 주인 주변에서 때릴 것을 고른다. <b>주인이 최근에 때린 것</b>을 먼저 본다 —
     * 「내가 싸우는 것을 같이 친다」가 소환수에게 기대하는 동작이고, 가장 가까운 것만 고르면
     * 주인이 도망치는 중에도 엉뚱한 몹을 물고 늘어진다.
     */
    private static LivingEntity pick(ServerLevel level, ServerPlayer owner) {
        LivingEntity last = owner.getLastHurtMob();
        if (last != null && last.isAlive() && last.distanceToSqr(owner) <= SEEK * SEEK
                && !(last instanceof Player) && !isSummon(last)) {
            return last;
        }
        AABB box = owner.getBoundingBox().inflate(SEEK);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        // Monster 로 좁혔으므로 주민 검사는 필요 없다 — 그쪽은 Monster 가 아니다.
        for (Monster m : level.getEntitiesOfClass(Monster.class, box,
                e -> e.isAlive() && !isSummon(e))) {
            double d = m.distanceToSqr(owner);
            if (d < bestD) { bestD = d; best = m; }
        }
        return best;
    }

    // ══════════════════════════════════════════════════════════════════
    //  잔영은 사람을 안 때린다
    // ══════════════════════════════════════════════════════════════════

    /**
     * 표적을 우리가 정하므로 원칙적으로는 사람을 칠 일이 없다. 그래도 막아두는 이유:
     * 벡스의 돌진 공격은 «지나가면서» 부딪치는 것이라 표적이 아닌 것도 맞을 수 있고,
     * 남의 모드가 표적을 건드릴 여지도 있다. 소환수가 주인을 때리는 사고는 한 번만 나도
     * 그 직업을 아무도 안 쓰게 만든다.
     */
    @SubscribeEvent
    public static void onSummonHit(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Vex v) || !isSummon(v)) return;
        if (event.getEntity() instanceof Player || event.getEntity() instanceof AbstractVillager
                || isSummon(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * <b>사람의 공격은 잔영에게 안 들어간다.</b>
     *
     * <p>첫 시험에서 바로 나온 문제다 — 잔영이 주인 주변을 날아다니는데 평타·광역 스킬이
     * 그걸 같이 때렸다. 원인은 이 파일이 아니라 <b>스킬 쪽 필터</b>다:
     * {@code RelicSkills} 의 광역 판정이 전부 {@code !(en instanceof Player)} 로만 거르는데,
     * 벡스는 몬스터라 그 체를 그냥 통과한다. 재의 채찍·헤카테의 밤·결속의 매듭·일도양단이
     * 죄다 소환수를 때리고 있었다.
     *
     * <p>스킬 필터를 스무 곳 넘게 고치는 대신 여기 한 곳에서 막는다 —
     * {@code isRelic} 이나 {@code LSKubeBridge.isMonster} 를 한 곳만 고친 것과 같은 판단이다.
     * 놓치는 스킬이 생길 여지를 아예 없앤다.
     *
     * <p>주인만이 아니라 <b>모든 플레이어</b>를 막는다. 협동 서버에서 남의 광역기에 내 잔영이
     * 녹으면 그건 내 잘못도 아닌데 손해다. 몹·환경 피해는 그대로 들어간다 —
     * 잔영은 죽을 수 있어야 하고, 그게 소환사가 자리를 잡는 이유다.
     */
    @SubscribeEvent
    public static void onSummonHurt(LivingIncomingDamageEvent event) {
        if (!isSummon(event.getEntity())) return;
        if (event.getSource().getEntity() instanceof Player) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        for (Vex v : ACTIVE) {
            if (v.isAlive()) v.discard();
        }
        ACTIVE.clear();
    }
}
