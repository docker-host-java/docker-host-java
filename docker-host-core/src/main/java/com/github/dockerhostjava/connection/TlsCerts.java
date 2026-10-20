package com.github.dockerhostjava.connection;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class TlsCerts {

    private Path caPem;
    private Path certPem;
    private Path keyPem;

    public TlsCerts caPem(Path caPem) {
        this.caPem = caPem;
        return this;
    }

    public TlsCerts certPem(Path certPem) {
        this.certPem = certPem;
        return this;
    }

    public TlsCerts keyPem(Path keyPem) {
        this.keyPem = keyPem;
        return this;
    }

    Path caPem() {
        return caPem;
    }

    Path certPem() {
        return certPem;
    }

    Path keyPem() {
        return keyPem;
    }

    SSLContext sslContext() {
        validateFile(caPem, "ca.pem");
        validateFile(certPem, "cert.pem");
        validateFile(keyPem, "key.pem");
        try {
            // X509Certificate : .pem reader class
            X509Certificate ca = readCertificate(caPem);
            X509Certificate cert = readCertificate(certPem);
            PrivateKey key = readPrivateKey(keyPem);

            // ca
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("ca", ca);
            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(trustStore);

            // cert + key
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            char[] password = new char[0];
            keyStore.setKeyEntry("client", key, password, new Certificate[]{cert, ca});
            KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(keyStore, password);

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
            return context;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException(
                    "tls certs could not be loaded: ca=" + caPem
                            + " cert=" + certPem
                            + " key=" + keyPem
                            + " error=" + e.getMessage(),
                    e);
        }
    }

    // 경로가 비었거나 파일이 아니면 예외
    private static void validateFile(Path path, String name) {
        if (path == null) {
            throw new IllegalStateException("tls cert is missing: " + name);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("tls cert file is missing: " + name + " path=" + path);
        }
    }

    // PEM 인증서를 X509Certificate로 읽는다
    private static X509Certificate readCertificate(Path path) throws CertificateException, IOException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (InputStream input = Files.newInputStream(path)) {
            return (X509Certificate) factory.generateCertificate(input);
        }
    }

    // PEM 개인키를 PrivateKey로 읽는다. PKCS#8 또는 PKCS#1 RSA
    private static PrivateKey readPrivateKey(Path path) throws GeneralSecurityException, IOException {
        String pem = Files.readString(path);
        if (pem.contains("ENCRYPTED")) {
            throw new IllegalStateException("tls key is encrypted: path=" + path);
        }
        if (pem.contains("BEGIN PRIVATE KEY")) {
            return pkcs8(pemBytes(path, pem, "PRIVATE KEY"));
        }
        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            return pkcs8(pkcs1ToPkcs8(pemBytes(path, pem, "RSA PRIVATE KEY")));
        }
        throw new IllegalStateException(
                "tls key format is unsupported: path=" + path
                        + " expected PKCS#8 PRIVATE KEY or PKCS#1 RSA PRIVATE KEY");
    }

    // PKCS#8 바이트를 RSA 또는 EC PrivateKey로 만든다
    private static PrivateKey pkcs8(byte[] encoded) throws GeneralSecurityException {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(encoded);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (InvalidKeySpecException ignored) {
            return KeyFactory.getInstance("EC").generatePrivate(spec);
        }
    }

    // PEM 헤더 사이 Base64를 바이트로 푼다
    private static byte[] pemBytes(Path path, String pem, String type) {
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        int start = pem.indexOf(begin);
        int stop = pem.indexOf(end);
        if (start < 0 || stop < 0) {
            throw new IllegalStateException("tls pem block is missing: path=" + path + " type=" + type);
        }
        return Base64.getMimeDecoder().decode(pem.substring(start + begin.length(), stop));
    }

    // PKCS#1 RSA 개인키를 PKCS#8로 감싼다
    private static byte[] pkcs1ToPkcs8(byte[] pkcs1) {
        byte[] version = new byte[]{0x02, 0x01, 0x00};
        byte[] algorithm = new byte[]{
                0x30, 0x0d,
                0x06, 0x09,
                0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
                0x05, 0x00
        };
        return der(0x30, concat(version, algorithm, der(0x04, pkcs1)));
    }

    // tag와 값을 DER 한 덩어리로 붙인다
    private static byte[] der(int tag, byte[] value) {
        byte[] length = derLength(value.length);
        byte[] out = new byte[1 + length.length + value.length];
        out[0] = (byte) tag;
        System.arraycopy(length, 0, out, 1, length.length);
        System.arraycopy(value, 0, out, 1 + length.length, value.length);
        return out;
    }

    // DER 길이 필드를 만든다
    private static byte[] derLength(int length) {
        if (length < 128) {
            return new byte[]{(byte) length};
        }
        if (length <= 0xff) {
            return new byte[]{(byte) 0x81, (byte) length};
        }
        return new byte[]{(byte) 0x82, (byte) (length >> 8), (byte) length};
    }

    // 바이트 배열을 순서대로 이어 붙인다
    private static byte[] concat(byte[]... parts) {
        int size = 0;
        for (byte[] part : parts) {
            size += part.length;
        }
        byte[] out = new byte[size];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }
}
