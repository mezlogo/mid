package mezlogo.mid.netty.test;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/*
keytool -genkeypair -alias ssl -keyalg RSA -keysize 2048 -dname "CN=localhost,OU=IT,O=mezlogo,L=SaintPetersburg,C=RU" -validity 1140 -keystore keystore.jks -storepass changeit -keypass changeit -ext san:critical=dns:localhost,ip:127.0.0.1 -ext bc=ca:false

keytool -importkeystore -srckeystore keystore.jks -destkeystore keystore.jks -deststoretype pkcs12
 */
public class SslUtils {

    public static SSLContext buildSSLContext() {
        SSLContext context = null;
        try {
            context = SSLContext.getInstance("TLS");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        var km = buildManagers();

        try {
            context.init(km, null, null);
        } catch (KeyManagementException e) {
            throw new IllegalStateException(e);
        }

        return context;
    }

    public static KeyManager[] buildManagers() {
        var is = SslUtils.class.getResourceAsStream("/keystore.jks");
        var pass = "changeit";
        var ks = getKeyStore(is, pass);
        var km = getKeyManagers(ks, pass);
        return km;
    }

    public static KeyStore getKeyStore(InputStream is, String pass) {
        try {
            KeyStore keyStore = KeyStore.getInstance("pkcs12");
            keyStore.load(is, pass.toCharArray());
            return keyStore;
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    public static KeyManager[] getKeyManagers(KeyStore keyStore, String pass) {
        String keyManagerAlgo = KeyManagerFactory.getDefaultAlgorithm();
        KeyManagerFactory keyManagerFactory = null;
        try {
            keyManagerFactory = KeyManagerFactory.getInstance(keyManagerAlgo);
            keyManagerFactory.init(keyStore, pass.toCharArray());
            return keyManagerFactory.getKeyManagers();
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
            throw new IllegalStateException(e);
        }
    }
}

