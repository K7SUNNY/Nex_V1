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

        setupRecyclers();
    }

    private void setupRecyclers() {
        pinnedMemories = new ArrayList<>();
        recentMemories = new ArrayList<>();

        // Dummy data
        pinnedMemories.add(new Memory("User preferences", "Nex prefers concise code examples and OLED dark mode themes.", true));
        
        recentMemories.add(new Memory("Project architecture", "The app uses a JNI bridge to run llama.cpp for local inference.", false));
        recentMemories.add(new Memory("Design principles", "Focus on slick, modern, and minimal interface with pure black backgrounds.", false));

        RecyclerView pinnedRecycler = findViewById(R.id.pinnedRecycler);
        pinnedRecycler.setLayoutManager(new LinearLayoutManager(this));
        pinnedAdapter = new MemoryAdapter(pinnedMemories, this::showMemoryOptions);
        pinnedRecycler.setAdapter(pinnedAdapter);

        RecyclerView recentRecycler = findViewById(R.id.recentRecycler);
        recentRecycler.setLayoutManager(new LinearLayoutManager(this));
        recentAdapter = new MemoryAdapter(recentMemories, this::showMemoryOptions);
        recentRecycler.setAdapter(recentAdapter);
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
            dialog.dismiss();
        });

        dialog.show();
    }
}
