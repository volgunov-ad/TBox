package com.mengbo.libraryoms.entity;

public class MbFeatureItem {
    public byte nAge;
    public int eGender;
    public int eEmotion;
    public int eSeatPostion;
    public int eBeltState;
    public int eChildrenChair;

    public MbFeatureItem(
            byte nAge,
            int eGender,
            int eEmotion,
            int eSeatPostion,
            int eBeltState,
            int eChildrenChair
    ) {
        this.nAge = nAge;
        this.eGender = eGender;
        this.eEmotion = eEmotion;
        this.eSeatPostion = eSeatPostion;
        this.eBeltState = eBeltState;
        this.eChildrenChair = eChildrenChair;
    }
}
