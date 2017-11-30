package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.*;
import org.jctools.queues.SpscLinkedQueue;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
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

    private long allStartTime;
    private long packTotalTime;
    private long sendTotalTime;
    private long recvTotalTime;

    private AtomicInteger numPendingJobs;

    private long sendWaitTime;
    private long sendTransferTime;

    private long numSends;

    public AESPipeline(String inputData, int size, int repeatFactor, int TILE_SIZE) {
        this.inputData = inputData;
        this.size = size;
        this.repeatFactor = repeatFactor;
        this.TILE_SIZE = TILE_SIZE;
        this.finalData = new byte[size];

        numPendingJobs = new AtomicInteger(0);
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
            long startTime = System.nanoTime();
            Socket socket = new Socket();
            SocketAddress address = new InetSocketAddress("127.0.0.1", 6070);
            while (true) {
                try {
                    socket.connect(address);
                    break;
                } catch (Exception e) {
                    logger.warning("Connection failed, try it again");
                }
            }
            sendWaitTime += System.nanoTime() - startTime;
            startTime = System.nanoTime();
            byte[] data = ((AESSendObject) obj).getData();
            //logger.info("Sending data with length " + data.length + ": " + (new String(data)).substring(0, 64));
            //BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
            //out.write(data, 0, TILE_SIZE);
            socket.getOutputStream().write(data);
            socket.close();
            sendTransferTime += System.nanoTime() - startTime;
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
            //int offset = 0, length = TILE_SIZE;
            //while((n = in.read(data, offset, length)) > 0) {
            //    if (n == length) break;
            //    offset += n;
            //    length -= n;
            //}
            in.read(data);
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

        Runnable packer = () -> {
            try {
                int numOfTiles = size / TILE_SIZE;
                SpscLinkedQueue<SendObject> aesSendQueue = AESPipeline.getSendQueue();
                    for (int j = 0; j < repeatFactor; j++) {
                    for (int i = 0; i < numOfTiles; i++) {
                        AESPackObject packObj = new AESPackObject(inputData, i * TILE_SIZE, (i+1) * TILE_SIZE);
                        AESSendObject sendObj = (AESSendObject) pack(packObj);
                        while (!aesSendQueue.offer(sendObj)) ;
                        numPendingJobs.getAndIncrement();
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
                    if (obj.getData() == null) {
                        done = true;
                    } else {
                        while (numPendingJobs.get() >= 32) Thread.sleep(0, 100000);
                        send(obj);
                        numSends++;
                    }
                }
            } catch (Exception e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        Runnable receiver = () -> {
            try (ServerSocket server = new ServerSocket()) {
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(9520));
                int numOfTiles = size / TILE_SIZE;
                for (int j = 0; j < repeatFactor; j++) {
                    for (int i = 0; i < numOfTiles; i++) {
                        AESRecvObject curObj = (AESRecvObject) receive(server);
                        numPendingJobs.getAndDecrement();
                        System.arraycopy(curObj.getData(), 0, finalData, i*TILE_SIZE, TILE_SIZE);
                        //logger.info("Recv queue full");
                    }
                }
            } catch (Exception e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        allStartTime = System.nanoTime();
        sendWaitTime = 0;
        sendTransferTime = 0;
        numSends = 0;

        //Thread splitThread = new Thread(splitter);
        //splitThread.start();
        Thread packThread = new Thread(packer);
        packThread.start();
        Thread sendThread = new Thread(sender);
        sendThread.start();
        Thread recvThread = new Thread(receiver);
        recvThread.start();
        //Thread unpackThread = new Thread(unpacker);
        //unpackThread.start();
        //Thread mergeThread = new Thread(merger);
        //mergeThread.start();

        try {
            //splitThread.join();
            packThread.join();
            packTotalTime = System.nanoTime() - allStartTime;
            sendThread.join();
            sendTotalTime = System.nanoTime() - allStartTime;
            recvThread.join();
            recvTotalTime = System.nanoTime() - allStartTime;
            //unpackThread.join();
            //mergeThread.join();
        } catch (Exception e) {
            logger.severe("Caught exception: " + e);
            e.printStackTrace();
        }

        long overallTime = System.nanoTime() - overallStartTime;
        System.out.println("[Overall] " + overallTime / 1.0e9);
        System.out.println("[Pack] " + packTotalTime / 1.0e9);
        System.out.println("[Send] " + sendTotalTime / 1.0e9);
        System.out.println("[Recv] " + recvTotalTime / 1.0e9);
        System.out.println("[Wait] " + sendWaitTime / 1.0e9);
        System.out.println("[Transfer] " + sendTransferTime / 1.0e9);
        System.out.println("Number of sending: " + numSends);
        //return stringBuilder.toString();
        for (int i = 0; i < 16; i++) {
            System.out.print(((int) finalData[i] & 255));
            System.out.print(" ");
        }
        System.out.println();
        return new String(finalData);
    }
}
