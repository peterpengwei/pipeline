package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.*;

import java.io.IOException;
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
        int endIdx = startIdx + (int) TILE_SIZE;
        return new AESSendObject(aesPackObject.getData().substring(startIdx, endIdx).getBytes());
    }

    @Override
    public void send(SendObject obj) {
        try (Socket socket = new Socket("localhost", 6070)) {
	    byte[] data = ((AESSendObject) obj).getData();
	    logger.info("Sending data with length " + data.length + ": " + (new String(data)).substring(0, 64));
	    socket.getOutputStream().write(data, 0, TILE_SIZE);
        } catch (Exception e) {
            logger.severe("Caught exception: " + e);
        }
    }

    @Override
    public RecvObject receive(ServerSocket server) {
        try (Socket incoming = server.accept()) {
            byte[] data = new byte[TILE_SIZE];
            incoming.getInputStream().read(data, 0, TILE_SIZE);
	    logger.info("Received data with length " + data.length + ": " + (new String(data)).substring(0, 64));
            return new AESRecvObject(data);
        } catch (Exception e) {
            logger.severe("Caught exceptino: " + e);
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
        Runnable splitter = () -> {
            try {
                int numOfTiles = (int) (size / TILE_SIZE);
                BlockingQueue<PackObject> aesPackQueue = AESPipeline.getPackQueue();
                for (int i=0; i<numOfTiles; i++) {
                    AESPackObject inputObj = new AESPackObject(inputData, (long) i*TILE_SIZE);
                    aesPackQueue.put(inputObj);
                }
                aesPackQueue.put(null);
            } catch (InterruptedException e) {
                logger.severe("Caught exception: " + e);
            }
        };
        new Thread(splitter).start();

        Runnable packer = () -> {
            try {
                boolean done = false;
                BlockingQueue<SendObject> aesSendQueue = AESPipeline.getSendQueue();
                while (!done) {
                    AESPackObject obj = (AESPackObject) AESPipeline.getPackQueue().take();
                    if (obj == null) {
                        done = true;
                        aesSendQueue.put(null);
                    }
                    else {
                        aesSendQueue.put(pack(obj));
                    }
                }
            } catch (InterruptedException e) {
                logger.severe("Caught exception: " + e);
            }
        };
        new Thread(packer).start();

        Runnable sender = () -> {
            try {
                boolean done = false;
                while (!done) {
                    AESSendObject obj = (AESSendObject) AESPipeline.getSendQueue().take();
                    if (obj == null) {
                        done = true;
                    }
                    else {
                        send(obj);
                    }
                }
            } catch (InterruptedException e) {
                logger.severe("Caught exception: " + e);
            }
        };
        new Thread(sender).start();

        Runnable receiver = () -> {
            try (ServerSocket server = new ServerSocket(9520)) {
                int numOfTiles = (int) (size / TILE_SIZE);
                BlockingQueue<RecvObject> aesRecvQueue = AESPipeline.getRecvQueue();
                for (int i=0; i<numOfTiles; i++) {
                    aesRecvQueue.put(receive(server));
                }
                aesRecvQueue.put(null);
            } catch (Exception e) {
                logger.severe("Caught exception: " + e);
            }
        };
        new Thread(receiver).start();

        Runnable unpacker = () -> {
            try {
                boolean done = false;
                BlockingQueue<UnpackObject> aesUnpackQueue = AESPipeline.getUnpackQueue();
                while (!done) {
                    AESRecvObject obj = (AESRecvObject) AESPipeline.getRecvQueue().take();
                    if (obj == null) {
                        done = true;
                        aesUnpackQueue.put(null);
                    }
                    else {
                        aesUnpackQueue.put(unpack(obj));
                    }
                }
            } catch (InterruptedException e) {
                logger.severe("Caught exception: " + e);
            }
        };
        new Thread(unpacker).start();

        StringBuilder stringBuilder = new StringBuilder();
        Runnable merger = () -> {
            try {
                boolean done = false;
                while (!done) {
                    AESUnpackObject obj = (AESUnpackObject) AESPipeline.getUnpackQueue().take();
                    if (obj == null) {
                        done = true;
                    }
                    else {
                        stringBuilder.append(obj.getData());
                    }
                }
            } catch (InterruptedException e) {
                logger.severe("Caught exception: " + e);
            }
        };
        new Thread(sender).start();

        return stringBuilder.toString();
    }

    private String inputData;
    private long size;
    private static final int TILE_SIZE = (1 << 20);
    private static final Logger logger = Logger.getLogger(AESPipeline.class.getName());
}
