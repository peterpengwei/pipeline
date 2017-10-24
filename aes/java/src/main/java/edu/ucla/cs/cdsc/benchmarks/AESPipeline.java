package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.*;
import org.jctools.queues.SpscLinkedQueue;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
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
    private static final int TILE_SIZE = (1 << 20);
    private static final Logger logger = Logger.getLogger(AESPipeline.class.getName());
    private String inputData;
    private int size;
    private int repeatFactor;

    public AESPipeline() {
        this("", 0, 0);
    }

    public AESPipeline(String inputData, int size, int repeatFactor) {
        this.inputData = inputData;
        this.size = size;
        this.repeatFactor = repeatFactor;
    }

    @Override
    public SendObject pack(PackObject obj) {
        AESPackObject aesPackObject = (AESPackObject) obj;
        int startIdx = aesPackObject.getStartIdx();
        int endIdx = startIdx + TILE_SIZE;
        int repeatIdx = aesPackObject.getRepeatIdx();
        try {
            String filename = System.getProperty("java.io.tmpdir") +
                    "/AESInput_" + Integer.toString(startIdx) + "_" + Integer.toString(repeatIdx) + ".txt";
            RandomAccessFile file = new RandomAccessFile(filename, "rw");
            FileChannel channel = file.getChannel();
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, TILE_SIZE);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            for (int i=startIdx; i<endIdx; i++) buffer.put((byte) aesPackObject.getData().charAt(i));
            //channel.close();
            //file.close();
            AESSendObject retObj = new AESSendObject(filename);
	    return retObj;
        } catch (Exception e) {
            logger.severe("Caught exception: " + e);
            e.printStackTrace();
            return new AESSendObject(null);
        }
    }

    @Override
    public void send(SendObject obj) {
        try (Socket socket = new Socket("localhost", 6070)) {
            //byte[] data = ((AESSendObject) obj).getFilename().getBytes();
            //logger.info("Sending data with length " + data.length + " " + new String(data));
            //DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
	    //out.writeBytes(((AESSendObject) obj).getFilename());
	    socket.getOutputStream().write(((AESSendObject) obj).getFilename().getBytes());
            //out.write(data);
        } catch (Exception e) {
            logger.severe("Caught exception: " + e);
            e.printStackTrace();
        }
    }

    @Override
    public RecvObject receive(ServerSocket server) {
        try (Socket incoming = server.accept()) {
            DataInputStream in = new DataInputStream(incoming.getInputStream());
            long nameIdx = Long.reverseBytes(in.readLong());
            int startIdx = (int) (nameIdx >> 32);
            int repeatIdx = (int) nameIdx;
            String filename = System.getProperty("java.io.tmpdir") + "/AESInput_" + Integer.toString(startIdx) +
                    "_" + Integer.toString(repeatIdx) + ".txt";
            //logger.info("Received data with length " + data.length + ": " + (new String(data)).substring(0, 64));
            return new AESRecvObject(filename);
        } catch (Exception e) {
            logger.severe("Caught exceptino: " + e);
            e.printStackTrace();
            return new AESRecvObject(null);
        }
    }

    @Override
    public UnpackObject unpack(RecvObject obj) {
        AESRecvObject aesRecvObject = (AESRecvObject) obj;
        String filename = aesRecvObject.getFilename();
        Path path = Paths.get(filename);
        try {
            FileChannel channel = FileChannel.open(path);
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, TILE_SIZE);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            byte[] data = new byte[TILE_SIZE];
            buffer.get(data, 0, TILE_SIZE);
            //channel.close();
            Files.delete(path);
            return new AESUnpackObject(data);
        } catch (IOException e) {
            logger.severe("Caught exception: " + e);
            e.printStackTrace();
            return new AESUnpackObject(null);
        }
    }

    @Override
    public Object execute(Object input) {
        long overallStartTime = System.nanoTime();

        Runnable packer = () -> {
            try {
                int numOfTiles = size / TILE_SIZE;
                SpscLinkedQueue<SendObject> aesSendQueue = AESPipeline.getSendQueue();
                for (int j = 0; j < repeatFactor; j++) {
                    for (int i = 0; i < numOfTiles; i++) {
                        AESPackObject packObj = new AESPackObject(inputData, i * TILE_SIZE, j);
                        AESSendObject sendObj = (AESSendObject) pack(packObj);
                        while (aesSendQueue.offer(sendObj) == false) ;
                        //logger.info("Pack queue full");
                    }
                }
                AESSendObject endNode = new AESSendObject(null);
                while (aesSendQueue.offer(endNode) == false) ;
            } catch (Exception e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        Runnable sender = () -> {
            try {
                boolean done = false;
                while (!done) {
                    AESSendObject obj;
                    while ((obj = (AESSendObject) AESPipeline.getSendQueue().poll()) == null) ;
                    if (obj.getFilename() == null) {
                        done = true;
                    } else {
                        send(obj);
                    }
                }
            } catch (Exception e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        Runnable receiver = () -> {
            try (ServerSocket server = new ServerSocket(9520)) {
                int numOfTiles = size / TILE_SIZE;
                SpscLinkedQueue<RecvObject> aesRecvQueue = AESPipeline.getRecvQueue();
                for (int j = 0; j < repeatFactor; j++) {
                    for (int i = 0; i < numOfTiles; i++) {
                        RecvObject curObj = receive(server);
                        while (!aesRecvQueue.offer(curObj)) ;
                        //logger.info("Recv queue full");
                    }
                }
                AESRecvObject endNode = new AESRecvObject(null);
                while (!aesRecvQueue.offer(endNode)) ;
            } catch (Exception e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        StringBuilder stringBuilder = new StringBuilder();
        Runnable unpacker = () -> {
            try {
                boolean done = false;
                int idx = 0;
                while (!done) {
                    AESRecvObject obj;
                    while ((obj = (AESRecvObject) AESPipeline.getRecvQueue().poll()) == null) ;
                    if (obj.getFilename() == null) {
                        done = true;
                    } else {
                        AESUnpackObject unpackObj = (AESUnpackObject) unpack(obj);
                        if (idx < size / TILE_SIZE) {
                            stringBuilder.append(unpackObj.getData());
                        }
                    }
                    idx++;
                }
            } catch (Exception e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        //Thread splitThread = new Thread(splitter);
        //splitThread.start();
        Thread packThread = new Thread(packer);
        packThread.start();
        Thread sendThread = new Thread(sender);
        sendThread.start();
        Thread recvThread = new Thread(receiver);
        recvThread.start();
        Thread unpackThread = new Thread(unpacker);
        unpackThread.start();
        //Thread mergeThread = new Thread(merger);
        //mergeThread.start();

        try {
            //splitThread.join();
            packThread.join();
            sendThread.join();
            recvThread.join();
            unpackThread.join();
            //mergeThread.join();
        } catch (Exception e) {
            logger.severe("Caught exception: " + e);
            e.printStackTrace();
        }

        long overallTime = System.nanoTime() - overallStartTime;
        System.out.println("[Overall] " + overallTime / 1.0e9);
        return stringBuilder.toString();
    }
}
