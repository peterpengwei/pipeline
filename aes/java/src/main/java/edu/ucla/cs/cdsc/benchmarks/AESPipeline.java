package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.*;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Created by Peter on 10/10/2017.
 */
public class AESPipeline extends Pipeline {
    private static final int TILE_SIZE = (1 << 24);
    private static final Logger logger = Logger.getLogger(AESPipeline.class.getName());
    private String inputData;
    private int size;
    private int repeatFactor;
    private byte[] finalData;

    public AESPipeline() {
        this("", 0, 0);
    }

    public AESPipeline(String inputData, int size, int repeatFactor) {
        this.inputData = inputData;
        this.size = size;
        this.repeatFactor = repeatFactor;
        this.finalData = new byte[size];
    }

    @Override
    public SendObject pack(PackObject obj) {
        AESPackObject aesPackObject = (AESPackObject) obj;
        int startIdx = aesPackObject.getStartIdx();
        int endIdx = aesPackObject.getEndIdx();
        String data = aesPackObject.getData();
        byte[] output = new byte[TILE_SIZE];
        for (int i=0; i<TILE_SIZE; i++) output[i] = (byte) data.charAt(startIdx++);
        return new AESSendObject(output);
    }

    @Override
    public void send(SendObject obj) {
        try (Socket socket = new Socket("localhost", 6070)) {
            byte[] data = ((AESSendObject) obj).getData();
            //logger.info("Sending data with length " + data.length + ": " + (new String(data)).substring(0, 64));
            //BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
            //out.write(data, 0, TILE_SIZE);
            socket.getOutputStream().write(data);
        } catch (Exception e) {
            logger.severe("Caught exception: " + e);
            e.printStackTrace();
        }
    }

    @Override
    public RecvObject receive(ServerSocket server) {
        try (Socket incoming = server.accept()) {
            byte[] data = new byte[TILE_SIZE];
            //BufferedInputStream in = new BufferedInputStream(incoming.getInputStream());
            //in.read(data, 0, TILE_SIZE);
            //logger.info("Received data with length " + data.length + ": " + (new String(data)).substring(0, 64));
            int n, totalSize = TILE_SIZE, offset = 0;
            InputStream in = incoming.getInputStream();
            while ((n = in.read(data, offset, totalSize)) > 0) {
                if (n == totalSize) break;
                totalSize -= n;
                offset += n;
            }
            return new AESRecvObject(data);
        } catch (Exception e) {
            logger.severe("Caught exceptino: " + e);
            e.printStackTrace();
            return new AESRecvObject(null);
        }
    }

    @Override
    public UnpackObject unpack(RecvObject obj) {
        AESRecvObject aesRecvObject = (AESRecvObject) obj;
        return new AESUnpackObject(new String(aesRecvObject.getData()));
    }

    @Override
    public Object execute(Object input) {
        long overallStartTime = System.nanoTime();

        try (ServerSocket server = new ServerSocket()) {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(9520));
            int numOfTiles = size / TILE_SIZE;
            for (int j = 0; j < repeatFactor; j++) {
                for (int i = 0; i < numOfTiles; i++) {
                    AESPackObject packObj = new AESPackObject(inputData, i * TILE_SIZE, (i+1) * TILE_SIZE);
                    AESSendObject sendObj = (AESSendObject) pack(packObj);
                    send(sendObj);
                    AESRecvObject recvObj = (AESRecvObject) receive(server);
                    //AESUnpackObject unpackObj = (AESUnpackObject) unpack(recvObj);
                    System.arraycopy(recvObj.getData(), 0, finalData, i*TILE_SIZE, TILE_SIZE);
                }
            }
        } catch (Exception e) {
            logger.severe("Caught exception: " + e);
            e.printStackTrace();
        }

        long overallTime = System.nanoTime() - overallStartTime;
        System.out.println("[Overall] " + overallTime / 1.0e9);
        return new String(finalData);
    }
}
