package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.*;
import org.jctools.queues.MpscLinkedQueue;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

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

    private AtomicInteger numJobs;
    private AtomicInteger numPendingJobs;
    private int numFPGAJobs;

    public AESPipeline(String inputData, int size, int repeatFactor, int TILE_SIZE, int numPackThreads) {
        this.inputData = inputData;
        this.size = size;
        this.repeatFactor = repeatFactor;
        this.TILE_SIZE = TILE_SIZE;
        this.numPackThreads = numPackThreads;
        this.finalData = new byte[size];

        numJobs = new AtomicInteger(repeatFactor * numPackThreads * (size / TILE_SIZE));
        numPendingJobs = new AtomicInteger(0);
        numFPGAJobs = 0;
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
            while ((n = in.read(data, offset, length)) > 0) {
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
                        numFPGAJobs++;
                        numPendingJobs.getAndIncrement();
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

                int tileIdx = 0;
                //for (int i = 0; i < repeatFactor * numOfTiles * numPackThreads; i++) {
                while (true) {
                    //logger.info("numJobs = " + numJobs.get() + ", numPendingJobs = " + numPendingJobs.get());
                    if (numPendingJobs.get() > 0) {
                        AESRecvObject curObj = (AESRecvObject) receive(server);
                        numPendingJobs.getAndDecrement();
                        numJobs.getAndDecrement();
                        if (curObj.getData()[0] == 0 && tileIdx < numOfTiles) {
                            System.arraycopy(curObj.getData(), 0, finalData, tileIdx * TILE_SIZE, TILE_SIZE);
                            tileIdx++;
                        }
                    }
                    if (numJobs.get() == 0) break;
                }
            } catch (Exception e) {
                logger.severe("Caught exception: " + e);
                e.printStackTrace();
            }
        };

        //Thread splitThread = new Thread(splitter);
        //splitThread.start();
        Thread[] packThreads = new Thread[numPackThreads];
        for (int i = 0; i < numPackThreads; i++) {
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
            for (int i = 0; i < numPackThreads; i++) {
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
        int numOverallJobs = numPackThreads * (size / TILE_SIZE) * repeatFactor;
        System.out.println("[CPU Jobs] " + (numOverallJobs - numFPGAJobs) + ", [FPGA Jobs] " + numFPGAJobs);
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

    public AtomicInteger getNumJobs() {
        return numJobs;
    }

    public void setNumJobs(AtomicInteger numJobs) {
        this.numJobs = numJobs;
    }

    public int getNumPackThreads() {
        return numPackThreads;
    }
}

class PackRunnable implements Runnable {
    private static final Logger logger = Logger.getLogger(PackRunnable.class.getName());
    private static final String key = "Bar12345Bar12345"; // 128 bit key
    private static final String initVector = "RandomInitVector"; // 16 bytes IV
    private int threadID;
    private AESPipeline pipeline;
    public PackRunnable(int threadID, AESPipeline pipeline) {
        this.threadID = threadID;
        this.pipeline = pipeline;
    }

    public static byte[] encrypt(byte[] value) {
        try {
            IvParameterSpec iv = new IvParameterSpec(initVector.getBytes("UTF-8"));
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);

            byte[] encrypted = cipher.doFinal(value);
            for (int i = 0; i < 25; i++) encrypted = cipher.doFinal(encrypted);

            return encrypted;
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    @Override
    public void run() {
        try {
            int numOfTiles = pipeline.getSize() / pipeline.getTILE_SIZE();
            MpscLinkedQueue<SendObject> aesSendQueue = AESPipeline.getSendQueue();
            for (int j = 0; j < pipeline.getRepeatFactor(); j++) {
                for (int i = 0; i < numOfTiles; i++) {
                    AESPackObject packObj = new AESPackObject(pipeline.getInputData(),
                            i * pipeline.getTILE_SIZE(), (i + 1) * pipeline.getTILE_SIZE(), threadID);
                    AESSendObject sendObj = (AESSendObject) pipeline.pack(packObj);

                    // while (pipeline.getNumPendingJobs().get() >= 32) ;
                    // while (!aesSendQueue.offer(sendObj)) ;

                    if (pipeline.getNumPendingJobs().get() >= 32) {
                        //logger.info("Pack Thread " + threadID + ": " + (j*numOfTiles+i) + "-th task on CPU");
                        long timeToSleep = (long) ((long) pipeline.getTILE_SIZE() * 1e9 / (1 << 27));
                        Thread.sleep((int) (timeToSleep/1e6), (int) timeToSleep % 1000000);
                        //byte[] encryptedData = encrypt(sendObj.getData());
                        //encryptedData[0] = (byte) threadID;
                        pipeline.getNumJobs().getAndDecrement();
                    }
                    //while (numPendingJobs.get() >= 64) Thread.sleep(0, 1000);
                    else {
                        //logger.info("Pack Thread " + threadID + ": " + (j*numOfTiles+i) + "-th task on FPGA");
                        while (!aesSendQueue.offer(sendObj)) ;
                    }

                    /*
                    if (pipeline.getNumPendingJobs().get() >= 32) {
                        //logger.info("Pack Thread " + threadID + ": " + (j*numOfTiles+i) + "-th task on CPU");
                        long timeToSleep = (long) ((long) pipeline.getTILE_SIZE() * 1e9 / (1 << 23));
                        Thread.sleep((int) (timeToSleep/1e6), (int) timeToSleep % 1000000);
                        //byte[] encryptedData = encrypt(sendObj.getData());
                        //encryptedData[0] = (byte) threadID;
                        pipeline.getNumJobs().getAndDecrement();
                    }
                    //while (numPendingJobs.get() >= 64) Thread.sleep(0, 1000);
                    else {
                        //logger.info("Pack Thread " + threadID + ": " + (j*numOfTiles+i) + "-th task on FPGA");
                        while (!aesSendQueue.offer(sendObj)) ;
                    }
                    */

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
