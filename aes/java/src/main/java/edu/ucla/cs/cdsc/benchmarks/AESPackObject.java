package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.PackObject;

/**
 * Created by Peter on 10/11/2017.
 */
public class AESPackObject extends PackObject {
    private String data;
    private int startIdx;

    public AESPackObject(String data, int startIdx) {
        this.data = data;
        this.startIdx = startIdx;
    }

    public int getStartIdx() {
        return startIdx;
    }

    public void setStartIdx(int startIdx) {
        this.startIdx = startIdx;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
