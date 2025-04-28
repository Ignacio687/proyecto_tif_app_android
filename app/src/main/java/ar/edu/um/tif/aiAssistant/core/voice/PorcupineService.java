package ar.edu.um.tif.aiAssistant.core.voice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;

import ai.picovoice.porcupine.*;
import ar.edu.um.tif.aiAssistant.BuildConfig;

public class PorcupineService extends Service {
    private static final String CHANNEL_ID = "PorcupineServiceChannel";
    private PorcupineManager porcupineManager;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(1, getNotification());

        PorcupineManagerCallback callback = keywordIndex -> {
            // Launch AssistantActivity when wake word is detected
            Intent assistantIntent = new Intent(this,
                    ar.edu.um.tif.aiAssistant.component.assistant.AssistantScreenKt.class);
            assistantIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(assistantIntent);
        };

        try {
//            String keywordPath = copyAssetToFile(this, "picovoice/Asistente_es_android_v3_0_0.ppn");
//            String modelPath = copyAssetToFile(this, "picovoice/porcupine_params_es.pv");

            porcupineManager = new PorcupineManager.Builder()
                    .setAccessKey(BuildConfig.PORCUPINE_API_KEY)
                    .setKeywordPath("Asistente_es_android_v3_0_0.ppn")
                    .setModelPath("porcupine_params_es.pv")
                    .setSensitivity(0.7f)
                    .build(getApplicationContext(), callback);
            porcupineManager.start();
//        } catch (IOException e) {
//            onPorcupineInitError("Error copying model files: " + e.getMessage());
        } catch (PorcupineInvalidArgumentException e) {
            onPorcupineInitError(e.getMessage());
        } catch (PorcupineActivationException e) {
            onPorcupineInitError("AccessKey activation error");
        } catch (PorcupineActivationLimitException e) {
            onPorcupineInitError("AccessKey reached its device limit");
        } catch (PorcupineActivationRefusedException e) {
            onPorcupineInitError("AccessKey refused");
        } catch (PorcupineActivationThrottledException e) {
            onPorcupineInitError("AccessKey has been throttled");
        } catch (PorcupineException e) {
            onPorcupineInitError("Failed to initialize Porcupine: " + e.getMessage());
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Porcupine_Wake_Word_Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification getNotification() {
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(), // Empty intent, no UI
                PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Voice Assistant")
                .setContentText("Listening for wake word...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pendingIntent)
                .build();
    }
    private void onPorcupineInitError(String error) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Porcupine Error")
                .setContentText(error)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(error));
        NotificationManager notificationManager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(2, builder.build());
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (porcupineManager != null) {
            try {
                porcupineManager.stop();
                porcupineManager.delete();
            } catch (PorcupineException ignored) {}
        }
        super.onDestroy();
    }
}