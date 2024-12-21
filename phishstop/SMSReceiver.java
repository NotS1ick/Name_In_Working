package com.example.phishstop;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class SMSReceiver extends BroadcastReceiver {
    private static final String TAG = "SMSReceiver";
    private MessageListener messageListener;
    public static final String SMS_RECEIVED_ACTION = "com.example.phishstop.SMS_RECEIVED";

    public interface MessageListener {
        void onMessageReceived(String sender, String message);
    }

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
        Log.d(TAG, "Message listener set: " + (listener != null));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "SMSReceiver onReceive called with action: " + (intent != null ? intent.getAction() : "null"));

        if (intent == null || intent.getAction() == null) {
            Log.e(TAG, "Received null intent or null action");
            return;
        }

        // Check for runtime permissions
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SMS permission not granted");
            return;
        }

        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            Log.d(TAG, "Not an SMS received action, received: " + intent.getAction());
            return;
        }

        try {
            Bundle bundle = intent.getExtras();
            if (bundle == null) {
                Log.e(TAG, "No extras in intent");
                return;
            }

            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus == null || pdus.length == 0) {
                Log.e(TAG, "No PDUs in SMS");
                return;
            }

            String format = bundle.getString("format");
            Log.d(TAG, "Processing " + pdus.length + " PDUs with format: " + format);

            StringBuilder fullMessage = new StringBuilder();
            String sender = null;

            for (Object pdu : pdus) {
                SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
                if (sender == null) {
                    sender = smsMessage.getDisplayOriginatingAddress();
                }
                fullMessage.append(smsMessage.getMessageBody());
            }

            String completeMessage = fullMessage.toString();
            Log.d(TAG, "Successfully parsed SMS - Sender: " + sender + ", Message length: " + completeMessage.length());

            if (messageListener != null) {
                Log.d(TAG, "Notifying message listener");
                messageListener.onMessageReceived(sender, completeMessage);
            } else {
                Log.w(TAG, "Message listener is null, cannot notify directly");
            }

            Intent messageIntent = new Intent(SMS_RECEIVED_ACTION);
            messageIntent.putExtra("sender", sender);
            messageIntent.putExtra("message", completeMessage);
            boolean broadcastSent = LocalBroadcastManager.getInstance(context)
                    .sendBroadcast(messageIntent);
            Log.d(TAG, "Local broadcast sent: " + broadcastSent + " with message length: " +
                    completeMessage.length());

        } catch (Exception e) {
            Log.e(TAG, "Error processing SMS: " + e.getMessage(), e);
        }
    }
}