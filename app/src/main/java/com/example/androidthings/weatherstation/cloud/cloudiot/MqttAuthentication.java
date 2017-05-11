/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.androidthings.weatherstation.cloud.cloudiot;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;
import com.google.common.io.BaseEncoding;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import javax.security.auth.x500.X500Principal;
import org.joda.time.DateTime;

/**
 * This class wraps the storage and access of the authentication key used for Cloud IoT.  One of
 * the driving reasons for this is to leverage the secure key storage on Android Things.
 *
 * This class currently only support RS256 Authentication.  TODO:  Add EC256 support.
 */

public class MqttAuthentication {

  private static final String TAG = MqttAuthentication.class.getSimpleName();
  private static final String DEFAULT_KEYSTORE = "AndroidKeyStore";
  private static final String DEFAULT_ALIAS = "Cloud IoT Authentication";
  private final String keystoreName;
  private final String keyAlias;

  private Certificate certificate;
  private PrivateKey privateKey;

  /**
   * Create a new Cloud IoT Authentication wrapper using the default keystore and alias.
   */
  public MqttAuthentication() {
    this(DEFAULT_KEYSTORE, DEFAULT_ALIAS);
  }

  /**
   * Create a new Cloud IoT Authentication wrapper using the specified keystore and alias (instead
   * of the defaults)
   *
   * @param keystoreName The keystore to load
   * @param keyAlias the alias in the keystore for Cloud IoT Authentication
   */
  public MqttAuthentication(String keystoreName, String keyAlias) {
    this.keystoreName = keystoreName;
    this.keyAlias = keyAlias;
  }

  public void initialize() {
    try {
      KeyStore ks = KeyStore.getInstance(keystoreName);
      ks.load(null);

      certificate = ks.getCertificate(keyAlias);
      if (certificate == null) {
        // generate key
        Log.w(TAG, "No IoT Auth Certificate found, generating new cert");
        generateAuthenticationKey();
        certificate = ks.getCertificate(keyAlias);
      }
      Log.i(TAG, "loaded certificate: " + keyAlias);

      if (certificate instanceof X509Certificate) {
        X509Certificate x509Certificate = (X509Certificate) certificate;
        Log.d(TAG, "Subject: " + x509Certificate.getSubjectX500Principal().toString());
        Log.d(TAG, "Issuer: " + x509Certificate.getIssuerX500Principal().toString());
        Log.d(TAG, "Signature: " + BaseEncoding.base16().lowerCase().withSeparator(":", 2)
            .encode(x509Certificate.getSignature()));
      }

      Key key = ks.getKey(keyAlias, null);
      privateKey = (PrivateKey) key;
      boolean keyIsInSecureHardware = false;
      try {
        KeyFactory factory = KeyFactory.getInstance(privateKey.getAlgorithm(), keystoreName);
        KeyInfo keyInfo = factory.getKeySpec(privateKey, KeyInfo.class);
        keyIsInSecureHardware = keyInfo.isInsideSecureHardware();
        Log.d(TAG, "able to confirm if key is secured or not");
      } catch (GeneralSecurityException e) {
        // ignored
      }
      Log.i(TAG, "Key is in secure hardware? " + keyIsInSecureHardware);


    } catch (GeneralSecurityException | IOException e) {
      Log.e(TAG, "Failed to open keystore", e);
    }

  }

  /**
   * Generate a new RSA key pair entry in the Android Keystore by
   * using the KeyPairGenerator API. This creates both a KeyPair
   * and a self-signed certificate, both with the same alias
   */
  private void generateAuthenticationKey() throws GeneralSecurityException {

    KeyPairGenerator kpg = KeyPairGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_RSA, keystoreName);
    kpg.initialize(new KeyGenParameterSpec.Builder(
        keyAlias,
        KeyProperties.PURPOSE_SIGN)
        .setKeySize(2048)
        .setCertificateSubject(new X500Principal("CN=unused"))
        .setDigests(KeyProperties.DIGEST_SHA256)
        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
        .build());

    kpg.generateKeyPair();
  }

  /**
   * Exports the authentication certificate to a file.
   *
   * @param destination the file to write the certificate to (PEM encoded)
   */
  public void exportPublicKey(File destination) throws IOException, GeneralSecurityException {
    FileOutputStream os = new FileOutputStream(destination);
    os.write(getCertificatePEM().getBytes());
    os.flush();
    os.close();
  }

  public Certificate getCertificate() {
    return certificate;
  }

  /**
   * Returns the PEM-format encoded certificate
   */
  public String getCertificatePEM() throws GeneralSecurityException {
    StringBuilder sb = new StringBuilder();
    sb.append("-----BEGIN CERTIFICATE-----\n");
    sb.append(Base64.encodeToString(certificate.getEncoded(), Base64.DEFAULT));
    sb.append("-----END CERTIFICATE-----\n");
    return sb.toString();
  }

  public PrivateKey getPrivateKey() {
    return privateKey;
  }

  public char[] createJwt(String projectId)
      throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
    DateTime now = new DateTime();

    // Create a JWT to authenticate this device. The device will be disconnected after the token
    // expires, and will have to reconnect with a new token. The audience field should always
    // be set to the GCP project id.
    JwtBuilder jwtBuilder =
        Jwts.builder()
            .setIssuedAt(now.toDate())
            .setExpiration(now.plusMinutes(60).toDate())
            .setAudience(projectId);

    return jwtBuilder.signWith(SignatureAlgorithm.RS256, privateKey).compact().toCharArray();
  }
}