package com.markdave.sinomatakaw;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A tiny Wi-Fi room. It does not know the game rules: it gives each phone an id,
 * stamps "from" on every message and passes it on.
 *   to:"host"  -> the phone running this server (connected from 127.0.0.1)
 *   to:<id>    -> that one phone
 *   no "to"    -> everyone else
 */
public class RelayServer extends WebSocketServer {
    private final Map<WebSocket, Integer> ids = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public RelayServer(int port) {
        super(new InetSocketAddress(port));
        setReuseAddr(true);
        setConnectionLostTimeout(20);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        int id = nextId.getAndIncrement();
        ids.put(conn, id);
        try {
            JSONObject hello = new JSONObject();
            hello.put("t", "hello");
            hello.put("id", id);
            conn.send(hello.toString());
        } catch (Exception ignored) { }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Integer id = ids.remove(conn);
        if (id == null) return;
        try {
            JSONObject left = new JSONObject();
            left.put("t", "left");
            left.put("id", id);
            String out = left.toString();
            for (WebSocket c : ids.keySet()) safeSend(c, out);
        } catch (Exception ignored) { }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Integer from = ids.get(conn);
        if (from == null) return;
        JSONObject o;
        try {
            o = new JSONObject(message);
            o.put("from", from);
        } catch (Exception e) {
            return;
        }
        Object to = o.opt("to");
        String out = o.toString();
        if ("host".equals(to)) {
            for (WebSocket c : ids.keySet()) if (isLocal(c)) safeSend(c, out);
        } else if (to instanceof Number) {
            int target = ((Number) to).intValue();
            for (Map.Entry<WebSocket, Integer> e : ids.entrySet()) {
                if (e.getValue() == target) safeSend(e.getKey(), out);
            }
        } else {
            for (WebSocket c : ids.keySet()) if (c != conn) safeSend(c, out);
        }
    }

    private static boolean isLocal(WebSocket c) {
        try {
            return c.getRemoteSocketAddress().getAddress().isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }

    private static void safeSend(WebSocket c, String s) {
        try { if (c.isOpen()) c.send(s); } catch (Exception ignored) { }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) { }

    @Override
    public void onStart() { }
}
