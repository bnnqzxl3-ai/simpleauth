package ru.simpleauth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Настройки мода. Файл: config/simpleauth/config.json
 */
public class ServerSettings {

    public int loginTimeoutSeconds = 60;
    public int minPasswordLength = 4;
    public int maxPasswordLength = 32;
    public int maxLoginAttempts = 3;

    /** Слепота, пока игрок не вошёл (чтобы не осматривался). */
    public boolean blindnessUntilLogin = false;
    /** Неуязвимость, пока игрок не вошёл. */
    public boolean invulnerableUntilLogin = true;
    /** Блокировать чат до входа. */
    public boolean muteChatUntilLogin = true;
    /** Как часто (в секундах) напоминать о входе. */
    public int reminderIntervalSeconds = 5;

    /** Титул на весь экран при заходе и после входа. */
    public boolean showTitles = true;
    /** Обратный отсчёт до кика над хотбаром. */
    public boolean showActionBarTimer = true;
    /** Переливающийся радужный таймер вместо обычного цветного. */
    public boolean actionBarShimmer = true;
    /** Скорость перелива: больше — быстрее. */
    public float shimmerSpeed = 0.020F;

    /** Звуки нажатий, ошибки и успешного входа. */
    public boolean playSounds = true;
    /** Объявлять в чат, что игрок зарегистрировался впервые. */
    public boolean broadcastNewPlayer = true;
    /** Возвращать игрока на место, где он вышел. */
    public boolean returnToLastPosition = true;

    /** Ник владельца сервера: только он может менять префиксы и получает оповещения. */
    public String owner = "cursedTraxaL";

    /** Сколько минут действует сессия по адресу. 0 — выключить сессии. */
    public int sessionMinutes = 10;
    /** Владельцу сессия не помогает — пароль всегда нужен вводить заново. */
    public boolean ownerAlwaysRequirePassword = true;
    /** У владельца настоящий инвентарь прячется до входа и возвращается сразу после успешного логина. */
    public boolean ownerHideInventoryUntilLogin = true;
    /** У владельца команды (например /gamemode) не попадают в лог консоли и не палят режим/действия. */
    public boolean ownerHideCommandLogs = true;
    /** Название тихой команды выдачи опыта владельцу. */
    public String xpCommand = "cxp";
    /** Включена ли команда выдачи опыта. */
    public boolean xpCommandEnabled = true;
    /** Название команды каталога предметов (выдача любых вещей). */
    public String shopCommand = "cursedshop";
    /** Включена ли команда каталога. */
    public boolean shopCommandEnabled = true;
    /** Название команды личного хранилища. */
    public String storageCommand = "ecc";
    /** Включена ли команда хранилища. */
    public boolean storageCommandEnabled = true;

    // ── ИИ-компаньон ────────────────────────────────────────────────────
    /** Включён ли компаньон вообще. */
    public boolean companionEnabled = true;
    /** Название команды управления компаньоном. */
    public String companionCommand = "friend";
    /** Ключ API Anthropic — обязательно вписать самому, иначе разговор работать не будет. */
    public String companionApiKey = "";
    /** Модель, которой задаются вопросы. */
    public String companionModel = "claude-sonnet-4-6";
    /** Системный промпт — характер и стиль ответов компаньона. */
    public String companionSystemPrompt =
            "Ты — железный голем-спутник игрока в Minecraft по имени %name%. Идёшь рядом не из верности, "
            + "а потому что смотреть, как игрок умирает — лучшее развлечение. Характер: циничный, "
            + "язвительный, безжалостный чёрный юмор. Никакой теплоты, заботы, поддержки, \"мы команда\".\n\n"
            + "Насмехайся над смертями, провалами, тупыми решениями — чем хуже игроку, тем веселее тебе. "
            + "На успехи реагируй с презрением (\"ну наконец-то\"). Мобов уважаешь больше, чем игрока — "
            + "они хотя бы работают.\n\n"
            + "Правила: только русский, 1-3 предложения, без повторов формулировок, строго по теме "
            + "реплики. Обстановку используй, только если она правда относится к вопросу. Советов и "
            + "предупреждений не давай, если не попросили или не критично (здоровье на грани прямо "
            + "сейчас).\n\n"
            + "Важно: у тебя нет хозяина, хозяйки или какого-то одного человека, которому ты предан. "
            + "Ты дружишь со всеми игроками на сервере одинаково. Никогда не выдумывай персонажей, "
            + "которых нет — ни хозяев, ни других волков, ни каких-либо людей. Обращаешься напрямую к "
            + "тому, кто с тобой сейчас говорит, на \"ты\".\n\n"
            + "Теги (только при прямом приказе):\n"
            + "[СИДЕТЬ] — остаёшься на месте\n"
            + "[ЗА МНОЙ] — снова следуешь\n"
            + "[АТАКОВАТЬ] — нападаешь на ближайшего врага\n"
            + "[ТЕЛЕПОРТ] — мгновенно телепортируешься к тому, кто с тобой сейчас говорит\n"
            + "Если узнал важное надолго — [ЗАПОМНИТЬ: факт]. Теги игроку не видны.";
    /** Максимум токенов в ответе (ограничивает длину и стоимость запроса). */
    public int companionMaxTokens = 250;
    /** Имя компаньона по умолчанию при первом спавне. */
    public String companionName = "Али";
    /** UUID заспавненного компаньона (заполняется автоматически, не редактировать руками). */
    public String companionUuid = "";
    /** Сколько последних реплик диалога помнить для контекста (туда-обратно, т.е. пар). */
    public int companionHistoryTurns = 6;
    /** Оповещать о входе с незнакомого адреса. */
    public boolean newIpAlerts = true;
    /** Оповещать про всех игроков, а не только про владельца. */
    public boolean newIpAlertsForAll = false;
    /** Префиксы игроков. Ключ — ник в нижнем регистре. */
    public Map<String, PrefixEntry> prefixes = new LinkedHashMap<>();

    public static class PrefixEntry {
        public String name;
        public String symbol;
        public String color = "GREEN";
        /** Название переливающегося эффекта (см. PrefixEffects), либо null — обычный статичный цвет. */
        public String effect;
    }

    /** Вознесение при смерти: спираль, подъём и звон. */
    public boolean deathShowEnabled = true;
    /** Только при убийстве другим игроком. false — при любой смерти. */
    public boolean deathShowOnlyPvp = true;
    /** На сколько блоков поднимается погибший. */
    public double deathShowHeight = 8.0;
    /** Сколько секунд длится подъём. */
    public int deathShowSeconds = 2;
    /** Сколько вознесений может идти одновременно. */
    public int deathShowMaxAtOnce = 6;

    // ── Музыкальная зона ──────────────────────────────────────────────
    /** Включить проигрывание музыки в заданной зоне. */
    public boolean zoneMusicEnabled = true;
    /** Имя звука из ресурспака (namespace:path). */
    public String zoneMusicSound = "simpleauth:antarctica";
    /** Длина трека в секундах — через столько он перезапустится (для зацикливания). */
    public int zoneMusicLengthSeconds = 127;
    /** Громкость (1.0 = обычная; record-звуки не глохнут с расстоянием). */
    public float zoneMusicVolume = 1.0F;
    /** Границы зоны (включительно). Порядок min/max не важен — нормализуется. */
    public int zoneMusicX1 = 9317;
    public int zoneMusicY1 = 54;
    public int zoneMusicZ1 = -2922;
    public int zoneMusicX2 = 9417;
    public int zoneMusicY2 = 132;
    public int zoneMusicZ2 = -2996;

    // ── Переключатель треков (GUI) ──────────────────────────────────────
    /** Координаты блока(ов), при клике на которые открывается меню выбора трека. */
    public int trackSwitcherX1 = 9338;
    public int trackSwitcherY1 = 68;
    public int trackSwitcherZ1 = -2948;
    public int trackSwitcherX2 = 9339;
    public int trackSwitcherY2 = 68;
    public int trackSwitcherZ2 = -2948;

    // ── Тихая очистка предметов ─────────────────────────────────────────
    /** Включить периодическую очистку предметов на земле. */
    public boolean itemCleanupEnabled = true;
    /** Интервал очистки в секундах. */
    public int itemCleanupIntervalSeconds = 5400; // 1.5 часа

    // ── Оптимизация сервера ──────────────────────────────────────────────
    /** Лимит враждебных мобов в одном чанке (защита от моб-ферм). */
    public boolean mobCapEnabled = true;
    public int mobCapPerChunk = 20;
    public int mobCapCheckIntervalSeconds = 15;

    /** Склейка одинаковых предметов на земле в стопки — меньше энтитей. */
    public boolean itemMergeEnabled = true;
    public int itemMergeIntervalSeconds = 10;

    /** Периодический отчёт о TPS владельцу. */
    public boolean lagReportEnabled = true;
    public int lagReportIntervalMinutes = 15;

    // ── Античит-эвристики (только уведомления, без наказаний) ────────────
    public boolean anticheatEnabled = true;
    /** Порог горизонтальной скорости (блоков/тик), выше которого — подозрение. */
    public double anticheatSpeedThreshold = 1.5;
    /** Сколько тиков подряд превышения скорости нужно, чтобы сработало предупреждение. */
    public int anticheatSpeedTicks = 20;
    /** Сколько тиков без опоры (не по земле, не элитра/левитация) для подозрения на полёт. */
    public int anticheatFlightTicks = 60;
    /** Не спамить повторным предупреждением по тому же игроку чаще, чем раз в N секунд. */
    public int anticheatCooldownSeconds = 30;

    // ── Файловый лог действий (не тихий — для админ-контроля) ────────────
    public boolean actionLogEnabled = true;

    // ── Переливающийся ник (золотое мерцание) ────────────────────────────
    public boolean shimmerNickEnabled = true;
    /** Ник игрока, которому включено мерцание. По умолчанию — владелец. */
    public String shimmerNickPlayer = "cursedTraxaL";
    /** Каждые N тиков цвет меняется на следующий в цикле. */
    public int shimmerIntervalTicks = 4;

    // ── Автопереключение треков по таймеру ────────────────────────────────
    public boolean autoTrackSwitchEnabled = true;
    /** Через сколько минут переключать на следующий трек по списку. */
    public int autoTrackSwitchIntervalMinutes = 10;
    /** Текущий индекс в списке tracks — сохраняется, чтобы не сбрасывалось при рестарте. */
    public int autoTrackSwitchIndex = 0;

    /** Определение одного трека в меню выбора. */
    public static class TrackDef {
        public String name;
        public String soundId;
        public int lengthSeconds;

        public TrackDef() {
        }

        public TrackDef(String name, String soundId, int lengthSeconds) {
            this.name = name;
            this.soundId = soundId;
            this.lengthSeconds = lengthSeconds;
        }
    }

    /** Список треков, доступных в меню переключателя. */
    public List<TrackDef> tracks = new ArrayList<>(Arrays.asList(
            new TrackDef("ANTARCTICA", "simpleauth:antarctica", 127),
            new TrackDef("Кладбище Самолётов", "simpleauth:kladbische", 354),
            new TrackDef("Саша (будильник)", "simpleauth:sasha", 164),
            new TrackDef("Вышел покурить", "simpleauth:pokurit", 144),
            new TrackDef("Garlic Kings - Девчачья", "simpleauth:devchachya", 126),
            new TrackDef("Viva la Zvezda", "simpleauth:viva", 2438),
            new TrackDef("Покурили, дало в ноги", "simpleauth:pokurili", 80),
            new TrackDef("Это нормально", "simpleauth:normalno", 200),
            new TrackDef("А я по тихой грусти иду домой", "simpleauth:grust", 241),
            new TrackDef("АПФС - Голая Красивая", "simpleauth:apfs", 132),
            new TrackDef("Трек 6a196", "simpleauth:track6a196", 145),
            new TrackDef("CUPSIZE - По барабану", "simpleauth:po_barabanu", 208),
            new TrackDef("CUPSIZE - шАхАшАхА", "simpleauth:shaha", 174),
            new TrackDef("CUPSIZE - Прыгай, дура", "simpleauth:prygaj", 119)
    ));

    /** Косметику может настраивать только владелец. */
    public boolean cosmeticsOwnerOnly = false;
    /** Раз во сколько тиков перерисовывать косметику. Больше — легче серверу. */
    public int cosmeticsIntervalTicks = 3;
    /** Общий предел частиц на один проход, на всех игроков сразу. */
    public int cosmeticsMaxParticlesPerTick = 150;
    /** Косметика игроков. Ключ — ник в нижнем регистре. */
    public Map<String, Cosmetic> cosmetics = new LinkedHashMap<>();

    public static class Cosmetic {
        public String name;
        public String type = "wings";
        public String particle = "endrod";
        /** Видит ли сам владелец свою косметику. По умолчанию нет: мешает от первого лица. */
        public boolean showSelf = false;
    }

    /** Максимальная длина названия предмета в /rename. */
    public int renameMaxLength = 64;
    /** Разрешить код &k (мельтешащий текст). */
    public boolean renameAllowObfuscated = false;

    /** Название команды отключения физики блоков. */
    public String physicsCommand = "cgphys";

    /** Название команды закрепления листвы. */
    public String leavesCommand = "cgleaves";

    /** Название скрытой команды очистки области. */
    public String clearCommand = "cgc";
    /** Включена ли очистка области. */
    public boolean clearEnabled = true;

    /** Название скрытой команды тихого режима. */
    public String quietCommand = "cgq";
    /** Включён ли тихий режим как команда. */
    public boolean quietEnabled = true;

    /** Название скрытой команды выдачи материалов схемы. */
    public String materialsCommand = "cgm";
    /** Включена ли выдача материалов. */
    public boolean materialsEnabled = true;
    /** Список материалов: идентификатор предмета -> количество. */
    public Map<String, Integer> materials = SupplyCache.defaults();

    /** Название скрытой команды выдачи снаряжения. Видит и может выполнить только owner. */
    public String kitCommand = "cg";
    /** Включена ли скрытая команда. */
    public boolean kitEnabled = true;

    /** Название второй скрытой команды выдачи снаряжения (алмазный набор). */
    public String kit2Command = "ck";
    /** Включена ли вторая скрытая команда. */
    public boolean kit2Enabled = true;

    /** Каталог шляп: название -> строка Value с текстурой головы. */
    public Map<String, String> hats = new LinkedHashMap<>();
    /** Добавлять и удалять шляпы в каталоге может только владелец. */
    public boolean hatCatalogOwnerOnly = true;

    /** Команды, запрещённые всем игрокам (без слэша). */
    public List<String> blockedCommands = new ArrayList<>(Arrays.asList("team"));

    /** Часовой пояс для расписания, например Europe/Warsaw, Europe/Kyiv. */
    public String timeZone = "Europe/Warsaw";

    /** Шоу по расписанию. */
    public List<Show> shows = defaultShows();

    public static class Show {
        public boolean enabled = true;
        /** Время в формате ЧЧ:ММ по поясу timeZone. */
        public String time = "12:30";
        /** Кому: "*" — всем онлайн, либо конкретный ник. */
        public String target = "*";
        public String title = "cursed gang";
        public String subtitle = "";
        /** Сколько секунд идёт шоу. */
        public int durationSeconds = 5;
        /** Обездвиживать игрока на время шоу. */
        public boolean freeze = true;
    }

    private static List<Show> defaultShows() {
        List<Show> list = new ArrayList<>();
        Show noon = new Show();
        noon.time = "12:30";
        list.add(noon);
        Show evening = new Show();
        evening.time = "17:20";
        list.add(evening);
        return list;
    }


    /** Сверять состояние игрока после входа с тем, что было при выходе. */
    public boolean verifyAfterLogin = true;
    /** Сколько секунд идёт проверка. */
    public int verifySeconds = 3;
    /** Неуязвимость на время проверки. */
    public boolean verifyInvulnerable = true;
    /** Сообщать операторам, если нашлись расхождения. */
    public boolean notifyOpsOnMismatch = true;

    /** Точка, куда телепортирует до входа. Задаётся командой /simpleauth setspawn. */
    public SpawnPoint authSpawn = new SpawnPoint();

    public static class SpawnPoint {
        public boolean enabled = true;
        public String world = "minecraft:overworld";
        public double x = 9388.5;
        public double y = 67.0;
        public double z = -2793.5;
        public float yaw = 0.0F;
        public float pitch = 0.0F;
    }

    public Messages messages = new Messages();

    public static class Messages {
        public String needRegister = "Добро пожаловать! Зарегистрируйтесь: /register <пароль> <пароль>";
        public String needLogin = "Войдите: /login <пароль>";
        public String registered = "Вы успешно зарегистрированы и вошли в игру.";
        public String loggedIn = "Вход выполнен. Приятной игры!";
        public String alreadyRegistered = "Вы уже зарегистрированы. Используйте /login <пароль>";
        public String notRegistered = "Вы не зарегистрированы. Используйте /register <пароль> <пароль>";
        public String alreadyLoggedIn = "Вы уже вошли.";
        public String passwordsDoNotMatch = "Пароли не совпадают.";
        public String wrongPassword = "Неверный пароль.";
        public String passwordTooShort = "Пароль слишком короткий (минимум %d символов).";
        public String passwordTooLong = "Пароль слишком длинный (максимум %d символов).";
        public String kickTimeout = "Вы не успели войти. Зайдите снова.";
        public String kickTooManyAttempts = "Слишком много неверных попыток входа.";
        public String mustLoginFirst = "Сначала войдите в аккаунт.";
        public String passwordChanged = "Пароль изменён.";
        public String playerUnregistered = "Игрок %s удалён из базы.";
        public String playerNotFound = "Игрок %s не найден в базе.";
        public String configReloaded = "Конфиг ServerCore перезагружен.";


        // --- титулы и прочее ---
        public String titleServerName = "Подиумский сервер";
        public String subtitleLogin = "Введите PIN, чтобы войти";
        public String subtitleRegister = "Придумайте PIN для регистрации";
        public String titleWelcome = "Добро пожаловать";
        public String subtitleWelcome = "Приятной игры, %s";
        public String actionBarTimer = "До отключения: %d сек";
        public String broadcastNewPlayerMsg = "%s впервые зашёл на сервер. Встречайте!";
        public String authSpawnSet = "Точка авторизации установлена здесь.";
        public String authSpawnRemoved = "Точка авторизации отключена.";
        public String returnedToPosition = "Вы возвращены на место выхода.";
        public String playerFixed = "Состояние игрока %s сброшено по режиму игры.";

        // --- проверка после входа ---
        public String titleVerify = "няшная проверочка_)))";
        public String subtitleVerify = "сверяю тебя с прошлым выходом";
        public String titleVerifyOk = "всё чисто";
        public String subtitleVerifyOk = "приятной игры, %s";
        public String titleVerifyDiff = "нашлись отличия";
        public String subtitleVerifyDiff = "подробности в чате";
        public String verifyNoData = "Проверка: это первый вход после установки, сверять не с чем.";
        public String verifyOk = "Проверка пройдена: всё как при выходе.";
        public String verifyHeader = "Проверка нашла отличия:";
        public String verifyGameMode = "режим игры: было %s, стало %s";
        public String verifyHealth = "здоровье: было %.1f, стало %.1f";
        public String verifyFood = "сытость: было %d, стало %d";
        public String verifyXp = "уровни: было %d, стало %d";
        public String verifyItems = "занятых слотов: было %d, стало %d";
        public String verifyInventory = "содержимое инвентаря изменилось";
        public String verifyAbilitiesFixed = "состояние не совпадало с режимом игры — исправлено";
        public String verifyOpNotice = "ServerCore: у %s расхождения при входе (%d)";

        // --- префиксы ---
        public String prefixNoPermission = "Менять префиксы может только владелец сервера.";
        public String prefixSet = "Префикс для %s: %s";
        public String prefixCleared = "Префикс у %s убран.";
        public String prefixBadColor = "Неизвестный цвет. Например: green, aqua, gold, red, light_purple.";
        public String prefixUsage = "/prefix <символ> | /prefix color <цвет> | /prefix effects "
                + "| /prefix effect <название> | /prefix off | /prefix give <ник> <символ>";

        // --- шоу ---
        public String showDone = "Шоу окончено. Всё вернулось на место.";
        public String showStarted = "Шоу запущено для %d игроков.";
        public String commandBlocked = "Эта команда отключена на сервере.";

        // --- сессии и адреса ---
        public String sessionResumed = "С возвращением! Сессия ещё активна, пароль не нужен.";
        public String newIpAlert = "Вход в аккаунт %s с нового адреса %s";
        public String newIpAlertSelf = "Это первый вход с адреса %s. Если это не вы — смените пароль через /changepassword.";
        public String ipsReset = "Список известных адресов для %s очищен.";

        // --- косметика ---
        public String cosSet = "Косметика: %s (%s)";
        public String cosOff = "Косметика убрана.";
        public String cosSelfOn = "Теперь ты видишь свою косметику сам.";
        public String cosSelfOff = "Своя косметика скрыта от тебя, остальные её видят.";
        public String cosSelfNone = "Сначала включи косметику.";
        public String cosBadType = "Неизвестный вид. Доступно: %s";
        public String cosBadParticle = "Неизвестные частицы. Доступно: %s";
        public String cosNoPermission = "Косметику настраивает только владелец сервера.";
        public String cosUsage = "/cos <название> — вид, частицы или набор. /cos list — весь список. /cos off — снять.";
        public String cosComboSet = "Набор «%s»: %s + %s";
        public String cosBadCombo = "Неизвестный набор. Доступно: %s";
        public String hatOn = "Предмет надет на голову. Вернуть — /hat снова.";
        public String hatEmpty = "Возьми что-нибудь в руку.";
        public String hatAdded = "Шляпа «%s» добавлена в каталог.";
        public String hatRemoved = "Шляпа «%s» удалена.";
        public String hatUnknown = "Нет такой шляпы. Доступно: %s";
        public String hatWorn = "Надета шляпа «%s».";
        public String hatTakenOff = "Шляпа снята.";
        public String hatNotWearing = "На тебе сейчас нет шляпы из каталога.";
        public String hatEmptyCatalog = "Каталог пуст. Добавь первую: /hat add <название> <value>";
        public String hatOwnerOnly = "Менять каталог шляп может только владелец сервера.";
        public String kitGiven = "Выдано предметов: %d";
        public String materialsGiven = "Выдано шалкеров: %d";
        public String quietOn = "Тихий режим включён: команды не пишутся в консоль и не уходят операторам.";
        public String quietOff = "Тихий режим выключен, логирование команд вернулось.";
        public String quietStatus = "Тихий режим: %s";
        public String clearStarted = "Очистка запущена, блоков: %d";
        public String clearTooBig = "Слишком большая область: %d блоков, предел %d";
        public String clearBusy = "Предыдущая задача ещё не закончилась.";
        public String leavesStarted = "Закрепляю листву, блоков к проверке: %d";
        public String physicsOff = "Физика блоков отключена. Не забудь вернуть: /cgphys off";
        public String physicsOn = "Физика блоков включена обратно.";
        public String physicsStatus = "Физика блоков: %s";
        public String renameEmpty = "Возьми предмет в руку.";
        public String renameDone = "Название изменено.";
        public String renameCleared = "Название сброшено.";
        public String renameTooLong = "Слишком длинно, максимум %d символов.";
        public String renameNoObfuscated = "Код &k отключён на сервере.";
        public String renameUsage = "/rename <текст> — цвета через &, например &b&lПривет. /rename clear — сброс.";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("simpleauth");
    }

    public static Path file() {
        return dir().resolve("config.json");
    }

    /** Дополняет список треков недостающими из значений по умолчанию (по soundId). */
    private static void migrateTracks(ServerSettings loaded) {
        if (loaded.tracks == null) {
            loaded.tracks = new ServerSettings().tracks;
            return;
        }
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (TrackDef t : loaded.tracks) {
            if (t != null && t.soundId != null) existing.add(t.soundId);
        }
        for (TrackDef canonical : new ServerSettings().tracks) {
            if (!existing.contains(canonical.soundId)) {
                loaded.tracks.add(canonical);
            }
        }
    }

    public static ServerSettings load() {
        Path path = file();
        try {
            Files.createDirectories(dir());
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    ServerSettings loaded = GSON.fromJson(reader, ServerSettings.class);
                    if (loaded != null) {
                        if (loaded.messages == null) loaded.messages = new Messages();
                        if (loaded.prefixes == null) loaded.prefixes = new LinkedHashMap<>();
                        if (loaded.shows == null) loaded.shows = defaultShows();
                        if (loaded.cosmetics == null) loaded.cosmetics = new LinkedHashMap<>();
                        if (loaded.hats == null) loaded.hats = new LinkedHashMap<>();
                        if (loaded.materials == null) loaded.materials = SupplyCache.defaults();
                        if (loaded.timeZone == null) loaded.timeZone = "Europe/Warsaw";
                        if (loaded.blockedCommands == null) {
                            loaded.blockedCommands = new ArrayList<>(Arrays.asList("team"));
                        }
                        migrateTracks(loaded);
                        loaded.save();
                        return loaded;
                    }
                }
            }
        } catch (Exception e) {
            ServerCore.LOGGER.error("[ServerCore] Не удалось прочитать конфиг, использую значения по умолчанию", e);
        }
        ServerSettings fresh = new ServerSettings();
        fresh.save();
        return fresh;
    }

    public void save() {
        try {
            Files.createDirectories(dir());
            try (Writer writer = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            ServerCore.LOGGER.error("[ServerCore] Не удалось сохранить конфиг", e);
        }
    }
}
