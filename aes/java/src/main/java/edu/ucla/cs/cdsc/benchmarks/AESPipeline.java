package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.*;
import org.jctools.queues.SpscLinkedQueue;

import java.io.InputStream;
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
        String input = aesPackObject.getData();
        int startIdx = aesPackObject.getStartIdx();
        int endIdx = aesPackObject.getEndIdx();
        byte[] data = new byte[TILE_SIZE];
        int idx = 0;
        for (int i=startIdx; i<endIdx; i++) {
            data[idx++] = (byte) input.charAt(i);
        }
        return new AESSendObject(data);
    }

    @Override
    public void send(SendObject obj) {
        try (Socket socket = new Socket("localhost", 6070)) {
            byte[] data = ((AESSendObject) obj).getData();
            //logger.info("Sending data with length " + data.length + ": " + (new String(data)).substring(0, 64));
            socket.getOutputStream().write(data);
            //BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
            //out.write(data, 0, TILE_SIZE);
            socket.close();
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
            int n;
            InputStream in = incoming.getInputStream();
            int offset = 0, length = TILE_SIZE;
            while((n = in.read(data, offset, length)) > 0) {
                if (n == length) break;
                offset += n;
                length -= n;
            }
            //logger.info("Received data with length " + data.length + ": " + (new String(data)).substring(0, 64));
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

        Runnable splitter = () -> {
            try {
                int numOfTiles = (int) (size / TILE_SIZE);
                SpscLinkedQueue<PackObject> aesPackQueue = AESPipeline.getPackQueue();
                for (int j = 0; j < repeatFactor; j++) {
                    for (int i = 0; i < numOfTiles; i++) {
                        AESPackObject inputObj = new AESPackObject(inputData, i * TILE_SIZE, (i+1) * TILE_SIZE);
                        while (aesPackQueue.offer(inputObj) == false) ;
                        //logger.info("Pack queue full");
                    }
                }
                AESPackObject endNode = new AESPackObject(null, -1, -1);
                while (aesPackQueue.offer(endNode) == false) ;
            } catch (Exception e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        Runnable packer = () -> {
            try {
                boolean done = false;
                SpscLinkedQueue<SendObject> aesSendQueue = AESPipeline.getSendQueue();
                while (!done) {
                    AESPackObject obj;
                    while ((obj = (AESPackObject) AESPipeline.getPackQueue().poll()) == null) ;
                    if (obj.getData() == null || obj.getStartIdx() == -1 || obj.getEndIdx() == -1) {
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

        byte[] finalData = new byte[size];
        Runnable unpacker = () -> {
            try {
                boolean done = false;
                SpscLinkedQueue<UnpackObject> aesUnpackQueue = AESPipeline.getUnpackQueue();
                int idx = 0;
                while (!done) {
                    AESRecvObject obj;
                    while ((obj = (AESRecvObject) AESPipeline.getRecvQueue().poll()) == null) ;
                    if (obj.getData() == null) {
                        done = true;
                    } else {
                        System.arraycopy(obj.getData(), 0, finalData, idx, TILE_SIZE);
                        idx = (idx + TILE_SIZE) % size;
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
                int numOfTiles = size / TILE_SIZE;
                while (!done) {
                    AESUnpackObject obj;
                    while ((obj = (AESUnpackObject) AESPipeline.getUnpackQueue().poll()) == null) ;
                    if (obj.getData() == null) {
                        done = true;
                    } else {
                        if (idx++ < numOfTiles) {
                            stringBuilder.append(obj.getData());
                        }
                    }
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
        //Thread mergeThread = new Thread(merger);
        //mergeThread.start();

        try {
            splitThread.join();
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
        //return stringBuilder.toString();
        return new String(finalData);
    }
}
