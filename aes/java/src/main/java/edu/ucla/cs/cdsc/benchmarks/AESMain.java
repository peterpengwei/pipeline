package edu.ucla.cs.cdsc.benchmarks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Created by Peter on 10/10/2017.
 */
public class AESMain {
    public static void main(String[] args) {
        Logger logger = Logger.getLogger(AESMain.class.getName());

        // args[0]: path to input file
        // args[1]: path to output file
        // args[2]: file size
        if (args.length != 3) {
            logger.severe("Invalid command-line format");
            System.exit(1);
        }
        String inputFile = args[0];
        String outputFile = args[1];
        long size = Integer.parseInt(args[2]);

        String inputData = "";
        try {
            Path inputPath = Paths.get(inputFile);
            byte[] bytes = Files.readAllBytes(inputPath);
            inputData = new String(bytes);
        } catch (Exception e) {
            logger.severe("Caught exception: " + e);
            System.exit(1);
        }

        AESPipeline pipeline = new AESPipeline(inputData, size);

        String outputData = (String) pipeline.execute(null);

        try {
            Path outputPath = Paths.get(outputFile);
            Files.write(outputPath, outputData.getBytes());
        } catch (Exception e) {
            logger.severe("Caught exception: " + e);
            System.exit(1);
        }
    }
}
