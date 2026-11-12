# API лаунчера DCL для TBox Monitor — инструкция разработчику

**Аудитория:** разработчик Dashing Club Launcher (`ras.dashing.tbox.launcher`).  
**Цель:** описать **новый API со стороны DCL**, который будет вызывать **TBox Monitor** (`vad.dashing.tbox`) на том же головном устройстве (ГУ).  
**Язык:** русский; имена методов, JSON-полей и пакетов — на английском, как в коде.

Это **не** повтор инструкции «как DCL ходит в API Monitor». Связь DCL → Monitor уже есть (`content://vad.dashing.tbox.localaccess` + HTTP `127.0.0.1`). Здесь — **обратное направление**: Monitor вызывает DCL, чтобы (1) открывать приложения, (2) узнавать видимость UI-хрома лаунчера и (3) получать **текущее состояние экрана/layout** (home / одно приложение / fullscreen / split).

---

## 1. Контекст: кто есть кто и что уже работает

| Сторона | Пакет | Роль |
|---------|--------|------|
| **TBox Monitor** | `vad.dashing.tbox` | Телеметрия, плавающие панели, автоматизации, External HTTP API |
| **Dashing Club Launcher (DCL)** | `ras.dashing.tbox.launcher` | HOME-лаунчер на ГУ; рисует свой UI (бары, панель ADAS, область приложений) |

Сворачивание чужих окон на A9 делает не этот API, а root-помощник лаунчера: [DCL_WINDOW_HELPER_RU.md](DCL_WINDOW_HELPER_RU.md).

### 1.1. Уже налаженная связь DCL → Monitor (для справки)

DCL — доверенный локальный клиент Monitor (как TBox MQTT и VAD Voice):

1. DCL вызывает ContentProvider Monitor: `content://vad.dashing.tbox.localaccess` методом `ContentResolver.call` (`getAccess` / `getStatus`), **не** `query`.
2. Monitor узнаёт вызывающий пакет через Binder (`callingPackage` + UID) и сверяет его с allowlist `ExternalApiLocalClients`.
3. Пользователь **один раз** нажимает «Разрешить» в диалоге Monitor.
4. DCL сохраняет выданный `accessToken` и `port` и ходит на `http://127.0.0.1:<port>/v1/...` с `Authorization: Bearer <accessToken>`.

Подробности стороны Monitor: репозиторий TBox, `docs/DCL_LOCAL_API_ACCESS_RU.md`, `docs/EXTERNAL_API_RU.md` §5.1. Готовые классы клиента: `LocalAccessContract` / `LocalAccessClient` в `shared/localaccess/`.

### 1.2. Что нужно добавить в DCL (этот документ)

Новый **API, которым владеет DCL**. TBox Monitor будет вызывать его, чтобы:

1. **Открыть приложение** в полноэкранном режиме **или** в **области приложений** лаунчера (не-fullscreen регион, где DCL показывает apps).
2. **Узнать (или получить событие)**, видны ли сейчас:
   - панель ADAS;
   - верхний бар;
   - нижний бар.
3. **Узнать (или получить событие) текущий screen/layout state:**
   - на экране home / главный экран DCL;
   - одно приложение в области приложений;
   - приложение в **fullscreen**;
   - **два приложения рядом (split)** — с именами пакетов слева/справа;
   - либо какое приложение сейчас на экране (для single / fullscreen).

Зачем п.2–3 TBox: автоматизации смогут **скрывать плавающие панели**, когда хром лаунчера скрыт или открыт fullscreen/split, и снова показывать на home / в оконном режиме. Сейчас TBox опирается на UsageStats и списки пакетов — этого недостаточно, когда layout и видимость баров контролирует именно DCL.

Клиент TBox Monitor (Binder-вызовы, parse Bundle, push-receiver) — [DCL_LAUNCHER_API_INTEGRATION_FOR_TBM_RU.md](DCL_LAUNCHER_API_INTEGRATION_FOR_TBM_RU.md) и пакет `vad.dashing.tbox.dcl`. Автоматизации / скрытие плавающих панелей по `screenMode` — отдельно. В этой инструкции — контракт для реализации в DCL.

---

## 2. Транспорт (рекомендация)

Опираемся на то, что уже работает между процессами на ГУ: **локальный ContentProvider + Binder**, без сети.

### 2.1. Основной канал: ContentProvider в DCL

| Параметр | Значение |
|----------|----------|
| Authority | `ras.dashing.tbox.launcher.api` (зафиксировать; не менять без согласования) |
| URI | `content://ras.dashing.tbox.launcher.api` |
| Вызов | только `ContentResolver.call(uri, method, arg, extras)` |
| Поток | **не** main thread (вызовы блокирующие) |
| Ответ | `Bundle` с ключами ниже |

Почему не HTTP как основной путь: для DCL→Monitor HTTP уже есть у Monitor; поднимать второй HTTP-сервер в лаунчере дороже по жизненному циклу (порт, boot, конфликты). ContentProvider живёт вместе с процессом лаунчера и идентифицирует вызывающего через Binder — как `vad.dashing.tbox.localaccess`.

### 2.2. Дополнительно (желательно): push при смене хрома

Для видимости баров и смены layout **предпочтительнее события**, чем опрос:

- sticky `Broadcast` только на пакет `vad.dashing.tbox`, **или**
- тот же ContentProvider + колбэк через уже установленный локальный канал (если появится позже).

Минимум для приёмки: **query/poll** через `call` (метод `getUiState` / `getUiChrome` — один снимок: хром + layout). Push — strongly recommended, чтобы TBox не опрашивал чаще ~1 Гц.

### 2.3. Не использовать

- Открытый сетевой listen (не `0.0.0.0`, не Wi‑Fi SoftAP).
- Незащищённые broadcast без ограничения пакета.
- ADB / shell как API.
- Подмену пакета строкой в extras вместо Binder.

### 2.4. Что TBox должен видеть в манифесте DCL

```xml
<provider
    android:name=".api.DclLauncherApiProvider"
    android:authorities="ras.dashing.tbox.launcher.api"
    android:exported="true"
    android:permission="ras.dashing.tbox.launcher.permission.CALL_LAUNCHER_API" />
```

Permission signature-level или custom permission, выдаваемый только приложению с известной подписью Monitor — по выбору DCL; **обязательна** проверка `callingPackage == "vad.dashing.tbox"` внутри `call()`.

Monitor со своей стороны добавит в `<queries>` authority DCL (это **TBox side later**).

---

## 3. Безопасность: направления разные, действий пользователя меньше

**Важно:** Bearer-токен существует **только** в направлении **DCL → Monitor** (уже работает).  
Направление **Monitor → DCL** (этот документ) — **без токена и без второго «Разрешить»** в DCL. Цель — **меньше действий пользователя**, а не симметричная пара согласий.

### 3.1. Только DCL → Monitor: уже существующий одноразовый consent в Monitor

Этот подпункт — **справка** про уже налаженный доступ лаунчера к API Monitor. Он **не** описывает аутентификацию нового API лаунчера.

Как сейчас (не менять продуктовую модель):

1. Пользователь **один раз** нажимает «Разрешить» в **диалоге TBox Monitor** (не в DCL).
2. **Monitor** выдаёт DCL один Bearer-токен для клиента `local:ras.dashing.tbox.launcher` и привязывает его к пакету DCL + SHA-256 подписи установленного APK (lowercase hex без двоеточий; эталон `launcher-ru-release.apk`: `c78bcd6ff57fa4ea119cae592e145c0ec6b8bc2222b36ec7320972a40544a512`).
3. DCL сохраняет токен (`tbm_api_token`) и порт (`tbm_api_port`) и ходит на `http://127.0.0.1:<port>/v1/...` с `Authorization: Bearer …`.
4. Токен не протухает по времени. Повторный запрос токена у Monitor — только если токена нет, пришёл HTTP `401`, или сменился ключ подписи (тогда снова диалог Monitor). Пока токен валиден, не вызывать `getAccess` на каждый запрос.

Итого **только для DCL → Monitor**: consent once in Monitor → token once from Monitor → reuse. Копировать токен вручную не нужно.

### 3.2. Monitor → DCL (новый API): только Binder, токена от DCL нет

Для вызовов TBox Monitor в ContentProvider DCL:

1. Принимать запросы **только** если `callingPackage == "vad.dashing.tbox"` и пакет принадлежит `Binder.getCallingUid()`.
2. Опционально: сверить SHA-256 подписи вызывающего APK с эталоном Monitor (тот же формат digest). Это **не** consent UI и **не** выдача секрета.
3. **Запрещено / out of scope on purpose:**
   - DCL **не** выдаёт токен (one-shot, rotating, shared secret — любой) приложению Monitor;
   - DCL **не** показывает второй диалог «Разрешить» / pairing / consent для Monitor;
   - не копировать модель §3.1 «в обратную сторону».
4. Пользователь уже один раз согласился в Monitor на связь с DCL (§3.1). Этого достаточно для доверия процессу `vad.dashing.tbox` на том же ГУ.
5. HTTP-сервер в DCL **не требуется**. Если когда-либо понадобится — всё равно без второго user consent и без токена от DCL к Monitor; по умолчанию остаётся только ContentProvider + Binder.

Доступ только local-on-device. Никакого listen на внешний интерфейс.

---

## 4. API: запуск приложений

### 4.1. Метод `launchApp`

`ContentResolver.call(uri, "launchApp", null, extras)`

**Extras (вход):**

| Ключ | Тип | Обязательно | Описание |
|------|-----|-------------|----------|
| `packageName` | String | да | Имя пакета приложения |
| `activityClass` | String | нет | Полное имя activity; если пусто — launch activity пакета (как `getLaunchIntentForPackage`) |
| `action` | String | нет | Intent action (опционально) |
| `mode` | String | да | `"fullscreen"`, `"full_width"` или `"app_area"` |
| `requestId` | String | нет | Идемпотентность (см. ниже) |
| `displayId` | int | нет | Только если DCL на A10 реально различает VD; иначе игнорировать |

**`mode`:**

| Значение | Смысл для DCL |
|----------|----------------|
| `fullscreen` | Открыть приложение на весь экран лаунчера / immersive так, как DCL уже открывает apps в fullscreen (пользовательский «на весь экран»). |
| `full_width` | На всю ширину экрана между верхним и нижним барами, **без ADAS**, не immersive (`screenMode=full_width`, типичные `appAreaBounds≈0,51,1920,998`). |
| `app_area` | Открыть в **области приложений** — том же не-fullscreen регионе, где DCL показывает обычные apps рядом с барами / ADAS (аналог роли штатного Adayo `LAUNCH_APP` → ActivityView ~окно приложений). Не путать с freeform TBox и с виртуальными дисплеями TBox. |

На A9 при одном физическом дисплее `app_area` = «внутри layout лаунчера, бары остаются по политике DCL»; `full_width` = «бары да, ADAS скрыта, на всю ширину»; `fullscreen` = «как сейчас DCL разворачивает app на весь экран». Конкретные пиксели — ответственность DCL; TBox передаёт только режим.

**Ответ Bundle:**

| Ключ | Тип | Описание |
|------|-----|----------|
| `status` | String | `ok` \| `accepted` \| `error` |
| `mode` | String | Эхо запрошенного режима |
| `packageName` | String | Эхо пакета |
| `message` | String | Человекочитаемая ошибка (если `error`) |
| `errorCode` | String | Машинный код (см. ниже) |

**Коды ошибок (`errorCode`):**

| Код | Когда |
|-----|--------|
| `forbidden` | Вызывающий не `vad.dashing.tbox` |
| `invalid_args` | Пустой `packageName` / неизвестный `mode` |
| `not_installed` | Пакета нет |
| `no_launch_activity` | Нечего запускать |
| `busy` | Лаунчер занят (опционально; лучше всё же принять и поставить в очередь) |
| `unsupported` | Режим не поддержан на этой сборке/платформе |
| `internal` | Прочая ошибка |

**Идемпотентность:**

- Повторный `launchApp` с тем же `packageName` + `mode` + (опционально) `requestId` в течение короткого окна (~2 с) не должен порождать второй instance «ради дубля»: достаточно поднять уже открытую задачу / переключить режим.
- Смена только `mode` для того же пакета — валидный новый запрос (перевести app_area ↔ full_width ↔ fullscreen), если DCL это умеет; иначе вернуть `unsupported` с понятным `message`.

**Синхронность:** метод может вернуть `accepted`, если запуск асинхронный, но приложение **должно** реально открыться без дальнейшего пинка от TBox. Предпочтительно `ok` после того, как intent/start уже отдан системе.

---

## 5. API: UI-хром и состояние экрана/layout (**обязательный v1**)

Один снимок состояния. Предпочтительное имя метода: **`getUiState`**. Допускается алиас **`getUiChrome`** с тем же Bundle (чтобы не плодить два контракта).

`ContentResolver.call(uri, "getUiState", null, null)`  
или `ContentResolver.call(uri, "getUiChrome", null, null)` — тот же ответ.

### 5.1. Видимость хрома (boolean)

Фактическая видимость **сейчас**, с точки зрения layout DCL:

| Ключ | Смысл |
|------|--------|
| `adasPanelVisible` | Видна ли панель / карточка ADAS. `false`, если свёрнута, скрыта immersive-политикой или отсутствует в layout. |
| `topBarVisible` | Виден ли **верхний** бар DCL (не штатный SystemUI status bar, если он отключён). |
| `bottomBarVisible` | Виден ли **нижний** бар DCL (навигация / dock). |

Если отдельной панели ADAS нет — всегда `adasPanelVisible=false` и в `capabilities` → `"adasPanel": false`.

### 5.2. Состояние экрана / layout (`screenMode`) — **обязательно в v1**

| Ключ | Тип | Описание |
|------|-----|----------|
| `screenMode` | String | Enum ниже |
| `foregroundPackage` | String | Пакет «главного» видимого app; пусто на `home` |
| `leftPackage` | String | Для `split` — пакет слева; иначе пусто / не передавать |
| `rightPackage` | String | Для `split` — пакет справа; иначе пусто / не передавать |

**`screenMode`:**

| Значение | Смысл |
|----------|--------|
| `home` | Показан home / главный экран DCL (нет app поверх области приложений, либо «чистый» лаунчер). |
| `single` | Одно приложение в **области приложений** (не fullscreen); бары по политике DCL обычно видны. `foregroundPackage` = это приложение. |
| `full_width` | Одно приложение на всю ширину между барами, без ADAS, не immersive. `foregroundPackage` = это приложение. |
| `fullscreen` | Приложение на весь экран / immersive по правилам DCL. `foregroundPackage` = это приложение. |
| `split` | Два приложения рядом. Обязательны `leftPackage` и `rightPackage`. `foregroundPackage` = сторона с фокусом **или** левая, если фокус DCL не различает (тогда в capabilities указать отсутствие focus — см. §6). |

Правила заполнения:

- Ровно одно из значений `screenMode` в каждый момент.
- На `home`: `foregroundPackage` пустой; `leftPackage` / `rightPackage` пустые.
- На `single` / `full_width` / `fullscreen`: заполнен `foregroundPackage`; split-поля пустые.
- На `split`: оба `leftPackage` и `rightPackage` непустые; если фокус неизвестен — всё равно отдать оба пакета.
- Смена app в том же режиме (например другой пакет в `single`) — новое состояние + новый `revision`.

### 5.3. Общие поля снимка

| Ключ | Тип | Описание |
|------|-----|----------|
| `status` | String | `ok` \| `error` |
| `adasPanelVisible` | boolean | §5.1 |
| `topBarVisible` | boolean | §5.1 |
| `bottomBarVisible` | boolean | §5.1 |
| `screenMode` | String | §5.2 |
| `foregroundPackage` | String | §5.2 |
| `leftPackage` | String | §5.2, для `split` |
| `rightPackage` | String | §5.2, для `split` |
| `immersive` | boolean | опционально в v1: DCL считает UI «chrome hidden» единым флагом |
| `revision` | long | монотонный счётчик **любого** изменения хрома или layout |
| `updatedAtEpochMs` | long | время последнего изменения |

Опрос TBox — не чаще **1 Гц**. Push предпочтительнее.

### 5.4. Push-событие (желательно уже в v1, не блокирует shipping)

Action: `ras.dashing.tbox.launcher.action.UI_STATE_CHANGED`  
(допустимый алиас того же extras: `…UI_CHROME_CHANGED`)  
Package: явно `vad.dashing.tbox`  
Extras: **полный снимок** как у `getUiState` (хром + `screenMode` + пакеты + `revision`).

Слать при каждом реальном изменении хрома **или** layout (debounce ~100–200 мс на анимации). Sticky optional.

### 5.5. Метод `getCapabilities` (желательно)

| Ключ | Тип | Пример |
|------|-----|--------|
| `status` | String | `ok` |
| `apiVersion` | int | `1` |
| `launchFullscreen` | boolean | `true` |
| `launchAppArea` | boolean | `true` |
| `launchFullWidth` | boolean | `true` (опционально; без ключа Monitor всё равно пробует `full_width`) |
| `uiStateQuery` | boolean | `true` |
| `uiStateEvents` | boolean | `true` / `false` |
| `screenModes` | String | `"home,single,full_width,fullscreen,split"` (какие реально есть) |
| `adasPanel` | boolean | есть ли ADAS panel |
| `splitSupported` | boolean | умеет ли лаунчер split |

Алиасы `uiChromeQuery` / `uiChromeEvents` допустимы как синонимы `uiState*`.

---

## 6. Рекомендуемые дополнительные поля (**не обязательно для v1**)

Ниже — то, что сильно поможет TBox (геометрия панелей, sync анимаций, источники), но **не входит в обязательный v1**. DCL может поставить v1 без них; поля additive в том же Bundle / том же broadcast. Приоритет — по убыванию ценности для TBox.

### 6.1. Geometry (высоты/bounds) — **P1**

Не только boolean «виден/нет», а размеры в **px экрана лаунчера** (тот же координатный мир, в котором DCL кладёт views):

| Ключ | Тип | Зачем TBox |
|------|-----|------------|
| `topBarHeightPx` | int | `0`, если бар скрыт |
| `bottomBarHeightPx` | int | `0`, если скрыт |
| `adasPanelBounds` | String | `"l,t,r,b"` или отдельные `adasX/Y/W/H` — чтобы не перекрывать ADAS |
| `appAreaBounds` | String | `"l,t,r,b"` области приложений — якорь для плавающих панелей «не залезать на хром» |
| `displayWidthPx` / `displayHeightPx` | int | базовая система координат |

Без этого TBox угадывает отступы; с bounds — панели можно класть ровно в `app_area`.

### 6.2. Split details — **P1** (поверх обязательных left/right package)

| Ключ | Тип | Смысл |
|------|-----|--------|
| `splitFocusSide` | String | `left` \| `right` \| `unknown` |
| `splitRatio` | float | доля ширины левой панели `0.0…1.0` (например `0.5`) |
| `focusedPackage` | String | пакет с фокусом (может совпадать с `foregroundPackage`) |

Обязательные `leftPackage` / `rightPackage` из §5.2 остаются в v1; эти поля — уточнение.

### 6.3. Transient chrome (шторки / оверлеи / модалки) — **P1**

| Ключ | Тип | Смысл |
|------|-----|--------|
| `notificationShadeExpanded` | boolean | шторка уведомлений открыта |
| `overlayOpen` | boolean | любой полноэкранный/крупный оверлей DCL поверх apps |
| `modalOpen` | boolean | диалог / sheet / picker DCL |
| `transientChromeVisible` | boolean | удобный OR всех «временных» слоёв |

`notificationShadeExpanded` в gist API v1.3 входит в основной снимок UI; Monitor парсит
поле в `DclUiState` (push + `getUiState`) и публикует сигнал автоматизаций
`dcl_notification_shade_expanded` (`on`/`off`). Остальные ключи §6.3 пока wishlist.

Чтобы плавающие панели TBox не рисовались поверх system/DCL UI.

### 6.4. Active source / media host — **P2**

Если DCL знает текущий «источник» (аналог Adayo `SourceType`):

| Ключ | Тип | Примеры значений |
|------|-----|------------------|
| `activeSource` | String | `carplay` \| `android_auto` \| `nav` \| `radio` \| `music` \| `bt_phone` \| `other` \| `none` |
| `activeSourcePackage` | String | пакет хоста, если есть |

Помогает автоматизациям и политике панелей (AA/CP vs native).

### 6.5. Launcher day/night (тема) — **P2**

| Ключ | Тип | Смысл |
|------|-----|--------|
| `dayNight` | String | `day` \| `night` \| `auto` |
| `darkTheme` | boolean | фактическая тёмная тема UI лаунчера сейчас |

Для синхронизации темы/панелей TBox с лаунчером.

### 6.6. Event model extras (анимация) — **P2**

В push (и опционально в query), дополнительно к `revision`:

| Ключ | Тип | Смысл |
|------|-----|--------|
| `animationDurationMs` | int | длительность текущей/только что начатой анимации смены хрома/layout; `0` если мгновенно |
| `changeReason` | String | опционально: `user` \| `launch` \| `immersive` \| `split` \| `system` |

TBox может отложить hide/show плавающих панелей на `animationDurationMs`, а не дёргать UI на каждом poll.

### 6.7. DCL — текущий HOME / resumed launcher — **P3**

| Ключ | Тип | Смысл |
|------|-----|--------|
| `isDefaultLauncher` | boolean | DCL назначен default HOME |
| `isLauncherResumed` | boolean | UI DCL в resumed (не другое fullscreen-приложение «в обход» лаунчера) |
| `bypassedByPackage` | String | если лаунчер не resumed — какой пакет поверх, если DCL знает |

Полезно отличать «DCL спрятал бары» от «вообще открыто чужое activity поверх».

### 6.8. Как заявлять optional в `getCapabilities`

Для каждого блока из §6 — boolean capability, например:  
`geometry`, `splitDetails`, `transientChrome`, `activeSource`, `dayNight`, `animationDuration`, `launcherResumed`.  
Отсутствие ключа = «не поддержано». TBox не должен падать, если поля нет.

---

## 7. Версионирование

- **Обязательный v1** (этот документ): auth Monitor→DCL = Binder only (§3.2); существующий DCL→Monitor consent в Monitor без изменений (§3.1); `launchApp` fullscreen + full_width + app_area; boolean ADAS/top/bottom; `screenMode` ∈ {`home`,`single`,`full_width`,`fullscreen`,`split`} + пакеты.
- Поля §6 — **рекомендуемые phase-2 / additive**; shipping v1 без них допустим.
- Новые ключи в Bundle — только additive; не ломать смысл существующих.
- Смена authority или семантики `screenMode` / launch `mode` — только major + согласование с TBox.

---

## 8. Минимальный acceptance checklist

### 8.1. Сторона DCL — **обязательный v1**

- [ ] Provider `content://ras.dashing.tbox.launcher.api` экспортирован, отвечает на `call`.
- [ ] Чужой пакет → `forbidden`; только `vad.dashing.tbox` → `ok`.
- [ ] `launchApp` + `mode=fullscreen` / `mode=full_width` / `mode=app_area` работают; повтор ~1 с идемпотентен.
- [ ] Невалидный пакет → `not_installed` / `no_launch_activity`, без краша.
- [ ] `getUiState` (или `getUiChrome`) отдаёт `adasPanelVisible` / `topBarVisible` / `bottomBarVisible`.
- [ ] Тот же снимок отдаёт `screenMode` ∈ {`home`,`single`,`full_width`,`fullscreen`,`split`} и корректные `foregroundPackage` / `leftPackage` / `rightPackage`.
- [ ] Ручной прогон: home → single → full_width → fullscreen → split → home; `revision` растёт.
- [ ] Нет listen на внешний IP; **нет** токена/consent UI от DCL к Monitor (§3.2); чужой пакет → `forbidden`.
- [ ] (Желательно) broadcast `UI_STATE_CHANGED` с полным снимком.

### 8.2. Сторона DCL — **рекомендуемое (не блокирует v1)**

- [ ] Geometry: `appAreaBounds`, высоты баров, bounds ADAS (§6.1).
- [ ] Split focus/ratio (§6.2).
- [ ] Transient chrome (§6.3).
- [ ] `animationDurationMs` в событиях (§6.6).
- [ ] Capabilities-флаги на optional блоки.

### 8.3. Сторона TBox (**phase 2**, out of scope этой инструкции)

- [ ] `<queries>` + клиент `call` off main thread.
- [ ] Автоматизации launch + сигналы по хрому/layout → hide floating panels.
- [ ] (Позже) использование geometry / animationDuration / source.
- [ ] Деградация, если DCL без provider / без optional полей.

---

## 9. Вне объёма (explicit out of scope)

| Тема | Кто / когда |
|------|-------------|
| Проводка автоматизаций и сигналов в TBox Monitor | TBox, **phase 2** |
| Обязательная реализация полей §6 в первой поставке DCL | **не требуется** для v1 |
| Изменение UsageStats hide / sticky-hide multi-VD | TBox, отдельно |
| Замена штатного Adayo `com.adayo.launcher.LAUNCH_APP` | не цель; DCL — свой лаунчер |
| Freeform / virtual display ADB launch TBox | уже в Monitor |
| Управление климатом / CAN / MQTT | External API Monitor |
| Обратный токен / second «Разрешить» / pairing UI в DCL для Monitor | **out of scope on purpose** — меньше действий пользователя; см. §3.2 |
| Ротация токенов / OAuth / симметричный shared secret DCL↔Monitor | **не предлагается** |
| Сетевой API с телефона / LAN | запрещено для этого контракта |

---

## 10. Краткая сводка контракта (для вставки в тикет)

```
Authority: content://ras.dashing.tbox.launcher.api
Caller:    only vad.dashing.tbox (Binder)

REQUIRED v1
  call "launchApp"   mode=fullscreen|full_width|app_area + packageName…
  call "getUiState"  (alias getUiChrome)
       → adasPanelVisible, topBarVisible, bottomBarVisible
       → screenMode=home|single|full_width|fullscreen|split
       → foregroundPackage; leftPackage/rightPackage if split
       → revision, updatedAtEpochMs
  optional push: …UI_STATE_CHANGED → same snapshot

RECOMMENDED later (§6): geometry/bounds, split focus+ratio,
  transient chrome, activeSource, dayNight, animationDurationMs,
  isLauncherResumed — additive, not required to ship v1

Auth (DCL→Monitor, existing): one Allow in Monitor → Monitor issues token once → DCL stores it.
Auth (Monitor→DCL, this API): Binder callingPackage==vad.dashing.tbox (+ optional cert).
  NO token issued by DCL. NO second Allow/consent UI in DCL.
```

---

## 11. Ссылки на существующие материалы TBox (для контекста вендора)

В репозитории TBox Monitor (`preRelease`):

- `docs/DCL_LOCAL_API_ACCESS_RU.md` — как DCL получает доступ к API Monitor (one-shot Allow).
- `docs/DCL_LAUNCHER_API_INTEGRATION_FOR_TBM_RU.md` — интеграционный гайд DCL→TBM (gist вендора; клиент Monitor).
- `docs/EXTERNAL_API_RU.md` §5.1 — локальный провайдер, consent, хранение токена.
- `docs/EXTERNAL_API_USER_GUIDE_RU.md` §3.0 — пользовательский сценарий «Разрешить».
- `docs/LAUNCHER_WINDOW_AA_CARPLAY_RU.md` — окно приложений / fullscreen у штатного Adayo (аналогия семантики, не копировать intents 1:1).
- `docs/PANELS_AND_WIDGETS_RU.md` — плавающие панели и скрытие по приложениям (зачем TBox нужны хром, layout и geometry).

---

*Версия инструкции: 1.2 (DCL apiVersion 1 + recommended §6; Monitor→DCL без обратного токена). Изменения обязательного контракта — по согласованию с командой TBox Monitor.*
