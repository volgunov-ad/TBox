package com.mengbo.mbavm.entity;

import java.nio.ByteBuffer;

public class AvmImage {
    public int nColorFmt;
    public short nWidth;
    public short nHeight;
    public int nstride;
    public int nImageSize;
    public ByteBuffer pImageBuf;
    public int nImageNum;

    public AvmImage(
            int nColorFmt,
            short nWidth,
            short nHeight,
            int nstride,
            int nImageSize,
            ByteBuffer pImageBuf,
            int nImageNum
    ) {
        this.nColorFmt = nColorFmt;
        this.nWidth = nWidth;
        this.nHeight = nHeight;
        this.nstride = nstride;
        this.nImageSize = nImageSize;
        this.pImageBuf = pImageBuf;
        this.nImageNum = nImageNum;
    }
}
