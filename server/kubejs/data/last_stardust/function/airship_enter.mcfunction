# 비행선 진입 타이틀 — 커맨드블럭(충격) 하나가 이걸 부른다.
#   function last_stardust:airship_enter
#
# ⚠️ 순서가 전부다. 서브타이틀은 «보관»만 되고 타이틀 명령이 올 때 같이 뜬다 —
#    타이틀을 먼저 쏘면 그 판엔 자막이 안 나오고 다음번에 뒤늦게 따라붙는다.
#    실제로 ls_siege.js 에서 겪었다(「N번째 물결이 몰려온다!」가 §a성역 방어 성공!§r 밑에 붙었다).
#
# ⚠️ 대상이 @a 다 — 비행선에 들어온 «그 사람»에게만 띄우려면 감압판 위에서
#    `execute as @p at @s run function …` 으로 부르고 아래 @a 를 @s 로 바꾼다.

# 페이드인 0.5초 · 유지 3초 · 페이드아웃 1초
title @a times 10 60 20

title @a subtitle {"text":"남겨진 아이템이 있는 것 같다.","color":"gray"}
title @a title {"text":"성역으로 향하는 비행선","color":"gold","bold":true}

playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.7
