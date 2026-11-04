package server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe record of who is in the group chat.
 *
 * Each ClientHandler runs on its own thread, so joins, leaves and broadcasts
 * can happen at the same time. A ConcurrentHashMap lets them all proceed
 * without a ConcurrentModificationException, and putIfAbsent makes the
 * "is this username free?" check and the registration a single atomic step,
 * so two people can't grab the same name at the same moment.
 */
public class ClientRegistry {

    // Key is the lower-case username so "Ahmed" and "ahmed" count as the same.
    private final ConcurrentMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    /** Returns true if the name was free and the client is now registered. */
    public boolean register(String username, ClientHandler handler) {
        return clients.putIfAbsent(username.toLowerCase(), handler) == null;
    }

    public void unregister(String username, ClientHandler handler) {
        clients.remove(username.toLowerCase(), handler);
    }

    /** Sends a line to every joined client. */
    public void broadcast(String line) {
        for (ClientHandler client : clients.values()) {
            client.send(line);
        }
    }

    public List<String> usernames() {
        List<String> names = new ArrayList<>();
        for (ClientHandler client : clients.values()) {
            names.add(client.getUsername());
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public int size() {
        return clients.size();
    }

    /** Disconnects everyone, used when the server shuts down. */
    public void closeAll() {
        for (ClientHandler client : clients.values()) {
            client.close();
        }
    }
}
