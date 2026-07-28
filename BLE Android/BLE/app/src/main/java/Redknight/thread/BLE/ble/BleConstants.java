package Redknight.thread.BLE.ble;

import java.util.UUID;

public final class BleConstants {

    private BleConstants(){}

    public static final String DEVICE_NAME = "Spider-Man";

    public static final String METER_NUMBER = "48720993827";

    public static final UUID SERVICE_UUID =
            UUID.fromString("b2c5bee9-8200-450b-88cc-5fae669e7176");

    public static final UUID CHARACTERISTIC_UUID =
            UUID.fromString("488c41d1-51f9-4f31-b27e-0030dc0824da");

    public static final UUID CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static final int RSSI_LIMIT = -70;

    public static final int SCAN_TIMEOUT = 5000;

    public static final int RSSI_REFRESH = 3000;



}