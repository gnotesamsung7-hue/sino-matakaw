package com.markdave.sinomatakaw;

import android.content.Context;
import android.net.wifi.WifiManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Finds games on the same Wi-Fi: the host shouts "I'm here" every second, joiners listen. */
public class LanBridge {
    static final int DISCOVERY_PORT = 8788;
    static final String TAG = "SINOMATAKAW";

    private final WifiManager wifi;
    private WifiManager.MulticastLock lock;
    private volatile boolean hosting = false;
    private volatile boolean scanning = false;
    private volatile DatagramSocket listenSock;
    private final Map<String, JSONObject> games = new ConcurrentHashMap<>();

    LanBridge(Context ctx) {
        wifi = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    /* ---------- host side ---------- */
    synchronized void startBroadcast(final String name, final int port) {
        hosting = true;
        Thread t = new Thread(() -> {
            DatagramSocket s = null;
            try {
                s = new DatagramSocket();
                s.setBroadcast(true);
                byte[] data = (TAG + "|" + name.replace("|", " ") + "|" + port).getBytes(StandardCharsets.UTF_8);
                while (hosting) {
                    for (InetAddress a : broadcastAddresses()) {
                        try { s.send(new DatagramPacket(data, data.length, a, DISCOVERY_PORT)); } catch (Exception ignored) { }
                    }
                    Thread.sleep(1000);
                }
            } catch (Exception ignored) {
            } finally {
                if (s != null) s.close();
            }
        }, "lan-broadcast");
        t.setDaemon(true);
        t.start();
    }

    void stopBroadcast() { hosting = false; }

    private List<InetAddress> broadcastAddresses() {
        List<InetAddress> out = new ArrayList<>();
        try { out.add(InetAddress.getByName("255.255.255.255")); } catch (Exception ignored) { }
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress b = ia.getBroadcast();
                    if (b != null && !out.contains(b)) out.add(b);
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    /* ---------- joiner side ---------- */
    synchronized void startScan() {
        if (scanning) return;
        scanning = true;
        games.clear();
        try {
            if (wifi != null) {
                lock = wifi.createMulticastLock("sinomatakaw");
                lock.setReferenceCounted(false);
                lock.acquire();
            }
        } catch (Exception ignored) { }
        Thread t = new Thread(() -> {
            try {
                DatagramSocket s = new DatagramSocket(null);
                s.setReuseAddress(true);
                s.setBroadcast(true);
                s.bind(new InetSocketAddress(DISCOVERY_PORT));
                s.setSoTimeout(1000);
                listenSock = s;
                byte[] buf = new byte[512];
                while (scanning) {
                    try {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        s.receive(p);
                        String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                        String[] parts = msg.split("\\|");
                        if (parts.length >= 3 && TAG.equals(parts[0])) {
                            String ip = p.getAddress().getHostAddress();
                            JSONObject g = new JSONObject();
                            g.put("name", parts[1]);
                            g.put("ip", ip);
                            g.put("port", Integer.parseInt(parts[2].trim()));
                            g.put("seen", System.currentTimeMillis());
                            games.put(ip, g);
                        }
                    } catch (java.net.SocketTimeoutException ignored) {
                    } catch (Exception e) {
                        if (!scanning) break;
                    }
                }
                s.close();
            } catch (Exception ignored) { }
        }, "lan-scan");
        t.setDaemon(true);
        t.start();
    }

    synchronized void stopScan() {
        scanning = false;
        DatagramSocket s = listenSock;
        listenSock = null;
        if (s != null) s.close();
        try { if (lock != null && lock.isHeld()) lock.release(); } catch (Exception ignored) { }
    }

    String getGames() {
        JSONArray a = new JSONArray();
        long now = System.currentTimeMillis();
        for (JSONObject g : games.values()) {
            if (now - g.optLong("seen") < 4000) a.put(g);
        }
        return a.toString();
    }

    /** This phone's Wi-Fi (or hotspot) address, e.g. 192.168.1.23. */
    String getIp() {
        String fallback = "";
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String n = ni.getName().toLowerCase();
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                    String ip = a.getHostAddress();
                    if (n.startsWith("wlan") || n.startsWith("ap") || n.startsWith("swlan") || n.startsWith("eth")) return ip;
                    if (a.isSiteLocalAddress() && fallback.isEmpty()) fallback = ip;
                }
            }
        } catch (Exception ignored) { }
        return fallback;
    }
}
