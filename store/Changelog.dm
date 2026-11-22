# Changelog — Dashing Store (`:store`, `vad.dashing.store`)

Версии APK: `versionName` / `versionCode` в `store/build.gradle.kts`.
При каждой публикации на AppStore (Яндекс / Mail.ru) поднимать **versionCode**
на 1 и править **versionName** (semver); сюда — блок с тем же номером сверху.
Каталог: id `tbox-store`, самообновление с карточки магазина.

0.1.0
1. Первый выпуск **Dashing Store**: каталог с Яндекс.Диска (зеркало Mail.ru), каналы Релиз / Разработка, фильтр ГУ A9/A10.
2. Скачивание APK, проверка sha256 / packageName / cert pin, установка через системный Intent; кнопка «Удалить» с системным диалогом.
3. Витрина в стиле магазина приложений: карточки Get/Update/Open, детали, иконки с Диска.
4. Сборки ru / en (язык UI); выбор языка скачиваемого APK, если в `version.json` оба flavor.
5. Самообновление: карточка `tbox-store`, баннер «Обновить магазин»; Open/Uninstall для себя скрыты.
6. DCL: две карточки (лаунчер и DCL Install); у Install — `copyApkToDownloadsAppId` кладёт APK лаунчера в Загрузки.
7. Phone (`installMode: phone`): QR на публичную страницу APK и «Скачать в Загрузки», без установки на ГУ.
8. Windows-менеджер каталога (`tools/appstore_manager/`): meta, APK/icon/cert из файла, seed merge, installMode.
