# Полный каталог OEM mbCAN property (из enum + libmbcanclient.so)

Сгенерировано статическим разбором (`tools/mbcan_so_catalog.py`):

- Java enum `MBVehicleProperty` / `MBAudioProperty`
- Таблица `id → nItem` в `.data` `mbcan/libmbcanclient.so` (`MBCan_Vehicle_Get` / `MBCan_Audio_Get`)
- Сверка с `MbCanKnownVehiclePropertyId` / `MbCanKnownAudioPropertyId` и `docs/MBCAN_VHAL_PARAMETERS_RU.md`

Нативный слой: [MBCAN_LIB_SO_REVERSE_RU.md](MBCAN_LIB_SO_REVERSE_RU.md).  
JNI push-поля: [MBCAN_JNI_PUSH_FIELDS_RU.md](MBCAN_JNI_PUSH_FIELDS_RU.md).  
Используемые в UI параметры: [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md).

> **Важно:** имя `App const` — как в TBox Monitor на Dashing; оно может **не совпадать** с OEM enum
> (пример: id **12** в OEM = `KEYMODE`, в приложении = `SUNROOF_TILT`).

## Статусы покрытия

| Статус | Смысл |
|--------|--------|
| `in_app_and_docs` | в приложении и в MBCAN_VHAL_PARAMETERS_RU |
| `in_app_only` | в приложении (MbCanKnown*), в справочнике параметров не описан |
| `in_docs_only` | упомянут в справочнике, нет константы в MbCanKnown* |
| `oem_only_unverified` | только OEM enum / native table — на Dashing не подтверждён |

### Сводка Vehicle

| Статус | Число (из 324) |
|--------|------|
| `in_app_and_docs` | 79 |
| `in_app_only` | 14 |
| `in_docs_only` | 11 |
| `oem_only_unverified` | 220 |

### Сводка Audio

| Статус | Число (из 37) |
|--------|------|
| `in_app_and_docs` | 11 |
| `in_app_only` | 0 |
| `in_docs_only` | 18 |
| `oem_only_unverified` | 8 |

## Vehicle properties (`MBCan_Vehicle_Get/Set`)

Native id **1…324**. `nItem` — внутренний код modular IPC (обычно **800+**).

| ID | OEM enum | nItem | App const | Статус |
|----|----------|------:|-----------|--------|
| 1 | `eVEHICLE_PROPERTY_DOOR_AUTO_LOCK` | 800 | `DOOR_AUTO_LOCK` | `in_app_and_docs` |
| 2 | `eVEHICLE_PROPERTY_DOOR_IGNOFF_UNLOCK` | 801 | `DOOR_IGNOFF_UNLOCK` | `in_app_and_docs` |
| 3 | `eVEHICLE_PROPERTY_DEFENCES_PROMPT` | 802 | `DEFENCES_PROMPT` | `in_app_and_docs` |
| 4 | `eVEHICLE_PROPERTY_MIRROR_AUTOFOLD_SW` | 803 | `MIRROR_AUTOFOLD_SW` | `in_app_and_docs` |
| 5 | `eVEHICLE_PROPERTY_MIRROR_REVERSE_TURN` | 804 | — | `in_docs_only` |
| 6 | `eVEHICLE_PROPERTY_DOOR_TRUNK_POS` | 805 | `DOOR_TRUNK_POS` | `in_app_and_docs` |
| 7 | `eVEHICLE_PROPERTY_HEADLIGHTS_HOMELIGHT_DELAY` | 806 | `HEADLIGHTS_HOMELIGHT_DELAY` | `in_app_and_docs` |
| 8 | `eVEHICLE_PROPERTY_TURN_LIGHTS_AUTO_REPEAT` | 807 | `TURN_FLASH_COUNT` | `in_app_and_docs` |
| 9 | `eVEHICLE_PROPERTY_EMERGENCY_STOP_LIGHTS` | 808 | — | `in_docs_only` |
| 10 | `eVEHICLE_PROPERTY_NFC` | 809 | — | `in_docs_only` |
| 11 | `eVEHICLE_PROPERTY_LASER` | 810 | — | `in_docs_only` |
| 12 | `eVEHICLE_PROPERTY_KEYMODE` | 811 | `SUNROOF_TILT` | `in_app_and_docs` |
| 13 | `eVEHICLE_PROPERTY_DOOR_OPEN_WARNING` | 819 | `DOOR_OPEN_WARNING` | `in_app_and_docs` |
| 14 | `eVEHICLE_PROPERTY_REAR_COLLISION_WARNING` | 820 | — | `oem_only_unverified` |
| 15 | `eVEHICLE_PROPERTY_BLIND_AREA_DETECTION` | 821 | `BLIND_AREA_DETECTION` | `in_app_and_docs` |
| 16 | `eVEHICLE_PROPERTY_LAS_SENSITIVITY_LEVEL` | 822 | `LAS_SENSITIVITY_LEVEL` | `in_app_and_docs` |
| 17 | `eVEHICLE_PROPERTY_LAS_MODE_SELECTION` | 823 | `LAS_MODE_SELECTION` | `in_app_and_docs` |
| 18 | `eVEHICLE_PROPERTY_TSR_SPEED_LIMIT_SIGN` | 824 | `VEHICLE_TSR_SWITCH` | `in_app_and_docs` |
| 19 | `eVEHICLE_PROPERTY_ID_HEADLIGHTS_SWITCH` | 825 | `HMA_SWITCH` | `in_app_and_docs` |
| 20 | `eVEHICLE_PROPERTY_ACC_AUTOBRAKE_SW` | 826 | `ACC_AUTOBRAKE_SWITCH` | `in_app_and_docs` |
| 21 | `eVEHICLE_PROPERTY_CRUISE_ACC_FCW_WARN_SET` | 827 | — | `in_docs_only` |
| 22 | `eVEHICLE_PROPERTY_SAFE_DISTANCE_WARNING` | 828 | `SAFE_DISTANCE_WARNING` | `in_app_and_docs` |
| 23 | `eVEHICLE_PROPERTY_TJA_ICA` | 829 | `TJA_ICA_SWITCH` | `in_app_and_docs` |
| 24 | `eVEHICLE_PROPERTY_STEERING_MODE` | 831 | `VEHICLE_PROPERTY_STEERING_MODE` | `in_app_only` |
| 25 | `eVEHICLE_PROPERTY_EPS_MODE` | 830 | `VEHICLE_PROPERTY_EPS_MODE` | `in_app_and_docs` |
| 26 | `eVEHICLE_PROPERTY_ATMO_LIGHT_BRIGHT` | 832 | — | `oem_only_unverified` |
| 27 | `eVEHICLE_PROPERTY_BRIGHTNESS` | 833 | — | `oem_only_unverified` |
| 28 | `eVEHICLE_PROPERTY_ATMO_LIGHT_COLOR` | 834 | — | `oem_only_unverified` |
| 29 | `eVEHICLE_PROPERTY_BREATHING_LAMP` | 835 | — | `oem_only_unverified` |
| 30 | `eVEHICLE_PROPERTY_MUSICAL_RHYTHM` | 836 | — | `in_docs_only` |
| 31 | `eVEHICLE_PROPERTY_ATMO_LIGHT_ASSOCIATE_DRIVING_MODE` | 837 | — | `oem_only_unverified` |
| 32 | `eVEHICLE_PROPERTY_WELCOME_LAMP` | 838 | — | `oem_only_unverified` |
| 33 | `eVEHICLE_PROPERTY_FRAGRANCE_SWITCH` | 839 | `FRAGRANCE_SWITCH` | `in_app_and_docs` |
| 34 | `eVEHICLE_PROPERTY_FRAGRANCE_SMELL` | 840 | `FRAGRANCE_SMELL` | `in_app_and_docs` |
| 35 | `eVEHICLE_PROPERTY_FRAGRANCE_CONCENTRATION` | 841 | `FRAGRANCE_CONCENTRATION` | `in_app_and_docs` |
| 36 | `eVEHICLE_PROPERTY_HVAC_POWER` | 842 | `HVAC_POWER` | `in_app_and_docs` |
| 37 | `eVEHICLE_PROPERTY_HVAC_TEMPERATURE` | 843 | `HVAC_TEMPERATURE_LEFT` | `in_app_and_docs` |
| 38 | `eVEHICLE_PROPERTY_HVAC_FAN_SPEED` | 844 | `HVAC_FAN_SPEED` | `in_app_and_docs` |
| 39 | `eVEHICLE_PROPERTY_HVAC_AIR_RECIRCULATION` | 845 | `HVAC_AIR_RECIRCULATION` | `in_app_and_docs` |
| 40 | `eVEHICLE_PROPERTY_HVAC_FAN_DIRECTION` | 846 | `HVAC_FAN_DIRECTION` | `in_app_and_docs` |
| 41 | `eVEHICLE_PROPERTY_HVAC_DEFROSTER` | 847 | `HVAC_DEFROSTER_SWITCH` | `in_app_and_docs` |
| 42 | `eVEHICLE_PROPERTY_HVAC_AQS` | 848 | `HVAC_AQS` | `in_app_and_docs` |
| 43 | `eVEHICLE_PROPERTY_HVAC_INOUT_PM25` | 849 | — | `oem_only_unverified` |
| 44 | `eVEHICLE_PROPERTY_HVAC_DISPLAY_REPORT` | 850 | — | `oem_only_unverified` |
| 45 | `eVEHICLE_PROPERTY_SUNROOF_CONTROL` | 851 | `SUNROOF_CONTROL` | `in_app_and_docs` |
| 46 | `eVEHICLE_PROPERTY_SUNSHADE_POS` | 852 | `SUNSHADE_POS` | `in_app_and_docs` |
| 47 | `eVEHICLE_PROPERTY_WINDOW_POS` | 853 | `WINDOW_POS` | `in_app_only` |
| 48 | `eVEHICLE_PROPERTY_BREATHING_UNLOCK` | 813 | — | `oem_only_unverified` |
| 49 | `eVEHICLE_PROPERTY_BREATHING_LOCK` | 814 | — | `oem_only_unverified` |
| 50 | `eVEHICLE_PROPERTY_LIGHT_SHOW` | 815 | — | `oem_only_unverified` |
| 51 | `eVEHICLE_PROPERTY_BT_REDUCED_WIND_SPEED` | 817 | `BT_REDUCED_WIND_SPEED` | `in_app_and_docs` |
| 52 | `eVEHICLE_PROPERTY_HVAC_BLOWER_DELAY` | 816 | `HVAC_BLOWER_DELAY` | `in_app_and_docs` |
| 53 | `eVEHICLE_PROPERTY_POWER_FIRST_BREATH` | 818 | `POWER_FIRST_BREATH` | `in_app_and_docs` |
| 54 | `eVEHICLE_PROPERTY_DVR_SNAP_SHOOT` | 864 | — | `oem_only_unverified` |
| 55 | `eVEHICLE_PROPERTY_FRWINDOW_POS` | 853 | `WINDOW_FR_POS` | `in_app_only` |
| 56 | `eVEHICLE_PROPERTY_FLWINDOW_POS` | 853 | `WINDOW_FL_POS` | `in_app_only` |
| 57 | `eVEHICLE_PROPERTY_RRWINDOW_POS` | 853 | `WINDOW_RR_POS` | `in_app_only` |
| 58 | `eVEHICLE_PROPERTY_RLWINDOW_POS` | 853 | `WINDOW_RL_POS` | `in_app_only` |
| 59 | `eAVM_DISPLAY_SWITCH` | 854 | — | `oem_only_unverified` |
| 60 | `eAVM_INTERIOR_FILL_LIGHT_SWITCH` | 855 | — | `oem_only_unverified` |
| 61 | `eAVM_LEGACY_WARNING_SWITCH` | 856 | — | `oem_only_unverified` |
| 62 | `eCFG_WIFI_NAME` | 857 | — | `oem_only_unverified` |
| 63 | `eCFG_WIFI_PASSWORD` | 858 | — | `oem_only_unverified` |
| 64 | `eCFG_BLUETOOCH_ADRESS` | 859 | — | `oem_only_unverified` |
| 65 | `eAVM_CALLING_STATUS` | 860 | — | `oem_only_unverified` |
| 66 | `eAVM_MONITER_TOUNCH_STATUS` | 861 | — | `oem_only_unverified` |
| 67 | `eAVM_RGEAR_STATUS` | 862 | — | `oem_only_unverified` |
| 68 | `eAVM_RVC_STATUS` | 863 | — | `oem_only_unverified` |
| 69 | `eLCD_BRIGHTNESS` | 1025 | — | `oem_only_unverified` |
| 70 | `eCFG_RESET` | 865 | — | `oem_only_unverified` |
| 71 | `eSCREEN_SAVE` | 866 | — | `oem_only_unverified` |
| 72 | `eSCREEN_BACKLIGHT_SWITCH` | 867 | — | `oem_only_unverified` |
| 73 | `eSYSTEM_MODE` | 1026 | `SYSTEM_MODE` | `in_app_only` |
| 74 | `eSYSTEM_REBOOT` | 1027 | `SYSTEM_REBOOT` | `in_app_and_docs` |
| 75 | `eDVD_AIDTURNINGLLLUMINATION` | 868 | — | `oem_only_unverified` |
| 76 | `eDVD_HOUR_MODE` | 869 | — | `oem_only_unverified` |
| 77 | `eDVD_WASH_CAR` | 870 | — | `oem_only_unverified` |
| 78 | `eDVD_POLLING` | 871 | — | `oem_only_unverified` |
| 79 | `eDVD_CALIBRATION` | 872 | — | `oem_only_unverified` |
| 80 | `eDVD_LDWSWITCH` | 873 | — | `oem_only_unverified` |
| 81 | `eDVD_STREERINGWHEEL` | 874 | — | `oem_only_unverified` |
| 82 | `eAVM_SET_LANG` | 875 | — | `oem_only_unverified` |
| 83 | `eDVD_RADARWARING` | 876 | — | `oem_only_unverified` |
| 84 | `eDVD_DEFDISPMODE` | 877 | — | `oem_only_unverified` |
| 85 | `eDVD_OP_TP_X` | 878 | — | `oem_only_unverified` |
| 86 | `eDVD_OP_TP_Y` | 879 | — | `oem_only_unverified` |
| 87 | `eAVM_WORK_MODESTS` | 880 | — | `in_docs_only` |
| 88 | `eDVD_SCREEN_OPERATION` | 881 | — | `oem_only_unverified` |
| 89 | `eDEFAULT` | 882 | — | `oem_only_unverified` |
| 90 | `eFRONT_OFF` | 883 | `HVAC_FRONT_OFF` | `in_app_and_docs` |
| 91 | `eTEMPKNOBROLLINGCOUNTERREQ` | 884 | — | `oem_only_unverified` |
| 92 | `eCONTROL_MODE_REQ` | 885 | — | `oem_only_unverified` |
| 93 | `eENGINECONTROLEDREQ` | 886 | — | `oem_only_unverified` |
| 94 | `eSYNCSWTICH_REQ` | 887 | `HVAC_SYNC_SWITCH` | `in_app_and_docs` |
| 95 | `eTIMEGAPSET1REQ` | 888 | — | `oem_only_unverified` |
| 96 | `eFCW_SWTICH` | 889 | `FCW_SWITCH` | `in_app_and_docs` |
| 97 | `eFCW_OPTION` | 890 | `FCW_SENSITIVITY` | `in_app_and_docs` |
| 98 | `eTIMEGAPLASTSETREQ` | 891 | — | `oem_only_unverified` |
| 99 | `eLOGSAVE` | 1028 | — | `oem_only_unverified` |
| 100 | `eRRM_LANGULAGE` | 893 | — | `oem_only_unverified` |
| 101 | `eRRM_RESOLUTATION_X` | 894 | — | `oem_only_unverified` |
| 102 | `eRRM_RESOLUTATION_Y` | 895 | — | `in_docs_only` |
| 103 | `eNAVI_SPEEDLIMIT` | 896 | — | `oem_only_unverified` |
| 104 | `eNAVI_ROADTYPE` | 897 | — | `oem_only_unverified` |
| 105 | `eUART_TEST` | 1029 | — | `oem_only_unverified` |
| 106 | `eNAVI_SPEEDLIMIT_STATUS` | 898 | — | `oem_only_unverified` |
| 107 | `eNAVI_SPEEDLIMIT_UNITS` | 899 | — | `oem_only_unverified` |
| 108 | `eOLDMODE_SWITCH` | 1030 | — | `oem_only_unverified` |
| 109 | `eDTC_INFO` | 1031 | — | `oem_only_unverified` |
| 110 | `eHVAC_AUTO_STATE` | 900 | `HVAC_AUTO_STATE` | `in_app_and_docs` |
| 111 | `eHVAC_FR_TEMPERATURE` | 901 | `HVAC_TEMPERATURE_RIGHT` | `in_app_and_docs` |
| 112 | `eVEHICLE_VIN` | 1032 | — | `oem_only_unverified` |
| 113 | `eSOFTWARE_CONFIG` | 1033 | — | `oem_only_unverified` |
| 114 | `eMUSIC_KEY` | 1036 | — | `oem_only_unverified` |
| 115 | `eDVR_SWITCH` | 1037 | — | `oem_only_unverified` |
| 116 | `eCFG_WIFI_ADRESS` | 904 | — | `oem_only_unverified` |
| 117 | `eAPA_DISPLAY_SWITCH` | 905 | — | `oem_only_unverified` |
| 118 | `eVIDEO_LIMIT` | 1038 | — | `oem_only_unverified` |
| 119 | `eCFG_REMOTETRUNKONLY` | 906 | — | `oem_only_unverified` |
| 120 | `eLAMP_CONTROL` | 907 | — | `oem_only_unverified` |
| 121 | `eCFG_BLANKCHANGINGLANE` | 908 | — | `oem_only_unverified` |
| 122 | `eHVAC_DEFROSTER_FRONT` | 909 | — | `oem_only_unverified` |
| 123 | `eTRAFFIC_DIRECTION` | 910 | — | `oem_only_unverified` |
| 124 | `eAVM_DATE_TP_STS` | 911 | — | `oem_only_unverified` |
| 125 | `eACC_IGN_GET` | 1040 | — | `oem_only_unverified` |
| 126 | `eMANUFACTURE` | 1041 | — | `oem_only_unverified` |
| 127 | `eSOURCE_STATION_MODE` | 1042 | `SOURCE_STATION_MODE` | `in_app_only` |
| 128 | `eRRM_THEME` | 1043 | — | `oem_only_unverified` |
| 129 | `eVEHICLE_HIGHBEAM_ADJUST` | 912 | `HIGHBEAM_ADJUST` | `in_app_and_docs` |
| 130 | `eVEHICLE_SMART_HIGHBEAM_SWITCH` | 913 | — | `in_docs_only` |
| 131 | `eVEHICLE_DRIVER_UNLOCKMODE` | 914 | `DRIVER_UNLOCK_MODE` | `in_app_and_docs` |
| 132 | `eVEHICLE_DOORKONB_TIME` | 915 | — | `oem_only_unverified` |
| 133 | `eVEHICLE_DOORKONB_SWITCH` | 916 | — | `oem_only_unverified` |
| 134 | `eVEHICLE_PLG_CONTROL` | 917 | `TRUNK_PLG_CONTROL` | `in_app_and_docs` |
| 135 | `eVEHICLE_LIGHTCONTROL` | 918 | `LIGHTCONTROL` | `in_app_and_docs` |
| 136 | `eVEHICLE_REARFOGLIGHT` | 919 | `REAR_FOG_LIGHT` | `in_app_and_docs` |
| 137 | `eVEHICLE_TUNNEL_LAMP` | 920 | — | `oem_only_unverified` |
| 138 | `eSEAT_FL_HEATVENTSW` | 921 | `FRONT_LEFT_SEAT_HEAT_VENT_SWITCH` | `in_app_and_docs` |
| 139 | `eSEAT_FR_HEATVENTSW` | 922 | `FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH` | `in_app_and_docs` |
| 140 | `eHVAC_CUSTOM` | 923 | `HVAC_CUSTOM` | `in_app_and_docs` |
| 141 | `eHVAC_VENTILATION_AUTO_SWITCH` | 924 | `HVAC_VENTILATION_AUTO_SWITCH` | `in_app_and_docs` |
| 142 | `eVEHICLE_AVH_SWITCH` | 925 | `AVH_SWITCH` | `in_app_and_docs` |
| 143 | `eVEHICLE_HDC_SWITCH` | 926 | `HDC_SWITCH` | `in_app_and_docs` |
| 144 | `eVEHICLE_ESCOFF_SWITCH` | 927 | `ESP_OFF_SWITCH` | `in_app_and_docs` |
| 145 | `eVEHICLE_DRIVEMODE` | 928 | `VEHICLE_DRIVEMODE` | `in_app_and_docs` |
| 146 | `eVEHICLE_REFUEL` | 929 | — | `oem_only_unverified` |
| 147 | `eVEHICLE_POWERMODE` | 930 | `VEHICLE_POWERMODE` | `in_app_only` |
| 148 | `eVEHICLE_ISS_SWITCH` | 931 | — | `oem_only_unverified` |
| 149 | `eVEHICLE_DRIVEMODE_6DCT_WET` | 932 | `VEHICLE_DRIVEMODE_6DCT_WET` | `in_app_and_docs` |
| 150 | `eVEHICLE_ECOMODESWSTS` | 933 | — | `oem_only_unverified` |
| 151 | `eVEHICLE_LIGHT_SHOW_MODE` | 934 | — | `oem_only_unverified` |
| 152 | `eVEHICLE_EXTERNAL_LIGHT_WELCOME_LAMP_MODE` | 935 | — | `oem_only_unverified` |
| 153 | `eVEHICLE_SET_ROLLO_CONTROL` | 936 | — | `oem_only_unverified` |
| 154 | `eVEHICLE_SET_RADIO_FREQUANCE_MODE` | 937 | — | `oem_only_unverified` |
| 155 | `eVEHICLE_SET_RADIO_RESEARCH_STS` | 938 | — | `oem_only_unverified` |
| 156 | `eVEHICLE_SET_RADIO_SOURCE_STATION_MODE` | 939 | — | `oem_only_unverified` |
| 157 | `eVEHICLE_SET_RRM_ONSTS` | 940 | — | `oem_only_unverified` |
| 158 | `eVEHICLE_FMRADIO_FREQUANCE_VALUE` | 941 | — | `oem_only_unverified` |
| 159 | `eVEHICLE_AMRADIO_FREQUANCE_VALUE` | 942 | — | `oem_only_unverified` |
| 160 | `eVEHICLE_KNOB_STS` | 943 | — | `oem_only_unverified` |
| 161 | `eVEHICLE_SET_FREQUENCY_STS` | 944 | — | `oem_only_unverified` |
| 162 | `eVEHICLE_BT_PHONE_STATUS_SET` | 945 | — | `oem_only_unverified` |
| 163 | `eVEHICLE_PM25_DISPLAY_TOGGLE` | 946 | `VEHICLE_PM25_DISPLAY_TOGGLE` | `in_app_only` |
| 164 | `eVEHICLE_UV_LAMP_REQ` | 947 | `VEHICLE_UV_LAMP_REQ` | `in_app_only` |
| 165 | `eVEHICLE_STERILIZE_STRENGTH_REQ` | 948 | `VEHICLE_STERILIZE_STRENGTH_REQ` | `in_app_only` |
| 166 | `eVEHICLE_SET_PM25_MONITORING` | 949 | — | `oem_only_unverified` |
| 167 | `eVEHICLE_SET_DMS_MONITORING` | 950 | — | `oem_only_unverified` |
| 168 | `eVEHICLE_SET_SET_64COLOUR` | 951 | — | `oem_only_unverified` |
| 169 | `eVEHICLE_SET_STATIC_EFFECT` | 952 | — | `oem_only_unverified` |
| 170 | `eVEHICLE_SET_AMBIENTLIGHT_SCENEMODE` | 953 | — | `oem_only_unverified` |
| 171 | `eVEHICLE_SET_CSTFUNCTIONSTS` | 954 | — | `oem_only_unverified` |
| 172 | `eVEHICLE_SET_BRAKE_MODE` | 955 | — | `oem_only_unverified` |
| 173 | `eVEHICLE_SET_DRIVER_SEAT_BACK_FR` | 1281 | — | `oem_only_unverified` |
| 174 | `eVEHICLE_SET_DRIVER_SEAT_FR` | 1282 | — | `oem_only_unverified` |
| 175 | `eVEHICLE_SET_DRIVER_SEAT_FRONTENDUD` | 1283 | — | `oem_only_unverified` |
| 176 | `eVEHICLE_SET_DRIVER_SEAT_REARENDUD` | 1284 | — | `oem_only_unverified` |
| 177 | `eVEHICLE_SET_DRIVER_LUMBART_FR` | 1285 | — | `oem_only_unverified` |
| 178 | `eVEHICLE_SET_DRIVER_LUMBART_UD` | 1286 | — | `oem_only_unverified` |
| 179 | `eVEHICLE_SET_PASSENGER_SEAT_BACK_FR` | 1287 | — | `oem_only_unverified` |
| 180 | `eVEHICLE_SET_PASSENGER_SEAT_FR` | 1288 | — | `oem_only_unverified` |
| 181 | `eVEHICLE_SET_PASSENGER_SEAT_LEG_UD` | 1289 | — | `oem_only_unverified` |
| 182 | `eVEHICLE_SET_PASSENGER_MASSAGE_SWITCH` | 1290 | — | `oem_only_unverified` |
| 183 | `eVEHICLE_SET_PASSENGER_MASSAGE_STRENGTH` | 1291 | — | `oem_only_unverified` |
| 184 | `eVEHICLE_SET_PASSENGER_MASSAGE_MODE` | 1292 | — | `oem_only_unverified` |
| 185 | `eVEHICLE_SET_WIPER_MAINTENANCE_SWITCH` | 956 | `WIPER_MAINTENANCE_SWITCH` | `in_app_and_docs` |
| 186 | `eVEHICLE_SET_REAR_WIPER_SWITCH` | 957 | `REAR_WIPER` | `in_app_and_docs` |
| 187 | `eVEHICLE_SET_UNDER_VOLTAGE_TIP_SWITCH` | 958 | — | `oem_only_unverified` |
| 188 | `eVEHICLE_SET_MFS_HEAT_SWITCH` | 959 | `STEERING_WHEEL_HEAT_SWITCH` | `in_app_and_docs` |
| 189 | `eVEHICLE_SET_MFS_SHAKE_SWITCH` | 960 | — | `oem_only_unverified` |
| 190 | `eVEHICLE_SET_WELCOME_SEAT` | 961 | — | `oem_only_unverified` |
| 191 | `eVEHICLE_WIPER_SENSITIVITY` | 962 | `WIPER_SENSITIVITY` | `in_app_and_docs` |
| 192 | `eVEHICLE_WELCOME_SOUND_DEVICE_SWITCH` | 963 | — | `oem_only_unverified` |
| 193 | `eVEHICLE_WELCOME_SOUND_OPTIONS_SIGNAL` | 964 | — | `oem_only_unverified` |
| 194 | `eVEHICLE_R_SEAT_BELT_BUCKLE_SWITCH` | 965 | — | `oem_only_unverified` |
| 195 | `eVEHICLE_SET_RRM_SPEECH_CONTROL` | 966 | — | `oem_only_unverified` |
| 196 | `eVEHICLE_MIRROR_SET_LEFT_MIRROR_CTRL` | 1537 | — | `oem_only_unverified` |
| 197 | `eVEHICLE_MIRROR_SET_RIGHT_MIRROR_CTRL` | 1538 | — | `oem_only_unverified` |
| 198 | `eVEHICLE_MIRROR_SET_DRIVER_MIRROR_LOCATION` | 1539 | — | `oem_only_unverified` |
| 199 | `eVEHICLE_MIRROR_SET_PASSENGER_MIRROR_LOCATION` | 1540 | — | `oem_only_unverified` |
| 200 | `eVEHICLE_MIRROR_SET_LMIRROR_LR_LOCATON` | 1541 | — | `oem_only_unverified` |
| 201 | `eVEHICLE_MIRROR_SET_LMIRROR_UD_LOCATON` | 1542 | — | `oem_only_unverified` |
| 202 | `eVEHICLE_MIRROR_SET_RMIRROR_LR_LOCATON` | 1543 | — | `oem_only_unverified` |
| 203 | `eVEHICLE_MIRROR_SET_RMIRROR_UD_LOCATON` | 1544 | — | `oem_only_unverified` |
| 204 | `eVEHICLE_SMART_SCENE_MODE` | 967 | — | `oem_only_unverified` |
| 205 | `eVEHICLE_FATIGUE_DRIVING` | 968 | — | `oem_only_unverified` |
| 206 | `eVEHICLE_UNLOCK_ANIMATION` | 969 | — | `oem_only_unverified` |
| 207 | `eVEHICLE_LOCK_ANIMATION` | 970 | — | `oem_only_unverified` |
| 208 | `eVEHICLE_SET_ICM_BRIGHTNESS_MODE` | 971 | `ICM_BRIGHTNESS_MODE` | `in_app_and_docs` |
| 209 | `eVEHICLE_ICM_BRIGHTNESS_MANUAL_ADJ` | 972 | `ICM_BRIGHTNESS_MANUAL` | `in_app_and_docs` |
| 210 | `eVEHICLE_MFS_CRUISE_CONTROL` | 973 | `MFS_CRUISE_CONTROL` | `in_app_and_docs` |
| 211 | `eVEHICLE_MFS_SEEPD_LIMIT` | 974 | — | `oem_only_unverified` |
| 212 | `eVEHICLE_MFS_CANCEL` | 975 | `MFS_CANCEL` | `in_app_and_docs` |
| 213 | `eVEHICLE_MFS_RESPLUS` | 976 | `MFS_RES_PLUS` | `in_app_and_docs` |
| 214 | `eVEHICLE_MFS_SETMINUS` | 977 | `MFS_SET_MINUS` | `in_app_and_docs` |
| 215 | `eVEHICLE_RESERVED_MFS_SHIFT_UP` | 978 | — | `oem_only_unverified` |
| 216 | `eVEHICLE_RESERVED_MFS_SHIFT_DOWN` | 979 | — | `oem_only_unverified` |
| 217 | `eVEHICLE_MFS_TIME_GAP` | 980 | — | `oem_only_unverified` |
| 218 | `eVEHICLE_SET_PAS_SWITCH` | 981 | `PARKING_RADAR_SWITCH` | `in_app_and_docs` |
| 219 | `eVEHICLE_SET_RRM_DVD_RCTA_SWITCH` | 982 | — | `oem_only_unverified` |
| 220 | `eVEHICLE_SET_HUD_DISPLAY_SWITCH` | 1793 | `HUD_SWITCH` | `in_app_and_docs` |
| 221 | `eVEHICLE_SET_HUD_HEIGHT` | 1794 | `HUD_HEIGHT` | `in_app_and_docs` |
| 222 | `eVEHICLE_SET_HUD_BRIGHTNESS` | 1795 | `HUD_BRIGHTNESS` | `in_app_and_docs` |
| 223 | `eVEHICLE_SET_HUD_DISPLAYMODE` | 1796 | `HUD_DISPLAY_MODE` | `in_app_and_docs` |
| 224 | `eVEHICLE_SET_NAVIGATI_ONDISPLAY` | 1797 | — | `oem_only_unverified` |
| 225 | `eVEHICLE_SET_ADAS_DISPLAY` | 1798 | — | `oem_only_unverified` |
| 226 | `eVEHICLE_SET_BLUETOOTH_WECHART_DISPLAY` | 1799 | — | `oem_only_unverified` |
| 227 | `eVEHICLE_SET_AUTO_HUD_BRIGHTNESS` | 1800 | `HUD_AUTO_BRIGHTNESS` | `in_app_and_docs` |
| 228 | `eVEHICLE_SET_RRM_ACMAX_REQ` | 983 | `HVAC_AC_MAX` | `in_app_and_docs` |
| 229 | `eVEHICLE_PROPERTY_DRIVER_BRIGHTNESS` | 984 | — | `oem_only_unverified` |
| 230 | `eVEHICLE_SET_MIRROR_FOLD_SWITCH` | 985 | `MIRROR_FOLD_SWITCH` | `in_app_and_docs` |
| 231 | `eVEHICLE_SET_DWMCON_SWITCH` | 986 | — | `oem_only_unverified` |
| 232 | `eVEHICLE_SET_MIRROR_REVERSE_TURN_LOC` | 987 | — | `oem_only_unverified` |
| 233 | `eVEHICLE_EPB_APPLYREQ` | 1801 | — | `oem_only_unverified` |
| 234 | `eVEHICLE_LDW_WARNINGSOUND_SWITCH` | 1802 | — | `oem_only_unverified` |
| 235 | `eVEHICLE_TSR_SWITCH` | 1803 | — | `oem_only_unverified` |
| 236 | `eVEHICLE_PSNGAIRBAG_SWITCH` | 1804 | — | `oem_only_unverified` |
| 237 | `eVEHICLE_PDC_SWITCH` | 1805 | — | `oem_only_unverified` |
| 238 | `eVEHICLE_DRL_SWITCH` | 1806 | — | `oem_only_unverified` |
| 239 | `eVEHICLE_COMFORTMODESET` | 1807 | — | `oem_only_unverified` |
| 240 | `eVEHICLE_MIRROR_FOLDREQ` | 1808 | — | `oem_only_unverified` |
| 241 | `eVEHICLE_EMERGENCYPOWEROFF_CONFIRM` | 1809 | — | `oem_only_unverified` |
| 242 | `eVEHICLE_LIGHT_DECORATIVE_SWITCH` | 1810 | — | `oem_only_unverified` |
| 243 | `eVEHICLE_WINDOW_AUTOCLOSE_SWITCH` | 1811 | — | `oem_only_unverified` |
| 244 | `eVEHICLE_LIGHT_DOME_SWITCH` | 1812 | — | `oem_only_unverified` |
| 245 | `eVEHICLE_LIGHT_DOME_DOORCTRL_SWITCH` | 1813 | — | `oem_only_unverified` |
| 246 | `eVEHICLE_CESC_SWITCH` | 1814 | — | `oem_only_unverified` |
| 247 | `eVEHICLE_EHAC_SWITCH` | 1815 | — | `oem_only_unverified` |
| 248 | `eVEHICLE_EAVH_TIMESET` | 1816 | — | `oem_only_unverified` |
| 249 | `eVEHICLE_EAVH_REVERSE_DISABLE_SWITCH` | 1817 | — | `oem_only_unverified` |
| 250 | `eVEHICLE_INVERTER_CONFIRMSTS` | 1818 | — | `oem_only_unverified` |
| 251 | `eVEHICLE_INVERTER_SWITCH` | 1819 | — | `oem_only_unverified` |
| 252 | `eVEHICLE_VEHWASH_MODESET` | 1820 | `VEHICLE_VEHWASH_MODESET` | `in_app_only` |
| 253 | `eVEHICLE_SPEEDLIMIT_VALUESET` | 1821 | `VEHICLE_SPEEDLIMIT_VALUESET` | `in_app_and_docs` |
| 254 | `eVEHICLE_SPEEDLIMIT_SWITCH` | 1822 | `VEHICLE_SPEEDLIMIT_SWITCH` | `in_app_and_docs` |
| 255 | `eVEHICLE_REGENERATION_LEVELSET` | 1823 | — | `in_docs_only` |
| 256 | `eVEHICLE_OUTPUT_LIMITSOCSET` | 1824 | — | `oem_only_unverified` |
| 257 | `eVEHICLE_SHIFT_VOICEREMIND_SWITCH` | 1825 | — | `oem_only_unverified` |
| 258 | `eVEHICLE_SHIFT_ERRORACTION_REMIND_SWITCH` | 1826 | — | `oem_only_unverified` |
| 259 | `eVEHICLE_HVAC_PTC_SWITCH` | 2049 | — | `oem_only_unverified` |
| 260 | `eVEHICLE_HVAC_CIRCULATION_SWITCH` | 2050 | — | `oem_only_unverified` |
| 261 | `eVEHICLE_HVAC_AUTOCLEAN_SWITCH` | 2051 | — | `oem_only_unverified` |
| 262 | `eVEHICLE_HVAC_MEMORYMODE_SWITCH` | 2052 | — | `oem_only_unverified` |
| 263 | `eVEHICLE_MIRROR_REVERSEPOSITION_STOREREQ` | 1827 | — | `oem_only_unverified` |
| 264 | `eVEHICLE_CHG_WIRELESS_SWITCH` | 1828 | `CHG_WIRELESS_SWITCH` | `in_app_and_docs` |
| 265 | `eVEHICLE_AVAS_VOLUMESET` | 1829 | — | `oem_only_unverified` |
| 266 | `eVEHICLE_AVAS_AUDIOSOURCESET` | 1830 | — | `oem_only_unverified` |
| 267 | `eVEHICLE_SEAT_FOLDREQ` | 1831 | — | `oem_only_unverified` |
| 268 | `eVEHICLE_SEAT_RELEASEREQ` | 1832 | — | `oem_only_unverified` |
| 269 | `eVEHICLE_AVAS_SWITCH` | 1833 | — | `oem_only_unverified` |
| 270 | `eVEHICLE_SEAT_POSITION_STOREREQ` | 1834 | — | `oem_only_unverified` |
| 271 | `eVEHICLE_SEAT_POSITION_CALLOUTREQ` | 1835 | — | `oem_only_unverified` |
| 272 | `eVEHICLE_FACEREC_SEAT_SWITCH` | 1836 | — | `oem_only_unverified` |
| 273 | `eVEHICLE_FACEREC_MIRRORINCLINE_SWITCH` | 1837 | — | `oem_only_unverified` |
| 274 | `eVEHICLE_FACEREC_AUTHENTICATION_RESULT` | 1838 | — | `oem_only_unverified` |
| 275 | `eVEHICLE_ACCOUNT_DELETEREQ` | 1839 | — | `oem_only_unverified` |
| 276 | `eVEHICLE_ENTRYEXITSEAT_CTRL_SWITCH` | 1840 | — | `oem_only_unverified` |
| 277 | `eVEHICLE_ICM_THEMESET` | 2305 | — | `oem_only_unverified` |
| 278 | `eVEHICLE_ICM_SCREENREQ` | 2306 | — | `oem_only_unverified` |
| 279 | `eVEHICLE_ICM_MENU_OK_BUTTONSTS` | 2307 | — | `oem_only_unverified` |
| 280 | `eVEHICLE_ICM_MODE_BUTTONSTS` | 2308 | — | `oem_only_unverified` |
| 281 | `eVEHICLE_ICM_UP_BUTTONSTS` | 2309 | — | `oem_only_unverified` |
| 282 | `eVEHICLE_ICM_DOWN_BUTTONSTS` | 2310 | — | `oem_only_unverified` |
| 283 | `eVEHICLE_ICM_LEFT_BUTTONSTS` | 2311 | — | `oem_only_unverified` |
| 284 | `eVEHICLE_ICM_RIGHT_BUTTONSTS` | 2312 | — | `oem_only_unverified` |
| 285 | `eVEHICLE_ICM_BRIGHTNESS_ADJUSTSET` | 2313 | — | `oem_only_unverified` |
| 286 | `eVEHICLE_ICM_FATIGUREDRIVING_TIMESET` | 2314 | — | `oem_only_unverified` |
| 287 | `eVEHICLE_BSD_WARNINGSOUND_SWITCH` | 2315 | — | `oem_only_unverified` |
| 288 | `eVEHICLE_CHG_SOC_LIMITPOINTSET` | 2561 | — | `oem_only_unverified` |
| 289 | `eVEHICLE_CHG_MODESET` | 2562 | — | `oem_only_unverified` |
| 290 | `eVEHICLE_CHG_BOOK_STARTTIME_HOUR` | 2563 | — | `oem_only_unverified` |
| 291 | `eVEHICLE_CHG_BOOK_STARTTIME_MINUTE` | 2564 | — | `oem_only_unverified` |
| 292 | `eVEHICLE_CHG_BOOK_STOPTIME_HOUR` | 2565 | — | `oem_only_unverified` |
| 293 | `eVEHICLE_CHG_BOOK_STOPTIME_MINUTE` | 2566 | — | `oem_only_unverified` |
| 294 | `eVEHICLE_CST_SENSITIVITYREQ` | 988 | — | `oem_only_unverified` |
| 295 | `eVEHICLE_IPB_ASSOCIATE_DRIVE_MODE` | 989 | — | `oem_only_unverified` |
| 296 | `eVEHICLE_OVERSPEEDALARM_SET` | 991 | `OVERSPEED_ALARM_SET` | `in_app_and_docs` |
| 297 | `eVEHICLE_FATIGUEDRIVINGREMINDER_SET` | 992 | — | `oem_only_unverified` |
| 298 | `eVEHICLE_TRIP_RESET` | 993 | — | `oem_only_unverified` |
| 299 | `eVEHICLE_GPS_TIMESWSTS` | 994 | — | `oem_only_unverified` |
| 300 | `eVEHICEL_BRAKE_PEDA_FEEL_MODE` | 995 | `VEHICEL_BRAKE_PEDA_FEEL_MODE` | `in_app_only` |
| 301 | `eVEHICLE_SET_SOC_MANAGE` | 996 | — | `oem_only_unverified` |
| 302 | `eVEHICLE_REGENERATE_LEVEL_CTRL` | 997 | — | `oem_only_unverified` |
| 303 | `eVEHICLE_SET_SOC_VALUE` | 998 | — | `oem_only_unverified` |
| 304 | `eVHEICEL_VOICE_WAKE` | 999 | — | `oem_only_unverified` |
| 305 | `eVHEICEL_SET_CHRGN_FCT_MEM` | 1001 | — | `oem_only_unverified` |
| 306 | `eVHEICEL_ECAVERHISCLEAR` | 1002 | — | `oem_only_unverified` |
| 307 | `eVHEICEL_PTREADY` | 1003 | — | `oem_only_unverified` |
| 308 | `eVHEICEL_POWERTRAINFAULT` | 1004 | — | `oem_only_unverified` |
| 309 | `eVHEICEL_NAVI_STATUS` | 2817 | — | `oem_only_unverified` |
| 310 | `eVHEICEL_NAVI_TURNTYPE` | 2818 | — | `oem_only_unverified` |
| 311 | `eVHEICEL_NAVI_DISTANCE` | 2819 | — | `oem_only_unverified` |
| 312 | `eVHEICEL_NAVI_TRAFFICSTS` | 2820 | — | `oem_only_unverified` |
| 313 | `eVHEICEL_NAVI_NEXTTRAFFICSTS` | 2821 | — | `oem_only_unverified` |
| 314 | `eVHEICEL_NAVI_ASSOCIATE_LIGHT_ATMO` | 2822 | — | `oem_only_unverified` |
| 315 | `eVHEICEL_DYNAMIC_RESIDUAL_ODOMETER_SWITCH` | 1841 | — | `oem_only_unverified` |
| 316 | `eVHEICEL_FRONTWINDSCREEN_HEAT` | 1005 | `FRONT_WINDSCREEN_HEAT_SWITCH` | `in_app_and_docs` |
| 317 | `eVHEICEL_HVAL_COOLANTFILL` | 2053 | — | `oem_only_unverified` |
| 318 | `eVHEICEL_SEAT_LR_HEATVENTSW` | 1293 | `REAR_LEFT_SEAT_HEAT_SWITCH` | `in_app_and_docs` |
| 319 | `eVHEICEL_SEAT_RR_HEATVENTSW` | 1294 | `REAR_RIGHT_SEAT_HEAT_SWITCH` | `in_app_and_docs` |
| 320 | `eVHEICEL_SEAT_QUEENOPENSTS` | 1295 | — | `oem_only_unverified` |
| 321 | `eVHEICEL_SEAT_QUEENCLOSESTS` | 1296 | — | `oem_only_unverified` |
| 322 | `eVHEICEL_HVREADY` | 1006 | — | `oem_only_unverified` |
| 323 | `eVHEICEL_DOOR_WINDOWCTRL_SWITCH` | 1842 | — | `oem_only_unverified` |
| 324 | `eTBOX_REMOTEPOWER_ONOFFREQ` | 1843 | — | `in_docs_only` |

## Audio properties (`MBCan_Audio_Get/Set`)

Native table: id **1…37**. Id **14–16** = `0xFFFFFFFF` (нет mapping). Enum: max id **37**, `eAUDIO_PROPERTY_COUNT=38`; в native table 37 слотов (id 14–16 = invalid).

| ID | OEM enum | nItem | Native | App const | Статус |
|----|----------|------:|:------:|-----------|--------|
| 1 | `eAUDIO_PROPERTY_SOURCE` | 513 | yes | — | `in_docs_only` |
| 2 | `eAUDIO_PROPERTY_VOLUME` | 514 | yes | `VOLUME` | `in_app_and_docs` |
| 3 | `eAUDIO_PROPERTY_BALANCE_BALANCE` | 515 | yes | `BALANCE` | `in_app_and_docs` |
| 4 | `eAUDIO_PROPERTY_BALANCE_FADER` | 516 | yes | `FADER` | `in_app_and_docs` |
| 5 | `eAUDIO_PROPERTY_EQBAND_BASS` | 517 | yes | `EQ_BAND_BASS` | `in_app_and_docs` |
| 6 | `eAUDIO_PROPERTY_EQBAND_MIDDLE` | 518 | yes | `EQ_BAND_MIDDLE` | `in_app_and_docs` |
| 7 | `eAUDIO_PROPERTY_EQBAND_TREBLE` | 519 | yes | `EQ_BAND_TREBLE` | `in_app_and_docs` |
| 8 | `eAUDIO_PROPERTY_SOFTMUTE` | 520 | yes | — | `in_docs_only` |
| 9 | `eAUDIO_PROPERTY_AMPMUTE` | 521 | yes | — | `in_docs_only` |
| 10 | `eAUDIO_PROPERTY_EQMODE` | 523 | yes | `EQ_MODE` | `in_app_and_docs` |
| 11 | `eAUDIO_PROPERTY_VOLUME_RADAS` | 524 | yes | `VOLUME_RADAR` | `in_app_and_docs` |
| 12 | `eAUDIO_PROPERTY_VOLUME_INSTRCUMENT` | 525 | yes | — | `in_docs_only` |
| 13 | `eAUDIO_PROPERTY_VOLUME_SPEED` | 526 | yes | `VOLUME_SPEED` | `in_app_and_docs` |
| 14 | `eAUDIO_PROPERTY_MIX` | — | invalid | — | `oem_only_unverified` |
| 15 | `eVEHICLE_PROPERTY_XFMIC_MODE` | — | invalid | — | `in_docs_only` |
| 16 | `eAUDIO_PROPERTY_LOUNDNESS_MODE` | — | invalid | — | `in_docs_only` |
| 17 | `eAUDIO_PROPERTY_VOLUME_KEY` | 528 | yes | `VOLUME_KEY` | `in_app_and_docs` |
| 18 | `eAUDIO_PROPERTY_VOLUME_PHONE` | 529 | yes | — | `in_docs_only` |
| 19 | `eAUDIO_PROPERTY_VOLUME_NAVI` | 530 | yes | — | `in_docs_only` |
| 20 | `eAUDIO_PROPERTY_VOLUME_VOICE` | 531 | yes | — | `in_docs_only` |
| 21 | `eAUDIO_PROPERTY_ACOUSTIC_FIELD_MODE` | 539 | yes | — | `in_docs_only` |
| 22 | `eAUDIO_PROPERTY_SOUNDWAVE_MODE` | 540 | yes | — | `in_docs_only` |
| 23 | `eAUDIO_PROPERTY_SURROUND` | 541 | yes | — | `in_docs_only` |
| 24 | `eAUDIO_PROPERTY_ACOUSTIC_MODE` | 542 | yes | — | `oem_only_unverified` |
| 25 | `eAUDIO_PROPERTY_LOUDNESS` | 543 | yes | — | `in_docs_only` |
| 26 | `eAUDIO_PROPERTY_RESET` | 544 | yes | — | `oem_only_unverified` |
| 27 | `eAUDIO_PROPERTY_MUSICLOUDNESS_120HZ` | 532 | yes | — | `oem_only_unverified` |
| 28 | `eAUDIO_PROPERTY_MUSICLOUDNESS_250HZ` | 533 | yes | — | `oem_only_unverified` |
| 29 | `eAUDIO_PROPERTY_MUSICLOUDNESS_500HZ` | 534 | yes | — | `oem_only_unverified` |
| 30 | `eAUDIO_PROPERTY_MUSICLOUDNESS_1000HZ` | 535 | yes | — | `in_docs_only` |
| 31 | `eAUDIO_PROPERTY_MUSICLOUDNESS_2000HZ` | 537 | yes | — | `oem_only_unverified` |
| 32 | `eAUDIO_PROPERTY_MUSICLOUDNESS_6000HZ` | 538 | yes | — | `oem_only_unverified` |
| 33 | `eAUDIO_PROPERTY_MUSICLOUDNESS_1500HZ` | 536 | yes | — | `in_docs_only` |
| 34 | `eAUDIO_PROPERTY_DEFAULT_MAX_VOLUME` | 545 | yes | — | `in_docs_only` |
| 35 | `eAUDIO_PROPERTY_FACTORY` | 902 | yes | — | `in_docs_only` |
| 36 | `eAUDIO_PROPERTY_VOLUME_BCALL` | 547 | yes | — | `in_docs_only` |
| 37 | `eAUDIO_AUDIO_HEADREST_SPEAKER` | 548 | yes | `HEADREST_SPEAKER` | `in_app_and_docs` |

## Vehicle: в приложении, нет в справочнике параметров

| ID | OEM enum | App const |
|----|----------|-----------|
| 24 | `eVEHICLE_PROPERTY_STEERING_MODE` | `VEHICLE_PROPERTY_STEERING_MODE` |
| 47 | `eVEHICLE_PROPERTY_WINDOW_POS` | `WINDOW_POS` |
| 55 | `eVEHICLE_PROPERTY_FRWINDOW_POS` | `WINDOW_FR_POS` |
| 56 | `eVEHICLE_PROPERTY_FLWINDOW_POS` | `WINDOW_FL_POS` |
| 57 | `eVEHICLE_PROPERTY_RRWINDOW_POS` | `WINDOW_RR_POS` |
| 58 | `eVEHICLE_PROPERTY_RLWINDOW_POS` | `WINDOW_RL_POS` |
| 73 | `eSYSTEM_MODE` | `SYSTEM_MODE` |
| 127 | `eSOURCE_STATION_MODE` | `SOURCE_STATION_MODE` |
| 147 | `eVEHICLE_POWERMODE` | `VEHICLE_POWERMODE` |
| 163 | `eVEHICLE_PM25_DISPLAY_TOGGLE` | `VEHICLE_PM25_DISPLAY_TOGGLE` |
| 164 | `eVEHICLE_UV_LAMP_REQ` | `VEHICLE_UV_LAMP_REQ` |
| 165 | `eVEHICLE_STERILIZE_STRENGTH_REQ` | `VEHICLE_STERILIZE_STRENGTH_REQ` |
| 252 | `eVEHICLE_VEHWASH_MODESET` | `VEHICLE_VEHWASH_MODESET` |
| 300 | `eVEHICEL_BRAKE_PEDA_FEEL_MODE` | `VEHICEL_BRAKE_PEDA_FEEL_MODE` |

## Vehicle: в справочнике без MbCanKnown* константы

| ID | OEM enum |
|----|----------|
| 5 | `eVEHICLE_PROPERTY_MIRROR_REVERSE_TURN` |
| 9 | `eVEHICLE_PROPERTY_EMERGENCY_STOP_LIGHTS` |
| 10 | `eVEHICLE_PROPERTY_NFC` |
| 11 | `eVEHICLE_PROPERTY_LASER` |
| 21 | `eVEHICLE_PROPERTY_CRUISE_ACC_FCW_WARN_SET` |
| 30 | `eVEHICLE_PROPERTY_MUSICAL_RHYTHM` |
| 87 | `eAVM_WORK_MODESTS` |
| 102 | `eRRM_RESOLUTATION_Y` |
| 130 | `eVEHICLE_SMART_HIGHBEAM_SWITCH` |
| 255 | `eVEHICLE_REGENERATION_LEVELSET` |
| 324 | `eTBOX_REMOTEPOWER_ONOFFREQ` |

## Пересборка

```bash
python3 tools/mbcan_so_catalog.py
```

