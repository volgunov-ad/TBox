package com.mengbo.mbdms.interfaces;

import com.mengbo.mbdms.entity.DmsFaceFeature;

public interface IFaceFeatureCallback {
    void onFaceFeatureResult(String accountId, DmsFaceFeature feature);
}
