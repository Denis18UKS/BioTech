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

const int CLOUD_STEPS = 22;
const float RAY_START = 20.0;
const float RAY_END = 1160.0;

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

float smoothPulse(float value) {
    return value * value * (3.0 - 2.0 * value);
}

float cloudVerticalMask(float relativeHeight) {
    float bottom = smoothstep(38.0, 126.0, relativeHeight);
    float top = 1.0 - smoothstep(352.0, 548.0, relativeHeight);
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

    heightFraction = clamp((relativeHeight - 38.0) / 510.0, 0.0, 1.0);

    // Только непрерывные мировые координаты: никаких mod(), тайлов, экранных
    // половин и жёстких границ, способных создать вертикальный шов.
    vec3 base = vec3(
        worldPosition.x * 0.00218,
        relativeHeight * 0.00605,
        worldPosition.z * 0.00218
    );

    float reverse = mix(1.0, -0.82, smoothPulse(DissolveProgress));
    vec3 wind = vec3(Time * 0.0105 * reverse, Time * 0.0017, -Time * 0.0071 * reverse);

    float warpLarge = noise3(base * 0.46 + wind * 0.40);
    float warpMedium = noise3(base * 0.92 - wind * 0.25 + vec3(8.0, 3.0, -5.0));
    float bendX = sin(base.z * 0.69 + Time * 0.013 * reverse + warpLarge * 2.35);
    float bendZ = cos(base.x * 0.66 - Time * 0.011 * reverse - warpLarge * 2.05);
    vec3 warped = base + wind + vec3(bendX, 0.0, bendZ) * (0.13 + StormStrength * 0.31);
    warped += vec3(warpLarge * 0.50, warpMedium * 0.06, warpLarge * 0.38);

    float broad = fbm3(warped * 0.60 + vec3(3.2, 1.7, 8.4));
    float body = fbm3(warped * 1.13 + vec3(11.0, -3.0, 5.0));
    float detail = noise3(warped * 4.00 + vec3(-7.0, Time * 0.0046, 12.0));

    float calmCoverage = 0.595;
    float stormCoverage = StormStrength * 0.205;
    float countdownCoverage = CountdownStrength * 0.090;
    float threshold = calmCoverage - stormCoverage - countdownCoverage - WarningProgress * 0.030;

    float billow = 1.0 - abs(heightFraction * 2.0 - 1.0);
    float raw = broad * 0.51 + body * 0.37 + detail * 0.09 + billow * 0.095;
    float density = smoothstep(threshold, threshold + 0.245, raw) * verticalMask;

    // Полное растворение до RAY_END не создаёт геометрического среза на горизонте.
    density *= 1.0 - smoothstep(865.0, RAY_END, travel);

    float stormExcess = StormStrength * (0.16 + CountdownStrength * 0.09);
    density = clamp(density + stormExcess * verticalMask * (broad - 0.32), 0.0, 1.0);

    float folded = 1.0 - abs(detail * 2.0 - 1.0);
    float cellular = 1.0 - abs(warpMedium * 2.0 - 1.0);
    ridge = pow(clamp(folded * 0.75 + cellular * 0.25, 0.0, 1.0), 6.5)
            * density
            * smoothstep(0.42, 0.77, body + broad * 0.24);

    return density;
}

vec3 calmSky(vec3 direction) {
    float up = clamp(direction.y * 0.5 + 0.5, 0.0, 1.0);
    float horizon = pow(clamp(1.0 - abs(direction.y), 0.0, 1.0), 2.4);

    vec3 nightZenith = vec3(0.005, 0.010, 0.017);
    vec3 nightHorizon = vec3(0.016, 0.026, 0.035);
    vec3 dayZenith = vec3(0.060, 0.090, 0.112);
    vec3 dayHorizon = vec3(0.130, 0.160, 0.168);

    vec3 zenith = mix(nightZenith, dayZenith, DayFactor);
    vec3 horizonColor = mix(nightHorizon, dayHorizon, DayFactor);
    vec3 color = mix(horizonColor, zenith, pow(up, 0.72));
    color += horizonColor * horizon * 0.10;
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
    return (pow(alignment, 6.0) * 0.18 + pow(alignment, 30.0) * 1.28) * intensity;
}

float localFlash(vec3 sampleWorld, vec3 position, float intensity) {
    float distanceToFlash = length((sampleWorld - position) * vec3(0.0037, 0.0019, 0.0037));
    return exp(-distanceToFlash * distanceToFlash * 1.40) * intensity;
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
    color += vec3(0.050, 0.625, 0.220) * broadSkyFlash;

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
            float enteringEdge = clamp((density - previousDensity) * 3.0 + 0.42, 0.0, 1.0);
            float depthLight = exp(-(travel - RAY_START) * 0.00072);
            float topLight = mix(0.14, 0.82, heightFraction);
            float internalMotion = 0.77 + 0.23 * sin(
                    Time * 0.34 + sampleWorld.x * 0.0064 + sampleWorld.z * 0.0054
            );

            vec3 calmShadow = vec3(0.018, 0.023, 0.029) * (0.58 + DayFactor * 0.52);
            vec3 calmBody = vec3(0.072, 0.080, 0.086) * (0.62 + DayFactor * 0.50);
            vec3 calmTop = vec3(0.155, 0.162, 0.162) * (0.60 + DayFactor * 0.52);

            vec3 stormShadow = vec3(0.0020, 0.0090, 0.0105);
            vec3 stormBody = vec3(0.0050, 0.0430, 0.0310);
            vec3 stormTop = vec3(0.0160, 0.1180, 0.0640);

            vec3 shadow = mix(calmShadow, stormShadow, StormStrength);
            vec3 bodyColor = mix(calmBody, stormBody, StormStrength);
            vec3 topColor = mix(calmTop, stormTop, StormStrength);
            vec3 sampleColor = mix(shadow, bodyColor, topLight);
            sampleColor = mix(sampleColor, topColor, enteringEdge * 0.44 * depthLight);

            float local = totalLocalFlash(sampleWorld);
            float flashCore = min(local, 2.35);
            sampleColor += vec3(0.075, 0.900, 0.290) * flashCore * (0.25 + density * 1.05);

            // Светящиеся биологические прожилки проявляются только внутри массы
            // облаков и становятся особенно яркими рядом с несколькими очагами.
            float biological = ridge * StormStrength * internalMotion;
            float veinPulse = 0.70 + 0.30 * sin(
                    Time * 0.68 + sampleWorld.x * 0.010 + sampleWorld.y * 0.015 - sampleWorld.z * 0.008
            );
            sampleColor += vec3(0.026, 0.620, 0.190)
                    * biological
                    * veinPulse
                    * (0.46 + flashCore * 1.05);
            sampleColor += vec3(0.050, 1.020, 0.300)
                    * ridge
                    * dissolveGlow
                    * (0.55 + enteringEdge * 0.45);

            float extinction = density * stepLength * mix(0.0123, 0.0218, StormStrength);
            float sampleAlpha = 1.0 - exp(-extinction);
            float remaining = 1.0 - accumulatedAlpha;
            accumulatedColor += remaining * sampleColor * sampleAlpha;
            accumulatedAlpha += remaining * sampleAlpha;
        }

        previousDensity = density;
        travel += stepLength;

        if (accumulatedAlpha > 0.987) {
            break;
        }
    }

    color = color * (1.0 - accumulatedAlpha) + accumulatedColor;

    float horizon = pow(clamp(1.0 - abs(worldDirection.y), 0.0, 1.0), 3.0);
    color = mix(color, color * vec3(0.72, 0.84, 0.78), horizon * StormStrength * 0.20);

    fragColor = vec4(max(color, vec3(0.0)), 1.0);
}
