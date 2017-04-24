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

import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;

import com.example.androidthings.weatherstation.SensorData;
import com.example.androidthings.weatherstation.cloud.CloudPublisher;
import com.example.androidthings.weatherstation.cloud.MessagePayload;

import org.apache.commons.io.IOUtils;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.joda.time.DateTime;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * Handle publishing sensor data to a Cloud IoT MQTT endpoint.
 *
 */
public class MQTTPublisher implements CloudPublisher {

    private static final String TAG = MQTTPublisher.class.getSimpleName();

    // Indicate if this message should be a MQTT 'retained' message.
    private static final boolean SHOULD_RETAIN = false;

    // Use mqttQos=1 (at least once delivery), mqttQos=0 (at most once delivery) also supported.
    private static final int MQTT_QOS = 1;

    // Instance names for various key factories.
    private static final String RSA_KEY_FACTORY_INSTANCE_NAME = "RSA";
    private static final String EX_KEY_FACTORY_INSTANCE_NAME = "EC";

    private MqttClient mqttClient;
    private CloudIotOptions cloudIotOptions;
    private AtomicBoolean mReady = new AtomicBoolean(false);

    public MQTTPublisher(@NonNull CloudIotOptions options) {
        initialize(options);
    }

    @Override
    public void reconfigure(@NonNull CloudIotOptions newOptions) {
        if (newOptions.equals(cloudIotOptions)) {
            return;
        }
        mReady.set(false);
        try {
            close();
        } catch (MqttException e) {
            // empty
        }
        initialize(newOptions);
    }

    /**
     * Initialize a Cloud IoT Endpoint given a set of configuration options.
     *
     * @param options Cloud IoT configuration options.
     */
    private void initialize(@NonNull CloudIotOptions options) {
        if (!options.isValid()) {
            Log.w(TAG, "Postponing initialization, since CloudIotOptions is incomplete. " +
                    "Please configure via intent, for example: \n" +
                    "adb shell am startservice -a " +
                    "com.example.androidthings.weatherstation.mqtt.CONFIGURE " +
                    "-e project_id <PROJECT_ID> -e registry_id <REGISTRY_ID> " +
                    "-e device_id <DEVICE_ID> " +
                    "com.example.androidthings.weatherstation/.cloud.CloudPublisherService\n");
            return;
        }
        try {
            cloudIotOptions = options;
            initializeMqttClient();
        } catch (MqttException | IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Could not initialize MQTT", e);
        }
    }

    @Override
    public void publish(List<SensorData> data) {
        try {
            if (isReady()) {
                if (mqttClient != null && !mqttClient.isConnected()) {
                    // if for some reason the mqtt client has disconnected, we should try to connect
                    // it again.
                    try {
                        initializeMqttClient();
                    } catch (MqttException | IOException | GeneralSecurityException e) {
                        throw new IllegalArgumentException("Could not initialize MQTT", e);
                    }
                }
                sendMessage(cloudIotOptions.getTopicName(), MessagePayload.encode(data));
            }
        } catch (MqttException e) {
            throw new IllegalArgumentException("Could not send message", e);
        }
    }

    @Override
    public boolean isReady() {
        return mReady.get();
    }

    @Override
    public void close() throws MqttException {
        cloudIotOptions = null;
        if (mqttClient != null) {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
            mqttClient = null;
        }
    }

    private byte[] getKeyBytes() throws IOException {
        File keyFile = new File(Environment.getExternalStorageDirectory(),
                cloudIotOptions.getPrivateKeyFile());
        try (FileInputStream inputStream = new FileInputStream(keyFile)) {
            return IOUtils.toByteArray(inputStream);
        }
    }

    private void initializeMqttClient()
            throws MqttException, IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        mqttClient = new MqttClient(cloudIotOptions.getBrokerUrl(),
                cloudIotOptions.getClientId(), new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        // Note that the the Google Cloud IoT only supports MQTT 3.1.1, and Paho requires that we
        // explicitly set this. If you don't set MQTT version, the server will immediately close its
        // connection to your device.
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setUserName(CloudIotOptions.UNUSED_ACCOUNT_NAME);
        options.setPassword(createJwt(cloudIotOptions.getProjectId(),
                cloudIotOptions.getAlgorithm(), getKeyBytes()));

        mqttClient.connect(options);
        mReady.set(true);
    }

    private char[] createJwt(String projectId, String algorithm, byte[] privateKeyBytes)
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

        SignatureAlgorithm algorithmObj;
        String factoryName;

        switch (algorithm) {
            case CloudIotOptions.RS_256_DESIGNATOR:
                algorithmObj = SignatureAlgorithm.RS256;
                factoryName = RSA_KEY_FACTORY_INSTANCE_NAME;
                break;
            case CloudIotOptions.ES_256_DESIGNATOR:
                algorithmObj = SignatureAlgorithm.ES256;
                factoryName = EX_KEY_FACTORY_INSTANCE_NAME;
                break;
            default:
                throw new IllegalArgumentException(
                        "Invalid algorithm " + algorithm + ". Should be one of '" +
                                CloudIotOptions.ES_256_DESIGNATOR + "' or '" +
                                CloudIotOptions.RS_256_DESIGNATOR + "'.");
        }
        PrivateKey privateKey = wrapPrivateKey(factoryName, privateKeyBytes);
        return jwtBuilder.signWith(algorithmObj, privateKey).compact().toCharArray();
    }

    private static PrivateKey wrapPrivateKey(String algorithm, byte[] keyBytes)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance(algorithm);
        return kf.generatePrivate(spec);
    }

    private void sendMessage(String mqttTopic, byte[] mqttMessage) throws MqttException {
        mqttClient.publish(mqttTopic, mqttMessage, MQTT_QOS, SHOULD_RETAIN);
    }
}
