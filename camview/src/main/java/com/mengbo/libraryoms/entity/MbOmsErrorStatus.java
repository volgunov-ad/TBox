package com.mengbo.libraryoms.entity;

public class MbOmsErrorStatus {
    public int nCameraStatus;
    public int nServiceSdkStatus;
    public int nServiceStatus;
    public int nServiceCAN;
    public int nAndroidSdkStatus;

    public MbOmsErrorStatus(
            int nCameraStatus,
            int nServiceSdkStatus,
            int nServiceStatus,
            int nServiceCAN,
            int nAndroidSdkStatus
    ) {
        this.nCameraStatus = nCameraStatus;
        this.nServiceSdkStatus = nServiceSdkStatus;
        this.nServiceStatus = nServiceStatus;
        this.nServiceCAN = nServiceCAN;
        this.nAndroidSdkStatus = nAndroidSdkStatus;
    }

    @Override
    public String toString() {
        return "cam=" + nCameraStatus + ", srvSdk=" + nServiceSdkStatus +
                ", srv=" + nServiceStatus + ", can=" + nServiceCAN +
                ", andSdk=" + nAndroidSdkStatus;
    }
}
