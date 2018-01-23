package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.*;
import org.jctools.queues.SpscLinkedQueue;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Created by Peter on 10/10/2017.
 */
public class WordCountPipeline extends Pipeline {
    private static final Logger logger = Logger.getLogger(WordCountPipeline.class.getName());
    private String inputData;
    private int size;
    private int repeatFactor;
    private int TILE_SIZE;
    private int wordCount;

    private final String word = "ease";

    private AtomicInteger numPendingJobs;

    public WordCountPipeline(String inputData, int size, int repeatFactor, int TILE_SIZE) {
        this.inputData = inputData;
        this.size = size;
        this.repeatFactor = repeatFactor;
        this.TILE_SIZE = TILE_SIZE;
        this.wordCount = 0;

        numPendingJobs = new AtomicInteger(0);
    }

    @Override
    public SendObject pack(PackObject obj) {
        WordCountPackObject wordCountPackObject = (WordCountPackObject) obj;
        int startIdx = wordCountPackObject.getStartIdx();
        String data = wordCountPackObject.getData();
        byte[] output = new byte[TILE_SIZE];
        for (int i = 0; i < TILE_SIZE; i++) output[i] = (byte) data.charAt(startIdx++);
        return new WordCountSendObject(output);
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
            byte[] data = ((WordCountSendObject) obj).getData();
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
            //int offset = 0, length = TILE_SIZE;
            //while((n = in.read(data, offset, length)) > 0) {
            //    if (n == length) break;
            //    offset += n;
            //    length -= n;
            //}
            in.read(data);
            //logger.info("Received data with length " + data.length + ": " + (new String(data)).substring(0, 64));
            incoming.close();
            return new WordCountRecvObject(data);
        } catch (Exception e) {
            logger.severe("Caught exceptino: " + e);
            e.printStackTrace();
            return new WordCountRecvObject(null);
        }
    }

    @Override
    public UnpackObject unpack(RecvObject obj) {
        WordCountRecvObject wordCountRecvObject = (WordCountRecvObject) obj;
        return new WordCountUnpackObject(new String(wordCountRecvObject.getData()));
    }

    @Override
    public Object execute(Object input) {
        long overallStartTime = System.nanoTime();

        Runnable packer = () -> {
            try {
                int numOfTiles = size / TILE_SIZE;
                SpscLinkedQueue<SendObject> aesSendQueue = WordCountPipeline.getSendQueue();
                    for (int j = 0; j < repeatFactor; j++) {
                    for (int i = 0; i < numOfTiles; i++) {
                        WordCountPackObject packObj = new WordCountPackObject(inputData, i * TILE_SIZE, (i+1) * TILE_SIZE);
                        WordCountSendObject sendObj = (WordCountSendObject) pack(packObj);
                        while (numPendingJobs.get() >= 64) Thread.sleep(0, 1000);
                        while (!aesSendQueue.offer(sendObj)) ;
                        numPendingJobs.getAndIncrement();
                    }
                }
                WordCountSendObject endNode = new WordCountSendObject(null);
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
                    WordCountSendObject obj;
                    while ((obj = (WordCountSendObject) WordCountPipeline.getSendQueue().poll()) == null) ;
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
            try (ServerSocket server = new ServerSocket()) {
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(9520));
                int numOfTiles = size / TILE_SIZE;
                for (int j = 0; j < repeatFactor; j++) {
                    for (int i = 0; i < numOfTiles; i++) {
                        WordCountRecvObject curObj = (WordCountRecvObject) receive(server);
                        numPendingJobs.getAndDecrement();
                        wordCount += ByteBuffer.wrap(curObj.getData()).order(ByteOrder.LITTLE_ENDIAN).getInt();
                        //logger.info("Recv queue full");
                    }
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
        //Thread unpackThread = new Thread(unpacker);
        //unpackThread.start();
        //Thread mergeThread = new Thread(merger);
        //mergeThread.start();

        try {
            //splitThread.join();
            packThread.join();
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
        return wordCount;
    }
}
