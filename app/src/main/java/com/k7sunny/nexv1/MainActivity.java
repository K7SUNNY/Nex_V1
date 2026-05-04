package com.k7sunny.nexv1;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Tell the window to let the layout handle insets (allows keyboard/IME handling)
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_main);

        // Handle window insets manually to support both status bar and keyboard (IME)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            
            // Use systemBars.top for the status bar and the maximum of navigation bar or keyboard for the bottom
            int bottomPadding = Math.max(systemBars.bottom, ime.bottom);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding);
            
            return WindowInsetsCompat.CONSUMED;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);

        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, DrawerActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_left, 0);
            });
        }
    }
}