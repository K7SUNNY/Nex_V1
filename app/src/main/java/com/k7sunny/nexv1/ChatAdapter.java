package com.k7sunny.nexv1;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.core.MarkwonTheme;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.ImageView;
import java.util.ArrayList;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.core.MarkwonTheme;
import android.graphics.Color;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<Message> messages;
    private final OnMessageActionListener actionListener;
    private boolean isGenerating = false;
    private Markwon markwon;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Message, Runnable> pendingDisappears = new HashMap<>();

    public static class MessageBlock {
        public static final int TYPE_TEXT = 0;
        public static final int TYPE_CODE = 1;

        public final int type;
        public final String content;
        public final String language;

        public MessageBlock(int type, String content, String language) {
            this.type = type;
            this.content = content;
            this.language = language;
        }
    }

    public static List<MessageBlock> parseBlocks(String text) {
        List<MessageBlock> blocks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return blocks;
        }

        String[] parts = text.split("```", -1);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (i % 2 == 0) {
                if (!part.isEmpty()) {
                    blocks.add(new MessageBlock(MessageBlock.TYPE_TEXT, part, null));
                }
            } else {
                String language = "code";
                String code = part;
                int firstNewline = part.indexOf('\n');
                if (firstNewline != -1) {
                    String langCandidate = part.substring(0, firstNewline).trim();
                    if (!langCandidate.isEmpty() && langCandidate.length() < 20 && !langCandidate.contains(" ")) {
                        language = langCandidate;
                        code = part.substring(firstNewline + 1);
                    }
                }
                if (code.endsWith("\n")) {
                    code = code.substring(0, code.length() - 1);
                }
                blocks.add(new MessageBlock(MessageBlock.TYPE_CODE, code, language));
            }
        }
        return blocks;
    }

    public interface OnMessageActionListener {
        void onRegenerate(int position);
        void onPinToMemory(String text);
        void onDeleteMessage(int position);
    }

    public ChatAdapter(List<Message> messages, OnMessageActionListener actionListener) {
        this.messages = messages;
        this.actionListener = actionListener;
        registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                int prevLast = positionStart - 1;
                if (prevLast >= 0 && prevLast < getItemCount()) {
                    handler.post(() -> {
                        if (prevLast < getItemCount()) {
                            notifyItemChanged(prevLast);
                        }
                    });
                }
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                int newLast = getItemCount() - 1;
                if (newLast >= 0) {
                    handler.post(() -> {
                        if (newLast < getItemCount()) {
                            notifyItemChanged(newLast);
                        }
                    });
                }
            }
        });
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
        if (markwon == null) {
            markwon = Markwon.builder(parent.getContext())
                .usePlugin(TablePlugin.create(parent.getContext()))
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                        builder.codeBlockBackgroundColor(Color.parseColor("#1C1C1E"))
                               .codeBlockTextColor(Color.parseColor("#E5E5EA"))
                               .codeBackgroundColor(Color.parseColor("#2C2C2E"))
                               .codeTextColor(Color.parseColor("#FF9500"));
                    }
                })
                .build();
        }
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

        PreferenceManager pm = new PreferenceManager(itemView.getContext());
        boolean hapticsEnabled = pm.isHapticFeedbackEnabled();
        itemView.setHapticFeedbackEnabled(hapticsEnabled);

        if (holder instanceof UserViewHolder) {
            UserViewHolder userHolder = (UserViewHolder) holder;
            String imgUriStr = message.getImageUri();
            if (imgUriStr != null && !imgUriStr.isEmpty()) {
                if (userHolder.cardAttachedImage != null) {
                    userHolder.cardAttachedImage.setVisibility(View.VISIBLE);
                }
                if (userHolder.ivUserAttachedImage != null) {
                    try {
                        if (imgUriStr.startsWith("file://") || imgUriStr.startsWith("content://")) {
                            userHolder.ivUserAttachedImage.setImageURI(android.net.Uri.parse(imgUriStr));
                        } else {
                            userHolder.ivUserAttachedImage.setImageURI(android.net.Uri.fromFile(new java.io.File(imgUriStr)));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (userHolder.cardAttachedImage != null) {
                            userHolder.cardAttachedImage.setVisibility(View.GONE);
                        }
                    }
                }
            } else {
                if (userHolder.cardAttachedImage != null) {
                    userHolder.cardAttachedImage.setVisibility(View.GONE);
                }
            }

            if (message.getText() != null && !message.getText().trim().isEmpty()) {
                userHolder.messageText.setVisibility(View.VISIBLE);
                markwon.setMarkdown(userHolder.messageText, message.getText());
            } else if (imgUriStr != null && !imgUriStr.isEmpty()) {
                userHolder.messageText.setVisibility(View.GONE);
            } else {
                userHolder.messageText.setVisibility(View.VISIBLE);
                markwon.setMarkdown(userHolder.messageText, "");
            }

            itemView.setOnLongClickListener(v -> {
                if (message.getText() != null && !message.getText().isEmpty()) {
                    copyToClipboard(v.getContext(), message.getText());
                }
                return true;
            });
        } else if (holder instanceof AiViewHolder) {
            bindAiHolder((AiViewHolder) holder, message, position);
            itemView.setOnLongClickListener(v -> {
                if (message.getType() == Message.TYPE_AI) {
                    toggleActionsWithTimeout(message, position);
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

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof AiViewHolder) {
            stopTypingAnimation((AiViewHolder) holder);
        }
    }

    private void bindAiHolder(AiViewHolder holder, Message message, int position) {
        if (message.getType() == Message.TYPE_TYPING) {
            if (holder.messageText != null) {
                holder.messageText.setVisibility(View.GONE);
            }
            if (holder.messageContainer != null) {
                holder.messageContainer.setVisibility(View.GONE);
            }
            if (holder.typingIndicator != null) {
                holder.typingIndicator.setVisibility(View.VISIBLE);
                startTypingAnimation(holder);
            }
            if (holder.aiActionContainer != null) {
                holder.aiActionContainer.setVisibility(View.GONE);
            }
        } else {
            if (holder.messageText != null) {
                holder.messageText.setVisibility(View.GONE);
            }
            if (holder.messageContainer != null) {
                holder.messageContainer.removeAllViews();
                holder.messageContainer.setVisibility(View.VISIBLE);

                List<MessageBlock> blocks = parseBlocks(message.getText());
                float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;
                for (MessageBlock block : blocks) {
                    if (block.type == MessageBlock.TYPE_TEXT) {
                        TextView tv = new TextView(holder.itemView.getContext());
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        );
                        lp.setMargins(0, 0, 0, (int) (6 * density));
                        tv.setLayoutParams(lp);
                        tv.setTextColor(Color.parseColor("#E3E3E3"));
                        tv.setTextSize(15);
                        tv.setLineSpacing(5, 1);
                        markwon.setMarkdown(tv, block.content);
                        holder.messageContainer.addView(tv);
                    } else if (block.type == MessageBlock.TYPE_CODE) {
                        View codeBlockView = LayoutInflater.from(holder.itemView.getContext())
                            .inflate(R.layout.item_message_code_block, holder.messageContainer, false);

                        TextView tvLanguage = codeBlockView.findViewById(R.id.tvLanguage);
                        TextView tvCode = codeBlockView.findViewById(R.id.tvCode);
                        View btnCopyCode = codeBlockView.findViewById(R.id.btnCopyCode);
                        TextView tvCopyStatus = codeBlockView.findViewById(R.id.tvCopyStatus);

                        tvLanguage.setText(block.language.toUpperCase());
                        tvCode.setText(SyntaxHighlighter.formatCode(block.content, block.language));

                        btnCopyCode.setOnClickListener(v -> {
                            triggerHaptic(v, android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                            ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("Code block", block.content);
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(clip);
                                tvCopyStatus.setText("Copied!");
                                v.postDelayed(() -> {
                                    if (tvCopyStatus != null) {
                                        tvCopyStatus.setText("Copy code");
                                    }
                                }, 2000);
                            }
                        });

                        holder.messageContainer.addView(codeBlockView);
                    }
                }
            }
            if (holder.memoryIndicator != null) {
                if (message.getMemoryTag() != null && !message.getMemoryTag().isEmpty()) {
                    holder.memoryIndicator.setText("• " + message.getMemoryTag());
                    holder.memoryIndicator.setVisibility(View.VISIBLE);
                } else {
                    holder.memoryIndicator.setVisibility(View.GONE);
                }
            }
            if (holder.typingIndicator != null) {
                holder.typingIndicator.setVisibility(View.GONE);
                stopTypingAnimation(holder);
            }

            if (holder.aiActionContainer != null) {
                boolean isLastMessage = (position == messages.size() - 1);
                boolean showActions = message.isActionsVisible() || (isLastMessage && !isGenerating);

                if (showActions) {
                    holder.aiActionContainer.setVisibility(View.VISIBLE);

                    if (holder.btnCopy != null) {
                        holder.btnCopy.setOnClickListener(v -> {
                            triggerHaptic(v, android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                            copyToClipboard(v.getContext(), message.getText());
                        });
                    }

                    if (holder.btnShare != null) {
                        holder.btnShare.setOnClickListener(v -> {
                            triggerHaptic(v, android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                            shareText(v.getContext(), message.getText());
                        });
                    }

                    if (holder.btnRegenerate != null) {
                        holder.btnRegenerate.setOnClickListener(v -> {
                            triggerHaptic(v, android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                            if (actionListener != null) {
                                actionListener.onRegenerate(position);
                            }
                        });
                    }

                    if (holder.btnMore != null) {
                        holder.btnMore.setOnClickListener(v -> {
                            triggerHaptic(v, android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                            showMoreOptions(v.getContext(), holder.btnMore, message, position);
                        });
                    }
                } else {
                    holder.aiActionContainer.setVisibility(View.GONE);
                }
            }
        }
    }

    private void startTypingAnimation(AiViewHolder holder) {
        if (holder.typingIndicator == null || holder.dot1 == null || holder.dot2 == null || holder.dot3 == null) {
            return;
        }

        stopTypingAnimation(holder);

        float bounceHeight = -10f;

        ObjectAnimator anim1 = ObjectAnimator.ofFloat(holder.dot1, "translationY", 0f, bounceHeight, 0f);
        anim1.setDuration(600);
        anim1.setRepeatCount(ValueAnimator.INFINITE);
        anim1.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator anim2 = ObjectAnimator.ofFloat(holder.dot2, "translationY", 0f, bounceHeight, 0f);
        anim2.setDuration(600);
        anim2.setRepeatCount(ValueAnimator.INFINITE);
        anim2.setRepeatMode(ValueAnimator.REVERSE);
        anim2.setStartDelay(150);

        ObjectAnimator anim3 = ObjectAnimator.ofFloat(holder.dot3, "translationY", 0f, bounceHeight, 0f);
        anim3.setDuration(600);
        anim3.setRepeatCount(ValueAnimator.INFINITE);
        anim3.setRepeatMode(ValueAnimator.REVERSE);
        anim3.setStartDelay(300);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(anim1, anim2, anim3);
        set.start();

        holder.typingAnimator = set;
    }

    private void stopTypingAnimation(AiViewHolder holder) {
        if (holder.typingAnimator != null) {
            holder.typingAnimator.cancel();
            holder.typingAnimator = null;
        }
        if (holder.dot1 != null) holder.dot1.setTranslationY(0f);
        if (holder.dot2 != null) holder.dot2.setTranslationY(0f);
        if (holder.dot3 != null) holder.dot3.setTranslationY(0f);
    }

    private void toggleActionsWithTimeout(Message message, int position) {
        Runnable pending = pendingDisappears.remove(message);
        if (pending != null) {
            handler.removeCallbacks(pending);
        }

        boolean nextState = !message.isActionsVisible();
        message.setActionsVisible(nextState);
        notifyItemChanged(position, "actions_visibility_update");

        if (nextState) {
            Runnable hideRunnable = new Runnable() {
                @Override
                public void run() {
                    message.setActionsVisible(false);
                    pendingDisappears.remove(message);
                    int currentPos = messages.indexOf(message);
                    if (currentPos != -1) {
                        notifyItemChanged(currentPos, "actions_visibility_update");
                    }
                }
            };
            pendingDisappears.put(message, hideRunnable);
            handler.postDelayed(hideRunnable, 5000); // 5 seconds
        }
    }

    public void updateStreamingText(int position) {
        notifyItemChanged(position, "text_update");
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        handler.removeCallbacksAndMessages(null);
        pendingDisappears.clear();
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
            triggerHaptic(anchor, android.view.HapticFeedbackConstants.KEYBOARD_TAP);
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

    private void triggerHaptic(View view, int type) {
        if (view != null) {
            PreferenceManager pm = new PreferenceManager(view.getContext());
            if (pm.isHapticFeedbackEnabled()) {
                view.performHapticFeedback(type, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }
        }
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        ImageView ivUserAttachedImage;
        View cardAttachedImage;

        UserViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            ivUserAttachedImage = itemView.findViewById(R.id.ivUserAttachedImage);
            cardAttachedImage = itemView.findViewById(R.id.card_attached_image);
        }
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        LinearLayout messageContainer;
        TextView memoryIndicator;
        View aiActionContainer;
        ImageButton btnCopy;
        ImageButton btnShare;
        ImageButton btnRegenerate;
        ImageButton btnMore;
        View typingIndicator;
        View dot1;
        View dot2;
        View dot3;
        Animator typingAnimator;

        AiViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            messageContainer = itemView.findViewById(R.id.messageContainer);
            memoryIndicator = itemView.findViewById(R.id.memoryIndicator);
            aiActionContainer = itemView.findViewById(R.id.aiActionContainer);
            btnCopy = itemView.findViewById(R.id.btnCopy);
            btnShare = itemView.findViewById(R.id.btnShare);
            btnRegenerate = itemView.findViewById(R.id.btnRegenerate);
            btnMore = itemView.findViewById(R.id.btnMore);
            typingIndicator = itemView.findViewById(R.id.typingIndicator);
            dot1 = itemView.findViewById(R.id.dot1);
            dot2 = itemView.findViewById(R.id.dot2);
            dot3 = itemView.findViewById(R.id.dot3);
        }
    }
}