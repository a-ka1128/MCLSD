package com.laststardust.relics.compat;

import com.laststardust.relics.data.LSData;
import com.laststardust.relics.data.SiegeData;
import com.laststardust.relics.data.TownCatalog;
import com.laststardust.relics.town.TownGui;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// KubeJS 스크립트 ↔ 모드 데이터의 다리. **이관 기간 동안만 존재한다.**
//
// ── 왜 필요한가 ──
// 마을·금고는 모드(LSData)로 옮겼지만 공성·관문·봉화·현상금·구출은 아직 스크립트다.
// 그 스크립트들이 금고에 보상을 넣고 비용을 확인해야 하는데, 옛 persistentData 키를 그대로 두면
// **모드의 금고와 스크립트의 금고가 서로 다른 두 개**가 되어 공성 보상이 영영 도착하지 않는다.
//
// 그래서 남은 스크립트는 이 바인딩을 통해 **모드가 가진 하나의 금고**를 본다:
//     LS.treasury(server)            // 잔액
//     LS.addTreasury(server, 50)     // 적립
//     LS.spendTreasury(server, 200)  // 지출 (부족하면 false)
//     LS.townLevel(server, 'ramparts')
//
// ── 수명 ──
// 남은 시스템을 전부 모드로 옮기면 이 파일과 build.gradle 의 KubeJS compileOnly 를 함께 지운다.
// 그때까지는 "데이터의 단일 소유자는 모드"라는 원칙을 지키게 해 주는 장치다.
public class LSKubeBridge implements KubeJSPlugin {

    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("LS", new Api());
    }

    // Rhino 가 public 메서드를 그대로 노출한다.
    public static class Api {

        public int treasury(MinecraftServer server) {
            return server == null ? 0 : LSData.get(server).town().treasury();
        }

        public void addTreasury(MinecraftServer server, int amount) {
            if (server == null || amount == 0) return;
            LSData data = LSData.get(server);
            data.town().addTreasury(amount);
            data.dirty();
            TownGui.syncAll(server);
        }

        // 부족하면 아무것도 하지 않고 false. 호출부가 "확인 후 차감"을 두 번 하지 않아도 되게.
        public boolean spendTreasury(MinecraftServer server, int amount) {
            if (server == null) return false;
            LSData data = LSData.get(server);
            if (!data.town().spend(amount)) return false;
            data.dirty();
            TownGui.syncAll(server);
            return true;
        }

        public int townLevel(MinecraftServer server, String track) {
            if (server == null || TownCatalog.byKey(track) == null) return 0;
            return LSData.get(server).town().level(track);
        }

        public void addContribution(MinecraftServer server, String name, int points) {
            if (server == null || name == null || points <= 0) return;
            LSData data = LSData.get(server);
            data.town().addContribution(name, points);
            data.dirty();
        }

        // ── 기여도 읽기 ──
        // 쓰기만 있고 읽기가 없어서 ls_stats.js 가 옛 persistentData 키(town_c_<name>)를 계속 읽고 있었다.
        // 그 키를 채우던 ls_town.js 는 이관되며 .disabled 됐으므로 값은 늘 0 — 명예 보드가
        // "CSV 첫 사람, 0점"을 항상 1위로 내보내고 있었다. 장부가 여기 하나뿐이니 읽기도 여기서 준다.
        //
        // Map.Entry 리스트를 그대로 넘기지 않는 이유: Rhino 쪽에서 다루기 번거롭고,
        // 스크립트가 필요한 건 "1위가 누구고 몇 점인가" 둘뿐이다.
        public String topContributorName(MinecraftServer server) {
            if (server == null) return "";
            var top = LSData.get(server).town().topContributors(1);
            return top.isEmpty() ? "" : top.get(0).getKey();
        }

        public int topContributorPoints(MinecraftServer server) {
            if (server == null) return 0;
            var top = LSData.get(server).town().topContributors(1);
            return top.isEmpty() ? 0 : top.get(0).getValue();
        }

        public boolean townFlag(MinecraftServer server, String key) {
            return server != null && LSData.get(server).town().flag(key);
        }

        // ── 성역 좌표 — 이제 모드가 유일한 소유자다 ──
        // 2026-07-25 이전에는 KubeJS persistentData(`ls_sanc_*`)와 LSData 가 **각자 한 벌씩**
        // 들고 있었다. 스크립트가 좌표를 정해도 모드는 몰랐고, 그걸 메꾸려고 ls_towneffect.js 가
        // 2초마다 밀어 넣었는데 그 코드가 예외로 죽어 성공 0건 — 귀환석이 몇 시간 동안 먹통이었다.
        // 두 벌이 있는 한 "한쪽만 갱신됨"은 시간 문제였다. 지금은 여기가 유일한 저장소고,
        // 스크립트는 아래 getter 로 읽는다(자기 사본을 두지 않는다).
        //
        // 명령(`/sanctuary`)은 아직 ls_siege.js 에 있다 — info 가 위협도·노드처럼 스크립트가
        // 소유한 값을 함께 출력하기 때문이다. 그쪽까지 이관되면 이 통로도 사라진다.
        public void setSanctuary(MinecraftServer server, int x, int y, int z) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.setSanctuary(x, y, z);
            data.dirty();
        }

        public boolean hasSanctuary(MinecraftServer server) {
            return server != null && LSData.get(server).hasSanctuary();
        }

        // 좌표는 셋을 따로 낸다. int[] 나 BlockPos 로 내면 스크립트 호출부(`c.x`)를 전부
        // 고쳐야 하는데, 그 대량 수정이야말로 이번에 없애려는 종류의 실수를 부른다.
        // 미지정일 때는 0 을 준다 — 호출 전에 hasSanctuary() 로 걸러야 한다.
        public int sanctuaryX(MinecraftServer server) { return sanc(server, 0); }
        public int sanctuaryY(MinecraftServer server) { return sanc(server, 1); }
        public int sanctuaryZ(MinecraftServer server) { return sanc(server, 2); }

        // ── 관문 진행도 (0~4) ──
        // 6개 스크립트가 각자 persistentData 에서 읽고 있었다. 성역 좌표와 같은 구조라
        // 같은 사고가 나기 전에 옮긴다. 상한은 LSData 가 걸므로 호출부는 신경 쓰지 않아도 된다.
        public int progress(MinecraftServer server) {
            return server == null ? 0 : LSData.get(server).progress();
        }

        public void setProgress(MinecraftServer server, int n) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.setProgress(n);
            data.dirty();
        }

        // ── 가호·유물·각성 (이관 3단계) ──
        // `fate_<이름>` · `relic_<이름>` · `star_<이름>` 이라는 - 문자열로 조립한 키 - 셋이
        // 세 스크립트에 흩어져 있었다. 그런 키는 오타가 나도 예외 없이 «없음/0» 을 돌려주고,
        // 쓰는 쪽만 옮기면 읽는 쪽이 조용히 기본값을 읽는다(명예 보드 사고와 같은 구조).
        // 이제 HeroData 가 유일한 소유자다. 상한(1~5)도 그쪽이 건다.
        //
        // ※ 성역 좌표 때와 같은 원칙: **스크립트는 자기 사본을 두지 않는다.** 두 벌이 있는 한
        //   "한쪽만 갱신됨"은 시간 문제였다.
        public String fate(MinecraftServer server, String name) {
            return server == null ? "" : LSData.get(server).hero().fate(name);
        }

        public void setFate(MinecraftServer server, String name, String key) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.hero().setFate(name, key);
            data.dirty();
        }

        // 이 가호를 이미 가진 사람 — 없으면 "". 같은 직업 둘을 막는 데 쓴다.
        public String fateOwner(MinecraftServer server, String key, String exceptName) {
            return server == null ? "" : LSData.get(server).hero().ownerOf(key, exceptName);
        }

        public boolean hasRelic(MinecraftServer server, String name) {
            return server != null && LSData.get(server).hero().hasRelic(name);
        }

        public void setHasRelic(MinecraftServer server, String name, boolean v) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.hero().setHasRelic(name, v);
            data.dirty();
        }

        // 0 = 아직 유물이 없다. 표시용으로 1 을 깔지 말 것 — 그 구분이 «유물은 받았는데
        // 각성이 0» 같은 어긋난 상태를 알아보는 유일한 근거다.
        public int star(MinecraftServer server, String name) {
            return server == null ? 0 : LSData.get(server).hero().star(name);
        }

        public void setStar(MinecraftServer server, String name, int n) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.hero().setStar(name, n);
            data.dirty();
        }

        // 제단은 사람이 아니라 - 가호 - 에 붙는다. 여덟 직업이 각자 자기 제단을 갖는다.
        public boolean hasAltar(MinecraftServer server, String fateKey) {
            return server != null && LSData.get(server).hero().hasAltar(fateKey);
        }

        public int altarX(MinecraftServer server, String f) { return altar(server, f, 0); }
        public int altarY(MinecraftServer server, String f) { return altar(server, f, 1); }
        public int altarZ(MinecraftServer server, String f) { return altar(server, f, 2); }

        public void setAltar(MinecraftServer server, String fateKey, int x, int y, int z) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.hero().setAltar(fateKey, x, y, z);
            data.dirty();
        }

        // 이관이 실제로 됐는지 한 줄로 본다 (/lsdata).
        public String heroSummary(MinecraftServer server) {
            return server == null ? "" : LSData.get(server).hero().summary();
        }

        // ── 공성 (이관 4단계) ──
        // 키 26개. 이름을 하나하나 메서드로 낸다 — 문자열 키 하나로 받는 통로
        // (`LS.siegeI(server, 'ls_threat')` 같은 것)를 두면 이관해서 얻는 게 없다.
        // 그 형태는 오타가 여전히 «예외 없이 0» 이고, 그게 애초에 여기로 옮기는 이유다.
        //
        // ※ **읽는 쪽 다섯이 다른 파일에 있다** — `ls_hope`(위협) · `ls_voice`(위협·성벽·진행·첫격퇴) ·
        //   `ls_stats`(최종장). 그쪽도 이 통로로 바꿨다. 쓰는 쪽만 옮기면 저 다섯이 조용히 0 을 읽는다.
        private SiegeData sg(MinecraftServer server) {
            return LSData.get(server).siege();
        }

        private void sgDirty(MinecraftServer server) {
            LSData.get(server).dirty();
        }

        public int threat(MinecraftServer server) {
            return server == null ? 0 : sg(server).threat();
        }

        public void setThreat(MinecraftServer server, int v) {
            if (server == null) return;
            sg(server).setThreat(v);
            sgDirty(server);
        }

        // 노드는 CSV 한 줄로 주고받는다(스크립트가 이미 그 형식이다).
        public String nodeCsv(MinecraftServer server) {
            return server == null ? "" : sg(server).nodeCsv();
        }

        public void setNodeCsv(MinecraftServer server, String csv) {
            if (server == null) return;
            sg(server).setNodeCsv(csv);
            sgDirty(server);
        }

        public int nodeCount(MinecraftServer server) {
            return server == null ? 0 : sg(server).nodeCount();
        }

        // ── 성벽 ──
        // `wallInit` 을 따로 내는 이유: hp 0 이 «미설정»인지 «부서짐»인지 스크립트가 가려야 한다.
        public boolean wallInit(MinecraftServer server) {
            return server != null && sg(server).wallInit();
        }

        public int wallHpRaw(MinecraftServer server) {
            return server == null ? 0 : sg(server).wallHpRaw();
        }

        // 천장은 호출부가 준다 — 최대 HP 가 `3000 + 방벽Lv×1000` 이고 3000 은 스크립트 상수다.
        public void setWallHp(MinecraftServer server, int hp, int max) {
            if (server == null) return;
            sg(server).setWallHp(hp, max);
            sgDirty(server);
        }

        public int wallRadius(MinecraftServer server) {
            return server == null ? 0 : sg(server).wallRadius();
        }

        public void setWallRadius(MinecraftServer server, int r) {
            if (server == null) return;
            sg(server).setWallRadius(r);
            sgDirty(server);
        }

        public int wallWarn(MinecraftServer server) {
            return server == null ? 0 : sg(server).wallWarn();
        }

        public void setWallWarn(MinecraftServer server, int n) {
            if (server == null) return;
            sg(server).setWallWarn(n);
            sgDirty(server);
        }

        public boolean wallLastStand(MinecraftServer server) {
            return server != null && sg(server).wallLastStand();
        }

        public void setWallLastStand(MinecraftServer server, boolean v) {
            if (server == null) return;
            sg(server).setWallLastStand(v);
            sgDirty(server);
        }

        public int wallStandUntil(MinecraftServer server) {
            return server == null ? 0 : sg(server).wallStandUntil();
        }

        public void setWallStandUntil(MinecraftServer server, int t) {
            if (server == null) return;
            sg(server).setWallStandUntil(t);
            sgDirty(server);
        }

        // ── 최종장 ──
        public int finale(MinecraftServer server) {
            return server == null ? 0 : sg(server).finale();
        }

        public void setFinale(MinecraftServer server, int v) {
            if (server == null) return;
            sg(server).setFinale(v);
            sgDirty(server);
        }

        public boolean finaleArmed(MinecraftServer server) {
            return server != null && sg(server).finaleArmed();
        }

        public void setFinaleArmed(MinecraftServer server, boolean v) {
            if (server == null) return;
            sg(server).setFinaleArmed(v);
            sgDirty(server);
        }

        public boolean finaleNightOk(MinecraftServer server) {
            return server != null && sg(server).finaleNightOk();
        }

        public void setFinaleNightOk(MinecraftServer server, boolean v) {
            if (server == null) return;
            sg(server).setFinaleNightOk(v);
            sgDirty(server);
        }

        public boolean trueSpawned(MinecraftServer server) {
            return server != null && sg(server).trueSpawned();
        }

        public void setTrueSpawned(MinecraftServer server, boolean v) {
            if (server == null) return;
            sg(server).setTrueSpawned(v);
            sgDirty(server);
        }

        public boolean trueFormOff(MinecraftServer server) {
            return server != null && sg(server).trueFormOff();
        }

        public void setTrueFormOff(MinecraftServer server, boolean v) {
            if (server == null) return;
            sg(server).setTrueFormOff(v);
            sgDirty(server);
        }

        public boolean dawnbreak(MinecraftServer server) {
            return server != null && sg(server).dawnbreak();
        }

        public void setDawnbreak(MinecraftServer server, boolean v) {
            if (server == null) return;
            sg(server).setDawnbreak(v);
            sgDirty(server);
        }

        // ── 진행 중인 공세 ──
        public boolean siegeActive(MinecraftServer server) {
            return server != null && sg(server).siegeActive();
        }

        public void setSiegeActive(MinecraftServer server, boolean v) {
            if (server == null) return;
            sg(server).setSiegeActive(v);
            sgDirty(server);
        }

        public boolean siegeGrand(MinecraftServer server) {
            return server != null && sg(server).siegeGrand();
        }

        public void setSiegeGrand(MinecraftServer server, boolean v) {
            if (server == null) return;
            sg(server).setSiegeGrand(v);
            sgDirty(server);
        }

        public int siegeWaves(MinecraftServer server) {
            return server == null ? 0 : sg(server).siegeWaves();
        }

        public void setSiegeWaves(MinecraftServer server, int n) {
            if (server == null) return;
            sg(server).setSiegeWaves(n);
            sgDirty(server);
        }

        public int siegeWaveNo(MinecraftServer server) {
            return server == null ? 0 : sg(server).siegeWaveNo();
        }

        public void setSiegeWaveNo(MinecraftServer server, int n) {
            if (server == null) return;
            sg(server).setSiegeWaveNo(n);
            sgDirty(server);
        }

        public int siegeRemaining(MinecraftServer server) {
            return server == null ? 0 : sg(server).siegeRemaining();
        }

        public void setSiegeRemaining(MinecraftServer server, int n) {
            if (server == null) return;
            sg(server).setSiegeRemaining(n);
            sgDirty(server);
        }

        public int siegeReward(MinecraftServer server) {
            return server == null ? 0 : sg(server).siegeReward();
        }

        public void setSiegeReward(MinecraftServer server, int n) {
            if (server == null) return;
            sg(server).setSiegeReward(n);
            sgDirty(server);
        }

        public int siegeAngle(MinecraftServer server) {
            return server == null ? 0 : sg(server).siegeAngle();
        }

        public void setSiegeAngle(MinecraftServer server, int deg) {
            if (server == null) return;
            sg(server).setSiegeAngle(deg);
            sgDirty(server);
        }

        // 공세를 끝낼 때 되돌려야 하는 네 값을 한 번에 — 스크립트에 이 네 줄이 두 군데 있었다.
        public void endSiege(MinecraftServer server) {
            if (server == null) return;
            sg(server).endSiege();
            sgDirty(server);
        }

        // ── 하루 · 예고 ──
        public int dreadStep(MinecraftServer server) {
            return server == null ? 0 : sg(server).dreadStep();
        }

        public void setDreadStep(MinecraftServer server, int n) {
            if (server == null) return;
            sg(server).setDreadStep(n);
            sgDirty(server);
        }

        public int annTier(MinecraftServer server) {
            return server == null ? 0 : sg(server).annTier();
        }

        public void setAnnTier(MinecraftServer server, int n) {
            if (server == null) return;
            sg(server).setAnnTier(n);
            sgDirty(server);
        }

        public int siegeDay(MinecraftServer server) {
            return server == null ? 0 : sg(server).day();
        }

        public void setSiegeDay(MinecraftServer server, int n) {
            if (server == null) return;
            sg(server).setDay(n);
            sgDirty(server);
        }

        public boolean wasNight(MinecraftServer server) {
            return server != null && sg(server).wasNight();
        }

        public void setWasNight(MinecraftServer server, boolean v) {
            if (server == null) return;
            sg(server).setWasNight(v);
            sgDirty(server);
        }

        // ── 이력 ──
        public boolean firstSiegeDone(MinecraftServer server) {
            return server != null && sg(server).firstSiegeDone();
        }

        public void setFirstSiegeDone(MinecraftServer server, boolean v) {
            if (server == null) return;
            sg(server).setFirstSiegeDone(v);
            sgDirty(server);
        }

        public String siegeSummary(MinecraftServer server) {
            return server == null ? "" : sg(server).summary();
        }

        private int altar(MinecraftServer server, String fateKey, int axis) {
            if (server == null) return 0;
            var h = LSData.get(server).hero();
            if (!h.hasAltar(fateKey)) return 0;
            var p = h.altar(fateKey);
            return axis == 0 ? p.getX() : axis == 1 ? p.getY() : p.getZ();
        }

        private int sanc(MinecraftServer server, int axis) {
            if (server == null) return 0;
            LSData data = LSData.get(server);
            if (!data.hasSanctuary()) return 0;
            var p = data.sanctuary();
            return axis == 0 ? p.getX() : axis == 1 ? p.getY() : p.getZ();
        }

        // ── 몹이 「몬스터」인가 (2026-07-31 유저 결정: DECISIONS 2절 C안) ──
        // `ls_mobscale.js` 가 여태 «공격력 속성이 있으면 적대» 로 판별하고 있었다.
        // 원래는 MobCategory 를 보려 했는데, KubeJS 의 `e.getType()` 은 EntityType 이 아니라
        // **id 문자열**이라 `.getCategory()` 가 처음부터 예외만 던졌다 — 그래서 판별이
        // 계속 예비 경로로 떨어졌고, 결과적으로 철골렘·눈사람·늑대·벌까지 세지고 있었다.
        //
        // 자바에서는 EntityType 을 그대로 들고 있으니 한 줄이다. 스크립트가 못 하던 걸
        // 모드가 하는 전형적인 자리라 다리를 놓는다.
        //
        // ※ 이 판정은 **늑대·북극곰도 뺀다.** 「실제로 덤비는데 안 세지는」 경우가 생긴다는 걸
        //   알고 고른 값이다(DECISIONS 2절에 A/B/C 를 비교해 뒀다). 규칙이 한 문장으로
        //   설명되는 쪽을 택한 것이다 — «몬스터만 세진다».
        // ⚠️ 인자를 Object 로 받는 이유 — 처음엔 `Entity` 로 받았는데, 그러면
        // **「엔티티가 아니다」와 「몬스터가 아니다」가 둘 다 false 로 뭉개진다.**
        // 실제로 그 상태로 넣었더니 좀비가 스케일링을 못 받았고, 예외도 로그도 안 나서
        // 원인을 못 찾을 뻔했다(Rhino 가 변환 못 하는 인자를 null 로 넘긴다).
        // 여기서는 **못 알아본 타입이면 던진다** — 호출부의 catch 가 경고를 찍고 예비 경로로
        // 내려가므로, 조용히 틀리는 대신 시끄럽게 틀린다.
        public boolean isMonster(Object entity) {
            if (entity instanceof net.minecraft.world.entity.Entity e) {
                return e.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER;
            }
            throw new IllegalArgumentException(
                "LS.isMonster: 엔티티가 아니다 — " + (entity == null ? "null" : entity.getClass().getName()));
        }

        // ── 그 몹 종류의 «공장 출고» 속성값 (2026-07-31) ──
        // 몹 스케일링이 재시작마다 복리로 붙던 걸 막으려고 만들었다. 원인은 두 겹이었다:
        //   ① `EntityEvents.spawned` 는 디스크에서 다시 로드될 때도 온다
        //   ② 이 모드팩은 로드 시 속성 모디파이어를 base 에 구워 넣는다
        // 그래서 «태그로 표식» 도 «이름 붙은 모디파이어» 도 안 통했다. 전자는 태그가 아직
        // 안 붙은 시점에 이벤트가 오고, 후자는 구워지면서 다음 로드의 새 base 가 된다.
        // 실측: 좀비 하나가 재시작 한 번에 20 → 34.56 (20×1.2³) 이 됐다.
        //
        // **그래서 현재값을 아예 안 읽는다.** 엔티티 «종류»의 기본값에서 곱하면 몇 번을
        // 다시 돌려도 결과가 하나다 — 탐지가 필요 없어진다.
        // 없는 속성이면 -1 (0 이 아니다 — 0 은 «값이 0» 과 구분이 안 된다).
        public double defaultAttrBase(Object entity, String attrId) {
            if (!(entity instanceof net.minecraft.world.entity.LivingEntity le)) return -1;
            try {
                var loc = net.minecraft.resources.ResourceLocation.parse(attrId);
                var holder = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(loc);
                if (holder.isEmpty()) return -1;
                @SuppressWarnings("unchecked")
                var type = (net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity>) le.getType();
                var supplier = net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier(type);
                if (!supplier.hasAttribute(holder.get())) return -1;
                return supplier.getBaseValue(holder.get());
            } catch (Exception e) {
                return -1;
            }
        }

        // ── 보스 난이도 라이브 오버라이드 (이관 5단계) ──
        // 여기 있는 건 «/bossdiff 로 건 값» 뿐이다. 영구 기본값(ls_config.js)은 스크립트가 계속
        // 소유한다 — 그래서 아래 getter 는 전부 **0 = 오버라이드 없음** 을 돌려주고,
        // 파일 값으로 내려가는 판단은 스크립트(bdEffHp 등)에 남는다. 2층 구조를 안 바꾼 것이다.
        public int bossGlobalHpOverride(MinecraftServer server) {
            return server == null ? 0 : LSData.get(server).bossDiff().globalHp();
        }

        public int bossGlobalDmgOverride(MinecraftServer server) {
            return server == null ? 0 : LSData.get(server).bossDiff().globalDmg();
        }

        public void setBossGlobal(MinecraftServer server, int hp, int dmg) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.bossDiff().setGlobal(hp, dmg);
            data.dirty();
        }

        public int bossHpOverride(MinecraftServer server, String id) {
            return server == null ? 0 : LSData.get(server).bossDiff().hp(id);
        }

        public int bossDmgOverride(MinecraftServer server, String id) {
            return server == null ? 0 : LSData.get(server).bossDiff().dmg(id);
        }

        public int bossAbsOverride(MinecraftServer server, String id) {
            return server == null ? 0 : LSData.get(server).bossDiff().abs(id);
        }

        // hp·dmg 를 따로 두지 않고 한 번에 받는 이유: `/bossdiff set` 이 늘 둘을 같이 준다.
        // 나눠 두면 한쪽만 부른 호출부가 생기고, 그게 «공격력만 안 먹는» 버그가 된다.
        public void setBossDiff(MinecraftServer server, String id, int hp, int dmg) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.bossDiff().setHp(id, hp);
            data.bossDiff().setDmg(id, dmg);
            data.dirty();
        }

        public void setBossAbs(MinecraftServer server, String id, int abs) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.bossDiff().setAbs(id, abs);
            data.dirty();
        }

        // 오버라이드가 걸린 보스만 CSV 로. 스크립트가 33종 전체를 훑지 않아도 되게 —
        // 훑는 쪽이 목록을 갖고 있으면 모드팩에서 보스가 늘 때 그 목록이 조용히 낡는다.
        public String bossOverriddenCsv(MinecraftServer server) {
            return server == null ? "" : String.join(",", LSData.get(server).bossDiff().overridden());
        }

        public String bossDiffSummary(MinecraftServer server) {
            return server == null ? "" : LSData.get(server).bossDiff().summary();
        }

        public void resetBossDiff(MinecraftServer server) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.bossDiff().reset();
            data.dirty();
        }

        // ── 칭호 (이관 5단계) ──
        // 카탈로그(12종의 이름·색)는 `ls_title.js` 에 남는다. 여기는 장부만 갖는다.
        // 목록은 CSV 로 주고받는다 — 4단계 노드와 같은 이유로, 이관과 형식 변경을 같이 하지 않는다.
        public String titlesOwnedCsv(MinecraftServer server, String name) {
            return server == null ? "" : String.join(",", LSData.get(server).titles().owned(name));
        }

        public String activeTitle(MinecraftServer server, String name) {
            return server == null ? "" : LSData.get(server).titles().active(name);
        }

        // **새로 받았을 때만 true.** 옛 코드는 부여와 방송이 한 덩어리라 조용히 주는 경로를
        // 만들 수 없었다. 연출은 호출부가 이 반환값으로 가른다.
        public boolean grantTitle(MinecraftServer server, String name, String key) {
            if (server == null) return false;
            LSData data = LSData.get(server);
            if (!data.titles().grant(name, key)) return false;
            data.dirty();
            return true;
        }

        // 보유하지 않은 칭호면 false — 불변식은 TitleData 가 건다.
        // 빈 문자열은 「표시 안 함」이고 항상 성공한다.
        public boolean setActiveTitle(MinecraftServer server, String name, String key) {
            if (server == null) return false;
            LSData data = LSData.get(server);
            if (!data.titles().setActive(name, key)) return false;
            data.dirty();
            return true;
        }

        // 착용자 전원 — 재시작 뒤 팀 prefix 를 한 번에 되살리려면 목록이 필요하다.
        // 접속 이벤트에서만 복구하면 «접속 안 한 사람의 이름표가 남들 눈에 빈 채로» 남는다.
        public String titleWearersCsv(MinecraftServer server) {
            return server == null ? "" : String.join(",", LSData.get(server).titles().namesWithActive());
        }

        public String titleSummary(MinecraftServer server) {
            return server == null ? "" : LSData.get(server).titles().summary();
        }

        // ── 현상금 (이관 5단계) ──
        // 후보 목록(사냥 8·납품 8·정예 6)과 보상 배율은 `ls_bounty.js` 에 남는다.
        // 여기 오는 건 **굴린 결과**뿐이다. 필드를 하나씩 주고받는 이유는 옛 저장이
        // `'hunt|minecraft:zombie|좀비|25|60'` 이었기 때문이다 — 이름에 `|` 하나만 들어가면
        // 그 현상금이 조용히 사라졌다(BountyData 머리말).
        public int bountyCycle(MinecraftServer server) {
            return server == null ? 0 : LSData.get(server).bounty().cycle();
        }

        public void setBountyCycle(MinecraftServer server, int n) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.bounty().setCycle(n);
            data.dirty();
        }

        // 새로 건다 — 진행도 초기화가 **여기 안에 들어 있다.** 옛 코드는 굴리기와 초기화가
        // 다른 루프라, 한쪽만 돌면 새 현상금이 「이미 완료」로 시작할 수 있었다.
        public void postBounty(MinecraftServer server, int slot, String kind, String target,
                               String name, int need, int reward) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.bounty().post(slot, kind, target, name, need, reward);
            data.dirty();
        }

        public boolean bountyPosted(MinecraftServer server, int slot) {
            return server != null && LSData.get(server).bounty().posted(slot);
        }

        public String bountyKind(MinecraftServer server, int slot) {
            return server == null ? "" : LSData.get(server).bounty().kind(slot);
        }

        public String bountyTarget(MinecraftServer server, int slot) {
            return server == null ? "" : LSData.get(server).bounty().target(slot);
        }

        public String bountyName(MinecraftServer server, int slot) {
            return server == null ? "" : LSData.get(server).bounty().name(slot);
        }

        public int bountyNeed(MinecraftServer server, int slot) {
            return server == null ? 0 : LSData.get(server).bounty().need(slot);
        }

        public int bountyReward(MinecraftServer server, int slot) {
            return server == null ? 0 : LSData.get(server).bounty().reward(slot);
        }

        public int bountyHave(MinecraftServer server, int slot) {
            return server == null ? 0 : LSData.get(server).bounty().have(slot);
        }

        public boolean bountyDone(MinecraftServer server, int slot) {
            return server != null && LSData.get(server).bounty().done(slot);
        }

        // 더한 뒤의 값을 돌려준다. 읽고·더하고·쓰는 세 줄을 한 줄로 접은 것이다 —
        // 킬 추적과 납품이 같은 칸을 만지므로 그 사이가 벌어지면 한쪽이 덮인다.
        public int addBountyProgress(MinecraftServer server, int slot, int n) {
            if (server == null) return 0;
            LSData data = LSData.get(server);
            int now = data.bounty().addProgress(slot, n);
            data.dirty();
            return now;
        }

        // 이미 완료였으면 false — 보상이 두 번 나가는 걸 막는 자리가 여기 하나다.
        public boolean completeBounty(MinecraftServer server, int slot) {
            if (server == null) return false;
            LSData data = LSData.get(server);
            if (!data.bounty().complete(slot)) return false;
            data.dirty();
            return true;
        }

        public String bountySummary(MinecraftServer server) {
            return server == null ? "" : LSData.get(server).bounty().summary();
        }

        // ── 정화 봉화 (이관 5단계) ──
        // **`beaconCount` 를 세 파일이 부른다** — `ls_beacon`(목록) · `ls_siege`(위협 하한) ·
        // `ls_hope`(희망 게이지). 옛날엔 뒤의 둘이 `pb_names` CSV 를 각자 읽어 쉼표를 셌다.
        // 쓰는 쪽만 옮겼으면 그 둘이 조용히 0 을 세고, **위협 하한 완화가 사라지고 희망 게이지가
        // 봉화를 못 보게** 된다. 오류는 안 난다. 그래서 세는 곳을 여기 하나로 만들었다.
        public int beaconCount(MinecraftServer server) {
            return server == null ? 0 : LSData.get(server).beacons().count();
        }

        public String beaconNamesCsv(MinecraftServer server) {
            return server == null ? "" : String.join(",", LSData.get(server).beacons().names());
        }

        public boolean hasBeacon(MinecraftServer server, String name) {
            return server != null && LSData.get(server).beacons().has(name);
        }

        public int beaconX(MinecraftServer server, String name) { return beaconPos(server, name, 0); }
        public int beaconY(MinecraftServer server, String name) { return beaconPos(server, name, 1); }
        public int beaconZ(MinecraftServer server, String name) { return beaconPos(server, name, 2); }

        private int beaconPos(MinecraftServer server, String name, int axis) {
            if (server == null) return 0;
            var p = LSData.get(server).beacons().pos(name);
            return axis == 0 ? p.getX() : axis == 1 ? p.getY() : p.getZ();
        }

        // 이미 있는 이름이면 false — 덮어쓰지 않는다.
        public boolean addBeacon(MinecraftServer server, String name, int x, int y, int z) {
            if (server == null) return false;
            LSData data = LSData.get(server);
            if (!data.beacons().add(name, x, y, z)) return false;
            data.dirty();
            return true;
        }

        // 이름과 좌표가 한 항목이라 같이 사라진다. 옛 코드는 CSV 에서 이름만 빼고
        // `pb_<이름>_x/y/z` 를 남겼다 — 지워진 봉화의 좌표가 세이브에 영원히 쌓였다.
        public boolean removeBeacon(MinecraftServer server, String name) {
            if (server == null) return false;
            LSData data = LSData.get(server);
            if (!data.beacons().remove(name)) return false;
            data.dirty();
            return true;
        }

        // 가장 가까운 봉화까지의 수평 거리 — 없으면 -1. **판정(96m)은 스크립트가 한다.**
        // 최소 거리는 `/reload` 로 만지는 튜닝 값이라 그쪽이 맞는 자리다.
        public double nearestBeaconDistance(MinecraftServer server, int x, int z) {
            return server == null ? -1 : LSData.get(server).beacons().nearestDistance(x, z);
        }

        public String nearestBeaconName(MinecraftServer server, int x, int z) {
            return server == null ? "" : LSData.get(server).beacons().nearestName(x, z);
        }

        public String beaconSummary(MinecraftServer server) {
            return server == null ? "" : LSData.get(server).beacons().summary();
        }

        // ── 별똥말 경마 (이관 5단계) ──
        // 말 이름·배당·목표 거리는 `ls_casino.js` 에 남는다. 여기 오는 건 진행 상태뿐이다.
        // 주사위 결투는 아예 안 온다 — 60초짜리 메모리 상태라 저장할 이유가 없다.
        public int racePhase(MinecraftServer server) {
            return server == null ? 0 : LSData.get(server).casino().phase();
        }

        public int raceTimer(MinecraftServer server) {
            return server == null ? 0 : LSData.get(server).casino().timer();
        }

        public void setRacePhase(MinecraftServer server, int phase) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.casino().setPhase(phase);
            data.dirty();
        }

        public void setRaceTimer(MinecraftServer server, int t) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.casino().setTimer(t);
            data.dirty();
        }

        // 개장 — 단계·타이머·말 위치·베팅을 한 번에 세운다. 옛 코드는 다섯 줄이 나란히 있었고,
        // 하나만 빠지면 지난 경기 위치에서 출발하는 경마가 됐다.
        public void openRace(MinecraftServer server, int seconds) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.casino().openRace(seconds);
            data.dirty();
        }

        // 폐장 — 정산은 호출부가 먼저 끝내고 부른다.
        public void closeRace(MinecraftServer server) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.casino().closeRace();
            data.dirty();
        }

        public int horsePos(MinecraftServer server, int horse) {
            return server == null ? 0 : LSData.get(server).casino().pos(horse);
        }

        public int advanceHorse(MinecraftServer server, int horse, int n) {
            if (server == null) return 0;
            LSData data = LSData.get(server);
            int now = data.casino().advance(horse, n);
            data.dirty();
            return now;
        }

        // 이미 건 사람이면 false — 칩을 두 번 걷지 않게 하는 자리가 여기 하나다.
        public boolean placeRaceBet(MinecraftServer server, String name, int horse, int amount) {
            if (server == null) return false;
            LSData data = LSData.get(server);
            if (!data.casino().placeBet(name, horse, amount)) return false;
            data.dirty();
            return true;
        }

        public String raceBettersCsv(MinecraftServer server) {
            return server == null ? "" : String.join(",", LSData.get(server).casino().betters());
        }

        public int raceBetHorse(MinecraftServer server, String name) {
            return server == null ? 0 : LSData.get(server).casino().betHorse(name);
        }

        public int raceBetAmount(MinecraftServer server, String name) {
            return server == null ? 0 : LSData.get(server).casino().betAmount(name);
        }

        public int raceBetCount(MinecraftServer server) {
            return server == null ? 0 : LSData.get(server).casino().betCount();
        }

        public String casinoSummary(MinecraftServer server) {
            return server == null ? "" : LSData.get(server).casino().summary();
        }

        // ── 생존자 구출 (이관 5단계) ──
        // 생존자 6명의 명부(이름·직업·간수)는 `ls_rescue.js` 에 남는다.
        // **인구는 저장하지 않는다** — 구출 명부의 크기다. 옛날엔 `town_pop` 이 따로 있어서
        // 이미 구출한 사람에게 `/rescue grant` 를 한 번 더 쓰면 인구만 늘었고,
        // 그러면 매일 들어오는 수입(인구×8)이 영구히 부풀었다.
        public int population(MinecraftServer server) {
            return server == null ? 0 : LSData.get(server).rescue().population();
        }

        public boolean isRescued(MinecraftServer server, String key) {
            return server != null && LSData.get(server).rescue().isRescued(key);
        }

        // 이미 구출한 생존자면 false — 인구가 두 번 늘지 않는 유일한 관문이다.
        public boolean settleSurvivor(MinecraftServer server, String key) {
            if (server == null) return false;
            LSData data = LSData.get(server);
            if (!data.rescue().settle(key)) return false;
            data.dirty();
            return true;
        }

        public boolean rescueActive(MinecraftServer server) {
            return server != null && LSData.get(server).rescue().active();
        }

        public boolean rescueBuilt(MinecraftServer server) {
            return server != null && LSData.get(server).rescue().built();
        }

        public int rescueIdx(MinecraftServer server) {
            return server == null ? 0 : LSData.get(server).rescue().idx();
        }

        public int rescueX(MinecraftServer server) { return server == null ? 0 : LSData.get(server).rescue().x(); }
        public int rescueY(MinecraftServer server) { return server == null ? 0 : LSData.get(server).rescue().y(); }
        public int rescueZ(MinecraftServer server) { return server == null ? 0 : LSData.get(server).rescue().z(); }

        public int rescueGuards(MinecraftServer server) {
            return server == null ? 0 : LSData.get(server).rescue().guards();
        }

        // 정찰 개시 — 다섯 값이 한 번에 선다.
        public void beginScout(MinecraftServer server, int index, int x, int z) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.rescue().beginScout(index, x, z);
            data.dirty();
        }

        // 감금지 노출 — 이미 지어졌으면 false. **두 번 짓는 걸 막는 자리가 여기 하나다.**
        // 옛 코드는 «지었다» 표식을 세우는 것과 실제로 짓는 것이 따로였다.
        public boolean revealCamp(MinecraftServer server, int y, int guardCount) {
            if (server == null) return false;
            LSData data = LSData.get(server);
            if (!data.rescue().reveal(y, guardCount)) return false;
            data.dirty();
            return true;
        }

        // 남은 간수를 돌려준다. 읽고·빼고·쓰는 세 줄을 접었다 —
        // 간수 둘이 같은 틱에 죽으면 옛 방식은 한쪽이 덮였다.
        public int killGuard(MinecraftServer server) {
            if (server == null) return 0;
            LSData data = LSData.get(server);
            int left = data.rescue().killGuard();
            data.dirty();
            return left;
        }

        // 성공·실패·취소 공통. 끝내는 방법이 하나뿐이면 어긋날 자리가 없다.
        public void endRescue(MinecraftServer server) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.rescue().endRun();
            data.dirty();
        }

        public int rescueDay(MinecraftServer server) {
            return server == null ? 0 : LSData.get(server).rescue().day();
        }

        public void setRescueDay(MinecraftServer server, int d) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.rescue().setDay(d);
            data.dirty();
        }

        public String rescueSummary(MinecraftServer server) {
            return server == null ? "" : LSData.get(server).rescue().summary();
        }

        // ── 하늘 (이관 6단계에서 합류) ──
        // 공성이 쓰고 `ls_daynight`(시간 진행) · `ls_voice`(밤 앰비언트 억제)가 읽는다.
        // 공성 자체는 4단계에 옮겼는데 이 둘만 persistentData 에 남아 있었다 — 마지막 경계 넘김.
        public boolean timeLocked(MinecraftServer server) {
            return server != null && LSData.get(server).siege().timeLocked();
        }

        public void setTimeLocked(MinecraftServer server, boolean v) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.siege().setTimeLocked(v);
            data.dirty();
        }

        public int nightRatePct(MinecraftServer server) {
            return server == null ? 0 : LSData.get(server).siege().nightRatePct();
        }

        public void setNightRatePct(MinecraftServer server, int pct) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.siege().setNightRatePct(pct);
            data.dirty();
        }

        // ── 안내자의 목소리 (이관 6단계 = 마지막) ──
        // 대사 10종의 문장·채널·쿨다운·예산 여부는 `ls_voice.js` 에 남는다.
        // 여기가 아는 건 «무엇을 언제 말했나» 뿐이다.
        //
        // 예산 상한(VC_BUDGET_MAX)도 스크립트가 갖는다 — 그래서 `voiceBudget` 은 아직 정해지지
        // 않았으면 **-1** 을 준다. 스크립트가 그걸 보고 상한으로 채운다.
        public int voiceBudget(MinecraftServer server) {
            return server == null ? 0 : LSData.get(server).voice().budget();
        }

        public void setVoiceBudget(MinecraftServer server, int n, int max) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.voice().setBudget(n, max);
            data.dirty();
        }

        // 남았으면 하나 쓰고 true. 읽고·빼고·쓰는 세 걸음을 한 번으로 접었다.
        public boolean takeVoiceBudget(MinecraftServer server) {
            if (server == null) return false;
            LSData data = LSData.get(server);
            if (!data.voice().takeBudget()) return false;
            data.dirty();
            return true;
        }

        public int voiceBudgetDay(MinecraftServer server) {
            return server == null ? 0 : LSData.get(server).voice().budgetDay();
        }

        public void setVoiceBudgetDay(MinecraftServer server, int d) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.voice().setBudgetDay(d);
            data.dirty();
        }

        public boolean voiceMuted(MinecraftServer server) {
            return server != null && LSData.get(server).voice().mute();
        }

        public void setVoiceMuted(MinecraftServer server, boolean v) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.voice().setMute(v);
            data.dirty();
        }

        public boolean voiceOnce(MinecraftServer server, String id) {
            return server != null && LSData.get(server).voice().once(id);
        }

        public void setVoiceOnce(MinecraftServer server, String id, boolean v) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.voice().setOnce(id, v);
            data.dirty();
        }

        public int voiceCd(MinecraftServer server, String id) {
            return server == null ? 0 : LSData.get(server).voice().cd(id);
        }

        public void setVoiceCd(MinecraftServer server, String id, int ticks) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.voice().setCd(id, ticks);
            data.dirty();
        }

        public boolean voicePend(MinecraftServer server, String id) {
            return server != null && LSData.get(server).voice().pend(id);
        }

        public void setVoicePend(MinecraftServer server, String id, boolean v) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.voice().setPend(id, v);
            data.dirty();
        }

        // 지금 인덱스를 주고 **동시에** 다음으로 넘긴다. 옛 코드는 읽기와 쓰기가 두 줄이라,
        // 그 사이에 다른 발화가 끼면 같은 대사가 두 번 연달아 나왔다.
        public int nextVoiceRot(MinecraftServer server, String id, int size) {
            if (server == null) return 0;
            LSData data = LSData.get(server);
            int i = data.voice().nextRot(id, size);
            data.dirty();
            return i;
        }

        // 되돌린 1회성 대사 수를 준다. **회전 인덱스는 안 지운다** — 「어디까지 읽었나」라
        // 초기화 대상이 아니다(지우면 리셋할 때마다 같은 첫 줄이 나온다).
        public int resetVoiceLines(MinecraftServer server) {
            if (server == null) return 0;
            LSData data = LSData.get(server);
            int n = data.voice().resetLines();
            data.dirty();
            return n;
        }

        // 감시 스냅샷 — **사본이 아니라 진도표다.**
        public int voiceSeenRf(MinecraftServer server)   { return server == null ? 0 : LSData.get(server).voice().seenRf(); }
        public int voiceSeenBand(MinecraftServer server) { return server == null ? 0 : LSData.get(server).voice().seenBand(); }
        public int voiceSeenWall(MinecraftServer server) { return server == null ? 0 : LSData.get(server).voice().seenWall(); }
        public boolean voiceSeenNight(MinecraftServer server) { return server != null && LSData.get(server).voice().seenNight(); }

        public void setVoiceSeenRf(MinecraftServer server, int n) {
            if (server == null) return;
            LSData d = LSData.get(server); d.voice().setSeenRf(n); d.dirty();
        }

        public void setVoiceSeenBand(MinecraftServer server, int n) {
            if (server == null) return;
            LSData d = LSData.get(server); d.voice().setSeenBand(n); d.dirty();
        }

        public void setVoiceSeenWall(MinecraftServer server, int n) {
            if (server == null) return;
            LSData d = LSData.get(server); d.voice().setSeenWall(n); d.dirty();
        }

        public void setVoiceSeenNight(MinecraftServer server, boolean v) {
            if (server == null) return;
            LSData d = LSData.get(server); d.voice().setSeenNight(v); d.dirty();
        }

        // **처음 보는 사람이면 true 를 주면서 동시에 표시한다.** 확인과 표시가 두 줄이면
        // 그 사이에 예외가 나서 첫 접속 인사가 접속할 때마다 반복될 수 있다.
        public boolean markVoiceKnown(MinecraftServer server, String name) {
            if (server == null) return false;
            LSData data = LSData.get(server);
            if (!data.voice().markKnown(name)) return false;
            data.dirty();
            return true;
        }

        public int voiceLastDay(MinecraftServer server, String name) {
            return server == null ? 0 : LSData.get(server).voice().lastDay(name);
        }

        public int voiceLastRf(MinecraftServer server, String name) {
            return server == null ? 0 : LSData.get(server).voice().lastRf(name);
        }

        public void stampVoiceSeen(MinecraftServer server, String name, int day, int rf) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.voice().stamp(name, day, rf);
            data.dirty();
        }

        public String voiceSummary(MinecraftServer server) {
            return server == null ? "" : LSData.get(server).voice().summary();
        }

        // 화면을 열어둔 사람에게 갱신을 밀어준다 (스크립트가 금고를 바꾼 직후 등)
        // ── 부활 규칙 ──
        // 리스폰 직후 ls_revive.js 가 부른다. 무적 창과 「별빛 쇠약」의 피해 감소는
        // 자바가 판정한다 — 속성 모디파이어로는 원거리·스킬 피해를 못 잡는다(ReviveRules 주석).
        public void reviveRule(ServerPlayer player) {
            com.laststardust.relics.ReviveRules.begin(player);
        }

        public void syncTown(ServerPlayer player) {
            if (player != null) TownGui.sync(player);
        }
    }
}
