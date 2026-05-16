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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Polling interval in milliseconds for download progress updates.
    private static final int PROGRESS_POLL_INTERVAL_MS = 500;

    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private List<Message> messageList;
    private View welcomeContainer;
    private EditText messageInput;
    private AIManager aiManager;
    private ModelManager modelManager;

    // Views used by the model download card.
    private View downloadModelCard;
    private Button btnDownloadModel;
    private LinearProgressIndicator downloadProgress;
    private TextView downloadStatusText;

    private long currentDownloadId = -1;

    // Handler and runnable for periodic DownloadManager polling.
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressPoller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        aiManager = new AIManager();
        modelManager = new ModelManager(this);

        // Bind download-related views.
        downloadModelCard = findViewById(R.id.downloadModelCard);
        btnDownloadModel = findViewById(R.id.btnDownloadModel);
        downloadProgress = findViewById(R.id.downloadProgress);
        downloadStatusText = findViewById(R.id.downloadStatusText);

        // Apply system bar and keyboard insets.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(systemBars.bottom, ime.bottom);

            // Update toolbar padding for top status bar
            View toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.setPadding(0, systemBars.top, 0, 0);
            }

            // Update input container margin for bottom navigation/keyboard
            View inputContainer = findViewById(R.id.inputContainer);
            if (inputContainer != null) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                        (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) inputContainer.getLayoutParams();
                int baseMargin = (int) (12 * getResources().getDisplayMetrics().density);
                lp.bottomMargin = baseMargin + bottomInset;
                inputContainer.setLayoutParams(lp);
            }

            return WindowInsetsCompat.CONSUMED;
        });

        welcomeContainer = findViewById(R.id.welcomeContainer);
        recyclerView = findViewById(R.id.recyclerView);
        messageInput = findViewById(R.id.messageInput);
        ImageButton sendButton = findViewById(R.id.sendButton);

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(chatAdapter);

        sendButton.setOnClickListener(v -> sendMessage());

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, DrawerActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_left, 0);
            });
        }

        // Listen for DownloadManager completion events.
        ContextCompat.registerReceiver(this, onDownloadComplete,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED);

        btnDownloadModel.setOnClickListener(v -> {
            if (currentDownloadId == -1) {
                startModelDownload();
            } else {
                Toast.makeText(this, "Download already in progress", Toast.LENGTH_SHORT).show();
            }
        });

        // Check model status on startup and update the screen.
        checkModelStatus();
    }

    // Handles the callback when DownloadManager finishes a download.

    private final BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id != currentDownloadId) return;

            // Stop polling because the download has finished.
            stopProgressPolling();

            // Read the final download status.
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
            Cursor cursor = dm.query(query);

            boolean success = false;
            if (cursor != null && cursor.moveToFirst()) {
                int statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                if (statusCol != -1) {
                    int status = cursor.getInt(statusCol);
                    success = (status == DownloadManager.STATUS_SUCCESSFUL);
                }
                cursor.close();
            }

            // Clear the active download ID in either case.
            currentDownloadId = -1;

            if (success) {
                Log.d(TAG, "Download succeeded, checking model...");
                Toast.makeText(context, "Model downloaded!", Toast.LENGTH_SHORT).show();
                checkModelStatus(); // Load the model and refresh UI state.
            } else {
                Log.e(TAG, "Download failed or was cancelled");
                Toast.makeText(context, "Download failed. Please try again.", Toast.LENGTH_SHORT).show();
                setDownloadIdleState("Download failed. Tap to retry.");
            }
        }
    };

    // Poll DownloadManager for byte-level progress updates.

    /**
     * Starts polling DownloadManager every PROGRESS_POLL_INTERVAL_MS ms
     * to get real byte progress and update the progress bar + status text.
     */
    private void startProgressPolling() {
        progressPoller = new Runnable() {
            @Override
            public void run() {
                if (currentDownloadId == -1) return; // Download already finished or canceled.

                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Query query = new DownloadManager.Query()
                        .setFilterById(currentDownloadId);
                Cursor cursor = dm.query(query);

                if (cursor != null && cursor.moveToFirst()) {
                    int bytesDownloadedCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                    int bytesTotalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                    int statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);

                    if (bytesDownloadedCol != -1 && bytesTotalCol != -1 && statusCol != -1) {
                        long downloaded = cursor.getLong(bytesDownloadedCol);
                        long total = cursor.getLong(bytesTotalCol);
                        int status = cursor.getInt(statusCol);

                        if (status == DownloadManager.STATUS_FAILED) {
                            cursor.close();
                            stopProgressPolling();
                            currentDownloadId = -1;
                            setDownloadIdleState("Download failed. Tap to retry.");
                            return;
                        }

                        if (total > 0) {
                            // Update determinate progress when total size is known.
                            int percent = (int) ((downloaded * 100L) / total);
                            downloadProgress.setIndeterminate(false);
                            downloadProgress.setMax(100);
                            downloadProgress.setProgress(percent);

                            // Show progress in MB for readability.
                            long downloadedMB = downloaded / (1024 * 1024);
                            long totalMB = total / (1024 * 1024);
                            downloadStatusText.setText(
                                    "Downloading... " + downloadedMB + " MB / " + totalMB + " MB (" + percent + "%)");
                        } else {
                            // Keep indeterminate mode when total size is unknown.
                            downloadProgress.setIndeterminate(true);
                            downloadStatusText.setText("Downloading model... Please wait.");
                        }
                    }
                    cursor.close();
                }

                // Queue the next progress check.
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

    // Update UI based on whether a valid model exists.

    private void checkModelStatus() {
        String modelPath = modelManager.getValidModelPath();

        if (modelPath != null) {
            // Model is valid, so hide the download card.
            downloadModelCard.setVisibility(View.GONE);
            downloadProgress.setVisibility(View.GONE);

            Log.d(TAG, "Model found, loading: " + modelPath);
            aiManager.loadModel(modelPath);

            Toast.makeText(this, "AI model ready!", Toast.LENGTH_SHORT).show();
        } else {
            // Model is missing, so show the download card.
            downloadModelCard.setVisibility(View.VISIBLE);
            downloadProgress.setVisibility(View.GONE);
            btnDownloadModel.setEnabled(true);
            btnDownloadModel.setText("Download Model");
            downloadStatusText.setText("Download the core AI engine (~700MB) to start chatting offline.");
        }
    }

    private void startModelDownload() {
        btnDownloadModel.setEnabled(false);
        downloadProgress.setVisibility(View.VISIBLE);
        downloadProgress.setIndeterminate(true); // Stay indeterminate until total size is available.
        downloadStatusText.setText("Starting download...");

        currentDownloadId = modelManager.downloadModel();

        if (currentDownloadId != -1) {
            Log.d(TAG, "Download started with ID: " + currentDownloadId);
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
            startProgressPolling(); // Start polling for real-time byte progress.
        } else {
            Log.e(TAG, "DownloadManager failed to enqueue");
            Toast.makeText(this, "Failed to start download", Toast.LENGTH_SHORT).show();
            setDownloadIdleState("Could not start download. Tap to retry.");
        }
    }

    /** Resets the download card to idle/error state without hiding it. */
    private void setDownloadIdleState(String statusMessage) {
        btnDownloadModel.setEnabled(true);
        btnDownloadModel.setText("Download Model");
        downloadProgress.setVisibility(View.GONE);
        downloadStatusText.setText(statusMessage);
    }

    // Send a user message and render the model response.

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;

        if (welcomeContainer != null) {
            welcomeContainer.setVisibility(View.GONE);
        }
        recyclerView.setVisibility(View.VISIBLE);

        messageList.add(new Message(text, Message.TYPE_USER));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.scrollToPosition(messageList.size() - 1);

        messageInput.setText("");

        Message typingMessage = new Message("", Message.TYPE_TYPING);
        messageList.add(typingMessage);
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.scrollToPosition(messageList.size() - 1);
        Log.d(TAG, "User message: " + text);

        long startTime = System.currentTimeMillis();
        aiManager.generateResponse(text, response -> {
            long duration = System.currentTimeMillis() - startTime;
            Log.d(TAG, "AI Raw Response: " + response);
            Log.d(TAG, "AI Generation Time: " + duration + " ms");
            int index = messageList.indexOf(typingMessage);
            if (index != -1) {
                messageList.set(index, new Message(response, Message.TYPE_AI));
                chatAdapter.notifyItemChanged(index);
                recyclerView.scrollToPosition(index);
            }
        });
    }

    // Release resources when the activity is destroyed.

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProgressPolling();
        try {
            unregisterReceiver(onDownloadComplete);
        } catch (Exception e) {
            Log.w(TAG, "Receiver already unregistered");
        }
        aiManager.release(); // Free native model resources.
    }
}
