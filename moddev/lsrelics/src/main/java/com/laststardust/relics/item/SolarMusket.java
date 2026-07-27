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
//   · R = 재장전 (기본 슬롯) — 20초마다 즉시, 쿨 중이면 2초  · V = 산탄 (이동, 2성)
//   · C = 작열탄 (추가, 3성)       · X = 일식 (궁극, 4성)
//   · 탄창을 비우면 자동 재장전 — 즉시가 놀고 있으면 그걸 쓰고, 아니면 2초간 무방비
//   · 실효 DPS = 6발 x 13.0 x 각성배율 / 7.5초 (5성 기준 31.2, 저격 패시브 최대 +40%)
//
// 한 발이 무거운 대신 리듬이 끊긴다는 점이 이 무기의 정체성이라, 활·지팡이의
// 균등 연사와 확실히 구분된다.
@EventBusSubscriber(modid = LSRelics.MODID)
public class SolarMusket extends Item implements RelicActions {
    public static final int MAG_SIZE = 6;
    public static final int FIRE_RATE = 20;     // 발사 간격(틱) — 1초에 1발
    // 1.5초 -> 2.0초 (2026-07-27). 아래 '즉시 재장전'이 생기면서 일반 재장전은
    // "타이밍을 놓쳤을 때의 대가"가 됐다. 대가가 있어야 R 을 언제 누르는지가 선택이 된다.
    public static final int RELOAD_TICKS = 40;  // 재장전 2.0초

    // ── 즉시 재장전 (R · 20초 쿨) ──
    // R 한 방에 탄창이 찬다. 20초에 한 번뿐이라 "지금 쓸까, 다음 교전까지 아낄까"가 생긴다.
    // 쿨 중에 눌러도 헛치지 않고 일반 2초 재장전으로 내려간다 — 급할 때 R 을 눌렀는데
    // 아무 일도 안 일어나는 게 제일 나쁘다.
    public static final int QUICK_RELOAD_CD = 400; // 20초

    // ── 연사 모드 「과열 전환」 (C · 3성) ──
    // 총이 라이플로 바뀌어 24발을 4발/초로 쏟아붓는다. 다 쏘면 자동으로 풀린다.
    //
    // 왜 이 형태인가: 마총 스킬은 쓰는 시간이 곧 사격을 멈추는 시간이라, "던져서 터지는 것"은
    // 무엇을 만들어도 결국 평타보다 약하다. 모드 전환은 사격을 대체하지 않고 **사격을 바꾼다** —
    // 그래서 이 무기에서 유일하게 손해가 아닌 스킬 형태다.
    //
    // 쿨 18초는 6초(한 탄창)의 배수다. 20초로 잡으면 탄창 주기와 어긋나 로테이션이 지저분해진다.
    // 즉시 재장전(20초)과 일부러 어긋내 뒀다 — 늘 같이 돌면 C 뒤에 R 이 항상 준비돼 긴장이 없다.
    public static final int RIFLE_CD = 720;        // 36초
    public static final int RIFLE_MAG = 30;        // 전환 시 이 탄창으로 교체
    // 초당 6발 = 3.33틱 간격. 틱은 정수라 3,3,4 를 돌려 10틱에 3발 = 정확히 6발/초로 맞춘다.
    // (3틱 고정이면 6.67발/초라 11% 더 쏜다 — 피해 계산이 어긋난다)
    private static final int[] RIFLE_RATE_SEQ = {3, 3, 4};
    // 5.5 -> 6.6 -> 4.5 (2026-07-27). 지속 8초/24발 -> 12초/30발·초당 6발로 늘어나면서
    // 한 번에 쏟는 양이 2.5배가 됐다. 36초 주기 총량을 목표 54 DPS 에 맞춰 역산한 값이다.
    //   36초 동안: 연사 60발(12초, 재장전 1회 포함) + 평타 18발 + 산탄 3회 + 일식 0.6회
    //   연사 몫이 전체의 약 54% — 이 무기를 "평타만 치는 무기"에서 빼내려는 게 목적이었다.
    private static final float RIFLE_DMG = 4.5f;   // 발당. 6발/초 x 4.5 x 3(5성) = 81 DPS
    // 지속시간 — 탄이 떨어져도 연사는 안 끝난다(중간에 2초 재장전하고 계속 쏜다).
    // 30발을 6발/초로 5초에 비우고, 2초 재장전, 다시 5초 = 12초에 딱 두 탄창이다.
    public static final int RIFLE_DURATION = 240;  // 12초

    // 15.0 -> 12.5 -> 11.5 -> 13.0
    //
    // ── 균등 하향을 그만두고 구조를 바꿨다 (2026-07-27) ──
    // 6인 파티 실측 60.9 (60.4 / 61.0 / 61.3, 더미 1기). 목표 54 대비 13% 초과였다.
    // 여기에 x0.89 를 걸어봤다가 되돌렸다 — 전체를 낮춰도 "무엇을 눌러야 하는가"는 안 바뀐다.
    //
    // 한때 "스코프만 87 / 작열탄 섞으면 30" 이라는 수치를 근거로 스코프가 압도적이라고 봤는데,
    // 지속 DPS 를 계산해보니 원래도 스코프가 평타보다 16% 높을 뿐이었다(아래 SCOPE_DMG).
    // - 그 87 이라는 값은 구조와 맞지 않는다. 근거로 쓰지 말 것 -
    // 믿을 수 있는 건 위의 6인 실측뿐이다.
    //
    // 그래서 총량이 아니라 주역을 바꿨다: 평타가 주력, 스코프가 상황용.
    // 이 무기의 정체성은 탄창 리듬인데 주력이 스코프면 그 리듬이 죽는다.
    // 13.0 -> 10.0 (2026-07-27). C 연사 모드에 예산을 넘겼다 — 평타가 총 DPS 의 3분의 2를
    // 먹으면 스킬을 안 눌러도 손해가 없어서 "평타만 치는 무기"가 된다.
    private static final float BULLET_DMG = 10.0f;
    private static final int BULLET_LIFE = 30;  // 틱 (속도 3.0 → 사거리 약 90칸)

    // ── 스코프 (기본 스킬 · 1성 · 우클릭 홀드) ──
    // 탄을 2발 먹는다 — 안 그러면 재장전 횟수까지 절반이 되어 스코프가 공짜 이득이 된다.
    public static final int SCOPE_CHARGE = 10;    // 진입 0.5초 — 그 전엔 노스코프 수치
    public static final int SCOPE_FIRE_RATE = 40; // 2초에 1발
    public static final int SCOPE_AMMO_COST = 2;
    // 35.0 -> 29.0 -> 26.68 -> 19.5 (2026-07-27)
    //
    // ── 평타와 주역을 맞바꾼다 ──
    // 5성·탄창 한 바퀴 기준 지속 DPS 로 보면 원래도 스코프가 평타보다 16% 높을 뿐이었다
    //   평타   11.5x3 = 34.5 x 0.8발/초 = 27.6
    //   스코프 26.68x3 = 80.0 x 0.4발/초(탄 2발) = 32.0
    // 그런데 이 무기의 정체성은 탄창 리듬이다. 주력이 스코프면 그 리듬이 죽는다.
    //   평타   13.0x3 = 39.0 x 0.8 = 31.2   <- 주력
    //   스코프 19.5x3 = 58.5 x 0.4 = 23.4   <- 상황용(장거리·관통·탄 효율)
    // 최대 지속 DPS 는 32.0 -> 31.2 로 거의 그대로다. 바뀌는 건 "무엇을 눌러야 하는가"다.
    private static final float SCOPE_DMG = 19.5f;
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
        CompoundTag t = tag(stack);
        if (t.getBoolean("rifle")) {
            // ── 연사 중 배율은 "아이템 사용"이 아니라 토글이다 ──
            // startUsingItem 을 쓰면 바닐라가 이동 입력을 0.2배로 깎는다(저격 스코프는 그게
            // 대가지만, 라이플에서 발이 묶이면 근접전 무기가 못 된다).
            // 그래서 상태만 뒤집고 아이템 사용은 시작하지 않는다 — 감속이 붙을 자리가 없다.
            if (!level.isClientSide) {
                boolean on = !t.getBoolean("rifleZoom");
                t.putBoolean("rifleZoom", on);
                save(stack, t);
                level.playSound(null, player.blockPosition(), SoundEvents.SPYGLASS_USE,
                    SoundSource.PLAYERS, 0.5f, on ? 1.5f : 1.1f);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        level.playSound(null, player.blockPosition(), SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 0.6f, 1.2f);
        return InteractionResultHolder.consume(stack);
    }

    // ── R = 수동 재장전 (기본 슬롯·1성) ──
    // 다른 유물의 R 자리가 솔라리스에겐 재장전이다. 탄창이 빌 때까지 기다리면 반드시
    // 전투 한복판에서 2초를 무방비로 서게 되는데, 그 타이밍을 직접 고를 수 있게 해준다.
    // 엄폐 중이나 교전 사이에 미리 채워두는 것이 이 무기의 실력 차가 나는 지점.
    //
    // 20초마다 한 번은 **즉시** 채운다. 그래서 R 은 "언제 누르나"가 아니라
    // "지금 즉시를 쓸까, 다음 교전까지 아낄까"의 선택이 된다.
    @Override
    public void basicSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        long now = level.getGameTime();
        CompoundTag t = tag(stack);
        // 연사 중엔 재장전이 없다. 허용하면 24발짜리 버스트 도중에 6발을 얹어 늘릴 수 있다
        // (게다가 잔탄이 6 미만이면 오히려 늘어난다 — 명백한 구멍이다).
        if (t.getBoolean("rifle")) {
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(),
                SoundSource.PLAYERS, 0.4f, 0.6f);
            return;
        }
        boolean reloading = pending(t.getLong("reloadEnd"), now, RELOAD_TICKS);
        boolean quickReady = !pending(t.getLong("quickEnd"), now, QUICK_RELOAD_CD);

        if (!reloading && ammo(t) >= MAG_SIZE) {
            // 가득 참 — 헛 재장전으로 시간을 날리지 않게 막는다.
            // 잔탄은 액션바에 이미 보이므로 짧게 소리로만 알린다(액션바를 뺏지 않는다).
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(),
                SoundSource.PLAYERS, 0.4f, 0.8f);
            return;
        }
        // 즉시 재장전은 **진행 중인 일반 재장전도 끊는다.** "2초 기다리다 위험해져서 R 로 끊는다"가
        // 이 스킬의 제일 좋은 쓰임이라, 재장전 중에 못 쓰게 하면 의미가 절반이 된다.
        if (quickReady) { quickReload(level, player, stack, t, now); return; }
        if (reloading) return;   // 쿨 중 + 이미 재장전 중이면 할 게 없다
        startReload(level, player, stack, t, now);
    }

    // 이동기(V·2성) = 산탄 (후퇴 넉백이 있어 회피기 역할)
    @Override
    public void doubleSneak(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.buckshot(level, player, stack);
    }

    // ── C = 과열 전환 (추가·3성) ──
    // 총을 라이플로 바꾸고 24발 탄창을 끼운다. 다 쏘면 자동으로 풀리고 빈 탄창이 된다.
    @Override
    public void extraSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        long now = level.getGameTime();
        CompoundTag t = tag(stack);
        if (t.getBoolean("rifle")) return;                       // 이미 연사 중
        if (pending(t.getLong("rifleEnd"), now, RIFLE_CD)) {
            // 쿨 중 — 액션바에 이미 남은 초가 뜨므로 소리로만 알린다
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(),
                SoundSource.PLAYERS, 0.4f, 0.6f);
            return;
        }
        t.putBoolean("rifle", true);
        t.putInt("ammo", RIFLE_MAG);
        t.putLong("reloadEnd", 0);        // 재장전 중이었으면 끊고 바로 전환된다
        t.putLong("rifleEnd", now + RIFLE_CD);
        t.putLong("rifleUntil", now + RIFLE_DURATION);
        t.putInt("rifleSeq", 0);
        t.putBoolean("rifleZoom", false);
        save(stack, t);
        player.stopUsingItem();           // 저격 스코프 중이었으면 내린다

        var pos = player.blockPosition();
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7f, 1.6f);
        level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.9f, 0.7f);
        level.sendParticles(ParticleTypes.FLAME,
            player.getX(), player.getY() + player.getBbHeight() * 0.7, player.getZ(), 24, 0.4, 0.3, 0.4, 0.03);
        player.displayClientMessage(Component.literal("§6☀ §c과열 전환 §7— 연사"), true);
    }

    // 연사 모드 해제 (탄창 소진). 해제 후엔 평소대로 자동 재장전이 걸린다.
    private static void endRifle(ServerLevel level, ServerPlayer player, CompoundTag t) {
        t.putBoolean("rifle", false);
        t.putBoolean("rifleZoom", false);
        t.putLong("rifleUntil", 0);
        t.putInt("ammo", 0);   // 과열된 탄창은 통째로 버린다 — 남은 연사탄이 일반탄으로 둔갑하지 않게
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND,
            SoundSource.PLAYERS, 0.5f, 1.9f);
    }

    // ── X = 궁극 (4성) — 모드에 따라 형태가 갈린다 ──
    //   평소   일식      : 60칸 한 줄 관통 (장거리)
    //   연사 중 탄막 집중 : 16칸 원뿔 광역 (근중거리) — 남은 탄을 전부 태우고 연사가 끝난다
    // 쿨은 공유한다(cdEclipse). 따로 두면 "연사 켜고 궁 두 번"이 항상 정답이 되어
    // 선택이 사라진다 — 하나의 충전을 어느 형태로 쓸지가 판단으로 남아야 한다.
    @Override
    public void ultimate(ServerLevel level, ServerPlayer player, ItemStack stack) {
        CompoundTag t = tag(stack);
        if (!t.getBoolean("rifle")) {
            RelicSkills.eclipse(level, player, stack);
            return;
        }
        int ammo = ammo(t);
        if (ammo <= 0) {
            // 빈 탄창으로 쓰면 위력이 0 인데 쿨만 날아간다 — 아예 막는다
            player.displayClientMessage(Component.literal("§7탄창이 비어 있다 — 탄막을 쏟을 게 없다."), true);
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(),
                SoundSource.PLAYERS, 0.4f, 0.6f);
            return;
        }
        // 쿨·성급 검사가 안에 있다. 실패하면(쿨 중) 탄을 먹지 않고 돌아간다.
        if (!RelicSkills.solarBarrage(level, player, stack, ammo)) return;

        // ── 여기서 태그를 **다시 읽어야 한다** ──
        // ready() 는 stack 의 custom_data 에 cdEclipse 를 직접 쓴다. 위에서 떠둔 스냅샷 t 는
        // 그 이전 상태라, 그대로 save 하면 방금 기록된 쿨이 통째로 지워진다
        // — 탄막 집중을 써도 일식 쿨이 안 돌던 원인이 이것이었다.
        CompoundTag after = tag(stack);

        // 대가: 남은 탄을 전부 먹고 연사가 끝난다
        long now = level.getGameTime();
        endRifle(level, player, after);
        startReload(level, player, stack, after, now);
    }

    // ── R·C 는 ready() 를 안 거친다 — 쿨을 직접 알려준다 ──
    @Override
    public long skillCooldownLeft(ServerLevel level, ItemStack stack, String cdKey) {
        CompoundTag t = tag(stack);
        long now = level.getGameTime();
        if ("quickEnd".equals(cdKey)) {
            long e = t.getLong("quickEnd");
            return pending(e, now, QUICK_RELOAD_CD) ? e - now : 0;
        }
        if ("rifleEnd".equals(cdKey)) {
            if (t.getBoolean("rifle")) {
                // 쓰는 중 — 남은 **지속**을 돌려준다. 0 을 주면 "C 준비"로 보여
                // 지금 쓰고 있는데 또 쓸 수 있는 것처럼 읽힌다. 이름은 "연사"로 갈린다.
                long until = t.getLong("rifleUntil");
                return until > now ? until - now : 1;
            }
            long e = t.getLong("rifleEnd");
            return pending(e, now, RIFLE_CD) ? e - now : 0;
        }
        return -1;   // 나머지는 기본 처리
    }

    // 연사 중이면 궁극기가 탄막 집중으로 갈린다 — 이름도 따라가야 뭘 누르는지 안다.
    @Override
    public String skillLabel(ItemStack stack, String cdKey, String def) {
        if (!tag(stack).getBoolean("rifle")) return def;
        if ("cdEclipse".equals(cdKey)) return "탄막";
        if ("rifleEnd".equals(cdKey)) return "연사";
        return def;
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
    //   quickEnd   즉시 재장전 쿨 종료 게임시간 (0 = 준비됨)

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

        // 연사 모드가 스코프보다 우선한다 (연사 중엔 스코프 진입 자체가 막혀 있다)
        boolean rifle = t.getBoolean("rifle");
        boolean scoped = !rifle && isScoped(player, stack);
        int seq = t.getInt("rifleSeq");
        int rate = rifle ? RIFLE_RATE_SEQ[Math.floorMod(seq, RIFLE_RATE_SEQ.length)]
                         : (scoped ? SCOPE_FIRE_RATE : FIRE_RATE);
        int cost = scoped ? SCOPE_AMMO_COST : 1;

        if (pending(t.getLong("reloadEnd"), now, RELOAD_TICKS)) return;      // 재장전 중
        // span 은 "비정상값 회수"용 상한이라 가장 긴 간격(스코프)에 맞춘다 — 어떤 모드든 안전하다
        if (pending(t.getLong("nextShot"), now, SCOPE_FIRE_RATE)) return;    // 연사 간격

        int ammo = ammo(t);
        if (ammo < cost) { // 스코프 사격은 2발이 필요하다 — 1발만 남으면 재장전
            if (rifle) {
                // 연사 중엔 탄이 떨어져도 모드가 끝나지 않는다 — 2초 재장전하고 계속 쏜다.
                // 즉시 재장전은 쓰지 않는다. 연사 안에서 소모해버리면 연사가 끝난 뒤
                // 정작 필요할 때 없다 (그 쿨은 평타 리듬을 지키는 데 써야 한다).
                startReload(level, player, stack, t, now);
            } else if (!pending(t.getLong("quickEnd"), now, QUICK_RELOAD_CD)) {
                // 쿨이 다 돌았는데 탄이 비어 2초를 서 있는 건 순수 손해다 — 아껴서 얻는 것도 없다.
                quickReload(level, player, stack, t, now);
            } else {
                startReload(level, player, stack, t, now);
            }
            return;
        }

        ammo -= cost;
        t.putInt("ammo", ammo);
        t.putLong("nextShot", now + rate);
        if (rifle) t.putInt("rifleSeq", seq + 1);
        if (ammo <= 0) {
            // 연사 중이면 모드를 유지한 채 재장전한다. 재장전 완료 처리(onPlayerTick)가
            // rifle 을 보고 30발을 채운다 — 그게 없으면 연사 모드에 6발이 들어가 어긋난다.
            startReload(level, player, stack, t, now);
        } else {
            save(stack, t);
        }

        // ── 발사 ──
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        BulletManager.fire(level, player, eye.add(look.scale(0.6)), look,
            RelicSkills.dmg(stack, rifle ? RIFLE_DMG : (scoped ? SCOPE_DMG : BULLET_DMG)),
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

    // 즉시 재장전 — 탄창을 그 자리에서 채우고 20초 쿨을 건다.
    // 재장전 중(reloadEnd)이었더라도 그걸 끊고 채운다. "2초 기다리다 R 로 끊는다"가
    // 이 스킬의 제일 좋은 쓰임이라, 재장전 중에 못 쓰게 하면 의미가 절반이 된다.
    private static void quickReload(ServerLevel level, ServerPlayer player, ItemStack stack, CompoundTag t, long now) {
        t.putInt("ammo", MAG_SIZE);
        t.putLong("reloadEnd", 0);
        t.putLong("quickEnd", now + QUICK_RELOAD_CD);
        save(stack, t);
        // 일반 재장전과 확실히 다른 소리 — 눌렀을 때 뭐가 걸렸는지 귀로 구분돼야 한다
        var pos = player.blockPosition();
        level.playSound(null, pos, SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1.0f, 1.6f);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.8f);
        level.sendParticles(ParticleTypes.END_ROD,
            player.getX(), player.getY() + player.getBbHeight() * 0.7, player.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
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

        // ── 연사 지속시간 만료 ──
        // 다 쏘면 leftAttack 에서 끝나지만, 쏘지 않고 버티면 거기까지 못 간다.
        // 그러면 연사 탄창을 무한정 들고 다니게 되므로 시간으로도 끝낸다.
        if (t.getBoolean("rifle")) {
            long until = t.getLong("rifleUntil");
            if (until != 0 && now >= until) {
                endRifle(level, player, t);
                startReload(level, player, stack, t, now);   // 빈 탄창이 되므로 바로 재장전
                return;
            }
        }

        long rEnd = t.getLong("reloadEnd");
        boolean reloading = pending(rEnd, now, RELOAD_TICKS);

        if (rEnd != 0 && !reloading) { // 재장전 완료(또는 비정상값 회수)
            t.putInt("ammo", t.getBoolean("rifle") ? RIFLE_MAG : MAG_SIZE);
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
        // R·C 의 쿨은 CooldownDisplay 가 같은 줄 뒤쪽에 그린다(skillCooldownLeft).
        // 여기서 또 쓰면 한 줄에 C 가 두 번 나온다 — 실제로 그랬다.
        if (t.getBoolean("rifle")) {
            // 연사는 탄이 30발이라 6칸 게이지로는 못 그린다. 숫자로 보여준다.
            // 남은 지속은 뒤쪽 "C 연사 8.0s" 가 보여준다 — 여기선 잔탄만
            return "§c⚡ §e" + a + "§8/" + RIFLE_MAG;
        }
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

    // ── 내구도를 소모하지 않는다 (2026-07-27) ──
    // 유물은 첫 공세를 맨몸으로 버텨야 얻는 물건이라 소모품이 아니다. 닳아 없어지면
    // 그 직업을 통째로 잃는다. RiftAxe 와 같은 처리 — 이 훅 하나가 모든 경로를 덮는다.
    @Override
    public <T extends net.minecraft.world.entity.LivingEntity> int damageItem(
            ItemStack stack, int amount, T entity, java.util.function.Consumer<net.minecraft.world.item.Item> onBroken) {
        return 0;
    }
}
