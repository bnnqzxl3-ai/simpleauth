package ru.simpleauth;

import java.util.HashMap;
import java.util.Map;

/** Русские названия ванильных зачарований — для отображения и поиска в каталоге книг. */
public class EnchantmentNames {

    public static final Map<String, String> RU = build();

    private EnchantmentNames() {
    }

    private static Map<String, String> build() {
        Map<String, String> m = new HashMap<>();
        m.put("protection", "Защита");
        m.put("fire_protection", "Огнестойкость");
        m.put("feather_falling", "Мягкое приземление");
        m.put("blast_protection", "Взрывостойкость");
        m.put("projectile_protection", "Защита от снарядов");
        m.put("respiration", "Дыхание");
        m.put("aqua_affinity", "Родство с водой");
        m.put("thorns", "Шипы");
        m.put("depth_strider", "Покоритель глубин");
        m.put("frost_walker", "Ледоход");
        m.put("binding_curse", "Проклятие связывания");
        m.put("soul_speed", "Скорость души");
        m.put("swift_sneak", "Быстрое прокрадывание");
        m.put("sharpness", "Острота");
        m.put("smite", "Разящий клинок");
        m.put("bane_of_arthropods", "Гроза членистоногих");
        m.put("knockback", "Отдача");
        m.put("fire_aspect", "Заряд огня");
        m.put("looting", "Добыча");
        m.put("sweeping_edge", "Широкий клинок");
        m.put("sweeping", "Широкий клинок");
        m.put("efficiency", "Эффективность");
        m.put("silk_touch", "Шёлковое касание");
        m.put("unbreaking", "Прочность");
        m.put("fortune", "Удача");
        m.put("power", "Сила");
        m.put("punch", "Отбрасывание");
        m.put("flame", "Пламя");
        m.put("infinity", "Бесконечность");
        m.put("luck_of_the_sea", "Морская удача");
        m.put("lure", "Приманка");
        m.put("loyalty", "Верность");
        m.put("impaling", "Пронзание");
        m.put("riptide", "Раптура");
        m.put("channeling", "Управление бурей");
        m.put("multishot", "Мультивыстрел");
        m.put("quick_charge", "Быстрая перезарядка");
        m.put("piercing", "Пронзительность");
        m.put("density", "Плотность");
        m.put("breach", "Пролом");
        m.put("wind_burst", "Порыв ветра");
        m.put("mending", "Починка");
        m.put("vanishing_curse", "Проклятие исчезновения");
        return m;
    }
}
