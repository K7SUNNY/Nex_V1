package com.k7sunny.nexv1;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Collections;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<Message> messages;

    public ChatAdapter(List<Message> messages) {
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == Message.TYPE_USER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_message, parent, false);
            return new UserViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ai_message, parent, false);
            return new AiViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messages.get(position);

        View itemView = holder.itemView;
        itemView.setOnLongClickListener(v -> {
            copyToClipboard(v.getContext(), message.getText());
            return true;
        });

        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).messageText.setText(message.getText());
        } else if (holder instanceof AiViewHolder) {
            bindAiHolder((AiViewHolder) holder, message);
        }
    }

    /**
     * Payload-based partial bind: only updates the text without re-creating
     * the entire ViewHolder. Called when notifyItemChanged(pos, payload) is used.
     */
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && holder instanceof AiViewHolder) {
            // Partial bind — just update text content
            Message message = messages.get(position);
            bindAiHolder((AiViewHolder) holder, message);
        } else {
            // Full bind fallback
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    private void bindAiHolder(AiViewHolder holder, Message message) {
        if (message.getType() == Message.TYPE_TYPING) {
            holder.messageText.setText("...");
            holder.messageText.setAlpha(0.5f);
        } else {
            holder.messageText.setText(message.getText());
            holder.messageText.setAlpha(1.0f);
        }
    }

    /**
     * Efficiently updates a streaming AI message in-place.
     * Uses payload-based notification to avoid full ViewHolder rebind,
     * reducing layout passes from O(views) to O(1) per token.
     */
    public void updateStreamingText(int position) {
        notifyItemChanged(position, "text_update");
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    private void copyToClipboard(Context context, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Nex Message", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        UserViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
        }
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        AiViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
        }
    }
}