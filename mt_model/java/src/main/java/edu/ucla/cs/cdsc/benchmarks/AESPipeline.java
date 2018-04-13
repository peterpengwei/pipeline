package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.*;
import org.jctools.queues.MpscLinkedQueue;
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
    private int numPackThreads;
    private byte[] finalData;

    private AtomicInteger numPendingJobs;

    public AESPipeline(String inputData, int size, int repeatFactor, int TILE_SIZE, int numPackThreads) {
        this.inputData = inputData;
        this.size = size;
        this.repeatFactor = repeatFactor;
        this.TILE_SIZE = TILE_SIZE;
        this.numPackThreads = numPackThreads;
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
        output[0] = (byte) aesPackObject.getThreadID();
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
                    logger.warning("Connection failed, try it again");
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
            //in.read(data);
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

        Runnable sender = () -> {
            int numPendingThreads = numPackThreads;
            try {
                boolean done = false;
                while (!done) {
                    AESSendObject obj;
                    while ((obj = (AESSendObject) AESPipeline.getSendQueue().poll()) == null) ;
                    if (obj.getData() == null) {
                        numPendingThreads--;
                        if (numPendingThreads == 0)
                            done = true;
                    } else {
                        logger.info("Send thread: " + obj.getData()[0]);
                        send(obj);
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
                        if (curObj.getData()[0] == 0)
                            System.arraycopy(curObj.getData(), 0, finalData, i*TILE_SIZE, TILE_SIZE);
                        else
                            i--;
                        logger.info("Recv thread: " + curObj.getData()[0]);
                    }
                }
            } catch (Exception e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        //Thread splitThread = new Thread(splitter);
        //splitThread.start();
        Thread[] packThreads = new Thread[numPackThreads];
        for (int i=0; i<numPackThreads; i++) {
            packThreads[i] = new Thread(new PackRunnable(i, this));
            packThreads[i].start();
        }
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
            for (int i=0; i<numPackThreads; i++) {
                packThreads[i].join();
            }
            sendThread.join();
            recvThread.join();
            //unpackThread.join();
            //mergeThread.join();
        } catch (Exception e) {
            logger.severe("Caught exception: " + e);
            e.printStackTrace();
        }

        long overallTime = System.nanoTime() - overallStartTime;
        System.out.println("[Overall] " + overallTime / 1.0e9);
        //return stringBuilder.toString();
        for (int i = 0; i < 16; i++) {
            System.out.print(((int) finalData[i] & 255));
            System.out.print(" ");
        }
        System.out.println();
        return new String(finalData);
    }

    public String getInputData() {
        return inputData;
    }

    public void setInputData(String inputData) {
        this.inputData = inputData;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getRepeatFactor() {
        return repeatFactor;
    }

    public void setRepeatFactor(int repeatFactor) {
        this.repeatFactor = repeatFactor;
    }

    public int getTILE_SIZE() {
        return TILE_SIZE;
    }

    public void setTILE_SIZE(int TILE_SIZE) {
        this.TILE_SIZE = TILE_SIZE;
    }

    public byte[] getFinalData() {
        return finalData;
    }

    public void setFinalData(byte[] finalData) {
        this.finalData = finalData;
    }

    public AtomicInteger getNumPendingJobs() {
        return numPendingJobs;
    }

    public void setNumPendingJobs(AtomicInteger numPendingJobs) {
        this.numPendingJobs = numPendingJobs;
    }
}

class PackRunnable implements Runnable {
    private int threadID;
    private AESPipeline pipeline;
    private static final Logger logger = Logger.getLogger(PackRunnable.class.getName());

    public PackRunnable(int threadID, AESPipeline pipeline) {
        this.threadID = threadID;
        this.pipeline = pipeline;
    }

    @Override
    public void run() {
        try {
            int numOfTiles = pipeline.getSize() / pipeline.getTILE_SIZE();
            MpscLinkedQueue<SendObject> aesSendQueue = AESPipeline.getSendQueue();
            for (int j = 0; j < pipeline.getRepeatFactor(); j++) {
                for (int i = 0; i < numOfTiles; i++) {
                    AESPackObject packObj = new AESPackObject(pipeline.getInputData(),
                            i * pipeline.getTILE_SIZE(), (i+1) * pipeline.getTILE_SIZE(), threadID);
                    AESSendObject sendObj = (AESSendObject) pipeline.pack(packObj);
                    logger.info("Pack Thread " + threadID + ": " + sendObj.getData()[0]);
                    while (pipeline.getNumPendingJobs().get() >= 32) ;
                    //while (numPendingJobs.get() >= 64) Thread.sleep(0, 1000);
                    while (!aesSendQueue.offer(sendObj)) ;
                    pipeline.getNumPendingJobs().getAndIncrement();
                }
            }
            AESSendObject endNode = new AESSendObject(null);
            while (aesSendQueue.offer(endNode) == false) ;
        } catch (Exception e) {
            logger.severe("Caught exception: " + e);
            e.printStackTrace();
        }
    }
}
