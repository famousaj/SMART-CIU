package Redknight.thread.BLE.ble;

public interface BleEventListener {

    void onConnected();

    void onDisconnected();

    void onScanStarted();

    void onScanStopped();
    void onScanResult(String deviceName, String macAddress);

    void onRead(String message);

    void onNotification(String message);

    void onWrite( String message);

    void onRSSI(int rssi);

    void onLog(String log);

    void onError(String error);

    void onStateChanged(BleState state);

}