package com.k7sunny.nexv1;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<Message> messages;
    private final OnMessageActionListener actionListener;
    private boolean isGenerating = false;

    public interface OnMessageActionListener {
        void onRegenerate(int position);
        void onPinToMemory(String text);
        void onDeleteMessage(int position);
    }

    public ChatAdapter(List<Message> messages, OnMessageActionListener actionListener) {
        this.messages = messages;
        this.actionListener = actionListener;
    }

    public void setGenerating(boolean generating) {
        this.isGenerating = generating;
        if (!messages.isEmpty()) {
            notifyItemChanged(messages.size() - 1);
        }
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

        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).messageText.setText(message.getText());
            itemView.setOnLongClickListener(v -> {
                copyToClipboard(v.getContext(), message.getText());
                return true;
            });
        } else if (holder instanceof AiViewHolder) {
            bindAiHolder((AiViewHolder) holder, message, position);
            itemView.setOnLongClickListener(v -> {
                if (message.getType() == Message.TYPE_AI) {
                    message.setActionsVisible(!message.isActionsVisible());
                    notifyItemChanged(position, "actions_visibility_update");
                }
                return true;
            });
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && holder instanceof AiViewHolder) {
            Message message = messages.get(position);
            bindAiHolder((AiViewHolder) holder, message, position);
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    private void bindAiHolder(AiViewHolder holder, Message message, int position) {
        if (message.getType() == Message.TYPE_TYPING) {
            holder.messageText.setText("...");
            holder.messageText.setAlpha(0.5f);
            if (holder.aiActionContainer != null) {
                holder.aiActionContainer.setVisibility(View.GONE);
            }
        } else {
            holder.messageText.setText(message.getText());
            holder.messageText.setAlpha(1.0f);

            if (holder.aiActionContainer != null) {
                boolean isLastMessage = (position == messages.size() - 1);
                boolean showActions = message.isActionsVisible() || (isLastMessage && !isGenerating);

                if (showActions) {
                    holder.aiActionContainer.setVisibility(View.VISIBLE);

                    if (holder.btnCopy != null) {
                        holder.btnCopy.setOnClickListener(v -> copyToClipboard(v.getContext(), message.getText()));
                    }

                    if (holder.btnShare != null) {
                        holder.btnShare.setOnClickListener(v -> shareText(v.getContext(), message.getText()));
                    }

                    if (holder.btnRegenerate != null) {
                        holder.btnRegenerate.setOnClickListener(v -> {
                            if (actionListener != null) {
                                actionListener.onRegenerate(position);
                            }
                        });
                    }

                    if (holder.btnMore != null) {
                        holder.btnMore.setOnClickListener(v -> showMoreOptions(v.getContext(), holder.btnMore, message, position));
                    }
                } else {
                    holder.aiActionContainer.setVisibility(View.GONE);
                }
            }
        }
    }

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

    private void shareText(Context context, String text) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            context.startActivity(Intent.createChooser(intent, "Share response"));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to share", Toast.LENGTH_SHORT).show();
        }
    }

    private void showMoreOptions(Context context, View anchor, Message message, int position) {
        PopupMenu popup = new PopupMenu(context, anchor);
        popup.getMenu().add(0, 1, 0, "Pin to Memory");
        popup.getMenu().add(0, 2, 1, "Delete Message");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                if (actionListener != null) {
                    actionListener.onPinToMemory(message.getText());
                }
            } else if (item.getItemId() == 2) {
                if (actionListener != null) {
                    actionListener.onDeleteMessage(position);
                }
            }
            return true;
        });
        popup.show();
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
        View aiActionContainer;
        ImageButton btnCopy;
        ImageButton btnShare;
        ImageButton btnRegenerate;
        ImageButton btnMore;

        AiViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            aiActionContainer = itemView.findViewById(R.id.aiActionContainer);
            btnCopy = itemView.findViewById(R.id.btnCopy);
            btnShare = itemView.findViewById(R.id.btnShare);
            btnRegenerate = itemView.findViewById(R.id.btnRegenerate);
            btnMore = itemView.findViewById(R.id.btnMore);
        }
    }
}