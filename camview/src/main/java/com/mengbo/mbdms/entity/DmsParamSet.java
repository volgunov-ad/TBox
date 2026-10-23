package com.mengbo.mbdms.entity;

public class DmsParamSet {
    public String accountId;
    public boolean bDrowsiness;
    public boolean bDistraction;
    public boolean bDangerAction;
    public byte nSpeedThreshold;
    public byte nInterval;
    public boolean bSeat;
    public boolean bLamp;
    public boolean bFragrance;
    public boolean bScreen;
    public boolean bGazeAoi;
    public boolean bVerification;
    public boolean bEngine;
    public boolean bRecognize;

    public DmsParamSet(
            boolean bDrowsiness,
            boolean bDistraction,
            boolean bDangerAction,
            byte nSpeedThreshold,
            byte nInterval,
            boolean bSeat,
            boolean bLamp,
            boolean bFragrance,
            boolean bScreen,
            boolean bGazeAoi,
            boolean bVerification,
            boolean bEngine,
            boolean bRecognize
    ) {
        this.bDrowsiness = bDrowsiness;
        this.bDistraction = bDistraction;
        this.bDangerAction = bDangerAction;
        this.nSpeedThreshold = nSpeedThreshold;
        this.nInterval = nInterval;
        this.bSeat = bSeat;
        this.bLamp = bLamp;
        this.bFragrance = bFragrance;
        this.bScreen = bScreen;
        this.bGazeAoi = bGazeAoi;
        this.bVerification = bVerification;
        this.bEngine = bEngine;
        this.bRecognize = bRecognize;
    }
}
