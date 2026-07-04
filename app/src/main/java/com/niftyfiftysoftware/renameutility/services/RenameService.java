package com.niftyfiftysoftware.renameutility.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.niftyfiftysoftware.renameutility.R;
import com.niftyfiftysoftware.renameutility.interfaces.RenameCallback;
import com.niftyfiftysoftware.renameutility.utils.FileUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RenameService extends Service {

    private static final String CHANNEL_ID = "RenameServiceChannel";
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private ExecutorService executorService;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        notificationManager = getSystemService(NotificationManager.class);
        executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String uriString = intent.getStringExtra("folderUri");
        String oldExt = intent.getStringExtra("oldExt");
        String newExt = intent.getStringExtra("newExt");

        if (uriString == null || oldExt == null || newExt == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        Uri folderUri = Uri.parse(uriString);

        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Renombrando archivos")
                .setContentText("Calculando...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, 0, true);

        startForeground(1, notificationBuilder.build());

        Handler handler = new Handler(Looper.getMainLooper());

        executorService.execute(() -> {
            try {
                int totalRenamed = FileUtils.renameFilesFast(this, folderUri, oldExt, newExt, new RenameCallback() {
                    @Override
                    public void onRenameProgress(int current, int total) {
                        notificationBuilder.setProgress(total, current, false);
                        notificationBuilder.setContentText("Renombrando: " + current + " de " + total);
                        notificationManager.notify(1, notificationBuilder.build());

                        Intent broadcastIntent = new Intent("RENAME_UPDATE");
                        broadcastIntent.setPackage(getPackageName());
                        broadcastIntent.putExtra("status", "progress");
                        broadcastIntent.putExtra("current", current);
                        broadcastIntent.putExtra("total", total);
                        sendBroadcast(broadcastIntent);
                    }

                    @Override
                    public void onRenameComplete(int totalRenamed) {}
                    @Override
                    public void onRenameCancelled() {}
                    @Override
                    public void onRenameError(String message) {}
                }, handler);

                handler.post(() -> {
                    terminarServicio("Éxito", "Se renombraron " + totalRenamed + " archivos.");
                    Intent completeIntent = new Intent("RENAME_UPDATE");
                    completeIntent.setPackage(getPackageName());
                    completeIntent.putExtra("status", "complete");
                    completeIntent.putExtra("total", totalRenamed);
                    sendBroadcast(completeIntent);
                });

            } catch (Exception e) {
                handler.post(() -> {
                    terminarServicio("Error", e.getMessage());
                    Intent errorIntent = new Intent("RENAME_UPDATE");
                    errorIntent.setPackage(getPackageName());
                    errorIntent.putExtra("status", "error");
                    errorIntent.putExtra("message", e.getMessage());
                    sendBroadcast(errorIntent);
                });
            }
        });

        return START_NOT_STICKY;
    }

    private void terminarServicio(String titulo, String mensaje) {
        Notification finalNotification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(titulo)
                .setContentText(mensaje)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
                .build();

        notificationManager.notify(2, finalNotification);
        stopForeground(true);
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Canal de Renombrado",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}