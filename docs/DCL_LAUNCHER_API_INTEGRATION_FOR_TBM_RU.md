# Руководство по интеграции с DCL Launcher API (для разработчика TBox Monitor)

> **Источник:** [gist stariu85/d1d08a5be223e6f223412ed9348e80d5](https://gist.github.com/stariu85/d1d08a5be223e6f223412ed9348e80d5).  
> Это **контракт со стороны DCL**, против которого TBox Monitor (`vad.dashing.tbox`) реализует клиент. Спецификация, которую мы передавали вендору: [DCL_LAUNCHER_API_FOR_VENDOR_RU.md](DCL_LAUNCHER_API_FOR_VENDOR_RU.md). Обратное направление (DCL → API Monitor): [DCL_LOCAL_API_ACCESS_RU.md](DCL_LOCAL_API_ACCESS_RU.md).

Данная инструкция описывает процесс подключения TBox Monitor (`vad.dashing.tbox`) к DCL Launcher (`ras.dashing.tbox.launcher`) для получения параметров рабочего стола, высоты панелей, границ ADAS-зоны и управления запуском приложений.

---

## 1. Основные сведения о протоколе

| Параметр | Значение |
| :--- | :--- |
| **Authority** | `ras.dashing.tbox.launcher.api` |
| **URI** | `content://ras.dashing.tbox.launcher.api` |
| **Разрешение** | `ras.dashing.tbox.launcher.permission.CALL_LAUNCHER_API` *(не обязательно на user-builds, проверка по UID)* |
| **Авторизация** | Автоматическая (по UID вызывающего пакета `vad.dashing.tbox`). Токены и подтверждения в UI не требуются. |
| **Формат обмена** | Синхронный `ContentResolver.call()` (вызывать в фоновом потоке IO). |

---

## 2. Подключение и получение возможностей API

Перед использованием рекомендуем проверить доступность API лаунчера через метод `getCapabilities`.

```kotlin
import android.content.Context
import android.net.Uri
import android.os.Bundle

object DclLauncherApi {
    private val LAUNCHER_API_URI = Uri.parse("content://ras.dashing.tbox.launcher.api")

    fun getCapabilities(context: Context): Bundle? {
        return try {
            context.contentResolver.call(LAUNCHER_API_URI, "getCapabilities", null, null)
        } catch (e: Exception) {
            null // Лаунчер не установлен или API недоступно
        }
    }
}
```

**Ответ `Bundle`**:
- `status` (String): `"ok"`
- `apiVersion` (Int): `1`
- `launchFullscreen` (Boolean): `true`
- `launchAppArea` (Boolean): `true`
- `launchFullWidth` (Boolean, опционально): `true` — если ключ отсутствует, Monitor всё равно пытается `mode=full_width` и обрабатывает `unsupported` как у прочих режимов
- `uiStateQuery` (Boolean): `true`
- `uiStateEvents` (Boolean): `true`
- `adasPanel` (Boolean): `true`
- `screenModes` (String): `"home,single,full_width,fullscreen,split"`

---

## 3. Запрос состояния экрана и геометрии (`getUiState` / `getUiChrome`)

Вызывайте метод `getUiState` для получения ширины/высоты баров, зоны ADAS и рабочей области приложений.

```kotlin
data class DclUiState(
    val screenMode: String,          // "home" | "single" | "full_width" | "fullscreen" | "split"
    val foregroundPackage: String,   // Пакет текущего видимого приложения
    val topBarHeightPx: Int,         // Высота верхнего бара (px)
    val bottomBarHeightPx: Int,      // Высота нижнего бара (px)
    val adasPanelBounds: String,     // Границы зоны ADAS: "left,top,right,bottom"
    val appAreaBounds: String,       // Границы рабочей области: "left,top,right,bottom"
    val displayWidthPx: Int,         // Ширина экрана в пикселях (напр. 1920)
    val displayHeightPx: Int,        // Высота экрана в пикселях (напр. 1080)
    val overlayOpen: Boolean,        // Открыты ли оверлеи DCL поверх приложений
    val revision: Long,              // Монотонно растущий номер версии состояния
)

fun getUiState(context: Context): DclUiState? {
    val bundle = context.contentResolver.call(LAUNCHER_API_URI, "getUiState", null, null) 
        ?: return null

    if (bundle.getString("status") != "ok") return null

    return DclUiState(
        screenMode = bundle.getString("screenMode", "home"),
        foregroundPackage = bundle.getString("foregroundPackage", ""),
        topBarHeightPx = bundle.getInt("topBarHeightPx", 51),
        bottomBarHeightPx = bundle.getInt("bottomBarHeightPx", 82),
        adasPanelBounds = bundle.getString("adasPanelBounds", "0,51,344,998"),
        appAreaBounds = bundle.getString("appAreaBounds", "344,51,1920,998"),
        displayWidthPx = bundle.getInt("displayWidthPx", 1920),
        displayHeightPx = bundle.getInt("displayHeightPx", 1080),
        overlayOpen = bundle.getBoolean("overlayOpen", false),
        revision = bundle.getLong("revision", 1L)
    )
}
```

---

## 4. Подписка на Push-события изменений UI (Broadcast)

Чтобы TBox Monitor не производил непрерывный опрос (`polling`), DCL автоматически отправляет Push Broadcast при любом изменении режима или геометрии.

### Регистрация Receiver в TBox Monitor

#### `AndroidManifest.xml`
```xml
<receiver
    android:name=".receiver.DclUiStateReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="ras.dashing.tbox.launcher.action.UI_STATE_CHANGED" />
        <action android:name="ras.dashing.tbox.launcher.action.UI_CHROME_CHANGED" />
    </intent-filter>
</receiver>
```

#### `DclUiStateReceiver.kt`
```kotlin
package vad.dashing.tbox.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DclUiStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "ras.dashing.tbox.launcher.action.UI_STATE_CHANGED" ||
            intent.action == "ras.dashing.tbox.launcher.action.UI_CHROME_CHANGED"
        ) {
            val extras = intent.extras ?: return
            val screenMode = extras.getString("screenMode", "home")
            val foregroundPackage = extras.getString("foregroundPackage", "")
            val adasBounds = extras.getString("adasPanelBounds", "")
            val appAreaBounds = extras.getString("appAreaBounds", "")
            val topBarHeightPx = extras.getInt("topBarHeightPx", 51)
            val bottomBarHeightPx = extras.getInt("bottomBarHeightPx", 82)
            
            // Обработать изменение геометрии/экрана в TBox Monitor (например, подстроить плавающие окна)
        }
    }
}
```

---

## 5. Запуск приложений и самого TBox Monitor (`launchApp`)

Вызывайте метод `launchApp`, чтобы запустить TBox Monitor или стороннее приложение в требуемом режиме (`app_area`, `full_width` или `fullscreen`).

### Параметры `extras`
- `packageName` (String, обязательно): Имя пакета приложения (например, `"vad.dashing.tbox"` или `"ru.yandex.yandexnavi"`).
- `mode` (String, обязательно):
  - `"app_area"` — открытие приложения в рабочей области лаунчера (между верхним и нижним барами, справа от ADAS-зоны).
  - `"full_width"` — на всю ширину экрана между верхним и нижним барами, без ADAS (не immersive).
  - `"fullscreen"` — открытие на полный экран.
- `activityClass` (String, опционально): Полное имя класса конкретной Activity.
- `requestId` (String, опционально): Уникальный ID запроса для предотвращения дублирующих нажатий.

### Пример вызова

```kotlin
fun launchApp(
    context: Context,
    packageName: String,
    mode: String = "app_area",
    activityClass: String? = null
): Boolean {
    val extras = Bundle().apply {
        putString("packageName", packageName)
        putString("mode", mode)
        if (!activityClass.isNullOrEmpty()) {
            putString("activityClass", activityClass)
        }
        putString("requestId", java.util.UUID.randomUUID().toString())
    }

    return try {
        val response = context.contentResolver.call(
            LAUNCHER_API_URI,
            "launchApp",
            null,
            extras
        )
        response?.getString("status") == "ok"
    } catch (e: Exception) {
        false
    }
}
```

---

## 6. Пример считывания координат для плавающих окон TBox

Для размещения плавающих оверлеев TBox Monitor относительно рабочей зоны DCL:

```kotlin
fun parseBounds(boundsStr: String): android.graphics.Rect {
    val parts = boundsStr.split(",")
    if (parts.size == 4) {
        return android.graphics.Rect(
            parts[0].toIntOrNull() ?: 0,
            parts[1].toIntOrNull() ?: 0,
            parts[2].toIntOrNull() ?: 1920,
            parts[3].toIntOrNull() ?: 1080
        )
    }
    return android.graphics.Rect(344, 51, 1920, 998)
}
```

---

## 7. Коды ошибок в ответе API

При возникновении ошибок метод `call()` возвращает `status = "error"` с полями:
- `errorCode`:
  - `"forbidden"` — доступ запрещен (вызывающий пакет не `vad.dashing.tbox`).
  - `"invalid_args"` — отсутствуют обязательные аргументы.
  - `"unsupported"` — вызов неизвестного метода.
  - `"internal"` — внутреннее исключение лаунчера.
- `message`: Текстовое описание ошибки.

---

## Реализация в TBox Monitor

Клиент в коде Monitor: пакет `vad.dashing.tbox.dcl` (`DclLauncherApiContract`, `DclLauncherApiClient`, `DclLauncherApiRepository`; UI push — только dynamic receiver из `DclLauncherApiRepository.start`).
