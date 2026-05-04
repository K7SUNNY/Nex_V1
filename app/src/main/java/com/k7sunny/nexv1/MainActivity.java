package com.k7sunny.nexv1;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private List<Message> messageList;
    private View welcomeContainer;
    private EditText messageInput;
    private ImageButton sendButton;

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

        welcomeContainer = findViewById(R.id.welcomeContainer);
        recyclerView = findViewById(R.id.recyclerView);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(chatAdapter);

        sendButton.setOnClickListener(v -> sendMessage());

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, DrawerActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_left, 0);
            });
        }
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (!text.isEmpty()) {
            // Hide welcome screen if it's visible
            if (welcomeContainer != null && welcomeContainer.getVisibility() == View.VISIBLE) {
                welcomeContainer.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }

            // Add user message
            messageList.add(new Message(text, Message.TYPE_USER));
            chatAdapter.notifyItemInserted(messageList.size() - 1);
            recyclerView.scrollToPosition(messageList.size() - 1);

            // Clear input
            messageInput.setText("");

            // Simulate AI response after a short delay
            recyclerView.postDelayed(() -> {
                messageList.add(new Message("This is a simulated AI response for: \"" + text + "\"", Message.TYPE_AI));
                chatAdapter.notifyItemInserted(messageList.size() - 1);
                recyclerView.scrollToPosition(messageList.size() - 1);
            }, 1000);
        }
    }
}