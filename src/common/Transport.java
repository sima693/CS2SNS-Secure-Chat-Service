package common;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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

    /** Binds to all interfaces (0.0.0.0) so other machines can connect (Level 4). */
    public static ServerSocket createServerSocket(Mode mode, int port) throws IOException {

    if (mode == Mode.TLS) {
        try {
            /* Read the keystore password from an environment variable
             rather than hard-coding secrets into the source code.*/

            /* Coursework testing uses a self-signed certificate generated locally.
             Because the certificate is not issued by a trusted Certificate
            Authority (CA), the client would normally reject it. */

            /* TODO: Replace this trust-all configuration with a dedicated
             client truststore that validates only trusted server certificates.*/
            
             /* Read the keystore password from an environment variable
             rather than storing sensitive credentials in source control. */
            String password = System.getenv("KEYSTORE_PASSWORD");

            if (password == null || password.isBlank()) {
                throw new IOException(
                        "KEYSTORE_PASSWORD environment variable is not set.");
            }
            // Load the server certificate and private key from the Java 
            KeyStore ks = KeyStore.getInstance("JKS");

            try (FileInputStream fis = new FileInputStream("certs/server.jks")) {
                ks.load(fis, password.toCharArray());
            }
            /* Creates key managers which present the server certificate
             during the TLS handshake.*/
            KeyManagerFactory kmf =
                    KeyManagerFactory.getInstance(
                            KeyManagerFactory.getDefaultAlgorithm());

            kmf.init(ks, password.toCharArray());

            // Configure a TLS context for secure communication.             
            SSLContext context = SSLContext.getInstance("TLS");

            context.init(
                    kmf.getKeyManagers(),
                    null,
                    null);

            /* Create an SSL-enabled server socket rather than a normal
            TCP ServerSocket. */        
            SSLServerSocketFactory factory =
                    context.getServerSocketFactory();

            return factory.createServerSocket(port);

        } catch (Exception e) {
            throw new IOException("Failed to create TLS server socket", e);
        }
    }

    return new ServerSocket(
            port,
            50,
            InetAddress.getByName("0.0.0.0"));
}

    public static Socket createClientSocket(Mode mode, String host, int port) throws IOException {

    if (mode == Mode.TLS) {
    try {
        /* Trust manager used for local coursework testing with a
         self-signed certificate. In production a dedicated
         truststore should be used instead.*/

        TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(
                            X509Certificate[] chain,
                            String authType) {
                    }

                    public void checkServerTrusted(
                            X509Certificate[] chain,
                            String authType) {
                    }
                }
        };

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustAll, null);

        SSLSocketFactory factory =
                context.getSocketFactory();

        return factory.createSocket(host, port);

    } catch (Exception e) {
        throw new IOException("Failed to create TLS client socket", e);
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
