#version 150

uniform mat4 InvViewMat;
uniform mat4 InvProjMat;
uniform vec3 CameraPosition;
uniform vec3 FlashPosition;
uniform float Time;
uniform float Intensity;
uniform float FlashIntensity;

in vec2 screenPosition;
out vec4 fragColor;

const float CLOUD_BOTTOM = 122.0;
const float CLOUD_TOP = 410.0;
const float MAX_CLOUD_DISTANCE = 1450.0;
const int CLOUD_STEPS = 18;

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float noise2(vec2 p) {
    vec2 cell = floor(p);
    vec2 local = fract(p);
    local = local * local * (3.0 - 2.0 * local);

    float a = hash21(cell);
    float b = hash21(cell + vec2(1.0, 0.0));
    float c = hash21(cell + vec2(0.0, 1.0));
    float d = hash21(cell + vec2(1.0, 1.0));
    return mix(mix(a, b, local.x), mix(c, d, local.x), local.y);
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
    float amplitude = 0.56;

    for (int octave = 0; octave < 3; octave++) {
        value += noise3(p) * amplitude;
        p = p * 2.03 + vec3(17.2, 9.4, 13.7);
        amplitude *= 0.48;
    }

    return value;
}

mat2 rotation(float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return mat2(c, -s, s, c);
}

float cloudHeightShape(float normalizedHeight) {
    float bottomFade = smoothstep(0.0, 0.14, normalizedHeight);
    float topFade = 1.0 - smoothstep(0.68, 1.0, normalizedHeight);
    return bottomFade * topFade;
}

/**
 * Возвращает настоящую трёхмерную плотность облака в мировой точке.
 * Никаких плоских пересечений с одной текстурной плоскостью здесь нет.
 */
float cloudDensity(vec3 worldPosition, out float biologicalRidge, out float heightFraction) {
    heightFraction = clamp(
        (worldPosition.y - CLOUD_BOTTOM) / (CLOUD_TOP - CLOUD_BOTTOM),
        0.0,
        1.0
    );

    float shape = cloudHeightShape(heightFraction);
    if (shape <= 0.001) {
        biologicalRidge = 0.0;
        return 0.0;
    }

    vec3 wrappedWorld = mod(worldPosition, 8192.0);
    vec2 stormCell = mod(wrappedWorld.xz + 460.0, 920.0) - 460.0;
    float radius = length(stormCell);
    float swirlStrength = 1.0 - smoothstep(35.0, 520.0, radius);
    float swirlAngle = swirlStrength * (1.25 + heightFraction * 1.8) - Time * 0.014;
    vec2 swirledXZ = rotation(swirlAngle) * stormCell;

    vec2 cellOrigin = wrappedWorld.xz - stormCell;
    vec2 swirledWorld = cellOrigin + swirledXZ;
    vec3 samplePosition = vec3(
        swirledWorld.x * 0.00365,
        wrappedWorld.y * 0.0105,
        swirledWorld.y * 0.00365
    );

    vec3 wind = vec3(Time * 0.020, Time * 0.0035, -Time * 0.013);
    float broadWeather = noise2(wrappedWorld.xz * 0.00072 + vec2(Time * 0.004, -Time * 0.0025));
    float domainWarp = noise3(samplePosition * 0.53 + wind * 0.45);
    vec3 warped = samplePosition + wind + vec3(domainWarp * 0.72, 0.0, domainWarp * 0.54);

    float body = fbm3(warped);
    float detail = noise3(warped * 3.25 + vec3(8.4, -Time * 0.011, 3.7));
    float verticalBillow = 1.0 - abs(heightFraction * 2.0 - 1.0);

    float rawDensity = body * 0.77 + detail * 0.20;
    rawDensity += broadWeather * 0.22 + verticalBillow * 0.20 + swirlStrength * 0.08;
    rawDensity -= 0.69;

    float density = smoothstep(0.0, 0.36, rawDensity) * shape;

    float folded = 1.0 - abs(detail * 2.0 - 1.0);
    biologicalRidge = pow(clamp(folded, 0.0, 1.0), 7.0)
            * density
            * smoothstep(0.34, 0.80, body + broadWeather * 0.18);

    return density;
}

bool intersectCloudVolume(vec3 rayOrigin, vec3 rayDirection, out float nearDistance, out float farDistance) {
    if (abs(rayDirection.y) < 0.0015) {
        nearDistance = 0.0;
        farDistance = 0.0;
        return false;
    }

    float bottomHit = (CLOUD_BOTTOM - rayOrigin.y) / rayDirection.y;
    float topHit = (CLOUD_TOP - rayOrigin.y) / rayDirection.y;
    nearDistance = max(min(bottomHit, topHit), 0.0);
    farDistance = min(max(bottomHit, topHit), MAX_CLOUD_DISTANCE);
    return farDistance > nearDistance;
}

vec3 baseStormSky(vec3 direction) {
    float up = clamp(direction.y * 0.5 + 0.5, 0.0, 1.0);
    float horizon = pow(clamp(1.0 - abs(direction.y), 0.0, 1.0), 2.3);
    float lowerSky = smoothstep(-0.42, 0.08, direction.y);

    vec3 nadirColor = vec3(0.0012, 0.0060, 0.0065);
    vec3 horizonColor = vec3(0.0045, 0.0500, 0.0370);
    vec3 zenithColor = vec3(0.0008, 0.0065, 0.0090);

    vec3 color = mix(nadirColor, horizonColor, lowerSky);
    color = mix(color, zenithColor, pow(up, 0.72));
    color += vec3(0.002, 0.030, 0.020) * horizon;
    return color;
}

void main() {
    vec4 viewPosition = InvProjMat * vec4(screenPosition, 1.0, 1.0);
    vec3 viewDirection = normalize(viewPosition.xyz / max(abs(viewPosition.w), 0.0001));
    vec3 worldDirection = normalize((InvViewMat * vec4(viewDirection, 0.0)).xyz);

    vec3 color = baseStormSky(worldDirection);

    vec3 flashVector = FlashPosition - CameraPosition;
    vec3 flashDirection = length(flashVector) > 0.01
            ? normalize(flashVector)
            : vec3(0.0, 1.0, 0.0);
    float flashAlignment = max(dot(worldDirection, flashDirection), 0.0);
    float broadSkyFlash = pow(flashAlignment, 8.0) * 0.18
            + pow(flashAlignment, 42.0) * 1.15;
    color += vec3(0.060, 0.600, 0.235) * broadSkyFlash * FlashIntensity;

    float rayStart;
    float rayEnd;
    if (intersectCloudVolume(CameraPosition, worldDirection, rayStart, rayEnd)) {
        float rayLength = rayEnd - rayStart;
        float stepLength = rayLength / float(CLOUD_STEPS);
        float jitter = hash21(screenPosition * 913.17 + vec2(Time * 0.017, -Time * 0.011));
        float travel = rayStart + stepLength * jitter;

        vec3 accumulatedColor = vec3(0.0);
        float accumulatedAlpha = 0.0;
        float previousDensity = 0.0;

        for (int stepIndex = 0; stepIndex < CLOUD_STEPS; stepIndex++) {
            vec3 sampleWorld = CameraPosition + worldDirection * travel;
            float biologicalRidge;
            float heightFraction;
            float density = cloudDensity(sampleWorld, biologicalRidge, heightFraction);

            if (density > 0.001) {
                float frontEdge = clamp((density - previousDensity) * 3.4 + 0.48, 0.0, 1.0);
                float depthFade = exp(-max(travel - rayStart, 0.0) * 0.00075);
                float verticalLight = mix(0.22, 0.82, heightFraction);
                float internalPulse = 0.86 + 0.14 * sin(
                    Time * 0.42 + sampleWorld.x * 0.010 + sampleWorld.z * 0.007
                );

                vec3 cloudShadow = vec3(0.0025, 0.0140, 0.0150);
                vec3 cloudBody = vec3(0.0080, 0.0610, 0.0490);
                vec3 cloudTop = vec3(0.0210, 0.1550, 0.0920);
                vec3 sampleColor = mix(cloudShadow, cloudBody, verticalLight);
                sampleColor = mix(sampleColor, cloudTop, frontEdge * 0.42 * depthFade);

                float flashDistance = length((sampleWorld - FlashPosition) * vec3(0.0042, 0.0022, 0.0042));
                float localFlash = exp(-flashDistance * flashDistance * 1.65) * FlashIntensity;
                sampleColor += vec3(0.080, 0.820, 0.310) * localFlash * (0.35 + density * 0.85);
                sampleColor += vec3(0.050, 0.760, 0.280)
                        * biologicalRidge
                        * internalPulse
                        * (0.45 + localFlash * 0.85);

                float extinction = density * stepLength * 0.020;
                float sampleAlpha = 1.0 - exp(-extinction);
                float remaining = 1.0 - accumulatedAlpha;
                accumulatedColor += remaining * sampleColor * sampleAlpha;
                accumulatedAlpha += remaining * sampleAlpha;
            }

            previousDensity = density;
            travel += stepLength;

            if (accumulatedAlpha > 0.985) {
                break;
            }
        }

        // accumulatedColor уже хранится в premultiplied-виде.
        color = color * (1.0 - accumulatedAlpha) + accumulatedColor;

        float underCloudDarkening = accumulatedAlpha * (1.0 - max(worldDirection.y, 0.0)) * 0.30;
        color *= 1.0 - underCloudDarkening;
    }

    float horizonHaze = pow(clamp(1.0 - abs(worldDirection.y), 0.0, 1.0), 3.0);
    color += vec3(0.003, 0.042, 0.026) * horizonHaze * (0.25 + Intensity * 0.35);

    // При активном биовыбросе Intensity = 1, поэтому солнце, луна и звёзды полностью закрыты.
    fragColor = vec4(max(color, vec3(0.0)), clamp(Intensity, 0.0, 1.0));
}
