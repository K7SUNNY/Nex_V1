package com.k7sunny.nexv1;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageButton;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.List;

public class MemoryActivity extends AppCompatActivity {

    private MemoryAdapter pinnedAdapter;
    private MemoryAdapter recentAdapter;
    private List<Memory> pinnedMemories;
    private List<Memory> recentMemories;
    private MemoryManager memoryManager;
    private List<Memory> masterAllMemories;
    private String currentSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_memory);

        View root = findViewById(R.id.memory_root);
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
        toolbar.setNavigationOnClickListener(v -> finish());

        memoryManager = new MemoryManager(this);
        setupRecyclers();
        setupSearch();
    }

    private void setupRecyclers() {
        masterAllMemories = memoryManager.getAllMemories();
        pinnedMemories = new ArrayList<>();
        recentMemories = new ArrayList<>();

        PreferenceManager pm = new PreferenceManager(this);
        if (!pm.isMemoryInitialized()) {
            pm.setMemoryInitialized(true);
        }

        RecyclerView pinnedRecycler = findViewById(R.id.pinnedRecycler);
        pinnedRecycler.setLayoutManager(new LinearLayoutManager(this));
        pinnedAdapter = new MemoryAdapter(pinnedMemories, this::showMemoryOptions);
        pinnedRecycler.setAdapter(pinnedAdapter);

        RecyclerView recentRecycler = findViewById(R.id.recentRecycler);
        recentRecycler.setLayoutManager(new LinearLayoutManager(this));
        recentAdapter = new MemoryAdapter(recentMemories, this::showMemoryOptions);
        recentRecycler.setAdapter(recentAdapter);

        filterMemories("");
    }

    private void saveAllToManager() {
        memoryManager.saveMemories(masterAllMemories);
    }

    private void updateEmptyState() {
        View emptyState = findViewById(R.id.layout_empty_state_memory);
        View searchBar = findViewById(R.id.search_bar_memory);
        View tvPinnedLabel = findViewById(R.id.tv_pinned_label);
        View pinnedRecycler = findViewById(R.id.pinnedRecycler);
        View tvRecentLabel = findViewById(R.id.tv_recent_label);
        View recentRecycler = findViewById(R.id.recentRecycler);

        boolean hasAnyMemory = masterAllMemories != null && !masterAllMemories.isEmpty();
        boolean hasPinned = pinnedMemories != null && !pinnedMemories.isEmpty();
        boolean hasRecent = recentMemories != null && !recentMemories.isEmpty();

        if (searchBar != null) {
            searchBar.setVisibility(hasAnyMemory ? View.VISIBLE : View.GONE);
        }

        if (!hasPinned && !hasRecent) {
            if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
            if (tvPinnedLabel != null) tvPinnedLabel.setVisibility(View.GONE);
            if (pinnedRecycler != null) pinnedRecycler.setVisibility(View.GONE);
            if (tvRecentLabel != null) tvRecentLabel.setVisibility(View.GONE);
            if (recentRecycler != null) recentRecycler.setVisibility(View.GONE);
        } else {
            if (emptyState != null) emptyState.setVisibility(View.GONE);
            
            if (tvPinnedLabel != null) tvPinnedLabel.setVisibility(hasPinned ? View.VISIBLE : View.GONE);
            if (pinnedRecycler != null) pinnedRecycler.setVisibility(hasPinned ? View.VISIBLE : View.GONE);
            
            if (tvRecentLabel != null) tvRecentLabel.setVisibility(hasRecent ? View.VISIBLE : View.GONE);
            if (recentRecycler != null) recentRecycler.setVisibility(hasRecent ? View.VISIBLE : View.GONE);
        }
    }

    private void showMemoryOptions(Memory memory, int position) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.CustomBottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_memory_options, null);
        dialog.setContentView(view);

        MaterialButton btnEdit = view.findViewById(R.id.btn_edit);
        MaterialButton btnPin = view.findViewById(R.id.btn_pin);
        MaterialButton btnCopy = view.findViewById(R.id.btn_copy);
        MaterialButton btnDelete = view.findViewById(R.id.btn_delete);

        btnPin.setText(memory.isPinned() ? R.string.unpin_memory : R.string.pin_memory);

        btnEdit.setOnClickListener(v -> {
            dialog.dismiss();
            showEditMemoryDialog(memory);
        });

        btnPin.setOnClickListener(v -> {
            memory.setPinned(!memory.isPinned());
            saveAllToManager();
            filterMemories(currentSearchQuery);
            dialog.dismiss();
        });

        btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Nex Memory", memory.getContent());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.copy_content, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnDelete.setOnClickListener(v -> {
            masterAllMemories.remove(memory);
            saveAllToManager();
            filterMemories(currentSearchQuery);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void setupSearch() {
        EditText searchInput = findViewById(R.id.search_input_memory);
        ImageButton btnClear = findViewById(R.id.btn_clear_search_memory);

        if (searchInput != null && btnClear != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentSearchQuery = s.toString();
                    btnClear.setVisibility(currentSearchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                    filterMemories(currentSearchQuery);
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            btnClear.setOnClickListener(v -> searchInput.setText(""));
        }
    }

    private void filterMemories(String query) {
        pinnedMemories.clear();
        recentMemories.clear();

        String cleanQuery = query.toLowerCase().trim();
        for (Memory m : masterAllMemories) {
            boolean matches = cleanQuery.isEmpty() ||
                    m.getTitle().toLowerCase().contains(cleanQuery) ||
                    m.getContent().toLowerCase().contains(cleanQuery);
            if (matches) {
                if (m.isPinned()) pinnedMemories.add(m);
                else recentMemories.add(m);
            }
        }

        pinnedAdapter.notifyDataSetChanged();
        recentAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void showEditMemoryDialog(Memory memory) {
        BottomSheetDialog editDialog = new BottomSheetDialog(this, R.style.CustomBottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_memory, null);
        editDialog.setContentView(view);

        TextInputEditText editTitle = view.findViewById(R.id.edit_memory_title);
        TextInputEditText editContent = view.findViewById(R.id.edit_memory_content);
        MaterialButton btnSave = view.findViewById(R.id.btn_save_memory_edit);

        if (editTitle != null && editContent != null) {
            editTitle.setText(memory.getTitle());
            editContent.setText(memory.getContent());

            if (editTitle.getText() != null) {
                editTitle.setSelection(editTitle.getText().length());
            }
        }

        btnSave.setOnClickListener(v -> {
            if (editTitle != null && editContent != null && editTitle.getText() != null && editContent.getText() != null) {
                String newTitle = editTitle.getText().toString().trim();
                String newContent = editContent.getText().toString().trim();

                if (!newTitle.isEmpty() && !newContent.isEmpty()) {
                    memory.setTitle(newTitle);
                    memory.setContent(newContent);
                    saveAllToManager();
                    filterMemories(currentSearchQuery);
                    Toast.makeText(this, "Memory updated", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Title and details cannot be empty", Toast.LENGTH_SHORT).show();
                }
            }
            editDialog.dismiss();
        });

        editDialog.show();
    }
}
