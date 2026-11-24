# Архитектура TerminalPlayer

Документ описывает, как устроен проект, почему выбраны именно такие решения и где что менять.
Код организован по слоям: `core` → `data` → `playback` → `ui`, зависимости идут только в одну
сторону.

```
                    +-----------------------------+
                    |        MainActivity         |
                    |  enableEdgeToEdge + Compose |
                    +--------------+--------------+
                                   |
                    +--------------v--------------+
                    |        ui/MainScreen        |
                    |  route + bottom nav + mini  |
                    +--+-------+-------+----------+
                       |       |       |
         +-------------v-+  +--v----+  +v-------------------+
         | ui/home       |  | ui/   |  | ui/player          |
         | HomeScreen    |  |library|  | NowPlayingScreen   |
         |               |  |Collect|  | PlayerViewModel    |
         +---------------+  +-------+  +--------+-----------+
                                                 |
                                    +------------v-------------+
                                    | ui/visualizer            |
                                    | VisualizerScreen/Engine  |
                                    | AsciiVisualizer (Canvas) |
                                    +------------+-------------+
                                                 |
                          AudioSessionBus (StateFlow<Int>)
                                                 ^
                    +----------------------------+--------------+
                    | playback/PlayerConnection                   |
                    | (MediaController)                          |
                    +---------------------+----------------------+
                                          | Binder
                    +---------------------v----------------------+
                    | playback/PlaybackService                   |
                    | MediaSessionService + MediaSession         |
                    | ExoPlayer (audioSessionId)                 |
                    +---------------------+----------------------+
                                          |
                    +---------------------v----------------------+
                    | data/MusicRepository  -> MediaStore         |
                    | data/FavoritesRepository -> SharedPreferences|
                    | data/Track                                  |
                    +--------------------------------------------+
```

## 1. Слой данных

### 1.1. Track

`data/Track.kt` — иммутабельная модель одного трека (`id`, `title`, `artist`, `album`,
`durationMs`, `uri`, `folder`) плюс маппинг в `MediaItem` Media3. `folder` — последний сегмент
`RELATIVE_PATH`, нужен для категории `FOLDERS`; маппинг в `MediaItem` живёт рядом с моделью, чтобы
остальные слои не собирали `MediaMetadata` вручную.

### 1.2. MusicRepository

`data/MusicRepository.kt` — единственное место, которое знает про `MediaStore`:

* запрос идёт через `MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)`, то есть
  через scoped storage, без `File` и без угадывания путей;
* фильтр `IS_MUSIC != 0 AND DURATION > 10000` отсекает рингтоны, уведомления и битые записи;
* сортировка `TITLE COLLATE NOCASE`, `ARTIST = "<unknown>"` нормализуется в пустую строку
  (UI подставляет `UNKNOWN ARTIST`);
* `RELATIVE_PATH` читается через `getColumnIndex` (а не `getColumnIndexOrThrow`), потому что
  часть OEM-провайдеров может не отдать колонку; пустое значение нормализуется в `ROOT`;
* `refresh()` выполняется на `Dispatchers.IO`, ошибки не пробрасываются наружу, а превращаются в
  терминальное сообщение в `LibraryState.error` (`PERMISSION DENIED // READ_MEDIA_AUDIO` и т.п.).

Состояние библиотеки — `StateFlow<LibraryState>`; экран не выполняет запросы сам.

### 1.3. FavoritesRepository

`data/FavoritesRepository.kt` — единственный «плейлист» приложения: набор MediaStore id в
`SharedPreferences`.

* `StateFlow<Set<Long>>` читается один раз при создании;
* `toggle(id)` обновляет и состояние, и `SharedPreferences` (`.apply()`), поэтому избранное
  переживает перезапуск;
* id, которых больше нет в MediaStore после пересканирования, просто не попадают в
  `favoriteTracks` — отдельная чистка не нужна.

### 1.4. Каталог и группировка

`ui/player/PlayerViewModel.kt` склеивает три потока (`MusicRepository.state`, выбранная
сортировка, `FavoritesRepository.ids`) в один `CatalogUiState`:

```kotlin
data class CatalogUiState(
    val tracks: List<Track>,          // отсортированный плоский список
    val favoriteTracks: List<Track>,  // избранное в том же порядке
    val albums: List<TrackGroup>,     // группировки для CATEGORIES
    val artists: List<TrackGroup>,
    val folders: List<TrackGroup>,
    val favorites: Set<Long>,
    val loading / loaded / error / sort,
)
```

`TrackGroup` — ключ, заголовок, подпись и список треков; флаг `unknownTitle` позволяет экрану
подставить локализованное «НЕИЗВЕСТНЫЙ ИСПОЛНИТЕЛЬ» вместо служебного `UNKNOWN ARTIST`.
Группировка считается **в ViewModel**, а не в композаблах: `LazyColumn` не должен пересобирать
карты альбомов/исполнителей на каждый кадр. Очередь воспроизведения тоже собирается здесь:
`playTrack(track, queue)` ставит в очередь именно тот список, который видит пользователь
(группу, избранное или всю библиотеку).

### 1.5. SettingsRepository

`data/SettingsRepository.kt` хранит единственный `AppSettings(language, animationsEnabled)` в
`SharedPreferences` и отдаёт его как `StateFlow`. Запись обновляет и поток, и файл, поэтому выбор
языка и выключатель анимаций переживают перезапуск процесса. Репозиторий создаётся в
`TerminalPlayerApp` рядом с остальными синглтонами.

## 2. Слой воспроизведения

### 2.1. PlaybackService

`playback/PlaybackService.kt` — `MediaSessionService`, то есть единственный владелец `ExoPlayer`:

```
onCreate()
  ├─ AudioManager.generateAudioSessionId()      // id известен ДО первого трека
  ├─ ExoPlayer.Builder(...)
  │    .setAudioAttributes(USAGE_MEDIA/CONTENT_TYPE_MUSIC, handleAudioFocus = true)
  │    .setHandleAudioBecomingNoisy(true)       // пауза при выдёргивании наушников
  ├─ exoPlayer.setAudioSessionId(sessionId)
  ├─ MediaSession.Builder(this, exoPlayer).setSessionActivity(...)
  ├─ DefaultMediaNotificationProvider(setChannelId, setChannelName, setSmallIcon)
  └─ AudioSessionBus.publish(sessionId)
```

Почему так:

* `MediaSessionService` сам поднимает foreground-сервис при старте воспроизведения, строит
  медиа-уведомление, отдаёт управление на lock screen, Bluetooth-гарнитуры и в системный
  медиаплеер. Писать свой foreground-сервис и `NotificationCompat` вручную не нужно;
* аудиосессия генерируется заранее: `Visualizer` можно создать до того, как пользователь нажал
  play, и он сразу получит валидный `audioSessionId`;
* `onTaskRemoved` останавливает сервис только если ничего не играет — свайп из recents не рвёт
  музыку.

### 2.2. PlayerConnection

`playback/PlayerConnection.kt` — process-scoped обёртка над `MediaController`:

* `connect()` строит `SessionToken(context, ComponentName(context, PlaybackService::class.java))` и
  `MediaController.Builder(...).buildAsync()`, подписка на future идёт через main executor;
* любое событие плеера (`Player.Listener.onEvents`) пересобирает иммутабельный
  `PlaybackUiState` — Compose никогда не обращается к контроллеру напрямую;
* позиция обновляется тикером раз в 250 мс, но только пока `isPlaying` — в паузе нет лишних
  рекомпозиций;
* команды (`playQueue`, `togglePlayPause`, `next`, `previous`, `seekToFraction`, `seekBy`,
  `toggleShuffle`, `cycleRepeat`) — тонкие обёртки над `MediaController`, включая защиту от
  `duration == C.TIME_UNSET`;
* `playQueue(tracks, startIndex, shuffle)` умеет включать shuffle сразу при старте очереди — так
  работает карточка `LOCAL MIX`.

`PlaybackUiState` — производные значения для UI: `progress`, `stateLabel`, `repeatLabel`,
`hasTrack`. Никакой логики в композаблах.

### 2.3. Жизненный цикл

`TerminalPlayerApp` создаёт `MusicRepository`, `FavoritesRepository` и `PlayerConnection` в
`onCreate` и сразу зовёт `connect()`. Это гарантирует, что:

* сервис и аудиосессия существуют к моменту открытия вкладки VISUALIZER;
* поворот экрана/смена экрана не пересоздают плеер;
* `MediaController` живёт столько же, сколько процесс, а звук продолжается в фоне.

## 3. UI-слой

### 3.1. Дизайн-система

Палитра (`ui/theme/TerminalTheme.kt`): `Ink` — фон, `SurfaceCard` / `SurfaceElevated` — карточки,
`Stroke` / `StrokeSoft` — бордеры, `TextPrimary` / `TextSecondary` / `TextMuted` — текст,
`Accent` — единственный синий акцент, `MixStart` / `MixEnd` — градиент карточки `LOCAL MIX`.
Теней и elevation нет: глубина создаётся одним пикселем бордера.

Типографика — моноширинная (`FontFamily.Monospace`) на всех уровнях: `headline`, `title`, `body`,
`bodyStrong`, `small`, `label`, `marquee`, `glyph`, `glyphLarge`. Это не ностальгия, а требование
вёрстки: ASCII-обложки, линейки и прогресс-метры совпадают по сетке только при одинаковой ширине
глифа.

Поверхности и контролы в `ui/components/TerminalSurface.kt`:

* `TerminalCard` — скруглённая карточка с фоновым цветом и бордером;
* `GlyphButton` — квадратная/круглая кнопка с одним текстовым глифом;
* `DashedRingButton` — пунктирное кольцо для `LOCAL MIX`;
* `SectionTitle`, `Hairline`, `MetaLabel`, `GlyphPill`.

`ui/components/AsciiArt.kt` генерирует обложки: `asciiArtLines(seed)` строит поле интенсивности
по одному из трёх паттернов (шум, кольца, спираль), маскирует его по радиусу и квантует в ramp
`" .:-=+*#%@"`. `AsciiArtwork` рисует результат по одному `drawText` на глиф через
`nativeCanvas`, `DotMatrix` — точечную матрицу для плиток категорий.

`ui/components/AsciiComponents.kt` остаётся «терминальным» слоем: бегущая строка `MarqueeText`
(метрики моноширинного глифа, прокрутка только если текст не влезает), панель ошибок `InfoPanel`,
мигающий курсор. Кнопки перерисованы в пилюли, но логика прежняя.

### 3.2. Навигация

`ui/MainScreen.kt` — пять маршрутов (`HOME`, `COLLECTION`, `PLAYER`, `VISUALIZER`, `SETTINGS`),
сохранённые в нескольких строках `rememberSaveable` (маршрут, тип коллекции, ключ открытой группы,
экран, из которого открыли плеер, и экран, из которого открыли настройки). Navigation Compose не
используется: пяти экранов не оправдывают библиотеку, а общий composition-scope сохраняет
`PlayerViewModel` при переходах.

Маршруты меняются через `AnimatedContent` (fade + короткий вертикальный сдвиг); при выключенных
анимациях длительности становятся нулевыми, и переходы мгновенные.

`BackHandler` ведёт себя как ожидает пользователь: из группы — к списку групп, из коллекции и
плеера — на главную, из настроек — на экран, с которого их открыли. Нижняя навигация, клавиша
`[S]` и мини-плеер рисуются в `MainScreen`, поэтому доступны на главной, в коллекциях, в
визуализаторе и в настройках, но скрываются на полноэкранном плеере.

### 3.3. Экраны

**`ui/home/HomeScreen.kt`** — главная: строка поиска (пока декоративная), кнопка `[=]` с
раскрывающейся строкой сортировки, карточка `MY TRACKS` (три трека), карточка `LOCAL MIX`,
сетка `CATEGORIES` 2×2. Список прокручивается, мини-плеер и нижняя навигация остаются на месте.
Состояния библиотеки (нет разрешения, ошибка, скан, пустая библиотека) показываются внутри
карточки `MY TRACKS`, поэтому главная никогда не бывает пустым экраном.

**`ui/library/CollectionScreen.kt`** — один экран на три режима: вся библиотека (`kind = null`),
список групп и треки выбранной группы. Группы — карточки с ASCII-глифом категории; треки —
`LazyColumn` с обложками и подсказкой про долгое нажатие. Долгое нажатие на трек переключает
избранное в любом списке.

**`ui/player/NowPlayingScreen.kt`** — ASCII-обложка во всю ширину, бегущее название,
исполнитель/альбом, графический seek-bar (тап + перетаскивание) и транспорт. `[*]` в шапке —
избранное, внизу — строка `ST / SHF / RPT / SES`. Экран скроллируется, чтобы транспорт оставался
доступен на невысоких телефонах.

**`ui/visualizer/VisualizerScreen.kt`** — заголовок в новом стиле, дальше прежняя цепочка
`AudioSessionBus` → `VisualizerEngine` → `AsciiVisualizer`. Палитра Canvas-рендера переведена на
`TextSecondary` + `AccentBright`.

**`ui/settings/SettingsScreen.kt`** — язык (`Системный` / `Русский` / `English`) и глобальный
выключатель анимаций. Строки не перезапускают Activity: `MainActivity` собирает
`SettingsRepository.settings` и раздаёт `LocalStrings` и `LocalMotionEnabled`, поэтому вся оболочка
перерисовывается на месте, а воспроизведение в сервисе не прерывается.

### 3.4. Мини-плеер

`ui/components/MiniPlayer.kt` — карточка над нижней навигацией: обложка, `MarqueeText`, play/pause
и next, плюс двухпиксельная полоса прогресса по нижней границе. Тап по телу карточки открывает
`NowPlayingScreen`; вложенные кнопки обрабатывают свои клики и не всплывают наверх. Если трека
нет, композабл вообще не эмитит контент, поэтому навигация прижимается к низу.

### 3.5. Локализация и анимации

* `i18n/AppStrings.kt` — интерфейс `AppStrings` и две реализации (`AppStringsEn`, `AppStringsRu`).
  `LocalStrings` раздаёт выбранную таблицу, `stringsFor(language)` разрешает `SYSTEM` через
  `Locale.getDefault()`. Строки лежат в Kotlin, а не в `res/values`, потому что язык меняется на
  лету: ресурсы потребовали бы пересоздания Activity и остановили бы анимации. Плюрализация
  (`tracksCount`, `groupsCount`) написана вручную по тем же причинам.
* `i18n/AppLanguage.kt` — `SYSTEM` / `RUSSIAN` / `ENGLISH` плюс `resolve()` и
  `fromStorage()` для `SettingsRepository`.
* `ui/motion/Motion.kt` — `LocalMotionEnabled`, `Reveal` (fade + сдвиг на 18 dp; layout
  зарезервирован с первого кадра, поэтому стаггер не дёргает список), `animatedPressScale` и
  `scaleLayer`. `MainScreen`, `HomeScreen`, `CollectionScreen`, кнопки и настройки читают
  переключатель, поэтому «АНИМАЦИИ: ВЫКЛ» делает интерфейс полностью статичным.
* `res/values-ru/strings.xml` — только строки, которые читает Android (канал уведомлений);
  сам интерфейс приложения берёт текст из `i18n`.

## 4. Визуализатор

### 4.1. Цепочка данных

```
ExoPlayer (audioSessionId)
        │  setAudioSessionId(id)
        ▼
AudioSessionBus (StateFlow<Int>)                // сервис -> UI
        │
        ▼
VisualizerEngine(audioSessionId)
        │  Visualizer.setOnDataCaptureListener(..., waveform=true, fft=true)
        │  captureRate = Visualizer.getMaxCaptureRate() = 20000 mHz = 20 Hz
        ├─ onFftDataCapture   -> 40 логарифмических полос (dBFS -> 0..1 -> target[])
        └─ onWaveFormDataCapture -> RMS -> targetRms
        │
        ▼  advance(deltaNanos) на каждом VSYNC (60/90/120 Гц)
   bands[] (attack 35 мс / release 180 мс), peaks[], rms, bass, sweep, fps
        │
        ▼
AsciiVisualizer (Canvas, char grid 44 x N)
        │  1 строка = 1 drawText на слой (base + highlight)
        ▼
Экран
```

### 4.2. Почему два «клока»

Платформенный `Visualizer` отдаёт данные максимум с частотой `getMaxCaptureRate()` = 20 Гц.
Если рисовать прямо из колбэков, получится 20 fps. Поэтому:

* колбэки только пишут **целевые** значения (быстро, без аллокаций);
* `VisualizerEngine.advance()` вызывается из `withFrameNanos` и экспоненциально подтягивает
  **отображаемые** значения к целевым с разными постоянными времени для роста и спада
  (классическая «быстрая атака, медленное затухание»);
* если колбэки перестали приходить (пауза, `release()`), цели плавно затухают через 450 мс.

Итог: анимация идёт на VSYNC (60+ fps), но реагирует на реальный звук.

### 4.3. Обработка FFT

* `captureSize` берётся из `Visualizer.getCaptureSizeRange()` (обычно 1024) и приводится к степени
  двойки;
* бины маппятся в 40 полос логарифмически (`pow(maxBin/minBin, i/40)`) — так низкие частоты не
  занимают пол-экрана;
* амплитуда каждой полосы: среднее `hypot(re, im) / 128`, затем `20*log10(...)`, нормализация из
  диапазона `-62..0 dBFS`, затем квадратичная кривая для читаемости тихих мест;
* `bass` — среднее первых 6 полос (пульсация внутреннего кольца), `rms` — из waveform-буфера.

### 4.4. Рендер

* ячейка моноширинного шрифта вдвое выше ширины, поэтому все полярные координаты умножаются на
  `0.5` по Y — иначе круг выглядит вытянутым;
* символ выбирается по направлению (касательная кольца / направление луча): 8 секторов →
  `- \ | /`;
* два слоя (`dim` и `hot`) в одном `CharArray`-буфере: `hot`-глиф вытесняет базовый, поэтому
  подсветка не требует второго прохода;
* кольцо сэмплируется по длине дуги (`samples ≈ outerRadius * 22`, шаг ~0.25 ячейки), поэтому
  окружность непрерывная, а не пунктирная; уровни полос для кольца сглаживаются окном из 5
  соседей, чтобы контур не был «пилой»;
* под кругом рисуется 6-строчная полоса ASCII-спектра (21 столбик `#` с `*`-вершинами), которая
  заполняет нижнюю часть экрана и даёт ещё один способ читать громкость;
* каждая строка слоя склеивается в `String` и рисуется одним `drawText` — около 60 вызовов на
  кадр вместо ~1700;
* `frameTick` читается **внутри** draw-лямбды `Canvas`: изменение состояния инвалидирует только
  фазу рисования, рекомпозиции экрана нет;
* буфер переиспользуется между кадрами (`AsciiCellBuffer.reset`), массивы полос тоже — в
  steady-state кадр не создаёт мусора, кроме строк HUD и строк-рядов.

### 4.5. Деградация

`VisualizerEngine.start()` возвращает `null` при успехе или строку ошибки. Сценарии:

| Ситуация | Что видит пользователь |
|---|---|
| нет `RECORD_AUDIO` | `E_AUDIO_CAPTURE` + `[ GRANT ]` / `[ SETTINGS ]` |
| сервис ещё не опубликовал сессию | `E_NO_AUDIO_SESSION` + `[ OPEN PLAYER ]` |
| OEM не поддерживает `Visualizer` | `E_VISUALIZER` + `[ RETRY ]` |
| всё хорошо | ASCII-визуализатор + HUD с FPS |

## 5. Потоки и жизненный цикл

| Что | Где выполняется |
|---|---|
| Композиция, `advance()`, рисование | main thread (VSYNC) |
| Колбэки `Visualizer` | main thread (создан в композиции, есть Looper) |
| Запрос MediaStore | `Dispatchers.IO` |
| Позиция плеера | `Dispatchers.Main.immediate`, тикер 250 мс |
| ExoPlayer / MediaSession | main thread сервиса |

`VisualizerEngine` намеренно не использует блокировки: все методы вызываются на main thread,
изменяемые поля помечены `@Volatile` только для читаемости из HUD.

Экран визуализатора удерживает включённый экран (`keepScreenOn`) и освобождает эффект в
`DisposableEffect.onDispose` — при уходе с вкладки нативный `Visualizer` освобождается сразу.

## 6. Разрешения

* `READ_MEDIA_AUDIO` (33+) / `READ_EXTERNAL_STORAGE` (31–32) — доступ к медиатеке;
* `POST_NOTIFICATIONS` запрашивается вместе с медиатекой (на 33+), чтобы медиа-уведомление
  выглядело предсказуемо;
* `RECORD_AUDIO` — требование `Visualizer`; запрашивается только на вкладке визуализации и с
  явным объяснением, что микрофон не используется;
* `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — манифест, без рантайм-запроса.

Разрешения не запрашиваются на старте приложения: сначала пользователь видит интерфейс и
объяснение, потом системный диалог. Карточка `MY TRACKS` на главной — это и есть объяснение.

## 7. Производительность

* визуализатор: ~3000 операций записи в `CharArray` + ~80 `drawText` на кадр, 60+ fps;
* каталог и группировки пересчитываются только при изменении библиотеки, сортировки или избранного;
* ASCII-обложки генерируются через `remember(seed)` — скролл не пересоздаёт символьные поля;
* список библиотеки — `LazyColumn` с ключом по `id`;
* позиция обновляется 4 раза в секунду (а не каждый кадр), поэтому верхняя часть экрана не
  рекомпозируется на каждом VSYNC;
* `MarqueeText` анимирует только `offset` одного `Text` (draw-фаза), без пересчёта layout;
* HUD визуализатора рисуется внутри Canvas, а не отдельными композаблами со своими состояниями.

## 8. Как расширять

* **Поиск** — добавить `query` в `CatalogUiState`, фильтрацию в `PlayerViewModel` и сделать
  строку поиска настоящим `BasicTextField`.
* **Пользовательские плейлисты** — заменить `Set<Long>` в `FavoritesRepository` на список
  плейлистов (`id`, `name`, `trackIds`) и добавить группу в `CatalogUiState`.
* **Эквалайзер** — второй `AudioEffect` на тот же `audioSessionId`; публиковать его состояние
  через отдельный bus по образцу `AudioSessionBus`.
* **Кастомный шрифт** — `TerminalType.mono` (см. README); метрики и Canvas используют
  `Typeface.MONOSPACE`, поэтому для полного соответствия замените `Typeface.MONOSPACE` в
  `MonoMetrics`/`AsciiVisualizer` на `Typeface.createFromAsset(...)`.
* **Другой визуализатор** — реализовать альтернативный `DrawScope`-рендер, `VisualizerEngine`
  останется тем же.
* **Тесты** — `VisualizerEngine` не зависит от Android UI (кроме самого `Visualizer`), FFT-маппинг
  и огибающие можно покрыть unit-тестами, если вынести их в чистые функции. Генератор ASCII-обложек
  (`asciiArtLines`) — чистый Kotlin и тестируется без Android.

## 9. Осознанные ограничения

* Только MediaStore, без SAF-доступа к отдельным папкам.
* Поиск пока декоративный — это следующий шаг, а не забытая функция.
* Плейлисты ограничены избранным.
* Нет обложек из тегов: ASCII-обложки генерируются детерминированно, поэтому один и тот же трек
  всегда выглядит одинаково.
* `MediaController` живёт весь процесс: для плеера это плюс, для «библиотеки на 100k треков»
  понадобился бы `MediaBrowser` + пагинация.
