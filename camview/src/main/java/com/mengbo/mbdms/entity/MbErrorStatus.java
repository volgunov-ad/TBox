package com.mengbo.mbdms.entity;

public class MbErrorStatus {
    public int nCameraStatus;
    public int nServiceSdkStatus;
    public int nServiceStatus;
    public int nServiceCAN;
    public int nAndroidSdkStatus;

    public MbErrorStatus(
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
