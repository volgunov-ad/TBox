package com.mengbo.mbdms.interfaces;

import com.mengbo.mbdms.entity.DmsUserInfo;

public interface IFaceRecognizeCallback {
    void onFaceRecognizeResult(DmsUserInfo userInfo, int result);
}
