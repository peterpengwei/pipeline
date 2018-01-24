package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.UnpackObject;

/**
 * Created by Peter on 10/16/2017.
 */
public class WordCountUnpackObject extends UnpackObject {
    private String data;

    public WordCountUnpackObject(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
