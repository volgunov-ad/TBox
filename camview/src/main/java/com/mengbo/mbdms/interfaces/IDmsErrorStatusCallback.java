package com.mengbo.mbdms.interfaces;

import com.mengbo.mbdms.entity.MbErrorStatus;

public interface IDmsErrorStatusCallback {
    void onDmsErrorStatusResult(MbErrorStatus status);
}
