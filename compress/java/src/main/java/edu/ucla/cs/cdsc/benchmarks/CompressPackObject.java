package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.PackObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Peter on 10/11/2017.
 */
public class CompressPackObject extends PackObject {
    private FileInputStream data;

    public CompressPackObject(FileInputStream data) throws IOException {
        this.data = data;
    }

    public FileInputStream getData() {
        return data;
    }

    public void setData(FileInputStream data) {
        this.data = data;
    }
}
