package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.SendObject;

/**
 * Created by Peter on 10/16/2017.
 */
public class AESSendObject extends SendObject {
    private String filename;

    public AESSendObject(String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}
