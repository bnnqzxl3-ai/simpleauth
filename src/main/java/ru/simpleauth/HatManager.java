package ru.simpleauth;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.UUID;

/**
 * Декоративные шляпы — головы игроков с произвольной текстурой.
 * Работают на ванильных клиентах, ресурспак не нужен.
 */
public class HatManager {

    /** Пометка, по которой отличаем нашу шляпу от настоящего шлема. */
    private static final String MARK = "\u2691 ";

    private final AuthManager manager;

    public HatManager(AuthManager manager) {
        this.manager = manager;
    }

    private Config config() {
        return manager.config();
    }

    // -------------------------------------------------------------- каталог

    public boolean has(String name) {
        return config().hats != null && config().hats.containsKey(key(name));
    }

    public void add(String name, String texture) {
        config().hats.put(key(name), texture);
        config().save();
    }

    public boolean remove(String name) {
        boolean removed = config().hats.remove(key(name)) != null;
        if (removed) config().save();
        return removed;
    }

    public String list() {
        if (config().hats == null || config().hats.isEmpty()) return "";
        return String.join(", ", config().hats.keySet());
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    // --------------------------------------------------------------- ношение

    /** Собирает голову с нужной текстурой. */
    public ItemStack build(String name) {
        String texture = config().hats.get(key(name));
        if (texture == null) return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        try {
            GameProfile profile = new GameProfile(UUID.randomUUID(), "hat");
            profile.getProperties().put("textures", new Property("textures", texture));
            stack.set(DataComponentTypes.PROFILE, new ProfileComponent(profile));
        } catch (Exception e) {
            SimpleAuth.LOGGER.warn("[SimpleAuth] Плохая текстура шляпы «{}»", name, e);
            return ItemStack.EMPTY;
        }

        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(MARK + name).formatted(Formatting.LIGHT_PURPLE)
                        .styled(s -> s.withItalic(false)));
        return stack;
    }

    /** Надевает шляпу. Настоящий шлем, если он был, уходит в инвентарь. */
    public boolean wear(ServerPlayerEntity player, String name) {
        ItemStack hat = build(name);
        if (hat.isEmpty()) return false;

        ItemStack current = player.getEquippedStack(EquipmentSlot.HEAD).copy();
        if (!current.isEmpty() && !isOurHat(current)) {
            if (!player.getInventory().insertStack(current)) {
                player.dropItem(current, false);
            }
        }
        player.equipStack(EquipmentSlot.HEAD, hat);
        return true;
    }

    /**
     * Снимает шляпу. Настоящий шлем не трогаем — иначе легко случайно
     * уничтожить чужую броню.
     */
    public boolean off(ServerPlayerEntity player) {
        ItemStack current = player.getEquippedStack(EquipmentSlot.HEAD);
        if (current.isEmpty() || !isOurHat(current)) return false;
        player.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
        return true;
    }

    private static boolean isOurHat(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(Items.PLAYER_HEAD)) return false;
        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        return name != null && name.getString().startsWith(MARK);
    }
}
