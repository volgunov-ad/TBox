# Гарантированные параметры штатных mbCAN / VHAL

Критерий CERT: UI-вызов в штатке A9+A10, известные R/W id, явные значения/формула, сквозная связка A9↔A10.

См. также [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md), [STOCK_PUSH_SUBSCRIPTIONS_RU.md](STOCK_PUSH_SUBSCRIPTIONS_RU.md), [SYSTEMSETTINGS_CERT_RU.md](SYSTEMSETTINGS_CERT_RU.md).

## UI rules

- **Виджет ⇒ пункт в «Настройки автомобиля»**.
- Car Settings: `ScrollableTabRow` + подписки только на signals **активного** раздела.
- Стиль UI/виджетов — существующие controls, scale, active/inactive colors.

## Сделано на ветке `feature/guaranteed-stock-can-widgets`

| Функция | Настройки | Виджет |
|---------|-----------|--------|
| Blow mode panel scale fix | — | H/V panels scale icons |
| EPS ECO/Comfort/Sport | да (write) | — |
| Drive mode / 6DCT / VSC / SLA | да | да (как раньше) |
| AVH / HDC / ESP off | да | да (иконки A9) |
| LDW + LKA (enum 1/2/3) | да | два текстовых |
| TJA/ICA, HMA | да | текстовые |
| HVAC ECO/Comfort/Strong | да | цикл + 3 XML, цвета green/cyan/orange |
| AC MAX | да | да |
| Климат mirrors (A/C, AUTO, recirc, SYNC, defrost, руль, лобовое) | да | да (существующие) |
| Wiper service / PAS | да | да |
| Режим фар (1=AUTO…4=OFF) | да | циклический текст |
| Задний ПТФ | да | да |
| Замки (auto lock/unlock, follow-me-home, unlock mode, feedback) | да | — |
| Чувствительность и задний дворник | да | — |
| Высота ближнего / число миганий | да | — |
| ADAS: BSD, DOW, FCW master, FCW/LDW sensitivity | да | — |
| Климат: first blowing, BT reduce fan, auto ventilation | да | — |
| Климат: ионизация воздуха (anion purify) | да | — |
| Климат: ароматизация, аромат и интенсивность (только A9) | да | — |
| HUD: on/off, высота, яркость, режим, автояркость | да | — |
| Display / ICM: ручная яркость 1–10 и авто/ручной режим | да | — |
| Overspeed alarm (A10 CERT; A9 best-effort) | да | — |
| Media volume (CAN/VHAL, 0–31) | да | да (существующий) |
| EQ, bands, balance/fader (A9 mbCAN only) | да | — |

## Backlog CERT (ещё не UI)

- Остальные HUD / ICM параметры без полной CERT-связки (кроме яркости ICM)

## Исключено

- Передний ПТФ
- Виджет заднего дворника
- Speed limiter на Dashing
