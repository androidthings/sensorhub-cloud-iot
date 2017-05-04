package com.google.android.things.contrib.driver.bmx280;

import com.google.android.things.pio.I2cDevice;

import java.io.IOException;

/**
 * Copied from
 * https://github.com/acristescu/pi-temperature/blob/article1/app/src/main/java/com/google/android/things/contrib/driver/bmx280/Bme280.java
 *
 * TODO(mangini): Change Bmx280 to accept different I2C_ADDRESS and integrate humidity calculation
 */

public class Bme280 extends Bmx280 {
	private static final String TAG = "Bme280";

	private static final int BME280_REG_HUM_CALIB_1 = 0xA1;
	private static final int BME280_REG_HUM_CALIB_2 = 0xE1;
	private static final int BME280_REG_HUM_CALIB_3 = 0xE3;
	private static final int BME280_REG_HUM_CALIB_4 = 0xE4;
	private static final int BME280_REG_HUM_CALIB_5 = 0xE5;
	private static final int BME280_REG_HUM_CALIB_6 = 0xE6;
	private static final int BME280_REG_HUM_CALIB_7 = 0xE7;

	private static final int BME280_REG_CTRL_HUM = 0xF2;
	private static final int BME280_REG_HUM = 0xFD;

	private final int[] mHumCalibrationData = new int[6];
	private I2cDevice mDevice;
	private final byte[] mBuffer = new byte[2]; // for reading sensor values

	private int mHumidityOversampling;

	public Bme280(I2cDevice device) throws IOException {
		super(device);
		mDevice = device;
		readHumidityCalibrationData();
	}

	private void readHumidityCalibrationData() throws IOException {
		mHumCalibrationData[0] = mDevice.readRegByte(BME280_REG_HUM_CALIB_1) & 0xFF; // dig_H1
		mHumCalibrationData[1] = mDevice.readRegWord(BME280_REG_HUM_CALIB_2); // dig_H2
		mHumCalibrationData[2] = mDevice.readRegByte(BME280_REG_HUM_CALIB_3) & 0xFF; // dig_H3

		int E4 = mDevice.readRegByte(BME280_REG_HUM_CALIB_4) & 0xFF;
		int E5 = mDevice.readRegByte(BME280_REG_HUM_CALIB_5) & 0xFF;
		int E6 = mDevice.readRegByte(BME280_REG_HUM_CALIB_6) & 0xFF;
		int E7 = mDevice.readRegByte(BME280_REG_HUM_CALIB_7);

		mHumCalibrationData[3] = (E4 << 4) | (E5 & 0x0F); // dig_H4
		mHumCalibrationData[4] = (E6 << 4) | (E5 >> 4); // dig_H5
		mHumCalibrationData[5] = E7; // dig_H6
	}

	/**
	 * Read the current temperature.
	 *
	 * @return the current temperature in degrees Celsius
	 */
	public float readHumidity() throws IOException, IllegalStateException {
		if (mHumidityOversampling == OVERSAMPLING_SKIPPED) {
			throw new IllegalStateException("temperature oversampling is skipped");
		}
		int rawHum = readSample(BME280_REG_HUM);
		return compensateHumidity(rawHum, readTemperature());
	}

	/**
	 * Set oversampling multiplier for the temperature measurement.
	 * @param oversampling temperature oversampling multiplier.
	 * @throws IOException
	 */
	public void setHumidityOversampling(@Oversampling int oversampling) throws IOException {
		mDevice.writeRegByte(BME280_REG_CTRL_HUM, (byte)(oversampling));
		mHumidityOversampling = oversampling;
	}

	/**
	 * Reads 16 bits from the given address.
	 * @throws IOException
	 */
	private int readSample(int address) throws IOException, IllegalStateException {
		if (mDevice == null) {
			throw new IllegalStateException("I2C device is already closed");
		}
		synchronized (mBuffer) {
			mDevice.readRegBuffer(address, mBuffer, 2);
			// msb[7:0] lsb[7:0]
			int msb = mBuffer[0] & 0xff;
			int lsb = mBuffer[1] & 0xff;
			return msb << 8 | lsb;
		}
	}

	// Compensation formula from the BME280 datasheet.
	// https://cdn-shop.adafruit.com/datasheets/BST-BME280_DS001-10.pdf
	private float compensateHumidity(int adc_H, float temp)
	{
		int dig_H1 = mHumCalibrationData[0];
		int dig_H2 = mHumCalibrationData[1];
		int dig_H3 = mHumCalibrationData[2];
		int dig_H4 = mHumCalibrationData[3];
		int dig_H5 = mHumCalibrationData[4];
		int dig_H6 = mHumCalibrationData[5];

		float var_H;
		var_H = (temp - 76800f);
		var_H = (adc_H - (((float) dig_H4) * 64f + ((float) dig_H5) / 16384f * var_H)) *
				(((float) dig_H2) / 65536f * (1f + ((float) dig_H6) / 67108864f * var_H *
						(1f + ((float) dig_H3) / 67108864f * var_H)));
		var_H = var_H * (1f - ((float) dig_H1) * var_H / 524288f);
		if (var_H > 100)
			var_H = 100f;
		else if (var_H < 0)
			var_H = 0f;

		return var_H;
	}
}