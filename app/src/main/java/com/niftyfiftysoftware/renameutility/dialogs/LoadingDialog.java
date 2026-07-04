package com.niftyfiftysoftware.renameutility.dialogs;

import android.content.Context;
import android.widget.ProgressBar;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class LoadingDialog {

    private AlertDialog dialog;

    public void show(Context context, Runnable onCancel) {
        ProgressBar progressBar = new ProgressBar(context);
        progressBar.setPadding(0, 48, 0, 48);

        dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Buscando archivos")
                .setMessage("Por favor espera...")
                .setView(progressBar)
                .setCancelable(false)
                .setNegativeButton("Cancelar", (d, which) -> {
                    if (onCancel != null) {
                        onCancel.run();
                    }
                })
                .create();

        dialog.show();
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}