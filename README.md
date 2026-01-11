# KingdomsAddon

Плагин для Minecraft-сервера Folia 1.21+, расширяющий Lead API для управления тремя королевствами: Снежным, Лесным и Тропическим.

## Особенности

- **Три королевства**: snow_kingdom, forest_kingdom, tropical_kingdom
- **Интеграция с Lead**: Автоматическое создание команд в Lead
- **Поддержка nLogin**: Работает с nLogin Premium для offline-mode серверов
- **Folia-совместимость**: Полная поддержка многопоточной архитектуры Folia
- **Система респавна**: Индивидуальные точки спавна для каждого королевства
- **Модификатор урона**: Настраиваемый урон между союзниками и врагами
- **Система призраков**: Долгая смерть с воскрешением у алтаря
- **Русская локализация**: Все сообщения на русском языке

## Система призраков (Ghost System)

При включении этой системы игроки после смерти становятся призраками:

### Состояние призрака
- **Режим Adventure** с включённым полётом
- **Невидимость** для живых игроков (видят только светящийся силуэт)
- **Призраки видят друг друга**
- Нельзя взаимодействовать с миром (кроме дверей/люков)
- Нельзя наносить/получать урон
- Можно писать в чат

### Воскрешение
1. **Соклановцы** могут воскресить у алтаря за ресурсы
2. **Саморес** после истечения времени (по умолчанию 18 часов)

### Алтарь
- Визуально выглядит как лектерн с книгой (Display Entity)
- Кликабельный (Interaction Entity)
- Открывает GUI торговца со списком всех призраков клана
- Стоимость воскрешения определяется случайно при смерти

## Требования

- Folia 1.21.11+ или Paper 1.21+
- Java 21+
- Lead Plugin (Lead API 1.1.11+)
- nLogin Premium (опционально, для offline-mode серверов)

## Установка

1. Скачайте `KingdomsAddon-1.0.0.jar`
2. Поместите JAR в папку `plugins/` сервера
3. Убедитесь, что Lead уже установлен
4. Запустите сервер
5. Отредактируйте конфигурационные файлы
6. Добавьте игроков в списки королевств
7. Установите точки спавна командами

## Сборка

```bash
mvn clean package
```

Результат: `target/KingdomsAddon-1.0.0.jar`

## Конфигурация

### config.yml

```yaml
# Настройки урона
damage:
  ally-multiplier: 0.5    # 50% урона союзникам
  enemy-multiplier: 1.0   # 100% урона врагам
  block-teamless-damage: false

# Настройки телепортации
teleport:
  delay-ticks: 20         # Задержка перед ТП (1 секунда)
  on-first-join: true     # ТП при первом входе
  on-death-no-respawn: true  # ТП при смерти без кровати

# Интеграция с nLogin
integration:
  use-nlogin: true
  nlogin-timeout: 30      # Таймаут ожидания авторизации
```

### Файлы королевств

Файлы находятся в `plugins/KingdomsAddon/teams/`:

- `snow_kingdom.yml` - Снежное королевство
- `forest_kingdom.yml` - Лесное королевство  
- `tropical_kingdom.yml` - Тропическое королевство

Формат файла:
```yaml
players:
  - Notch
  - Herobrine
  - Steve
```

**Важно**: Никнеймы нечувствительны к регистру (Notch = notch = NOTCH)

## Команды

| Команда | Описание | Права |
|---------|----------|-------|
| `/kingdoms help` | Список команд | - |
| `/kingdoms list` | Список королевств | kingdoms.info |
| `/kingdoms info [kingdom]` | Информация о королевстве | kingdoms.info |
| `/kingdoms setspawn <kingdom>` | Установить спавн | kingdoms.setspawn |
| `/kingdoms assign <player> <kingdom>` | Добавить игрока | kingdoms.assign |
| `/kingdoms reload` | Перезагрузить конфиг | kingdoms.reload |
| `/kingdoms altar create <kingdom>` | Создать алтарь | kingdoms.admin |
| `/kingdoms altar remove <kingdom> [index]` | Удалить алтарь | kingdoms.admin |
| `/kingdoms altar list [kingdom]` | Список алтарей | kingdoms.info |
| `/kingdoms altar tp <kingdom> [index]` | Телепорт к алтарю | kingdoms.admin |
| `/kingdoms resurrect` | Саморесурреция (для призраков) | - |

Альтернативы команды: `/kd`, `/kingdom`

## Права

| Право | Описание | По умолчанию |
|-------|----------|--------------|
| `kingdoms.admin` | Все права | op |
| `kingdoms.setspawn` | Установка спавна | op |
| `kingdoms.reload` | Перезагрузка | op |
| `kingdoms.assign` | Назначение игроков | op |
| `kingdoms.info` | Просмотр информации | op |

## Как это работает

### Процесс входа игрока

1. Игрок подключается к серверу
2. (Если nLogin) Ожидание авторизации
3. Проверка никнейма в файлах королевств
4. Если найден → добавление в команду Lead
5. Если не найден → кик с сообщением
6. Телепортация на спавн королевства (первый вход)

### Система урона

- Урон между членами одной команды умножается на `ally-multiplier`
- Урон между членами разных команд умножается на `enemy-multiplier`
- Поддерживает прямой урон и урон от снарядов (стрелы, трезубцы и т.д.)

### Респавн

- При смерти без кровати/якоря игрок респавнится на спавне королевства
- Если спавн не установлен, используется стандартный респавн мира

## API для разработчиков

```java
import gg.brim.kingdoms.KingdomsAddon;
import gg.brim.kingdoms.manager.KingdomManager;

// Получить плагин
KingdomsAddon plugin = KingdomsAddon.getInstance();

// Проверить королевство игрока
String kingdom = plugin.getKingdomManager().findKingdomForPlayer("Notch");

// Проверить союзничество
boolean allies = plugin.getKingdomManager().areAllies(uuid1, uuid2);

// Получить спавн королевства
Location spawn = plugin.getSpawnManager().getSpawn("snow_kingdom");
```

## Структура проекта

```
KingdomsAddon/
├── src/main/java/gg/brim/kingdoms/
│   ├── KingdomsAddon.java          # Главный класс
│   ├── commands/
│   │   └── KingdomsCommand.java    # Обработчик команд
│   ├── config/
│   │   ├── ConfigManager.java      # Управление конфигом
│   │   └── MessagesConfig.java     # Локализация
│   ├── listeners/
│   │   ├── DamageListener.java     # Модификация урона
│   │   ├── NLoginListener.java     # Интеграция nLogin
│   │   ├── PlayerJoinListener.java # Обработка входа
│   │   └── PlayerRespawnListener.java # Обработка респавна
│   ├── manager/
│   │   ├── KingdomManager.java     # Управление командами
│   │   ├── PlayerDataManager.java  # Данные игроков
│   │   └── SpawnManager.java       # Точки спавна
│   └── util/
│       └── FoliaUtil.java          # Folia-совместимые утилиты
└── src/main/resources/
    ├── plugin.yml
    ├── config.yml
    ├── messages.yml
    └── teams/
        ├── snow_kingdom.yml
        ├── forest_kingdom.yml
        └── tropical_kingdom.yml
```

## Совместимость с Folia

Плагин использует Folia-специфичные API:
- `Entity#getScheduler()` для задач, привязанных к сущностям
- `Entity#teleportAsync()` для асинхронной телепортации
- `Bukkit.getRegionScheduler()` для задач в определённых регионах
- `Bukkit.getAsyncScheduler()` для асинхронных задач

## Отладка

Включите режим отладки в `config.yml`:
```yaml
debug: true
```

Логи будут выводиться с префиксом `[DEBUG]`

## Лицензия

MIT License

## Поддержка

Создайте Issue на GitHub для сообщений об ошибках или предложений.
