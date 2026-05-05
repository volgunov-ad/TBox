package com.mengbo.mbCan.defines;

/* loaded from: classes.dex */
public enum MBInstrumentProper {
    eMBINSTRUMENT_CLEAR(3),
    eMBINSTRUMENT_NAVI_NEXTROADNAME(48),
    eMBINSTRUMENT_NAVI_INFO(49),
    eMBINSTRUMENT_NAVI_DESTINATION_DIS(50),
    eMBINSTRUMENT_NAVI_ROAD_INFO(51),
    eMBINSTRUMENT_MEDIA_SONG_TITLE(80),
    eMBINSTRUMENT_MEDIA_SINGER(81),
    eMBINSTRUMENT_PHONE_CALLNUMBER(112),
    eMBINSTRUMENT_PHONE_CALLER(113),
    eMBINSTRUMENT_PHONE_CALLINFO(114);

    private int value;

    MBInstrumentProper(int i) {
        this.value = i;
    }

    public int getValue() {
        return this.value;
    }
}
