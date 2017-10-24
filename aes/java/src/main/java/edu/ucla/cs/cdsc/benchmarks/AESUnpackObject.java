package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.UnpackObject;

/**
 * Created by Peter on 10/16/2017.
 */
public class AESUnpackObject extends UnpackObject {
    private byte[] data;

    public AESUnpackObject(byte[] data) {
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
