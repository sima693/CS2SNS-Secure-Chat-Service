package server;

import common.Args;
import common.Protocol;
import common.Transport;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point for the group chat server.
 *
 * Usage: java -cp out server.ChatServer [--port 8888] [--secure]
 *
 * The main thread only accepts connections; each client is handed to a
 * ClientHandler running on a thread pool, so one slow or misbehaving client
 * never blocks the others.
 */
public class ChatServer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int port;
    private final Transport.Mode mode;
    private final ClientRegistry registry = new ClientRegistry();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile ServerSocket serverSocket;

    public ChatServer(int port, Transport.Mode mode) {
        this.port = port;
        this.mode = mode;
    }

    public void start() throws IOException {
        serverSocket = Transport.createServerSocket(mode, port);
        log("Server listening on port " + port + " (" + mode + " mode). Ctrl+C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                log("CONN  " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
                pool.execute(new ClientHandler(socket, registry));
            } catch (SocketException e) {
                if (!serverSocket.isClosed()) {
                    log("Accept failed: " + e.getMessage());
                }
            }
        }
    }

    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                log("Shutting down...");
                registry.broadcast("*** Server is shutting down.");
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // Closing anyway.
        }
        registry.closeAll();
        pool.shutdownNow();
    }

    static void log(String message) {
        System.out.println("[" + LocalTime.now().format(TIME) + "] " + message);
    }

    public static void main(String[] args) {
        try {
            int port = Args.getInt(args, "--port", Protocol.DEFAULT_PORT);
            new ChatServer(port, Transport.modeFromArgs(args)).start();
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
            System.exit(1);
        }
    }
}
