package Redknight.thread.BLE.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import Redknight.thread.BLE.ble.BleEventListener;
import Redknight.thread.BLE.ble.BleManager;
import Redknight.thread.BLE.ble.BleState;
import Redknight.thread.BLE.ui.MainActivity;

public class BleBackgroundService extends Service {

    public static final String SERVICE_CHANNEL = "BLE_FOREGROUND";
    public static final String ALERT_CHANNEL   = "BLE_ALERTS";

    private final IBinder binder = new LocalBinder();
    public BleManager bleManager;
    public String Message;
    private String latestNotification;
    private int latestRSSI;
    private BleState currentState;
    private static final String TAG = "BLE_SERVICE";

    // Receiver tracking reference
    private BroadcastReceiver bluetoothStateReceiver;

    private void log(String msg){
        Log.d(TAG, msg);
    }

    public class LocalBinder extends Binder {
        public BleBackgroundService getService(){
            log("Activity Bound to Service");
            return BleBackgroundService.this;
        }
    }

    public interface ServiceListener {
        void onRSSI(int rssi);
        void onNotification(String msg);
        void onStateChanged(BleState state);

        void onRead(String message);
    }

    private ServiceListener listener;

    public void startScanning() {
        if (bleManager != null) {
            BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bm != null && bm.getAdapter() != null && bm.getAdapter().isEnabled()) {
                bleManager.startScan();
            } else {
                log("Cannot manually execute startScan: Bluetooth hardware radio remains disabled.");
            }
        }
    }

    public void disconnect() {
        if (bleManager != null && bleManager.isConnected() && bleManager.getState() == BleState.CONNECTED) {
            bleManager.disconnect();
        }
    }

    public void setListener(ServiceListener listener) {
        this.listener = listener;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                NotificationChannel serviceChannel = new NotificationChannel(
                        SERVICE_CHANNEL, "BLE Background Service", NotificationManager.IMPORTANCE_LOW);
                NotificationChannel alertChannel = new NotificationChannel(
                        ALERT_CHANNEL, "BLE Alerts", NotificationManager.IMPORTANCE_HIGH);
                manager.createNotificationChannel(serviceChannel);
                manager.createNotificationChannel(alertChannel);
            }
        }
    }

    private void showNotification(String title, String message) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, ALERT_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log("onStartCommand() called");
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, SERVICE_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("Spider-Man")
                .setContentText("Monitoring BLE connection...")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        startForeground(1, notification);

        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        boolean isAdapterReady = (bm != null && bm.getAdapter() != null && bm.getAdapter().isEnabled());

        if (bleManager == null) {
            bleManager = new BleManager(getApplicationContext());
            registerListeners();

            if (isAdapterReady) {
                bleManager.startScan();
                log("Scan requested successfully on cold start.");
            } else {
                log("Cold start bypass: Bluetooth radio is OFF. Waiting for user interaction.");
            }
        } else {
            log("Service already alive. Current State: " + bleManager.getState());
            if (isAdapterReady && !bleManager.isScanning() && !bleManager.isConnected()) {
                bleManager.startScan();
                log("Scan requested successfully on warm service update.");
            }
        }

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        log("Service Created");
        if (bleManager == null) {
            bleManager = new BleManager(getApplicationContext());
            registerListeners();
        }
        // Register for system hardware toggles
        registerBluetoothStateReceiver();
    }

    private void registerBluetoothStateReceiver() {
        if (bluetoothStateReceiver == null) {
            bluetoothStateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                        int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                        if (state == BluetoothAdapter.STATE_OFF) {
                            log("System Hook: Bluetooth turned OFF. Stopping scanning operations.");
                            // Optional: clean up old scan states inside your manager if needed
                            if(bleManager != null){
                                bleManager.stopScan(false);
                                bleManager.disconnect();
                            }
                        }
                        else if (state == BluetoothAdapter.STATE_ON) {
                            log("System Hook: Bluetooth turned ON! Reviving the BLE scanning thread.");

                            // Reinitialize manager if it got corrupted by the hard shut down
                            if (bleManager == null) {
                                bleManager = new BleManager(getApplicationContext());
                                registerListeners();
                            }

                            // Allow a small delay for hardware initialization, then fire scan
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                if (!bleManager.isScanning() && !bleManager.isConnected()) {
                                    bleManager.startScan();
                                    log("Scan successfully executed via Hardware Broadcast Event.");
                                }
                            }, 1000);
                        }
                    }
                }
            };
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(bluetoothStateReceiver, filter);
            }
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        log("========== TASK REMOVED ==========");
        Intent restart = new Intent(this, BleBackgroundService.class);
        try {
            startForegroundService(restart);
        } catch (Exception e) {
            log("Failed background auto-revival: " + e.getMessage());
        }
        super.onTaskRemoved(rootIntent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        log("onBind()");
        return binder;
    }

    @Override
    public void onDestroy() {
        log("Service Destroyed");
        if (bluetoothStateReceiver != null) {
            unregisterReceiver(bluetoothStateReceiver);
            bluetoothStateReceiver = null;
        }
        if (bleManager != null) {
            bleManager.disconnect();
        }
        super.onDestroy();
    }

    public void writeData(String Message) {
        if (bleManager != null) bleManager.writeData(Message);
    }

    public String readData() {
        return Message;
    }

    public void readNotification() {
        if (bleManager != null) bleManager.readCharacteristic(bleManager.notifyCharacteristic);
    }

    private void registerListeners() {
        bleManager.setBleEventListener(new BleEventListener() {
            @Override
            public void onStateChanged(BleState state) {
                log("STATE -> " + state);
                currentState = state;
                if (listener != null) listener.onStateChanged(state);
            }

            @Override
            public void onLog(String message) { log("BLE -> " + message); }

            @Override
            public void onError(String error) { log("ERROR -> " + error); }

            @Override
            public void onConnected() { log("CONNECTED"); }

            @Override
            public void onDisconnected() { log("DISCONNECTED"); }

            @Override
            public void onScanStarted() { log("SCAN STARTED"); }

            @Override
            public void onScanStopped() { log("SCAN STOPPED"); }

            @Override
            public void onScanResult(String deviceName, String macAddress) {
                log("SCAN RESULT -> " + deviceName + " [" + macAddress + "]");
            }

            @Override
            public void onRead(String message) {
                log("READ -> " + message);
                Message = message;

                if (listener != null) listener.onRead(message);
            }

            @Override
            public void onWrite(String message) { log("WRITE -> " + message); }

            @Override
            public void onNotification(String message) {
                log("NOTIFICATION -> " + message);
                if (listener != null) listener.onNotification(message);

                if(message.startsWith("TOKENS" )){
                showNotification("ESP32:", message);}

                else if(message.startsWith("Low Tokens")){

                    AlertSnoozeManager snoozeManager = new AlertSnoozeManager(getApplicationContext());
                    if(!snoozeManager.isAlertMuted()){
                        showNotification("ESP32:", message);

                    }
                    else{
                        log("Low Token notification suppressed by user 24-hour snooze window.");
                    }

                }

            }

            @Override
            public void onRSSI(int rssi) {
                log("RSSI -> " + rssi);
                latestRSSI = rssi;
                if (listener != null) listener.onRSSI(rssi);
            }
        });
    }
}