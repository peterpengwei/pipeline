package edu.ucla.cs.cdsc.benchmarks;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Created by Peter on 10/10/2017.
 */
public class NeedWunMain {
    public static void main(String[] args) {
        Logger logger = Logger.getLogger(NeedWunMain.class.getName());

        // args[0]: path to input file
        // args[1]: word
        // args[2]: file size
        // args[3]: repeat factor
        // args[4]: tile size
        if (args.length != 5) {
            logger.severe("Invalid command-line format");
            System.exit(1);
        }
        String inputFile = args[0];
        String outputFile = args[1];
        int size = Integer.parseInt(args[2]);
        int repeatFactor = Integer.parseInt(args[3]);
        int TILE_SIZE = Integer.parseInt(args[4]);

        String inputData = "";
        try {
            FileInputStream inputStream = new FileInputStream(inputFile);
            byte[] bytes = new byte[size];
            inputStream.read(bytes, 0, size);
            inputData = new String(bytes);
        } catch (Exception e) {
            logger.severe("Caught exception: " + e);
            e.printStackTrace();
            System.exit(1);
        }

        NeedWunPipeline pipeline = new NeedWunPipeline(inputData, size, repeatFactor, TILE_SIZE);

        String outputData = (String) pipeline.execute(null);

        try {
            Path outputPath = Paths.get(outputFile);
            Files.write(outputPath, outputData.getBytes());
        } catch (Exception e) {
            logger.severe("Caught exception: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }
}
