package com.google.android.things.contrib.driver.tsl2561;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;

/**
 * Driver for the TSL2561 luminosity sensor.
 *
 * Constants and most of the logic copied from
 * https://github.com/intel-iot-devkit/upm/blob/master/src/tsl2561/
 */
public class TSL2561 implements AutoCloseable {
    private static final int TSL2561_Address = 0x39;  //Device address

    // Integration time
    public static final byte INTEGRATION_TIME0_13MS = 0x00;  // 13.7ms
    public static final byte INTEGRATION_TIME1_101MS = 0x01;  // 101ms
    public static final byte INTEGRATION_TIME2_402MS = 0x02;  // 402ms

    // Integration time
    public static final byte GAIN_0X = 0x00;                // No gain - Low
    public static final byte GAIN_16X = 0x10;                // 16x gain - High

    // Power control bits
    private static final byte CONTROL_POWERON = 0x03;        // ON
    private static final byte CONTROL_POWEROFF = 0x00;        // OFF

    // TSL2561 registers
    private static final int REGISTER_Control = 0x80;
    private static final int REGISTER_Timing = 0x81;
    private static final int REGISTER_Interrupt = 0x86;
    private static final int REGISTER_Channels = 0x8C;

    private static final int LUX_SCALE = 14;      // Scale by 2^14
    private static final int LUX_RATIOSCALE = 9;      // Scale ratio by 2^9
    private static final int LUX_CHSCALE = 10;      // Scale channel values by 2^10
    private static final int LUX_CHSCALE_TINT0 = 0x7517;  // 322/11 * 2^TSL2561_LUX_CHSCALE
    private static final int LUX_CHSCALE_TINT1 = 0x0FE7;  // 322/81 * 2^TSL2561_LUX_CHSCALE

    // CS package Coefficients
    private static final int LUX_K1C = 0x0043;  // 0.130 * 2^RATIO_SCALE
    private static final int LUX_B1C = 0x0204;  // 0.0315 * 2^LUX_SCALE
    private static final int LUX_M1C = 0x01ad;  // 0.0262 * 2^LUX_SCALE
    private static final int LUX_K2C = 0x0085;  // 0.260 * 2^RATIO_SCALE
    private static final int LUX_B2C = 0x0228;  // 0.0337 * 2^LUX_SCALE
    private static final int LUX_M2C = 0x02c1;  // 0.0430 * 2^LUX_SCALE
    private static final int LUX_K3C = 0x00c8;  // 0.390 * 2^RATIO_SCALE
    private static final int LUX_B3C = 0x0253;  // 0.0363 * 2^LUX_SCALE
    private static final int LUX_M3C = 0x0363;  // 0.0529 * 2^LUX_SCALE
    private static final int LUX_K4C = 0x010a;  // 0.520 * 2^RATIO_SCALE
    private static final int LUX_B4C = 0x0282;  // 0.0392 * 2^LUX_SCALE
    private static final int LUX_M4C = 0x03df;  // 0.0605 * 2^LUX_SCALE
    private static final int LUX_K5C = 0x014d;  // 0.65 * 2^RATIO_SCALE
    private static final int LUX_B5C = 0x0177;  // 0.0229 * 2^LUX_SCALE
    private static final int LUX_M5C = 0x01dd;  // 0.0291 * 2^LUX_SCALE
    private static final int LUX_K6C = 0x019a;  // 0.80 * 2^RATIO_SCALE
    private static final int LUX_B6C = 0x0101;  // 0.0157 * 2^LUX_SCALE
    private static final int LUX_M6C = 0x0127;  // 0.0180 * 2^LUX_SCALE
    private static final int LUX_K7C = 0x029a;  // 1.3 * 2^RATIO_SCALE
    private static final int LUX_B7C = 0x0037;  // 0.00338 * 2^LUX_SCALE
    private static final int LUX_M7C = 0x002b;  // 0.00260 * 2^LUX_SCALE
    private static final int LUX_K8C = 0x029a;  // 1.3 * 2^RATIO_SCALE
    private static final int LUX_B8C = 0x0000;  // 0.000 * 2^LUX_SCALE
    private static final int LUX_M8C = 0x0000;  // 0.000 * 2^LUX_SCALE

    private I2cDevice mDevice;
    private byte gain;
    private byte integrationTime;


    /**
     * Create a new TSL2561 sensor driver connected on the given bus and address.
     *
     * @param bus             I2C bus the sensor is connected to.
     * @param address         I2C address the sensor is connected to.
     * @param gain
     * @param integrationTime
     * @throws IOException
     */
    public TSL2561(String bus, int address, byte gain, byte integrationTime) throws IOException {
        this.gain = gain;
        this.integrationTime = integrationTime;
        PeripheralManagerService pioService = new PeripheralManagerService();
        I2cDevice device = pioService.openI2cDevice(bus, address);
        try {
            connect(device);
        } catch (IOException|RuntimeException e) {
            try {
                close();
            } catch (IOException|RuntimeException ignored) {
            }
            throw e;
        }
    }

    public TSL2561(String i2c) throws IOException {
        this(i2c, TSL2561_Address, GAIN_0X, INTEGRATION_TIME1_101MS);
    }

    private void connect(I2cDevice device) throws IOException {
        mDevice = device;

        mDevice.writeRegByte(REGISTER_Control, CONTROL_POWERON);
        mDevice.writeRegByte(REGISTER_Timing, (byte) (gain | integrationTime));
        mDevice.writeRegByte(REGISTER_Interrupt, (byte) 0);
    }

    /**
     * Gets the calculated lux reading
     *
     * @return Calculated lux value
     */
    public long getLux() throws IOException {
        long lux;
        int rawLuxCh0, rawLuxCh1;
        byte[] channels = new byte[4];

        mDevice.readRegBuffer(REGISTER_Channels, channels, 4);
        rawLuxCh0 = (channels[1] & 0xff) << 8 | (channels[0] & 0xff);
        rawLuxCh1 = (channels[3] & 0xff) << 8 | (channels[2] & 0xff);

        int scale;

        switch (integrationTime) {
            case INTEGRATION_TIME0_13MS: // 13.7 msec
                scale = LUX_CHSCALE_TINT0;
                break;
            case INTEGRATION_TIME1_101MS: // 101 msec
                scale = LUX_CHSCALE_TINT1;
                break;
            default: // assume no scaling
                scale = (1 << LUX_CHSCALE);
                break;
        }

        // scale if gain is NOT 16X
        if (gain == GAIN_0X) scale = scale << 4;

        // scale the channel values
        long channel0 = (rawLuxCh0 * scale) >> LUX_CHSCALE;
        long channel1 = (rawLuxCh1 * scale) >> LUX_CHSCALE;

        // find the ratio of the channel values (Channel1/Channel0)
        // protect against divide by zero
        long ratio1 = 0;
        if (channel0 != 0) ratio1 = (channel1 << (LUX_RATIOSCALE + 1)) / channel0;

        // round the ratio value
        long ratio = (ratio1 + 1) >> 1;

        int b = 0, m = 0;

        // Check if ratio <= eachBreak ?
        if ((ratio >= 0) && (ratio <= LUX_K1C)) {
            b = LUX_B1C;
            m = LUX_M1C;
        } else if (ratio <= LUX_K2C) {
            b = LUX_B2C;
            m = LUX_M2C;
        } else if (ratio <= LUX_K3C) {
            b = LUX_B3C;
            m = LUX_M3C;
        } else if (ratio <= LUX_K4C) {
            b = LUX_B4C;
            m = LUX_M4C;
        } else if (ratio <= LUX_K5C) {
            b = LUX_B5C;
            m = LUX_M5C;
        } else if (ratio <= LUX_K6C) {
            b = LUX_B6C;
            m = LUX_M6C;
        } else if (ratio <= LUX_K7C) {
            b = LUX_B7C;
            m = LUX_M7C;
        } else if (ratio > LUX_K8C) {
            b = LUX_B8C;
            m = LUX_M8C;
        }

        long tempLux = 0;
        tempLux = ((channel0 * b) - (channel1 * m));
        // do not allow negative lux value
        if (tempLux < 0) tempLux = 0;

        // round lsb (2^(LUX_SCALE-1))
        tempLux += (1 << (LUX_SCALE - 1));

        // strip off fractional portion
        lux = tempLux >> LUX_SCALE;

        return lux;
    }

    /**
     * Close the driver and the underlying mDevice.
     */
    @Override
    public void close() throws IOException {
        if (mDevice != null) {
            try {
                mDevice.close();
            } finally {
                mDevice = null;
            }
        }
    }

}
