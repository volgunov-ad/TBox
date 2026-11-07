# DCL: автоматический доступ к API TBox Monitor

Инструкция для разработчика Dashing Club Launcher. После этих правок лаунчер получает токен API сам. Пользователь один раз нажимает «Разрешить» в TBox Monitor. Копировать токен и включать режим «Подключить приложение» не нужно.

Так же уже работают **TBox MQTT** (`vad.dashing.mqtt`) и **VAD Voice** (`vad.dashing.voice`). Пакет DCL зарегистрирован в `ExternalApiLocalClients` Monitor:

| Поле | Значение |
|------|----------|
| Package Name | `ras.dashing.tbox.launcher` |
| Display Name (диалог) | `Dashing Club Launcher` |
| Signer #1 certificate SHA-256 digest (эталон с `launcher-ru-release.apk`) | `c78bcd6ff57fa4ea119cae592e145c0ec6b8bc2222b36ec7320972a40544a512` |

Digest в код Monitor не прошивается: при первом «Разрешить» сохраняется SHA-256 сертификата реально установленного APK (формат — lowercase hex без двоеточий, как у `apksigner`). Смена ключа снова показывает диалог. Ранее сообщали digest `55c85c124c1d20d15ff130e7645738e0bd702bbfe535182fe679c9f2eca33cb5` — он от другого артефакта/ключа; для релизного `launcher-ru-release.apk` верен `c78bcd6f…`. DCL `0.7.7` ещё ходит только через HTTP `POST /v1/pair/request` и поля `tbm_api_token` / `tbm_api_port`; для провайдера нужна сборка DCL с `LocalAccessClient` (ниже) и Monitor с DCL в `ExternalApiLocalClients`.

## Что сделать в DCL

Пакет остаётся `ras.dashing.tbox.launcher`. Вызов делает сам этот процесс. Установщик или другое приложение представиться лаунчером не может: Monitor берёт имя пакета из Binder.

1. В манифесте разрешить видеть Monitor. У DCL `targetSdk` 36, без этого `ContentResolver.call` не дойдёт до провайдера:

```xml
<queries>
    <package android:name="vad.dashing.tbox" />
    <provider android:authorities="vad.dashing.tbox.localaccess" />
</queries>
```

2. Вызывать провайдер `content://vad.dashing.tbox.localaccess` не с главного потока. Готовые классы можно скопировать как есть, пакет не менять:

- `shared/localaccess/java/vad/dashing/tbox/localaccess/LocalAccessContract.kt`
- `shared/localaccess/java/vad/dashing/tbox/localaccess/LocalAccessClient.kt`

3. Когда токена ещё нет, один раз вызвать `LocalAccessClient.awaitAccess(context)`. Это открывает в Monitor диалог и ждёт ответ до 120 секунд. Пока статус `pending`, диалог не открывать повторно.

4. Ответ `granted` сохранить в уже существующие поля:

- `accessToken` → `tbm_api_token`
- `port` → `tbm_api_port`

Дальше, как и сейчас, запросы на `http://127.0.0.1:<port>/v1/...` с заголовком `Authorization: Bearer <accessToken>`.

5. На `401` вызвать `LocalAccessClient.getAccess(context, interactive = false)` не чаще раза в 30 секунд. Monitor каждый раз выдаёт новый токен, предыдущий сразу перестаёт действовать. Если ответ `consent_required` или `denied`, снова показать пользователю запрос через `awaitAccess`.

6. `getStatus` токен не выдаёт. Он нужен, только чтобы объяснить, почему HTTP молчит:

| `status` | Что это |
|---|---|
| `granted` | есть `accessToken` и `port` |
| `pending` | диалог Monitor ещё на экране |
| `consent_required` | подтверждения ещё не было или доступ отозвали |
| `denied` | пользователь нажал «Отклонить»; следующий `awaitAccess` спросит снова |
| `unavailable` | Monitor установлен, фоновая служба не запущена |
| `unknown_client` | этот пакет Monitor ещё не знает; нужна сборка Monitor с DCL в списке |
| `not_supported` | Monitor не установлен или провайдера в этой сборке нет |

7. Смотреть `serverEnabled`. Токен может быть выдан, а HTTP-сервер в «Настройки → API» выключен. Порт в ответе — текущий порт Monitor, не зашитый `8765`.

8. HTTP-сопряжение `POST /v1/pair/request` оставить запасным путём, если провайдер вернул `not_supported`.

## Регистрация в Monitor

Пакет уже добавлен в `ExternalApiLocalClients` (см. таблицу выше). На головном устройстве нужны сборка Monitor с этой записью и DCL, который вызывает `LocalAccessClient`. Первый запрос показывает один диалог Monitor. Дальше токен обновляется сам.

Если сменится ключ подписи релизных APK, пришлите новый `Signer #1 certificate SHA-256 digest` из:

```
apksigner verify --print-certs <dcl.apk>
```

Повторно править `ExternalApiLocalClients` не нужно: достаточно, чтобы пользователь снова нажал «Разрешить» на ГУ.
