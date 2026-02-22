package vad.dashing.tbox;

import android.os.Bundle;
import vad.dashing.tbox.ITboxDataListener;

interface ITboxDataService {
    const int FLAG_TBOX = 1;
    const int FLAG_CAN = 2;
    const int FLAG_CYCLE = 4;
    const int FLAG_ALL = 7;

    Bundle getTboxData();
    Bundle getCanData();
    Bundle getCycleData();
    List<String> getCanIds();
    Bundle getLastCanFrame(in String canId);
    void registerListener(in ITboxDataListener listener, int flags);
    void unregisterListener(in ITboxDataListener listener);
}
