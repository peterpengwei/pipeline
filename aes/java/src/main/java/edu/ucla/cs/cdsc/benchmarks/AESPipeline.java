package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.*;

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
                long startTime = System.nanoTime();
                int numOfTiles = (int) (size / TILE_SIZE);
                BlockingQueue<PackObject> aesPackQueue = AESPipeline.getPackQueue();
                for (int j = 0; j < 64; j++) {
                    for (int i = 0; i < numOfTiles; i++) {
                        AESPackObject inputObj = new AESPackObject(inputData, (long) i * TILE_SIZE);
                        aesPackQueue.put(inputObj);
                    }
                }
                aesPackQueue.put(new AESPackObject(null, -1));
            } catch (InterruptedException e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        Runnable packer = () -> {
            try {
                boolean done = false;
                BlockingQueue<SendObject> aesSendQueue = AESPipeline.getSendQueue();
                while (!done) {
                    AESPackObject obj = (AESPackObject) AESPipeline.getPackQueue().take();
                    if (obj.getData() == null && obj.getStartIdx() == -1) {
                        done = true;
                        aesSendQueue.put(new AESSendObject(null));
                    } else {
                        aesSendQueue.put(pack(obj));
                    }
                }
            } catch (InterruptedException e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        Runnable sender = () -> {
            try {
                boolean done = false;
                while (!done) {
                    AESSendObject obj = (AESSendObject) AESPipeline.getSendQueue().take();
                    if (obj.getData() == null) {
                        done = true;
                    } else {
                        send(obj);
                    }
                }
            } catch (InterruptedException e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        Runnable receiver = () -> {
            try (ServerSocket server = new ServerSocket(9520)) {
                int numOfTiles = (int) (size / TILE_SIZE);
                BlockingQueue<RecvObject> aesRecvQueue = AESPipeline.getRecvQueue();
                for (int j = 0; j < 64; j++) {
                    for (int i = 0; i < numOfTiles; i++) {
                        aesRecvQueue.put(receive(server));
                    }
                }
                aesRecvQueue.put(new AESRecvObject(null));
            } catch (Exception e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        Runnable unpacker = () -> {
            try {
                boolean done = false;
                BlockingQueue<UnpackObject> aesUnpackQueue = AESPipeline.getUnpackQueue();
                while (!done) {
                    AESRecvObject obj = (AESRecvObject) AESPipeline.getRecvQueue().take();
                    if (obj.getData() == null) {
                        done = true;
                        aesUnpackQueue.put(new AESUnpackObject(null));
                    } else {
                        aesUnpackQueue.put(unpack(obj));
                    }
                }
            } catch (InterruptedException e) {
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
                    AESUnpackObject obj = (AESUnpackObject) AESPipeline.getUnpackQueue().take();
                    if (obj.getData() == null) {
                        done = true;
                    } else {
                        if (idx % 64 == 0) {
                            stringBuilder.append(obj.getData());
                        }
                    }
                    idx++;
                }
            } catch (InterruptedException e) {
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
}
