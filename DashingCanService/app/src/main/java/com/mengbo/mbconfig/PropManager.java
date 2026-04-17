package com.mengbo.mbconfig;

import android.text.TextUtils;
import android.util.Log;
import com.mengbo.mbconfig.utils.CMDUtil;
import com.mengbo.mbconfig.utils.PropertyUtils;
import java.util.concurrent.ConcurrentHashMap;

/* loaded from: classes2.dex */
public class PropManager {
    private static final String TAG = "[mbconfig][PropManager]";
    private static volatile PropManager instance;
    private final ConcurrentHashMap<String, String> propResultMap = new ConcurrentHashMap<>();

    public static PropManager getInstance() {
        if (instance == null) {
            synchronized (PropManager.class) {
                if (instance == null) {
                    instance = new PropManager();
                }
            }
        }
        return instance;
    }

    public String getDeviceSerialNumber() {
        if (this.propResultMap.containsKey(MBConfigConstant.RO_BOARD_SERIAL)) {
            return this.propResultMap.get(MBConfigConstant.RO_BOARD_SERIAL);
        }
        String prop = getProp(MBConfigConstant.RO_BOARD_SERIAL);
        Log.i(TAG, "getDeviceSerialNumber: serial = " + prop);
        this.propResultMap.put(MBConfigConstant.RO_BOARD_SERIAL, prop);
        return prop;
    }

    public String getVIN() {
        if (this.propResultMap.containsKey(MBConfigConstant.RO_MB_CAN_VIN)) {
            return this.propResultMap.get(MBConfigConstant.RO_MB_CAN_VIN);
        }
        String prop = getProp(MBConfigConstant.RO_MB_CAN_VIN);
        Log.i(TAG, "getVIN: vin = " + prop);
        this.propResultMap.put(MBConfigConstant.RO_MB_CAN_VIN, prop);
        return prop;
    }

    public String getConfig() {
        if (this.propResultMap.containsKey(MBConfigConstant.RO_MB_CAN_CONFIG)) {
            return this.propResultMap.get(MBConfigConstant.RO_MB_CAN_CONFIG);
        }
        String prop = getProp(MBConfigConstant.RO_MB_CAN_CONFIG);
        Log.i(TAG, "getConfig: softConfigCode = " + prop);
        this.propResultMap.put(MBConfigConstant.RO_MB_CAN_CONFIG, prop);
        return prop;
    }

    public String getPersistConfig() {
        if (this.propResultMap.containsKey(MBConfigConstant.PERSIST_MB_CAN_CONFIG)) {
            return this.propResultMap.get(MBConfigConstant.PERSIST_MB_CAN_CONFIG);
        }
        String prop = getProp(MBConfigConstant.PERSIST_MB_CAN_CONFIG);
        Log.i(TAG, "getConfig: softConfigCode = " + prop);
        this.propResultMap.put(MBConfigConstant.PERSIST_MB_CAN_CONFIG, prop);
        return prop;
    }

    public String getProjectName() {
        if (this.propResultMap.containsKey(MBConfigConstant.RO_WT_PROJECT_NAME)) {
            return this.propResultMap.get(MBConfigConstant.RO_WT_PROJECT_NAME);
        }
        String prop = getProp(MBConfigConstant.RO_WT_PROJECT_NAME);
        Log.i(TAG, "getProjectName: projectName = " + prop);
        this.propResultMap.put(MBConfigConstant.RO_WT_PROJECT_NAME, prop);
        return prop;
    }

    public String getCarType() {
        if (this.propResultMap.containsKey(MBConfigConstant.VENDOR_CARTYPE_VERSION)) {
            return this.propResultMap.get(MBConfigConstant.VENDOR_CARTYPE_VERSION);
        }
        String prop = getProp(MBConfigConstant.VENDOR_CARTYPE_VERSION);
        Log.i(TAG, "getCarType: carType = " + prop);
        this.propResultMap.put(MBConfigConstant.VENDOR_CARTYPE_VERSION, prop);
        return prop;
    }

    public String getHWVersion() {
        if (this.propResultMap.containsKey("vendor.soc.board.hw.version")) {
            return this.propResultMap.get("vendor.soc.board.hw.version");
        }
        String prop = getProp("vendor.soc.board.hw.version");
        Log.i(TAG, "getHWVersion: hardwareVersion = " + prop);
        this.propResultMap.put("vendor.soc.board.hw.version", prop);
        return prop;
    }

    public String getMBCarModel() {
        if (this.propResultMap.containsKey("persist.mb.can.car_model")) {
            return this.propResultMap.get("persist.mb.can.car_model");
        }
        String prop = getProp("persist.mb.can.car_model");
        Log.i(TAG, "getMBCarModel: mbCarModel = " + prop);
        this.propResultMap.put("persist.mb.can.car_model", prop);
        return prop;
    }

    public String getMcuCarModel() {
        if (this.propResultMap.containsKey(MBConfigConstant.RO_MB_MCU_CAR_MODEL)) {
            return this.propResultMap.get(MBConfigConstant.RO_MB_MCU_CAR_MODEL);
        }
        String prop = getProp(MBConfigConstant.RO_MB_MCU_CAR_MODEL);
        Log.i(TAG, "getMcuCarModel: mbMcuCarModel = " + prop);
        this.propResultMap.put(MBConfigConstant.RO_MB_MCU_CAR_MODEL, prop);
        return prop;
    }

    public String getBranch() {
        if (this.propResultMap.containsKey(MBConfigConstant.RO_MB_BRANCH)) {
            return this.propResultMap.get(MBConfigConstant.RO_MB_BRANCH);
        }
        String prop = getProp(MBConfigConstant.RO_MB_BRANCH);
        Log.i(TAG, "getBranch: branch = " + prop);
        this.propResultMap.put(MBConfigConstant.RO_MB_BRANCH, prop);
        return prop;
    }

    public boolean isFactoryBranch() {
        String branch = getBranch();
        Log.i(TAG, "isFactoryBranch branch = " + branch);
        return branch.startsWith(MBConfigConstant.MB_BRANCH_FACTORY);
    }

    public boolean isOverseaBranch() {
        String branch = getBranch();
        Log.i(TAG, "isOverseaBranch branch = " + branch);
        return branch.endsWith("os");
    }

    public String getProp(String str) {
        if (TextUtils.isEmpty(str)) {
            return "";
        }
        String prop = CMDUtil.getProp(str);
        String trim = TextUtils.isEmpty(prop) ? "" : prop.replace("\n", "").trim();
        Log.i(TAG, "getProp: prop = " + str + " , propResult = " + trim);
        return trim;
    }

    public boolean setProp(String str, String str2) {
        try {
            if (TextUtils.isEmpty(str)) {
                return false;
            }
            PropertyUtils.setProperty(str, str2);
            Log.i(TAG, "setProp: prop = " + str + " , value = " + str2);
            return true;
        } catch (Exception e) {
            Log.i(TAG, "setProp: prop = " + str + " , value = " + str2 + " , error = " + e.getMessage());
            return false;
        }
    }
}