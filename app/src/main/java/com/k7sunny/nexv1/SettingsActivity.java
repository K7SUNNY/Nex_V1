package com.k7sunny.nexv1;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import java.io.File;

public class SettingsActivity extends AppCompatActivity {

    private PreferenceManager preferenceManager;
    private ModelManager modelManager;
    private long currentDownloadId = -1;

    // UI elements for model selection
    private View layoutModelFast;
    private View layoutModelPro;
    private View layoutModelUltra;
    private ImageView checkModelFast;
    private ImageView checkModelPro;
    private ImageView checkModelUltra;

    // UI elements for download status
    private MaterialCardView cardDownloadManager;
    private TextView tvDownloadTitle;
    private TextView tvDownloadStatus;
    private LinearProgressIndicator pbDownloadProgress;
    private MaterialButton btnActionDownload;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressPoller;
    private static final int PROGRESS_POLL_INTERVAL_MS = 1000;
    private static final String TAG_DOWNLOAD = "SettingsDownload";

    private TextView tvPersonaSummary;
    private TextView tvMaxTokensVal;
    private Slider sliderMaxTokens;
    private TextView tvTemperatureVal;
    private Slider sliderTemperature;
    private TextView tvContextWindowVal;
    private Slider sliderContextWindow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_settings);

        preferenceManager = new PreferenceManager(this);
        modelManager = new ModelManager(this);

        tvPersonaSummary = findViewById(R.id.tv_persona_summary);
        tvMaxTokensVal = findViewById(R.id.tv_max_tokens_val);
        sliderMaxTokens = findViewById(R.id.slider_max_tokens);
        tvTemperatureVal = findViewById(R.id.tv_temperature_val);
        sliderTemperature = findViewById(R.id.slider_temperature);
        tvContextWindowVal = findViewById(R.id.tv_context_window_val);
        sliderContextWindow = findViewById(R.id.slider_context_window);

        // Bind model selection views
        layoutModelFast = findViewById(R.id.layout_model_fast);
        layoutModelPro = findViewById(R.id.layout_model_pro);
        layoutModelUltra = findViewById(R.id.layout_model_ultra);
        checkModelFast = findViewById(R.id.check_model_fast);
        checkModelPro = findViewById(R.id.check_model_pro);
        checkModelUltra = findViewById(R.id.check_model_ultra);

        // Bind download views
        cardDownloadManager = findViewById(R.id.card_download_manager);
        tvDownloadTitle = findViewById(R.id.tv_download_title);
        tvDownloadStatus = findViewById(R.id.tv_download_status);
        pbDownloadProgress = findViewById(R.id.pb_download_progress);
        btnActionDownload = findViewById(R.id.btn_action_download);

        layoutModelFast.setOnClickListener(v -> selectModel("fast"));
        layoutModelPro.setOnClickListener(v -> selectModel("pro"));
        layoutModelUltra.setOnClickListener(v -> selectModel("ultra"));

        btnActionDownload.setOnClickListener(v -> {
            if (currentDownloadId == -1) {
                startModelDownload();
            } else {
                Toast.makeText(this, "Download already in progress", Toast.LENGTH_SHORT).show();
            }
        });

        sliderMaxTokens.addOnChangeListener((slider, value, fromUser) -> {
            int val = Math.round(value);
            tvMaxTokensVal.setText(String.valueOf(val));
            preferenceManager.setMaxTokens(val);
        });

        sliderTemperature.addOnChangeListener((slider, value, fromUser) -> {
            tvTemperatureVal.setText(String.format(java.util.Locale.US, "%.2f", value));
            preferenceManager.setTemperature(value);
        });

        if (sliderContextWindow != null) {
            sliderContextWindow.addOnChangeListener((slider, value, fromUser) -> {
                int val = Math.round(value);
                if (tvContextWindowVal != null) {
                    tvContextWindowVal.setText(val + " messages");
                }
                preferenceManager.setContextWindow(val);
            });
        }

        com.google.android.material.materialswitch.MaterialSwitch switchHaptic = findViewById(R.id.switch_haptic);
        View layoutHaptic = findViewById(R.id.layout_haptic_feedback);
        if (switchHaptic != null) {
            switchHaptic.setChecked(preferenceManager.isHapticFeedbackEnabled());
            switchHaptic.setOnCheckedChangeListener((btn, isChecked) -> {
                preferenceManager.setHapticFeedbackEnabled(isChecked);
            });
            if (layoutHaptic != null) {
                layoutHaptic.setOnClickListener(v -> switchHaptic.toggle());
            }
        }

        View root = findViewById(R.id.settings_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            
            View toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.setPadding(0, systemBars.top, 0, 0);
            }
            return WindowInsetsCompat.CONSUMED;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Check if there is an active download on launch
        currentDownloadId = preferenceManager.getActiveDownloadId();
        if (currentDownloadId != -1) {
            checkActiveDownloadStatus();
        }

        updateUI();

        findViewById(R.id.btn_edit_persona).setOnClickListener(v -> showPersonaEditDialog());
    }

    private void updateUI() {
        if (!preferenceManager.isCustomPersonaSet()) {
            tvPersonaSummary.setText("Default (Optimized for Nex)");
        } else {
            String persona = preferenceManager.getSystemPersona();
            if (persona.length() > 60) {
                tvPersonaSummary.setText(persona.substring(0, 57) + "...");
            } else {
                tvPersonaSummary.setText(persona);
            }
        }

        int maxTokens = preferenceManager.getMaxTokens();
        float temperature = preferenceManager.getTemperature();

        if (tvMaxTokensVal != null) tvMaxTokensVal.setText(String.valueOf(maxTokens));
        if (sliderMaxTokens != null) sliderMaxTokens.setValue((float) maxTokens);

        if (tvTemperatureVal != null) {
            tvTemperatureVal.setText(String.format(java.util.Locale.US, "%.2f", temperature));
        }
        if (sliderTemperature != null) sliderTemperature.setValue(temperature);

        int contextWindow = preferenceManager.getContextWindow();
        if (tvContextWindowVal != null) {
            tvContextWindowVal.setText(contextWindow + " messages");
        }
        if (sliderContextWindow != null) {
            sliderContextWindow.setValue((float) contextWindow);
        }

        updateModelSelectionUI();
        updateDownloadCardUI();
    }

    private void selectModel(String modelKey) {
        long activeId = preferenceManager.getActiveDownloadId();
        if (activeId != -1) {
            Toast.makeText(this, "Cannot change model while download is in progress.", Toast.LENGTH_SHORT).show();
            return;
        }

        preferenceManager.setSelectedModel(modelKey);
        updateModelSelectionUI();
        updateDownloadCardUI();
    }

    private void updateModelSelectionUI() {
        String currentModel = preferenceManager.getSelectedModel();
        
        checkModelFast.setVisibility("fast".equals(currentModel) ? View.VISIBLE : View.GONE);
        checkModelPro.setVisibility("pro".equals(currentModel) ? View.VISIBLE : View.GONE);
        checkModelUltra.setVisibility("ultra".equals(currentModel) ? View.VISIBLE : View.GONE);
    }

    private void updateDownloadCardUI() {
        String currentModel = preferenceManager.getSelectedModel();
        boolean downloaded = modelManager.isModelFilePresentWithCorrectSize() && modelManager.isModelVerified();

        if (downloaded) {
            cardDownloadManager.setVisibility(View.GONE);
        } else {
            cardDownloadManager.setVisibility(View.VISIBLE);
            pbDownloadProgress.setVisibility(View.GONE);
            btnActionDownload.setEnabled(true);
            btnActionDownload.setText("Download");

            String sizeStr = "fast".equals(currentModel) ? "~450MB" : ("pro".equals(currentModel) ? "~1.1GB" : "~2.0GB");
            
            if (modelManager.isModelFileCorrupted()) {
                tvDownloadTitle.setText("Corrupted Model Detected");
                tvDownloadStatus.setText("The existing file is incomplete or corrupted. Tap below to delete and download a clean copy (" + sizeStr + ").");
                btnActionDownload.setText("Clean & Download");
            } else {
                tvDownloadTitle.setText("Model Download Required");
                tvDownloadStatus.setText("Download the core AI engine (" + sizeStr + ") to use this model offline.");
            }
        }
        
        if (currentDownloadId != -1) {
            cardDownloadManager.setVisibility(View.VISIBLE);
            btnActionDownload.setEnabled(false);
            btnActionDownload.setText("Downloading...");
            pbDownloadProgress.setVisibility(View.VISIBLE);
        }
    }

    private void checkActiveDownloadStatus() {
        DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(currentDownloadId);
        try (Cursor cursor = dm.query(query)) {
            if (cursor != null && cursor.moveToFirst()) {
                int statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                if (statusCol != -1) {
                    int status = cursor.getInt(statusCol);
                    if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PAUSED || status == DownloadManager.STATUS_PENDING) {
                        startProgressPolling();
                        ContextCompat.registerReceiver(this, onDownloadComplete,
                                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                                ContextCompat.RECEIVER_EXPORTED);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        currentDownloadId = -1;
        preferenceManager.setActiveDownloadId(-1);
    }

    private void startModelDownload() {
        modelManager.cleanupCorruptedModel();

        btnActionDownload.setEnabled(false);
        pbDownloadProgress.setVisibility(View.VISIBLE);
        pbDownloadProgress.setIndeterminate(true);
        tvDownloadTitle.setText("Starting download...");
        tvDownloadStatus.setText("Requesting download slot...");

        currentDownloadId = modelManager.downloadModel();

        if (currentDownloadId != -1) {
            Log.d(TAG_DOWNLOAD, "Download started with ID: " + currentDownloadId);
            preferenceManager.setActiveDownloadId(currentDownloadId);
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
            
            ContextCompat.registerReceiver(this, onDownloadComplete,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    ContextCompat.RECEIVER_EXPORTED);
            
            startProgressPolling();
            updateDownloadCardUI();
        } else {
            Log.e(TAG_DOWNLOAD, "DownloadManager failed to enqueue");
            Toast.makeText(this, "Failed to start download", Toast.LENGTH_SHORT).show();
            updateDownloadCardUI();
        }
    }

    private void startProgressPolling() {
        if (progressPoller != null) return;
        progressPoller = new Runnable() {
            @Override
            public void run() {
                if (currentDownloadId == -1) return;

                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Query query = new DownloadManager.Query().setFilterById(currentDownloadId);
                try (Cursor cursor = dm.query(query)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int bytesDownloadedCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                        int bytesTotalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                        int statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);

                        if (bytesDownloadedCol != -1 && bytesTotalCol != -1 && statusCol != -1) {
                            long downloaded = cursor.getLong(bytesDownloadedCol);
                            long total = cursor.getLong(bytesTotalCol);
                            int status = cursor.getInt(statusCol);

                            if (status == DownloadManager.STATUS_FAILED) {
                                int reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                                int reason = -1;
                                if (reasonCol != -1) {
                                    reason = cursor.getInt(reasonCol);
                                }
                                stopProgressPolling();
                                currentDownloadId = -1;
                                preferenceManager.setActiveDownloadId(-1);
                                Toast.makeText(SettingsActivity.this, "Download failed (reason: " + reason + ")", Toast.LENGTH_SHORT).show();
                                modelManager.cleanupCorruptedModel();
                                updateDownloadCardUI();
                                return;
                            }

                            if (total > 0) {
                                int percent = (int) ((downloaded * 100L) / total);
                                pbDownloadProgress.setIndeterminate(false);
                                pbDownloadProgress.setMax(100);
                                pbDownloadProgress.setProgress(percent);

                                long downloadedMB = downloaded / (1024 * 1024);
                                long totalMB = total / (1024 * 1024);
                                tvDownloadStatus.setText("Downloading... " + downloadedMB + " MB / " + totalMB + " MB (" + percent + "%)");
                            } else {
                                pbDownloadProgress.setIndeterminate(true);
                                tvDownloadStatus.setText("Downloading model... Please wait.");
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                progressHandler.postDelayed(this, PROGRESS_POLL_INTERVAL_MS);
            }
        };
        progressHandler.post(progressPoller);
    }

    private void stopProgressPolling() {
        if (progressPoller != null) {
            progressHandler.removeCallbacks(progressPoller);
            progressPoller = null;
        }
    }

    private void verifyModelInBackground() {
        cardDownloadManager.setVisibility(View.VISIBLE);
        btnActionDownload.setEnabled(false);
        pbDownloadProgress.setVisibility(View.VISIBLE);
        pbDownloadProgress.setIndeterminate(true);
        tvDownloadTitle.setText("Verifying Integrity");
        tvDownloadStatus.setText("Verifying AI Model integrity...");

        new Thread(() -> {
            boolean success = modelManager.verifyModelHash();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (success) {
                    Toast.makeText(this, "AI model ready!", Toast.LENGTH_SHORT).show();
                    updateDownloadCardUI();
                } else {
                    Toast.makeText(this, "Verification failed! Corrupted model.", Toast.LENGTH_LONG).show();
                    modelManager.cleanupCorruptedModel();
                    updateDownloadCardUI();
                }
            });
        }).start();
    }

    private final BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id != currentDownloadId) return;

            stopProgressPolling();

            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
            Cursor cursor = dm.query(query);

            boolean success = false;
            int reason = -1;
            if (cursor != null && cursor.moveToFirst()) {
                int statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                if (statusCol != -1) {
                    int status = cursor.getInt(statusCol);
                    success = (status == DownloadManager.STATUS_SUCCESSFUL);
                }
                if (reasonCol != -1) {
                    reason = cursor.getInt(reasonCol);
                }
                cursor.close();
            }

            currentDownloadId = -1;
            preferenceManager.setActiveDownloadId(-1);

            try {
                unregisterReceiver(this);
            } catch (Exception e) {
                // Ignore
            }

            if (success) {
                Log.d(TAG_DOWNLOAD, "Download succeeded, verifying model hash...");
                Toast.makeText(context, "Model downloaded!", Toast.LENGTH_SHORT).show();
                verifyModelInBackground();
            } else {
                Log.e(TAG_DOWNLOAD, "Download failed or was cancelled, reason: " + reason);
                Toast.makeText(context, "Download failed (reason: " + reason + "). Please try again.", Toast.LENGTH_SHORT).show();
                updateDownloadCardUI();
            }
        }
    };

    private void showPersonaEditDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.CustomBottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.dialog_rename_chat, null);
        dialog.setContentView(view);

        TextView title = (TextView) ((android.view.ViewGroup)view).getChildAt(1); 
        com.google.android.material.textfield.TextInputEditText input = view.findViewById(R.id.edit_text_rename);
        MaterialButton btnSave = view.findViewById(R.id.btn_save_rename);

        title.setText("System Persona");
        input.setHint("e.g. Be funny, Reply in Python...");
        
        if (preferenceManager.isCustomPersonaSet()) {
            input.setText(preferenceManager.getSystemPersona());
            if (input.getText() != null) {
                input.setSelection(input.getText().length());
            }
        }

        btnSave.setOnClickListener(v -> {
            String newPersona = input.getText() != null ? input.getText().toString().trim() : "";
            if (newPersona.isEmpty()) {
                preferenceManager.resetSystemPersona();
                Toast.makeText(this, "Reset to Nex default", Toast.LENGTH_SHORT).show();
            } else {
                preferenceManager.setSystemPersona(newPersona);
                Toast.makeText(this, "Persona updated", Toast.LENGTH_SHORT).show();
            }
            updateUI();
            dialog.dismiss();
        });

        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProgressPolling();
        try {
            unregisterReceiver(onDownloadComplete);
        } catch (Exception e) {
            // Ignore
        }
    }
}
