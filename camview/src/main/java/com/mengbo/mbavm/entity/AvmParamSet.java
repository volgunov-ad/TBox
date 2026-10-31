package com.mengbo.mbavm.entity;

public class AvmParamSet {
    public boolean bPanorama;
    public boolean bTurnSignal;
    public boolean bCorner;
    public boolean bZoomIn;
    public boolean bDoor;
    public boolean bAuxiliary;
    public boolean bRadar;
    public int nLanguage;
    public byte nView;
    public short nTransparent;
    public byte nCarColor;
    public boolean bAutoExitPanoramic;
    public boolean bRadarShield;

    public AvmParamSet(
            boolean bPanorama,
            boolean bTurnSignal,
            boolean bCorner,
            boolean bZoomIn,
            boolean bDoor,
            boolean bAuxiliary,
            boolean bRadar,
            int nLanguage,
            byte nView,
            short nTransparent,
            byte nCarColor,
            boolean bAutoExitPanoramic,
            boolean bRadarShield
    ) {
        this.bPanorama = bPanorama;
        this.bTurnSignal = bTurnSignal;
        this.bCorner = bCorner;
        this.bZoomIn = bZoomIn;
        this.bDoor = bDoor;
        this.bAuxiliary = bAuxiliary;
        this.bRadar = bRadar;
        this.nLanguage = nLanguage;
        this.nView = nView;
        this.nTransparent = nTransparent;
        this.nCarColor = nCarColor;
        this.bAutoExitPanoramic = bAutoExitPanoramic;
        this.bRadarShield = bRadarShield;
    }
}
