package client;

import common.Args;
import common.Protocol;
import common.Transport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Text-based chat client.
 *
 * Usage: java -cp out client.ChatClient [--host localhost] [--port 8888] [--secure]
 *
 * Two threads run at once: a background listener prints everything the
 * server sends, while the main thread reads what the user types. That way
 * incoming messages appear immediately even while the user is typing.
 */
public class ChatClient {

    private final String host;
    private final int port;
    private final Transport.Mode mode;
    private volatile boolean leaving = false;

    public ChatClient(String host, int port, Transport.Mode mode) {
        this.host = host;
        this.port = port;
        this.mode = mode;
    }

    public void start() throws IOException {
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, Protocol.CHARSET));

        try (Socket socket = Transport.createClientSocket(mode, host, port);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), Protocol.CHARSET));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), Protocol.CHARSET), true)) {

            System.out.println("Connected to " + host + ":" + port + " (" + mode + " mode).");

            if (!joinChat(keyboard, in, out)) {
                return;
            }

            Thread listener = new Thread(() -> listen(in), "server-listener");
            listener.setDaemon(true);
            listener.start();

            String line;
            while ((line = keyboard.readLine()) != null) {
                if (!listener.isAlive()) {
                    break; // server went away
                }
                out.println(line);
                if (line.strip().equalsIgnoreCase(Protocol.CMD_QUIT)) {
                    leaving = true;
                    break;
                }
            }
            leaving = true;
            // Give the listener a moment to print the server's goodbye.
            listener.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Runs the username handshake. Returns false if the connection closed first. */
    private boolean joinChat(BufferedReader keyboard, BufferedReader in, PrintWriter out) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (line.equals(Protocol.USERNAME_PROMPT)) {
                System.out.print("Enter your username: ");
                System.out.flush();
                String name = keyboard.readLine();
                if (name == null) {
                    return false;
                }
                out.println(name);
            } else if (line.startsWith(Protocol.ACCEPTED_PREFIX)) {
                System.out.println("Joined as " + line.substring(Protocol.ACCEPTED_PREFIX.length())
                        + ". Type " + Protocol.CMD_QUIT + " to leave.");
                return true;
            } else if (line.startsWith(Protocol.REJECTED_PREFIX)) {
                System.out.println(line.substring(Protocol.REJECTED_PREFIX.length()));
            } else {
                System.out.println(line);
            }
        }
        System.out.println("Server closed the connection.");
        return false;
    }

    /** Prints every line from the server until the connection ends. */
    private void listen(BufferedReader in) {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            // Socket closed - reported below.
        }
        if (!leaving) {
            System.out.println("Disconnected from server. Press Enter to exit.");
        }
    }

    public static void main(String[] args) {
        try {
            String host = Args.get(args, "--host", "localhost");
            int port = Args.getInt(args, "--port", Protocol.DEFAULT_PORT);
            new ChatClient(host, port, Transport.modeFromArgs(args)).start();
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Could not connect: " + e.getMessage());
            System.exit(1);
        }
    }
}
