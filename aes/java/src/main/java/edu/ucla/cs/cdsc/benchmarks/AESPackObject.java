package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.PackObject;

/**
 * Created by Peter on 10/11/2017.
 */
public class AESPackObject extends PackObject {
    private String data;
    private long startIdx;

    public AESPackObject(String data, long startIdx) {
        this.data = data;
        this.startIdx = startIdx;
    }

    public long getStartIdx() {
        return startIdx;
    }

    public void setStartIdx(long startIdx) {
        this.startIdx = startIdx;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
