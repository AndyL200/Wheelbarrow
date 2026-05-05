package Handlers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EventBus {
    private static final Map<String, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    public static void subscribe(String event, Consumer<Object> handler) {
        listeners.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public static void publish(String event, Object payload) {
        List<Consumer<Object>> handlers = listeners.get(event);
        if (handlers != null) {
            handlers.forEach(h -> h.accept(payload));
        }
    }

    public static void publish(String event) {
        publish(event, null);
    }

    public static void unsubscribe(String event, Consumer<Object> handler) {
        List<Consumer<Object>> handlers = listeners.get(event);
        if (handlers != null) handlers.remove(handler);
    }
}