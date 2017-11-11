package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.*;
import sun.misc.Cleaner;
import sun.nio.ch.DirectBuffer;

import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Created by Peter on 10/10/2017.
 */
public class AESPipeline extends Pipeline {
    private static final Logger logger = Logger.getLogger(AESPipeline.class.getName());
    private String inputData;
    private int size;
    private int repeatFactor;
    private int TILE_SIZE;
    private byte[] finalData;

    private long acceptTime;

    public AESPipeline(String inputData, int size, int repeatFactor, int TILE_SIZE) {
        this.inputData = inputData;
        this.size = size;
        this.repeatFactor = repeatFactor;
        this.TILE_SIZE = TILE_SIZE;
        this.finalData = new byte[size];
    }

    @Override
    public SendObject pack(PackObject obj) {
        AESPackObject aesPackObject = (AESPackObject) obj;
        int startIdx = aesPackObject.getStartIdx();
        String data = aesPackObject.getData();
        byte[] output = new byte[TILE_SIZE];
        for (int i = 0; i < TILE_SIZE; i++) output[i] = (byte) data.charAt(startIdx++);
        return new AESSendObject(output);
    }

    @Override
    public void send(SendObject obj) {
        try {
            Socket socket = new Socket();
            SocketAddress address = new InetSocketAddress("127.0.0.1", 6070);
            while (true) {
                try {
                    socket.connect(address);
                    break;
                } catch (Exception e) {
                }
            }
            byte[] data = ((AESSendObject) obj).getData();
            //logger.info("Sending data with length " + data.length + ": " + (new String(data)).substring(0, 64));
            //BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
            //out.write(data, 0, TILE_SIZE);
            socket.getOutputStream().write(data);
            socket.close();
        } catch (Exception e) {
            logger.severe("Caught exception: " + e);
            e.printStackTrace();
        }
    }

    public long put(AESPackObject obj, int i, int j) {
        String filename = System.getProperty("java.io.tmpdir") + "/aes_"
                + Integer.toString(i) + "_" + Integer.toString(j) + ".sig";
        long retTime;
        try {
            RandomAccessFile raf = new RandomAccessFile(filename, "rw");
            FileChannel channel = raf.getChannel();
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, TILE_SIZE);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            int idx = obj.getStartIdx();
            String data = obj.getData();
            for (int k = 0; k < TILE_SIZE; k++) buffer.put((byte) data.charAt(idx++));
            retTime = System.nanoTime();
            channel.close();
            raf.close();
            Files.delete(Paths.get(filename));
            Cleaner cleaner = ((DirectBuffer) buffer).cleaner();
            cleaner.clean();
        } catch (Exception e) {
            retTime = System.nanoTime();
            e.printStackTrace();
        }
        return retTime;
    }

    @Override
    public RecvObject receive(ServerSocket server) {
        try (Socket incoming = server.accept()) {
            acceptTime = System.nanoTime();
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
            incoming.close();
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

            long putTime = 0L;
            long packTime = 0L;
            long sendTime = 0L;
            long recvTime = 0L;
            long unpackTime = 0L;

            int numOfTiles = size / TILE_SIZE;

            for (int j = 0; j < repeatFactor; j++) {
                for (int i = 0; i < numOfTiles; i++) {
                    long startTime = System.nanoTime();
                    AESPackObject packObj = new AESPackObject(inputData, i * TILE_SIZE, (i + 1) * TILE_SIZE);
                    AESSendObject sendObj = (AESSendObject) pack(packObj);
                    long packDoneTime = System.nanoTime();
                    packTime += packDoneTime - startTime;

                    startTime = System.nanoTime();
                    long putSingleDoneTime = put(packObj, i, j);
                    putTime += putSingleDoneTime - startTime;

                    startTime = System.nanoTime();
                    send(sendObj);
                    long sendDoneTime = System.nanoTime();
                    sendTime += sendDoneTime - startTime;

                    //startTime = System.nanoTime();
                    AESRecvObject recvObj = (AESRecvObject) receive(server);
                    long recvDoneTime = System.nanoTime();
                    recvTime += recvDoneTime - acceptTime;

                    startTime = System.nanoTime();
                    //AESUnpackObject unpackObj = (AESUnpackObject) unpack(recvObj);
                    System.arraycopy(recvObj.getData(), 0, finalData, i * TILE_SIZE, TILE_SIZE);
                    long unpackDoneTime = System.nanoTime();
                    unpackTime += unpackDoneTime - startTime;
                }
            }
            System.out.println("[Pack] " + packTime / 1.0e9);
            System.out.println("[Single] " + putTime / 1.0e9);
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
