# Гарантированные параметры штатных mbCAN / VHAL

Критерий CERT: UI-вызов в штатке A9+A10, известные R/W id, явные значения/формула, сквозная связка A9↔A10.

См. также [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md), [STOCK_PUSH_SUBSCRIPTIONS_RU.md](STOCK_PUSH_SUBSCRIPTIONS_RU.md).

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

## Backlog CERT (ещё не UI)

- Замки: auto lock/unlock, follow-me-home, unlock mode, remote feedback
- Свет: высота ближнего, число миганий поворотника
- Дворники: чувствительность, задний дворник (только настройки)
- ADAS: FCW(+AEB), BSD, DOW, чувствительности FCW/LDW
- Климат: anion, fragrance, first blowing, BT reduce fan, auto ventilation
- HUD / ICM / overspeed alarm

## Исключено

- Передний ПТФ
- Виджет заднего дворника
- Speed limiter на Dashing
