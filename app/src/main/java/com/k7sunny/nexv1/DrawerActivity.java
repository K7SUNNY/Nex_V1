package com.k7sunny.nexv1;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DrawerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_drawer);

        View root = findViewById(R.id.drawer_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        ImageButton btn_close = findViewById(R.id.btn_close);
        btn_close.setOnClickListener(v -> finish());

        findViewById(R.id.btn_new_chat).setOnClickListener(v -> {
            // Signal MainActivity to start new chat
            setResult(RESULT_OK);
            finish();
        });

        findViewById(R.id.nav_settings).setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.nav_memory).setOnClickListener(v -> {
            Intent intent = new Intent(this, MemoryActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.nav_account).setOnClickListener(v -> {
            Intent intent = new Intent(this, AccountActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.nav_chat).setOnClickListener(v -> finish());

        setupVersionInfo();
        setupRecentChats();
    }

    private void setupVersionInfo() {
        TextView tvVersion = findViewById(R.id.tv_app_version);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            tvVersion.setText(getString(R.string.app_version, version));
        } catch (Exception e) {
            tvVersion.setText(getString(R.string.app_version, "1.0"));
        }
    }

    private void setupRecentChats() {
        RecyclerView recyclerView = findViewById(R.id.recentChatsRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        HistoryManager historyManager = new HistoryManager(this);
        refreshChats(recyclerView, historyManager);
    }

    private void refreshChats(RecyclerView recyclerView, HistoryManager historyManager) {
        List<ChatSession> sessions = historyManager.getSessions();
        RecentChatAdapter adapter = new RecentChatAdapter(sessions, new RecentChatAdapter.OnChatClickListener() {
            @Override
            public void onChatClick(ChatSession session) {
                Intent data = new Intent();
                data.putExtra("session_id", session.getId());
                setResult(RESULT_OK, data);
                finish();
            }

            @Override
            public void onOptionsClick(ChatSession session) {
                showChatOptions(session, historyManager, () -> refreshChats(recyclerView, historyManager));
            }
        });
        recyclerView.setAdapter(adapter);
    }

    private void showChatOptions(ChatSession session, HistoryManager historyManager, Runnable onRefresh) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.CustomBottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_chat_options, null);
        dialog.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tv_sheet_title);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault());
        String dateStr = sdf.format(new Date(session.getTimestamp()));
        tvTitle.setText(session.getTitle() + "\n" + dateStr);

        view.findViewById(R.id.btn_rename).setOnClickListener(v -> {
            dialog.dismiss();
            showRenameDialog(session, historyManager, onRefresh);
        });

        view.findViewById(R.id.btn_delete).setOnClickListener(v -> {
            dialog.dismiss();
            new AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setMessage("Are you sure you want to delete this chat?")
                .setPositiveButton(R.string.delete, (d, which) -> {
                    historyManager.deleteSession(session.getId());
                    onRefresh.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        });

        dialog.show();
    }

    private void showRenameDialog(ChatSession session, HistoryManager historyManager, Runnable onComplete) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.CustomBottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.dialog_rename_chat, null);
        dialog.setContentView(view);

        TextInputEditText input = view.findViewById(R.id.edit_text_rename);
        input.setText(session.getTitle());
        input.setSelectAllOnFocus(true);
        input.requestFocus();

        view.findViewById(R.id.btn_save_rename).setOnClickListener(v -> {
            if (input.getText() != null) {
                String newTitle = input.getText().toString().trim();
                if (!newTitle.isEmpty()) {
                    historyManager.renameSession(session.getId(), newTitle);
                    onComplete.run();
                }
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.slide_out_left);
    }
}