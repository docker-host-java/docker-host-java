package com.github.dockerhostjava.connection;

import com.github.dockerhostjava.host.SshHostObject;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyEncryptionContext;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SshConnectionTest {

    @TempDir
    Path temp;

    @Test
    void connectSucceedsWithIdentityAndPort() throws Exception {
        KeyPair keyPair = keyPair();
        Path key = writeKey("key.pem", keyPair, null);
        try (SshServer server = server(0, keyPair.getPublic())) {
            SshHostObject host = new SshHostObject()
                    .user("dkk")
                    .host("127.0.0.1")
                    .port(server.getPort())
                    .auth(SshConnection.Auth.KEY_FILE)
                    .keyFilePath(key);
            host.connect();
            try (SshConnection connection = host.connection()) {
                assertTrue(connection.connected());
                assertEquals(key, connection.keyFilePath());
                assertEquals("dkk", host.user());
                assertEquals("127.0.0.1", host.host());
                assertEquals(server.getPort(), host.port());
            }
        }
    }

    @Test
    void portDefaultsTo22AndPasswordIsCopied() {
        char[] password = "pw".toCharArray();
        SshHostObject host = new SshHostObject()
                .user("dkk")
                .host("127.0.0.1")
                .auth(SshConnection.Auth.PASSWORD)
                .password(password);

        assertEquals(22, host.port());
        assertNotSame(password, host.password());
        Arrays.fill(password, '\0');
        assertArrayEquals("pw".toCharArray(), host.password());
    }

    @Test
    void connectSucceedsWithPassword() throws Exception {
        try (SshServer server = server(0, null)) {
            SshHostObject host = new SshHostObject()
                    .user("dkk")
                    .host("127.0.0.1")
                    .port(server.getPort())
                    .auth(SshConnection.Auth.PASSWORD)
                    .password("pw".toCharArray());
            host.connect();
            try (SshConnection connection = host.connection()) {
                assertTrue(connection.connected());
            }
        }
    }

    @Test
    void connectSucceedsWithEncryptedIdentity() throws Exception {
        KeyPair keyPair = keyPair();
        Path key = writeKey("key.pem", keyPair, "key-secret");
        try (SshServer server = server(0, keyPair.getPublic())) {
            SshHostObject host = new SshHostObject()
                    .user("dkk")
                    .host("127.0.0.1")
                    .port(server.getPort())
                    .auth(SshConnection.Auth.KEY_FILE)
                    .keyFilePath(key)
                    .passphrase("key-secret".toCharArray());
            host.connect();
            try (SshConnection connection = host.connection()) {
                assertTrue(connection.connected());
            }
        }
    }

    @Test
    void connectFailsWhenIdentityAndPasswordAreBothSet() throws IOException {
        Path key = temp.resolve("key.pem");
        Files.writeString(key, "secret-bytes");
        char[] password = "s3cret-value".toCharArray();
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> host()
                .auth(SshConnection.Auth.KEY_FILE)
                .keyFilePath(key)
                .password(password)
                .connect());

        assertTrue(error.getMessage().contains("ssh password is set for key file auth"));
        assertFalse(error.getMessage().contains("s3cret-value"));
        assertFalse(error.getMessage().contains("secret-bytes"));
    }

    @Test
    void connectFailsWhenPassphraseIsSetWithoutIdentity() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> host()
                .auth(SshConnection.Auth.KEY_FILE)
                .passphrase("key-secret".toCharArray())
                .connect());
        assertEquals("ssh passphrase requires a key file", error.getMessage());
    }

    @Test
    void connectFailsWhenEncryptedIdentityHasNoPassphrase() throws Exception {
        Path key = writeKey("key.pem", keyPair(), "key-secret");
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> host()
                .auth(SshConnection.Auth.KEY_FILE)
                .keyFilePath(key)
                .connect());
        assertTrue(error.getMessage().contains("ssh key file passphrase is missing: path=" + key));
        assertFalse(error.getMessage().contains("key-secret"));
    }

    @Test
    void connectFailsWhenAuthIsMissing() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> host().connect());
        assertTrue(error.getMessage().contains("ssh auth method is missing: password or key file"));
    }

    @Test
    void connectFailsWhenIdentityFileIsMissing() {
        Path missing = temp.resolve("missing.pem");
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> host()
                .auth(SshConnection.Auth.KEY_FILE)
                .keyFilePath(missing)
                .connect());
        assertTrue(error.getMessage().contains("ssh key file is missing: path=" + missing));
    }

    @Test
    void connectFailsWhenUserIsBlank() {
        SshHostObject host = new SshHostObject()
                .user(" ")
                .host("127.0.0.1")
                .auth(SshConnection.Auth.PASSWORD)
                .password("pw".toCharArray());
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                host::connect);
        assertTrue(error.getMessage().contains("ssh address is incomplete"));
    }

    @Test
    void connectFailsWhenHostIsBlank() {
        SshHostObject host = new SshHostObject()
                .user("dkk")
                .host("")
                .auth(SshConnection.Auth.PASSWORD)
                .password("pw".toCharArray());
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                host::connect);
        assertTrue(error.getMessage().contains("ssh address is incomplete"));
    }

    @Test
    void connectFailsWhenPasswordIsEmpty() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> host()
                .auth(SshConnection.Auth.PASSWORD)
                .password(new char[0])
                .connect());
        assertEquals("ssh password is empty", error.getMessage());
    }

    @Test
    void connectFailsWhenPortIsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> host()
                .port(0)
                .auth(SshConnection.Auth.PASSWORD)
                .password("pw".toCharArray())
                .connect());
        assertThrows(IllegalArgumentException.class, () -> host()
                .port(65536)
                .auth(SshConnection.Auth.PASSWORD)
                .password("pw".toCharArray())
                .connect());
    }

    @Test
    void connectFailsWhenPasswordIsWrong() throws Exception {
        try (SshServer server = server(0, null)) {
            SshHostObject host = new SshHostObject()
                    .user("dkk")
                    .host("127.0.0.1")
                    .port(server.getPort())
                    .auth(SshConnection.Auth.PASSWORD)
                    .password("s3cret-value".toCharArray());
            IllegalStateException error = assertThrows(IllegalStateException.class, host::connect);
            assertTrue(error.getMessage().contains("ssh auth failed"));
            assertTrue(error.getMessage().contains("auth=password"));
            assertFalse(error.getMessage().contains("s3cret-value"));
            assertNull(host.connection());
        }
    }

    @Test
    void connectFailsWhenIdentityIsRejected() throws Exception {
        Path key = writeKey("key.pem", keyPair(), null);
        try (SshServer server = server(0, keyPair().getPublic())) {
            SshHostObject host = new SshHostObject()
                    .user("dkk")
                    .host("127.0.0.1")
                    .port(server.getPort())
                    .auth(SshConnection.Auth.KEY_FILE)
                    .keyFilePath(key);
            IllegalStateException error = assertThrows(IllegalStateException.class, host::connect);
            assertTrue(error.getMessage().contains("auth=keyFile"));
            assertFalse(error.getMessage().contains("secret-bytes"));
            assertNull(host.connection());
        }
    }

    @Test
    void acceptNewStoresTheHostKeyAndAskAcceptsIt() throws Exception {
        Path known = temp.resolve("known_hosts");
        try (SshServer server = server(0, null)) {
            SshHostObject host = new SshHostObject()
                    .user("dkk")
                    .host("127.0.0.1")
                    .port(server.getPort())
                    .auth(SshConnection.Auth.PASSWORD)
                    .password("pw".toCharArray())
                    .knownHosts(known);
            host.connect();
            try (SshConnection first = host.connection()) {
                assertTrue(first.connected());
            }
            assertTrue(Files.size(known) > 0);

            SshHostObject again = new SshHostObject()
                    .user("dkk")
                    .host("127.0.0.1")
                    .port(server.getPort())
                    .auth(SshConnection.Auth.PASSWORD)
                    .password("pw".toCharArray())
                    .knownHosts(known)
                    .hostKeyChecking(SshConnection.HostKeyChecking.ASK);
            again.connect();
            try (SshConnection second = again.connection()) {
                assertTrue(second.connected());
            }
        }
    }

    @Test
    void askRejectsAnUnknownHostKey() throws Exception {
        Path known = temp.resolve("known_hosts");
        try (SshServer server = server(0, null)) {
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> new SshHostObject()
                    .user("dkk")
                    .host("127.0.0.1")
                    .port(server.getPort())
                    .auth(SshConnection.Auth.PASSWORD)
                    .password("pw".toCharArray())
                    .knownHosts(known)
                    .hostKeyChecking(SshConnection.HostKeyChecking.ASK)
                    .connect());
            assertTrue(error.getMessage().contains("ssh host key confirmation is required"));
            assertTrue(error.getMessage().contains("checking=ASK"));
            assertFalse(error.getMessage().contains("pw"));
            assertFalse(Files.exists(known));
        }
    }

    @Test
    void yesRejectsAnUnknownHostKey() throws Exception {
        try (SshServer server = server(0, null)) {
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> new SshHostObject()
                    .user("dkk")
                    .host("127.0.0.1")
                    .port(server.getPort())
                    .auth(SshConnection.Auth.PASSWORD)
                    .password("pw".toCharArray())
                    .knownHosts(temp.resolve("known_hosts"))
                    .hostKeyChecking(SshConnection.HostKeyChecking.YES)
                    .connect());
            assertTrue(error.getMessage().contains("ssh host key was rejected"));
            assertTrue(error.getMessage().contains("checking=YES"));
        }
    }

    @Test
    void acceptNewRejectsAChangedHostKey() throws Exception {
        Path known = temp.resolve("known_hosts");
        int port;
        try (SshServer first = server(0, null)) {
            port = first.getPort();
            SshHostObject host = new SshHostObject()
                    .user("dkk")
                    .host("127.0.0.1")
                    .port(port)
                    .auth(SshConnection.Auth.PASSWORD)
                    .password("pw".toCharArray())
                    .knownHosts(known);
            host.connect();
            try (SshConnection connection = host.connection()) {
                assertTrue(connection.connected());
            }
        }
        try (SshServer second = server(port, null)) {
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> new SshHostObject()
                    .user("dkk")
                    .host("127.0.0.1")
                    .port(port)
                    .auth(SshConnection.Auth.PASSWORD)
                    .password("pw".toCharArray())
                    .knownHosts(known)
                    .connect());
            assertTrue(error.getMessage().contains("ssh host key was rejected"));
            assertTrue(error.getMessage().contains("checking=ACCEPT_NEW"));
        }
    }

    @Test
    void reconnectReusesSameHostAndPort() throws Exception {
        KeyPair keyPair = keyPair();
        Path key = writeKey("key.pem", keyPair, null);
        try (SshServer first = server(0, keyPair.getPublic())) {
            SshHostObject host = new SshHostObject()
                    .user("dkk")
                    .host("127.0.0.1")
                    .port(first.getPort())
                    .auth(SshConnection.Auth.KEY_FILE)
                    .keyFilePath(key);
            host.connect();
            SshConnection connected = host.connection();
            try {
                assertSame(connected, host.reconnect("127.0.0.1", first.getPort()).orElseThrow());
                try (SshServer second = server(0, keyPair.getPublic())) {
                    SshConnection moved = host.reconnect("127.0.0.1", second.getPort()).orElseThrow();
                    assertNotSame(connected, moved);
                    assertTrue(moved.connected());
                    assertEquals("dkk", host.user());
                    assertEquals(key, moved.keyFilePath());
                    assertEquals(second.getPort(), host.port());
                }
            } finally {
                if (host.connection() != null) {
                    host.connection().close();
                }
            }
        }
    }

    private SshHostObject host() {
        return new SshHostObject()
                .user("dkk")
                .host("127.0.0.1");
    }

    private SshServer server(int port, PublicKey clientPublicKey) throws IOException {
        SshServer server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(port);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        server.setPasswordAuthenticator((username, password, session) ->
                "dkk".equals(username) && "pw".equals(password));
        server.setPublickeyAuthenticator((username, key, session) ->
                clientPublicKey != null && "dkk".equals(username) && KeyUtils.compareKeys(clientPublicKey, key));
        server.start();
        return server;
    }

    private Path writeKey(String name, KeyPair keyPair, String passphrase) throws IOException, GeneralSecurityException {
        Path key = temp.resolve(name);
        OpenSSHKeyEncryptionContext options = null;
        if (passphrase != null) {
            options = new OpenSSHKeyEncryptionContext();
            options.setCipherType("256");
            options.setPassword(passphrase);
        }
        try (OutputStream output = Files.newOutputStream(key)) {
            OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(keyPair, "test", options, output);
        }
        return key;
    }

    private static KeyPair keyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }
}
