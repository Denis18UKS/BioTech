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
uniform float NoiseAmount;

in vec2 screenPosition;
out vec4 fragColor;

const int CLOUD_STEPS = 24;
const float RAY_START = 2.0;
const float RAY_END = 1380.0;

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
    for (int octave = 0; octave < 4; octave++) {
        value += noise3(p) * amplitude;
        p = p * 2.02 + vec3(13.7, 8.9, 17.3);
        amplitude *= 0.48;
    }
    return value;
}

float band(float y, float bottom0, float bottom1, float top0, float top1) {
    return smoothstep(bottom0, bottom1, y) * (1.0 - smoothstep(top0, top1, y));
}

float cloudVerticalMask(float worldY) {
    float calmLayer = band(worldY, 118.0, 150.0, 232.0, 278.0);
    float stormLower = band(worldY, 82.0, 126.0, 286.0, 348.0);
    float stormUpper = band(worldY, 224.0, 272.0, 430.0, 520.0) * 0.82;
    float stormLayer = max(stormLower, stormUpper);
    return mix(calmLayer, stormLayer, StormStrength);
}

float worldHeightFraction(float worldY) {
    float calm = clamp((worldY - 118.0) / 160.0, 0.0, 1.0);
    float storm = clamp((worldY - 82.0) / 438.0, 0.0, 1.0);
    return mix(calm, storm, StormStrength);
}

float veinNetwork(vec3 worldPosition, float density) {
    float reverse = mix(1.0, -0.82, smoothstep(0.0, 1.0, DissolveProgress));
    vec3 p = vec3(
        worldPosition.x * 0.0080,
        worldPosition.y * 0.0105,
        worldPosition.z * 0.0080
    );

    vec3 drift = vec3(Time * 0.010 * reverse, Time * 0.0020, -Time * 0.007 * reverse);
    float warpA = noise3(p * 0.72 + drift + vec3(7.0, -2.0, 4.0));
    float warpB = noise3(p.yzx * 1.08 - drift * 0.44 + vec3(-3.0, 8.0, 1.0));
    vec3 warped = p + vec3(warpA - 0.5, warpB - 0.5, warpA - warpB) * 0.92;

    float fieldA = noise3(warped * 1.18 + vec3(4.0, 1.0, -7.0));
    float fieldB = noise3(warped.zxy * 1.76 + vec3(-8.0, 5.0, 2.0));
    float fieldC = noise3(warped.yxz * 2.45 + vec3(3.0, -6.0, 9.0));

    float mainLine = 1.0 - smoothstep(0.018, 0.070, abs(fieldA - 0.535));
    float branchLine = 1.0 - smoothstep(0.014, 0.055, abs(fieldB - 0.505));
    float fineLine = 1.0 - smoothstep(0.010, 0.040, abs(fieldC - 0.575));

    float branchMask = smoothstep(0.34, 0.66, warpA * 0.62 + warpB * 0.38);
    float network = max(mainLine, branchLine * branchMask);
    network = max(network, fineLine * branchMask * 0.62);
    return network * density;
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
        worldPosition.x * 0.00218,
        worldPosition.y * 0.00605,
        worldPosition.z * 0.00218
    );

    float reverse = mix(1.0, -0.82, smoothstep(0.0, 1.0, DissolveProgress));
    vec3 wind = vec3(Time * 0.0105 * reverse, Time * 0.0017, -Time * 0.0071 * reverse);

    float warpLarge = noise3(base * 0.46 + wind * 0.40);
    float warpMedium = noise3(base * 0.92 - wind * 0.25 + vec3(8.0, 3.0, -5.0));
    float bendX = sin(base.z * 0.69 + Time * 0.013 * reverse + warpLarge * 2.35);
    float bendZ = cos(base.x * 0.66 - Time * 0.011 * reverse - warpLarge * 2.05);

    vec3 warped = base + wind + vec3(bendX, 0.0, bendZ) * (0.13 + StormStrength * 0.31);
    warped += vec3(warpLarge * 0.50, warpMedium * 0.06, warpLarge * 0.38);

    float broad = fbm3(warped * 0.60 + vec3(3.2, 1.7, 8.4));
    float body = fbm3(warped * 1.13 + vec3(11.0, -3.0, 5.0));
    float detailSmooth = noise3(warped * 1.85 + vec3(-7.0, Time * 0.0022, 12.0));
    float detailFine = noise3(warped * 3.20 + vec3(-7.0, Time * 0.0030, 12.0));
    float detail = mix(detailSmooth, detailFine, clamp(NoiseAmount, 0.0, 1.0));

    float threshold = 0.612
            - StormStrength * 0.235
            - CountdownStrength * 0.090
            - WarningProgress * 0.030;

    float billow = 1.0 - abs(heightFraction * 2.0 - 1.0);
    float detailWeight = 0.035 + clamp(NoiseAmount, 0.0, 1.0) * 0.035;
    float raw = broad * 0.53 + body * 0.39 + detail * detailWeight + billow * 0.105;
    float density = smoothstep(threshold, threshold + 0.265, raw) * verticalMask;

    density *= 1.0 - smoothstep(1030.0, RAY_END, travel);

    float stormExcess = StormStrength * (0.18 + CountdownStrength * 0.10);
    density = clamp(density + stormExcess * verticalMask * (broad - 0.30), 0.0, 1.0);

    float folded = 1.0 - abs(detail * 2.0 - 1.0);
    float cellular = 1.0 - abs(warpMedium * 2.0 - 1.0);
    ridge = pow(clamp(folded * 0.70 + cellular * 0.30, 0.0, 1.0), 5.5)
            * density
            * smoothstep(0.40, 0.76, body + broad * 0.24);

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

    vec3 low = vec3(0.0015, 0.0100, 0.0120);
    vec3 high = vec3(0.0008, 0.0065, 0.0100);
    vec3 color = mix(low, high, pow(up, 0.75));
    color += vec3(0.003, 0.038, 0.023) * horizon;
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
    vec2 stablePixel = floor(gl_FragCoord.xy * 0.5);
    float stableNoise = hash21(stablePixel + vec2(17.0, 53.0)) - 0.5;
    float jitter = 0.5 + stableNoise * clamp(NoiseAmount, 0.0, 1.0);
    float travel = RAY_START + stepLength * jitter;

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
            float enteringEdge = clamp((density - previousDensity) * 3.0 + 0.42, 0.0, 1.0);
            float depthLight = exp(-(travel - RAY_START) * 0.00072);
            float topLight = mix(0.12, 0.88, heightFraction);
            float internalMotion = 0.76 + 0.24 * sin(
                    Time * 0.34 + sampleWorld.x * 0.0064 + sampleWorld.z * 0.0054
            );

            vec3 calmShadow = vec3(0.095, 0.105, 0.120) * (0.55 + DayFactor * 0.70);
            vec3 calmBody = vec3(0.330, 0.355, 0.390) * (0.48 + DayFactor * 0.72);
            vec3 calmTop = vec3(0.750, 0.790, 0.835) * (0.45 + DayFactor * 0.72);

            vec3 stormShadow = vec3(0.0018, 0.0080, 0.0100);
            vec3 stormBody = vec3(0.0040, 0.0370, 0.0280);
            vec3 stormTop = vec3(0.0140, 0.1150, 0.0610);

            vec3 shadow = mix(calmShadow, stormShadow, StormStrength);
            vec3 bodyColor = mix(calmBody, stormBody, StormStrength);
            vec3 topColor = mix(calmTop, stormTop, StormStrength);
            vec3 sampleColor = mix(shadow, bodyColor, topLight);
            sampleColor = mix(sampleColor, topColor, enteringEdge * 0.48 * depthLight);

            float local = totalLocalFlash(sampleWorld);
            float flashCore = min(local, 2.55);
            sampleColor += vec3(0.070, 0.980, 0.310)
                    * flashCore
                    * (0.28 + density * 1.15);

            float veinPulse = 0.78 + 0.22 * sin(
                    Time * 0.74
                    + sampleWorld.x * 0.010
                    + sampleWorld.y * 0.014
                    - sampleWorld.z * 0.008
            );

            float veinHalo = pow(clamp(veins, 0.0, 1.0), 0.62);
            float veinCore = pow(clamp(veins, 0.0, 1.0), 2.35);
            float veinStrength = StormStrength
                    * veinPulse
                    * (0.82 + flashCore * 1.35 + dissolveGlow * 0.92);

            sampleColor += vec3(0.020, 0.520, 0.130)
                    * veinHalo
                    * veinStrength;
            sampleColor += vec3(0.090, 1.850, 0.420)
                    * veinCore
                    * veinStrength
                    * 1.55;

            float biological = ridge * StormStrength * internalMotion;
            sampleColor += vec3(0.018, 0.330, 0.095)
                    * biological
                    * (0.32 + flashCore * 0.82);

            float extinction = density * stepLength * mix(0.0108, 0.0228, StormStrength);
            float sampleAlpha = 1.0 - exp(-extinction);
            float remaining = 1.0 - accumulatedAlpha;

            accumulatedColor += remaining * sampleColor * sampleAlpha;
            accumulatedAlpha += remaining * sampleAlpha;
        }

        previousDensity = density;
        travel += stepLength;

        if (accumulatedAlpha > 0.989) {
            break;
        }
    }

    color = color * (1.0 - accumulatedAlpha) + accumulatedColor;

    float horizon = pow(clamp(1.0 - abs(worldDirection.y), 0.0, 1.0), 3.0);
    color = mix(color, color * vec3(0.72, 0.84, 0.78), horizon * StormStrength * 0.20);

    fragColor = vec4(max(color, vec3(0.0)), 1.0);
}
