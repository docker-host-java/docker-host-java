package com.github.dockerhostjava.connection.SshConnection.logic;

import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.util.io.resource.PathResource;
import org.apache.sshd.common.util.security.SecurityUtils;

import javax.security.auth.login.FailedLoginException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Iterator;

public final class KeyFileLoader {

    private KeyFileLoader() {
    }

    /** 비밀키 파일을 읽어 KeyPair로 만든다. */
    public static KeyPair load(Path keyFilePath, char[] passphrase) {
        FilePasswordProvider provider = passphrase == null
                ? null
                : FilePasswordProvider.of(new String(passphrase));
        try (InputStream input = Files.newInputStream(keyFilePath)) {
            Iterable<KeyPair> keys = SecurityUtils.loadKeyPairIdentities(
                    null, new PathResource(keyFilePath), input, provider);
            if (keys == null) {
                throw new IllegalStateException("ssh key file could not be loaded: path=" + keyFilePath);
            }
            Iterator<KeyPair> iterator = keys.iterator();
            if (!iterator.hasNext()) {
                throw new IllegalStateException("ssh key file could not be loaded: path=" + keyFilePath);
            }
            return iterator.next();
        } catch (IllegalStateException e) {
            throw e;
        } catch (GeneralSecurityException | IOException e) {
            if (passphrase == null && e instanceof FailedLoginException) {
                throw new IllegalStateException(
                        "ssh key file passphrase is missing: path=" + keyFilePath, e);
            }
            throw new IllegalStateException(
                    "ssh key file could not be loaded: path=" + keyFilePath
                            + " error=" + e.getClass().getSimpleName(),
                    e);
        }
    }
}
