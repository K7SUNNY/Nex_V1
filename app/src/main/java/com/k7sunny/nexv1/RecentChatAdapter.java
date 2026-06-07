package com.k7sunny.nexv1;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.List;

public class RecentChatAdapter extends RecyclerView.Adapter<RecentChatAdapter.ViewHolder> {

    private List<ChatSession> sessions;
    private OnChatClickListener listener;

    public interface OnChatClickListener {
        void onChatClick(ChatSession session);
        void onOptionsClick(ChatSession session);
    }

    public RecentChatAdapter(List<ChatSession> sessions, OnChatClickListener listener) {
        this.sessions = sessions;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recent_chat, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatSession session = sessions.get(position);
        holder.btnRecentChat.setText(session.getTitle());
        holder.btnRecentChat.setOnClickListener(v -> listener.onChatClick(session));
        
        PreferenceManager pm = new PreferenceManager(holder.btnRecentChat.getContext());
        holder.btnRecentChat.setHapticFeedbackEnabled(pm.isHapticFeedbackEnabled());
        
        holder.btnRecentChat.setOnLongClickListener(v -> {
            listener.onOptionsClick(session);
            return true;
        });
        
        holder.btnMoreOptions.setOnClickListener(v -> {
            listener.onOptionsClick(session);
        });
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialButton btnRecentChat;
        ImageButton btnMoreOptions;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            btnRecentChat = itemView.findViewById(R.id.btn_recent_chat);
            btnMoreOptions = itemView.findViewById(R.id.btn_more_options);
        }
    }
}
