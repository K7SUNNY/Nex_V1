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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
import java.util.ArrayList;
import java.util.List;

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
    private String currentSessionId;
    private boolean isGenerating = false;
    private View fabScrollToBottom;

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
                }
            }
    );

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
        historyManager = new HistoryManager(this);
        memoryManager = new MemoryManager(this);
        preferenceManager = new PreferenceManager(this);
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

            // Update toolbar padding for top status bar
            View toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.setPadding(0, systemBars.top, 0, 0);
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
        });
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
            boolean isPro = "pro".equals(preferenceManager.getSelectedModel());
            if (isPro) {
                modelSelector.setText("Nex Pro");
                modelSelector.setIconResource(R.drawable.app_icon);
            } else {
                modelSelector.setText(R.string.nex_fast);
                modelSelector.setIconResource(R.drawable.ic_bolt);
            }
            modelSelector.setOnClickListener(v -> showModelSelection());
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, DrawerActivity.class);
                drawerLauncher.launch(intent);
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
        updateTokenCount("");
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
                Log.d(TAG_DOWNLOAD, "Download succeeded, checking model...");
                Toast.makeText(context, "Model downloaded!", Toast.LENGTH_SHORT).show();
                checkModelStatus(); // Load the model and refresh UI state.
            } else {
                Log.e(TAG_DOWNLOAD, "Download failed or was cancelled");
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
        if (modelManager.isModelFilePresentWithCorrectSize()) {
            if (modelManager.isModelVerified()) {
                String modelPath = modelManager.getModelPath();
                downloadModelCard.setVisibility(View.GONE);
                downloadProgress.setVisibility(View.GONE);

                Log.d(TAG, "Model found and verified, loading: " + modelPath);
                aiManager.loadModel(modelPath);

                Toast.makeText(this, "AI model ready!", Toast.LENGTH_SHORT).show();
            } else {
                verifyModelInBackground();
            }
        } else {
            downloadModelCard.setVisibility(View.VISIBLE);
            downloadProgress.setVisibility(View.GONE);
            btnDownloadModel.setEnabled(true);
            btnDownloadModel.setText("Download Model");
            downloadStatusText.setText("Download the core AI engine (~400MB) to start chatting offline.");
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
                    Toast.makeText(this, "Verification failed! Corrupted model.", Toast.LENGTH_LONG).show();
                    setDownloadIdleState("Model verification failed. Please re-download.");
                }
            });
        }).start();
    }

    private void startModelDownload() {
        btnDownloadModel.setEnabled(false);
        downloadProgress.setVisibility(View.VISIBLE);
        downloadProgress.setIndeterminate(true); // Stay indeterminate until total size is available.
        downloadStatusText.setText("Starting download...");

        currentDownloadId = modelManager.downloadModel();

        if (currentDownloadId != -1) {
            Log.d(TAG_DOWNLOAD, "Download started with ID: " + currentDownloadId);
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
            startProgressPolling(); // Start polling for real-time byte progress.
        } else {
            Log.e(TAG_DOWNLOAD, "DownloadManager failed to enqueue");
            Toast.makeText(this, "Failed to start download", Toast.LENGTH_SHORT).show();
            setDownloadIdleState("Could not start download. Tap to retry.");
        }
    }

    private void setupSuggestions() {
        View chipFast = findViewById(R.id.chipFastChat);
        View chipMemory = findViewById(R.id.chipSavedMemory);
        View chipCode = findViewById(R.id.chipCodeReady);
        View cardPlan = findViewById(R.id.cardPlanDay);
        View cardImprove = findViewById(R.id.cardImproveUI);

        if (chipFast != null) chipFast.setOnClickListener(v -> fillAndSend("Let's start a fast chat."));
        if (chipMemory != null) chipMemory.setOnClickListener(v -> fillAndSend("Recall my saved memories."));
        if (chipCode != null) chipCode.setOnClickListener(v -> fillAndSend("Help me write some code."));
        if (cardPlan != null) cardPlan.setOnClickListener(v -> fillAndSend("Help me plan my day."));
        if (cardImprove != null) cardImprove.setOnClickListener(v -> fillAndSend("How can I improve my app's UI?"));
    }

    private void fillAndSend(String text) {
        messageInput.setText(text);
        messageInput.setSelection(text.length()); // Move cursor to the end
        messageInput.requestFocus();
    }

    private void showModelSelection() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_model_selection, null);
        dialog.setContentView(view);

        MaterialCardView cardFast = view.findViewById(R.id.card_nex_fast);
        MaterialCardView cardPro = view.findViewById(R.id.card_nex_pro);
        View checkFast = view.findViewById(R.id.check_fast);
        View checkPro = view.findViewById(R.id.check_pro);

        MaterialButton modelSelector = findViewById(R.id.modelSelector);
        boolean isProSelected = "pro".equals(preferenceManager.getSelectedModel());

        checkFast.setVisibility(isProSelected ? View.GONE : View.VISIBLE);
        checkPro.setVisibility(isProSelected ? View.VISIBLE : View.GONE);
        cardFast.setStrokeColor(isProSelected ? 0x1AFFFFFF : 0xFF007AFF);
        cardPro.setStrokeColor(isProSelected ? 0xFF007AFF : 0x1AFFFFFF);

        cardFast.setOnClickListener(v -> {
            modelSelector.setText(R.string.nex_fast);
            modelSelector.setIconResource(R.drawable.ic_bolt);
            preferenceManager.setSelectedModel("fast");
            if (aiManager != null) {
                aiManager.setSystemPrompt(preferenceManager.getSystemPersona());
            }
            dialog.dismiss();
        });

        cardPro.setOnClickListener(v -> {
            modelSelector.setText("Nex Pro");
            modelSelector.setIconResource(R.drawable.app_icon);
            preferenceManager.setSelectedModel("pro");
            if (aiManager != null) {
                aiManager.setSystemPrompt(preferenceManager.getSystemPersona());
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void loadSession(String sessionId) {
        currentSessionId = sessionId;
        messageList.clear();
        messageList.addAll(historyManager.getMessages(sessionId));
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
            aiManager.setHistory(messageList);
            updateTokenCount("");
        }
    }

    private void startNewChat() {
        messageList.clear();
        chatAdapter.notifyDataSetChanged();
        if (welcomeContainer != null) {
            welcomeContainer.setVisibility(View.VISIBLE);
        }
        recyclerView.setVisibility(View.GONE);
        currentSessionId = String.valueOf(System.currentTimeMillis());
        isUserScrolledUp = false;
        if (fabScrollToBottom != null) {
            fabScrollToBottom.setVisibility(View.GONE);
        }
        // Also clear AI history
        aiManager.clearHistory();
        updateTokenCount("");
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

        View sendButton = findViewById(R.id.sendButton);
        triggerHapticFeedback(sendButton != null ? sendButton : messageInput, android.view.HapticFeedbackConstants.KEYBOARD_TAP);

        if (welcomeContainer != null) {
            welcomeContainer.setVisibility(View.GONE);
        }
        recyclerView.setVisibility(View.VISIBLE);

        // Reset scroll state: user just sent a message, so they expect to see it
        isUserScrolledUp = false;

        Message userMsg = new Message(text, Message.TYPE_USER);
        messageList.add(userMsg);
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.scrollToPosition(messageList.size() - 1);

        // Save session and messages
        String title = messageList.get(0).getText();
        if (title.length() > 30) title = title.substring(0, 27) + "...";
        historyManager.saveSession(new ChatSession(currentSessionId, title, System.currentTimeMillis()), messageList);

        messageInput.setText("");

        setGeneratingState(true);

        Message typingMessage = new Message("", Message.TYPE_TYPING);
        messageList.add(typingMessage);
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.scrollToPosition(messageList.size() - 1);
        Log.d(TAG_CHAT, "User message: " + text);

        long startTime = System.currentTimeMillis();
        aiManager.generateResponse(text, new AIManager.ResponseCallback() {
            @Override
            public void onResponse(String response) {
                setGeneratingState(false);
                long duration = System.currentTimeMillis() - startTime;
                Log.d(TAG_CHAT, "AI response (" + duration + "ms): " + response);
                int index = messageList.indexOf(typingMessage);
                if (index != -1) {
                    messageList.set(index, new Message(response, Message.TYPE_AI));
                    chatAdapter.notifyItemChanged(index);
                    smartScrollToBottom();

                    // Save history after AI response
                    String title = messageList.get(0).getText();
                    if (title.length() > 30) title = title.substring(0, 27) + "...";
                    historyManager.saveSession(new ChatSession(currentSessionId, title, System.currentTimeMillis()), messageList);
                    updateTokenCount("");

                    // Trigger Auto-Title if this is the first exchange
                    if (messageList.size() == 2) {
                        generateAutoTitle(text, response);
                    }

                    // Trigger Memory Extraction
                    checkAndExtractMemory(text, response, messageList.get(index), index);
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
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Older context dropped to fit window", Toast.LENGTH_SHORT).show());
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

        // Remove the target AI message and all subsequent messages in the list
        int originalSize = messageList.size();
        int numToRemove = originalSize - position;
        for (int k = 0; k < numToRemove; k++) {
            messageList.remove(position);
        }
        chatAdapter.notifyItemRangeRemoved(position, numToRemove);

        // Sync AI history with messageList (which now ends at the user prompt at position - 1)
        aiManager.setHistory(messageList);

        // Add typing indicator
        Message typingMessage = new Message("", Message.TYPE_TYPING);
        messageList.add(typingMessage);
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.scrollToPosition(messageList.size() - 1);
        updateTokenCount("");

        setGeneratingState(true);

        long startTime = System.currentTimeMillis();
        aiManager.generateResponse(promptText, new AIManager.ResponseCallback() {
            @Override
            public void onResponse(String response) {
                setGeneratingState(false);
                long duration = System.currentTimeMillis() - startTime;
                Log.d(TAG_CHAT, "AI response (" + duration + "ms): " + response);
                int index = messageList.indexOf(typingMessage);
                if (index != -1) {
                    messageList.set(index, new Message(response, Message.TYPE_AI));
                    chatAdapter.notifyItemChanged(index);
                    smartScrollToBottom();

                    // Save history after AI response
                    String title = messageList.get(0).getText();
                    if (title.length() > 30) title = title.substring(0, 27) + "...";
                    historyManager.saveSession(new ChatSession(currentSessionId, title, System.currentTimeMillis()), messageList);
                    updateTokenCount("");

                    // Trigger Auto-Title if this is the first exchange
                    if (messageList.size() == 2) {
                        generateAutoTitle(promptText, response);
                    }

                    // Trigger Memory Extraction
                    checkAndExtractMemory(promptText, response, messageList.get(index), index);
                }
            }

            @Override
            public void onToken(String token) {
                int index = messageList.indexOf(typingMessage);
                if (index != -1) {
                    Message msg = messageList.get(index);
                    if (msg.getType() == Message.TYPE_TYPING) {
                        msg.setType(Message.TYPE_AI);
                        triggerHapticFeedback(recyclerView, android.view.HapticFeedbackConstants.CONFIRM);
                    }
                    msg.setText(msg.getText() + token);
                    chatAdapter.updateStreamingText(index);
                    smartScrollToBottom();
                }
            }

            @Override
            public void onContextDropped() {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Older context dropped to fit window", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void pinMessageToMemory(String text) {
        if (text == null || text.trim().isEmpty()) return;
        new Thread(() -> {
            List<Memory> memories = memoryManager.getAllMemories();
            boolean exists = false;
            for (Memory m : memories) {
                if (m.getContent().equalsIgnoreCase(text.trim())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                String title = text.length() > 20 ? text.substring(0, 17) + "..." : text;
                memories.add(new Memory(title, text.trim(), true));
                memoryManager.saveMemories(memories);
                aiManager.setMemories(memoryManager.getPinnedMemoryStrings());
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Added to AI memory", Toast.LENGTH_SHORT).show());
            } else {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Already in memory", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void deleteMessageAt(int position) {
        if (position >= 0 && position < messageList.size()) {
            messageList.remove(position);
            chatAdapter.notifyItemRemoved(position);

            // Save session history after deleting
            if (messageList.isEmpty()) {
                startNewChat();
            } else {
                String title = messageList.get(0).getText();
                if (title.length() > 30) title = title.substring(0, 27) + "...";
                historyManager.saveSession(new ChatSession(currentSessionId, title, System.currentTimeMillis()), messageList);
                aiManager.setHistory(messageList);
                updateTokenCount("");
            }
            Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show();
        }
    }

    private void setGeneratingState(boolean generating) {
        this.isGenerating = generating;
        updateSendButtonState();
        if (chatAdapter != null) {
            chatAdapter.setGenerating(generating);
        }
    }

    private void generateAutoTitle(String userPrompt, String aiResponse) {
        aiManager.generateTitle(userPrompt, aiResponse, new AIManager.TitleCallback() {
            @Override
            public void onTitleGenerated(String title) {
                if (title != null && !title.isEmpty()) {
                    // Sanitize title: remove wrapping quotes, leading/trailing punctuation or "Title:" prefix
                    String cleanTitle = title.trim();
                    if (cleanTitle.startsWith("\"") && cleanTitle.endsWith("\"")) {
                        cleanTitle = cleanTitle.substring(1, cleanTitle.length() - 1);
                    }
                    if (cleanTitle.startsWith("'") && cleanTitle.endsWith("'")) {
                        cleanTitle = cleanTitle.substring(1, cleanTitle.length() - 1);
                    }
                    if (cleanTitle.endsWith(".")) {
                        cleanTitle = cleanTitle.substring(0, cleanTitle.length() - 1);
                    }
                    cleanTitle = cleanTitle.trim();

                    if (!cleanTitle.isEmpty()) {
                        Log.d("NexUI", "Auto-generated title: " + cleanTitle);
                        // Save session with the new title
                        historyManager.saveSession(
                            new ChatSession(currentSessionId, cleanTitle, System.currentTimeMillis()),
                            messageList
                        );
                    }
                }
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
                aiManager.setMemories(memoryManager.getPinnedMemoryStrings());
            }
            if (preferenceManager != null) {
                aiManager.setSystemPrompt(preferenceManager.getSystemPersona());
                aiManager.setMaxTokens(preferenceManager.getMaxTokens());
                aiManager.setTemperature(preferenceManager.getTemperature());
                aiManager.setContextWindow(preferenceManager.getContextWindow());
            }
        }
        updateTokenCount(messageInput != null ? messageInput.getText().toString() : "");
    }

    private void cancelGeneration() {
        if (isGenerating) {
            aiManager.cancelInference();
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
        try {
            unregisterReceiver(onDownloadComplete);
        } catch (Exception e) {
            Log.w(TAG, "Receiver already unregistered");
        }
        aiManager.release(); // Free native model resources.
    }

    private int estimateContextTokens(String currentInput) {
        int totalChars = 0;

        // 1. System Prompt Chars
        if (preferenceManager != null) {
            totalChars += preferenceManager.getSystemPersona().length();
        }

        // 2. Pinned Memories Chars
        if (memoryManager != null) {
            java.util.List<String> memories = memoryManager.getPinnedMemoryStrings();
            if (memories != null) {
                for (String memory : memories) {
                    totalChars += memory.length() + 2;
                }
            }
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

    private void checkAndExtractMemory(String userPrompt, String aiResponse, Message aiMsg, int msgIndex) {
        if (aiManager == null || memoryManager == null) return;
        aiManager.extractMemory(userPrompt, aiResponse, new AIManager.MemoryCallback() {
            @Override
            public void onMemoryExtracted(String title, String content) {
                if (title != null && content != null) {
                    new Thread(() -> {
                        List<Memory> memories = memoryManager.getAllMemories();
                        boolean exists = false;
                        for (Memory m : memories) {
                            if (m.getContent().equalsIgnoreCase(content.trim())) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            Memory newMemory = new Memory(title, content.trim(), false);
                            memories.add(newMemory);
                            memoryManager.saveMemories(memories);

                            // Update message visual tag
                            aiMsg.setMemoryTag("Memory: " + title);

                            // Save history after updating tag to persist it
                            String sessionTitle = messageList.get(0).getText();
                            if (sessionTitle.length() > 30) sessionTitle = sessionTitle.substring(0, 27) + "...";
                            historyManager.saveSession(new ChatSession(currentSessionId, sessionTitle, System.currentTimeMillis()), messageList);

                            runOnUiThread(() -> {
                                chatAdapter.notifyItemChanged(msgIndex);
                            });
                        }
                    }).start();
                }
            }
        });
    }

    private void triggerHapticFeedback(View view, int type) {
        if (preferenceManager != null && preferenceManager.isHapticFeedbackEnabled() && view != null) {
            view.performHapticFeedback(type, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }
}
