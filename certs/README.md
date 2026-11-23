# certs/

Certificates, keystores and truststores used for Level 2 SSL/TLS encryption.

## Files

- `server.jks` - Server keystore containing the server certificate and private key. This file is git-ignored and must not be committed.
- `server.cer` - Public server certificate used to create client truststores. This file may be committed.
- `client-truststore.jks` - Client truststore containing trusted server certificates. Generated locally for testing and deployment.

## Generating the server certificate

```text
keytool -genkeypair -alias chatserver -keyalg RSA -keysize 2048 -validity 365 -keystore certs/server.jks
```

## Exporting the public certificate

```text
keytool -exportcert -alias chatserver -keystore certs/server.jks -file certs/server.cer
```

## Creating the client truststore

```text
keytool -importcert -alias chatserver -file certs/server.cer -keystore certs/client-truststore.jks
```

## Runtime Configuration

The application expects the keystore password to be supplied through the `KEYSTORE_PASSWORD` environment variable.

Example:

```text
KEYSTORE_PASSWORD=Aston2468
```
