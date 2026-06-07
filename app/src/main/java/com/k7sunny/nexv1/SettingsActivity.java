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
import com.google.android.material.slider.Slider;

public class SettingsActivity extends AppCompatActivity {

    private PreferenceManager preferenceManager;
    private TextView tvPersonaSummary;
    private TextView tvMaxTokensVal;
    private Slider sliderMaxTokens;
    private TextView tvTemperatureVal;
    private Slider sliderTemperature;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_settings);

        preferenceManager = new PreferenceManager(this);
        tvPersonaSummary = findViewById(R.id.tv_persona_summary);
        tvMaxTokensVal = findViewById(R.id.tv_max_tokens_val);
        sliderMaxTokens = findViewById(R.id.slider_max_tokens);
        tvTemperatureVal = findViewById(R.id.tv_temperature_val);
        sliderTemperature = findViewById(R.id.slider_temperature);

        sliderMaxTokens.addOnChangeListener((slider, value, fromUser) -> {
            int val = Math.round(value);
            tvMaxTokensVal.setText(String.valueOf(val));
            preferenceManager.setMaxTokens(val);
        });

        sliderTemperature.addOnChangeListener((slider, value, fromUser) -> {
            tvTemperatureVal.setText(String.format(java.util.Locale.US, "%.2f", value));
            preferenceManager.setTemperature(value);
        });

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
        if (!preferenceManager.isCustomPersonaSet()) {
            tvPersonaSummary.setText("Default (Optimized for Nex)");
        } else {
            String persona = preferenceManager.getSystemPersona();
            if (persona.length() > 60) {
                tvPersonaSummary.setText(persona.substring(0, 57) + "...");
            } else {
                tvPersonaSummary.setText(persona);
            }
        }

        int maxTokens = preferenceManager.getMaxTokens();
        float temperature = preferenceManager.getTemperature();

        if (tvMaxTokensVal != null) tvMaxTokensVal.setText(String.valueOf(maxTokens));
        if (sliderMaxTokens != null) sliderMaxTokens.setValue((float) maxTokens);

        if (tvTemperatureVal != null) {
            tvTemperatureVal.setText(String.format(java.util.Locale.US, "%.2f", temperature));
        }
        if (sliderTemperature != null) sliderTemperature.setValue(temperature);
    }

    private void showPersonaEditDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.CustomBottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.dialog_rename_chat, null);
        dialog.setContentView(view);

        TextView title = (TextView) ((android.view.ViewGroup)view).getChildAt(1); 
        com.google.android.material.textfield.TextInputEditText input = view.findViewById(R.id.edit_text_rename);
        MaterialButton btnSave = view.findViewById(R.id.btn_save_rename);

        title.setText("System Persona");
        input.setHint("e.g. Be funny, Reply in Python...");
        
        // Only pre-fill if it's a custom one, otherwise leave blank for a fresh start
        if (preferenceManager.isCustomPersonaSet()) {
            input.setText(preferenceManager.getSystemPersona());
            if (input.getText() != null) {
                input.setSelection(input.getText().length());
            }
        }

        btnSave.setOnClickListener(v -> {
            String newPersona = input.getText() != null ? input.getText().toString().trim() : "";
            if (newPersona.isEmpty()) {
                preferenceManager.resetSystemPersona();
                Toast.makeText(this, "Reset to Nex default", Toast.LENGTH_SHORT).show();
            } else {
                preferenceManager.setSystemPersona(newPersona);
                Toast.makeText(this, "Persona updated", Toast.LENGTH_SHORT).show();
            }
            updateUI();
            dialog.dismiss();
        });

        dialog.show();
    }
}
