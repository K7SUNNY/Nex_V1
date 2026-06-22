package com.k7sunny.nexv1;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;

public class AccountActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_account);

        View root = findViewById(R.id.account_root);
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

        loadStatistics();
    }

    private void loadStatistics() {
        TextView tvTotalConversations = findViewById(R.id.tv_total_conversations_val);
        TextView tvTokensGenerated = findViewById(R.id.tv_tokens_generated_val);

        new Thread(() -> {
            HistoryManager hm = new HistoryManager(this);
            int sessionCount = hm.getSessionCount();
            int charCount = hm.getTotalAiCharacters();

            int estTokens = (int) Math.ceil(charCount / 3.8);
            String tokensStr;
            if (estTokens >= 1000000) {
                tokensStr = String.format(java.util.Locale.US, "%.1fM", estTokens / 1000000.0);
            } else if (estTokens >= 1000) {
                tokensStr = String.format(java.util.Locale.US, "%.1fk", estTokens / 1000.0);
            } else {
                tokensStr = String.valueOf(estTokens);
            }

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (tvTotalConversations != null) {
                    tvTotalConversations.setText(String.valueOf(sessionCount));
                }
                if (tvTokensGenerated != null) {
                    tvTokensGenerated.setText(tokensStr);
                }
            });
        }).start();
    }
}
