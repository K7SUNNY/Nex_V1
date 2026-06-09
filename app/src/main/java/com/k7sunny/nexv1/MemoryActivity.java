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
import java.util.ArrayList;
import java.util.List;

public class MemoryActivity extends AppCompatActivity {

    private MemoryAdapter pinnedAdapter;
    private MemoryAdapter recentAdapter;
    private List<Memory> pinnedMemories;
    private List<Memory> recentMemories;
    private MemoryManager memoryManager;

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
    }

    private void setupRecyclers() {
        List<Memory> allMemories = memoryManager.getAllMemories();
        pinnedMemories = new ArrayList<>();
        recentMemories = new ArrayList<>();

        PreferenceManager pm = new PreferenceManager(this);
        if (!pm.isMemoryInitialized()) {
            pm.setMemoryInitialized(true);
        }
        for (Memory m : allMemories) {
            if (m.isPinned()) pinnedMemories.add(m);
            else recentMemories.add(m);
        }

        RecyclerView pinnedRecycler = findViewById(R.id.pinnedRecycler);
        pinnedRecycler.setLayoutManager(new LinearLayoutManager(this));
        pinnedAdapter = new MemoryAdapter(pinnedMemories, this::showMemoryOptions);
        pinnedRecycler.setAdapter(pinnedAdapter);

        RecyclerView recentRecycler = findViewById(R.id.recentRecycler);
        recentRecycler.setLayoutManager(new LinearLayoutManager(this));
        recentAdapter = new MemoryAdapter(recentMemories, this::showMemoryOptions);
        recentRecycler.setAdapter(recentAdapter);

        updateEmptyState();
    }

    private void saveAllToManager() {
        List<Memory> all = new ArrayList<>(pinnedMemories);
        all.addAll(recentMemories);
        memoryManager.saveMemories(all);
    }

    private void updateEmptyState() {
        View emptyState = findViewById(R.id.layout_empty_state_memory);
        View searchBar = findViewById(R.id.search_bar_memory);
        View tvPinnedLabel = findViewById(R.id.tv_pinned_label);
        View pinnedRecycler = findViewById(R.id.pinnedRecycler);
        View tvRecentLabel = findViewById(R.id.tv_recent_label);
        View recentRecycler = findViewById(R.id.recentRecycler);

        boolean hasPinned = pinnedMemories != null && !pinnedMemories.isEmpty();
        boolean hasRecent = recentMemories != null && !recentMemories.isEmpty();

        if (!hasPinned && !hasRecent) {
            if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
            if (searchBar != null) searchBar.setVisibility(View.GONE);
            if (tvPinnedLabel != null) tvPinnedLabel.setVisibility(View.GONE);
            if (pinnedRecycler != null) pinnedRecycler.setVisibility(View.GONE);
            if (tvRecentLabel != null) tvRecentLabel.setVisibility(View.GONE);
            if (recentRecycler != null) recentRecycler.setVisibility(View.GONE);
        } else {
            if (emptyState != null) emptyState.setVisibility(View.GONE);
            if (searchBar != null) searchBar.setVisibility(View.VISIBLE);
            
            if (tvPinnedLabel != null) tvPinnedLabel.setVisibility(hasPinned ? View.VISIBLE : View.GONE);
            if (pinnedRecycler != null) pinnedRecycler.setVisibility(hasPinned ? View.VISIBLE : View.GONE);
            
            if (tvRecentLabel != null) tvRecentLabel.setVisibility(hasRecent ? View.VISIBLE : View.GONE);
            if (recentRecycler != null) recentRecycler.setVisibility(hasRecent ? View.VISIBLE : View.GONE);
        }
    }

    private void showMemoryOptions(Memory memory, int position) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_memory_options, null);
        dialog.setContentView(view);

        MaterialButton btnEdit = view.findViewById(R.id.btn_edit);
        MaterialButton btnPin = view.findViewById(R.id.btn_pin);
        MaterialButton btnCopy = view.findViewById(R.id.btn_copy);
        MaterialButton btnDelete = view.findViewById(R.id.btn_delete);

        btnPin.setText(memory.isPinned() ? R.string.unpin_memory : R.string.pin_memory);

        btnEdit.setOnClickListener(v -> {
            Toast.makeText(this, getString(R.string.edit_memory) + ": " + memory.getTitle(), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnPin.setOnClickListener(v -> {
            if (memory.isPinned()) {
                pinnedMemories.remove(memory);
                memory.setPinned(false);
                recentMemories.add(0, memory);
            } else {
                recentMemories.remove(memory);
                memory.setPinned(true);
                pinnedMemories.add(0, memory);
            }
            pinnedAdapter.notifyDataSetChanged();
            recentAdapter.notifyDataSetChanged();
            saveAllToManager();
            updateEmptyState();
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
            if (memory.isPinned()) {
                pinnedMemories.remove(memory);
                pinnedAdapter.notifyDataSetChanged();
            } else {
                recentMemories.remove(memory);
                recentAdapter.notifyDataSetChanged();
            }
            saveAllToManager();
            updateEmptyState();
            dialog.dismiss();
        });

        dialog.show();
    }
}
