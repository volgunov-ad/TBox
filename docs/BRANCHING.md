# Ветки и релизный процесс

Схема работы с Git в репозитории **TBox Monitor**: предрелизная разработка в `preRelease`, стабильные выпуски в `master`.

## Роли веток

| Ветка | Назначение |
|-------|------------|
| **`preRelease`** | Интеграционная ветка: сюда сливаются фичи, здесь проходят тесты и сборки перед релизом |
| **`master`** | Стабильная ветка: отражает то, что уже выпущено пользователям |
| **feature-ветки** | Короткоживущие ветки под одну задачу; база — всегда `preRelease` |

`master` и `preRelease` могут иметь разную историю коммитов, но перед релизом их **содержимое (дерево файлов) должно совпадать**. После merge `preRelease` → `master` код в обеих ветках одинаковый.

## Схема потока

```
feature/cursor-ветка  ──PR/merge──►  preRelease  ──тесты──►  preRelease  ──PR──►  master
                                           ▲                        │
                                           └──── merge master ──────┘
                                                (после релиза, опционально)
```

1. Фича разрабатывается в отдельной ветке от `preRelease`.
2. Готовая фича вливается в `preRelease` (через pull request).
3. На `preRelease` гоняются тесты и собираются debug/release APK.
4. Когда набор изменений готов к выпуску — PR **`preRelease` → `master`**.
5. После релиза (по желанию) `master` можно влить обратно в `preRelease`, чтобы выровнять историю.

## Разработка фичи

```bash
git fetch origin
git checkout preRelease
git pull origin preRelease
git checkout -b cursor/my-feature-59fd   # имя на ваш выбор

# ... правки, коммиты ...

git push -u origin cursor/my-feature-59fd
```

Откройте pull request: **`cursor/my-feature-59fd` → `preRelease`**.

Не создавайте feature-ветки от `master` — в `preRelease` может уже быть код, которого ещё нет в `master`.

## Интеграция в preRelease

После ревью и проверок влейте PR в `preRelease`. Несколько фич могут накапливаться в `preRelease` до следующего релиза.

Перед merge желательно убедиться, что ветка актуальна относительно `preRelease`:

```bash
git checkout cursor/my-feature-59fd
git fetch origin
git rebase origin/preRelease   # или merge origin/preRelease
```

## Релиз в master

Когда изменения в `preRelease` готовы к выпуску:

### Чеклист перед PR

- [ ] `./gradlew testRuDebugUnitTest` (и/или `testEnDebugUnitTest`)
- [ ] `./gradlew assembleRuRelease` / `assembleEnRelease` — сборка проходит
- [ ] Версия и changelog обновлены (если это релиз с новым номером)
- [ ] Нет незавершённых блокеров в открытых issue/PR

### Merge

```bash
git fetch origin
git checkout preRelease
git pull origin preRelease
```

Откройте pull request: **`preRelease` → `master`**.

Пример прошлого релиза: PR #122 (`preRelease` → `master`).

После merge в `master`:

- при необходимости поставьте git-тег версии;
- опубликуйте release APK / release notes.

### Синхронизация после релиза (рекомендуется)

Чтобы `git log` и сравнение веток не путали, подтяните `master` в `preRelease`:

```bash
git checkout preRelease
git pull origin preRelease
git merge origin/master
git push origin preRelease
```

На содержимое кода это обычно не влияет (деревья уже совпадают), но упрощает дальнейшую работу.

## Удаление влитых веток

После merge feature-ветки в `preRelease` её можно удалить на remote:

```bash
git push origin --delete cursor/my-feature-59fd
```

Проверить, что ветка полностью содержится в `preRelease`:

```bash
git fetch origin
git merge-base --is-ancestor origin/cursor/my-feature-59fd preRelease && echo "можно удалять"
```

## Чего избегать

| Не делать | Почему |
|-----------|--------|
| Коммитить фичи напрямую в `master` | Обходит предрелизное тестирование |
| Базировать feature-ветки от `master` | Риск конфликтов и пропуска кода из `preRelease` |
| Долго не вливать `preRelease` в `master` | Накапливается расхождение, релизы становятся тяжелее |
| Удалять ветки, не влитые в `preRelease` | Потеря незамерженной работы |

## Текущие долгоживущие ветки

На момент оформления документа в репозитории используются:

- **`master`** — стабильная;
- **`preRelease`** — предрелизная;
- отдельные feature-ветки (`cursor/…`, `feature/…`, `fix/…`) — только пока идёт работа; после merge в `preRelease` удаляются.

## Связанные документы

- [README.md](../README.md) — сборка и обзор проекта
- [AGENTS.md](../AGENTS.md) — окружение разработки (Cursor Cloud, тесты, Gradle)
