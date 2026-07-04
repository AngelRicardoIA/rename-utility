package com.niftyfiftysoftware.renameutility.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.niftyfiftysoftware.renameutility.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainActivity extends AppCompatActivity {

    private Uri selectedFolderUri;
    private TextView tvFolderPath;
    private TextView tvCount;
    private TextInputEditText etSearchExtension;

    private ActivityResultLauncher<Uri> folderPickerLauncher;
    private ExecutorService executorService;
    private Future<?> searchFuture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_main);

        Button btnSelectFolder = findViewById(R.id.btnSelectFolder);
        tvFolderPath = findViewById(R.id.tvFolderPath);
        tvCount = findViewById(R.id.tvCount);
        etSearchExtension = findViewById(R.id.etSearchExtension);

        executorService = Executors.newSingleThreadExecutor();

        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri == null) return;

                    selectedFolderUri = uri;

                    try {
                        getContentResolver().takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        );
                    } catch (Exception ignored) {
                        tvFolderPath.setText("Sin permiso");
                        tvCount.setText("Archivos: 0");
                        return;
                    }

                    DocumentFile rootFolder = DocumentFile.fromTreeUri(this, uri);

                    if (rootFolder == null) {
                        tvCount.setText("Error leyendo carpeta");
                        return;
                    }

                    String displayPath = Uri.decode(uri.getLastPathSegment());
                    if (displayPath != null && displayPath.contains(":")) {
                        displayPath = displayPath.substring(displayPath.indexOf(":") + 1);
                    }
                    tvFolderPath.setText(displayPath != null ? displayPath : rootFolder.getName());

                    String ext = etSearchExtension.getText() != null ? etSearchExtension.getText().toString().trim() : "";
                    if (ext.isEmpty()) {
                        tvCount.setText("Ingresa una extensión válida");
                        return;
                    }

                    ProgressBar progressBar = new ProgressBar(this);
                    progressBar.setPadding(0, 48, 0, 48);

                    AlertDialog loadingDialog = new MaterialAlertDialogBuilder(this)
                            .setTitle("Buscando archivos")
                            .setMessage("Por favor espera...")
                            .setView(progressBar)
                            .setCancelable(false)
                            .setNegativeButton("Cancelar", (dialog, which) -> {
                                if (searchFuture != null) {
                                    searchFuture.cancel(true);
                                }
                                tvCount.setText("Búsqueda cancelada");
                            })
                            .create();

                    loadingDialog.show();

                    Handler handler = new Handler(Looper.getMainLooper());

                    searchFuture = executorService.submit(() -> {
                        int total = countFilesRecursive(rootFolder, ext);

                        if (!Thread.currentThread().isInterrupted()) {
                            handler.post(() -> {
                                if (loadingDialog.isShowing()) {
                                    loadingDialog.dismiss();
                                }
                                tvCount.setText("Archivos: " + total);
                            });
                        }
                    });
                }
        );

        btnSelectFolder.setOnClickListener(v -> folderPickerLauncher.launch(null));
    }

    private int countFilesRecursive(DocumentFile folder, String extension) {
        if (Thread.currentThread().isInterrupted()) return 0;
        if (folder == null || !folder.isDirectory()) return 0;

        DocumentFile[] files = folder.listFiles();
        if (files == null) return 0;

        String ext = extension.toLowerCase();
        if (!ext.startsWith(".")) ext = "." + ext;

        int count = 0;

        for (DocumentFile file : files) {
            if (Thread.currentThread().isInterrupted()) return 0;
            if (file == null || file.getName() == null) continue;

            if (file.isDirectory()) {
                count += countFilesRecursive(file, ext);
            } else {
                String name = file.getName().toLowerCase();
                if (name.endsWith(ext)) {
                    count++;
                }
            }
        }

        return count;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}