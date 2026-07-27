package com.laststardust.relics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.laststardust.relics.item.RelicSkills;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 들고 있는 유물의 스킬별 쿨다운을 액션바 한 줄로 표시.
// 예) R 파동 3.4s · V 돌진 준비 · C 반격 준비 · X 맹세 41s
//
// 유물을 든 동안 상시 표시한다 — 스킬이 어느 키인지 외우지 않아도 되는 게 이 표시의 요점이라
// "쿨이 돌 때만" 보이면 정작 배우는 단계에서 안 보인다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class CooldownDisplay {
    private CooldownDisplay() {}

    // {쿨 키, 짧은 이름, 총 쿨(틱), 키 라벨}
    //   · 총 쿨은 비정상 값 판별에도 쓰인다
    //   · 키 라벨은 기본 키다. 이 표시는 서버에서 만들어지는데 키 재지정은 클라 설정이라
    //     서버가 알 수 없다 — 키를 바꿔 쓰는 사람에겐 라벨이 안 맞을 수 있다.
    //     (배우는 단계에서 "이 스킬이 무슨 키였더라"를 없애는 게 목적이라 기본값 기준으로 둔다)
    //   · 솔라리스는 기본 슬롯이 쿨 없는 수동 재장전이라 항목이 셋뿐이다.
    private static final Object[][] GUARDIAN_SKILLS = {
        {"cdPulse", "파동", 160, "R"}, {"cdCharge", "돌진", 200, "V"}, {"cdParry", "반격", 280, "C"}, {"cdOath", "맹세", 1200, "X"}
    };
    private static final Object[][] HUNTER_SKILLS = {
        {"cdHaste", "속사", 240, "R"}, {"cdEvade", "회피", 120, "V"}, {"cdMeteor", "유성", 200, "C"}, {"cdStorm", "폭풍", 900, "X"}
    };
    private static final Object[][] SAGE_SKILLS = {
        {"cdAnnihilate", "소멸", 120, "R"}, {"cdBlink", "도약", 140, "V"}, {"cdGravity", "중력", 240, "C"}, {"cdSupernova", "초신성", 1200, "X"}
    };
    private static final Object[][] PIONEER_SKILLS = {
        {"cdRift", "균열", 160, "R"}, {"cdStomp", "지축", 120, "V"}, {"cdSplit", "대지", 240, "C"}, {"cdTitan", "강림", 1200, "X"}
    };
    // ── 신규 4종 ──
    // 솔라리스는 R(즉시 재장전)·C(과열 전환)가 ready() 를 안 거치므로 쿨을 아이템이 직접 준다
    // (RelicActions.skillCooldownLeft). 여기 적힌 총 쿨은 표시·검증용이다.
    private static final Object[][] GUNNER_SKILLS = {
        {"quickEnd", "재장전", 400, "R"}, {"cdBuckshot", "산탄", 220, "V"},
        {"rifleEnd", "과열", 720, "C"}, {"cdEclipse", "일식", 1200, "X"}
    };
    private static final Object[][] HEALER_SKILLS = {
        {"cdJudge", "심판", 160, "R"}, {"cdAngelStep", "발걸음", 160, "V"}, {"cdSanctuary", "성역", 400, "C"}, {"cdRevive", "소생", 1800, "X"}
    };
    private static final Object[][] ASSASSIN_SKILLS = {
        {"cdVicious", "급소", 80, "R"}, {"cdLeap", "도약", 140, "V"}, {"cdMist", "안개", 300, "C"}, {"cdAbyss", "무저갱", 1200, "X"}
    };
    private static final Object[][] LANCER_SKILLS = {
        {"cdJavelin", "투창", 160, "R"}, {"cdDash", "돌진", 180, "V"}, {"cdPierce", "꿰뚫기", 160, "C"}, {"cdBabylon", "백창", 1200, "X"}
    };

    // ── 액션바 양보 ──
    // 액션바는 한 줄뿐이라 여러 시스템이 겹쳐 쓴다(탄약 표시·소생 알림·DPS 측정 바).
    // 쿨다운을 상시 표시하면 그것들이 다음 틱에 덮여 사라지므로, 다른 곳에서 액션바를 쓸 때
    // 잠시 비켜준다. 스킬 발동("✦ 발동!")은 일부러 양보하지 않는다 — 쿨다운 표시 자체가
    // 더 나은 피드백이라 굳이 가릴 이유가 없다.
    private static final Map<UUID, Long> YIELD_UNTIL = new HashMap<>();

    public static void yieldFor(ServerPlayer player, int ticks) {
        YIELD_UNTIL.put(player.getUUID(), player.level().getGameTime() + ticks);
    }

    private static boolean yielding(ServerPlayer player) {
        Long until = YIELD_UNTIL.get(player.getUUID());
        if (until == null) return false;
        if (player.level().getGameTime() >= until) {
            YIELD_UNTIL.remove(player.getUUID());
            return false;
        }
        return true;
    }

    // 마지막으로 보낸 문자열 + 시각. 액션바는 몇 초 뒤 흐려지므로 주기적으로 다시 보내야 하는데,
    // 매 틱 보내면 초당 20패킷이 된다. 내용이 바뀔 때 + 20틱마다만 보낸다.
    private static final Map<UUID, String> LAST_TEXT = new HashMap<>();
    private static final Map<UUID, Long> LAST_SENT = new HashMap<>();
    private static final int REFRESH_TICKS = 20;

    // ── 귀환석 쿨다운 ──
    // 유물 줄에 끼워넣지 않는다. 1시간 쿨이라 거의 항상 떠 있게 되는데, 그러면 정작
    // 초 단위로 봐야 하는 스킬 쿨을 밀어낸다. "들고 있는 것의 상태를 보여준다"는
    // 이 표시의 규칙을 그대로 따라, 귀환석을 들었을 때만 귀환석 줄을 보여준다.
    //
    // 남은 틱은 ItemCooldowns 에서 직접 못 읽는다(맵이 private) — 남은 비율에
    // 총 쿨을 곱해 되돌린다. 총 쿨이 한 곳(HearthStone)에만 있어 어긋날 일은 없다.
    private static long hearthLeftTicks(ServerPlayer player) {
        float pct = player.getCooldowns().getCooldownPercent(LSRelics.HEARTHSTONE.get(), 0.0f);
        if (pct <= 0.0f) return 0L;
        return (long) Math.ceil(pct * com.laststardust.relics.item.HearthStone.COOLDOWN_TICKS);
    }

    // 1시간 쿨을 "3421.5s" 로 쓰면 읽을 수가 없다. 분:초로 접는다.
    private static String mmss(long ticks) {
        long total = ticks / 20;
        long m = total / 60, s = total % 60;
        return m > 0 ? String.format("%d분 %02d초", m, s) : String.format("%d초", s);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ItemStack held = player.getMainHandItem();
            Item item = held.getItem();

            if (item == LSRelics.HEARTHSTONE.get()) {
                if (!(player.level() instanceof ServerLevel hl)) continue;
                if (yielding(player)) { LAST_TEXT.remove(player.getUUID()); continue; }
                long left = hearthLeftTicks(player);
                send(player, hl, left > 0
                    ? "§8귀환석 §7재사용까지 §e" + mmss(left)
                    : "§8귀환석 §a준비 §8┃ §7우클릭 3초 — 성역으로");
                continue;
            }

            Object[][] skills;
            if (item == LSRelics.GUARDIAN.get()) skills = GUARDIAN_SKILLS;
            else if (item == LSRelics.HUNTER.get()) skills = HUNTER_SKILLS;
            else if (item == LSRelics.SAGE.get()) skills = SAGE_SKILLS;
            else if (item == LSRelics.PIONEER.get()) skills = PIONEER_SKILLS;
            else if (item == LSRelics.GUNNER.get()) skills = GUNNER_SKILLS;
            else if (item == LSRelics.HEALER.get()) skills = HEALER_SKILLS;
            else if (item == LSRelics.ASSASSIN.get()) skills = ASSASSIN_SKILLS;
            else if (item == LSRelics.LANCER.get()) skills = LANCER_SKILLS;
            else {
                // 유물을 내려놓으면 기록도 지운다 — 다시 들었을 때 곧바로 다시 뜨게.
                LAST_TEXT.remove(player.getUUID());
                continue;
            }
            if (!(player.level() instanceof ServerLevel level)) continue;
            if (yielding(player)) { LAST_TEXT.remove(player.getUUID()); continue; }

            StringBuilder sb = new StringBuilder();
            // 유물이 자기 상태를 갖고 있으면(솔라리스 잔탄 등) 줄 앞에 얹는다.
            // 액션바는 한 줄뿐이라 따로 쓰면 서로 덮어써 깜빡인다 — 합쳐서 한 번에 보낸다.
            if (item instanceof com.laststardust.relics.item.RelicActions ra) {
                String status = ra.hudStatus(player, held);
                if (status != null && !status.isEmpty()) sb.append(status).append(" §8┃ ");
            }
            boolean first = true;
            for (Object[] s : skills) {
                String key = (String) s[0];
                String name = (String) s[1];
                long left;
                if (item instanceof com.laststardust.relics.item.RelicActions ra2) {
                    long own = ra2.skillCooldownLeft(level, held, key);
                    left = own >= 0 ? own : RelicSkills.cooldownLeft(level, held, key, (Integer) s[2]);
                    name = ra2.skillLabel(held, key, name);
                } else {
                    left = RelicSkills.cooldownLeft(level, held, key, (Integer) s[2]);
                }
                if (!first) sb.append(" §8· ");
                first = false;
                if (left > 0) sb.append(String.format("§8%s §7%s §e%.1fs", s[3], name, left / 20.0));
                else          sb.append(String.format("§8%s §7%s §a준비", s[3], name));
            }
            // 유물을 든 동안은 항상 보여준다 — 어떤 스킬이 어느 키인지 외우지 않아도 되게.
            send(player, level, sb.toString());
        }
    }

    // 액션바로 보낸다. 내용이 바뀔 때 + 20틱마다만 — 매 틱 보내면 초당 20패킷이 된다.
    private static void send(ServerPlayer player, ServerLevel level, String text) {
        UUID id = player.getUUID();
        long now = level.getGameTime();
        boolean changed = !text.equals(LAST_TEXT.get(id));
        boolean stale = now - LAST_SENT.getOrDefault(id, 0L) >= REFRESH_TICKS;
        if (!changed && !stale) return;

        LAST_TEXT.put(id, text);
        LAST_SENT.put(id, now);
        player.displayClientMessage(Component.literal(text), true);
    }
}
