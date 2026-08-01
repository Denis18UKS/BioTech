package neo.z_mods.biotech;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Отдельный серверный конфиг существ, у которых нельзя извлекать ДНК. */
public final class DnaBlacklistConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("biotech-dna-blacklist.json");
    private static final Set<String> EXCLUDED = new LinkedHashSet<>();

    private static final List<String> DEFAULTS = List.of(
            "minecraft:armor_stand",
            "minecraft:iron_golem",
            "minecraft:snow_golem"
    );

    public static synchronized void load() {
        EXCLUDED.clear();
        if (!Files.exists(FILE)) {
            EXCLUDED.addAll(DEFAULTS);
            save();
            return;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), JsonObject.class);
            JsonArray array = root == null ? null : root.getAsJsonArray("excluded_entities");
            if (array != null) {
                array.forEach(element -> {
                    String id = element.getAsString();
                    if (ResourceLocation.tryParse(id) != null) {
                        EXCLUDED.add(id);
                    }
                });
            }
        } catch (Exception exception) {
            BioTech.LOGGER.error("Не удалось прочитать {}. Используются значения по умолчанию.", FILE, exception);
            EXCLUDED.addAll(DEFAULTS);
        }
    }

    public static synchronized void replace(Collection<String> ids) {
        EXCLUDED.clear();
        ids.stream()
                .filter(id -> ResourceLocation.tryParse(id) != null)
                .sorted()
                .forEach(EXCLUDED::add);
        save();
    }

    public static synchronized boolean isExcluded(EntityType<?> type) {
        return EXCLUDED.contains(EntityType.getKey(type).toString());
    }

    public static synchronized Set<String> snapshot() {
        return Set.copyOf(EXCLUDED);
    }

    public static synchronized void save() {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        new TreeSet<>(EXCLUDED).forEach(array::add);
        root.add("excluded_entities", array);
        root.addProperty("description", "Существа из этого списка не могут давать образцы ДНК BioTech");
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            BioTech.LOGGER.error("Не удалось сохранить {}", FILE, exception);
        }
    }

    private DnaBlacklistConfig() {
    }
}
