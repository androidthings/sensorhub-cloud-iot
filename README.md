# Cloud IoT Core Sensor Hub

This sample shows how to implement a sensor hub on Android Things that collects
sensor data from connected sensors and publish as telemetry events to Google
Cloud IoT Core.

> **Note:** The Android Things Console will be turned down for non-commercial
> use on January 5, 2022. For more details, see the
> [FAQ page](https://developer.android.com/things/faq).

## Introduction

The sample showcases a sensor-based device publishing data to
[Cloud IoT Core](https://cloud.google.com/iot-core/). Sensors can be
added or removed dynamically, and the device will reconnect automatically to the
cloud when power or connectivity is lost.

Sensor data is collected and sent to Cloud IoT Core as telemetry events every 20 seconds by default,
and it can be changed from the cloud with device config (cloud to device) messages.

## Pre-requisites

- Android Things compatible board
- Android Studio 2.2+
- 1 [bmp280 temperature and pressure](https://www.adafruit.com/product/2651)
- 1 [PIR motion detector sensor](https://www.adafruit.com/product/189) or 1 button to simulate the
    motion detection
- [Google Cloud Platform](https://cloud.google.com/) project with Cloud IoT Core enabled

## Schematics

![Schematics for Raspberry Pi 3](rpi3_schematics.png)

## Build and install

On Android Studio, click on the "Run" button.
If you prefer to run on the command line, type
```
./gradlew installDebug
adb shell am start com.example.androidthings.sensorhub/.SensorHubActivity
```

## Prepare the device

This sample will create a key pair (private and public) on the device on the
first run. The private key will be saved to the Android Keystore, using a
secure hardware if one is available. The public key will be printed to logcat
and will be available as a file on your external storage location.

You will need the public key to register your device to Cloud IoT Core.
Here's how you can fetch it:

```
adb pull /sdcard/cloud_iot_auth_certificate.pem
```

or, depending on your platform:

```
adb -d shell "run-as com.example.androidthings.sensorhub cat /data/user/0/com.example.androidthings.sensorhub/files/cloud_iot_auth_certificate.pem" > cloud_iot_auth_certificate.pem
```

A new keypair is only generated again when the device is reflashed.

## Register the device

With the `cloud_iot_auth_certificate.pem` file, you can register your device on
Cloud IoT Core:

```
gcloud iot devices create <DEVICE_ID> --project=<PROJECT_ID> --region=<CLOUD_REGION> --registry=<REGISTRY_ID> --public-key path=cloud_iot_auth_certificate.pem,type=<CERTIFICATE_TYPE>
```

Where:
- `DEVICE_ID`: your device ID (it can be anything that identifies the device for you)
- `PROJECT_ID`: your Cloud IoT Core project id
- `CLOUD_REGION`: the cloud region for project registry
- `REGISTRY_ID`: the registry name where this device should be registered
- `CERTIFICATE_TYPE`: either "rsa-x509-pem" or "es256-x509-pem" depending on
  whether your device key algorithm is "RSA" or "EC" (see below)

## Configure the device

Now that your device's public key is registered, you can set
the device so that it can securely connect to Cloud IoT Core:

```
adb shell am start -e project_id <PROJECT_ID> -e cloud_region <CLOUD_REGION> -e registry_id <REGISTRY_ID> -e device_id <DEVICE_ID> -e key_algorithm <DEVICE_KEY_ALGORITHM> com.example.androidthings.sensorhub/.SensorHubActivity
```
Where PROJECT_ID, CLOUD_REGION, REGISTRY_ID and DEVICE_ID must be the
corresponding values used to register the device on Cloud IoT Core, and
DEVICE_KEY_ALGORITHM must be the standard name of the algorithm to be used for generating
the device authentication key. Currently "RSA" and "EC" are supported, and "RSA"
is the default in case this argument is not defined.


## Testing

If the registration and configuration steps were executed successfully, your
device will immediately start publishing sensor data to Cloud IoT Core.

You can pipe this data into other Google Cloud services.

If you want to quickly check if messages are being published correctly, you
can create a topic subscription (replace SUBSCRIPTION_NAME with any unique name
you want):

```
gcloud pubsub subscriptions create projects/PROJECT_ID/subscriptions/SUBSCRIPTION_NAME --topic=projects/PROJECT_ID/topics/REGISTRY_ID
```

and then pull messages from this subscription:

```
gcloud pubsub subscriptions pull --auto-ack projects/PROJECT_ID/subscriptions/SUBSCRIPTION_NAME
```


## Next steps

Take a look at the [Cloud IoT Core documentation](https://cloud.google.com/iot/docs/) to learn how to pipe the
data published by your devices into other Google Cloud services.

## License

Copyright 2018 The Android Open Source Project, Inc.

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.
