# OBD DTC catalog attribution

## English (`dtc_en.tsv`)

Derived from [mytrile/obd-trouble-codes](https://github.com/mytrile/obd-trouble-codes)
(`obd-trouble-codes.csv`), MIT License.

## Russian (`dtc_ru.tsv`)

Glossary translation of `dtc_en.tsv` via `tools/translate_obd_dtc_ru.py`
(token/phrase automotive dictionary). Regenerate after updating the English source:

```
python3 tools/translate_obd_dtc_ru.py
```

The `ru` product flavor loads `dtc_ru.tsv`; the `en` flavor loads `dtc_en.tsv`.
Manufacturer-specific codes may be absent from both.
