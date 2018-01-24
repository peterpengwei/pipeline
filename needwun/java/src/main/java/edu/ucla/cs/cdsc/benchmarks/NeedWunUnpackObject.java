package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.UnpackObject;

/**
 * Created by Peter on 10/16/2017.
 */
public class NeedWunUnpackObject extends UnpackObject {
    private String data;

    public NeedWunUnpackObject(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
