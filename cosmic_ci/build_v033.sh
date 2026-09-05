#!/usr/bin/env bash
set -euo pipefail

REPO="$GITHUB_WORKSPACE"
WORK=/tmp/cosmic
ROOT="$WORK/CosmicExperiments"
DIST=/tmp/dist
rm -rf "$WORK" "$DIST"
mkdir -p "$WORK" "$DIST"

# Standalone v0.1 project + wrapper.
git -C "$REPO" show 0255e907754e97194be6daa73457abbe1e2dea16:cosmic_ci/payload.part00 > /tmp/p0
git -C "$REPO" show 0255e907754e97194be6daa73457abbe1e2dea16:cosmic_ci/payload.part01 > /tmp/p1
cat /tmp/p0 /tmp/p1 | tr -d '\n\r' | base64 --decode > /tmp/cosmic.tar.gz
tar -xzf /tmp/cosmic.tar.gz -C "$WORK"
cp "$REPO/gradlew" "$REPO/gradlew.bat" "$ROOT/"
cp -r "$REPO/gradle" "$ROOT/"
chmod +x "$ROOT/gradlew"

# v0.2 realism base.
cat "$REPO"/cosmic_ci/v020_xz.part* | tr -d '\n\r' | base64 --decode > /tmp/cosmic-v020.tar.xz
tar -xJf /tmp/cosmic-v020.tar.xz -C "$WORK"

# v0.3 living system.
cat "$REPO"/cosmic_ci/v030_patch.part00 "$REPO"/cosmic_ci/v030_patch.part01 \
    "$REPO"/cosmic_ci/v030_patch.part02 "$REPO"/cosmic_ci/v030_patch.part03 \
    "$REPO"/cosmic_ci/v030_patch.part04 "$REPO"/cosmic_ci/v030_patch.part05 \
    | tr -d '\n\r' | base64 --decode | xz -dc > /tmp/cosmic-v030.patch
(cd "$ROOT" && patch --batch --forward -p5 < /tmp/cosmic-v030.patch)

# NeoForge 1.21.1 API compatibility carried by prior verified builds.
python - <<'PY'
from pathlib import Path
root=Path('/tmp/cosmic/CosmicExperiments')
p=root/'src/main/java/neo/z_mods/cosmicexperiment/SingularitySystem.java'
s=p.read_text(encoding='utf-8')
s=s.replace('level.getEntities(null, box, entity -> entity.isAlive()', 'level.getEntities((Entity) null, box, entity -> entity.isAlive()')
s=s.replace('player.hurtServer(level, level.damageSources().magic(), distance < 7.0 ? 9.0F : 4.0F);', 'player.hurt(level.damageSources().magic(), distance < 7.0 ? 9.0F : 4.0F);')
p.write_text(s,encoding='utf-8')
p=root/'src/main/java/neo/z_mods/cosmicexperiment/HologramSystem.java'
p.write_text(p.read_text(encoding='utf-8').replace('        marker.setMarker(true);\n',''),encoding='utf-8')
p=root/'src/main/java/neo/z_mods/cosmicexperiment/PlanetTerrainHandler.java'
s=p.read_text(encoding='utf-8')
s=s.replace('        if (!(event.getChunk().getLevel() instanceof ServerLevel level)) return;\n', '        if (!(event.getChunk() instanceof LevelChunk chunk)) return;\n        if (!(chunk.getLevel() instanceof ServerLevel level)) return;\n')
s=s.replace('        sculpt(event.getChunk(), body);','        sculpt(chunk, body);')
p.write_text(s,encoding='utf-8')
PY

# v0.3 item icons.
python -m pip install --disable-pip-version-check pillow
mkdir -p "$ROOT/tools"
cp "$REPO/cosmic_ci/generate_v030_item_textures.py" "$ROOT/tools/generate_v030_item_textures.py"
python "$ROOT/tools/generate_v030_item_textures.py"

# v0.3.1 runtime renderer safety.
tr -d '\n\r' < "$REPO/cosmic_ci/v031_overlay.b64" | base64 --decode > /tmp/cosmic-v031.tar.xz
tar -xJf /tmp/cosmic-v031.tar.xz -C "$WORK"

# v0.3.2 orbit pause + distant visual body scale.
cat "$REPO"/cosmic_ci/v032_overlay.part00 "$REPO"/cosmic_ci/v032_overlay.part01 \
    "$REPO"/cosmic_ci/v032_overlay.part02 "$REPO"/cosmic_ci/v032_overlay.part03 \
    "$REPO"/cosmic_ci/v032_overlay.part04 | tr -d '\n\r' | base64 --decode > /tmp/cosmic-v032.tar.xz
tar -xJf /tmp/cosmic-v032.tar.xz -C "$WORK"

# v0.3.3 configurable J supersonic flight + fractional orbit speed.
tr -d '\n\r' < "$REPO/cosmic_ci/v033_overlay.b64" | base64 --decode > /tmp/cosmic-v033.tar.xz
tar -xJf /tmp/cosmic-v033.tar.xz -C "$ROOT"

# Integrity + behavior checks before Gradle.
python - <<'PY'
from pathlib import Path
import json
root=Path('/tmp/cosmic/CosmicExperiments')
assert 'mod_version=0.3.3' in (root/'gradle.properties').read_text(encoding='utf-8')
commands=(root/'src/main/java/neo/z_mods/cosmicexperiment/CosmicCommands.java').read_text(encoding='utf-8')
assert 'literal("solar_pause")' in commands
assert 'literal("solar_speed")' in commands
keys=(root/'src/main/java/neo/z_mods/cosmicexperiment/client/CosmicKeyMappings.java').read_text(encoding='utf-8')
assert 'GLFW.GLFW_KEY_J' in keys and 'RegisterKeyMappingsEvent' in keys
profile=(root/'src/main/java/neo/z_mods/cosmicexperiment/SupersonicFlightProfile.java').read_text(encoding='utf-8')
assert '4048.0' in profile
travel=(root/'src/main/java/neo/z_mods/cosmicexperiment/SpaceTravelManager.java').read_text(encoding='utf-8')
assert 'SupersonicFlightSystem.tick(player)' in travel
net=(root/'src/main/java/neo/z_mods/cosmicexperiment/network/ModNetworking.java').read_text(encoding='utf-8')
assert 'playToServer(SupersonicTogglePacket.TYPE' in net
body=(root/'src/main/java/neo/z_mods/cosmicexperiment/CelestialBody.java').read_text(encoding='utf-8')
assert 'OrbitControl.orbitDays(gameTime)' in body
solar=(root/'src/main/resources/assets/cosmicexperiment/shaders/core/solar_system.fsh').read_text(encoding='utf-8')
assert 'visualBodyScale' in solar and 'pausedFloor' in solar
ru=json.loads((root/'src/main/resources/assets/cosmicexperiment/lang/ru_ru.json').read_text(encoding='utf-8'))
assert ru['key.cosmicexperiment.supersonic_flight']=='Сверхзвуковой полёт'
print('v0.3.3 static integrity checks: PASS')
PY

TMP=$(mktemp -d)
javac -d "$TMP" \
  "$ROOT/src/main/java/neo/z_mods/cosmicexperiment/AstronomyClock.java" \
  "$ROOT/src/main/java/neo/z_mods/cosmicexperiment/OrbitRateState.java" \
  "$ROOT/src/main/java/neo/z_mods/cosmicexperiment/SupersonicFlightProfile.java" \
  "$ROOT/src/test/java/neo/z_mods/cosmicexperiment/V033BehaviorSmokeTest.java"
java -cp "$TMP" neo.z_mods.cosmicexperiment.V033BehaviorSmokeTest
rm -rf "$TMP"

cd "$ROOT"
./gradlew build --stacktrace

cp build/libs/*.jar "$DIST/CosmicExperiments-v0.3.3.jar"
cd "$WORK"
zip -qr "$DIST/CosmicExperiments-v0.3.3-sources.zip" CosmicExperiments \
  -x 'CosmicExperiments/.gradle/*' 'CosmicExperiments/build/*' 'CosmicExperiments/run/*'
cat > "$DIST/README.txt" <<'TXT'
Cosmic Experiments v0.3.3 — standalone NeoForge 1.21.1 mod
- Supersonic flight is a configurable Minecraft keybind, default J.
- Continuous movement only: no teleport calls are used by the supersonic drive.
- Speed ramps up to ~4096 blocks/tick in deep space and automatically brakes near celestial bodies.
- /solar_speed <0..10> changes orbital revolution speed; e.g. 0.1 = ten times slower, 0.01 = one hundred times slower.
- /solar_pause still toggles complete orbital revolution freeze/resume.
- Axial rotation of planets never stops when orbit speed is changed or paused.
- v0.3.2 distant visual planet enlargement is preserved.
TXT
