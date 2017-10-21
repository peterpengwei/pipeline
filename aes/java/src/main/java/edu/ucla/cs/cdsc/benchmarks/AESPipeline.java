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
                splitTime = System.nanoTime() - startTime;
            } catch (InterruptedException e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        Runnable packer = () -> {
            try {
                long startTime = System.nanoTime();
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
                packTime = System.nanoTime() - startTime;
            } catch (InterruptedException e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        Runnable sender = () -> {
            try {
                long startTime = System.nanoTime();
                boolean done = false;
                while (!done) {
                    AESSendObject obj = (AESSendObject) AESPipeline.getSendQueue().take();
                    if (obj.getData() == null) {
                        done = true;
                    } else {
                        send(obj);
                    }
                }
                sendTime = System.nanoTime() - startTime;
            } catch (InterruptedException e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        Runnable receiver = () -> {
            try (ServerSocket server = new ServerSocket(9520)) {
                long startTime = System.nanoTime();
                int numOfTiles = (int) (size / TILE_SIZE);
                BlockingQueue<RecvObject> aesRecvQueue = AESPipeline.getRecvQueue();
                for (int j = 0; j < 64; j++) {
                    for (int i = 0; i < numOfTiles; i++) {
                        aesRecvQueue.put(receive(server));
                    }
                }
                aesRecvQueue.put(new AESRecvObject(null));
                receiveTime = System.nanoTime() - startTime;
            } catch (Exception e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        Runnable unpacker = () -> {
            try {
                long startTime = System.nanoTime();
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
                unpackTime = System.nanoTime() - startTime;
            } catch (InterruptedException e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        StringBuilder stringBuilder = new StringBuilder();
        Runnable merger = () -> {
            try {
                long startTime = System.nanoTime();
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
                mergeTime = System.nanoTime() - startTime;
            } catch (InterruptedException e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };
        new Thread(splitter).start();
        new Thread(packer).start();
        new Thread(sender).start();
        new Thread(receiver).start();
        new Thread(unpacker).start();
        new Thread(merger).start();

        long overallTime = System.nanoTime() - overallStartTime;
        System.out.println("[Overall] " + overallTime / 1.0e9);
        System.out.println("[Split] " + splitTime / 1.0e9);
        System.out.println("[Pack] " + packTime / 1.0e9);
        System.out.println("[Send] " + sendTime / 1.0e9);
        System.out.println("[Recv] " + receiveTime / 1.0e9);
        System.out.println("[Unpack] " + unpackTime / 1.0e9);
        System.out.println("[Merge] " + mergeTime / 1.0e9);
        return stringBuilder.toString();
    }

    private String inputData;
    private long size;
    private static final int TILE_SIZE = (1 << 20);
    private static final Logger logger = Logger.getLogger(AESPipeline.class.getName());
    private long splitTime;
    private long packTime;
    private long sendTime;
    private long receiveTime;
    private long unpackTime;
    private long mergeTime;
}
