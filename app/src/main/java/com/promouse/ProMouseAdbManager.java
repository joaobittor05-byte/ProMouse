package com.promouse;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;

import androidx.annotation.NonNull;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

public final class ProMouseAdbManager extends AbsAdbConnectionManager {
    private static final String PREF = "promouse_adb_identity";
    private static final String KEY_PRIVATE = "private_key";
    private static final String KEY_CERTIFICATE = "certificate";
    private static final BouncyCastleProvider BC = new BouncyCastleProvider();

    private static volatile ProMouseAdbManager instance;

    private PrivateKey privateKey;
    private X509Certificate certificate;

    public static ProMouseAdbManager getInstance(Context context) throws Exception {
        if (instance == null) {
            synchronized (ProMouseAdbManager.class) {
                if (instance == null) instance = new ProMouseAdbManager(context.getApplicationContext());
            }
        }
        return instance;
    }

    private ProMouseAdbManager(Context context) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        setHostAddress("127.0.0.1");
        setTimeout(12, TimeUnit.SECONDS);
        loadOrCreateIdentity(context);
    }

    private void loadOrCreateIdentity(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String privateEncoded = prefs.getString(KEY_PRIVATE, null);
        String certificateEncoded = prefs.getString(KEY_CERTIFICATE, null);

        if (privateEncoded != null && certificateEncoded != null) {
            try {
                byte[] privateBytes = Base64.decode(privateEncoded, Base64.NO_WRAP);
                byte[] certBytes = Base64.decode(certificateEncoded, Base64.NO_WRAP);
                privateKey = KeyFactory.getInstance("RSA")
                        .generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
                certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(certBytes));
                return;
            } catch (Exception ignored) {
                prefs.edit().clear().apply();
            }
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, new SecureRandom());
        KeyPair pair = generator.generateKeyPair();

        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - TimeUnit.DAYS.toMillis(1));
        Date notAfter = new Date(now + TimeUnit.DAYS.toMillis(3650));
        BigInteger serial = new BigInteger(63, new SecureRandom()).abs().add(BigInteger.ONE);
        X500Name subject = new X500Name("CN=ProMouse ADB Host");

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject,
                serial,
                notBefore,
                notAfter,
                subject,
                pair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BC)
                .build(pair.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider(BC)
                .getCertificate(certBuilder.build(signer));
        cert.verify(pair.getPublic());

        privateKey = pair.getPrivate();
        certificate = cert;

        prefs.edit()
                .putString(KEY_PRIVATE, Base64.encodeToString(privateKey.getEncoded(), Base64.NO_WRAP))
                .putString(KEY_CERTIFICATE, Base64.encodeToString(certificate.getEncoded(), Base64.NO_WRAP))
                .apply();
    }

    @NonNull
    @Override
    protected PrivateKey getPrivateKey() {
        return privateKey;
    }

    @NonNull
    @Override
    protected Certificate getCertificate() {
        return certificate;
    }

    @NonNull
    @Override
    protected String getDeviceName() {
        return "ProMouse";
    }
}
