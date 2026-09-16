package ru.simpleauth;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Каталог предметов "бери что хочешь" — команда /cursedshop. Работает как
 * витрина: клик по предмету кладёт указанное количество в инвентарь, сам
 * каталог не расходуется (это не настоящий сундук, а кнопки). Постранично,
 * с поиском (на русском и английском) и переключаемым количеством за клик.
 *
 * Открывается ПОСЛЕ выбора категории (см. ShopCategoryScreen) — category
 * тут только для кнопки "назад" и подписи, список entries уже
 * отфильтрован заранее. Поиск (SEARCH_SLOT) ищет по всем предметам разом,
 * не только внутри текущей категории.
 *
 * Важно: переключение страницы и количества НЕ закрывает и не открывает
 * окно заново — вместо этого содержимое того же самого открытого меню
 * просто обновляется на месте (refreshInPlace). Закрытие/переоткрытие
 * экрана у клиента сбрасывает позицию курсора мыши в центр — это и было
 * тем самым дёрганьем курсора при каждом клике, которое чинили.
 */
public class ShopScreenHandler extends GenericContainerScreenHandler {

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final int PAGE_SIZE = 36; // первые 4 ряда — каталог
    private static final int BACK_SLOT = 36;
    private static final int PREV_SLOT = 37;
    private static final int QTY_SLOT = 38;
    private static final int NEXT_SLOT = 39;
    private static final int SEARCH_SLOT = 40;
    private static final int TRASH_SLOT = 42;
    private static final int PAGE_INFO_SLOT = 44;
    private static final int[] QUANTITIES = {1, 16, 32, 64};

    private static List<Item> cachedItems;

    private final Inventory backing;
    private final List<ShopEntry> entries;
    private final ShopCategory category;
    private int page;
    private int qtyIndex;

    public ShopScreenHandler(int syncId, PlayerInventory playerInventory,
                              List<ShopEntry> entries, int page, int qtyIndex, ShopCategory category) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory,
                buildInventory(entries, page, qtyIndex, category), ROWS);
        this.backing = this.getInventory();
        this.entries = entries;
        this.page = page;
        this.qtyIndex = qtyIndex;
        this.category = category;
    }

    /** Полный список предметов игры (голых Item, не ShopEntry) — используется для сборки категорий. */
    public static List<Item> allItems() {
        if (cachedItems == null) {
            List<Item> list = new ArrayList<>();
            for (Item item : Registries.ITEM) {
                if (item == Items.AIR) continue;
                list.add(item);
            }
            list.sort(Comparator.comparing(item -> Registries.ITEM.getId(item).toString()));
            cachedItems = list;
        }
        return cachedItems;
    }

    private static int totalPages(List<ShopEntry> entries) {
        return Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static SimpleInventory buildInventory(List<ShopEntry> entries, int page, int qtyIndex,
                                                   ShopCategory category) {
        SimpleInventory inv = new SimpleInventory(SIZE);

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            if (idx >= entries.size()) break;
            inv.setStack(i, entries.get(idx).template.copy());
        }

        ItemStack back = new ItemStack(Items.NETHER_STAR);
        back.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("↩ К категориям").formatted(Formatting.LIGHT_PURPLE)
                        .styled(s -> s.withItalic(false)));
        inv.setStack(BACK_SLOT, back);

        ItemStack prev = new ItemStack(Items.ARROW);
        prev.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("← Назад").formatted(Formatting.YELLOW).styled(s -> s.withItalic(false)));
        inv.setStack(PREV_SLOT, prev);

        ItemStack qty = new ItemStack(Items.PAPER);
        qty.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Количество: " + QUANTITIES[qtyIndex])
                        .formatted(Formatting.GOLD, Formatting.BOLD).styled(s -> s.withItalic(false)));
        inv.setStack(QTY_SLOT, qty);

        ItemStack next = new ItemStack(Items.ARROW);
        next.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Вперёд →").formatted(Formatting.YELLOW).styled(s -> s.withItalic(false)));
        inv.setStack(NEXT_SLOT, next);

        ItemStack search = new ItemStack(Items.COMPASS);
        search.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("🔍 Поиск (рус./англ., по всем категориям)").formatted(Formatting.AQUA, Formatting.BOLD)
                        .styled(s -> s.withItalic(false)));
        inv.setStack(SEARCH_SLOT, search);

        ItemStack trash = new ItemStack(Items.BARRIER);
        trash.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("🗑 Уничтожить предмет в руке").formatted(Formatting.RED)
                        .styled(s -> s.withItalic(false)));
        inv.setStack(TRASH_SLOT, trash);

        ItemStack pageInfo = new ItemStack(Items.BOOK);
        String catLabel = category != null ? category.label : "Поиск";
        pageInfo.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(catLabel + " — стр. " + (page + 1) + "/" + totalPages(entries))
                        .formatted(Formatting.GRAY).styled(s -> s.withItalic(false)));
        inv.setStack(PAGE_INFO_SLOT, pageInfo);

        return inv;
    }

    /** Перерисовывает содержимое ТОГО ЖЕ открытого окна — без закрытия/переоткрытия, курсор не дёргается. */
    private void refreshInPlace() {
        SimpleInventory fresh = buildInventory(entries, page, qtyIndex, category);
        for (int i = 0; i < SIZE; i++) {
            backing.setStack(i, fresh.getStack(i));
        }
        this.sendContentUpdates();
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        if (slotIndex < 0) return; // клик мимо окна — просто игнор, ничего ронять не нужно

        if (slotIndex == BACK_SLOT) {
            serverPlayer.closeHandledScreen();
            serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId2, inv2, p2) -> new ShopCategoryScreen(syncId2, inv2, serverPlayer.getServer()),
                    Text.literal("Категории")));
            return;
        }
        if (slotIndex == PREV_SLOT) {
            if (page > 0) {
                page--;
                refreshInPlace();
            }
            return;
        }
        if (slotIndex == NEXT_SLOT) {
            if (page + 1 < totalPages(entries)) {
                page++;
                refreshInPlace();
            }
            return;
        }
        if (slotIndex == QTY_SLOT) {
            qtyIndex = (qtyIndex + 1) % QUANTITIES.length;
            refreshInPlace();
            return;
        }
        if (slotIndex == SEARCH_SLOT) {
            serverPlayer.closeHandledScreen();
            serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId2, inv2, p2) -> new ItemSearchScreen(syncId2, inv2, qtyIndex),
                    Text.literal("Поиск предмета")));
            return;
        }
        if (slotIndex == TRASH_SLOT) {
            ItemStack held = serverPlayer.getMainHandStack();
            if (!held.isEmpty()) {
                serverPlayer.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                serverPlayer.sendMessage(
                        Text.literal("Уничтожено предметов: " + held.getCount())
                                .formatted(Formatting.DARK_GRAY),
                        false);
            }
            return;
        }

        if (slotIndex >= 0 && slotIndex < PAGE_SIZE) {
            int idx = page * PAGE_SIZE + slotIndex;
            if (idx < entries.size()) {
                giveEntry(serverPlayer, entries.get(idx), QUANTITIES[qtyIndex]);
            }
        }
        // остальные слоты (декоративные/пустые, и реальный инвентарь игрока
        // ниже каталога) — намеренно не трогаем, это витрина, а не сундук
    }

    static void giveEntry(ServerPlayerEntity player, ShopEntry entry, int amount) {
        int remaining = amount;
        int maxCount = entry.template.getMaxCount();
        while (remaining > 0) {
            int take = Math.min(remaining, maxCount);
            ItemStack stack = entry.template.copyWithCount(take);
            if (!player.getInventory().insertStack(stack)) {
                player.dropItem(stack, false);
            }
            remaining -= take;
        }
    }
}
