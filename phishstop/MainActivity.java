package com.example.phishstop;

import android.Manifest;
import android.provider.Telephony;
import android.os.Build;
import android.content.BroadcastReceiver;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements SMSReceiver.MessageListener {
    private static final String TAG = "MainActivity";
    private SMSReceiver smsReceiver;
    private BroadcastReceiver localReceiver;
    private TextView messageTextView;
    private TextView senderTextView;
    private TextView timeTextView;

    private final String[] requiredPermissions = {
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE
    };

    private final ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            this::handlePermissionResult
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        messageTextView = findViewById(R.id.messageTextView);
        senderTextView = findViewById(R.id.senderTextView);
        timeTextView = findViewById(R.id.timeTextView);

        smsReceiver = new SMSReceiver();
        smsReceiver.setMessageListener(this);

        localReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "LocalReceiver onReceive called");
                String sender = intent.getStringExtra("sender");
                String message = intent.getStringExtra("message");

                if (sender != null && message != null) {
                    Log.d(TAG, "Received message in local receiver - Sender: " + sender +
                            ", Message length: " + message.length());
                    Log.d(TAG, "Message: " + message);
                    handleIncomingMessage(sender, message);
                } else {
                    Log.e(TAG, "Received null sender or message in local receiver");
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.provider.Telephony.SMS_RECEIVED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsReceiver, intentFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(smsReceiver, intentFilter);
        }

        checkAndRequestPermissions();
    }

    private boolean hasRequiredPermissions() {
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
        } else {
            onAllPermissionsGranted();
        }
    }

    private void setupSMSReceiver() {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Cannot setup SMS receiver - missing permissions");
            Toast.makeText(this, "Required permissions not granted", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(TAG, "Starting SMS receiver setup...");

        try {
            IntentFilter filter = new IntentFilter(SMSReceiver.SMS_RECEIVED_ACTION);
            LocalBroadcastManager.getInstance(this)
                    .registerReceiver(localReceiver, filter);
            Log.d(TAG, "Local broadcast receiver registered successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up SMS receiver: " + e.getMessage(), e);
            Toast.makeText(this,
                    "Error setting up SMS receiver: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onMessageReceived(String sender, String message) {
        Log.d(TAG, "Message received in MainActivity from " + sender + ": " + message);
        runOnUiThread(() -> {
            senderTextView.setText("From: " + sender);
            messageTextView.setText(message);

            String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            timeTextView.setText("Received at: " + currentTime);

            Toast.makeText(this, "New message received", Toast.LENGTH_SHORT).show();
        });
    }

    private void handleIncomingMessage(String sender, String message) {
        Log.d(TAG, "Handling incoming message - Sender: " + sender +
                ", Message length: " + message.length());

        runOnUiThread(() -> {
            try {
                senderTextView.setText("From: " + sender);
                messageTextView.setText(message);

                String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        .format(new Date());
                timeTextView.setText("Received at: " + currentTime);

                Toast.makeText(this, "New message received", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Message displayed successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error updating UI with message: " + e.getMessage(), e);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasRequiredPermissions()) {
            setupSMSReceiver();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (localReceiver != null) {
            try {
                LocalBroadcastManager.getInstance(this)
                        .unregisterReceiver(localReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering receiver", e);
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(smsReceiver);

            LocalBroadcastManager.getInstance(this)
                    .unregisterReceiver(localReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receivers", e);
        }
    }

    private void handlePermissionResult(Map<String, Boolean> result) {
        boolean allGranted = true;
        StringBuilder deniedPermissions = new StringBuilder();

        for (Map.Entry<String, Boolean> entry : result.entrySet()) {
            if (!entry.getValue()) {
                allGranted = false;
                deniedPermissions.append("\n").append(getPermissionDisplayName(entry.getKey()));
            }
        }

        if (allGranted) {
            onAllPermissionsGranted();
        } else {
            Toast.makeText(this, "The following permissions are required: " +
                    deniedPermissions.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private void onAllPermissionsGranted() {
        setupSMSReceiver();
        Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show();
    }

    private String getPermissionDisplayName(String permission) {
        switch (permission) {
            case Manifest.permission.READ_CONTACTS:
                return "Read Contacts";
            case Manifest.permission.WRITE_CONTACTS:
                return "Write Contacts";
            case Manifest.permission.READ_SMS:
                return "Read SMS";
            case Manifest.permission.RECEIVE_SMS:
                return "Receive SMS";
            case Manifest.permission.SEND_SMS:
                return "Send SMS";
            case Manifest.permission.CALL_PHONE:
                return "Make Phone Calls";
            default:
                return permission.substring(permission.lastIndexOf(".") + 1);
        }
    }
}