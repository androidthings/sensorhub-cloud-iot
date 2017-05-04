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

package com.example.androidthings.weatherstation.cloud.pubsub;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Base64;
import android.util.Log;

import com.example.androidthings.weatherstation.BuildConfig;
import com.example.androidthings.weatherstation.SensorData;
import com.example.androidthings.weatherstation.cloud.CloudPublisher;
import com.example.androidthings.weatherstation.cloud.MessagePayload;
import com.example.androidthings.weatherstation.cloud.cloudiot.CloudIotOptions;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.PubsubScopes;
import com.google.api.services.pubsub.model.PublishRequest;
import com.google.api.services.pubsub.model.PubsubMessage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public class PubsubPublisher implements CloudPublisher {
    private static final String TAG = PubsubPublisher.class.getSimpleName();

    private static final String TOPIC = "projects/" + BuildConfig.PROJECT_ID +
            "/topics/" + BuildConfig.PUBSUB_TOPIC;

    private Pubsub mPubsub;
    private HttpTransport mHttpTransport;
    private ConnectivityManager mConnectivityManager;

    public PubsubPublisher(Context context) throws IOException {
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        mHttpTransport = AndroidHttp.newCompatibleTransport();

        GoogleCredential credential = readCredentials(context);
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        mPubsub = new Pubsub.Builder(mHttpTransport, jsonFactory, credential)
                .setApplicationName(context.getApplicationInfo().packageName)
                .build();
    }

    @Override
    public void reconfigure(CloudIotOptions newOptions) {
        // TODO: support CloudIotOptions and reconfigure
    }

    private GoogleCredential readCredentials(Context context) throws IOException {
        int credentialsResource = context.getResources().getIdentifier(
                "credentials", "raw", context.getPackageName());
        try (InputStream jsonCredentials =
                     context.getResources().openRawResource(credentialsResource)) {
            return GoogleCredential.fromStream(jsonCredentials).createScoped(
                    Collections.singleton(PubsubScopes.PUBSUB));
        }
    }

    @Override
    public boolean isReady() {
        if (mPubsub == null) {
            return false;
        }
        NetworkInfo activeNetwork = mConnectivityManager.getActiveNetworkInfo();
        if (activeNetwork == null || !activeNetwork.isConnected()) {
            Log.e(TAG, "no connected network");
            return false;
        }
        return true;
    }

    @Override
    public void publish(List<SensorData> data) {
        try {
            PubsubMessage m = new PubsubMessage();
            m.setData(Base64.encodeToString(MessagePayload.createMessagePayload(data).getBytes(),
                Base64.DEFAULT));
            PublishRequest request = new PublishRequest();
            request.setMessages(Collections.singletonList(m));
            mPubsub.projects().topics().publish(TOPIC, request).execute();
        } catch (IOException e) {
            Log.e(TAG, "Error publishing message", e);
        }
    }

    public void close() {
        try {
            mHttpTransport.shutdown();
        } catch (IOException e) {
            Log.d(TAG, "error destroying http transport");
        } finally {
            mConnectivityManager = null;
            mHttpTransport = null;
            mPubsub = null;
        }
    }

}