package neo.z_mods.biotech.client.screen;

import neo.z_mods.biotech.network.DnaBlacklistUpdatePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Красивый поисковый список существ, у которых запрещено извлекать ДНК. */
public class DnaBlacklistScreen extends Screen {
    private static final int ROW_HEIGHT = 20;

    private final Screen parent;
    private final Set<String> excluded;
    private final boolean canEdit;
    private final List<Entry> allEntries = new ArrayList<>();
    private final List<Entry> visibleEntries = new ArrayList<>();

    private EditBox search;
    private int scrollRows;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;

    public DnaBlacklistScreen(Screen parent, Set<String> excluded, boolean canEdit) {
        super(Component.literal("Исключения извлечения ДНК"));
        this.parent = parent;
        this.excluded = new LinkedHashSet<>(excluded);
        this.canEdit = canEdit;

        BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
                .map(entry -> new Entry(entry.getKey().location(), entry.getValue()))
                .filter(entry -> !entry.id().toString().equals("minecraft:player"))
                .sorted(Comparator.comparing(entry -> entry.name().toLowerCase(Locale.ROOT)))
                .forEach(allEntries::add);
        visibleEntries.addAll(allEntries);
    }

    @Override
    protected void init() {
        panelWidth = Math.min(520, width - 24);
        panelHeight = Math.min(390, height - 24);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;

        search = new EditBox(
                font,
                panelLeft + 16,
                panelTop + 36,
                panelWidth - 32,
                20,
                Component.literal("Поиск существа")
        );
        search.setHint(Component.literal("Введите название или ID..."));
        search.setResponder(value -> rebuildFilter());
        addRenderableWidget(search);

        Button save = Button.builder(Component.literal("Сохранить"), button -> saveAndClose())
                .bounds(panelLeft + panelWidth - 112, panelTop + panelHeight - 30, 96, 20)
                .build();
        save.active = canEdit;
        addRenderableWidget(save);

        addRenderableWidget(Button.builder(Component.literal("Назад"), button -> onClose())
                .bounds(panelLeft + 16, panelTop + panelHeight - 30, 80, 20)
                .build());
    }

    private void rebuildFilter() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        visibleEntries.clear();
        for (Entry entry : allEntries) {
            if (query.isBlank()
                    || entry.name().toLowerCase(Locale.ROOT).contains(query)
                    || entry.id().toString().toLowerCase(Locale.ROOT).contains(query)) {
                visibleEntries.add(entry);
            }
        }
        scrollRows = Math.min(scrollRows, maxScrollRows());
    }

    private void saveAndClose() {
        if (!canEdit) {
            return;
        }
        PacketDistributor.sendToServer(new DnaBlacklistUpdatePacket(Set.copyOf(excluded)));
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC0101715);
        graphics.fill(panelLeft + 4, panelTop + 4, panelLeft + panelWidth + 4, panelTop + panelHeight + 4, 0x70000000);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xF0182320);
        border(graphics, panelLeft, panelTop, panelWidth, panelHeight, 0xFF668078);

        graphics.drawString(font, title, panelLeft + 16, panelTop + 14, 0xFF83F18D, false);
        String info = canEdit
                ? "Отмеченные существа не дают образцы ДНК"
                : "Только оператор сервера может изменять список";
        graphics.drawString(font, info, panelLeft + 210, panelTop + 15,
                canEdit ? 0xFFB9C8C2 : 0xFFFF9E73, false);

        int listTop = panelTop + 66;
        int listBottom = panelTop + panelHeight - 42;
        graphics.fill(panelLeft + 12, listTop - 4, panelLeft + panelWidth - 12, listBottom + 4, 0xFF101815);
        border(graphics, panelLeft + 12, listTop - 4, panelWidth - 24, listBottom - listTop + 8, 0xFF435A53);

        int visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        int end = Math.min(visibleEntries.size(), scrollRows + visibleRows);
        for (int index = scrollRows; index < end; index++) {
            Entry entry = visibleEntries.get(index);
            int row = index - scrollRows;
            int y = listTop + row * ROW_HEIGHT;
            boolean hovered = mouseX >= panelLeft + 16 && mouseX < panelLeft + panelWidth - 16
                    && mouseY >= y && mouseY < y + ROW_HEIGHT - 1;
            boolean checked = excluded.contains(entry.id().toString());

            graphics.fill(panelLeft + 16, y, panelLeft + panelWidth - 16, y + ROW_HEIGHT - 2,
                    hovered ? 0xFF293A35 : (row % 2 == 0 ? 0xFF17231F : 0xFF14201C));

            int checkX = panelLeft + 22;
            int checkY = y + 4;
            graphics.fill(checkX, checkY, checkX + 12, checkY + 12, 0xFF596A64);
            graphics.fill(checkX + 1, checkY + 1, checkX + 11, checkY + 11,
                    checked ? 0xFF45C85E : 0xFF0C1210);
            if (checked) {
                graphics.drawString(font, "✓", checkX + 2, checkY + 1, 0xFFFFFFFF, false);
            }

            graphics.drawString(font, entry.name(), panelLeft + 42, y + 3,
                    checked ? 0xFFFFB26F : 0xFFE4ECE8, false);
            int idWidth = font.width(entry.id().toString());
            graphics.drawString(font, entry.id().toString(), panelLeft + panelWidth - 22 - idWidth, y + 4,
                    0xFF70847C, false);
        }

        graphics.drawString(font,
                "Запрещено: " + excluded.size() + " / " + allEntries.size(),
                panelLeft + 112,
                panelTop + panelHeight - 25,
                0xFF9FB0AA,
                false
        );

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && canEdit) {
            int listTop = panelTop + 66;
            int listBottom = panelTop + panelHeight - 42;
            if (mouseX >= panelLeft + 16 && mouseX < panelLeft + panelWidth - 16
                    && mouseY >= listTop && mouseY < listBottom) {
                int row = (int) ((mouseY - listTop) / ROW_HEIGHT);
                int index = scrollRows + row;
                if (index >= 0 && index < visibleEntries.size()) {
                    String id = visibleEntries.get(index).id().toString();
                    if (!excluded.add(id)) {
                        excluded.remove(id);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D) {
            scrollRows = Math.max(0, Math.min(maxScrollRows(), scrollRows - (int) Math.signum(scrollY) * 2));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int maxScrollRows() {
        int listHeight = panelHeight - 108;
        int visibleRows = Math.max(1, listHeight / ROW_HEIGHT);
        return Math.max(0, visibleEntries.size() - visibleRows);
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private record Entry(ResourceLocation id, EntityType<?> type) {
        String name() {
            return type.getDescription().getString();
        }
    }
}
