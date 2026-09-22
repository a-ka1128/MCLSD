package com.laststardust.relics.client.anim;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;

/**
 * X「일도양단」의 <b>치켜들었다 내려찍는</b> 자세.
 *
 * <p>── 왜 키프레임 파일이 아니라 코드인가 ──
 * PlayerAnimator 는 보통 블록벤치로 뽑은 {@code .json} 을 읽어 {@code KeyframeAnimation} 으로
 * 재생한다. 여기서는 그러지 않았다. 우리가 필요한 건 <b>팔 두 짝과 상체의 회전 하나</b>뿐이고,
 * 그걸 위해 자산 파이프라인(블록벤치 플러그인 → 리소스팩 경로 → 로더)을 새로 여는 건
 * 유지비가 그림값보다 비싸다. 값이 코드에 있으면 <b>인게임에서 보고 숫자만 고쳐</b> 다시 빌드하면 된다.
 * 나중에 자세가 여러 개로 늘어나면 그때 파일로 옮긴다({@code docs/TODO.md} D-6).
 *
 * <p>── 시간표 (틱) ──
 * <pre>
 *   0 ──────── 16 ───── 21 ────────── 30
 *   │ 치켜든다  │ 내려찍음 │ 되돌아옴
 *   └ 서버의 SUNDER_WINDUP 과 «같은 길이»라야 그림과 판정이 맞는다
 * </pre>
 * 16 틱 지점이 {@code ParryManager.SUNDER_WINDUP} 이고, 바로 그때 서버가 피해를 준다.
 * <b>둘 중 하나를 고치면 반드시 다른 하나도 고쳐야 한다.</b>
 *
 * <p>── 회전값은 라디안이고 «절대값»이다 ──
 * {@code get3DTransform} 이 받는 {@code value0} 은 아래 레이어(바닐라 포함)가 계산해 둔 값이고,
 * 우리가 돌려주는 값이 그 자리를 <b>대신한다.</b> 그래서 팔은 통째로 덮어쓰고, 머리는 «보던 방향»을
 * 잃으면 안 되므로 {@code value0} 에 각도를 <b>더한다.</b>
 */
public final class SunderAnimation implements IAnimation {

    /** 치켜드는 시간. {@code ParryManager.SUNDER_WINDUP} 과 같아야 한다. */
    public static final int RAISE = 16;
    /** 내려찍는 시간. 짧을수록 «내려꽂힌다». */
    private static final int SLAM = 5;
    /** 되돌아오는 시간. */
    private static final int REST = 9;
    private static final int TOTAL = RAISE + SLAM + REST;

    /** 들어올 때·나갈 때 섞이는 구간. 갑자기 자세가 튀면 순간이동처럼 보인다. */
    private static final float FADE_IN = 2.0f;
    private static final float FADE_OUT = REST;

    private int time = 0;

    @Override public void tick() { time++; }

    @Override public boolean isActive() { return time < TOTAL; }

    @Override public void setupAnim(float tickDelta) { }

    /**
     * 1인칭에서도 보이게 한다. {@code THIRD_PERSON_MODEL} 은 «1인칭 팔» 대신 3인칭 모델을 그리므로
     * 치켜든 자세가 그대로 보인다.
     *
     * <p>⚠️ 인게임에서 이게 어색하면 {@code NONE} 으로 바꾸면 된다 — 그러면 3인칭에만 나온다.
     * 바꿔야 할 곳은 이 메서드 하나뿐이다.
     */
    @Override
    public FirstPersonMode getFirstPersonMode(float tickDelta) {
        return FirstPersonMode.THIRD_PERSON_MODEL;
    }

    @Override
    public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
        if (type != TransformType.ROTATION) return value0;

        float t = time + tickDelta;
        float a = blend(t);
        if (a <= 0.0f) return value0;

        switch (modelName) {
            case "rightArm": return mix(value0, arm(t, +1.0f), a);
            case "leftArm":  return mix(value0, arm(t, -1.0f), a);
            case "body":     return mix(value0, new Vec3f(lean(t), value0.getY(), value0.getZ()), a);
            // 머리는 «보던 방향»을 유지해야 한다 — 상체가 숙인 만큼 반대로 들어 시선을 붙잡는다.
            case "head":     return mix(value0,
                new Vec3f(value0.getX() - lean(t) * 0.6f, value0.getY(), value0.getZ()), a);
            default:         return value0;
        }
    }

    // ── 자세 ──

    /**
     * 팔. {@code side} 는 오른팔 +1 / 왼팔 −1 — 대검은 두 손으로 잡으므로 좌우가 거의 같고,
     * z 만 살짝 벌려 «두 손이 자루에 모인» 꼴을 만든다.
     */
    private Vec3f arm(float t, float side) {
        final float REST_X = 0.0f;
        final float UP_X = -2.95f;     // 거의 수직으로 치켜든다 (약 −169°)
        final float DOWN_X = 0.55f;    // 내려친 끝 — 앞아래
        float x;
        if (t <= RAISE) {
            x = lerp(REST_X, UP_X, easeOut(t / RAISE));           // 빠르게 올라가 천천히 멎는다
        } else if (t <= RAISE + SLAM) {
            x = lerp(UP_X, DOWN_X, easeIn((t - RAISE) / SLAM));   // 뜸 들이다 «꽂힌다»
        } else {
            x = lerp(DOWN_X, REST_X, (t - RAISE - SLAM) / REST);
        }
        // 치켜들 때만 손을 모은다. 내려칠 땐 자연스럽게 풀린다.
        float z = 0.16f * side * (t <= RAISE ? easeOut(t / RAISE) : 0.0f);
        return new Vec3f(x, 0.0f, z);
    }

    /** 상체. 치켜들 땐 뒤로 젖히고, 내려칠 땐 앞으로 숙인다 — 이게 «무게»를 만든다. */
    private float lean(float t) {
        if (t <= RAISE) return lerp(0.0f, -0.20f, easeOut(t / RAISE));
        if (t <= RAISE + SLAM) return lerp(-0.20f, 0.50f, easeIn((t - RAISE) / SLAM));
        return lerp(0.50f, 0.0f, (t - RAISE - SLAM) / REST);
    }

    // ── 셈 ──

    /** 들어올 때·나갈 때의 섞임 비율. */
    private float blend(float t) {
        if (t < 0) return 0.0f;
        if (t < FADE_IN) return t / FADE_IN;
        float left = TOTAL - t;
        if (left < FADE_OUT) return Math.max(0.0f, left / FADE_OUT);
        return 1.0f;
    }

    private static Vec3f mix(Vec3f from, Vec3f to, float a) {
        return new Vec3f(lerp(from.getX(), to.getX(), a),
                         lerp(from.getY(), to.getY(), a),
                         lerp(from.getZ(), to.getZ(), a));
    }

    private static float lerp(float a, float b, float t) {
        float c = t < 0 ? 0 : (t > 1 ? 1 : t);
        return a + (b - a) * c;
    }

    /** 빠르게 시작해 천천히 멎는다 — 「들어올린다」. */
    private static float easeOut(float t) {
        float c = t < 0 ? 0 : (t > 1 ? 1 : t);
        return 1.0f - (1.0f - c) * (1.0f - c);
    }

    /** 천천히 시작해 «가속한다» — 「내려꽂는다」. */
    private static float easeIn(float t) {
        float c = t < 0 ? 0 : (t > 1 ? 1 : t);
        return c * c * c;
    }
}
