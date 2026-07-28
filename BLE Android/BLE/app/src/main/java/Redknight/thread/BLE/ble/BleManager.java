package Redknight.thread.BLE.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import Redknight.thread.BLE.Cage.redCage;

@SuppressLint("MissingPermission")
public class BleManager {

    //#region Getters
    public BleState getState(){
        return currentState;
    }

    public boolean isConnected(){
        return currentState == BleState.CONNECTED;
    }

    public int getRSSI(){
        return currentRSSI;
    }

    public BluetoothDevice getCurrentDevice(){
        return currentDevice;
    }
    //#endregion

    //#region Globals
    String originalName;
    private BleEventListener listener;
    private BleState currentState = BleState.IDLE;
    private BluetoothGatt currentGatt;
    private BluetoothDevice currentDevice;
    private BluetoothGattCharacteristic writeCharacteristic;
    public BluetoothGattCharacteristic notifyCharacteristic;
    private int currentRSSI = -127;
    private boolean notificationsEnabled = false;
    private String deviceName;

    private int consecutiveFailureCount = 0;

    private final Handler handler;
    private static final String TAG = "BLE_MANAGER";
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothManager bluetoothManager;
    private BluetoothLeScanner leScanner;
    private Context context;

    public boolean isScanning = false;
    private boolean isResettingRadio = false; // State guard to block looping execution

    private final Runnable scanTimeoutRunnable = () -> stopScan(false);
    private final Runnable scanRetryRunnable = () -> {
        if (!isConnected() && !isScanning && !isResettingRadio) {
            Log.d(TAG, "Retry timer fired: restarting scan.");
            startScan();
        }
    };
    private final Runnable delayedRestartRunnable = () -> {
        if (!isConnected() && !isScanning && !isResettingRadio) {
            startScan();
        }
    };
    //#endregion

    //#region Helper Methods
    private void notifyLog(String message){
        Log.d(TAG, message);
        if(listener != null){
            listener.onLog(message);
        }
    }

    private void notifyRSSI(int rssi){
        currentRSSI = rssi;
        if(listener != null){
            listener.onRSSI(rssi);
        }
    }

    private void notifyNotification(String msg){
        if(listener != null){
            listener.onNotification(msg);
        }
    }

    private void notifyWrite(String message){
        Log.d(TAG, message);
        if(listener != null){
            listener.onWrite(message);
        }
    }

    private void notifyRead(String message){
        if(listener != null){
            listener.onRead(message);
        }
    }

    private void onScanResults(String deviceName, String macAddress){
        if(listener != null){
            listener.onScanResult(deviceName, macAddress);
        }
    }
    //#endregion

    public BleManager(Context context) {
        this.context = context.getApplicationContext();
        handler = new Handler(Looper.getMainLooper());
        bluetoothManager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);

        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            originalName = bluetoothAdapter.getName();
        }
        if (bluetoothAdapter != null) {
            leScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        notifyLog("BleManager initialized.");
    }



    private final BroadcastReceiver bondReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
            if (state == BluetoothDevice.BOND_BONDED && currentGatt != null) {
                Log.d(TAG, "Bonding complete, discovering services...");
                currentGatt.discoverServices();
            }
        }
    };


    private void updateState(BleState newState){
        currentState = newState;
        notifyLog("BLE STATE -> " + currentState);
        if(listener != null){
            listener.onStateChanged(newState);
        }
    }

    public boolean isScanning() {
        return isScanning;
    }

    public void setBleEventListener(BleEventListener listener){
        this.listener = listener;
    }

    public void startScan() {
        if (isScanning) return;

        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm == null || bm.getAdapter() == null || !bm.getAdapter().isEnabled()) {
            Log.e(TAG, "Bluetooth hardware adapter is unavailable or disabled.");
            return;
        }

        BluetoothLeScanner scanner = bm.getAdapter().getBluetoothLeScanner();
        if (scanner == null) {
            Log.e(TAG, "Failed to fetch system BluetoothLeScanner reference instance.");
            return;
        }

        // Initialize a new unique memory reference instance for the binder engine
        if (scanCallback == null) {
            scanCallback = new ScanCallback() {
                @Override
                public void onScanFailed(int errorCode) {
                    super.onScanFailed(errorCode);
                    Log.e(TAG, "onScanFailed: Error Code: " + errorCode);

                    updateState(BleState.IDLE);
                    isScanning = false;

                    if (errorCode == ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED) { // Code 2
                        consecutiveFailureCount++;
                        Log.w(TAG, "GATT Scanner registration exhausted. Consecutive counts: " + consecutiveFailureCount);

                        // CIRCUIT BREAKER: Avoid log loops if the system OS layer is completely frozen
                        if (consecutiveFailureCount > 3) {
                            Log.e(TAG, "CRITICAL: OS binder slots completely full. Recovery aborted. Manual system intervention required.");
                            updateState(BleState.DISCONNECTED);
                            return;
                        }

                        // Forcefully evict this exact instance reference from the hardware layer
                        try {
                            Log.d(TAG, "Evicting current callback memory footprint from OS binder table...");
                            scanner.stopScan(this);
                        } catch (Exception e) {
                            Log.e(TAG, "Eviction failure: " + e.getMessage());
                        }

                        // Nullify the reference so the next start iteration compiles a fresh object pointer
                        scanCallback = null;

                        // Clean out app scheduling threads
                        handler.removeCallbacksAndMessages(null);

                        Log.d(TAG, "Cooling down framework. Scheduling dynamic restart in 5 seconds...");
                        handler.postDelayed(() -> {
                            Log.d(TAG, "Executing clean recovery scan attempt with pristine memory state.");
                            startScan();
                        }, 5000);

                    }
                }

                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    consecutiveFailureCount = 0; // Reset circuit breaker on successful contact
                    // Your standard processing logic continues here...
                }
            };
        }

        try {

            List<ScanFilter> scFilter = new ArrayList<>();

            ScanFilter filter = new ScanFilter.Builder()

                    .setDeviceName(BleConstants.METER_NUMBER.trim())

                    .build();

            scFilter.add(filter);



            ScanSettings scSettings = new ScanSettings.Builder()

                    .setScanMode(ScanSettings.SCAN_MODE_BALANCED)

                    .build();
            Log.d(TAG, "BLE STATE -> SCANNING");
            Log.d(TAG, "Scanning For: " + BleConstants.METER_NUMBER);
            scanner.startScan(null, scSettings, scanCallback);
            isScanning = true;
            Log.d(TAG, "Started Scanning successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Fatal internal framework scan exception: " + e.getMessage());
            isScanning = false;
            scanCallback = null;
        }
    }

    public void stopScan(boolean retry) {
        handler.removeCallbacks(scanTimeoutRunnable);

        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothLeScanner scanner = (bm != null && bm.getAdapter() != null) ? bm.getAdapter().getBluetoothLeScanner() : null;

        if (!isScanning) {
            if (scanner != null) {
                try { scanner.stopScan(scanCallback); } catch (Exception ignored) {}
            }
            return;
        }

        if (scanner != null) {
            try {
                scanner.stopScan(scanCallback);
            } catch (Exception e) {
                notifyLog("Error stopping scanner: " + e.getMessage());
            }
        }

        isScanning = false;
        updateState(BleState.IDLE);

        if (retry && !isResettingRadio) {
            notifyLog("Retrying scan in 10 seconds");
            handler.removeCallbacks(scanRetryRunnable);
            handler.postDelayed(scanRetryRunnable, 10000);
        }
    }

    public void restartScanDelayed() {
        handler.removeCallbacks(scanRetryRunnable);
        handler.removeCallbacks(delayedRestartRunnable);
        handler.removeCallbacks(scanTimeoutRunnable);

        notifyLog("Restarting scan in 2 seconds...");
        handler.postDelayed(delayedRestartRunnable, 2000);
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "onScanFailed: Error Code: " + errorCode);

            updateState(BleState.IDLE);
            isScanning = false;

            if (errorCode == 2) { // SCAN_FAILED_APPLICATION_REGISTRATION_FAILED
                Log.w(TAG, "GATT Scanner registration exhausted. Executing software-level client recovery...");

                // 1. Clear out active app-side retry runnables
                handler.removeCallbacks(scanRetryRunnable);
                handler.removeCallbacks(delayedRestartRunnable);
                handler.removeCallbacks(scanTimeoutRunnable);

                // 2. Clear out any leaked or ghost GATT connections hanging in memory
                disconnect();

                // 3. FORCE-UNREGISTER: Clear the slot by explicitly calling stopScan on the system engine
                try {
                    BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
                    if (bm != null && bm.getAdapter() != null && bm.getAdapter().isEnabled()) {
                        BluetoothLeScanner scanner = bm.getAdapter().getBluetoothLeScanner();
                        if (scanner != null) {
                            Log.d(TAG, "Evicting corrupted callback instance registration from OS binder table...");
                            scanner.stopScan(scanCallback);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed casting sweep cleanup to system scanner: " + e.getMessage());
                }

                // 4. Safe recovery back-off sequence without locking the execution thread
                notifyLog("Cooling down BLE interface framework. Retrying link in 3.5 seconds...");
                handler.postDelayed(() -> {
                    Log.d(TAG, "Executing clean recovery scan attempt.");
                    isScanning = false;
                    startScan();
                }, 3500);

            } else {
                // Fallback strategy for standard operational scan errors (e.g., internal busy flags)
                handler.removeCallbacks(scanRetryRunnable);
                handler.postDelayed(scanRetryRunnable, 10000);
            }
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            if (isResettingRadio) return; // Retain safety guard logic

            BluetoothDevice device = result.getDevice();
            ScanRecord record = result.getScanRecord();
            currentRSSI = result.getRssi();

            notifyRSSI(currentRSSI);
            deviceName = (record != null) ? record.getDeviceName() : device.getName();
            String macAddress = device.getAddress();

         if (deviceName != null && isScanning) {
             if(deviceName.trim().equals(BleConstants.METER_NUMBER.trim()))
                if (currentRSSI >= BleConstants.RSSI_LIMIT) {
                    stopScan(false);
                    Log.d(TAG, "onScanResult: TARGET FOUND. Connecting -> " + deviceName + " [" + macAddress + "]");
                    onScanResults(deviceName, macAddress);
                    connectToDevice(device);
                }
            }
        }
    };
    public void connectToDevice(BluetoothDevice device){
        if (isResettingRadio) {
            Log.w(TAG, "Block connectToDevice: Radio is currently resetting.");
            return;
        }
        notifyLog("connectToDevice: Linking to: [" + device.getAddress() + "]");
        currentDevice = device;

        // 2. Set the custom name right before connecting
        boolean success = bluetoothAdapter.setName(BleConstants.METER_NUMBER);

        if (success) {
            notifyLog("Bluetooth adapter name successfully set to: " + BleConstants.METER_NUMBER);
        } else {
            notifyLog("Warning: Failed to set Bluetooth adapter name. (Is permission missing?)");
        }

        // CRITICAL CHANGE: Set autoConnect to false for explicit immediate resource setup
        currentGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        updateState(BleState.CONNECTING);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState ) {
            super.onConnectionStateChange(gatt, status, newState);
            gatt.requestMtu(256);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                notifyLog("onConnectionStateChange physical link error: " + status);
                disconnect();

                if (!isResettingRadio) {
                    notifyLog("Retrying scan in 5 seconds...");
                    handler.postDelayed(() -> startScan(), 5000);
                }
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                notifyLog("onConnectionStateChange: Connected to Server.");
                updateState(BleState.CONNECTED);

                BluetoothDevice device = gatt.getDevice();
                int bondState = device.getBondState();

                if(bondState == BluetoothDevice.BOND_BONDED){
                    gatt.discoverServices();
                    Log.d("BLE", "Device is already bonded. Discovering services...");

                }
                else{
                    Log.d("BLE", "Device is not bonded. Attempting to bond...");
                    device.createBond();
                    IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                    context.registerReceiver(bondReceiver, filter);
                }

            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                notifyLog("Disconnected.");
                disconnect();

                if (!isResettingRadio) {
                    notifyLog("Restarting scan in 5 seconds...");
                    handler.postDelayed(() -> startScan(), 5000);
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                notifyLog("onServicesDiscovered: Mapping Complete.");
                BluetoothGattService currentService = gatt.getService(BleConstants.SERVICE_UUID);

                if (currentService != null) {
                    BluetoothGattCharacteristic gattCT = currentService.getCharacteristic(BleConstants.CHARACTERISTIC_UUID);
                    if (gattCT != null) {
                        writeCharacteristic = gattCT;
                        notifyCharacteristic = gattCT;


                        gatt.setCharacteristicNotification(gattCT, true);
                        BluetoothGattDescriptor descriptor = gattCT.getDescriptor(BleConstants.CCCD_UUID);
                        if (descriptor != null) {
                            byte[] val = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                            descriptor.setValue(val);
                            notificationsEnabled = true;

                            boolean success;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                success = (gatt.writeDescriptor(descriptor, val) == BluetoothStatusCodes.SUCCESS);
                            } else {
                                success = gatt.writeDescriptor(descriptor);
                            }
                            notifyLog("Notification Handshake Initiated! Success status: " + success);
                        }
                    }
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                notifyLog("Notification Handshake Confirmed by ESP32!");
                if(writeCharacteristic!=null){
                    String securityPayload = redCage.generatePayload();

                    if (securityPayload != null) {
                        writeCharacteristic.setValue(securityPayload.getBytes());
                        writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                        gatt.writeCharacteristic(writeCharacteristic);
                        notifyLog("Security handshake transmitted successfully.");
                    }
                }
                handler.postDelayed(() -> gatt.readRemoteRssi(), 600);
            }
        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
            super.onCharacteristicRead(gatt, characteristic, value, status);
            if (status == BluetoothGatt.GATT_SUCCESS && value != null && value.length > 0) {
                String incomingMessage = new String(value, StandardCharsets.UTF_8).trim();
                notifyRead("Read Data: " + incomingMessage);
                Log.d(TAG, "onCharacteristicRead: " + incomingMessage);
                // INTERCEPT ROUTING: If a notification update arrives inside a Read frame, process it correctly
                if (incomingMessage.startsWith("CHECK_TOKEN_RESULT") || incomingMessage.startsWith("Low Tokens")) {
                    processNotification(characteristic.getUuid(), value);
                    Log.d(TAG, "onCharacteristicRead: " + incomingMessage);
                }
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
            super.onCharacteristicChanged(gatt, characteristic, value);
            processNotification(characteristic.getUuid(), value);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                notifyWrite("Write: Successfully Acked by ESP32");
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            if (status == BluetoothGatt.GATT_SUCCESS && currentState == BleState.CONNECTED) {
                currentRSSI = rssi;
                notifyRSSI(rssi);
                handler.postDelayed(() -> {
                    if (currentGatt != null && currentState == BleState.CONNECTED) {
                        currentGatt.readRemoteRssi();
                    }
                }, BleConstants.RSSI_REFRESH);
            }
        }
    };

    public void processNotification(UUID uuid, byte[] data) {
        if (uuid.equals(BleConstants.CHARACTERISTIC_UUID) && data != null) {
            // 1. Log raw length to verify if bytes are being dropped or just hidden
            Log.d(TAG, "Notification packet arrived. Raw byte length: " + data.length);

            // 2. Build string and clean out any null terminators or hidden control characters
            String message = new String(data, StandardCharsets.UTF_8);
           // message = message.replace("\0", "").trim();

            /*3. Alternate clean extraction fallback if standard parsing is truncating
            if (message.equals("CHECK_TOKEN_RESULT:") && data.length > 20) {
                Log.w(TAG, "String truncation detected! Forcing manual byte-to-char extraction...");
                StringBuilder sb = new StringBuilder();
                for (byte b : data) {
                    if (b >= 32 && b <= 126) {
                        sb.append((char) b);
                    }
                }
                message = sb.toString().trim();
            }*/

            notifyNotification(message);
            Log.d(TAG, "processNotification: " + message);
        }
    }

    public void writeData(String rawMessage) {
        if (currentGatt == null || writeCharacteristic == null) return;
        writeCharacteristic.setValue(rawMessage);
        writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        currentGatt.writeCharacteristic(writeCharacteristic);
        notifyLog("Sent Data: " + rawMessage);
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (currentGatt != null && characteristic != null) {
            currentGatt.readCharacteristic(characteristic);
        }
    }

    public void disconnect() {
        if (currentGatt != null) {
            try {
                currentGatt.disconnect();
                currentGatt.close();
            } catch (Exception ignored) {}
            currentGatt = null;
        }

        currentDevice = null;
        writeCharacteristic = null;
        notifyCharacteristic = null;

        handler.removeCallbacks(scanRetryRunnable);
        handler.removeCallbacks(delayedRestartRunnable);
        handler.removeCallbacks(scanTimeoutRunnable);

        updateState(BleState.DISCONNECTED);
        try {
            context.unregisterReceiver(bondReceiver);
        } catch (IllegalArgumentException ignored) {}

        bluetoothAdapter.setName(originalName);

    }
}