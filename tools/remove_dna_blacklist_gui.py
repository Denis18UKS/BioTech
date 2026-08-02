#!/usr/bin/env python3
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1] if Path(__file__).parent.name == 'tools' else Path.cwd()


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding='utf-8')


def write(rel: str, text: str) -> None:
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding='utf-8')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f'Expected block not found: {label}')
    return text.replace(old, new, 1)


write('src/main/java/neo/z_mods/biotech/DnaExtractionRules.java', '''package neo.z_mods.biotech;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/**
 * Fixed DNA extraction rules shared by client and server.
 *
 * The editable blacklist GUI was removed deliberately. Biological living
 * entities and players are accepted, while obvious artificial constructs are
 * rejected without config synchronization or a client overlay.
 */
public final class DnaExtractionRules {
    private static final Set<EntityType<?>> NON_BIOLOGICAL_TYPES = Set.of(
            EntityType.ARMOR_STAND,
            EntityType.IRON_GOLEM,
            EntityType.SNOW_GOLEM
    );

    public static boolean isExtractionForbidden(LivingEntity target) {
        if (target instanceof Player) {
            return false;
        }
        return NON_BIOLOGICAL_TYPES.contains(target.getType());
    }

    private DnaExtractionRules() {
    }
}
''')

bio = read('src/main/java/neo/z_mods/biotech/BioTech.java')
bio = bio.replace('        DnaBlacklistConfig.load();\n\n', '')
bio = bio.replace('        NeoForge.EVENT_BUS.register(new DnaBlacklistSyncHandler());\n', '')
write('src/main/java/neo/z_mods/biotech/BioTech.java', bio)

injector_path = 'src/main/java/neo/z_mods/biotech/item/DnaInjectorItem.java'
injector = read(injector_path)
injector = injector.replace('import neo.z_mods.biotech.DnaBlacklistConfig;\n', 'import neo.z_mods.biotech.DnaExtractionRules;\n')
injector = injector.replace('import neo.z_mods.biotech.network.ClientDnaBlacklistData;\n', '')
injector = replace_once(injector, '''        if (player.level().isClientSide()
                && !(target instanceof Player)
                && ClientDnaBlacklistData.isExcluded(EntityType.getKey(target.getType()).toString())) {
            return InteractionResult.FAIL;
        }
        if (!player.level().isClientSide()) {
            if (!(target instanceof Player) && DnaBlacklistConfig.isExcluded(target.getType())) {
                player.displayClientMessage(
                        Component.literal("У этого существа нельзя извлечь ДНК").withStyle(ChatFormatting.RED),
                        true
                );
                return InteractionResult.FAIL;
            }
''', '''        if (DnaExtractionRules.isExtractionForbidden(target)) {
            if (!player.level().isClientSide()) {
                player.displayClientMessage(
                        Component.literal("У этого существа нет пригодной для извлечения ДНК")
                                .withStyle(ChatFormatting.RED),
                        true
                );
            }
            return InteractionResult.FAIL;
        }
        if (!player.level().isClientSide()) {
''', 'injector beginExtraction blacklist')
injector = injector.replace(
        '|| (!(target instanceof Player) && DnaBlacklistConfig.isExcluded(target.getType()))) {',
        '|| DnaExtractionRules.isExtractionForbidden(target)) {'
)
write(injector_path, injector)

network_path = 'src/main/java/neo/z_mods/biotech/network/ModNetworking.java'
network = read(network_path)
for label, block in [
    ('sync packet', '''        registrar.playToClient(
                DnaBlacklistSyncPacket.TYPE,
                DnaBlacklistSyncPacket.STREAM_CODEC,
                DnaBlacklistSyncPacket::handle
        );

'''),
    ('request packet', '''        registrar.playToServer(
                DnaBlacklistRequestPacket.TYPE,
                DnaBlacklistRequestPacket.STREAM_CODEC,
                DnaBlacklistRequestPacket::handle
        );

'''),
    ('update packet', '''        registrar.playToServer(
                DnaBlacklistUpdatePacket.TYPE,
                DnaBlacklistUpdatePacket.STREAM_CODEC,
                DnaBlacklistUpdatePacket::handle
        );

'''),
]:
    network = replace_once(network, block, '', label)
write(network_path, network)

for rel in [
    'src/main/java/neo/z_mods/biotech/DnaBlacklistConfig.java',
    'src/main/java/neo/z_mods/biotech/DnaBlacklistSyncHandler.java',
    'src/main/java/neo/z_mods/biotech/client/DnaBlacklistClientHandler.java',
    'src/main/java/neo/z_mods/biotech/client/screen/DnaBlacklistScreen.java',
    'src/main/java/neo/z_mods/biotech/network/ClientDnaBlacklistData.java',
    'src/main/java/neo/z_mods/biotech/network/DnaBlacklistRequestPacket.java',
    'src/main/java/neo/z_mods/biotech/network/DnaBlacklistSyncPacket.java',
    'src/main/java/neo/z_mods/biotech/network/DnaBlacklistUpdatePacket.java',
]:
    path = ROOT / rel
    if path.exists():
        path.unlink()

for rel in [
    'src/main/resources/assets/biotech/lang/ru_ru.json',
    'src/main/resources/assets/biotech/lang/en_us.json',
]:
    path = ROOT / rel
    data = json.loads(path.read_text(encoding='utf-8'))
    data.pop('key.biotech.open_dna_blacklist', None)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

note = '''

## Исправление списка исключений ДНК
- экран/оверлей настройки запретов и клавиша его открытия удалены;
- удалена клиент-серверная синхронизация редактируемого blacklist;
- запрет определяется одинаковой фиксированной логикой на клиенте и сервере;
- ДНК нельзя извлекать из стойки для брони, железного и снежного големов;
- игроки и остальные живые существа остаются доступны.
'''
for rel in ['BUILD-INFO.txt', 'FEATURES_VISUAL_DNA_PIPELINE.md']:
    path = ROOT / rel
    if path.exists():
        text = path.read_text(encoding='utf-8')
        if 'Исправление списка исключений ДНК' not in text:
            path.write_text(text.rstrip() + note, encoding='utf-8')

print('Removed DNA blacklist GUI and installed fixed extraction rules.')
