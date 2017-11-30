package edu.ucla.cs.cdsc.pipeline;

import jdk.nashorn.internal.ir.Block;
import org.jctools.queues.QueueFactory;
import org.jctools.queues.SpscArrayQueue;
import org.jctools.queues.SpscLinkedQueue;
import org.jctools.queues.SpscLinkedQueue;

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

    private static SpscLinkedQueue<PackObject> packQueue = new SpscLinkedQueue<>();
    //private static SpscLinkedQueue<SendObject> sendQueue = new SpscLinkedQueue<>();
    private static SpscArrayQueue<SendObject> sendQueue = new SpscArrayQueue<>(SEND_QUEUE_SIZE);
    private static SpscLinkedQueue<RecvObject> recvQueue = new SpscLinkedQueue<>();
    private static SpscLinkedQueue<UnpackObject> unpackQueue = new SpscLinkedQueue<>();

    public static SpscLinkedQueue<PackObject> getPackQueue() {
        return packQueue;
    }

    /*
    public static SpscLinkedQueue<SendObject> getSendQueue() {
        return sendQueue;
    }
    */


    public static SpscLinkedQueue<RecvObject> getRecvQueue() {
        return recvQueue;
    }

    public static SpscLinkedQueue<UnpackObject> getUnpackQueue() {
        return unpackQueue;
    }

    public static SpscArrayQueue<SendObject> getSendQueue() {
        return sendQueue;
    }

    public abstract SendObject pack(PackObject obj);

    public abstract void send(SendObject obj);

    public abstract RecvObject receive(ServerSocket server);

    public abstract UnpackObject unpack(RecvObject obj);

    public abstract Object execute(Object input);
}
