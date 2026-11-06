# DCL: автоматический доступ к API TBox Monitor

Инструкция для разработчика Dashing Club Launcher. После этих правок лаунчер получает токен API сам. Пользователь один раз нажимает «Разрешить» в TBox Monitor. Копировать токен и включать режим «Подключить приложение» не нужно.

Сейчас это уже работает у **TBox MQTT** (`vad.dashing.mqtt`) и **VAD Voice** (`vad.dashing.voice`). DCL `0.7.7` так не делает: в нём есть только HTTP `POST /v1/pair/request` и поля `tbm_api_token` / `tbm_api_port`. Пока пакет не добавлен в Monitor, вызов провайдера отвечает `unknown_client`.

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

## Что прислать нам

Одного описания недостаточно: пока пакет не записан в Monitor, DCL получает `unknown_client`.

1. Имя пакета. Если это по-прежнему `ras.dashing.tbox.launcher`, так и напишите.
2. Подпись, которой подписывается APK для пользователей, и её SHA-256. Сертификат не прошивается в Monitor заранее: при первом «Разрешить» Monitor запоминает сертификат вызвавшего APK. Следующий токен выдаётся только тому же пакету с тем же сертификатом. Смена ключа снова показывает диалог.
3. Текст имени в диалоге, например «Dashing Club Launcher».
4. Тестовый APK, в котором вызов провайдера уже есть. Подойдёт сборка, которая при отсутствии токена вызывает `awaitAccess` и пишет в logcat статус (`granted`, `pending`, `unknown_client`).

SHA-256 сертификата:

```
apksigner verify --print-certs DCLv077ru.apk
```

Нужна строка `Signer #1 certificate SHA-256 digest`.

После этого мы добавляем пакет в `ExternalApiLocalClients` и выпускаем сборку TBox Monitor. На головном устройстве должны стоять эта сборка Monitor и новый DCL. Первый запуск лаунчера показывает один диалог Monitor. Дальше токен обновляется сам.
