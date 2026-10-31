package com.mengbo.libraryoms.entity;

public class MbOmsFlowParam {
    public boolean bDisplay;
    public boolean bAttribute;
    public boolean bLeft;
    public boolean bPhoto;
    public boolean bVideo;

    public MbOmsFlowParam(
            boolean bDisplay,
            boolean bAttribute,
            boolean bLeft,
            boolean bPhoto,
            boolean bVideo
    ) {
        this.bDisplay = bDisplay;
        this.bAttribute = bAttribute;
        this.bLeft = bLeft;
        this.bPhoto = bPhoto;
        this.bVideo = bVideo;
    }
}
