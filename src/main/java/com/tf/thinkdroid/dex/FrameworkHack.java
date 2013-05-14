package com.tf.thinkdroid.dex;

import android.os.Message;
import android.os.MessageQueue;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Collection of dirty codes
 * @author Alan Goo
 */
public class FrameworkHack {
    private static Method METHOD_MESSAGE_QUEUE_NEXT;
    private static Method METHOD_MESSAGE_QUEUE_ENQUEUE;
    private static Field FIELD_MESSAGE_QUEUE_MESSAGES;

    static {
        try {
            METHOD_MESSAGE_QUEUE_NEXT = MessageQueue.class.getDeclaredMethod("next");
            METHOD_MESSAGE_QUEUE_NEXT.setAccessible(true);
            METHOD_MESSAGE_QUEUE_ENQUEUE = MessageQueue.class.getDeclaredMethod("enqueueMessage", Message.class, long.class);
            METHOD_MESSAGE_QUEUE_ENQUEUE.setAccessible(true);
            FIELD_MESSAGE_QUEUE_MESSAGES = MessageQueue.class.getDeclaredField("mMessages");
            FIELD_MESSAGE_QUEUE_MESSAGES.setAccessible(true);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private FrameworkHack() {
        // do not create an instance
    }

    public static Message messageQueueNext(MessageQueue q) {
        try {
            Object oMsg = METHOD_MESSAGE_QUEUE_NEXT.invoke(q);
            return (Message) oMsg;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static Message getMessages(MessageQueue q) {
        try {
            return (Message) FIELD_MESSAGE_QUEUE_MESSAGES.get(q);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void setMessages(MessageQueue q, Message messages) {
        try {
            FIELD_MESSAGE_QUEUE_MESSAGES.set(q, messages);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void enqeue(MessageQueue q, Message msg) {
        try {
            METHOD_MESSAGE_QUEUE_ENQUEUE.invoke(q, msg, 0);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}