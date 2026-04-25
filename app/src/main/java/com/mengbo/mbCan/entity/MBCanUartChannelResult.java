package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanUartChannelResult {
    private short[] nCHANNEL_REV_FRROM_MCU;
    private short nMCU_RECV_TOTAL;

    public MBCanUartChannelResult(short s, short[] sArr) {
        this.nMCU_RECV_TOTAL = s;
        this.nCHANNEL_REV_FRROM_MCU = sArr;
    }

    public short getMCU_RECV_TOTAL() {
        return this.nMCU_RECV_TOTAL;
    }

    public short[] getCHANNEL_REV_FRROM_MCU() {
        return this.nCHANNEL_REV_FRROM_MCU;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanUartChannelResult{");
        stringBuffer.append("\nnMCU_RECV_TOTAL");
        stringBuffer.append((int) this.nMCU_RECV_TOTAL);
        for (int i = 0; i < this.nMCU_RECV_TOTAL; i++) {
            stringBuffer.append("\nnCHANNEL_REV_FRROM_MCU[" + i);
            stringBuffer.append("]=" + ((int) this.nCHANNEL_REV_FRROM_MCU[i]));
        }
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
