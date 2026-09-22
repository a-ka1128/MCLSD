# 비행선 진입 타이틀 — 커맨드블럭(충격) 하나가 이걸 부른다.
#   execute as @p at @s run function last_stardust:airship_enter
#
# ⚠️ 순서가 전부다. 서브타이틀은 «보관»만 되고 타이틀 명령이 올 때 같이 뜬다 —
#    타이틀을 먼저 쏘면 그 판엔 자막이 안 나오고 다음번에 뒤늦게 따라붙는다.
#    실제로 ls_siege.js 에서 겪었다(「N번째 물결이 몰려온다!」가 §a성역 방어 성공!§r 밑에 붙었다).
#
# ⚠️ 대상은 @s 다 — **밟은 사람에게만** 뜬다 (2026-08-14).
#    예전엔 @a 였다. 그러면 한 명이 감압판을 밟는 순간 접속자 «전원»의 화면에
#    「성역으로 향하는 비행선」이 뜬다. 여덟 명이 각자 지나갈 때마다 여덟 번 뜨는 셈이고,
#    광산에 있던 사람에게는 뜻을 알 수 없는 자막이 된다.
#
#    ⚠️ 그래서 이 함수는 **반드시 `as <플레이어>` 로 불려야 한다.**
#       그냥 `function last_stardust:airship_enter` 로 부르면 실행자가 커맨드블럭이라
#       @s 가 플레이어가 아니고 — 아무에게도 안 뜬다. 오류도 안 난다.
#       커맨드블럭에는 `execute as @p at @s run function …` 그대로 둘 것.

# 페이드인 0.5초 · 유지 3초 · 페이드아웃 1초
title @s times 10 60 20

title @s subtitle {"text":"남겨진 아이템이 있는 것 같다.","color":"gray"}
title @s title {"text":"성역으로 향하는 비행선","color":"gold","bold":true}

playsound minecraft:block.amethyst_block.chime master @s ~ ~ ~ 0.8 0.7
