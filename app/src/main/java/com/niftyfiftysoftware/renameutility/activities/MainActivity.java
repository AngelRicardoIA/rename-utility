package com.niftyfiftysoftware.renameutility.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.niftyfiftysoftware.renameutility.R;
import com.niftyfiftysoftware.renameutility.dialogs.LoadingDialog;
import com.niftyfiftysoftware.renameutility.interfaces.SearchCallback;
import com.niftyfiftysoftware.renameutility.services.FileSearchManager;

public class MainActivity extends AppCompatActivity {

    private Uri selectedFolderUri;
    private DocumentFile rootFolder;
    private TextView tvFolderPath;
    private TextView tvCount;
    private TextInputEditText etSearchExtension;

    private ActivityResultLauncher<Uri> folderPickerLauncher;
    private FileSearchManager fileSearchManager;

    private static final String PREF_URI = "carpeta_guardada_uri";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_main);

        Button btnSelectFolder = findViewById(R.id.btnSelectFolder);
        tvFolderPath = findViewById(R.id.tvFolderPath);
        tvCount = findViewById(R.id.tvCount);
        etSearchExtension = findViewById(R.id.etSearchExtension);

        fileSearchManager = new FileSearchManager();

        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri == null) {
                        if (rootFolder == null) mostrarDialogoPermiso();
                        return;
                    }

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
                    iniciarBusquedaAutomatica();
                }
        );

        btnSelectFolder.setOnClickListener(v -> folderPickerLauncher.launch(null));

        verificarCarpetaGuardada();
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
                return;
            }
        }
        mostrarDialogoPermiso();
    }

    private void mostrarDialogoPermiso() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Permiso requerido")
                .setMessage("Para renombrar y buscar archivos, necesitas seleccionar una carpeta y dar permisos de acceso.")
                .setCancelable(false)
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    tvFolderPath.setText("Ninguna carpeta seleccionada");
                    tvCount.setText("Archivos: 0");
                })
                .setPositiveButton("Permitir", (dialog, which) -> {
                    folderPickerLauncher.launch(null);
                })
                .show();
    }

    private void actualizarTextoRuta(Uri uri) {
        String displayPath = Uri.decode(uri.getLastPathSegment());
        if (displayPath != null && displayPath.contains(":")) {
            displayPath = displayPath.substring(displayPath.indexOf(":") + 1);
        }
        tvFolderPath.setText(displayPath != null ? displayPath : rootFolder.getName());
    }

    private void iniciarBusquedaAutomatica() {
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
                tvCount.setText("Archivos: " + totalFiles);
            }

            @Override
            public void onSearchCancelled() {
                tvCount.setText("Búsqueda cancelada");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fileSearchManager != null) {
            fileSearchManager.shutdown();
        }
    }
}