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

package com.example.androidthings.sensorhub.cloud.cloudiot;

import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;

import com.example.androidthings.sensorhub.SensorData;
import com.example.androidthings.sensorhub.cloud.CloudPublisher;
import com.example.androidthings.sensorhub.cloud.MessagePayload;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private MqttClient mqttClient;
    private CloudIotOptions cloudIotOptions;
    private MqttAuthentication mqttAuth;
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
                "com.example.androidthings.sensorhub.mqtt.CONFIGURE " +
                "-e project_id <PROJECT_ID> -e registry_id <REGISTRY_ID> " +
                "-e device_id <DEVICE_ID> " +
                "com.example.androidthings.sensorhub/.cloud.CloudPublisherService\n");
            return;
        }
        try {
            cloudIotOptions = options;
            Log.i(TAG, "Device Configuration:");
            Log.i(TAG, " Project ID: "+cloudIotOptions.getProjectId());
            Log.i(TAG, "  Region ID: "+cloudIotOptions.getCloudRegion());
            Log.i(TAG, "Registry ID: "+cloudIotOptions.getRegistryId());
            Log.i(TAG, "  Device ID: "+cloudIotOptions.getDeviceId());
            Log.i(TAG, "MQTT Configuration:");
            Log.i(TAG, "Broker: "+cloudIotOptions.getBridgeHostname()+":"+cloudIotOptions.getBridgePort());
            Log.i(TAG, "Publishing to topic: "+cloudIotOptions.getTopicName());
            mqttAuth = new MqttAuthentication();
            mqttAuth.initialize();
            if( Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                try {
                    mqttAuth.exportPublicKey(new File(Environment.getExternalStorageDirectory(),
                        "cloud_iot_auth_certificate.pem"));
                } catch (GeneralSecurityException | IOException e) {
                    if( e instanceof FileNotFoundException && e.getMessage().contains("Permission denied")) {
                        Log.e(TAG, "Unable to export certificate, may need to reboot to receive WRITE permissions?", e);
                    } else {
                        Log.e(TAG, "Unable to export certificate", e);
                    }
                }
            }
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
                String payload = MessagePayload.createMessagePayload(data);
                Log.d(TAG, "Publishing: "+payload);
                sendMessage(cloudIotOptions.getTopicName(), payload.getBytes());
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

        // generate the jwt password
        options.setPassword(mqttAuth.createJwt(cloudIotOptions.getProjectId()));

        mqttClient.connect(options);
        mReady.set(true);
    }

    private void sendMessage(String mqttTopic, byte[] mqttMessage) throws MqttException {
        mqttClient.publish(mqttTopic, mqttMessage, MQTT_QOS, SHOULD_RETAIN);
    }
}
