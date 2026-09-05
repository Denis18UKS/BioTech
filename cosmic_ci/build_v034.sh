#!/usr/bin/env bash
set -euo pipefail

REPO="$GITHUB_WORKSPACE"
# Reconstruct and verify the exact standalone v0.3.3 baseline first.
bash "$REPO/cosmic_ci/build_v033.sh"

ROOT=/tmp/cosmic/CosmicExperiments
DIST=/tmp/dist
rm -rf "$DIST"
mkdir -p "$DIST"

# v0.3.4 live celestial bodies in planetary surface skies.
tr -d '\n\r' < "$REPO/cosmic_ci/v034_overlay.b64" | base64 --decode > /tmp/cosmic-v034.tar.xz
tar -xJf /tmp/cosmic-v034.tar.xz -C "$ROOT"

python - <<'PY'
from pathlib import Path
import json,re
root=Path('/tmp/cosmic/CosmicExperiments')
assert 'mod_version=0.3.4' in (root/'gradle.properties').read_text(encoding='utf-8')
renderer=(root/'src/main/java/neo/z_mods/cosmicexperiment/client/CosmicShaderRenderer.java').read_text(encoding='utf-8')
shader=(root/'src/main/resources/assets/cosmicexperiment/shaders/core/planet_sky.fsh').read_text(encoding='utf-8')
config=json.loads((root/'src/main/resources/assets/cosmicexperiment/shaders/core/planet_sky.json').read_text(encoding='utf-8'))
json_uniforms={u['name'] for u in config['uniforms']}
shader_uniforms=set(re.findall(r'uniform\s+(?:mat4|float|vec[234])\s+([A-Za-z0-9_]+)\s*;',shader))
assert shader_uniforms == json_uniforms, (shader_uniforms-json_uniforms, json_uniforms-shader_uniforms)
for name in ['Sun','Mercury','Venus','Earth','Moon','Mars','Jupiter','Saturn','Uranus','Neptune']:
    assert f'setSurfaceBody(shader, "{name}"' in renderer
    assert f'{name}Rel' in shader and f'{name}Radius' in shader
assert 'localBody != CelestialBody.EARTH' not in renderer
assert 'observer == CelestialBody.MOON' in renderer
assert 'towardEarth' in renderer
# Preserve v0.3.3 requested controls.
keys=(root/'src/main/java/neo/z_mods/cosmicexperiment/client/CosmicKeyMappings.java').read_text(encoding='utf-8')
commands=(root/'src/main/java/neo/z_mods/cosmicexperiment/CosmicCommands.java').read_text(encoding='utf-8')
flight=(root/'src/main/java/neo/z_mods/cosmicexperiment/SupersonicFlightSystem.java').read_text(encoding='utf-8')
assert 'GLFW.GLFW_KEY_J' in keys
assert 'literal("solar_speed")' in commands and 'literal("solar_pause")' in commands
# Comments may say "No teleport"; reject only actual teleport/dimension-change invocations.
assert not re.search(r'\.(?:teleportTo|teleport|changeDimension)\s*\(', flight)
print('v0.3.4 static celestial-sky contract: PASS')
PY

TMP=$(mktemp -d)
javac -d "$TMP" \
  "$ROOT/src/main/java/neo/z_mods/cosmicexperiment/CelestialSkyMath.java" \
  "$ROOT/src/test/java/neo/z_mods/cosmicexperiment/V034CelestialSkySmokeTest.java"
java -cp "$TMP" neo.z_mods.cosmicexperiment.V034CelestialSkySmokeTest
rm -rf "$TMP"

cd "$ROOT"
./gradlew build --stacktrace

cp build/libs/*.jar "$DIST/CosmicExperiments-v0.3.4.jar"
cd /tmp/cosmic
zip -qr "$DIST/CosmicExperiments-v0.3.4-sources.zip" CosmicExperiments \
  -x 'CosmicExperiments/.gradle/*' 'CosmicExperiments/build/*' 'CosmicExperiments/run/*'
cp "$ROOT/README-v0.3.4.txt" "$DIST/README-v0.3.4.txt"
