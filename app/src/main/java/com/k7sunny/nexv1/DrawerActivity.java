package com.k7sunny.nexv1;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
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
        List<ChatSession> sessions = historyManager.getSessions();

        RecentChatAdapter adapter = new RecentChatAdapter(sessions, session -> {
            // Return selected session ID to MainActivity
            Intent data = new Intent();
            data.putExtra("session_id", session.getId());
            setResult(RESULT_OK, data);
            finish();
        });
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.slide_out_left);
    }
}