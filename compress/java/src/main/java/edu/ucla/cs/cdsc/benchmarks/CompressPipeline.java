package edu.ucla.cs.cdsc.benchmarks;

import edu.ucla.cs.cdsc.pipeline.*;
import org.jctools.queues.SpscLinkedQueue;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
public class CompressPipeline extends Pipeline {
    private static final Logger logger = Logger.getLogger(CompressPipeline.class.getName());
    private String inputFile;
    private String outputFile;
    private int size;
    private int repeatFactor;
    private int TILE_SIZE;

    private AtomicInteger numPendingJobs;

    public CompressPipeline(String inputFile, String outputFile, int size, int repeatFactor, int TILE_SIZE) {
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.size = size;
        this.repeatFactor = repeatFactor;
        this.TILE_SIZE = TILE_SIZE;

        numPendingJobs = new AtomicInteger(0);
    }

    @Override
    public SendObject pack(PackObject obj) {
        CompressPackObject compressPackObject = (CompressPackObject) obj;
        byte[] output = new byte[TILE_SIZE];
        try {
            compressPackObject.getData().read(output);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new CompressSendObject(output);
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
            byte[] data = ((CompressSendObject) obj).getData();
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
            return new CompressRecvObject(data);
        } catch (Exception e) {
            logger.severe("Caught exceptino: " + e);
            e.printStackTrace();
            return new CompressRecvObject(null);
        }
    }

    @Override
    public UnpackObject unpack(RecvObject obj) {
        CompressRecvObject compressRecvObject = (CompressRecvObject) obj;
        return new CompressUnpackObject(new String(compressRecvObject.getData()));
    }

    @Override
    public Object execute(Object input) {
        long overallStartTime = System.nanoTime();

        Runnable packer = () -> {
            try {
                int numOfTiles = size / TILE_SIZE;
                SpscLinkedQueue<SendObject> aesSendQueue = CompressPipeline.getSendQueue();
                for (int j = 0; j < repeatFactor; j++) {
                    FileInputStream inputStream = new FileInputStream(inputFile);
                    for (int i = 0; i < numOfTiles; i++) {
                        CompressPackObject packObj = new CompressPackObject(inputStream);
                        CompressSendObject sendObj = (CompressSendObject) pack(packObj);
                        while (numPendingJobs.get() >= 64) Thread.sleep(0, 1000);
                        while (!aesSendQueue.offer(sendObj)) ;
                        numPendingJobs.getAndIncrement();
                    }
                    inputStream.close();
                }
                CompressSendObject endNode = new CompressSendObject(null);
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
                    CompressSendObject obj;
                    while ((obj = (CompressSendObject) CompressPipeline.getSendQueue().poll()) == null) ;
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
                    FileOutputStream outputStream = new FileOutputStream(outputFile);
                    for (int i = 0; i < numOfTiles; i++) {
                        CompressRecvObject curObj = (CompressRecvObject) receive(server);
                        numPendingJobs.getAndDecrement();
                        outputStream.write(curObj.getData());
                        //logger.info("Recv queue full");
                    }
                    outputStream.close();
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
        return null;
    }
}
