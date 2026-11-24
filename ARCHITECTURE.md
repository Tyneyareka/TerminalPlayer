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
                    |   ui/MainScreen (2 tabs)    |
                    +----+-------------------+----+
                         |                   |
          +--------------v-----+   +---------v------------------+
          | ui/player          |   | ui/visualizer              |
          | PlayerScreen       |   | VisualizerScreen           |
          | PlayerViewModel    |   | VisualizerEngine           |
          +--------+-----------+   | AsciiVisualizer (Canvas)   |
                   |               +---------+------------------+
                   |                         |
                   |             AudioSessionBus (StateFlow<Int>)
                   |                         ^
          +--------v-----------+             |
          | playback           |             |
          | PlayerConnection   |             |
          | (MediaController)  |             |
          +--------+-----------+             |
                   |  Binder                 |
          +--------v-------------------------+----------+
          | playback/PlaybackService                    |
          | MediaSessionService + MediaSession          |
          | ExoPlayer (audioSessionId)                  |
          +--------+------------------------------------+
                   |
          +--------v-----------+
          | data/MusicRepository|  <- MediaStore (content resolver)
          | data/Track          |
          +---------------------+
```

## 1. Слой данных

`data/Track.kt` — иммутабельная модель одного трека (`id`, `title`, `artist`, `album`,
`durationMs`, `uri`) плюс маппинг в `MediaItem` Media3. Маппинг живёт рядом с моделью, чтобы
остальные слои не собирали `MediaMetadata` вручную.

`data/MusicRepository.kt` — единственное место, которое знает про `MediaStore`:

* запрос идёт через `MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)`, то есть
  через scoped storage, без `File` и без угадывания путей;
* фильтр `IS_MUSIC != 0 AND DURATION > 10000` отсекает рингтоны, уведомления и битые записи;
* сортировка `TITLE COLLATE NOCASE`, `ARTIST = "<unknown>"` нормализуется в пустую строку
  (UI подставляет `UNKNOWN ARTIST`);
* `refresh()` выполняется на `Dispatchers.IO`, ошибки не пробрасываются наружу, а превращаются в
  терминальное сообщение в `LibraryState.error` (`PERMISSION DENIED // READ_MEDIA_AUDIO` и т.п.).

Состояние библиотеки — `StateFlow<LibraryState>`; экран не выполняет запросы сам.

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
  `duration == C.TIME_UNSET`.

`PlaybackUiState` — производные значения для UI: `progress`, `stateLabel`, `repeatLabel`,
`hasTrack`. Никакой логики в композаблах.

### 2.3. Жизненный цикл

`TerminalPlayerApp` создаёт `MusicRepository` и `PlayerConnection` в `onCreate` и сразу зовёт
`connect()`. Это гарантирует, что:

* сервис и аудиосессия существуют к моменту открытия вкладки VISUALIZER;
* поворот экрана/смена вкладки не пересоздают плеер;
* `MediaController` живёт столько же, сколько процесс, а звук продолжается в фоне.

## 3. UI-слой

### 3.1. Терминальная сетка

Ключевая идея: интерфейс — это не «текст на экране», а **символьная сетка**. Метрики одного
глифа считает `ui/components/MonoMetrics.kt` через `android.graphics.Paint` с
`Typeface.MONOSPACE` (тот же шрифт, что и `FontFamily.Monospace` в Compose). Отсюда:

* `AsciiRule` рисует `=`/`-` ровно до правого края: количество символов = `ширина / advance`;
* `AsciiProgressBar` сам подбирает число ячеек под доступную ширину, поэтому
  `[=====>....] 01:20 / 03:45` не переносится и не обрезается;
* `MarqueeText` измеряет полную ширину строки и включает прокрутку только если она не влезает;
* визуализатор использует ту же логику (44 колонки, ячейка 2:1).

Такой подход даёт настоящий «CMD-вид» на любом устройстве и не требует ни одного изображения или
иконки.

### 3.2. Экраны

`ui/MainScreen.kt` — «окно терминала»: заголовок, ASCII-табы, контент, футер с мигающим курсором.
Навигация — `enum TerminalTab` в `rememberSaveable`: две вкладки не оправдывают Navigation
Compose, а общий composition-scope сохраняет `PlayerViewModel` при переключении.

`ui/player/PlayerScreen.kt` — фиксированная шапка (сейчас играет, прогресс, транспорт, статус) и
скроллируемый `LazyColumn` с библиотекой. Строка списка — две строки текста; выбранный трек
инвертирован. Рipple отключён (`indication = null`), чтобы не появлялось скруглённых волн.

`ui/player/PlayerViewModel.kt` — склеивает `LibraryState` и выбранную сортировку в
`LibraryUiState` (`combine` + `stateIn(WhileSubscribed(5s))`), отдаёт команды в
`PlayerConnection`. Сканирование запускается один раз при получении разрешения
(`refreshLibraryIfNeeded`).

### 3.3. Тема

`ui/theme/TerminalTheme.kt`: чёрный `background`, фосфорно-зелёный `primary`, набор плоских
констант и один `TextStyle`-скелет (`body`, `small`, `label`, `title`, `marquee`, `huge`).
Material3 присутствует только как пустая тема — ни `Button`, ни `Card`, ни `Icon` не
используются.

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
объяснение, потом системный диалог.

## 7. Производительность

* визуализатор: ~3000 операций записи в `CharArray` + ~80 `drawText` на кадр, 60+ fps;
* список библиотеки — `LazyColumn` с ключом по `id`;
* позиция обновляется 4 раза в секунду (а не каждый кадр), поэтому верхняя часть экрана не
  рекомпозируется на каждом VSYNC;
* `MarqueeText` анимирует только `offset` одного `Text` (draw-фаза), без пересчёта layout;
* HUD визуализатора рисуется внутри Canvas, а не отдельными композаблами со своими состояниями.

## 8. Как расширять

* **Плейлисты/поиск** — добавить поля в `LibraryState`, фильтрацию в `PlayerViewModel`, оставив
  `MusicRepository` единственным источником треков.
* **Эквалайзер** — второй `AudioEffect` на тот же `audioSessionId`; публиковать его состояние
  через отдельный bus по образцу `AudioSessionBus`.
* **Кастомный шрифт** — `TerminalType.mono` (см. README); метрики и Canvas используют
  `Typeface.MONOSPACE`, поэтому для полного соответствия замените `Typeface.MONOSPACE` в
  `MonoMetrics`/`AsciiVisualizer` на `Typeface.createFromAsset(...)`.
* **Другой визуализатор** — реализовать альтернативный `DrawScope`-рендер, `VisualizerEngine`
  останется тем же.
* **Тесты** — `VisualizerEngine` не зависит от Android UI (кроме самого `Visualizer`), FFT-маппинг
  и огибающие можно покрыть unit-тестами, если вынести их в чистые функции.

## 9. Осознанные ограничения

* Только MediaStore, без SAF-доступа к отдельным папкам.
* Нет обложек и иконок — это противоречит ASCII-стилистике.
* `MediaController` живёт весь процесс: для плеера это плюс, для «библиотеки на 100k треков»
  понадобился бы `MediaBrowser` + пагинация.
* UI-строка статуса и HUD сокращены до фиксированной ширины, чтобы не ломать сетку на узких
  экранах.
