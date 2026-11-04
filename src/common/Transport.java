package common;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Creates the sockets used by the server and client.
 *
 * Every socket in the project is created here, so switching between plain
 * TCP and TLS is a single flag. Level 5 needs captures of BOTH versions, so
 * the plaintext mode is kept rather than replaced when TLS is added.
 */
public final class Transport {

    public enum Mode {
        PLAIN, TLS
    }

    private Transport() {
    }

    /** Binds to all interfaces (0.0.0.0) so other machines can connect (Level 4). */
    public static ServerSocket createServerSocket(Mode mode, int port) throws IOException {
        if (mode == Mode.TLS) {
            throw new UnsupportedOperationException("TLS mode is added in Level 2.");
        }
        return new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
    }

    public static Socket createClientSocket(Mode mode, String host, int port) throws IOException {
        if (mode == Mode.TLS) {
            throw new UnsupportedOperationException("TLS mode is added in Level 2.");
        }
        return new Socket(host, port);
    }

    /** Reads "--secure" from the command-line arguments. */
    public static Mode modeFromArgs(String[] args) {
        for (String arg : args) {
            if (arg.equals("--secure")) {
                return Mode.TLS;
            }
        }
        return Mode.PLAIN;
    }
}
