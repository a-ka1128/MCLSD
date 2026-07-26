package com.laststardust.relics.item;

import com.laststardust.relics.BulletManager;
import com.laststardust.relics.LSRelics;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 솔라리스 — 마총. 8종 중 유일하게 "탄창 리듬"이 있는 평타를 쓴다.
//   · 좌클릭 홀드 = 태양탄 연사 (1초에 1발, 6발 탄창) · 우클릭 홀드 = 스코프
//   · R = 수동 재장전 (기본 슬롯)  · V = 산탄 (이동, 2성)
//   · C = 작열탄 (추가, 3성)       · X = 일식 (궁극, 4성)
//   · 탄창을 비우면 자동으로 1.5초 재장전 — 그동안 무방비
//   · 실효 DPS = 6발 × 15.0 ÷ 7.5초 = 12.0 (원거리 순수 딜러 기준치)
//
// 한 발이 무거운 대신 리듬이 끊긴다는 점이 이 무기의 정체성이라, 활·지팡이의
// 균등 연사와 확실히 구분된다.
@EventBusSubscriber(modid = LSRelics.MODID)
public class SolarMusket extends Item implements RelicActions {
    public static final int MAG_SIZE = 6;
    public static final int FIRE_RATE = 20;     // 발사 간격(틱) — 1초에 1발
    public static final int RELOAD_TICKS = 30;  // 재장전 1.5초
    // 2026-07-26 하향 2회: 15.0 → 12.5 → 11.5.
    // 최종 근거는 **최적 플레이 60초 실측 58.9 DPS** (목표 54) → ×0.92.
    // 최적 플레이 = 스코프 사격 + 궁극만. 작열탄·산탄은 오히려 DPS 를 떨어뜨린다
    // (작열탄 30 < 스코프 87) — 그건 배율이 아니라 구조 문제라 따로 봐야 한다.
    private static final float BULLET_DMG = 11.5f;
    private static final int BULLET_LIFE = 30;  // 틱 (속도 3.0 → 사거리 약 90칸)

    // ── 스코프 (기본 스킬 · 1성 · 우클릭 홀드) ──
    // 다른 유물의 기본 스킬이 대략 +2.0 DPS를 보태므로(셀레스티아 소멸 2.0), 스코프도 같은 값어치가
    // 되도록 맞췄다. 노스코프 12.0 → 스코프 14.0.
    //   3발 × 35.0 ÷ 7.5초 = 14.0   (탄 2발 소모라 재장전 주기 7.5초는 노스코프와 동일)
    // 탄을 2발 먹지 않으면 재장전 횟수까지 절반이 되어 스코프가 공짜 이득이 된다.
    public static final int SCOPE_CHARGE = 10;    // 진입 0.5초 — 그 전엔 노스코프 수치
    public static final int SCOPE_FIRE_RATE = 40; // 2초에 1발
    public static final int SCOPE_AMMO_COST = 2;
    private static final float SCOPE_DMG = 26.68f;   // 35.0 → 29.0 → 26.68 (평타와 같은 비율)
    private static final int SCOPE_BULLET_LIFE = 30;
    private static final int USE_DURATION = 72000; // 망원경과 동일 — 사실상 무제한 홀드

    public SolarMusket(Properties properties) {
        super(properties);
    }

    @Override
    public boolean firesOnLeftClick() {
        return true;
    }

    // ── 우클릭 홀드 = 스코프 ──
    // 바닐라 아이템 사용 상태를 그대로 쓴다. 이동 감속(-80%)이 자동으로 붙고,
    // 클라이언트도 isUsingItem()만 보면 되므로 별도 동기화가 필요 없다.
    // 8종 중 유일하게 우클릭을 쓰는 유물이다 — 조준은 홀드 의미가 필요해 키로 옮길 수 없다.
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        level.playSound(null, player.blockPosition(), SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 0.6f, 1.2f);
        return InteractionResultHolder.consume(stack);
    }

    // ── R = 수동 재장전 (기본 슬롯·1성) ──
    // 다른 유물의 R 자리가 솔라리스에겐 재장전이다. 탄창이 빌 때까지 기다리면 반드시
    // 전투 한복판에서 1.5초를 무방비로 서게 되는데, 그 타이밍을 직접 고를 수 있게 해준다.
    // 엄폐 중이나 교전 사이에 미리 채워두는 것이 이 무기의 실력 차가 나는 지점.
    @Override
    public void basicSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        long now = level.getGameTime();
        CompoundTag t = tag(stack);
        if (pending(t.getLong("reloadEnd"), now, RELOAD_TICKS)) return; // 이미 재장전 중
        if (ammo(t) >= MAG_SIZE) {
            // 가득 참 — 헛 재장전으로 1.5초를 날리지 않게 막는다.
            // 잔탄은 액션바에 이미 보이므로 짧게 소리로만 알린다(액션바를 뺏지 않는다).
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(),
                SoundSource.PLAYERS, 0.4f, 0.8f);
            return;
        }
        startReload(level, player, stack, t, now);
    }

    // 이동기(V·2성) = 산탄 (후퇴 넉백이 있어 회피기 역할)
    @Override
    public void doubleSneak(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.buckshot(level, player, stack);
    }

    // ── C = 작열탄 (추가·3성) ──
    @Override
    public void extraSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.solarFlare(level, player, stack);
    }

    // ── X = 일식 (궁극·4성) ──
    @Override
    public void ultimate(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.eclipse(level, player, stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!level.isClientSide) {
            level.playSound(null, entity.blockPosition(), SoundEvents.SPYGLASS_STOP_USING, SoundSource.PLAYERS, 0.5f, 1.2f);
        }
    }

    // 재장전 소리 한 번. **겹쳐서 두 번** 재생한다.
    //
    // 마크의 playSound 볼륨은 1.0 을 넘겨도 소리가 커지지 않는다 — 들리는 거리(16칸 × 볼륨)만
    // 늘어난다. 총을 든 본인은 거리 0 이라 1.6 을 넣어도 1.0 과 똑같이 들린다.
    // 그래서 볼륨은 1.0 으로 두고, 같은 소리를 미세하게 다른 음정으로 한 겹 더 얹어 진폭을 키운다.
    // (음정을 살짝 어긋내는 건 두 파형이 겹쳐 빈 소리가 나는 걸 피하려는 것 — 게임 오디오 상투 수단)
    private static void reloadSound(ServerLevel level, ServerPlayer player,
                                    net.minecraft.sounds.SoundEvent sound, float pitch) {
        var pos = player.blockPosition();
        level.playSound(null, pos, sound, SoundSource.PLAYERS, 1.0f, pitch);
        level.playSound(null, pos, sound, SoundSource.PLAYERS, 1.0f, pitch * 0.94f);
    }

    // 스코프가 완전히 자리 잡았는가 (진입 0.5초 이후).
    // 클라이언트(줌)와 서버(피해) 양쪽에서 같은 판정을 쓴다.
    public static boolean isScoped(LivingEntity entity, ItemStack stack) {
        if (!entity.isUsingItem() || entity.getUseItem() != stack) return false;
        return scopeProgress(entity) >= 1.0f;
    }

    // 스코프 진입 진행도 0.0~1.0 (줌 연출을 부드럽게 하려고 노출)
    public static float scopeProgress(LivingEntity entity) {
        int used = USE_DURATION - entity.getUseItemRemainingTicks();
        return Math.min(1.0f, used / (float) SCOPE_CHARGE);
    }

    // ── 상태 (custom_data) ──
    //   ammo       남은 탄 (키가 없으면 가득 찬 것으로 본다)
    //   reloadEnd  재장전 완료 게임시간 (0 = 재장전 중 아님)
    //   nextShot   다음 발사 가능 게임시간

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void save(ItemStack stack, CompoundTag t) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    private static int ammo(CompoundTag t) {
        return t.contains("ammo") ? t.getInt("ammo") : MAG_SIZE;
    }

    // 남은 시간이 정상 범위를 벗어나면 월드 시간 되감김으로 생긴 값이므로 무시한다.
    // (다른 스킬 쿨다운과 같은 방어 로직 — 없으면 총이 영영 재장전 상태로 굳는다)
    private static boolean pending(long until, long now, int span) {
        long left = until - now;
        return left > 0 && left <= span;
    }

    @Override
    public void leftAttack(ServerLevel level, ServerPlayer player, ItemStack stack) {
        long now = level.getGameTime();
        CompoundTag t = tag(stack);

        boolean scoped = isScoped(player, stack);
        int rate = scoped ? SCOPE_FIRE_RATE : FIRE_RATE;
        int cost = scoped ? SCOPE_AMMO_COST : 1;

        if (pending(t.getLong("reloadEnd"), now, RELOAD_TICKS)) return;      // 재장전 중
        if (pending(t.getLong("nextShot"), now, SCOPE_FIRE_RATE)) return;    // 연사 간격

        int ammo = ammo(t);
        if (ammo < cost) { // 스코프 사격은 2발이 필요하다 — 1발만 남으면 재장전
            startReload(level, player, stack, t, now);
            return;
        }

        ammo -= cost;
        t.putInt("ammo", ammo);
        t.putLong("nextShot", now + rate);
        if (ammo <= 0) {
            startReload(level, player, stack, t, now);
        } else {
            save(stack, t);
        }

        // ── 발사 ──
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        BulletManager.fire(level, player, eye.add(look.scale(0.6)), look,
            RelicSkills.dmg(stack, scoped ? SCOPE_DMG : BULLET_DMG),
            scoped ? SCOPE_BULLET_LIFE : BULLET_LIFE, scoped);

        // 총구 화염 + 반동감 있는 사운드 (스코프는 더 묵직하게)
        Vec3 muzzle = eye.add(look.scale(1.0));
        int n = scoped ? 10 : 6;
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, n, 0.05, 0.05, 0.05, 0.02);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, n + 2, 0.08, 0.08, 0.08, 0.01);
        level.sendParticles(ParticleTypes.END_ROD, muzzle.x, muzzle.y, muzzle.z, 4, 0.04, 0.04, 0.04, 0.03);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
            SoundSource.PLAYERS, scoped ? 0.6f : 0.35f, scoped ? 1.5f : 1.9f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
            SoundSource.PLAYERS, 0.5f, scoped ? 1.2f : 1.6f);
    }

    private static void startReload(ServerLevel level, ServerPlayer player, ItemStack stack, CompoundTag t, long now) {
        t.putInt("ammo", 0);
        t.putLong("reloadEnd", now + RELOAD_TICKS);
        save(stack, t);
        reloadSound(level, player, SoundEvents.CROSSBOW_LOADING_START.value(), 1.0f);
    }

    // ── 재장전 완료 처리 + 잔탄 HUD ──
    // 재장전 완료를 좌클릭 시점에만 처리하면, 총을 든 채 가만히 있을 때 "장전 완료"를
    // 알 방법이 없다. 매 틱 검사해서 완료 순간에 소리와 표시를 준다.
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() != LSRelics.GUNNER.get()) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        long now = level.getGameTime();
        CompoundTag t = tag(stack);
        long rEnd = t.getLong("reloadEnd");
        boolean reloading = pending(rEnd, now, RELOAD_TICKS);

        if (rEnd != 0 && !reloading) { // 재장전 완료(또는 비정상값 회수)
            t.putInt("ammo", MAG_SIZE);
            t.putLong("reloadEnd", 0);
            save(stack, t);
            // 표시는 hudStatus 가 상시로 하고 있으므로 여기서는 소리만 준다.
            reloadSound(level, player, SoundEvents.CROSSBOW_LOADING_END.value(), 1.3f);
            return;
        }

        // ── 재장전 중간 소리 ──
        // 예전엔 시작·끝에만 소리가 있어 1.5초 내내 무음이었다. 이 무기는 "재장전 동안 무방비"가
        // 핵심 리듬이라, 그 시간이 소리로 흘러야 언제 끝나는지 화면을 안 봐도 안다.
        // SoundScheduler 대신 여기서 처리한다 — 그쪽은 좌표 고정이라 걸어 다니면 소리가 뒤에 남는다.
        if (reloading) {
            int elapsed = RELOAD_TICKS - (int) (rEnd - now);
            if (elapsed == 8) {   // 탄을 밀어 넣는 금속음
                reloadSound(level, player, SoundEvents.ARMOR_EQUIP_IRON.value(), 1.6f);
            } else if (elapsed == 18) {  // 노리쇠를 당기는 소리
                reloadSound(level, player, SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 1.1f);
            }
        }
    }

    // 액션바 앞머리 — 잔탄/재장전/조준. 쿨다운 줄과 같은 줄에 합쳐져 함께 보인다.
    // 예) ☀ ●●●●○○ 4/6 ◎ · V 산탄 3.2s · C 작열 준비 · X 일식 준비
    @Override
    public String hudStatus(ServerPlayer player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        long now = level.getGameTime();
        CompoundTag t = tag(stack);
        long rEnd = t.getLong("reloadEnd");
        if (pending(rEnd, now, RELOAD_TICKS)) {
            return String.format("§6☀ §c재장전 %.1fs", (rEnd - now) / 20.0);
        }
        int a = ammo(t);
        String scope = isScoped(player, stack) ? " §b◎" : "";
        return "§6☀ §7" + bar(a) + " §8" + a + "/" + MAG_SIZE + scope;
    }

    private static String bar(int ammo) {
        StringBuilder sb = new StringBuilder("§e");
        for (int i = 0; i < MAG_SIZE; i++) {
            if (i == ammo) sb.append("§8");
            sb.append(i < ammo ? "●" : "○");
        }
        return sb.toString();
    }
}
