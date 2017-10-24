package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.RecvObject;

/**
 * Created by Peter on 10/16/2017.
 */
public class AESRecvObject extends RecvObject {
    private String filename;

    public AESRecvObject(String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}
