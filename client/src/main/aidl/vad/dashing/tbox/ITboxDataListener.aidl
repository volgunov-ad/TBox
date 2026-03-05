package vad.dashing.tbox;

import android.os.Bundle;

interface ITboxDataListener {
    void onTboxDataChanged(in Bundle data);
    void onCanDataChanged(in Bundle data);
    void onCycleDataChanged(in Bundle data);
}
