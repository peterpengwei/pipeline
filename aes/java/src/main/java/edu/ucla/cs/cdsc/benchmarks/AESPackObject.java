package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.PackObject;

/**
 * Created by Peter on 10/11/2017.
 */
public class AESPackObject extends PackObject {
    private String data;
    private int startIdx;
    private int endIdx;

    public AESPackObject(String data, int startIdx, int endIdx) {
        this.data = data;
        this.startIdx = startIdx;
        this.endIdx = endIdx;
    }

    public int getEndIdx() {
        return endIdx;
    }

    public void setEndIdx(int endIdx) {
        this.endIdx = endIdx;
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
