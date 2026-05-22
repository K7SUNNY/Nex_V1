package com.k7sunny.nexv1;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

public class SettingsActivity extends AppCompatActivity {

    private PreferenceManager preferenceManager;
    private TextView tvPersonaSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_settings);

        preferenceManager = new PreferenceManager(this);
        tvPersonaSummary = findViewById(R.id.tv_persona_summary);

        View root = findViewById(R.id.settings_root);
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
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        updateUI();

        findViewById(R.id.btn_edit_persona).setOnClickListener(v -> showPersonaEditDialog());
        findViewById(R.id.btn_select_model).setOnClickListener(v -> {
            // Future: Show model selection (already implemented in MainActivity, could be moved here)
            Toast.makeText(this, "Model selection coming soon to settings", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateUI() {
        String persona = preferenceManager.getSystemPersona();
        if (persona.length() > 60) {
            tvPersonaSummary.setText(persona.substring(0, 57) + "...");
        } else {
            tvPersonaSummary.setText(persona);
        }
    }

    private void showPersonaEditDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.CustomBottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.dialog_rename_chat, null);
        dialog.setContentView(view);

        TextView title = (TextView) ((android.view.ViewGroup)view).getChildAt(1); // The textview with "rename_chat" text
        com.google.android.material.textfield.TextInputEditText input = view.findViewById(R.id.edit_text_rename);
        MaterialButton btnSave = view.findViewById(R.id.btn_save_rename);

        title.setText("System Persona");
        input.setHint("Enter AI persona instructions...");
        input.setText(preferenceManager.getSystemPersona());
        input.setSelection(input.getText().length());

        btnSave.setOnClickListener(v -> {
            String newPersona = input.getText().toString().trim();
            if (!newPersona.isEmpty()) {
                preferenceManager.setSystemPersona(newPersona);
                updateUI();
                dialog.dismiss();
                Toast.makeText(this, "Persona updated", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }
}
