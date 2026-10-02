# OBD DTC catalog attribution

## English (`dtc_en.tsv`)

Derived from [mytrile/obd-trouble-codes](https://github.com/mytrile/obd-trouble-codes)
(`obd-trouble-codes.csv`), MIT License.

In that CSV, many generic P0 descriptions sit on the neighboring code. The P0 block
in this catalog was realigned so each existing sentence is stored under the SAE
code it describes. Manufacturer codes (P1, B, C, U) are unchanged.

A few standard P0 codes that the source omitted (HO2S heater, starter relay,
fuel-pump control, sensor reference voltage, A/C clutch relay, intake-manifold
tuning valve, cooling-fan control, and related open/low/high variants) were
added in the same short catalog style.

## Russian (`dtc_ru.tsv`)

Glossary translation of `dtc_en.tsv` via `tools/translate_obd_dtc_ru.py`
(token/phrase automotive dictionary). Regenerate after updating the English source:

```
python3 tools/translate_obd_dtc_ru.py
```

The `ru` product flavor loads `dtc_ru.tsv`; the `en` flavor loads `dtc_en.tsv`.
Manufacturer-specific codes may be absent from both.
