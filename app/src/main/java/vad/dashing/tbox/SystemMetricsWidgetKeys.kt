package vad.dashing.tbox

/** Head-unit CPU load percentage (from `/proc/stat`). */
const val CPU_USAGE_WIDGET_DATA_KEY = "cpuUsage"

/** Head-unit free RAM as percent of total (from [android.app.ActivityManager.MemoryInfo]). */
const val FREE_RAM_PERCENT_WIDGET_DATA_KEY = "freeRamPercent"

/** Head-unit SoC temperature in °C (thermal zone `aoss-usr` on A9, `cls0` on A10). */
const val HU_SOC_TEMPERATURE_WIDGET_DATA_KEY = "huSocTemperature"

/** Head-unit CPU-cluster temperature in °C (thermal zones `cpuss-0-usr`…`cpu-1-*-usr`, A9 only). */
const val HU_CPU_TEMPERATURE_WIDGET_DATA_KEY = "huCpuTemperature"

/** Head-unit GPU temperature in °C (thermal zone `gpu-usr`, A9 only). */
const val HU_GPU_TEMPERATURE_WIDGET_DATA_KEY = "huGpuTemperature"
