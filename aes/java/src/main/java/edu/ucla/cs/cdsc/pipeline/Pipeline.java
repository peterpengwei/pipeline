package edu.ucla.cs.cdsc.pipeline;

import jdk.nashorn.internal.ir.Block;
import org.jctools.queues.QueueFactory;
import org.jctools.queues.SpscArrayQueue;

import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by Peter on 10/10/2017.
 */
public abstract class Pipeline {
    private static final int PACK_QUEUE_SIZE = 32;
    private static final int SEND_QUEUE_SIZE = 32;
    private static final int RECV_QUEUE_SIZE = 32;
    private static final int UNPACK_QUEUE_SIZE = 32;

    private static SpscArrayQueue<PackObject> packQueue = new SpscArrayQueue<>(PACK_QUEUE_SIZE);
    private static SpscArrayQueue<SendObject> sendQueue = new SpscArrayQueue<>(SEND_QUEUE_SIZE);
    private static SpscArrayQueue<RecvObject> recvQueue = new SpscArrayQueue<>(RECV_QUEUE_SIZE);
    private static SpscArrayQueue<UnpackObject> unpackQueue = new SpscArrayQueue<>(UNPACK_QUEUE_SIZE);

    public static SpscArrayQueue<PackObject> getPackQueue() {
        return packQueue;
    }

    public static SpscArrayQueue<SendObject> getSendQueue() {
        return sendQueue;
    }

    public static SpscArrayQueue<RecvObject> getRecvQueue() {
        return recvQueue;
    }

    public static SpscArrayQueue<UnpackObject> getUnpackQueue() {
        return unpackQueue;
    }

    public abstract SendObject pack(PackObject obj);

    public abstract void send(SendObject obj);

    public abstract RecvObject receive(ServerSocket server);

    public abstract UnpackObject unpack(RecvObject obj);

    public abstract Object execute(Object input);
}
