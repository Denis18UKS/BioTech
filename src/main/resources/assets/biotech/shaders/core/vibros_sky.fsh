#version 150

uniform mat4 InvViewMat;
uniform mat4 InvProjMat;
uniform vec3 CameraPosition;
uniform vec3 FlashPosition0;
uniform vec3 FlashPosition1;
uniform vec3 FlashPosition2;
uniform vec3 FlashPosition3;
uniform float Time;
uniform float StormStrength;
uniform float WarningProgress;
uniform float CountdownStrength;
uniform float DissolveProgress;
uniform float FlashIntensity0;
uniform float FlashIntensity1;
uniform float FlashIntensity2;
uniform float FlashIntensity3;
uniform float DayFactor;

in vec2 screenPosition;
out vec4 fragColor;

const int CLOUD_STEPS = 30;
const float RAY_START = 4.0;
const float RAY_END = 1500.0;

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
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
    float amplitude = 0.55;
    for (int octave = 0; octave < 4; octave++) {
        value += noise3(p) * amplitude;
        p = p * 2.01 + vec3(13.7, 8.9, 17.3);
        amplitude *= 0.47;
    }
    return value;
}

float band(float y, float bottom0, float bottom1, float top0, float top1) {
    return smoothstep(bottom0, bottom1, y) * (1.0 - smoothstep(top0, top1, y));
}

/*
 * Облачное ядро находится высоко над обычной зоной игры. Плотная часть
 * активного БВ начинается выше максимальной высоты обычного строительства,
 * поэтому тучи больше не спускаются до земли и не поглощают ландшафт.
 */
float cloudVerticalMask(float worldY) {
    float calmLayer = band(worldY, 238.0, 276.0, 372.0, 430.0);
    float stormLayer = band(worldY, 296.0, 338.0, 510.0, 590.0);
    return mix(calmLayer, stormLayer, StormStrength);
}

float worldHeightFraction(float worldY) {
    float calm = clamp((worldY - 238.0) / 192.0, 0.0, 1.0);
    float storm = clamp((worldY - 296.0) / 294.0, 0.0, 1.0);
    return mix(calm, storm, StormStrength);
}

/* Расстояние до границы ячейки Вороного. Узор гладкий и не зависит от пикселя. */
float voronoiEdge(vec2 p) {
    vec2 cell = floor(p);
    vec2 local = fract(p);
    float nearest = 100.0;
    float second = 100.0;

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 neighbour = vec2(float(x), float(y));
            vec2 point = neighbour + hash22(cell + neighbour);
            vec2 delta = point - local;
            float distanceSquared = dot(delta, delta);

            if (distanceSquared < nearest) {
                second = nearest;
                nearest = distanceSquared;
            } else if (distanceSquared < second) {
                second = distanceSquared;
            }
        }
    }

    float edgeDistance = sqrt(second) - sqrt(nearest);
    return 1.0 - smoothstep(0.035, 0.125, edgeDistance);
}

/*
 * Биологические мембраны из двух размеров ячеек. Используются только плавные
 * мировые координаты: gl_FragCoord, случайный пиксельный джиттер и зерно удалены.
 */
float veinNetwork(vec3 worldPosition, float density) {
    float reverse = mix(1.0, -0.72, smoothstep(0.0, 1.0, DissolveProgress));
    vec3 drift = vec3(Time * 0.010 * reverse, Time * 0.0018, -Time * 0.0065 * reverse);

    float warpX = noise3(worldPosition * vec3(0.0030, 0.0042, 0.0030) + drift + vec3(7.0, 2.0, -4.0));
    float warpZ = noise3(worldPosition.zxy * vec3(0.0036, 0.0030, 0.0036) - drift * 0.48 + vec3(-3.0, 8.0, 1.0));

    vec2 p = worldPosition.xz * 0.0065;
    p += vec2(warpX - 0.5, warpZ - 0.5) * 1.38;
    p += worldPosition.y * vec2(0.0012, -0.0009);
    p += vec2(Time * 0.010 * reverse, -Time * 0.006 * reverse);

    float largeCells = voronoiEdge(p * 0.66);
    float mediumCells = voronoiEdge(p + vec2(4.7, -8.2));
    float network = max(largeCells, mediumCells * 0.58);

    // Мягкое расширение контура вместо точечного высокочастотного свечения.
    float softMask = smoothstep(0.08, 0.52, density);
    return network * density * softMask;
}

float cloudDensity(
        vec3 worldPosition,
        float travel,
        out float ridge,
        out float heightFraction,
        out float veins
) {
    float verticalMask = cloudVerticalMask(worldPosition.y);
    if (verticalMask <= 0.0001) {
        ridge = 0.0;
        veins = 0.0;
        heightFraction = 0.0;
        return 0.0;
    }

    heightFraction = worldHeightFraction(worldPosition.y);

    vec3 base = vec3(
        worldPosition.x * 0.00205,
        worldPosition.y * 0.00520,
        worldPosition.z * 0.00205
    );

    float reverse = mix(1.0, -0.72, smoothstep(0.0, 1.0, DissolveProgress));
    vec3 wind = vec3(Time * 0.0090 * reverse, Time * 0.0012, -Time * 0.0062 * reverse);

    float warpLarge = noise3(base * 0.48 + wind * 0.42);
    float warpMedium = noise3(base * 0.88 - wind * 0.22 + vec3(8.0, 3.0, -5.0));
    float bendX = sin(base.z * 0.63 + Time * 0.011 * reverse + warpLarge * 2.10);
    float bendZ = cos(base.x * 0.60 - Time * 0.009 * reverse - warpLarge * 1.90);

    vec3 warped = base + wind + vec3(bendX, 0.0, bendZ) * (0.10 + StormStrength * 0.24);
    warped += vec3(warpLarge * 0.42, warpMedium * 0.045, warpLarge * 0.34);

    float broad = fbm3(warped * 0.58 + vec3(3.2, 1.7, 8.4));
    float body = fbm3(warped * 1.04 + vec3(11.0, -3.0, 5.0));

    float threshold = 0.615
            - StormStrength * 0.225
            - CountdownStrength * 0.078
            - WarningProgress * 0.026;

    float billow = 1.0 - abs(heightFraction * 2.0 - 1.0);
    float raw = broad * 0.58 + body * 0.34 + billow * 0.125;
    float density = smoothstep(threshold, threshold + 0.255, raw) * verticalMask;

    density *= 1.0 - smoothstep(1120.0, RAY_END, travel);

    float stormExcess = StormStrength * (0.16 + CountdownStrength * 0.08);
    density = clamp(density + stormExcess * verticalMask * (broad - 0.34), 0.0, 1.0);

    float folded = 1.0 - abs(warpMedium * 2.0 - 1.0);
    ridge = pow(clamp(folded, 0.0, 1.0), 5.0)
            * density
            * smoothstep(0.42, 0.75, body + broad * 0.20);

    veins = veinNetwork(worldPosition, density);
    return density;
}

vec3 calmSky(vec3 direction) {
    float up = clamp(direction.y * 0.5 + 0.5, 0.0, 1.0);
    float horizon = pow(clamp(1.0 - abs(direction.y), 0.0, 1.0), 2.2);

    vec3 nightZenith = vec3(0.004, 0.008, 0.020);
    vec3 nightHorizon = vec3(0.018, 0.026, 0.052);
    vec3 dayZenith = vec3(0.125, 0.315, 0.610);
    vec3 dayHorizon = vec3(0.480, 0.665, 0.825);

    vec3 zenith = mix(nightZenith, dayZenith, DayFactor);
    vec3 horizonColor = mix(nightHorizon, dayHorizon, DayFactor);
    vec3 color = mix(horizonColor, zenith, pow(up, 0.68));
    color += horizonColor * horizon * 0.14;
    return color;
}

vec3 stormSky(vec3 direction) {
    float up = clamp(direction.y * 0.5 + 0.5, 0.0, 1.0);
    float horizon = pow(clamp(1.0 - abs(direction.y), 0.0, 1.0), 2.8);

    vec3 low = vec3(0.0012, 0.0080, 0.0090);
    vec3 high = vec3(0.0006, 0.0048, 0.0070);
    vec3 color = mix(low, high, pow(up, 0.75));
    color += vec3(0.002, 0.030, 0.018) * horizon;
    return color;
}

float broadFlash(vec3 direction, vec3 position, float intensity) {
    vec3 vectorToFlash = position - CameraPosition;
    vec3 flashDirection = length(vectorToFlash) > 0.01
            ? normalize(vectorToFlash)
            : vec3(0.0, 1.0, 0.0);
    float alignment = max(dot(direction, flashDirection), 0.0);
    return (pow(alignment, 5.0) * 0.25 + pow(alignment, 28.0) * 1.42) * intensity;
}

float localFlash(vec3 sampleWorld, vec3 position, float intensity) {
    float distanceToFlash = length((sampleWorld - position) * vec3(0.0037, 0.0019, 0.0037));
    return exp(-distanceToFlash * distanceToFlash * 1.35) * intensity;
}

float totalBroadFlash(vec3 direction) {
    return broadFlash(direction, FlashPosition0, FlashIntensity0)
            + broadFlash(direction, FlashPosition1, FlashIntensity1)
            + broadFlash(direction, FlashPosition2, FlashIntensity2)
            + broadFlash(direction, FlashPosition3, FlashIntensity3);
}

float totalLocalFlash(vec3 sampleWorld) {
    return localFlash(sampleWorld, FlashPosition0, FlashIntensity0)
            + localFlash(sampleWorld, FlashPosition1, FlashIntensity1)
            + localFlash(sampleWorld, FlashPosition2, FlashIntensity2)
            + localFlash(sampleWorld, FlashPosition3, FlashIntensity3);
}

void main() {
    vec4 viewPosition = InvProjMat * vec4(screenPosition, 1.0, 1.0);
    vec3 viewDirection = normalize(viewPosition.xyz / max(abs(viewPosition.w), 0.0001));
    vec3 worldDirection = normalize((InvViewMat * vec4(viewDirection, 0.0)).xyz);

    vec3 color = mix(calmSky(worldDirection), stormSky(worldDirection), StormStrength);

    float broadSkyFlash = totalBroadFlash(worldDirection);
    color += vec3(0.045, 0.720, 0.245) * broadSkyFlash;

    float dissolveGlow = sin(clamp(DissolveProgress, 0.0, 1.0) * 3.14159265);
    float skyGlow = dissolveGlow * (0.35 + 0.65 * pow(max(worldDirection.y, 0.0), 0.35));
    color += vec3(0.018, 0.240, 0.082) * skyGlow;

    float stepLength = (RAY_END - RAY_START) / float(CLOUD_STEPS);
    float travel = RAY_START + stepLength * 0.5;

    vec3 accumulatedColor = vec3(0.0);
    float accumulatedAlpha = 0.0;
    float previousDensity = 0.0;

    for (int stepIndex = 0; stepIndex < CLOUD_STEPS; stepIndex++) {
        vec3 sampleWorld = CameraPosition + worldDirection * travel;

        float ridge;
        float heightFraction;
        float veins;
        float density = cloudDensity(sampleWorld, travel, ridge, heightFraction, veins);

        if (density > 0.001) {
            float enteringEdge = clamp((density - previousDensity) * 2.8 + 0.40, 0.0, 1.0);
            float depthLight = exp(-(travel - RAY_START) * 0.00068);
            float topLight = mix(0.10, 0.86, heightFraction);
            float internalMotion = 0.78 + 0.22 * sin(
                    Time * 0.27 + sampleWorld.x * 0.0048 + sampleWorld.z * 0.0040
            );

            vec3 calmShadow = vec3(0.095, 0.105, 0.120) * (0.55 + DayFactor * 0.70);
            vec3 calmBody = vec3(0.330, 0.355, 0.390) * (0.48 + DayFactor * 0.72);
            vec3 calmTop = vec3(0.750, 0.790, 0.835) * (0.45 + DayFactor * 0.72);

            vec3 stormShadow = vec3(0.0008, 0.0045, 0.0050);
            vec3 stormBody = vec3(0.0020, 0.0180, 0.0130);
            vec3 stormTop = vec3(0.0070, 0.0650, 0.0340);

            vec3 shadow = mix(calmShadow, stormShadow, StormStrength);
            vec3 bodyColor = mix(calmBody, stormBody, StormStrength);
            vec3 topColor = mix(calmTop, stormTop, StormStrength);
            vec3 sampleColor = mix(shadow, bodyColor, topLight);
            sampleColor = mix(sampleColor, topColor, enteringEdge * 0.42 * depthLight);

            float local = totalLocalFlash(sampleWorld);
            float flashCore = min(local, 2.55);
            sampleColor += vec3(0.060, 0.880, 0.270)
                    * flashCore
                    * (0.24 + density * 1.05);

            float veinPulse = 0.86 + 0.14 * sin(
                    Time * 0.48
                    + sampleWorld.x * 0.006
                    + sampleWorld.y * 0.009
                    - sampleWorld.z * 0.005
            );

            float veinHalo = pow(clamp(veins, 0.0, 1.0), 0.58);
            float veinCore = pow(clamp(veins, 0.0, 1.0), 2.10);
            float veinStrength = StormStrength
                    * veinPulse
                    * (0.92 + flashCore * 1.20 + dissolveGlow * 0.75);

            // Широкие чистые зелёные контуры, близкие к исходной атмосфере БВ.
            sampleColor += vec3(0.010, 0.360, 0.080)
                    * veinHalo
                    * veinStrength;
            sampleColor += vec3(0.060, 1.420, 0.300)
                    * veinCore
                    * veinStrength
                    * 1.42;

            float biological = ridge * StormStrength * internalMotion;
            sampleColor += vec3(0.012, 0.210, 0.060)
                    * biological
                    * (0.28 + flashCore * 0.70);

            float extinction = density * stepLength * mix(0.0092, 0.0188, StormStrength);
            float sampleAlpha = 1.0 - exp(-extinction);
            float remaining = 1.0 - accumulatedAlpha;

            accumulatedColor += remaining * sampleColor * sampleAlpha;
            accumulatedAlpha += remaining * sampleAlpha;
        }

        previousDensity = density;
        travel += stepLength;

        if (accumulatedAlpha > 0.988) {
            break;
        }
    }

    color = color * (1.0 - accumulatedAlpha) + accumulatedColor;

    float horizon = pow(clamp(1.0 - abs(worldDirection.y), 0.0, 1.0), 3.0);
    color = mix(color, color * vec3(0.72, 0.84, 0.78), horizon * StormStrength * 0.16);

    fragColor = vec4(max(color, vec3(0.0)), 1.0);
}
