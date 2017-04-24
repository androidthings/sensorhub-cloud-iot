Android Things Weather Station sample
=====================================

This demo shows a robust Android Things way to collect sensor data and publish
on a Google Cloud IoT PubSub topic.

- connection parameters are configurable via intent and configuration is saved in sharedpreferences
- sensor robustness: you can remove and add sensors at runtime and the app will adapt accordingly
- network robustness: device can loose connectivity. When connectivity is restored, it will auto-reconnect
- power robustness: device can loose power. When is reboots, it will auto-reconnect
- sensor data collected since the last publish is sent to pubsub every 20 seconds
- sensor data is collected either as continuous mode or onchange mode. Continuous mode sensors (temperature, pressure, humidity and luminosity) publishes only the most recent value. Onchange mode sensors (motion detection) stores up to 10 sensor changes in between pubsub publications.

Pre-requisites
--------------
- Android Things compatible board
- Android Studio 2.2+
- 1 [bme280 temperature, pressure and humidity sensor](https://www.adafruit.com/product/2651)
- 1 [PIR motion detector sensor](https://www.adafruit.com/product/189)
- 1 [Luminosity sensor](https://www.adafruit.com/product/439)
- [Google Cloud Platform](https://cloud.google.com/) project with Cloud IoT support

Schematics
----------

![Schematics for Raspberry Pi 3](rpi3_schematics.png)


Prepare the device
==================

- Create a key pair for the device. Instructions in [docs](https://cloud.google.com/iot/docs/device_manager_guide)

- Register the device:
```
gcloud alpha iot devices create <DEVICE_ID> --project=<PROJECT_ID> --region=us-central1 --registry=<REGISTRY_ID> --public-key path=<PRIVATE_KEY_FILE>,type=rs256
```
- Push the private key file to the device:
```
adb shell mkdir -p /sdcard/CloudIot; adb push <PRIVATE_KEY_FILE> /sdcard/CloudIot/private_key.pkcs8
```

Where:
  `DEVICE_ID`: your device ID (it can be anything that identifies the device for you)
  `REGISTRY_ID`: the registry name where this device should be registered
  `PUBLIC_KEY_FILE`: the `*.pem` file from the key pair
  `PRIVATE_KEY_FILE`: the `*.pkcs8` file from the key pair


Build and install
=================

On Android Studio, click on the "Run" button.
If you prefer to run on the command line, type
```bash
./gradlew installDebug
adb shell am start com.example.androidthings.weatherstation/.WeatherStationActivity
```

Configure the service:
```bash
adb shell am startservice -a com.example.androidthings.weatherstation.mqtt.CONFIGURE -e project_id <PROJECT_ID> -e registry_id <REGISTRY_ID> -e device_id <DEVICE_ID> com.example.androidthings.weatherstation/.cloud.CloudPublisherService
```


License
-------
Copyright 2016 The Android Open Source Project, Inc.
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
