# Штатные параметры (VHAL) с Read/Write ID и значениями

Источник: декомпилированные исходники `D:\Dashing\SystemSettings`, `D:\Dashing\CarSettings`, `D:\Dashing\AirConditioning`, `D:\Dashing\Launcher`.

Обозначения:
- **Read ID**: читается/слушается в штатке.
- **Write ID**: пишется штаткой.
- **Значения**: только то, что явно подтверждено кодом штатных приложений; если не найдено — помечено как `неявно/не найдено`.

## SystemSettings

| Наименование (RU) | Read ID | Write ID | Тех. имя | Значения |
|---|---:|---:|---|---|
| Компенсация громкости по скорости | 557849227 | 557849227 | `AUDIO_VOL_VSC_MOD_REQ` | `1=off`, `2=low`, `3=mid`, `4=high` |
| Режим движения | 289412123 | 289412695 | `R_0400_TCU_G_DriverMode_7` / `T_0401_IHU_9_DriveMode` | `неявно` |
| Яркость приборки (ручная) | 289414939 | 289415087 | `R_0900_ICM_4_BrightnessFed` / `T_0901_IHU_ICMBrightnessManualAdj` | `неявно` |
| Режим яркости приборки | 289415088 | 289415088 | `T_0901_IHU_SET_ICMBrightnessMode` | `неявно (авто/ручной)` |
| Управление светом | — | 289412613 | `T_0405_SET_Lightcontrol` | `1..4` |
| Передний ПТФ | 289412133 | 289412614 | `R_0400_CEM_2_FrontFogLightSts` / `T_0405_Set_FrontFogLights` | `1/2` |
| Задний ПТФ | 289412136 | 289412612 | `R_0400_CEM_2_RearFogLightSts` / `T_0405_SET_Rearfoglight` | `1/2` |
| A/C и климат-статусы (`R_0200_CEM_IPM_*`) | 289415168..289415182 | — | `R_0200_CEM_IPM_*` | `статусные int, диапазон не задан` |
| PM2.5 (плотность/ошибка/уровень) | 289412224/231/230 | — | `R_0400_PM2_5_*` | `статусные int` |
| Подогрев руля статус | 289412111 | — | `R_0400_RBCM_MFS_HeatSts` | `неявно` |
| EPS/ESP/HDC/AVH статусы | 289412124/118/117/184 | — | `R_0400_EPS_1_*`, `R_0400_ESP_*` | `статусные int` |
| Беспроводная зарядка статусы | 289412217/218/219 | — | `R_0400_WCM_*` | `статусные int` |
| Скорость MCU | 557845547 | — | `MCU_REPLY_SPEED` | `telemetry` |
| Очистка логов | 289412344 | 289412726 | `R_0400_clearLogs_Result` / `T_0401_clearLogs_Requset` | write: `1` (запрос) |

## CarSettings (ключевые write-параметры с явно видимыми значениями)

| Наименование (RU) | Read ID | Write ID | Тех. имя | Значения |
|---|---:|---:|---|---|
| Режим движения | 289412123 | 289412695 | `R_0400_TCU_G_DriverMode_7` / `T_0401_IHU_9_DriveMode` | `0..6` (в UI точно `0/1/2`) |
| Режим КПП 6DCT | — | 289412692 | `T_0401_IHU_9_DriveMode_6DCT_Wet` | `0/1/2` |
| Чувствительность дворников | 289412140 | 289412688 | `R_0400_CEM_2_WiperSensitivitySWSts` / `T_0401_SET_Wiper_Sensitivity` | `1..4` |
| Режим обслуживания дворников | 289412194 | 289412682 | `R_0400_CEM_Wiper_MaintenanceSts` / `T_0401_SET_Wiper_Maintenance` | `1/2` |
| Задний дворник | 289412193 | `T_0401_SET_RearWiper_Switch` | `R_0400_CEM_RearWiper_SwitchSts` | `1/2` |
| Сигнал превышения скорости | 289414912 (связанные статусы) | 289415091 | `T_0901_IHU_21_OverspeedAlarm_Set` | `(speed-30)/5` |
| Auto Lock | 289412149 | `T_0401_IHU_1_DVD_SET_AutoLockSts` | `R_0400_CEM_2_AutoLockSts` | `1/2` |
| Auto Unlock | 289412143 | `T_0401_IHU_1_DVD_SET_AutoUnlockSts` | `R_0400_CEM_2_AutoUnlockSts` | `1/2` |
| Follow me home | 289412130 | `T_0401_IHU_1_DVD_SET_FollowMeHome` | `R_0400_CEM_2_FollowMeHomeTimeSts` | `1/2/3` |
| Driver unlock mode | 289412214 | `T_0405_SET_Driver_Unlockmode` | `R_0400_CEM_3_DHM_Driver_Unlockmode_Feed` | `1/2` |
| Remote lock feedback | 289412144 | `T_0401_IHU_1_DVD_SET_RemoteLockFeedback` | `R_0400_CEM_2_RemoteLockFeedbackSts` | `1/2/3` |

> Примечание: в `CarSettings` очень много дополнительных write-команд (сиденья, ambient, ADAS, окна, багажник). Для них в коде явно видны значения (`0/1/2/3/...`), но они не все имеют прямую и стабильную read-пару в том же фрагменте.

## AirConditioning

| Наименование (RU) | Read ID | Write ID | Тех. имя | Значения |
|---|---:|---:|---|---|
| Шумоподавление BT | 289415190 | 289412667 | `R_0200_CEM_IPM_BT_Reduce_Wind_SpeedSts` / `T_0401_IHU_1_SET_BT_Reduce_Wind_Speed` | `1=on`, `2=off` |
| Задний обогрев | 289415177 | `T_0201_IHU_5_RearDefrostSwitch_Req` | `R_0200_CEM_IPM_RearDefrosts` | `1/2` |
| A/C | 289415173 | `T_0201_IHU_5_ACRequestCommand` | `R_0200_CEM_IPM_ACStatus` | `1/2` |
| MAX A/C | 289412209 | `T_0401_SET_IHU_ACMAXReq` | `R_0400_CEM_IPM_3_ACMAXReq_Sts` | `1/2` |
| SYNC | 289415181 | `T_0201_IHU_5_SyncSwtich_Req` | `R_0200_CEM_IPM_SyncSts` | `1/2` |
| Front OFF | 289415175 | `T_0201_IHU_5_FrontOFF_Req` | `R_0200_CEM_IPM_FrontOFFSts` | `1/2` |
| Рециркуляция | 289415172 | `T_0201_IHU_5_CirculationMode_Req` | `R_0200_CEM_IPM_RecyMode` | `1/2` |
| Режим энергосбережения климата | 289415186 | `T_0201_SET_IPMCustom_Air_Conditioning` | `R_0200_CEM_IPM_Custom_Air_Conditioning` | read: `0..2`, write: `1..3` |
| Auto ventilation | 289415187 | `T_0401_SET_IPM_Automatic_Ventilation` | `R_0200_CEM_IPM_Automatic_Ventilation` | `1/2` |
| First blowing | 289415188 | `T_0401_IHU_1_DVD_SET_IPM_First_Blowing` | `R_0200_CEM_IPM_First_BlowingSts` | `1/2` |
| Blower delay | 289415189 | `T_0401_IHU_1_DVD_SET_IPM_Blower_Delay` | `R_0200_CEM_IPM_Blower_DelaySts` | `1/2` |

## Launcher

| Наименование (RU) | Read ID | Write ID | Тех. имя | Значения |
|---|---:|---:|---|---|
| ACC статус (MCU) | 557845540 | — | `MCU_REPLY_ACC_STATUS` | `status` |
| Скорость (MCU) | 557845547 | — | `MCU_REPLY_SPEED` | `telemetry` |
| Дистанции радаров (8 зон) | 289411329..289411336 | — | `Radar_*_Obstacle_Distance` | `distance raw` |
| Уровень омывайки | 289412346 | — | `R_0400_RBCM_1_WashingLiquid_LevelSts` | `status` |

## Что означает «значения параметра»

- Для переключателей штатка почти везде использует `1/2` (иногда `0/1` для отдельных параметров).
- Для режимов используются дискретные уровни (`0..N` или перечисления).
- Для telemetry/status параметров диапазон часто не ограничивается в UI-коде (поэтому в таблице пометка `status/raw`).

## Полный плоский список (по запрошенному формату)

Формат: `[App] Тех./RU имя | Read ID | Write ID | Значения`.

### SystemSettings

- `[SystemSettings] AUDIO_VOL_VSC_MOD_REQ | 557849227 | 557849227 | 1=off, 2=low, 3=mid, 4=high`
- `[SystemSettings] R_0400_clearLogs_Result / T_0401_clearLogs_Requset | 289412344 | 289412726 | write: 1`
- `[SystemSettings] R_0400_TCU_G_DriverMode_7 / T_0401_IHU_9_DriveMode | 289412123 | 289412695 | значения не найдены`
- `[SystemSettings] R_0900_ICM_4_BrightnessFed / T_0901_IHU_ICMBrightnessManualAdj | 289414939 | 289415087 | значения не найдены`
- `[SystemSettings] T_0901_IHU_SET_ICMBrightnessMode | 289415088 | 289415088 | значения не найдены`
- `[SystemSettings] T_0405_SET_Lightcontrol | — | 289412613 | 1..4`
- `[SystemSettings] R_0400_CEM_2_FrontFogLightSts / T_0405_Set_FrontFogLights | 289412133 | 289412614 | 1/2`
- `[SystemSettings] R_0400_CEM_2_RearFogLightSts / T_0405_SET_Rearfoglight | 289412136 | 289412612 | 1/2`
- `[SystemSettings] MCU_REPLY_SPEED | 557845547 | — | telemetry`
- `[SystemSettings] R_0200_CEM_IPM_FRTempsts | 289415168 | — | значения не найдены`
- `[SystemSettings] R_0200_CEM_IPM_FLTempsts | 289415169 | — | значения не найдены`
- `[SystemSettings] R_0200_CEM_IPM_FrontBlowSpdCtrlsts | 289415171 | — | значения не найдены`
- `[SystemSettings] R_0200_CEM_IPM_RecyMode | 289415172 | — | значения не найдены`
- `[SystemSettings] R_0200_CEM_IPM_ACStatus | 289415173 | — | значения не найдены`
- `[SystemSettings] R_0200_CEM_IPM_FrontBlowModeSts | 289415174 | — | значения не найдены`
- `[SystemSettings] R_0200_CEM_IPM_FrontOFFSts | 289415175 | — | значения не найдены`
- `[SystemSettings] R_0200_CEM_IPM_RearDefrosts | 289415177 | — | значения не найдены`
- `[SystemSettings] R_0200_CEM_IPM_TempknobRollingCounter | 289415179 | — | значения не найдены`
- `[SystemSettings] R_0200_CEM_IPM_SyncSts | 289415181 | — | значения не найдены`
- `[SystemSettings] R_0200_CEM_IPM_FrontAutoACSts | 289415182 | — | значения не найдены`
- `[SystemSettings] R_0202_CEM_IPM_FLSeatHeatVentSwSts | 289415205 | — | значения не найдены`
- `[SystemSettings] R_0202_CEM_IPM__FRSeatHeatVentSwSts | 289415204 | — | значения не найдены`
- `[SystemSettings] R_0202_RBCM_2_LRSeatHeatVentSwSts | 289415203 | — | значения не найдены`
- `[SystemSettings] R_0202_RBCM_2__RRSeatHeatVentSwSts | 289415202 | — | значения не найдены`
- `[SystemSettings] R_0400_EPS_1_EPSModeSts | 289412124 | — | значения не найдены`
- `[SystemSettings] R_0400_ESP_1_VDCControlSts | 289412118 | — | значения не найдены`
- `[SystemSettings] R_0400_ESP_1_HDCCtrlSts | 289412117 | — | значения не найдены`
- `[SystemSettings] R_0400_ESP_3_AVHSts | 289412184 | — | значения не найдены`
- `[SystemSettings] R_0400_RBCM_MFS_HeatSts | 289412111 | — | значения не найдены`
- `[SystemSettings] R_0400_PM2_5_Indensity | 289412224 | — | значения не найдены`
- `[SystemSettings] R_0400_PM2_5_ErrSts | 289412231 | — | значения не найдены`
- `[SystemSettings] R_0400_PM2_5_AirInQLevel | 289412230 | — | значения не найдены`
- `[SystemSettings] R_0400_CEM_IPM_3_ExternalTemperatureRaw | 289412223 | — | значения не найдены`
- `[SystemSettings] R_0400_WCM_PhoneDetection_Status | 289412217 | — | значения не найдены`
- `[SystemSettings] R_0400_WCM_WirelessCharging_Status | 289412218 | — | значения не найдены`
- `[SystemSettings] R_0400_WCM_WirelessChargingSet_Status | 289412219 | — | значения не найдены`
- `[SystemSettings] R_0404_CEM_Smart_HighBeamSts | 289412260 | — | значения не найдены`
- `[SystemSettings] R_0404_CEM_2_LowBeamSts | 289412250 | — | значения не найдены`
- `[SystemSettings] R_0404_CEM_2_HighBeamSts | 289412252 | — | значения не найдены`
- `[SystemSettings] R_0404_CEM_2_ParkTailLightSts | 289412253 | — | значения не найдены`
- `[SystemSettings] R_0402_PLG_1_SAS_Sts | 289412274 | — | значения не найдены`

### CarSettings

- `[CarSettings] R_0400_EPS_1_EPSModeSts | 289412124 | — | значения не найдены`
- `[CarSettings] R_0400_TCU_G_DriverMode_7 / T_0401_IHU_9_DriveMode | 289412123 | 289412695 | write: 0/1/2`
- `[CarSettings] T_0401_IHU_9_DriveMode_6DCT_Wet | — | 289412692 | 0/1/2`
- `[CarSettings] R_0400_ESP_3_AVHSts | 289412184 | — | значения не найдены`
- `[CarSettings] R_0400_ESP_1_VDCControlSts | 289412118 | — | значения не найдены`
- `[CarSettings] R_0400_ESP_1_HDCCtrlSts | 289412117 | — | значения не найдены`
- `[CarSettings] STEERING_WHEEL_HEAT_STS / STEERING_WHEEL_HEAT_REQ | 289414814 | 289414813 | 1/2`
- `[CarSettings] R_0400_CEM_Wiper_MaintenanceSts / T_0401_SET_Wiper_Maintenance | 289412194 | 289412682 | 1/2`
- `[CarSettings] R_0400_CEM_2_WiperSensitivitySWSts / T_0401_SET_Wiper_Sensitivity | 289412140 | 289412688 | 1/2/3/4`
- `[CarSettings] R_0400_CEM_RearWiper_SwitchSts / T_0401_SET_RearWiper_Switch | 289412193 | значения не найдены | 1/2`
- `[CarSettings] T_0901_IHU_21_OverspeedAlarm_Set | 289414912 (связ.) | 289415091 | (speed-30)/5`
- `[CarSettings] R_0400_CEM_2_AutoLockSts / T_0401_IHU_1_DVD_SET_AutoLockSts | 289412149 | значения не найдены | 1/2`
- `[CarSettings] R_0400_CEM_2_AutoUnlockSts / T_0401_IHU_1_DVD_SET_AutoUnlockSts | 289412143 | значения не найдены | 1/2`
- `[CarSettings] R_0400_CEM_2_RemoteLockFeedbackSts / T_0401_IHU_1_DVD_SET_RemoteLockFeedback | 289412144 | значения не найдены | 1/2/3`
- `[CarSettings] R_0400_CEM_2_FollowMeHomeTimeSts / T_0401_IHU_1_DVD_SET_FollowMeHome | 289412130 | значения не найдены | 1/2/3`
- `[CarSettings] R_0400_CEM_3_DHM_Driver_Unlockmode_Feed / T_0405_SET_Driver_Unlockmode | 289412214 | значения не найдены | 1/2`
- `[CarSettings] R_0400_CEM_3_DHM_Doorkonbmode_Feedback / T_0403_SET_Doorkonbmode | 289412213 | значения не найдены | 1/2`
- `[CarSettings] R_0402_DHM_1_Doorkonb_Time_Freeback / T_0403_SET_Doorkonb_Time | 289412284 | значения не найдены | 1/2/3`
- `[CarSettings] R_0400_CEM_MirrorFold_switchSts / T_0401_SET_Mirror_Fold_Switch | 289412195 | значения не найдены | 1/2`
- `[CarSettings] R_0400_CEM_MirrorFlip_CFGSts / T_0401_SET_Mirror_Flip_CFG | 289412189 | значения не найдены | 1/2/3/4`
- `[CarSettings] R_0400_CEM_2_FrontFogLightSts / T_0405_Set_FrontFogLights | 289412133 | 289412614 | 1/2`
- `[CarSettings] R_0400_CEM_2_RearFogLightSts / T_0405_SET_Rearfoglight | 289412136 | 289412612 | 1/2`
- `[CarSettings] R_0404_CEM_2_BlankingnumberSts / T_0401_IHU_1_DVD_SET_Blankingnumber | 289412257 | значения не найдены | 1/2/3`
- `[CarSettings] R_0404_CEM_Smart_HighBeamSts / T_0B01_IHU_8_HMAOnOffReq | 289412260 | значения не найдены | 0/1`
- `[CarSettings] R_0B00_FRM_3_FCW_OPTION_Sts / T_0B01_IHU_8_FCW_OPTION | 289415697 | значения не найдены | 1/2/3`
- `[CarSettings] R_0B00_SRR_1_BSDState / T_0901_IHU_3_BSDSwitch | 289415723 | значения не найдены | 1/2`
- `[CarSettings] R_0B00_SRR_1_DOWSts / T_0901_IHU_3_DVD_Set_DOW | 289415729 | значения не найдены | 1/2`
- `[CarSettings] R_0B00_FCM_2_TJA_ICA_ON_OFF_Sts / T_0B01_IHU_8_TJA_ICA_ON_OFF | 289415716 | значения не найдены | 1/2`

### AirConditioning

- `[AirConditioning] R_0200_CEM_IPM_FRTempsts / T_0201_IHU_5_R_Set_Temperature | 289415168 | значения не найдены | значения не найдены`
- `[AirConditioning] R_0200_CEM_IPM_FLTempsts / T_0201_IHU_5_L_Set_Temperature | 289415169 | значения не найдены | значения не найдены`
- `[AirConditioning] R_0200_CEM_IPM_FrontBlowSpdCtrlsts / T_0201_IHU_5_BlowSpeedLevel_Req | 289415171 | значения не найдены | значения не найдены`
- `[AirConditioning] R_0200_CEM_IPM_RecyMode / T_0201_IHU_5_CirculationMode_Req | 289415172 | значения не найдены | 1/2`
- `[AirConditioning] R_0200_CEM_IPM_ACStatus / T_0201_IHU_5_ACRequestCommand | 289415173 | значения не найдены | 1/2`
- `[AirConditioning] R_0200_CEM_IPM_FrontBlowModeSts / T_0201_IHU_5_ModeAdjust_Req | 289415174 | значения не найдены | 0..4`
- `[AirConditioning] R_0200_CEM_IPM_FrontOFFSts / T_0201_IHU_5_FrontOFF_Req | 289415175 | значения не найдены | 1/2`
- `[AirConditioning] R_0200_CEM_IPM_RearDefrosts / T_0201_IHU_5_RearDefrostSwitch_Req | 289415177 | значения не найдены | 1/2`
- `[AirConditioning] R_0200_CEM_IPM_TempknobRollingCounter / T_0201_IHU_5_TempknobRollingCounterReq | 289415179 | значения не найдены | значения не найдены`
- `[AirConditioning] R_0200_CEM_IPM_AC_DisplaySts | 289415180 | — | значения не найдены`
- `[AirConditioning] R_0200_CEM_IPM_SyncSts / T_0201_IHU_5_SyncSwtich_Req | 289415181 | значения не найдены | 1/2`
- `[AirConditioning] R_0200_CEM_IPM_FrontAutoACSts / T_0201_IHU_5_AutoState | 289415182 | значения не найдены | write: 2`
- `[AirConditioning] R_0200_CEM_IPM_Custom_Air_Conditioning / T_0201_SET_IPMCustom_Air_Conditioning | 289415186 | значения не найдены | read: 0..2, write: 1..3`
- `[AirConditioning] R_0200_CEM_IPM_Automatic_Ventilation / T_0401_SET_IPM_Automatic_Ventilation | 289415187 | значения не найдены | 1/2`
- `[AirConditioning] R_0200_CEM_IPM_First_BlowingSts / T_0401_IHU_1_DVD_SET_IPM_First_Blowing | 289415188 | значения не найдены | 1/2`
- `[AirConditioning] R_0200_CEM_IPM_Blower_DelaySts / T_0401_IHU_1_DVD_SET_IPM_Blower_Delay | 289415189 | значения не найдены | 1/2`
- `[AirConditioning] R_0200_CEM_IPM_BT_Reduce_Wind_SpeedSts / T_0401_IHU_1_SET_BT_Reduce_Wind_Speed | 289415190 | 289412667 | 1=on, 2=off`
- `[AirConditioning] R_0200_CEM_IPM_AnionPurify / T_0201_IHU_5_AnionPurify_Req | 289415191 | значения не найдены | 1/2`
- `[AirConditioning] R_0400_CEM_IPM_3_ACMAXReq_Sts / T_0401_SET_IHU_ACMAXReq | 289412209 | значения не найдены | 1/2`
- `[AirConditioning] R_0400_RBCM_FGHeat_Request_CommandFeedb / T_0201_SET_FrontWindscreenHeatiReq | 289412114 | значения не найдены | 1/2`

### Launcher

- `[Launcher] MCU_REPLY_ACC_STATUS | 557845540 | — | status`
- `[Launcher] MCU_REPLY_SPEED | 557845547 | — | telemetry`
- `[Launcher] Radar_B_L_Obstacle_Distance | 289411329 | — | distance raw`
- `[Launcher] Radar_B_L_M_Obstacle_Distance | 289411330 | — | distance raw`
- `[Launcher] Radar_B_R_M_Obstacle_Distance | 289411331 | — | distance raw`
- `[Launcher] Radar_B_R_Obstacle_Distance | 289411332 | — | distance raw`
- `[Launcher] Radar_F_L_Obstacle_Distance | 289411333 | — | distance raw`
- `[Launcher] Radar_F_L_M_Obstacle_Distance | 289411334 | — | distance raw`
- `[Launcher] Radar_F_R_M_Obstacle_Distance | 289411335 | — | distance raw`
- `[Launcher] Radar_F_R_Obstacle_Distance | 289411336 | — | distance raw`
- `[Launcher] R_0400_RBCM_1_WashingLiquid_LevelSts | 289412346 | — | значения не найдены`

## Android10VhalRepository: проверенная семантика raw (read/write)

Ниже зафиксированы значения, сверенные по штатным приложениям (`CarSettings`, `AirConditioning`), чтобы поведение `Android10VhalRepository` совпадало со штаткой.

### Бинарные параметры (важно: у части параметров read/write асимметричны)

- `Steering wheel heat`
  - Read: `R_0400_RBCM_MFS_HeatSts (289412111)` -> `1=ON`, `2=OFF`
  - Write: `T_0401_SET_MFS_Heat (289412679)` -> `1=ON`, `2=OFF`
- `Wiper maintenance` (as stock CarSettings)
  - Read: `R_0400_CEM_Wiper_MaintenanceSts (289412194)` -> `1=ON` (else OFF)
  - Write: `T_0401_SET_Wiper_Maintenance (289412682)` -> `1=service ON`, `2=working/OFF`
- `Front windscreen heat`
  - Read: `R_0400_RBCM_FGHeat_Request_CommandFeedb (289412114)` -> `1=ON`, `2=OFF`
  - Write: `T_0201_SET_FrontWindscreenHeatiReq (289415309)` -> `2=ON`, `1=OFF`
- `Rear defrost`
  - Read: `R_0200_CEM_IPM_RearDefrosts (289415177)` -> `1=ON`, `2=OFF`
  - Write: `T_0201_IHU_5_RearDefrostSwitch_Req (289415299)` -> `2=ON`, `1=OFF`
- `Air recirculation`
  - Read: `R_0200_CEM_IPM_RecyMode (289415172)` -> `1=ON` (inside; stock UI «outside» uses `==2`)
  - Write: `T_0201_IHU_5_CirculationMode_Req (289415302)` -> `1=inside/recirc ON`, `2=outside/recirc OFF`

### Небинарные параметры (доп. проверка)

- `Drive mode`: `R_0400_TCU_G_DriverMode_7 (289412123)` / `T_0401_IHU_9_DriveMode (289412695)` -> `0..6`
- `Drive mode 6DCT`: `T_0401_IHU_9_DriveMode_6DCT_Wet (289412692)` -> `0..6` (в UI обычно `0/1/2`)
- `EPS mode`: `R_0400_EPS_1_EPSModeSts (289412124)` / `T_0401_IHU_1_DVD_SET_EPSmode (289412662)` -> `0..6`
- `Volume speed compensation`: `AUDIO_VOL_VSC_MOD_REQ (557849227)` -> `1..4` (`1=off`, `2=low`, `3=mid`, `4=high`)
