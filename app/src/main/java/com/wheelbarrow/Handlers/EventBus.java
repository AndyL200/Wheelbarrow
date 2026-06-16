package com.wheelbarrow.Handlers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class EventBus {
    private static final Map<String, List<Runnable>> listeners = new ConcurrentHashMap<>();

    public static void subscribe(String event, Runnable runnable) {
        listeners.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>()).add(runnable);
    }

    public static void publish(String event) {
        List<Runnable> runnables = listeners.get(event);
        if (runnables != null) {
            runnables.forEach((r) -> {
                try {
                    r.run();
                } catch (Exception e) {
                    System.err.println("Error in event handler for event '" + event + "': " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
    }

    public static void unsubscribe(String event) {
        listeners.remove(event);
    }
}