#version 150

uniform mat4 InvViewMat;
uniform mat4 InvProjMat;
uniform vec3 CameraPosition;
uniform vec3 FlashPosition;
uniform float Time;
uniform float StormStrength;
uniform float WarningProgress;
uniform float CountdownStrength;
uniform float DissolveProgress;
uniform float FlashIntensity;
uniform float DayFactor;

in vec2 screenPosition;
out vec4 fragColor;

const int CLOUD_STEPS = 20;
const float RAY_START = 24.0;
const float RAY_END = 1120.0;

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise3(vec3 p) {
    vec3 cell = floor(p);
    vec3 local = fract(p);
    local = local * local * (3.0 - 2.0 * local);

    float n000 = hash31(cell + vec3(0.0, 0.0, 0.0));
    float n100 = hash31(cell + vec3(1.0, 0.0, 0.0));
    float n010 = hash31(cell + vec3(0.0, 1.0, 0.0));
    float n110 = hash31(cell + vec3(1.0, 1.0, 0.0));
    float n001 = hash31(cell + vec3(0.0, 0.0, 1.0));
    float n101 = hash31(cell + vec3(1.0, 0.0, 1.0));
    float n011 = hash31(cell + vec3(0.0, 1.0, 1.0));
    float n111 = hash31(cell + vec3(1.0, 1.0, 1.0));

    float x00 = mix(n000, n100, local.x);
    float x10 = mix(n010, n110, local.x);
    float x01 = mix(n001, n101, local.x);
    float x11 = mix(n011, n111, local.x);
    float y0 = mix(x00, x10, local.y);
    float y1 = mix(x01, x11, local.y);
    return mix(y0, y1, local.z);
}

float fbm3(vec3 p) {
    float value = 0.0;
    float amplitude = 0.54;

    for (int octave = 0; octave < 3; octave++) {
        value += noise3(p) * amplitude;
        p = p * 2.02 + vec3(13.7, 8.9, 17.3);
        amplitude *= 0.48;
    }

    return value;
}

float smoothPulse(float value) {
    return value * value * (3.0 - 2.0 * value);
}

float cloudVerticalMask(float relativeHeight) {
    // Очень широкие плавные границы вместо одной плоской облачной плоскости.
    float bottom = smoothstep(42.0, 132.0, relativeHeight);
    float top = 1.0 - smoothstep(345.0, 535.0, relativeHeight);
    return bottom * top;
}

float cloudDensity(vec3 worldPosition, float travel, out float ridge, out float heightFraction) {
    float relativeHeight = worldPosition.y - CameraPosition.y;
    float verticalMask = cloudVerticalMask(relativeHeight);
    if (verticalMask <= 0.0001) {
        ridge = 0.0;
        heightFraction = 0.0;
        return 0.0;
    }

    heightFraction = clamp((relativeHeight - 42.0) / 493.0, 0.0, 1.0);

    // Координаты непрерывные. Здесь намеренно нет mod(), тайлов и жёстких
    // границ ячеек, из-за которых раньше появлялись заметные склейки.
    vec3 base = vec3(
        worldPosition.x * 0.00225,
        relativeHeight * 0.00620,
        worldPosition.z * 0.00225
    );

    float reverse = mix(1.0, -0.82, smoothPulse(DissolveProgress));
    vec3 wind = vec3(Time * 0.011 * reverse, Time * 0.0018, -Time * 0.0075 * reverse);

    float warpLarge = noise3(base * 0.48 + wind * 0.42);
    float bendX = sin(base.z * 0.72 + Time * 0.014 * reverse + warpLarge * 2.2);
    float bendZ = cos(base.x * 0.68 - Time * 0.012 * reverse - warpLarge * 1.9);
    vec3 warped = base + wind + vec3(bendX, 0.0, bendZ) * (0.15 + StormStrength * 0.28);
    warped += vec3(warpLarge * 0.52, 0.0, warpLarge * 0.39);

    float broad = fbm3(warped * 0.63 + vec3(3.2, 1.7, 8.4));
    float body = fbm3(warped * 1.18 + vec3(11.0, -3.0, 5.0));
    float detail = noise3(warped * 4.20 + vec3(-7.0, Time * 0.005, 12.0));

    float calmCoverage = 0.61;
    float stormCoverage = mix(0.0, 0.19, StormStrength);
    float countdownCoverage = CountdownStrength * 0.08;
    float threshold = calmCoverage - stormCoverage - countdownCoverage - WarningProgress * 0.025;

    float billow = 1.0 - abs(heightFraction * 2.0 - 1.0);
    float raw = broad * 0.53 + body * 0.37 + detail * 0.10 + billow * 0.08;
    float density = smoothstep(threshold, threshold + 0.25, raw) * verticalMask;

    // Дальний край растворяется плавно, поэтому на горизонте нет полосы-среза.
    density *= 1.0 - smoothstep(850.0, RAY_END, travel);

    // Во время рассеивания облака не исчезают в пустоту, а возвращаются к
    // спокойному состоянию. Уходит только избыточная штормовая плотность.
    float stormExcess = StormStrength * (0.14 + CountdownStrength * 0.08);
    density = clamp(density + stormExcess * verticalMask * (broad - 0.35), 0.0, 1.0);

    float folded = 1.0 - abs(detail * 2.0 - 1.0);
    ridge = pow(clamp(folded, 0.0, 1.0), 6.0)
            * density
            * smoothstep(0.43, 0.78, body + broad * 0.22);

    return density;
}

vec3 calmSky(vec3 direction) {
    float up = clamp(direction.y * 0.5 + 0.5, 0.0, 1.0);
    float horizon = pow(clamp(1.0 - abs(direction.y), 0.0, 1.0), 2.4);

    vec3 nightZenith = vec3(0.006, 0.012, 0.020);
    vec3 nightHorizon = vec3(0.020, 0.032, 0.045);
    vec3 dayZenith = vec3(0.105, 0.175, 0.235);
    vec3 dayHorizon = vec3(0.255, 0.315, 0.340);

    vec3 zenith = mix(nightZenith, dayZenith, DayFactor);
    vec3 horizonColor = mix(nightHorizon, dayHorizon, DayFactor);
    vec3 color = mix(horizonColor, zenith, pow(up, 0.72));
    color += horizonColor * horizon * 0.13;
    return color;
}

vec3 stormSky(vec3 direction) {
    float up = clamp(direction.y * 0.5 + 0.5, 0.0, 1.0);
    float horizon = pow(clamp(1.0 - abs(direction.y), 0.0, 1.0), 2.8);

    vec3 low = vec3(0.002, 0.012, 0.014);
    vec3 high = vec3(0.001, 0.008, 0.012);
    vec3 color = mix(low, high, pow(up, 0.75));
    color += vec3(0.004, 0.046, 0.028) * horizon;
    return color;
}

void main() {
    vec4 viewPosition = InvProjMat * vec4(screenPosition, 1.0, 1.0);
    vec3 viewDirection = normalize(viewPosition.xyz / max(abs(viewPosition.w), 0.0001));
    vec3 worldDirection = normalize((InvViewMat * vec4(viewDirection, 0.0)).xyz);

    vec3 color = mix(calmSky(worldDirection), stormSky(worldDirection), StormStrength);

    vec3 flashVector = FlashPosition - CameraPosition;
    vec3 flashDirection = length(flashVector) > 0.01
            ? normalize(flashVector)
            : vec3(0.0, 1.0, 0.0);
    float flashAlignment = max(dot(worldDirection, flashDirection), 0.0);
    float broadSkyFlash = pow(flashAlignment, 7.0) * 0.17
            + pow(flashAlignment, 38.0) * 1.12;
    color += vec3(0.055, 0.590, 0.225) * broadSkyFlash * FlashIntensity;

    float dissolveGlow = sin(clamp(DissolveProgress, 0.0, 1.0) * 3.14159265);
    float skyGlow = dissolveGlow * (0.35 + 0.65 * pow(max(worldDirection.y, 0.0), 0.35));
    color += vec3(0.018, 0.205, 0.072) * skyGlow;

    float stepLength = (RAY_END - RAY_START) / float(CLOUD_STEPS);
    float jitter = hash21(screenPosition * 719.31 + vec2(Time * 0.013, -Time * 0.009));
    float travel = RAY_START + stepLength * jitter;

    vec3 accumulatedColor = vec3(0.0);
    float accumulatedAlpha = 0.0;
    float previousDensity = 0.0;

    for (int stepIndex = 0; stepIndex < CLOUD_STEPS; stepIndex++) {
        vec3 sampleWorld = CameraPosition + worldDirection * travel;
        float ridge;
        float heightFraction;
        float density = cloudDensity(sampleWorld, travel, ridge, heightFraction);

        if (density > 0.001) {
            float enteringEdge = clamp((density - previousDensity) * 3.1 + 0.43, 0.0, 1.0);
            float depthLight = exp(-(travel - RAY_START) * 0.00075);
            float topLight = mix(0.16, 0.84, heightFraction);
            float internalMotion = 0.82 + 0.18 * sin(
                    Time * 0.31 + sampleWorld.x * 0.0067 + sampleWorld.z * 0.0051
            );

            vec3 calmShadow = vec3(0.026, 0.032, 0.040) * (0.45 + DayFactor * 0.75);
            vec3 calmBody = vec3(0.110, 0.125, 0.135) * (0.50 + DayFactor * 0.70);
            vec3 calmTop = vec3(0.245, 0.255, 0.255) * (0.45 + DayFactor * 0.75);

            vec3 stormShadow = vec3(0.0025, 0.0120, 0.0135);
            vec3 stormBody = vec3(0.0065, 0.0510, 0.0390);
            vec3 stormTop = vec3(0.0170, 0.1320, 0.0740);

            vec3 shadow = mix(calmShadow, stormShadow, StormStrength);
            vec3 bodyColor = mix(calmBody, stormBody, StormStrength);
            vec3 topColor = mix(calmTop, stormTop, StormStrength);
            vec3 sampleColor = mix(shadow, bodyColor, topLight);
            sampleColor = mix(sampleColor, topColor, enteringEdge * 0.42 * depthLight);

            float flashDistance = length((sampleWorld - FlashPosition) * vec3(0.0038, 0.0020, 0.0038));
            float localFlash = exp(-flashDistance * flashDistance * 1.55) * FlashIntensity;
            sampleColor += vec3(0.070, 0.760, 0.270) * localFlash * (0.30 + density * 0.90);

            float biological = ridge * StormStrength * internalMotion;
            sampleColor += vec3(0.030, 0.590, 0.195) * biological * (0.40 + localFlash * 0.85);
            sampleColor += vec3(0.045, 0.920, 0.285)
                    * ridge
                    * dissolveGlow
                    * (0.55 + enteringEdge * 0.45);

            float extinction = density * stepLength * mix(0.0125, 0.0210, StormStrength);
            float sampleAlpha = 1.0 - exp(-extinction);
            float remaining = 1.0 - accumulatedAlpha;
            accumulatedColor += remaining * sampleColor * sampleAlpha;
            accumulatedAlpha += remaining * sampleAlpha;
        }

        previousDensity = density;
        travel += stepLength;

        if (accumulatedAlpha > 0.986) {
            break;
        }
    }

    color = color * (1.0 - accumulatedAlpha) + accumulatedColor;

    // Мягкое затемнение у горизонта без геометрической полосы или шва.
    float horizon = pow(clamp(1.0 - abs(worldDirection.y), 0.0, 1.0), 3.0);
    color = mix(color, color * vec3(0.72, 0.84, 0.78), horizon * StormStrength * 0.22);

    // Шейдер непрозрачен всегда: ванильные солнце, луна, звёзды и стандартное
    // небо не просвечивают ни в спокойную погоду, ни во время биовыброса.
    fragColor = vec4(max(color, vec3(0.0)), 1.0);
}
