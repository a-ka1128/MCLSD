package com.laststardust.relics.client;

import com.laststardust.relics.LSRelics;
import com.laststardust.relics.item.RelicActions;
import com.laststardust.relics.network.RelicInputPayload;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 유물 스킬 키 (R / V / C / X). 전부 게임 설정 › 조작 › Last Stardust 에서 재지정 가능.
//
// ── 왜 마우스에서 뺐나 ──
// 예전 배치(웅크림+좌클릭 = 궁극, 웅크림+우클릭 = 추가, 쉬프트 두 번 = 이동)는 셋 다
// 바닐라 조작과 부딪혔다. 특히 웅크린 채 상자를 열면 웅크림이 블록 상호작용을 막아
// 상자는 안 열리고 스킬이 나갔고, 절벽에서 웅크려 때리면 60초 궁극기가 헛발로 날아갔다.
//
// ── 키 선택 근거 ──
// 이 모드팩에서 이미 잡힌 키를 피했다: M(Xaero 월드맵) · Y(Xaero 미니맵) · G(Curios)
// · F(손 교체) · Q(버리기) · E(인벤) · N(마을 관리, 이 모드).
// R·V·C·X 는 전부 비어 있고 왼손이 WASD 에서 떨어지지 않는 자리다.
@EventBusSubscriber(modid = LSRelics.MODID, value = Dist.CLIENT)
public final class RelicKeybinds {
    private RelicKeybinds() {}

    public static final KeyMapping BASIC = mk("basic", InputConstants.KEY_R);
    public static final KeyMapping EXTRA = mk("extra", InputConstants.KEY_C);
    public static final KeyMapping DODGE = mk("dodge", InputConstants.KEY_V);
    public static final KeyMapping ULTIMATE = mk("ultimate", InputConstants.KEY_X);

    private static KeyMapping mk(String name, int key) {
        return new KeyMapping("key." + LSRelics.MODID + "." + name,
            InputConstants.Type.KEYSYM, key, TownKeybind.CATEGORY);
    }

    // 등록(RegisterKeyMappingsEvent)은 MOD 버스라 LSRelicsClient 가 받는다.
    public static KeyMapping[] all() {
        return new KeyMapping[] { BASIC, DODGE, EXTRA, ULTIMATE };
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        // 화면이 떠 있으면 키 입력은 화면 몫이다. 큐는 비워서 창을 닫자마자 스킬이 튀어나가지 않게 한다.
        if (player == null || mc.screen != null) {
            drain();
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof RelicActions)) {
            drain();
            return;
        }
        if (consume(BASIC)) send(RelicInputPayload.BASIC);
        if (consume(DODGE)) send(RelicInputPayload.DODGE);
        if (consume(EXTRA)) send(RelicInputPayload.EXTRA);
        if (consume(ULTIMATE)) send(RelicInputPayload.ULTIMATE);
    }

    // consumeClick() 은 눌린 횟수를 하나씩 꺼낸다. while 로 비워야 연타가 다음 틱으로 밀리지 않고,
    // 한 틱에 같은 스킬이 두 번 나가지도 않는다(어차피 쿨다운이 막지만 헛 알림이 뜬다).
    private static boolean consume(KeyMapping k) {
        boolean hit = false;
        while (k.consumeClick()) hit = true;
        return hit;
    }

    private static void drain() {
        for (KeyMapping k : all()) while (k.consumeClick()) { }
    }

    private static void send(int action) {
        PacketDistributor.sendToServer(new RelicInputPayload(action));
    }
}
