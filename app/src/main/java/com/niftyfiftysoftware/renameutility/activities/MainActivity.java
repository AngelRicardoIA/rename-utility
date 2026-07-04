package com.niftyfiftysoftware.renameutility.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.niftyfiftysoftware.renameutility.R;
import com.niftyfiftysoftware.renameutility.dialogs.LoadingDialog;
import com.niftyfiftysoftware.renameutility.interfaces.SearchCallback;
import com.niftyfiftysoftware.renameutility.services.FileSearchManager;
import com.niftyfiftysoftware.renameutility.services.RenameService;

public class MainActivity extends AppCompatActivity {

    private Uri selectedFolderUri;
    private DocumentFile rootFolder;
    private TextView tvFolderPath;
    private TextView tvCount;
    private TextInputEditText etSearchExtension;
    private TextInputEditText etNewExtension;
    private Button btnSearch;

    private ActivityResultLauncher<Uri> folderPickerLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private FileSearchManager fileSearchManager;

    private BroadcastReceiver renameReceiver;
    private AlertDialog currentProgressDialog;
    private LinearProgressIndicator currentProgressBar;

    private static final String PREF_URI = "carpeta_guardada_uri";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_main);

        Button btnSelectFolder = findViewById(R.id.btnSelectFolder);
        btnSearch = findViewById(R.id.btnSearch);
        tvFolderPath = findViewById(R.id.tvFolderPath);
        tvCount = findViewById(R.id.tvCount);
        etSearchExtension = findViewById(R.id.etSearchExtension);
        etNewExtension = findViewById(R.id.etNewExtension);

        fileSearchManager = new FileSearchManager();

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {}
        );

        pedirPermisoNotificaciones();
        configurarReceiver();

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
                        SharedPreferences prefs = getSharedPreferences("MisPreferencias", MODE_PRIVATE);
                        prefs.edit().putString(PREF_URI, uri.toString()).apply();
                    } catch (Exception ignored) {
                        tvFolderPath.setText("Sin permiso");
                        return;
                    }

                    rootFolder = DocumentFile.fromTreeUri(this, uri);
                    actualizarTextoRuta(uri);
                    iniciarBusquedaAutomatica(true);
                }
        );

        btnSelectFolder.setOnClickListener(v -> folderPickerLauncher.launch(null));

        btnSearch.setOnClickListener(v -> {
            if (rootFolder == null) {
                tvCount.setText("Selecciona una carpeta primero");
                return;
            }

            String oldExt = etSearchExtension.getText() != null ? etSearchExtension.getText().toString().trim() : "";
            String newExt = etNewExtension.getText() != null ? etNewExtension.getText().toString().trim() : "";

            if (oldExt.isEmpty() || newExt.isEmpty()) {
                tvCount.setText("Llena ambas extensiones");
                return;
            }

            if (oldExt.equalsIgnoreCase(newExt)) {
                tvCount.setText("Las extensiones no pueden ser iguales");
                return;
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle("Confirmar renombrado")
                    .setMessage("¿Estás seguro de que quieres renombrar los archivos que coincidan con '" + oldExt + "' a '" + newExt + "'?")
                    .setPositiveButton("Renombrar", (dialog, which) -> iniciarRenombrado(oldExt, newExt))
                    .setNegativeButton("Cancelar", null)
                    .show();
        });

        verificarCarpetaGuardada();
    }

    private void configurarReceiver() {
        renameReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String status = intent.getStringExtra("status");
                if (status == null) return;

                switch (status) {
                    case "progress":
                        int current = intent.getIntExtra("current", 0);
                        int total = intent.getIntExtra("total", 0);
                        if (currentProgressDialog != null && currentProgressDialog.isShowing()) {
                            if (currentProgressBar.isIndeterminate()) {
                                currentProgressBar.setIndeterminate(false);
                                currentProgressBar.setMax(total);
                            }
                            currentProgressBar.setProgressCompat(current, true);
                            currentProgressDialog.setMessage("Renombrando: " + current + " de " + total);
                        }
                        break;
                    case "complete":
                        int totalRenamed = intent.getIntExtra("total", 0);
                        if (currentProgressDialog != null) currentProgressDialog.dismiss();
                        btnSearch.setEnabled(true);
                        btnSearch.setText("Renombrar archivos");
                        new MaterialAlertDialogBuilder(MainActivity.this)
                                .setTitle("Renombrado exitoso")
                                .setMessage("Se renombraron " + totalRenamed + " archivos.")
                                .setPositiveButton("OK", null)
                                .show();
                        iniciarBusquedaAutomatica(false);
                        break;
                    case "error":
                        String message = intent.getStringExtra("message");
                        if (currentProgressDialog != null) currentProgressDialog.dismiss();
                        btnSearch.setEnabled(true);
                        btnSearch.setText("Renombrar archivos");
                        new MaterialAlertDialogBuilder(MainActivity.this)
                                .setTitle("Error")
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show();
                        break;
                }
            }
        };
    }

    private void iniciarRenombrado(String oldExt, String newExt) {
        btnSearch.setEnabled(false);
        btnSearch.setText("Procesando...");

        currentProgressBar = new LinearProgressIndicator(this);
        currentProgressBar.setIndeterminate(true);

        FrameLayout frameLayout = new FrameLayout(this);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        frameLayout.setPadding(padding, padding, padding, padding);
        frameLayout.addView(currentProgressBar);

        currentProgressDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Renombrando archivos")
                .setMessage("Iniciando...")
                .setView(frameLayout)
                .setCancelable(false)
                .create();

        currentProgressDialog.show();

        Intent serviceIntent = new Intent(this, RenameService.class);
        serviceIntent.putExtra("folderUri", selectedFolderUri.toString());
        serviceIntent.putExtra("oldExt", oldExt);
        serviceIntent.putExtra("newExt", newExt);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void verificarCarpetaGuardada() {
        SharedPreferences prefs = getSharedPreferences("MisPreferencias", MODE_PRIVATE);
        String uriGuardadaStr = prefs.getString(PREF_URI, null);

        if (uriGuardadaStr != null) {
            Uri uriGuardada = Uri.parse(uriGuardadaStr);
            boolean tienePermiso = false;

            for (UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
                if (permission.getUri().equals(uriGuardada)) {
                    tienePermiso = true;
                    break;
                }
            }

            if (tienePermiso) {
                selectedFolderUri = uriGuardada;
                rootFolder = DocumentFile.fromTreeUri(this, uriGuardada);
                actualizarTextoRuta(uriGuardada);
                iniciarBusquedaAutomatica(false);
            }
        }
    }

    private void actualizarTextoRuta(Uri uri) {
        String displayPath = Uri.decode(uri.getLastPathSegment());
        if (displayPath != null && displayPath.contains(":")) {
            displayPath = displayPath.substring(displayPath.indexOf(":") + 1);
        }
        tvFolderPath.setText(displayPath != null ? displayPath : rootFolder.getName());
    }

    private void iniciarBusquedaAutomatica(boolean mostrarPopup) {
        String ext = etSearchExtension.getText() != null ? etSearchExtension.getText().toString().trim() : "";
        if (ext.isEmpty()) {
            tvCount.setText("Ingresa una palabra o extensión");
            return;
        }

        LoadingDialog loadingDialog = new LoadingDialog();
        loadingDialog.show(this, () -> fileSearchManager.cancelSearch());

        fileSearchManager.startSearch(this, selectedFolderUri, ext, new SearchCallback() {
            @Override
            public void onSearchComplete(int totalFiles) {
                loadingDialog.dismiss();
                if (mostrarPopup) {
                    new MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle("Escaneo terminado")
                            .setMessage("Se encontraron " + totalFiles + " archivos que coinciden con '" + ext + "'.")
                            .setPositiveButton("OK", null)
                            .show();
                }
                tvCount.setText("Archivos: " + totalFiles);
            }

            @Override
            public void onSearchCancelled() {
                tvCount.setText("Búsqueda cancelada");
            }
        });
    }

    private void pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        androidx.core.content.ContextCompat.registerReceiver(
                this,
                renameReceiver,
                new IntentFilter("RENAME_UPDATE"),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(renameReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fileSearchManager != null) {
            fileSearchManager.shutdown();
        }
    }
}