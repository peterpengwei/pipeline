package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.*;

import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
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

            long splitTime = 0;
            long packTime = 0;
            long sendTime = 0;
            long recvTime = 0;
            long unpackTime = 0;

            for (int j = 0; j < repeatFactor; j++) {
                for (int i = 0; i < numOfTiles; i++) {
                    long startTime = System.nanoTime();
                    AESPackObject packObj = new AESPackObject(inputData, i * TILE_SIZE, (i+1) * TILE_SIZE);
                    long splitDoneTime = System.nanoTime();
                    splitTime += splitDoneTime - startTime;
                    String filename = System.getProperty("java.io.tmpdir") + "/aes_"
                            + Integer.toString(i) + "_" + Integer.toString(j) + ".tmp";
                    RandomAccessFile raf = new RandomAccessFile(filename, "rw");
                    FileChannel channel = raf.getChannel();
                    MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, TILE_SIZE);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    int idx = packObj.getStartIdx();
                    String data = packObj.getData();
                    for (int k=0; k<TILE_SIZE; k++) buffer.put((byte) data.charAt(idx++));
                    //AESSendObject sendObj = (AESSendObject) pack(packObj);
                    long packDoneTime = System.nanoTime();
                    packTime += packDoneTime - splitDoneTime;
                    AESSendObject sendObj = (AESSendObject) pack(packObj);
                    send(sendObj);
                    long sendDoneTime = System.nanoTime();
                    sendTime += sendDoneTime - packDoneTime;
                    AESRecvObject recvObj = (AESRecvObject) receive(server);
                    long recvDoneTime = System.nanoTime();
                    recvTime += recvDoneTime - sendDoneTime;
                    //AESUnpackObject unpackObj = (AESUnpackObject) unpack(recvObj);
                    System.arraycopy(recvObj.getData(), 0, finalData, i*TILE_SIZE, TILE_SIZE);
                    long unpackDoneTime = System.nanoTime();
                    unpackTime += unpackDoneTime - recvDoneTime;
                }
            }
            System.out.println("[Split] " + splitTime / 1.0e9);
            System.out.println("[Pack] " + packTime / 1.0e9);
            System.out.println("[Send] " + sendTime / 1.0e9);
            System.out.println("[Recv] " + recvTime / 1.0e9);
            System.out.println("[Unpack] " + unpackTime / 1.0e9);
        } catch (Exception e) {
            logger.severe("Caught exception: " + e);
            e.printStackTrace();
        }

        long overallTime = System.nanoTime() - overallStartTime;
        System.out.println("[Overall] " + overallTime / 1.0e9);
        return new String(finalData);
    }
}
