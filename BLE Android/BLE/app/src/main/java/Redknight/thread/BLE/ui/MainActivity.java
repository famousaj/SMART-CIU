package Redknight.thread.BLE.ui;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import com.google.android.material.switchmaterial.SwitchMaterial;

import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.activity.EdgeToEdge;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import Redknight.thread.BLE.adapters.TokenListAdapter;
import Redknight.thread.BLE.ble.BleConstants;
import Redknight.thread.BLE.ble.BleState;
import Redknight.thread.BLE.services.AlertSnoozeManager;
import Redknight.thread.BLE.services.BleBackgroundService;
import Redknight.thread.BLE.R;
import Redknight.thread.BLE.tokens.Tokens;

@SuppressLint("MissingPermission")
public class MainActivity extends AppCompatActivity {


    private final String TAG = "BLE_MAIN";

    private BleBackgroundService bleService;

    private SwitchMaterial switchConnection, switchSnooze;
    private EditText txtMessage;

    private int newRssi;

    private boolean serviceBound = false;
    private TextView txtLogs;

    private TextView bar1, bar2, bar3, bar4;
    private Drawable bgLow, bgHigh;

    private Button  btnBuyTokens, btnSend, btnSetLimit;
    RecyclerView tokenrecyclerView;
    LinearLayout logContainer;
    private Button btnShortcode1, btnShortcode2, btnShortcode3, btnShortcode4, btnShortcode5, btnShortcode6;


    private ActivityResultLauncher<Intent> bluetoothEnableLauncher;
    private ActivityResultLauncher<String[]> foregroundPermissionLauncher;
    private ActivityResultLauncher<String> backgroundPermissionLauncher;

    private List<Redknight.thread.BLE.items.Tokens> rcTokenList;
    TokenListAdapter tokenListAdapter;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

     // Link all UI layouts first

        switchConnection = findViewById(R.id.switchConnection);
        btnBuyTokens = findViewById(R.id.btnBuyTokens);
        txtMessage = findViewById(R.id.txtMessage);

        bar1 = findViewById(R.id.bar1);
        bar2 = findViewById(R.id.bar2);
        bar3 = findViewById(R.id.bar3);
        bar4 = findViewById(R.id.bar4);

        switchSnooze = findViewById(R.id.switch_snooze_low_token);
        btnSend = findViewById(R.id.btnWriteData);
        btnSetLimit = findViewById(R.id.btnSetThreshold);
        btnShortcode1 = findViewById(R.id.btnShortcode1);
        btnShortcode2 = findViewById(R.id.btnShortcode2);
        btnShortcode3 = findViewById(R.id.btnShortcode3);
        btnShortcode4 = findViewById(R.id.btnShortcode4);
        btnShortcode5 = findViewById(R.id.btnShortcode5);
        btnShortcode6 = findViewById(R.id.btnShortcode6);
        tokenrecyclerView = findViewById(R.id.token_recycler_view);
        logContainer = findViewById(R.id.logContainer);

        bgLow = ContextCompat.getDrawable(this, R.drawable.rectangle);
        bgHigh = ContextCompat.getDrawable(this, R.drawable.bghigh);

        tokenrecyclerView.setLayoutManager(new LinearLayoutManager(this));
        rcTokenList = new ArrayList<>();

       tokenListAdapter = new TokenListAdapter(rcTokenList, item -> {
            // This runs when a row's "SEND" button is pressed
            transmitTokenOverBle(item.getOriginalToken());
        });
        tokenrecyclerView.setAdapter(tokenListAdapter);

        // Initialize our result callbacks
        initActivityLaunchers();

        // Safe runtime check after bindings are finalized
        if (checkForegroundPermissions()) {
            checkAndRequestBackgroundLocation();
        } else {
            requestForegroundPermissions();
        }


        AlertSnoozeManager snoozeManager = new AlertSnoozeManager(this);


        switchSnooze.setChecked(snoozeManager.isAlertMuted());




        switchConnection.setOnCheckedChangeListener(null);


        boolean isConnected = serviceBound && bleService != null
                && bleService.bleManager.getState() == BleState.CONNECTED;
        if(!switchConnection.isChecked() ){

        switchConnection.setChecked(isConnected);
        }

        switchConnection.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {

                if (serviceBound && bleService != null) {
                    BleState currentState = bleService.bleManager.getState();
                    if (currentState != BleState.CONNECTED && currentState != BleState.CONNECTING) {
                        bleService.startScanning();
                    }
                }
            } else {

                if (serviceBound && bleService != null) {
                    bleService.disconnect();
                }
            }
        });

        switchSnooze.setOnCheckedChangeListener((buttonView, isChecked) -> {

            if (isChecked) {
                snoozeManager.snoozeNotifications();
                Log.d(TAG, "Snoozed for 24 hours.");
                Toast.makeText(this, "Snoozed for 24 hours.", Toast.LENGTH_SHORT).show();
            }else{
                snoozeManager.clearSnooze();
                Log.d(TAG, "onCreate: Snooze turned off ");
                Toast.makeText(this, "Snooze turned off.", Toast.LENGTH_SHORT).show();
            }

                });

        btnSend.setOnClickListener(v -> {
            String msg = txtMessage.getText().toString();
            if (!msg.isEmpty() && serviceBound && bleService != null) {
                bleService.writeData(msg);
                txtMessage.setText("");
                log("Sent: " + msg);
            }
        });

        btnShortcode1.setOnClickListener(v -> shortCodeHandler("000"));
        btnShortcode2.setOnClickListener(v -> shortCodeHandler("001"));
        btnShortcode3.setOnClickListener(v -> shortCodeHandler("002"));
        btnShortcode4.setOnClickListener(v -> shortCodeHandler("004"));
        btnShortcode5.setOnClickListener(v -> shortCodeHandler("005"));
        btnShortcode6.setOnClickListener(v -> shortCodeHandler("049"));



        btnBuyTokens.setOnClickListener(v -> showCustomPopup());
        btnSetLimit.setOnClickListener(v -> showThresholdPopUp());
    }

    private void initActivityLaunchers() {
        // Handles what happens immediately after the user accepts/denies turning on Bluetooth
        bluetoothEnableLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        log("Bluetooth turned on by user.");
                        startBleServiceFlow();
                    } else {
                        log("Bluetooth enablement denied. Cannot scan.");
                    }
                }
        );

        // Handles standard foreground permissions completion
        foregroundPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) { allGranted = false; break; }
                    }

                    if (allGranted) {
                        log("Foreground permissions approved.");
                        checkAndRequestBackgroundLocation();
                    } else {
                        log("Error: Basic permissions denied.");
                    }
                }
        );

        // Handles the specific "Always Allow" background prompt completion
        backgroundPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        log("Background location ('Always allow') granted.");
                    } else {
                        log("Warning: Background location denied. App will only scan in foreground.");
                    }
                    // Proceed with BLE initialization regardless of background status success
                    initBluetoothEnvironment();
                }
        );
    }

    private void initBluetoothEnvironment() {
        if (isBluetoothEnabled()) {
            startBleServiceFlow();
        } else {
            log("Bluetooth is turned off! Prompting setup...");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            bluetoothEnableLauncher.launch(enableBtIntent); // Triggers the launcher callback on complete
        }
    }

    private void startBleServiceFlow() {
        Intent intent = new Intent(this, BleBackgroundService.class);
        ContextCompat.startForegroundService(this, intent);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }, 300);
    }

    private boolean checkForegroundPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean hasScan = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
            boolean hasConnect = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                boolean hasNotifications = ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
                return hasScan && hasConnect && hasNotifications;
            }
            return hasScan && hasConnect;
        } else {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestForegroundPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            foregroundPermissionLauncher.launch(new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.ACCESS_FINE_LOCATION
            });
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            foregroundPermissionLauncher.launch(new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            });
        } else {
            foregroundPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            });
        }
    }

    private void checkAndRequestBackgroundLocation() {
        // Background location requirements only exist on Android 10 (API 29) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            boolean hasBackground = ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;

            if (!hasBackground) {
                // System requirement: Explain why you need "Always allow" before pushing them into the OS settings prompt
                new AlertDialog.Builder(this)
                        .setTitle("Background Location Access Required")
                        .setMessage("This app scans for BLE tags while running in the background. Please select 'Allow all the time' on the next screen.")
                        .setPositiveButton("Grant Permission", (dialog, which) -> {
                            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            log("Background scan permission skipped.");
                            initBluetoothEnvironment();
                        })
                        .show();
            } else {
                initBluetoothEnvironment();
            }
        } else {
            initBluetoothEnvironment();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            if (bleService != null) {
                bleService.setListener(null);
            }
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }


    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BleBackgroundService.LocalBinder binder = (BleBackgroundService.LocalBinder) service;
            bleService = binder.getService();
            serviceBound = true;
            Log.d("BLE_MAIN", "Service Bound.");
            bleService.setListener(new BleBackgroundService.ServiceListener() {
                @Override
                public void onRSSI(int rssi) {
                    if(rssi != newRssi) {
                        newRssi = rssi;
                        runOnUiThread(() -> {
                            if (newRssi <= -90) {
                                bar1.setBackground(bgHigh);
                                bar2.setBackground(bgLow);
                                bar3.setBackground(bgLow);
                                bar4.setBackground(bgLow);
                            } else if (newRssi <= -60) {
                                bar1.setBackground(bgHigh);
                                bar2.setBackground(bgHigh);
                                bar3.setBackground(bgLow);
                                bar4.setBackground(bgLow);
                            } else if (newRssi <= -50) {
                                bar1.setBackground(bgHigh);
                                bar2.setBackground(bgHigh);
                                bar3.setBackground(bgHigh);
                                bar4.setBackground(bgLow);
                            } else {
                                bar1.setBackground(bgHigh);
                                bar2.setBackground(bgHigh);
                                bar3.setBackground(bgHigh);
                                bar4.setBackground(bgHigh);
                            }
                        });

                    }
                }

                @Override
                public void onNotification(String msg) {
                    log(msg);

                    if (msg.contains("Token Value:") && msg.contains("|")) {

                        // Clean up extraction fragments using index splits
                        String[] messageParts = msg.split("\\|");
                        if (messageParts.length >= 2) {
                            // The second slot [1] holds our exact verified 20-digit confirmation key string
                            String confirmedTokenKey = messageParts[1].trim();

                            runOnUiThread(() -> {
                                // Traverse memory list arrays to find match targets
                                for (int i = 0; i < rcTokenList.size(); i++) {
                                    Redknight.thread.BLE.items.Tokens currentCard = rcTokenList.get(i);

                                    if (currentCard.getOriginalToken().equals(confirmedTokenKey) && !currentCard.isUsed()) {
                                        // Target match found. Mutate dataset properties instantly
                                        currentCard.markAsUsedAndScramble();
                                        tokenListAdapter.notifyItemChanged(i);

                                        Log.d("TOKEN_SYSTEM", "Success match. Scrambled index: " + i);
                                        break;
                                    }
                                }
                            });
                        }
                    }

                    else if(msg.equals("App Verified")){

                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "App Verified", Toast.LENGTH_SHORT).show();
                        });

                   } else if (msg.equals("App Not Verified")) {

                       runOnUiThread(() -> {
                           if(switchConnection.isChecked()){
                           // switchConnection.setChecked(false);
                           Toast.makeText(MainActivity.this, "App Not Verified", Toast.LENGTH_SHORT).show();}

                        });

                    }

                }

                @Override
                public void onStateChanged(BleState state) {
                    runOnUiThread(() -> {
                        // Temporarily detach listener to prevent infinite callback loops
                        switchConnection.setOnCheckedChangeListener(null);

                        // Keep switch ON if state is CONNECTING, CONNECTED, or SCANNING
                        boolean isActiveOrConnecting = (state == BleState.CONNECTED || state == BleState.CONNECTING);
                        switchConnection.setChecked(isActiveOrConnecting);

                        // ONLY clear/reset signal bars when link is fully DISCONNECTED or IDLE
                        if (state == BleState.DISCONNECTED || state == BleState.IDLE) {
                            bar1.setBackground(bgLow);
                            bar2.setBackground(bgLow);
                            bar3.setBackground(bgLow);
                            bar4.setBackground(bgLow);
                        }

                        // Re-attach switch listener
                        switchConnection.setOnCheckedChangeListener((buttonView, isChecked) -> {
                            if (isChecked) {
                                if (serviceBound && bleService != null) {
                                    BleState currentState = bleService.bleManager.getState();
                                    if (currentState != BleState.CONNECTED && currentState != BleState.CONNECTING) {
                                        bleService.startScanning();
                                    }
                                }
                            } else {
                                if (serviceBound && bleService != null) {
                                    bleService.disconnect();
                                }
                            }
                        });
                    });
                }


                @Override
                public void onRead(String message) {
                    log(message);
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            bleService = null;
            Log.d("BLE_MAIN", "Service Disconnected");

        }


    };

    private void showCustomPopup() {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.popup_layout, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        final AlertDialog dialog = builder.create();
        dialog.show();

        Button btnClosePopup = dialogView.findViewById(R.id.btnClosePopup);
        TextView txtToken = dialogView.findViewById(R.id.txtToken);
        EditText txtAmount = dialogView.findViewById(R.id.txtAmount);
        Button btnbuyTokensInner = dialogView.findViewById(R.id.btnbuyTokens);

        btnbuyTokensInner.setOnClickListener(v -> {
            try {

                String amount = txtAmount.getText().toString();
                String token = Tokens.awardToken(Integer.parseInt(amount));
                if (token != null && serviceBound && bleService != null && bleService.bleManager.getState() == BleState.CONNECTED) {
                    bleService.writeData(token);
                }
                txtToken.setText(token);

                String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                String formattedDisplay = token.substring(0, 5) + " - " +
                       token.substring(5, 10) + " - " +
                        token.substring(10, 15) + " - " +
                        token.substring(15, 20);

                Redknight.thread.BLE.items.Tokens newCard = new Redknight.thread.BLE.items.Tokens(formattedDisplay, token, BleConstants.METER_NUMBER, false);
                rcTokenList.add(newCard);

                // Notify layout manager to draw new row element
                tokenListAdapter.notifyItemInserted(rcTokenList.size() - 1);
                tokenrecyclerView.scrollToPosition(rcTokenList.size() - 1);

                if(switchSnooze.isChecked()){
                    switchSnooze.setChecked(false);
                }

            } catch (Exception e) {
                Log.e("POPUP", "Token generation failed", e);
            }
        });

        btnClosePopup.setOnClickListener(v -> dialog.dismiss());
    }

    private void showThresholdPopUp(){

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.threshold_popup, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        final AlertDialog dialog = builder.create();
        dialog.show();

        Button btnClosePopup = dialogView.findViewById(R.id.btnClosePopup);
        EditText txtThreshold = dialogView.findViewById(R.id.txtThreshold);
        Button btnSendThreshold = dialogView.findViewById(R.id.btnSendThreshold);

        btnSendThreshold.setOnClickListener(v -> {
            String threshHold = txtThreshold.getText().toString();
            if (threshHold != null && serviceBound && bleService != null) {
                bleService.writeData("THRESHOLD: " + threshHold);
                Toast.makeText(this, "Threshold set to " + threshHold, Toast.LENGTH_SHORT).show();
            }
        });

        btnClosePopup.setOnClickListener(v -> dialog.dismiss());

    }

    private boolean isBluetoothEnabled() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            BluetoothAdapter adapter = bluetoothManager.getAdapter();
            return adapter != null && adapter.isEnabled();
        }
        return false;
    }

    private void shortCodeHandler(String shortCode){
        bleService.writeData(shortCode);
    }

    private void log(String logText) {
        runOnUiThread(() -> {
            // Cap maximum child views in the log container to 40 (20 entries + 20 dividers)
            if (logContainer.getChildCount() > 40) {
                logContainer.removeViews(0, 2); // Remove oldest log and divider
            }

            TextView logView = new TextView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 8, 0, 8);
            logView.setLayoutParams(params);
            logView.setText(logText);
            logView.setTextColor(Color.WHITE);
            logView.setTextSize(15);
            logView.setTypeface(Typeface.MONOSPACE);

            View divider = new View(this);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
            );
            dividerParams.setMargins(0, 0, 0, 8);
            divider.setLayoutParams(dividerParams);
            divider.setBackgroundColor(Color.parseColor("#B2BEB5"));

            logContainer.addView(logView);
            logContainer.addView(divider);
        });
    }

    private void transmitTokenOverBle(String rawTokenStr) {
        // Send your string data safely down to your background BLE Service characteristic write routines
        if (bleService != null && serviceBound) {
            bleService.writeData(rawTokenStr);
            Toast.makeText(this, "Transmitting data string to ESP32...", Toast.LENGTH_SHORT).show();
        }
    }
}