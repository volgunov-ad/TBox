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

`ObdDtcCatalog` loads `dtc_<flavor>.tsv` from an explicit flavor map (`ru`, `en`).
A flavor that is not in the map, or whose file is missing, uses `dtc_en.tsv`.
Adding a language means a new TSV and one map entry.
Manufacturer-specific codes may be absent from every catalog.
