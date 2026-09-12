package com.k7sunny.nexv1;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "NexUI";
    private static final String TAG_DOWNLOAD = "NexDownload";
    private static final String TAG_CHAT = "NexChat";

    // Polling interval in milliseconds for download progress updates.
    private static final int PROGRESS_POLL_INTERVAL_MS = 500;

    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private List<Message> messageList;
    private View welcomeContainer;
    private EditText messageInput;
    private AIManager aiManager;
    private ModelManager modelManager;
    private HistoryManager historyManager;
    private MemoryManager memoryManager;
    private PreferenceManager preferenceManager;
    private ConversationAnalyzer conversationAnalyzer;
    private ExecutorService dbExecutor;
    private final List<String> cachedMemories = new ArrayList<>();
    private String currentSessionId;
    private String currentSessionTitle = null;
    private boolean isGenerating = false;
    private View fabScrollToBottom;

    // Image attachment UI in composer
    private String selectedImagePath = null;
    private FrameLayout layoutImagePreview;
    private ImageView ivComposerPreview;
    private ImageButton btnRemoveImage;
    private ImageButton btnAttachImage;

    /**
     * FIX (title-generation bug #1/#2/#3/#7): title/drift generation runs
     * asynchronously on the AI inference thread. Without these guards, if the
     * user switches chats (or manually renames the chat) while a request is
     * still in flight, the eventual callback would silently overwrite the
     * WRONG session's title in the database — a real data-corruption bug.
     * `titleGenerationSessionId` records which session a request belongs to
     * so the callback can detect and discard stale results, and
     * `titleGenerationInFlight` prevents firing overlapping requests for the
     * same session.
     */
    private boolean titleGenerationInFlight = false;
    private String titleGenerationSessionId = null;

    /**
     * Tracks whether the user has intentionally scrolled up to read older messages.
     * When true, new tokens from the AI won't force-scroll the view to the bottom,
     * preserving the user's reading position.
     */
    private boolean isUserScrolledUp = false;

    /**
     * True while the user is physically dragging or flinging the RecyclerView.
     * Suppresses ALL programmatic scrolling to avoid fighting the touch gesture.
     */
    private boolean isUserTouching = false;

    private final ActivityResultLauncher<Intent> drawerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (result.getData() != null) {
                        String sessionId = result.getData().getStringExtra("session_id");
                        if (sessionId != null) {
                            loadSession(sessionId);
                            return;
                        }
                    }
                    // If no session ID, it's a "New Chat" request
                    startNewChat();
                } else {
                    // FIX (title-generation bug #6): the drawer may have been
                    // used to rename the CURRENTLY open session without
                    // switching away from it (no session_id result is sent in
                    // that case). Refresh our in-memory title from the DB so
                    // a later auto-save doesn't clobber the rename with a
                    // stale in-memory title.
                    refreshSessionTitleIfManual();
                }
            }
    );

    private final ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    handleSelectedImage(uri);
                }
            });

    private final ActivityResultLauncher<Intent> legacyPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    handleSelectedImage(result.getData().getData());
                }
            });

    // Attached Document
    private View layoutDocumentPreview;
    private TextView tvDocName;
    private ImageButton btnRemoveDocument;
    private String attachedDocName = null;
    private String attachedDocText = null;
    private String attachedDocRenderedImage = null;

    private final ActivityResultLauncher<String[]> documentPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    handleSelectedDocument(uri);
                }
            });

    // Voice Input (STT)
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private ImageButton btnVoiceInput;

    private final ActivityResultLauncher<String> audioPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startVoiceRecognition();
                } else {
                    // Toast.makeText(this, "Microphone permission is required for voice input", Toast.LENGTH_SHORT).show();
                }
            });

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

        if (getApplication() instanceof NexApplication) {
            aiManager = ((NexApplication) getApplication()).getAiManager();
            dbExecutor = ((NexApplication) getApplication()).getDbExecutor();
        } else {
            aiManager = new AIManager();
            dbExecutor = Executors.newSingleThreadExecutor();
        }
        modelManager = new ModelManager(this);
        historyManager = new HistoryManager(this);
        memoryManager = new MemoryManager(this);
        preferenceManager = new PreferenceManager(this);
        conversationAnalyzer = new ConversationAnalyzer(this, aiManager);
        currentSessionId = String.valueOf(System.currentTimeMillis());

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

            // Update toolbar and center title padding for top status bar
            View toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.setPadding(0, systemBars.top, 0, 0);
            }
            View centerTitle = findViewById(R.id.layout_center_title);
            if (centerTitle != null) {
                centerTitle.setPadding(0, systemBars.top, 0, 0);
            }

            // Update input container margin for bottom navigation/keyboard
            View inputCard = findViewById(R.id.inputCard);
            if (inputCard != null) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                        (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) inputCard.getLayoutParams();
                int baseMargin = (int) (16 * getResources().getDisplayMetrics().density);
                lp.bottomMargin = baseMargin + bottomInset;
                inputCard.setLayoutParams(lp);
            }

            return WindowInsetsCompat.CONSUMED;
        });

        welcomeContainer = findViewById(R.id.welcomeContainer);
        recyclerView = findViewById(R.id.recyclerView);
        messageInput = findViewById(R.id.messageInput);
        ImageButton sendButton = findViewById(R.id.sendButton);
        TextView tvCharCount = findViewById(R.id.tv_char_count);

        if (messageInput != null && tvCharCount != null) {
            messageInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateTokenCount(s.toString());
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });

            messageInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    if (isGenerating) {
                        cancelGeneration();
                    } else {
                        sendMessage();
                    }
                    return true;
                }
                return false;
            });
        }

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList, new ChatAdapter.OnMessageActionListener() {
            @Override
            public void onRegenerate(int position) {
                regenerateResponse(position);
            }

            @Override
            public void onPinToMemory(String text) {
                pinMessageToMemory(text);
            }

            @Override
            public void onDeleteMessage(int position) {
                deleteMessageAt(position);
            }
        }, preferenceManager);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(chatAdapter);

        fabScrollToBottom = findViewById(R.id.fabScrollToBottom);
        if (fabScrollToBottom != null) {
            fabScrollToBottom.setOnClickListener(v -> {
                isUserScrolledUp = false;
                if (messageList != null && !messageList.isEmpty()) {
                    recyclerView.smoothScrollToPosition(messageList.size() - 1);
                }
                fabScrollToBottom.setVisibility(View.GONE);
            });
        }

        // Track whether the user has scrolled up away from the bottom.
        // This prevents auto-scroll from interrupting reading during streaming.
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                // Suppress programmatic scrolling while user is dragging or flinging
                isUserTouching = (newState == RecyclerView.SCROLL_STATE_DRAGGING
                        || newState == RecyclerView.SCROLL_STATE_SETTLING);
            }

            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;

                int lastVisible = lm.findLastVisibleItemPosition();
                int totalItems = lm.getItemCount();

                // Consider "at bottom" if within 2 items of the end.
                // This accounts for partially visible items at the edge.
                isUserScrolledUp = lastVisible < totalItems - 2;

                if (fabScrollToBottom != null) {
                    if (isUserScrolledUp && totalItems > 0 && recyclerView.getVisibility() == View.VISIBLE) {
                        fabScrollToBottom.setVisibility(View.VISIBLE);
                    } else {
                        fabScrollToBottom.setVisibility(View.GONE);
                    }
                }
            }
        });

        layoutImagePreview = findViewById(R.id.layout_image_preview);
        ivComposerPreview = findViewById(R.id.iv_composer_preview);
        btnRemoveImage = findViewById(R.id.btn_remove_image);
        btnAttachImage = findViewById(R.id.btnAttachImage);

        layoutDocumentPreview = findViewById(R.id.layout_document_preview);
        tvDocName = findViewById(R.id.tv_doc_name);
        btnRemoveDocument = findViewById(R.id.btn_remove_document);

        if (btnAttachImage != null) {
            btnAttachImage.setOnClickListener(v -> showAttachmentOptions());
        }
        if (btnRemoveImage != null) {
            btnRemoveImage.setOnClickListener(v -> clearSelectedImage());
        }
        if (btnRemoveDocument != null) {
            btnRemoveDocument.setOnClickListener(v -> clearSelectedDocument());
        }

        setupSuggestions();

        sendButton.setOnClickListener(v -> {
            if (isGenerating) {
                cancelGeneration();
            } else {
                sendMessage();
            }
        });

        MaterialButton modelSelector = findViewById(R.id.modelSelector);
        if (modelSelector != null) {
            updateModelSelectorButton();
            modelSelector.setOnClickListener(v -> showModelSelection());
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, DrawerActivity.class);
                drawerLauncher.launch(intent);
                overridePendingTransition(R.anim.slide_in_left, 0);
            });
            View btnAccount = toolbar.findViewById(R.id.btn_toolbar_account);
            if (btnAccount == null) {
                btnAccount = findViewById(R.id.btn_toolbar_account);
            }
            if (btnAccount != null) {
                btnAccount.setOnClickListener(v -> {
                    Intent intent = new Intent(MainActivity.this, AccountActivity.class);
                    startActivity(intent);
                });
            }
        }

        // Voice Input (STT) Button Setup
        btnVoiceInput = findViewById(R.id.btnVoiceInput);
        if (btnVoiceInput != null) {
            btnVoiceInput.setOnClickListener(v -> {
                triggerHapticFeedback(v, android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                if (isListening) {
                    stopVoiceRecognition();
                } else {
                    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        startVoiceRecognition();
                    } else {
                        audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO);
                    }
                }
            });
        }

        // Synchronize active system persona prompt
        if (aiManager != null && preferenceManager != null) {
            aiManager.setSystemPrompt(preferenceManager.getSystemPersona());
        }

        // Listen for DownloadManager completion events.
        ContextCompat.registerReceiver(this, onDownloadComplete,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED);

        btnDownloadModel.setOnClickListener(v -> {
            if (currentDownloadId == -1 && (preferenceManager == null || preferenceManager.getActiveDownloadId() == -1)) {
                startModelDownload();
            } else {
                Toast.makeText(this, "Download already in progress", Toast.LENGTH_SHORT).show();
            }
        });

        // Check model status on startup and update the screen.
        checkModelStatus();
        updateTokenCount("");
    }

    private void updateModelSelectorButton() {
        MaterialButton modelSelector = findViewById(R.id.modelSelector);
        if (modelSelector != null) {
            String model = preferenceManager.getSelectedModel();
            modelSelector.setText(modelManager.getModelDisplayName(model));
            modelSelector.setIconResource(modelManager.getModelIconRes(model));
        }
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

            boolean success = false;
            int reason = -1;
            try (Cursor cursor = dm.query(query)) {
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
                }
            }

            // Clear the active download ID in either case.
            currentDownloadId = -1;
            preferenceManager.setActiveDownloadId(-1);

            if (success) {
                Log.d(TAG_DOWNLOAD, "Download succeeded, checking model...");
                // Toast.makeText(context, "Model downloaded!", Toast.LENGTH_SHORT).show();
                checkModelStatus(); // Load the model and refresh UI state.
            } else {
                Log.e(TAG_DOWNLOAD, "Download failed or was cancelled, reason: " + reason);
                // Toast.makeText(context, "Download failed (reason: " + reason + "). Please try again.", Toast.LENGTH_SHORT).show();
                setDownloadIdleState("Download failed (reason: " + reason + "). Tap to retry.");
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

                try (Cursor cursor = dm.query(query)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int bytesDownloadedCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                        int bytesTotalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                        int statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);

                        if (bytesDownloadedCol != -1 && bytesTotalCol != -1 && statusCol != -1) {
                            long downloaded = cursor.getLong(bytesDownloadedCol);
                            long total = cursor.getLong(bytesTotalCol);
                            int status = cursor.getInt(statusCol);

                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                stopProgressPolling();
                                currentDownloadId = -1;
                                preferenceManager.setActiveDownloadId(-1);
                                checkModelStatus();
                                return;
                            }

                            if (status == DownloadManager.STATUS_FAILED) {
                                int reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                                int reason = -1;
                                if (reasonCol != -1) {
                                    reason = cursor.getInt(reasonCol);
                                }
                                stopProgressPolling();
                                currentDownloadId = -1;
                                preferenceManager.setActiveDownloadId(-1);
                                setDownloadIdleState("Download failed (reason: " + reason + "). Tap to retry.");
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
                    }
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
        String modelKey = preferenceManager.getSelectedModel();
        long activeDownload = preferenceManager != null ? preferenceManager.getActiveDownloadId() : -1;
        if (currentDownloadId != -1 || activeDownload != -1) {
            long downloadId = currentDownloadId != -1 ? currentDownloadId : activeDownload;
            currentDownloadId = downloadId;
            downloadModelCard.setVisibility(View.VISIBLE);
            downloadProgress.setVisibility(View.VISIBLE);
            btnDownloadModel.setEnabled(false);
            if (modelManager.isModelDownloaded(modelKey)) {
                btnDownloadModel.setText("Downloading Vision...");
                downloadStatusText.setText("Downloading companion vision projector...");
            } else {
                btnDownloadModel.setText("Downloading Model...");
                downloadStatusText.setText("Downloading core AI engine (" + modelManager.getModelSize(modelKey) + ")...");
            }
            startProgressPolling();
            return;
        }

        if (modelManager.isModelDownloaded()) {
            downloadModelCard.setVisibility(View.GONE);
            downloadProgress.setVisibility(View.GONE);

            String modelPath = modelManager.getModelPath();
            if (modelManager.isMmprojDownloaded(modelKey)) {
                String mmprojPath = modelManager.getMmprojPath();
                Log.d(TAG, "Model with vision projector found, loading: " + modelPath + ", mmproj: " + mmprojPath);
                aiManager.loadVisionModel(modelPath, mmprojPath);
            } else if (modelManager.isMmprojFilePresentWithCorrectSize(modelKey) && !modelManager.isMmprojVerified(modelKey)) {
                verifyModelInBackground();
            } else {
                Log.d(TAG, "Model found and verified, loading text: " + modelPath);
                aiManager.loadModel(modelPath);
            }
        } else if (modelManager.isModelFilePresentWithCorrectSize(modelKey) && !modelManager.isModelVerified(modelKey)) {
            verifyModelInBackground();
        } else {
            downloadModelCard.setVisibility(View.VISIBLE);
            downloadProgress.setVisibility(View.GONE);
            btnDownloadModel.setEnabled(true);
            btnDownloadModel.setText("Download Model");

            String sizeStr = modelManager.getModelSize(modelKey);
            downloadStatusText.setText("Download the core AI engine (" + sizeStr + ") to start chatting offline.");
        }
    }

    private void verifyModelInBackground() {
        downloadModelCard.setVisibility(View.VISIBLE);
        btnDownloadModel.setEnabled(false);
        downloadProgress.setVisibility(View.VISIBLE);
        downloadProgress.setIndeterminate(true);
        downloadStatusText.setText("Verifying AI Model integrity...");

        new Thread(() -> {
            boolean success = modelManager.verifyModelHash();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (success) {
                    checkModelStatus();
                } else {
                    // Toast.makeText(this, "Verification failed! Corrupted model.", Toast.LENGTH_LONG).show();
                    setDownloadIdleState("Model verification failed. Please re-download.");
                }
            });
        }).start();
    }

    private void startModelDownload() {
        btnDownloadModel.setEnabled(false);
        btnDownloadModel.setText("Downloading...");
        downloadProgress.setVisibility(View.VISIBLE);
        downloadProgress.setIndeterminate(true); // Stay indeterminate until total size is available.
        downloadStatusText.setText("Starting download...");

        currentDownloadId = modelManager.downloadModel();

        if (currentDownloadId != -1) {
            Log.d(TAG_DOWNLOAD, "Download started with ID: " + currentDownloadId);
            preferenceManager.setActiveDownloadId(currentDownloadId);
            // Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
            startProgressPolling(); // Start polling for real-time byte progress.
        } else {
            Log.e(TAG_DOWNLOAD, "DownloadManager failed to enqueue");
            // Toast.makeText(this, "Failed to start download", Toast.LENGTH_SHORT).show();
            setDownloadIdleState("Could not start download. Tap to retry.");
        }
    }

    private void startMmprojDownload() {
        btnDownloadModel.setEnabled(false);
        btnDownloadModel.setText("Downloading Vision...");
        downloadModelCard.setVisibility(View.VISIBLE);
        downloadProgress.setVisibility(View.VISIBLE);
        downloadProgress.setIndeterminate(true);
        downloadStatusText.setText("Starting companion vision projector download...");

        currentDownloadId = modelManager.downloadMmproj();
        if (currentDownloadId != -1) {
            Log.d(TAG_DOWNLOAD, "Vision projector download started with ID: " + currentDownloadId);
            preferenceManager.setActiveDownloadId(currentDownloadId);
            startProgressPolling();
        } else {
            Log.e(TAG_DOWNLOAD, "DownloadManager failed to enqueue mmproj");
            setDownloadIdleState("Could not start vision download. Tap to retry.");
        }
    }

    private void setupSuggestions() {
        View cardPlan = findViewById(R.id.cardPlanDay);
        View cardImprove = findViewById(R.id.cardImproveUI);

        if (cardPlan != null) cardPlan.setOnClickListener(v -> fillAndSend("Help me plan my day."));
        if (cardImprove != null) cardImprove.setOnClickListener(v -> fillAndSend("How can I improve my app's UI?"));
    }

    private void fillAndSend(String text) {
        messageInput.setText(text);
        messageInput.setSelection(text.length()); // Move cursor to the end
        messageInput.requestFocus();
    }

    private void showModelSelection() {
        long activeId = preferenceManager.getActiveDownloadId();
        if (activeId != -1 || currentDownloadId != -1) {
            Toast.makeText(this, "Cannot change model while a download is in progress.", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_model_selection, null);
        dialog.setContentView(view);

        androidx.recyclerview.widget.RecyclerView recycler = view.findViewById(R.id.recycler_model_selection);
        recycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));

        List<ModelItem> modelItems = modelManager.getAvailableModels();

        final ModelAdapter[] adapterHolder = new ModelAdapter[1];
        adapterHolder[0] = new ModelAdapter(modelItems, preferenceManager.getSelectedModel(), item -> {
            long currentActiveId = preferenceManager.getActiveDownloadId();
            if (currentActiveId != -1 || currentDownloadId != -1) {
                Toast.makeText(this, "Cannot change model while a download is in progress.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                return;
            }
            preferenceManager.setSelectedModel(item.getKey());
            if (aiManager != null) {
                aiManager.setSystemPrompt(preferenceManager.getSystemPersona());
            }
            updateModelSelectorButton();
            checkModelStatus();
            dialog.dismiss();
        });
        recycler.setAdapter(adapterHolder[0]);

        dialog.show();
    }

    private void loadSession(String sessionId) {
        currentSessionId = sessionId;
        dbExecutor.execute(() -> {
            String title = historyManager.getSessionTitle(sessionId);
            List<Message> messages = historyManager.getMessages(sessionId);
            boolean manual = preferenceManager.isSessionTitleManual(sessionId);
            runOnUiThread(() -> {
                // FIX (reopened sessions never got an AI title): sendMessage()
                // persists a FALLBACK title (truncated first message) from the
                // very first save, so the DB title is non-empty long before an
                // AI title exists. Treating that fallback as a real title made
                // isInitial=false forever after a session reload, so reopened
                // chats only ever got drift checks against a garbage title.
                // Detect the fallback and keep the in-memory title null so the
                // initial AI title can still be generated.
                if (!manual && isFallbackTitle(title, messages)) {
                    currentSessionTitle = null;
                } else {
                    currentSessionTitle = title;
                }
                messageList.clear();
                messageList.addAll(messages);
                chatAdapter.notifyDataSetChanged();

                if (messageList.isEmpty()) {
                    startNewChat();
                } else {
                    if (welcomeContainer != null) welcomeContainer.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);

                    // Reset scroll state and jump to bottom for loaded sessions
                    isUserScrolledUp = false;
                    recyclerView.scrollToPosition(messageList.size() - 1);

                    // Sync AI history with loaded messages
                    aiManager.setHistory(deepCopyMessageList(messageList));
                    updateTokenCount("");
                }
            });
        });
    }

    private void startNewChat() {
        messageList.clear();
        chatAdapter.notifyDataSetChanged();
        if (welcomeContainer != null) {
            welcomeContainer.setVisibility(View.VISIBLE);
        }
        recyclerView.setVisibility(View.GONE);
        currentSessionId = String.valueOf(System.currentTimeMillis());
        currentSessionTitle = null;
        isUserScrolledUp = false;
        if (fabScrollToBottom != null) {
            fabScrollToBottom.setVisibility(View.GONE);
        }
        // Also clear AI history
        aiManager.clearHistory();
        updateTokenCount("");
    }

    private String getActiveSessionTitle() {
        if (currentSessionTitle != null && !currentSessionTitle.isEmpty()) {
            return currentSessionTitle;
        }
        if (conversationAnalyzer != null && !messageList.isEmpty()) {
            return conversationAnalyzer.generateFallbackTitle(messageList.get(0).getText());
        }
        return "";
    }

    /**
     * True if the stored title is just the auto-derived fallback (truncated
     * first user message) rather than an AI-generated or manual title.
     */
    private boolean isFallbackTitle(String title, List<Message> messages) {
        if (title == null || title.isEmpty()) return true;
        for (Message m : messages) {
            if (m.getType() == Message.TYPE_USER) {
                return title.equals(conversationAnalyzer.generateFallbackTitle(m.getText()));
            }
        }
        return false;
    }

    /** Resets the download card to idle/error state without hiding it. */
    private void setDownloadIdleState(String statusMessage) {
        String modelKey = preferenceManager != null ? preferenceManager.getSelectedModel() : "";
        btnDownloadModel.setEnabled(true);
        if ("vision".equals(modelKey) && modelManager.isModelFilePresentWithCorrectSize("vision") && modelManager.isModelVerified("vision")) {
            btnDownloadModel.setText("Download Projector");
        } else {
            btnDownloadModel.setText("Download Model");
        }
        downloadProgress.setVisibility(View.GONE);
        downloadStatusText.setText(statusMessage);
    }

    // Send a user message and render the model response.

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        String imgPath = selectedImagePath;
        String docName = attachedDocName;
        String docText = attachedDocText;
        String docRenderedImage = attachedDocRenderedImage;

        if (text.isEmpty() && (imgPath == null || imgPath.isEmpty()) && (docText == null || docText.isEmpty())) return;

        View sendButton = findViewById(R.id.sendButton);
        triggerHapticFeedback(sendButton != null ? sendButton : messageInput, android.view.HapticFeedbackConstants.KEYBOARD_TAP);

        if (welcomeContainer != null) {
            welcomeContainer.setVisibility(View.GONE);
        }
        recyclerView.setVisibility(View.VISIBLE);

        // Reset scroll state: user just sent a message, so they expect to see it
        isUserScrolledUp = false;

        String promptToSend = text;
        if (docText != null && !docText.isEmpty()) {
            promptToSend = "[Document: " + (docName != null ? docName : "Attached File") + "]\n```\n" + docText + "\n```\n\n" + (text.isEmpty() ? "Please analyze and summarize this document." : text);
        }

        // If vision model is active and document has a rendered page image, pass it to vision inference
        String nativeImagePath = imgPath;
        if (nativeImagePath == null && docRenderedImage != null && modelManager.isVisionModel()) {
            nativeImagePath = docRenderedImage;
        }

        if (nativeImagePath != null && !nativeImagePath.isEmpty()) {
            String modelKey = preferenceManager.getSelectedModel();
            if (!modelManager.isMmprojDownloaded(modelKey)) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Vision Projector Required")
                        .setMessage("To analyze images & OCR with " + modelManager.getModelDisplayName(modelKey) + ", download the companion vision projector (" + modelManager.getMmprojSize(modelKey) + ").")
                        .setPositiveButton("Download Vision", (d, w) -> {
                            startMmprojDownload();
                        })
                        .setNeutralButton("Send Text Only", (d, w) -> {
                            clearSelectedImage();
                            clearSelectedDocument();
                            sendMessage();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            } else if (!aiManager.isVisionModel()) {
                aiManager.loadVisionModel(modelManager.getModelPath(), modelManager.getMmprojPath());
            }
        }

        Message userMsg = new Message(text, Message.TYPE_USER, imgPath, docName);
        messageList.add(userMsg);
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.scrollToPosition(messageList.size() - 1);

        messageInput.setText("");
        clearSelectedImage();
        clearSelectedDocument();

        // Save session and messages (asynchronously on background thread with copy of list)
        String title = getActiveSessionTitle();
        List<Message> copyListForDb = deepCopyMessageList(messageList);
        dbExecutor.execute(() -> {
            historyManager.saveSession(new ChatSession(currentSessionId, title, System.currentTimeMillis()), copyListForDb);
        });

        setGeneratingState(true);

        String activeModelName = modelManager.getCurrentModelDisplayName();

        Message typingMessage = new Message("", Message.TYPE_TYPING);
        typingMessage.setModelName(activeModelName);
        messageList.add(typingMessage);
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.scrollToPosition(messageList.size() - 1);
        Log.d(TAG_CHAT, "User message: '" + promptToSend + "' (image: " + nativeImagePath + ", doc: " + docName + ")");

        long startTime = System.currentTimeMillis();
        final String finalUserPrompt = text;
        aiManager.generateResponse(promptToSend, nativeImagePath, new AIManager.ResponseCallback() {
            @Override
            public void onResponse(String response) {
                setGeneratingState(false);
                long duration = System.currentTimeMillis() - startTime;
                Log.d(TAG_CHAT, "AI response (" + duration + "ms): " + response);
                int index = messageList.indexOf(typingMessage);
                if (index != -1) {
                    Message aiMsg = new Message(response, Message.TYPE_AI);
                    aiMsg.setModelName(activeModelName);
                    messageList.set(index, aiMsg);
                    chatAdapter.notifyItemChanged(index);
                    smartScrollToBottom();

                    // Save history after AI response (asynchronously on background thread with copy of list)
                    String title = getActiveSessionTitle();
                    List<Message> copyListForDb2 = deepCopyMessageList(messageList);
                    dbExecutor.execute(() -> {
                        historyManager.saveSession(new ChatSession(currentSessionId, title, System.currentTimeMillis()), copyListForDb2);
                    });
                    updateTokenCount("");

                    // Trigger Auto-Title logic (initial or drift check)
                    generateAutoTitle();

                    // Trigger Memory Extraction
                    // Only trigger if the user prompt is long enough to potentially contain a fact and is not a question/general query
                    if (isEligibleForMemoryExtraction(finalUserPrompt)) {
                        checkAndExtractMemory(finalUserPrompt, messageList.get(index), index);
                    }
                }
            }

            @Override
            public void onToken(String token) {
                int index = messageList.indexOf(typingMessage);
                if (index != -1) {
                    Message msg = messageList.get(index);
                    // If it's still marked as typing, change it to AI type on first token
                    if (msg.getType() == Message.TYPE_TYPING) {
                        msg.setType(Message.TYPE_AI);
                        msg.setModelName(activeModelName);
                        triggerHapticFeedback(recyclerView, android.view.HapticFeedbackConstants.CONFIRM);
                    }
                    msg.setText(msg.getText() + token);

                    // Use efficient partial-bind update instead of full notifyItemChanged
                    chatAdapter.updateStreamingText(index);
                    smartScrollToBottom();
                }
            }

            @Override
            public void onContextDropped() {
                // runOnUiThread(() -> Toast.makeText(MainActivity.this, "Older context dropped to fit window", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void regenerateResponse(int position) {
        if (messageList.isEmpty() || isGenerating) return;
        if (position < 1 || position >= messageList.size()) return;

        Message targetMsg = messageList.get(position);
        if (targetMsg.getType() != Message.TYPE_AI) return;

        Message userMsg = messageList.get(position - 1);
        if (userMsg.getType() != Message.TYPE_USER) return;

        String promptText = userMsg.getText();
        String imgPath = userMsg.getImageUri();

        // Remove the target AI message and all subsequent messages in the list
        int originalSize = messageList.size();
        int numToRemove = originalSize - position;
        for (int k = 0; k < numToRemove; k++) {
            messageList.remove(position);
        }
        chatAdapter.notifyItemRangeRemoved(position, numToRemove);

        // Sync AI history with messageList (which now ends at the user prompt at position - 1)
        aiManager.setHistory(deepCopyMessageList(messageList));

        // Add typing indicator
        String activeModelName = modelManager.getCurrentModelDisplayName();

        Message typingMessage = new Message("", Message.TYPE_TYPING);
        typingMessage.setModelName(activeModelName);
        messageList.add(typingMessage);
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.scrollToPosition(messageList.size() - 1);
        updateTokenCount("");

        setGeneratingState(true);

        long startTime = System.currentTimeMillis();
        aiManager.generateResponse(promptText, imgPath, new AIManager.ResponseCallback() {
            @Override
            public void onResponse(String response) {
                setGeneratingState(false);
                long duration = System.currentTimeMillis() - startTime;
                Log.d(TAG_CHAT, "AI response (" + duration + "ms): " + response);
                int index = messageList.indexOf(typingMessage);
                if (index != -1) {
                    Message aiMsg = new Message(response, Message.TYPE_AI);
                    aiMsg.setModelName(activeModelName);
                    messageList.set(index, aiMsg);
                    chatAdapter.notifyItemChanged(index);
                    smartScrollToBottom();

                    // Save history after AI response (asynchronously on background thread with copy of list)
                    String title = getActiveSessionTitle();
                    List<Message> copyListForDb = deepCopyMessageList(messageList);
                    dbExecutor.execute(() -> {
                        historyManager.saveSession(new ChatSession(currentSessionId, title, System.currentTimeMillis()), copyListForDb);
                    });
                    updateTokenCount("");

                    // Trigger Auto-Title logic (initial or drift check)
                    generateAutoTitle();

                    // Trigger Memory Extraction
                    if (isEligibleForMemoryExtraction(promptText)) {
                        checkAndExtractMemory(promptText, messageList.get(index), index);
                    }
                }
            }

            @Override
            public void onToken(String token) {
                int index = messageList.indexOf(typingMessage);
                if (index != -1) {
                    Message msg = messageList.get(index);
                    if (msg.getType() == Message.TYPE_TYPING) {
                        msg.setType(Message.TYPE_AI);
                        msg.setModelName(activeModelName);
                        triggerHapticFeedback(recyclerView, android.view.HapticFeedbackConstants.CONFIRM);
                    }
                    msg.setText(msg.getText() + token);
                    chatAdapter.updateStreamingText(index);
                    smartScrollToBottom();
                }
            }

            @Override
            public void onContextDropped() {
                // runOnUiThread(() -> Toast.makeText(MainActivity.this, "Older context dropped to fit window", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showAttachmentOptions() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.CustomBottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_attachment_picker, null);
        dialog.setContentView(view);

        View btnImage = view.findViewById(R.id.btn_attach_image_option);
        View btnDoc = view.findViewById(R.id.btn_attach_doc_option);

        if (btnImage != null) {
            btnImage.setOnClickListener(v -> {
                dialog.dismiss();
                launchImagePicker();
            });
        }

        if (btnDoc != null) {
            btnDoc.setOnClickListener(v -> {
                dialog.dismiss();
                launchDocumentPicker();
            });
        }

        dialog.show();
    }

    private void launchDocumentPicker() {
        try {
            documentPickerLauncher.launch(new String[]{
                    "text/*",
                    "application/pdf",
                    "application/json",
                    "application/xml",
                    "application/octet-stream"
            });
        } catch (Exception e) {
            // Toast.makeText(this, "Unable to open document picker", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSelectedDocument(Uri uri) {
        if (uri == null) return;
        if (tvDocName != null) {
            tvDocName.setText("Reading document...");
        }
        if (layoutDocumentPreview != null) {
            layoutDocumentPreview.setVisibility(View.VISIBLE);
        }
        clearSelectedImage(); // keep either image or document

        dbExecutor.execute(() -> {
            DocumentHelper.DocumentInfo info = DocumentHelper.parseDocument(MainActivity.this, uri);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (info != null && info.content != null && !info.content.trim().isEmpty()) {
                    attachedDocName = info.name;
                    attachedDocText = info.content;
                    attachedDocRenderedImage = info.renderedImagePath;

                    if (tvDocName != null) {
                        String label = info.name + (info.size.isEmpty() ? "" : " • " + info.size);
                        tvDocName.setText(label);
                    }
                    if (layoutDocumentPreview != null) {
                        layoutDocumentPreview.setVisibility(View.VISIBLE);
                    }
                } else {
                    clearSelectedDocument();
                }
            });
        });
    }

    private void clearSelectedDocument() {
        attachedDocName = null;
        attachedDocText = null;
        attachedDocRenderedImage = null;
        if (layoutDocumentPreview != null) {
            layoutDocumentPreview.setVisibility(View.GONE);
        }
    }

    private void launchImagePicker() {
        try {
            photoPickerLauncher.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                legacyPickerLauncher.launch(intent);
            } catch (Exception ex) {
                // Toast.makeText(this, "Unable to open image picker", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleSelectedImage(Uri uri) {
        try {
            File imagesDir = new File(getCacheDir(), "images");
            if (!imagesDir.exists()) {
                imagesDir.mkdirs();
            }
            File destFile = new File(imagesDir, "attach_" + System.currentTimeMillis() + ".jpg");

            Bitmap bitmap = null;
            int maxTarget = 720; // Fast mobile CPU inference (~3-5s), low memory, crisp OCR/vision
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(in, null, options);

                    int maxDim = Math.max(options.outWidth, options.outHeight);
                    int sampleSize = 1;
                    while (maxDim / sampleSize > maxTarget * 2) {
                        sampleSize *= 2;
                    }

                    BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
                    decodeOptions.inSampleSize = sampleSize;
                    try (InputStream in2 = getContentResolver().openInputStream(uri)) {
                        bitmap = BitmapFactory.decodeStream(in2, null, decodeOptions);
                    }
                }
            }

            if (bitmap != null) {
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                float ratio = Math.min((float) maxTarget / width, (float) maxTarget / height);
                if (ratio > 1.0f) ratio = 1.0f;
                int targetW = Math.round(width * ratio);
                int targetH = Math.round(height * ratio);
                // Ensure dimensions are multiples of 28 for Qwen2.5-VL patch grid
                targetW = Math.max(28, (targetW / 28) * 28);
                targetH = Math.max(28, (targetH / 28) * 28);
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true);
                if (scaled != bitmap) {
                    bitmap.recycle();
                    bitmap = scaled;
                }

                try (OutputStream out = new FileOutputStream(destFile)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                }
                bitmap.recycle();
            } else {
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(destFile)) {
                    if (in != null) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                }
            }

            selectedImagePath = destFile.getAbsolutePath();
            clearSelectedDocument();
            if (ivComposerPreview != null) {
                ivComposerPreview.setImageURI(Uri.fromFile(destFile));
            }
            if (layoutImagePreview != null) {
                layoutImagePreview.setVisibility(View.VISIBLE);
            }

            // If vision projector is already downloaded, ensure vision model is loaded
            String modelKey = preferenceManager.getSelectedModel();
            if (modelManager.isMmprojDownloaded(modelKey)) {
                if (!aiManager.isVisionModel() && modelManager.isModelDownloaded(modelKey)) {
                    aiManager.loadVisionModel(modelManager.getModelPath(), modelManager.getMmprojPath());
                }
            } else if (modelManager.isMmprojMissing(modelKey)) {
                // Prompt user to download companion vision projector for image reasoning
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Vision Projector Required")
                        .setMessage("To analyze images & OCR with " + modelManager.getModelDisplayName(modelKey) + ", download the companion vision projector (" + modelManager.getMmprojSize(modelKey) + ").")
                        .setPositiveButton("Download Vision", (d, w) -> {
                            startMmprojDownload();
                        })
                        .setNegativeButton("Cancel", (d, w) -> clearSelectedImage())
                        .show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to cache selected image", e);
            // Toast.makeText(this, "Failed to load selected image", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearSelectedImage() {
        selectedImagePath = null;
        if (layoutImagePreview != null) {
            layoutImagePreview.setVisibility(View.GONE);
        }
        if (ivComposerPreview != null) {
            ivComposerPreview.setImageDrawable(null);
        }
    }

    private void pinMessageToMemory(String text) {
        if (text == null || text.trim().isEmpty()) return;
        final String cleanText = text.trim();
        dbExecutor.execute(() -> {
            List<Memory> memories = memoryManager.getAllMemories();
            
            // IMPROVED: Better normalization for duplicate detection (Issue 4.6)
            String normalizedNew = cleanText.toLowerCase().replaceAll("[^a-z0-9]", "");
            boolean exists = false;
            for (Memory m : memories) {
                String normalizedExisting = m.getContent().toLowerCase().replaceAll("[^a-z0-9]", "");
                if (normalizedNew.equals(normalizedExisting)) {
                    exists = true;
                    break;
                }
            }
            
            if (!exists) {
                String title = cleanText.length() > 20 ? cleanText.substring(0, 17) + "..." : cleanText;
                memories.add(new Memory(title, cleanText, true));
                memoryManager.saveMemories(memories);
                List<String> memoryStrings = memoryManager.getAllMemoryStrings();
                runOnUiThread(() -> {
                    cachedMemories.clear();
                    cachedMemories.addAll(memoryStrings);
                    aiManager.setMemories(memoryStrings);
                    // Toast.makeText(MainActivity.this, "Added to AI memory", Toast.LENGTH_SHORT).show();
                });
            } else {
                // runOnUiThread(() -> Toast.makeText(MainActivity.this, "Already in memory", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void deleteMessageAt(int position) {
        if (position >= 0 && position < messageList.size()) {
            messageList.remove(position);
            chatAdapter.notifyItemRemoved(position);

            // Save session history after deleting
            if (messageList.isEmpty()) {
                startNewChat();
            } else {
                String title = getActiveSessionTitle();
                List<Message> copyListForDb = deepCopyMessageList(messageList);
                dbExecutor.execute(() -> {
                    historyManager.saveSession(new ChatSession(currentSessionId, title, System.currentTimeMillis()), copyListForDb);
                });
                aiManager.setHistory(deepCopyMessageList(messageList));
                updateTokenCount("");
            }
            // Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show();
        }
    }

    private void setGeneratingState(boolean generating) {
        this.isGenerating = generating;
        updateSendButtonState();
        if (chatAdapter != null) {
            chatAdapter.setGenerating(generating);
        }
    }

    /**
     * FIX: this method used to read `currentSessionId` and write via
     * `updateAndSaveSessionTitle` from INSIDE the async AI callback, which
     * meant that if the user switched chats (or manually renamed the chat)
     * while the request was in flight, the result could silently get saved
     * against the WRONG session — corrupting that session's title/messages
     * in the database. See `applyGeneratedTitle` for the fix: it captures
     * the session id up front and re-validates before writing anything.
     *
     * Also fixes a duplicate-request bug: previously nothing stopped
     * multiple overlapping `generateTitle`/`detectTopicDrift` calls from
     * being queued back-to-back for the same session (e.g. rapid sends
     * while eligible). `titleGenerationInFlight` guards against that.
     */
    private void generateAutoTitle() {
        if (conversationAnalyzer == null) return;

        // Capture which session this request is for. This is re-checked
        // before any write happens, once the async AI call resolves.
        final String sessionIdAtCallTime = currentSessionId;

        // Never overwrite manual titles
        if (preferenceManager.isSessionTitleManual(sessionIdAtCallTime)) {
            Log.d(TAG, "Skipping auto-title generation: session title was manually set.");
            return;
        }

        // Avoid firing overlapping title/drift requests for the same session.
        if (titleGenerationInFlight && sessionIdAtCallTime.equals(titleGenerationSessionId)) {
            Log.d(TAG, "Skipping auto-title generation: a request is already in flight for this session.");
            return;
        }

        // Get a snapshot copy of the message list
        final List<Message> snapshot = deepCopyMessageList(messageList);

        // Check if eligible for initial title or drift re-evaluation
        final boolean isInitial = (currentSessionTitle == null || currentSessionTitle.isEmpty());
        boolean eligible = isInitial
                ? conversationAnalyzer.isEligibleForInitialTitle(snapshot)
                : conversationAnalyzer.isEligibleForDriftCheck(snapshot);

        if (!eligible) {
            return;
        }

        titleGenerationInFlight = true;
        titleGenerationSessionId = sessionIdAtCallTime;

        if (isInitial) {
            // Generate initial title
            conversationAnalyzer.generateTitle(snapshot, title -> {
                clearInFlightFlag(sessionIdAtCallTime);
                if (title != null && !title.isEmpty()) {
                    applyGeneratedTitle(sessionIdAtCallTime, title);
                }
            });
        } else {
            // Check for topic drift
            conversationAnalyzer.detectTopicDrift(snapshot, currentSessionTitle, drifted -> {
                if (drifted) {
                    Log.d(TAG, "Topic drift detected! Regenerating title.");
                    conversationAnalyzer.generateTitle(snapshot, title -> {
                        clearInFlightFlag(sessionIdAtCallTime);
                        if (title != null && !title.isEmpty()) {
                            applyGeneratedTitle(sessionIdAtCallTime, title);
                        }
                    });
                } else {
                    clearInFlightFlag(sessionIdAtCallTime);
                }
            });
        }
    }

    /**
     * FIX (in-flight flag clobber): if the user switched to session B while
     * session A's title request was still running, B's request would set the
     * flag, and A's late callback would then blindly clear it — allowing a
     * second overlapping request for B. Only the request that currently owns
     * the flag may clear it.
     */
    private void clearInFlightFlag(String sessionIdAtCallTime) {
        if (sessionIdAtCallTime.equals(titleGenerationSessionId)) {
            titleGenerationInFlight = false;
        }
    }

    /**
     * Applies an AI-generated title to a session, but only after re-verifying
     * that:
     *  - the app is still showing the SAME session the title was generated
     *    for (the user may have switched chats or started a new one while
     *    the async AI call was running), and
     *  - the session hasn't been manually renamed while the request was in
     *    flight (which should always win over an auto-generated title).
     *
     * If either check fails, the result is discarded instead of being
     * written — this is what prevents the async callback from corrupting an
     * unrelated session's title/messages in the database.
     *
     * FIX (title-generation data loss): this used to call saveSession() with
     * the message SNAPSHOT captured when the request was queued. Title
     * inference takes seconds on-device; any message the user sent in the
     * meantime was already saved by sendMessage(), and replaceSession() then
     * deleted it again when the stale snapshot was written back. It also
     * bumped the session timestamp, reordering the drawer list. We now do a
     * title-only UPDATE via renameSession().
     */
    private void applyGeneratedTitle(String sessionIdAtCallTime, String title) {
        boolean stillSameSession = sessionIdAtCallTime.equals(currentSessionId);

        // A manual rename that happened while the request was in flight
        // always wins over an auto-generated title.
        if (preferenceManager.isSessionTitleManual(sessionIdAtCallTime)) {
            Log.d(TAG, "Discarding generated title for session " + sessionIdAtCallTime
                    + ": session was manually renamed while request was in flight");
            return;
        }

        // The title was generated from a snapshot of THIS session's messages,
        // so it's still valid even if the user has since switched chats — the
        // rename-only write below can't touch any other session's data. Only
        // the in-memory title of the visible chat must be guarded.
        if (stillSameSession) {
            currentSessionTitle = title;
        }
        Log.d(TAG, "New session title set for " + sessionIdAtCallTime + ": " + title);
        dbExecutor.execute(() -> historyManager.renameSession(sessionIdAtCallTime, title));
    }

    /**
     * FIX: if the user renames the currently-open session from the drawer
     * (without switching to a different chat), `MainActivity`'s in-memory
     * `currentSessionTitle` previously stayed stale until the next full
     * reload. The next message sent would then re-save the session using
     * the stale title, silently reverting the manual rename. This re-syncs
     * from the database whenever we return to the foreground.
     */
    private void refreshSessionTitleIfManual() {
        if (currentSessionId == null) return;
        final String sessionId = currentSessionId;
        if (!preferenceManager.isSessionTitleManual(sessionId)) return;

        dbExecutor.execute(() -> {
            String dbTitle = historyManager.getSessionTitle(sessionId);
            if (dbTitle != null) {
                runOnUiThread(() -> {
                    if (sessionId.equals(currentSessionId)) {
                        currentSessionTitle = dbTitle;
                    }
                });
            }
        });
    }

    /**
     * Scrolls to reveal the bottom of the last chat message, but only when:
     *  - The user hasn't scrolled up to read older messages
     *  - The user isn't actively touching/flinging the list
     *
     * Uses scrollBy() instead of scrollToPosition() to precisely reveal the
     * bottom of tall streaming items that grow with each token.
     */
    private void smartScrollToBottom() {
        if (isUserScrolledUp || isUserTouching || messageList.isEmpty()) return;

        // Post to ensure layout pass has completed before measuring
        recyclerView.post(() -> {
            int lastPos = messageList.size() - 1;
            RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(lastPos);
            if (vh != null) {
                // Item is bound — calculate exact pixels needed to reveal its bottom
                int itemBottom = vh.itemView.getBottom();
                int rvVisibleBottom = recyclerView.getHeight() - recyclerView.getPaddingBottom();
                int scrollNeeded = itemBottom - rvVisibleBottom;
                if (scrollNeeded > 0) {
                    recyclerView.scrollBy(0, scrollNeeded);
                }
            } else {
                // Item not yet laid out — fall back to position-based scroll
                recyclerView.scrollToPosition(lastPos);
            }
        });
    }

    // Release resources when the activity is destroyed.

    @Override
    protected void onResume() {
        super.onResume();
        if (aiManager != null) {
            if (memoryManager != null) {
                dbExecutor.execute(() -> {
                    List<String> memories = memoryManager.getAllMemoryStrings();
                    runOnUiThread(() -> {
                        cachedMemories.clear();
                        cachedMemories.addAll(memories);
                        aiManager.setMemories(memories);
                        updateTokenCount(messageInput != null ? messageInput.getText().toString() : "");
                    });
                });
            }
            if (preferenceManager != null) {
                aiManager.setSystemPrompt(preferenceManager.getSystemPersona());
                aiManager.setMaxTokens(preferenceManager.getMaxTokens());
                aiManager.setTemperature(preferenceManager.getTemperature());
                aiManager.setContextWindow(preferenceManager.getContextWindow());
            }
        }

        updateModelSelectorButton();

        // FIX: catches the case where the active session's title was renamed
        // (e.g. from the drawer) without switching away from it.
        refreshSessionTitleIfManual();

        if (preferenceManager != null) {
            long activeId = preferenceManager.getActiveDownloadId();
            if (activeId != -1) {
                if (currentDownloadId == -1) {
                    currentDownloadId = activeId;
                    startProgressPolling();
                }
            } else {
                if (currentDownloadId != -1) {
                    stopProgressPolling();
                    currentDownloadId = -1;
                }
            }
        }

        checkModelStatus();

        updateTokenCount(messageInput != null ? messageInput.getText().toString() : "");
    }

    private void cancelGeneration() {
        if (isGenerating) {
            aiManager.cancelInference();
            setGeneratingState(false);

            // Finalize active message if present
            if (!messageList.isEmpty()) {
                int lastIdx = messageList.size() - 1;
                Message lastMsg = messageList.get(lastIdx);
                if (lastMsg.getType() == Message.TYPE_TYPING) {
                    if (lastMsg.getText() != null && !lastMsg.getText().trim().isEmpty()) {
                        lastMsg.setType(Message.TYPE_AI);
                        chatAdapter.notifyItemChanged(lastIdx);
                    } else {
                        messageList.remove(lastIdx);
                        chatAdapter.notifyItemRemoved(lastIdx);
                    }
                } else if (lastMsg.getType() == Message.TYPE_AI) {
                    chatAdapter.notifyItemChanged(lastIdx);
                }

                // Persist current state asynchronously
                String title = getActiveSessionTitle();
                List<Message> copyListForDb = deepCopyMessageList(messageList);
                dbExecutor.execute(() -> {
                    historyManager.saveSession(new ChatSession(currentSessionId, title, System.currentTimeMillis()), copyListForDb);
                });
            }
        }
    }

    private void updateSendButtonState() {
        ImageButton sendButton = findViewById(R.id.sendButton);
        if (sendButton != null) {
            if (isGenerating) {
                sendButton.setImageResource(R.drawable.ic_stop);
                sendButton.setContentDescription("Stop generating");
            } else {
                sendButton.setImageResource(R.drawable.ic_send);
                sendButton.setContentDescription("Send");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProgressPolling();
        if (speechRecognizer != null) {
            try {
                speechRecognizer.destroy();
            } catch (Exception ignored) {}
            speechRecognizer = null;
        }
        try {
            unregisterReceiver(onDownloadComplete);
        } catch (Exception e) {
            Log.w(TAG, "Receiver already unregistered");
        }
        // AIManager and dbExecutor are managed as application-scoped singletons by NexApplication.
    }

    private void startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            // Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    isListening = true;
                    updateVoiceButtonState();
                    // Toast.makeText(MainActivity.this, "Listening...", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    isListening = false;
                    updateVoiceButtonState();
                }

                @Override
                public void onError(int error) {
                    isListening = false;
                    updateVoiceButtonState();
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        // Toast.makeText(MainActivity.this, "Voice recognition error", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    updateVoiceButtonState();
                    if (results != null) {
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String text = matches.get(0);
                            if (text != null && !text.isEmpty()) {
                                String current = messageInput.getText() != null ? messageInput.getText().toString() : "";
                                if (!current.isEmpty() && !current.endsWith(" ")) {
                                    current += " ";
                                }
                                messageInput.setText(current + text);
                                messageInput.setSelection(messageInput.getText().length());
                            }
                        }
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {}

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        try {
            speechRecognizer.startListening(intent);
            isListening = true;
            updateVoiceButtonState();
        } catch (Exception e) {
            e.printStackTrace();
            isListening = false;
            updateVoiceButtonState();
            // Toast.makeText(this, "Failed to start microphone", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopVoiceRecognition() {
        if (speechRecognizer != null && isListening) {
            try {
                speechRecognizer.stopListening();
            } catch (Exception ignored) {}
        }
        isListening = false;
        updateVoiceButtonState();
    }

    private void updateVoiceButtonState() {
        if (btnVoiceInput != null) {
            if (isListening) {
                btnVoiceInput.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF3B30")));
                btnVoiceInput.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
            } else {
                btnVoiceInput.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1A1C22")));
                btnVoiceInput.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
            }
        }
    }

    private boolean isEligibleForMemoryExtraction(String text) {
        if (text == null) return false;
        String clean = text.trim();
        if (clean.length() <= 10) return false;

        // 1. Skip questions (ending with ?)
        if (clean.endsWith("?")) return false;

        // 2. Skip common question words & imperative command starters
        String lower = clean.toLowerCase(java.util.Locale.US);
        String[] skipStarters = {
            "what", "why", "how", "who", "where", "when", "which",
            "do", "does", "did", "is", "are", "was", "were", 
            "can", "could", "should", "would", "will", "tell me",
            "explain", "show me", "write", "compose", "generate",
            "summarize", "translate", "code", "create", "list",
            "draft", "fix", "debug", "review", "convert", "calculate",
            "solve", "help me", "give me", "find", "search"
        };
        for (String starter : skipStarters) {
            if (lower.startsWith(starter + " ") || lower.startsWith(starter + "'")) {
                return false;
            }
        }
        return true;
    }

    private List<Message> deepCopyMessageList(List<Message> original) {
        List<Message> copy = new ArrayList<>();
        if (original != null) {
            synchronized (original) {
                for (Message m : original) {
                    copy.add(new Message(m));
                }
            }
        }
        return copy;
    }

    private int estimateContextTokens(String currentInput) {
        int totalChars = 0;

        // 1. System Prompt Chars
        if (preferenceManager != null) {
            totalChars += preferenceManager.getSystemPersona().length();
        }

        // 2. Memories Chars (Read from memory cache instead of querying DB on UI thread)
        for (String memory : cachedMemories) {
            totalChars += memory.length() + 2;
        }

        // 3. Active Chat History Chars (respecting context window size)
        int contextSize = (preferenceManager != null) ? preferenceManager.getContextWindow() : 12;
        if (messageList != null) {
            java.util.List<Message> activeMsgs = new java.util.ArrayList<>();
            for (Message m : messageList) {
                if (m.getType() == Message.TYPE_USER || m.getType() == Message.TYPE_AI) {
                    activeMsgs.add(m);
                }
            }

            int startIdx = Math.max(0, activeMsgs.size() - contextSize);
            for (int i = startIdx; i < activeMsgs.size(); i++) {
                totalChars += activeMsgs.get(i).getText().length();
            }
        }

        // 4. Current Input Chars
        if (currentInput != null) {
            totalChars += currentInput.length();
        }

        return (int) Math.ceil(totalChars / 3.8);
    }

    private void updateTokenCount(String currentInput) {
        TextView tvCharCount = findViewById(R.id.tv_char_count);
        if (tvCharCount != null) {
            int estTokens = estimateContextTokens(currentInput);
            tvCharCount.setText(estTokens + " / 2048");
            tvCharCount.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Fires background memory extraction after an AI turn completes, then
     * persists any newly-extracted memory.
     *
     * FIX: previously this method mutated `messageList` / `aiMsg` and read
     * `getActiveSessionTitle()` directly from the background `dbExecutor`
     * thread, while every other part of the app treats `messageList` as
     * UI-thread-owned. That's a data race (intermittent
     * ConcurrentModificationException / corrupted adapter state), especially
     * likely while a response is still streaming into the list. It also
     * trusted a `msgIndex` captured before the async extraction round-trip,
     * which can go stale (or out of bounds) if the user deletes a message,
     * regenerates, or switches sessions before extraction finishes.
     *
     * Fix: keep all `messageList` reads/writes on the UI thread, only use
     * `dbExecutor` for the actual Room DB calls, and re-locate the message by
     * reference (`messageList.indexOf(aiMsg)`) instead of trusting the old
     * index. Also guards against the session having changed underneath us.
     */
    private void checkAndExtractMemory(String userPrompt, Message aiMsg, int msgIndex) {
        if (aiManager == null || memoryManager == null) return;

        // Capture which session this response belongs to; if the user
        // switches or deletes it before extraction finishes, we shouldn't
        // write the result into whatever session happens to be active later.
        final String sessionIdAtCallTime = currentSessionId;

        aiManager.extractMemory(new AIManager.MemoryCallback() {
            @Override
            public void onMemoryExtracted(String title, String content) {
                if (title == null || content == null) return;

                String cleanTitle = title.trim();
                String cleanContent = content.trim();

                String titleLower = cleanTitle.toLowerCase();
                String contentLower = cleanContent.toLowerCase();

                Log.d("NexMemory", "Memory extraction result: title='" + cleanTitle + "', content='" + cleanContent + "'");

                // 1. Reject NONE/null placeholders or too short content
                if (titleLower.equals("none") || contentLower.equals("none") || contentLower.length() < 3) {
                    Log.d("NexMemory", "Memory discarded: placeholder or empty ('" + cleanContent + "')");
                    return;
                }

                // 2. Reject if the memory is about the AI assistant itself
                if (contentLower.startsWith("i ") || contentLower.startsWith("nex ") || contentLower.startsWith("assistant ") || contentLower.contains("as an ai")) {
                    Log.d("NexMemory", "Memory discarded: AI self-reference ('" + cleanContent + "')");
                    return;
                }

                // 3. Reject if model hallucinated prompt instruction regurgitations
                if (titleLower.contains("system prompt") || titleLower.contains("instruction") || contentLower.contains("extract any personal")) {
                    Log.d("NexMemory", "Memory discarded: instruction leak");
                    return;
                }

                dbExecutor.execute(() -> {
                    List<Memory> memories = memoryManager.getAllMemories();
                    boolean exists = false;
                    for (Memory m : memories) {
                        if (m.getContent().equalsIgnoreCase(cleanContent)) {
                            exists = true;
                            break;
                        }
                    }
                    if (exists) {
                        Log.d("NexMemory", "Memory already exists in DB: " + cleanContent);
                        return;
                    }

                    Memory newMemory = new Memory(cleanTitle, cleanContent, false);
                    memories.add(newMemory);
                    memoryManager.saveMemories(memories);
                    Log.d("NexMemory", "Memory saved to DB successfully: " + cleanTitle + " | " + cleanContent);

                    // Update AI manager memories context dynamically in memory
                    List<String> memoryStrings = memoryManager.getAllMemoryStrings();

                    runOnUiThread(() -> {
                        cachedMemories.clear();
                        cachedMemories.addAll(memoryStrings);
                        aiManager.setMemories(memoryStrings);
                        updateTokenCount("");

                        // Re-locate the message by reference instead of trusting
                        // msgIndex, which may be stale by now (list can have
                        // shifted due to deletion, regeneration, or a session
                        // switch while extraction was running).
                        int freshIndex = messageList.indexOf(aiMsg);
                        boolean stillSameSession = sessionIdAtCallTime.equals(currentSessionId);

                        if (freshIndex != -1 && stillSameSession) {
                            // Update message visual tag (now safely on the UI thread)
                            aiMsg.setMemoryTag("Memory: " + cleanTitle);
                            chatAdapter.notifyItemChanged(freshIndex);

                            // Save history after updating tag to persist it
                            String sessionTitle = getActiveSessionTitle();
                            List<Message> copyListForDb = deepCopyMessageList(messageList);
                            dbExecutor.execute(() -> historyManager.saveSession(
                                    new ChatSession(sessionIdAtCallTime, sessionTitle, System.currentTimeMillis()),
                                    copyListForDb));
                        }
                        // If the message is gone or the session changed, the
                        // memory itself is still saved above — we just skip the
                        // now-invalid UI tag update / session write.
                    });
                });
            }
        });
    }

    private void triggerHapticFeedback(View view, int type) {
        if (preferenceManager != null && preferenceManager.isHapticFeedbackEnabled() && view != null) {
            view.performHapticFeedback(type, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }
}