package common;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * Creates the sockets used by the server and client.
 *
 * Supports both plaintext TCP communication and encrypted TLS
 * communication. The transport mode is selected using the
 * "--secure" command-line argument.
 *
 * TLS mode uses a Java KeyStore (JKS) containing the server
 * certificate and private key.
 */
public final class Transport {

    public enum Mode {
        PLAIN, TLS
    }

    private Transport() {
    }

    /**
     * Binds to all interfaces (0.0.0.0) so other machines can connect
     * (Level 4).
     */
    public static ServerSocket createServerSocket(
            Mode mode,
            int port) throws IOException {

        if (mode == Mode.TLS) {
            try {

                String password = System.getenv("KEYSTORE_PASSWORD");

                if (password == null || password.isBlank()) {
                    throw new IOException(
                            "KEYSTORE_PASSWORD environment variable is not set.");
                }

                // Load the server certificate and private key.
                KeyStore ks = KeyStore.getInstance("JKS");

                try (FileInputStream fis =
                             new FileInputStream("certs/server.jks")) {
                    ks.load(fis, password.toCharArray());
                }

                /* Creates key managers which present the server
                 * certificate during the TLS handshake.
                 */
                KeyManagerFactory kmf =
                        KeyManagerFactory.getInstance(
                                KeyManagerFactory.getDefaultAlgorithm());

                kmf.init(ks, password.toCharArray());

                SSLContext context = SSLContext.getInstance("TLS");

                context.init(
                        kmf.getKeyManagers(),
                        null,
                        null);

                /* Create an SSL-enabled server socket rather than
                 * a normal TCP ServerSocket.
                 */
                SSLServerSocketFactory factory =
                        context.getServerSocketFactory();

                return factory.createServerSocket(port);

            } catch (Exception e) {
                throw new IOException(
                        "Failed to create TLS server socket",
                        e);
            }
        }

        return new ServerSocket(
                port,
                50,
                InetAddress.getByName("0.0.0.0"));
    }

    public static Socket createClientSocket(
            Mode mode,
            String host,
            int port) throws IOException {

        if (mode == Mode.TLS) {
            try {

                String password = System.getenv("KEYSTORE_PASSWORD");

                if (password == null || password.isBlank()) {
                    throw new IOException(
                            "KEYSTORE_PASSWORD environment variable is not set.");
                }

                /* Load the client truststore containing trusted
                 * server certificates. This allows the client
                 * to verify the server certificate during the
                 * TLS handshake.
                 */
                KeyStore trustStore = KeyStore.getInstance("JKS");

                try (FileInputStream fis =
                             new FileInputStream(
                                     "certs/client-truststore.jks")) {
                    trustStore.load(
                            fis,
                            password.toCharArray());
                }

                TrustManagerFactory tmf =
                        TrustManagerFactory.getInstance(
                                TrustManagerFactory.getDefaultAlgorithm());

                tmf.init(trustStore);

                SSLContext context = SSLContext.getInstance("TLS");

                context.init(
                        null,
                        tmf.getTrustManagers(),
                        null);

                SSLSocketFactory factory =
                        context.getSocketFactory();

                SSLSocket socket =
                        (SSLSocket) factory.createSocket(
                                host,
                                port);

                /* Explicitly perform the TLS handshake before
                 * any application data is exchanged.
                 */
                socket.startHandshake();

                System.out.println(
                        "TLS handshake completed successfully.");

                return socket;

            } catch (SSLHandshakeException e) {

                throw new IOException(
                        "TLS handshake failed. The server certificate could not be verified.",
                        e);

            } catch (Exception e) {

                throw new IOException(
                        "Failed to create TLS client socket",
                        e);
            }
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
