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

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RenameService extends Service {

    public static final String ACTION_CANCEL = "com.niftyfiftysoftware.ACTION_CANCEL";
    private static final String CHANNEL_ID = "RenameServiceChannel";
    public static boolean isRunning = false;

    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private ExecutorService executorService;
    private Future<?> renameTask;

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

        if (ACTION_CANCEL.equals(intent.getAction())) {
            if (renameTask != null && !renameTask.isDone()) {
                renameTask.cancel(true);
            }
            return START_NOT_STICKY;
        }

        String uriString = intent.getStringExtra("folderUri");
        String oldExt = intent.getStringExtra("oldExt");
        String newExt = intent.getStringExtra("newExt");
        String prefix = intent.getStringExtra("prefix");

        if (uriString == null || oldExt == null || newExt == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        isRunning = true;
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

        renameTask = executorService.submit(() -> {
            try {
                FileUtils.renameFilesFast(this, folderUri, oldExt, newExt, prefix, new RenameCallback() {
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
                    public void onRenameComplete(int totalRenamed, ArrayList<String> failedFiles) {
                        terminarServicio("Éxito", "Se renombraron " + totalRenamed + " archivos.");
                        Intent completeIntent = new Intent("RENAME_UPDATE");
                        completeIntent.setPackage(getPackageName());
                        completeIntent.putExtra("status", "complete");
                        completeIntent.putExtra("total", totalRenamed);
                        completeIntent.putStringArrayListExtra("failed", failedFiles);
                        sendBroadcast(completeIntent);
                    }

                    @Override
                    public void onRenameCancelled() {
                        terminarServicio("Cancelado", "El proceso fue detenido.");
                        Intent cancelIntent = new Intent("RENAME_UPDATE");
                        cancelIntent.setPackage(getPackageName());
                        cancelIntent.putExtra("status", "cancelled");
                        sendBroadcast(cancelIntent);
                    }

                    @Override
                    public void onRenameError(String message) {
                        terminarServicio("Error", message);
                        Intent errorIntent = new Intent("RENAME_UPDATE");
                        errorIntent.setPackage(getPackageName());
                        errorIntent.putExtra("status", "error");
                        errorIntent.putExtra("message", message);
                        sendBroadcast(errorIntent);
                    }
                }, handler);

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
        isRunning = false;
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
        isRunning = false;
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}