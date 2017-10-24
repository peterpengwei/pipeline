package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.PackObject;

/**
 * Created by Peter on 10/11/2017.
 */
public class AESPackObject extends PackObject {
    private String data;
    private int startIdx;
    private int repeatIdx;

    public AESPackObject(String data, int startIdx, int repeatIdx) {
        this.data = data;
        this.startIdx = startIdx;
        this.repeatIdx = repeatIdx;
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

    public int getRepeatIdx() {
        return repeatIdx;
    }

    public void setRepeatIdx(int repeatIdx) {
        this.repeatIdx = repeatIdx;
    }
}
