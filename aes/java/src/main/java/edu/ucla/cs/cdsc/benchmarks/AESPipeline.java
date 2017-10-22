package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.*;
import org.jctools.queues.SpscArrayQueue;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

/**
 * Created by Peter on 10/10/2017.
 */
public class AESPipeline extends Pipeline {
    public AESPipeline() {
        this("", 0);
    }

    public AESPipeline(String inputData, long size) {
        this.inputData = inputData;
        this.size = size;
    }

    @Override
    public SendObject pack(PackObject obj) {
        AESPackObject aesPackObject = (AESPackObject) obj;
        int startIdx = (int) aesPackObject.getStartIdx();
        int endIdx = startIdx + TILE_SIZE;
        return new AESSendObject(aesPackObject.getData().substring(startIdx, endIdx).getBytes());
    }

    @Override
    public void send(SendObject obj) {
        try (Socket socket = new Socket("localhost", 6070)) {
            byte[] data = ((AESSendObject) obj).getData();
            //logger.info("Sending data with length " + data.length + ": " + (new String(data)).substring(0, 64));
            socket.getOutputStream().write(data, 0, TILE_SIZE);
        } catch (Exception e) {
            logger.severe("Caught exception: " + e);
            e.printStackTrace();
        }
    }

    @Override
    public RecvObject receive(ServerSocket server) {
        try (Socket incoming = server.accept()) {
            byte[] data = new byte[TILE_SIZE];
            incoming.getInputStream().read(data, 0, TILE_SIZE);
            //logger.info("Received data with length " + data.length + ": " + (new String(data)).substring(0, 64));
            return new AESRecvObject(data);
        } catch (Exception e) {
            logger.severe("Caught exceptino: " + e);
            e.printStackTrace();
        } finally {
            return new AESRecvObject(new byte[0]);
        }
    }

    @Override
    public UnpackObject unpack(RecvObject obj) {
        AESRecvObject aesRecvObject = (AESRecvObject) obj;
        return new AESUnpackObject(aesRecvObject.getData().toString());
    }

    @Override
    public Object execute(Object input) {
        long overallStartTime = System.nanoTime();
        Runnable splitter = () -> {
            try {
                int numOfTiles = (int) (size / TILE_SIZE);
                SpscArrayQueue<PackObject> aesPackQueue = AESPipeline.getPackQueue();
                for (int j = 0; j < TRIP_COUNT; j++) {
                    for (int i = 0; i < numOfTiles; i++) {
                        AESPackObject inputObj = new AESPackObject(inputData, (long) i * TILE_SIZE);
                        while (aesPackQueue.offer(inputObj) == false) ;
                            //logger.info("Pack queue full");
                    }
                }
                AESPackObject endNode = new AESPackObject(null, -1);
                while (aesPackQueue.offer(endNode) == false) ;
            } catch (Exception e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        Runnable packer = () -> {
            try {
                boolean done = false;
                SpscArrayQueue<SendObject> aesSendQueue = AESPipeline.getSendQueue();
                while (!done) {
                    AESPackObject obj;
                    while ((obj = (AESPackObject) AESPipeline.getPackQueue().poll()) == null) ;
                    if (obj.getData() == null && obj.getStartIdx() == -1) {
                        done = true;
                        AESSendObject endNode = new AESSendObject(null);
                        while (!aesSendQueue.offer(endNode)) ;
                    } else {
                        SendObject curObj = pack(obj);
                        while (!aesSendQueue.offer(curObj)) ;
                            //logger.info("Send queue full");
                    }
                }
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
                    if (obj.getData() == null) {
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
                int numOfTiles = (int) (size / TILE_SIZE);
                SpscArrayQueue<RecvObject> aesRecvQueue = AESPipeline.getRecvQueue();
                for (int j = 0; j < TRIP_COUNT; j++) {
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

        Runnable unpacker = () -> {
            try {
                boolean done = false;
                SpscArrayQueue<UnpackObject> aesUnpackQueue = AESPipeline.getUnpackQueue();
                while (!done) {
                    AESRecvObject obj;
                    while ((obj = (AESRecvObject) AESPipeline.getRecvQueue().poll()) == null) ;
                    if (obj.getData() == null) {
                        done = true;
                        AESUnpackObject endNode = new AESUnpackObject(null);
                        while (!aesUnpackQueue.offer(endNode)) ;
                    } else {
                        UnpackObject curObj = unpack(obj);
                        while (!aesUnpackQueue.offer(curObj)) ;
                            //logger.info("Unpack queue full");
                    }
                }
            } catch (Exception e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        StringBuilder stringBuilder = new StringBuilder();
        Runnable merger = () -> {
            try {
                boolean done = false;
                int idx = 0;
                while (!done) {
                    AESUnpackObject obj;
                    while ((obj = (AESUnpackObject) AESPipeline.getUnpackQueue().poll()) == null) ;
                    if (obj.getData() == null) {
                        done = true;
                    } else {
                        if (idx % TRIP_COUNT == 0) {
                            stringBuilder.append(obj.getData());
                        }
                    }
                    idx++;
                }
            } catch (Exception e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };
        Thread splitThread = new Thread(splitter);
        splitThread.start();
        Thread packThread = new Thread(packer);
        packThread.start();
        Thread sendThread = new Thread(sender);
        sendThread.start();
        Thread recvThread = new Thread(receiver);
        recvThread.start();
        Thread unpackThread = new Thread(unpacker);
        unpackThread.start();
        Thread mergeThread = new Thread(merger);
        mergeThread.start();

        try {
            splitThread.join();
            packThread.join();
            sendThread.join();
            recvThread.join();
            unpackThread.join();
            mergeThread.join();
        } catch (Exception e) {
            logger.severe("Caught exception: " + e);
            e.printStackTrace();
        }

        long overallTime = System.nanoTime() - overallStartTime;
        System.out.println("[Overall] " + overallTime / 1.0e9);
        return stringBuilder.toString();
    }

    private String inputData;
    private long size;
    private static final int TILE_SIZE = (1 << 20);
    private static final Logger logger = Logger.getLogger(AESPipeline.class.getName());
    private static final int TRIP_COUNT = 32;
}
