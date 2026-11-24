# TerminalPlayer

Локальный музыкальный плеер для Android, который выглядит как командная строка: чёрный фон,
моноширинный шрифт, фосфорно-зелёный текст, ASCII-прогресс-бар и круговой ASCII-визуализатор,
собранный из символов `| / - \ * #`.

Никаких векторных иконок (`Icon`, `painterResource`), градиентов, теней и скруглённых углов —
только текст, символы и Canvas.

```
TERMINALPLAYER                              v1.0.0
LOCAL AUDIO TERMINAL // MEDIA3 EXOPLAYER
================================================
[ PLAYER ]   VISUALIZER
------------------------------------------------
> PLAYER                                 ONLINE
================================================
NOW PLAYING                                03/41
ASTRAL PROJECT // TERMINAL DREAMS
ALBUM: SIGNAL PROCESSING
[=================>...........] 01:20 / 03:45
ST:PLAYING SHF:OFF RPT:ALL SES:0x0000A1B2

[ << ]  [ || ]  [ >> ]
[-10s]  [+30s]  [ SHUF ]  [ RPT ]
> LIBRARY                              41 TRACKS
================================================
[ RESCAN ] [ SORT:TITLE ]              03:41:08
> 01 TERMINAL DREAMS
     ASTRAL PROJECT // 03:45
  02 NIGHT DRIVE
     KAVINSKY // 04:12
> READY_                                  API 31+
```

## Что внутри

| Слой | Технология |
|---|---|
| UI | Kotlin + Jetpack Compose (Material3 используется только как пустой контейнер темы) |
| Воспроизведение | `androidx.media3:media3-exoplayer` + `media3-session` (MediaSessionService) |
| Библиотека | `MediaStore.Audio` (scoped storage, без чтения путей к файлам) |
| Визуализатор | `android.media.audiofx.Visualizer` (FFT + waveform) → Compose `Canvas` |
| Состояние | `StateFlow` + `ViewModel`, без DI-фреймворка |
| Мин. версия | API 31 (Android 12) |

Версии сборки: AGP 8.13.2, Gradle 8.13, Kotlin 2.2.21, Compose BOM 2025.09.01, Media3 1.8.0,
compileSdk/targetSdk 36, JDK 17.

## Экраны

### 1. PLAYER
* **NOW PLAYING** — бегущая строка `АРТИСТ // НАЗВАНИЕ` (monospace, скорость ~38 px/s, пауза на
  границе). Если название короче экрана — строка статична.
* **Прогресс** — `[=====>........] 01:20 / 03:45`. Количество ячеек вычисляется из реальной ширины
  моноширинного глифа, поэтому бар всегда влезает в экран. Тап по бару — seek.
* **Транспорт** — `[ << ]`, `[ > ]` / `[ || ]`, `[ >> ]`, `[-10s]`, `[+30s]`, `[ SHUF ]`, `[ RPT ]`.
  Активное состояние показывается инверсией видео (зелёный фон, чёрный текст), а не иконкой.
* **Статус** — `ST:PLAYING SHF:OFF RPT:ALL SES:0x0000A1B2` (SES — id аудиосессии, тот самый, к
  которому цепляется визуализатор).
* **LIBRARY** — список из MediaStore: тап по строке ставит весь список в очередь и включает трек.
  `[ RESCAN ]` перечитывает медиатеку, `[ SORT:TITLE ]` циклически переключает сортировку
  TITLE → ARTIST → LENGTH. Текущий трек подсвечен инверсией.

### 2. VISUALIZER
Круговой ASCII-визуализатор, который рисуется на `Canvas` по символьной сетке (44 колонки):

* **внешнее кольцо** — символы `- / | \` по направлению касательной, сэмплируется по длине дуги
  (~0.25 ячейки), поэтому оно непрерывное; радиус «дышит» в такт частоте, которая соответствует
  углу; четыре `+`-засечки превращают кольцо в радарную шкалу;
* **лучи** — 40 лучей из центра (по числу FFT-полос), длина = уровень полосы, символ конца луча
  выбирается по громкости: `.` → `+` → `*` → `#`;
* **peak-маркеры** — `*` на месте затухающего пика;
* **вращающийся сектор** — яркая дуга кольца, которая обходит окружность с постоянной скоростью;
* **центр** — `[ CORE ]`, RMS в процентах и ASCII-индикатор уровня;
* **спектр снизу** — полоса из `#`-столбиков с `*`-вершинами (21 бар на 40 полос);
* **HUD** — `BANDS/FFT/SR/FPS`, `RMS/PK/BASS/SWP`, id сессии и мигающий курсор.

Всё это — текст в моноширинной сетке, ни одной линии, ни одного Image.

## Разрешения

| Разрешение | Зачем | Когда спрашивается |
|---|---|---|
| `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` (31–32) | список локальных треков | при открытии вкладки PLAYER |
| `POST_NOTIFICATIONS` | управление воспроизведением из шторки | вместе с доступом к медиатеке |
| `RECORD_AUDIO` | `Visualizer` требует его даже для захвата **своего** аудиосеанса | при открытии вкладки VISUALIZER |
| `FOREGROUND_SERVICE(_MEDIA_PLAYBACK)` | фоновое воспроизведение | без запроса |

`RECORD_AUDIO` не означает прослушку микрофона: `Visualizer` с явным `audioSessionId`
обрабатывает только выходной поток плеера. Аудио никуда не пишется и не отправляется.

## Сборка

### GitHub Actions
В репозитории уже есть workflow `.github/workflows/android.yml`: он ставит JDK 17 и Gradle 8.13,
перегенерирует wrapper, собирает `:app:assembleDebug` и складывает APK в артефакт
`TerminalPlayer-debug`. Достаточно запушить проект и открыть вкладку **Actions** →
последний run → **Artifacts**.

### Локально
```bash
# требуется JDK 17 и Android SDK (platform 36, build-tools 36.x)
./gradlew :app:assembleDebug      # macOS/Linux (при необходимости: chmod +x gradlew)
gradlew.bat :app:assembleDebug    # Windows
```
APK: `app/build/outputs/apk/debug/app-debug.apk`.

В Android Studio: **Open** → папка проекта → дождаться sync (Studio сама подхватит wrapper) → Run.

`local.properties` с `sdk.dir=...` не коммитится — Studio создаёт его сама, для CI он не нужен
(ANDROID_HOME задаёт `setup-android`/runner).

## Настройка внешнего вида

* **Цвета** — `ui/theme/TerminalTheme.kt`: `TerminalGreen`, `TerminalGreenSoft`, `TerminalBright`,
  `TerminalBlack` и т.д. Всё плоские константы, никаких градиентов.
* **Шрифт** — по умолчанию `FontFamily.Monospace`. Чтобы подключить кастомный терминальный шрифт,
  положите `.ttf` в `res/font/` и замените одну строку:
  ```kotlin
  val mono: FontFamily = FontFamily(Font(R.font.ibm_plex_mono))
  ```
* **Визуализатор** — `ui/visualizer/VisualizerEngine.kt` (`BAND_COUNT = 40`, attack/release) и
  `AsciiVisualizer.kt` (`TARGET_COLUMNS = 44`, набор символов `DIRECTION_CHARS`).
* **ASCII-элементы** — `core/Ascii.kt` (прогресс-бар, индикатор, время, hex-сессия) и
  `ui/components/AsciiComponents.kt` (кнопки, линейки, бегущая строка).

## Известные ограничения

* Библиотека — только MediaStore: файлы, которые система не проиндексировала, не видны.
* Обложки не показываются принципиально (это нарушило бы ASCII-стилистику).
* `Visualizer` на некоторых прошивках/эмуляторах возвращает ошибку инициализации — экран честно
  покажет `E_VISUALIZER` вместо падения.
* Нет эквалайзера, плейлистов и поиска — это осознанный минимализм первой версии.
* Приложение не подписано релизным ключом: CI собирает debug APK; для release добавьте свой
  keystore.

## Если что-то не собирается

* **`gradlew: Permission denied`** — `chmod +x gradlew` (в CI это делает workflow).
* **`Failed to find target with hash string 'android-36'`** — в SDK Manager не установлена платформа
  36; поставьте `SDK Platform 36` + `Build-Tools 36.x`, либо дайте AGP скачать её (нужен
  принятый `sdkmanager --licenses`).
* **Предупреждение про пару Kotlin X / AGP Y** — это warning, сборка продолжится. Если хочется
  убрать: обновите `kotlin` и/или `agp` в `gradle/libs.versions.toml` (например, `kotlin = "2.3.21"`
  официально поддерживает AGP до 8.13).
* **`E_VISUALIZER` на устройстве** — прошивка не отдаёт `Visualizer` для чужой/неактивной
  аудиосессии. Нажмите `[ RETRY ]` уже во время воспроизведения; если не помогает — устройство
  не поддерживает эффект, остальной плеер работает.
* **Пустая библиотека** — MediaStore не видит файлы: проверьте, что треки проиндексированы
  (`Files`/`Музыка`), и нажмите `[ RESCAN ]`.

Подробное описание архитектуры и принятых решений — в [ARCHITECTURE.md](ARCHITECTURE.md).
