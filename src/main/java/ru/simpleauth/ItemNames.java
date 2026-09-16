package ru.simpleauth;

import java.util.HashMap;
import java.util.Map;

/**
 * Словарь английский_id -> русское название, для поиска в магазине и
 * отображения предметов. Честно: тут не все ~1500 предметов игры, а
 * несколько сотен самых ходовых (инструменты, оружие, броня по всем
 * материалам, еда, частые блоки). Для того, чего нет в словаре, магазин
 * просто продолжает работать под английским id — как было раньше.
 */
public class ItemNames {

    public static final Map<String, String> RU = build();

    private ItemNames() {
    }

    public static String of(String path) {
        return RU.get(path);
    }

    private static Map<String, String> build() {
        Map<String, String> m = new HashMap<>();

        // --- инструменты и оружие по материалам ---
        String[][] materials = {
                {"wooden", "Деревянный", "Деревянная", "Деревянное"},
                {"stone", "Каменный", "Каменная", "Каменное"},
                {"golden", "Золотой", "Золотая", "Золотое"},
                {"iron", "Железный", "Железная", "Железное"},
                {"diamond", "Алмазный", "Алмазная", "Алмазное"},
                {"netherite", "Незеритовый", "Незеритовая", "Незеритовое"},
        };
        String[][] tools = {
                {"sword", "%s меч", "m"},
                {"pickaxe", "%s кирка", "f"},
                {"axe", "%s топор", "m"},
                {"shovel", "%s лопата", "f"},
                {"hoe", "%s мотыга", "f"},
        };
        for (String[] mat : materials) {
            for (String[] tool : tools) {
                String gender = tool[2];
                String adj = gender.equals("m") ? mat[1] : gender.equals("f") ? mat[2] : mat[3];
                m.put(mat[0] + "_" + tool[0], String.format(tool[1], adj));
            }
        }

        // --- броня по материалам (+ кожа и кольчуга отдельно) ---
        String[][] armorMats = {
                {"leather", "Кожаный", "Кожаная", "Кожаное"},
                {"chainmail", "Кольчужный", "Кольчужная", "Кольчужное"},
                {"golden", "Золотой", "Золотая", "Золотое"},
                {"iron", "Железный", "Железная", "Железное"},
                {"diamond", "Алмазный", "Алмазная", "Алмазное"},
                {"netherite", "Незеритовый", "Незеритовая", "Незеритовое"},
        };
        String[][] armorPieces = {
                {"helmet", "%s шлем", "m"},
                {"chestplate", "%s нагрудник", "m"},
                {"leggings", "%s поножи", "pl"},
                {"boots", "%s ботинки", "pl"},
        };
        for (String[] mat : armorMats) {
            for (String[] piece : armorPieces) {
                String gender = piece[2];
                String adj = gender.equals("m") ? mat[1] : gender.equals("pl") ? mat[1] : mat[3];
                m.put(mat[0] + "_" + piece[0], String.format(piece[1], adj));
            }
        }

        // --- еда ---
        m.put("apple", "Яблоко");
        m.put("baked_potato", "Печёная картошка");
        m.put("beef", "Сырая говядина");
        m.put("beetroot", "Свёкла");
        m.put("beetroot_soup", "Свекольный суп");
        m.put("bread", "Хлеб");
        m.put("cake", "Торт");
        m.put("carrot", "Морковь");
        m.put("chicken", "Сырая курица");
        m.put("chorus_fruit", "Плод хоруса");
        m.put("cooked_beef", "Стейк");
        m.put("cooked_chicken", "Жареная курица");
        m.put("cooked_cod", "Жареная треска");
        m.put("cooked_mutton", "Жареная баранина");
        m.put("cooked_porkchop", "Жареная свинина");
        m.put("cooked_rabbit", "Жареный кролик");
        m.put("cooked_salmon", "Жареный лосось");
        m.put("cookie", "Печенье");
        m.put("dried_kelp", "Сушёные водоросли");
        m.put("egg", "Яйцо");
        m.put("golden_apple", "Золотое яблоко");
        m.put("golden_carrot", "Золотая морковь");
        m.put("honey_bottle", "Бутылка мёда");
        m.put("melon_slice", "Долька арбуза");
        m.put("mushroom_stew", "Грибной суп");
        m.put("mutton", "Сырая баранина");
        m.put("poisonous_potato", "Ядовитая картошка");
        m.put("porkchop", "Сырая свинина");
        m.put("potato", "Картошка");
        m.put("pumpkin_pie", "Тыквенный пирог");
        m.put("rabbit", "Сырой кролик");
        m.put("rabbit_stew", "Рагу из кролика");
        m.put("rotten_flesh", "Гнилая плоть");
        m.put("spider_eye", "Глаз паука");
        m.put("suspicious_stew", "Странное рагу");
        m.put("sweet_berries", "Сладкие ягоды");
        m.put("glow_berries", "Светящиеся ягоды");
        m.put("cod", "Сырая треска");
        m.put("salmon", "Сырой лосось");
        m.put("tropical_fish", "Тропическая рыба");
        m.put("pufferfish", "Иглобрюх");
        m.put("enchanted_golden_apple", "Зачарованное золотое яблоко");

        // --- частые блоки ---
        m.put("dirt", "Земля");
        m.put("grass_block", "Блок травы");
        m.put("stone", "Камень");
        m.put("cobblestone", "Булыжник");
        m.put("oak_planks", "Дубовые доски");
        m.put("oak_log", "Дубовое бревно");
        m.put("glass", "Стекло");
        m.put("sand", "Песок");
        m.put("gravel", "Гравий");
        m.put("obsidian", "Обсидиан");
        m.put("bedrock", "Коренная порода");
        m.put("netherrack", "Незеррак");
        m.put("end_stone", "Эндерит-камень");
        m.put("iron_block", "Блок железа");
        m.put("gold_block", "Блок золота");
        m.put("diamond_block", "Блок алмаза");
        m.put("emerald_block", "Блок изумруда");
        m.put("netherite_block", "Блок незерита");
        m.put("crafting_table", "Верстак");
        m.put("furnace", "Печь");
        m.put("blast_furnace", "Доменная печь");
        m.put("smoker", "Коптильня");
        m.put("anvil", "Наковальня");
        m.put("enchanting_table", "Стол зачарования");
        m.put("bookshelf", "Книжный шкаф");
        m.put("chest", "Сундук");
        m.put("ender_chest", "Эндер-сундук");
        m.put("shulker_box", "Шалкеровая коробка");
        m.put("barrel", "Бочка");
        m.put("torch", "Факел");
        m.put("lantern", "Фонарь");
        m.put("glowstone", "Светокамень");
        m.put("sea_lantern", "Морской фонарь");
        m.put("redstone", "Редстоун");
        m.put("redstone_block", "Блок редстоуна");
        m.put("redstone_torch", "Редстоуновый факел");
        m.put("repeater", "Повторитель");
        m.put("comparator", "Компаратор");
        m.put("piston", "Поршень");
        m.put("sticky_piston", "Липкий поршень");
        m.put("observer", "Наблюдатель");
        m.put("hopper", "Воронка");
        m.put("dropper", "Раздатчик");
        m.put("dispenser", "Выбрасыватель");
        m.put("tnt", "Динамит");
        m.put("ladder", "Лестница");
        m.put("rail", "Рельсы");
        m.put("powered_rail", "Ускоряющие рельсы");
        m.put("detector_rail", "Рельсы-детектор");
        m.put("water_bucket", "Ведро воды");
        m.put("lava_bucket", "Ведро лавы");
        m.put("bucket", "Ведро");
        m.put("milk_bucket", "Ведро молока");
        m.put("bed", "Кровать");
        m.put("bricks", "Кирпичи");
        m.put("bone_block", "Костяной блок");
        m.put("hay_block", "Тюк сена");
        m.put("wool", "Шерсть");
        m.put("beacon", "Маяк");
        m.put("nether_star", "Звезда Нижнего мира");
        m.put("elytra", "Элитры");
        m.put("totem_of_undying", "Тотем бессмертия");
        m.put("shield", "Щит");
        m.put("bow", "Лук");
        m.put("crossbow", "Арбалет");
        m.put("trident", "Трезубец");
        m.put("arrow", "Стрела");
        m.put("spectral_arrow", "Светящаяся стрела");
        m.put("fishing_rod", "Удочка");
        m.put("shears", "Ножницы");
        m.put("flint_and_steel", "Кремень и огниво");
        m.put("compass", "Компас");
        m.put("clock", "Часы");
        m.put("map", "Карта");
        m.put("saddle", "Сёдло");
        m.put("lead", "Поводок");
        m.put("name_tag", "Бирка с именем");
        m.put("firework_rocket", "Фейерверк");
        m.put("string", "Нить");
        m.put("stick", "Палка");
        m.put("bone", "Кость");
        m.put("bone_meal", "Костная мука");
        m.put("gunpowder", "Порох");
        m.put("blaze_rod", "Жезл ифрита");
        m.put("blaze_powder", "Порошок ифрита");
        m.put("ender_pearl", "Жемчуг Края");
        m.put("ender_eye", "Око Края");
        m.put("nether_star_wand", "Жезл звезды");
        m.put("slime_ball", "Шарик слизи");
        m.put("magma_cream", "Крем-магма");
        m.put("glass_bottle", "Стеклянная бутылка");
        m.put("brewing_stand", "Варочная стойка");
        m.put("cauldron", "Котёл");
        m.put("experience_bottle", "Бутылка опыта");
        m.put("book", "Книга");
        m.put("writable_book", "Книга и перо");
        m.put("written_book", "Написанная книга");
        m.put("enchanted_book", "Зачарованная книга");
        m.put("knowledge_book", "Книга знаний");
        m.put("paper", "Бумага");
        m.put("emerald", "Изумруд");
        m.put("diamond", "Алмаз");
        m.put("gold_ingot", "Золотой слиток");
        m.put("iron_ingot", "Железный слиток");
        m.put("netherite_ingot", "Незеритовый слиток");
        m.put("netherite_scrap", "Незеритовый лом");
        m.put("coal", "Уголь");
        m.put("charcoal", "Древесный уголь");
        m.put("lapis_lazuli", "Лазурит");
        m.put("quartz", "Кварц");
        m.put("clay_ball", "Ком глины");
        m.put("brick", "Кирпич");
        m.put("leather", "Кожа");
        m.put("feather", "Перо");
        m.put("egg_item", "Яйцо");
        m.put("potion", "Зелье");
        m.put("splash_potion", "Взрывное зелье");
        m.put("lingering_potion", "Затяжное зелье");

        return m;
    }
}
