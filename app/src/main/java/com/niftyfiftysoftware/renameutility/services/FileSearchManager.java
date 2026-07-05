package com.niftyfiftysoftware.renameutility.services;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.niftyfiftysoftware.renameutility.interfaces.SearchCallback;
import com.niftyfiftysoftware.renameutility.utils.FileUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FileSearchManager {

    private final ExecutorService executorService;
    private Future<?> searchFuture;

    public FileSearchManager() {
        executorService = Executors.newSingleThreadExecutor();
    }

    public void startSearch(Context context, Uri folderUri, String query, SearchCallback callback) {
        cancelSearch();
        Handler handler = new Handler(Looper.getMainLooper());
        searchFuture = executorService.submit(() -> {
            int total = FileUtils.startFastSearch(context, folderUri, query);
            if (!Thread.currentThread().isInterrupted()) {
                handler.post(() -> callback.onSearchComplete(total));
            } else {
                handler.post(callback::onSearchCancelled);
            }
        });
    }

    public void cancelSearch() {
        if (searchFuture != null) {
            searchFuture.cancel(true);
        }
    }

    public void shutdown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}