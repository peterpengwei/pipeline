package edu.ucla.cs.cdsc.pipeline;

import jdk.nashorn.internal.ir.Block;

import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by Peter on 10/10/2017.
 */
public abstract class Pipeline {
    private static final int PACK_QUEUE_SIZE = 8;
    private static final int SEND_QUEUE_SIZE = 8;
    private static final int RECV_QUEUE_SIZE = 8;
    private static final int UNPACK_QUEUE_SIZE = 8;

    private static BlockingQueue<PackObject> packQueue = new ArrayBlockingQueue<>(PACK_QUEUE_SIZE);
    private static BlockingQueue<SendObject> sendQueue = new ArrayBlockingQueue<>(SEND_QUEUE_SIZE);
    private static BlockingQueue<RecvObject> recvQueue = new ArrayBlockingQueue<>(RECV_QUEUE_SIZE);
    private static BlockingQueue<UnpackObject> unpackQueue = new ArrayBlockingQueue<>(UNPACK_QUEUE_SIZE);

    public static BlockingQueue<PackObject> getPackQueue() {
        return packQueue;
    }

    public static BlockingQueue<SendObject> getSendQueue() {
        return sendQueue;
    }

    public static BlockingQueue<RecvObject> getRecvQueue() {
        return recvQueue;
    }

    public static BlockingQueue<UnpackObject> getUnpackQueue() {
        return unpackQueue;
    }

    public abstract SendObject pack(PackObject obj);

    public abstract void send(SendObject obj);

    public abstract RecvObject receive(ServerSocket server);

    public abstract UnpackObject unpack(RecvObject obj);

    public abstract Object execute(Object input);
}
