package com.k7sunny.nexv1;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tables.TableTheme;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.noties.markwon.movement.MovementMethodPlugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<Message> messages;
    private final OnMessageActionListener actionListener;
    private final PreferenceManager preferenceManager;
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

    public static String formatSymbols(String text) {
        if (text == null || text.isEmpty()) return "";

        String s = text;
        // LaTeX math environments \[ ... \] and \( ... \)
        s = s.replace("\\[", "\n$$\n").replace("\\]", "\n$$\n");
        s = s.replace("\\(", "$").replace("\\)", "$");

        // Fractions: \frac{a}{b} -> (a / b) or specific common fractions
        s = s.replaceAll("\\\\frac\\{1\\}\\{2\\}", "½")
             .replaceAll("\\\\frac\\{1\\}\\{3\\}", "⅓")
             .replaceAll("\\\\frac\\{2\\}\\{3\\}", "⅔")
             .replaceAll("\\\\frac\\{1\\}\\{4\\}", "¼")
             .replaceAll("\\\\frac\\{3\\}\\{4\\}", "¾")
             .replaceAll("\\\\frac\\{([^}]+)\\}\\{([^}]+)\\}", "($1 / $2)");

        // Square roots
        s = s.replaceAll("\\\\sqrt\\{([^}]+)\\}", "√($1)")
             .replaceAll("\\\\sqrt", "√");

        // Greek letters
        s = s.replace("\\alpha", "α")
             .replace("\\beta", "β")
             .replace("\\gamma", "γ")
             .replace("\\Gamma", "Γ")
             .replace("\\delta", "δ")
             .replace("\\Delta", "Δ")
             .replace("\\epsilon", "ε")
             .replace("\\zeta", "ζ")
             .replace("\\eta", "η")
             .replace("\\theta", "θ")
             .replace("\\Theta", "Θ")
             .replace("\\iota", "ι")
             .replace("\\kappa", "κ")
             .replace("\\lambda", "λ")
             .replace("\\Lambda", "Λ")
             .replace("\\mu", "μ")
             .replace("\\nu", "ν")
             .replace("\\xi", "ξ")
             .replace("\\pi", "π")
             .replace("\\Pi", "Π")
             .replace("\\rho", "ρ")
             .replace("\\sigma", "σ")
             .replace("\\Sigma", "Σ")
             .replace("\\tau", "τ")
             .replace("\\upsilon", "υ")
             .replace("\\phi", "φ")
             .replace("\\Phi", "Φ")
             .replace("\\chi", "χ")
             .replace("\\psi", "ψ")
             .replace("\\Psi", "Ψ")
             .replace("\\omega", "ω")
             .replace("\\Omega", "Ω");

        // Operators & Relations
        s = s.replace("\\times", "×")
             .replace("\\div", "÷")
             .replace("\\pm", "±")
             .replace("\\mp", "∓")
             .replace("\\approx", "≈")
             .replace("\\neq", "≠")
             .replace("\\ne", "≠")
             .replace("\\leq", "≤")
             .replace("\\le", "≤")
             .replace("\\geq", "≥")
             .replace("\\ge", "≥")
             .replace("\\ll", "≪")
             .replace("\\gg", "≫")
             .replace("\\infty", "∞")
             .replace("\\propto", "∝")
             .replace("\\equiv", "≡")
             .replace("\\sim", "∼")
             .replace("\\simeq", "≃")
             .replace("\\cong", "≅");

        // Set theory & logic
        s = s.replace("\\forall", "∀")
             .replace("\\exists", "∃")
             .replace("\\in", "∈")
             .replace("\\notin", "∉")
             .replace("\\subset", "⊂")
             .replace("\\subseteq", "⊆")
             .replace("\\supset", "⊃")
             .replace("\\supseteq", "⊇")
             .replace("\\cup", "∪")
             .replace("\\cap", "∩")
             .replace("\\emptyset", "∅")
             .replace("\\land", "∧")
             .replace("\\lor", "∨")
             .replace("\\neg", "¬");

        // Calculus & Linear Algebra
        s = s.replace("\\cdot", "·")
             .replace("\\circ", "°")
             .replace("\\degree", "°")
             .replace("\\nabla", "∇")
             .replace("\\partial", "∂")
             .replace("\\sum", "∑")
             .replace("\\prod", "∏")
             .replace("\\int", "∫")
             .replace("\\oint", "∮");

        // Arrows
        s = s.replace("\\rightarrow", "→")
             .replace("\\to", "→")
             .replace("\\leftarrow", "←")
             .replace("\\gets", "←")
             .replace("\\Rightarrow", "⇒")
             .replace("\\Leftarrow", "⇐")
             .replace("\\leftrightarrow", "↔")
             .replace("\\Leftrightarrow", "⇔")
             .replace("\\iff", "⟺")
             .replace("\\uparrow", "↑")
             .replace("\\downarrow", "↓");

        // Common superscripts
        s = s.replace("^0", "⁰").replace("^1", "¹").replace("^2", "²").replace("^3", "³")
             .replace("^4", "⁴").replace("^5", "⁵").replace("^6", "⁶").replace("^7", "⁷")
             .replace("^8", "⁸").replace("^9", "⁹").replace("^+", "⁺").replace("^-", "⁻")
             .replace("^n", "ⁿ").replace("^x", "ˣ").replace("^y", "ʸ");

        // Common subscripts
        s = s.replace("_0", "₀").replace("_1", "₁").replace("_2", "₂").replace("_3", "₃")
             .replace("_4", "₄").replace("_5", "₅").replace("_6", "₆").replace("_7", "₇")
             .replace("_8", "₈").replace("_9", "₉").replace("_i", "ᵢ").replace("_j", "ⱼ")
             .replace("_k", "ₖ").replace("_n", "ₙ").replace("_m", "ₘ").replace("_x", "ₓ");

        // Clean standalone $$ and $ math delimiters
        s = s.replaceAll("\\$\\$([^$]+)\\$\\$", "\n\n* $1 *\n\n")
             .replaceAll("\\$([^$]+)\\$", "*$1*");

        return s;
    }

    public static class ParsedMessage {
        public final String thinkingContent;
        public final boolean isThinking;
        public final boolean hasThinking;
        public final String responseContent;

        public ParsedMessage(String thinkingContent, boolean isThinking, boolean hasThinking, String responseContent) {
            this.thinkingContent = thinkingContent;
            this.isThinking = isThinking;
            this.hasThinking = hasThinking;
            this.responseContent = responseContent;
        }
    }

    public static ParsedMessage parseMessageContent(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return new ParsedMessage("", false, false, "");
        }

        if (!rawText.contains("<think>")) {
            return new ParsedMessage("", false, false, rawText);
        }

        int thinkStart = rawText.indexOf("<think>");
        int thinkEnd = rawText.indexOf("</think>");

        if (thinkEnd != -1 && thinkEnd >= thinkStart) {
            String before = rawText.substring(0, thinkStart).trim();
            String thinkBody = rawText.substring(thinkStart + 7, thinkEnd).trim();
            String after = rawText.substring(thinkEnd + 8).trim();

            String response = before.isEmpty() ? after : (before + "\n\n" + after);
            return new ParsedMessage(thinkBody, false, true, response);
        } else {
            String before = rawText.substring(0, thinkStart).trim();
            String thinkBody = rawText.substring(thinkStart + 7).trim();
            return new ParsedMessage(thinkBody, true, true, before);
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
                    blocks.add(new MessageBlock(MessageBlock.TYPE_TEXT, formatSymbols(part), null));
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

    public ChatAdapter(List<Message> messages, OnMessageActionListener actionListener, PreferenceManager preferenceManager) {
        this.messages = messages;
        this.actionListener = actionListener;
        this.preferenceManager = preferenceManager;
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

    public ChatAdapter(List<Message> messages, OnMessageActionListener actionListener) {
        this(messages, actionListener, null);
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
            float density = parent.getContext().getResources().getDisplayMetrics().density;
            markwon = Markwon.builder(parent.getContext())
                .usePlugin(TablePlugin.create(new TableTheme.Builder()
                    .tableBorderColor(Color.parseColor("#2C2C2E"))
                    .tableBorderWidth((int) (1 * density))
                    .tableCellPadding((int) (8 * density))
                    .tableHeaderRowBackgroundColor(Color.parseColor("#1C1C1E"))
                    .tableEvenRowBackgroundColor(Color.parseColor("#121316"))
                    .tableOddRowBackgroundColor(Color.parseColor("#18191D"))
                    .build()))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TaskListPlugin.create(parent.getContext()))
                .usePlugin(HtmlPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(MovementMethodPlugin.link())
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                        builder.codeBlockBackgroundColor(Color.parseColor("#141519"))
                               .codeBlockTextColor(Color.parseColor("#E5E5EA"))
                               .codeBackgroundColor(Color.parseColor("#22242B"))
                               .codeTextColor(Color.parseColor("#FF9F0A"))
                               .codeTextSize((int) (13 * density))
                               .blockQuoteColor(Color.parseColor("#3A3D48"))
                               .blockQuoteWidth((int) (3.5f * density))
                               .blockMargin((int) (10 * density))
                               .listItemColor(Color.parseColor("#E5E5EA"))
                               .bulletWidth((int) (6 * density))
                               .thematicBreakColor(Color.parseColor("#2C2C2E"))
                               .thematicBreakHeight((int) (1 * density))
                               .headingBreakColor(Color.parseColor("#2C2C2E"))
                               .headingBreakHeight((int) (1 * density))
                               .headingTextSizeMultipliers(new float[]{1.35f, 1.2f, 1.1f, 1.05f, 1.0f, 0.95f});
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

        boolean hapticsEnabled = isHapticsEnabled(itemView.getContext());
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

            String docName = message.getDocumentName();
            if (docName != null && !docName.isEmpty()) {
                if (userHolder.cardAttachedDocument != null) {
                    userHolder.cardAttachedDocument.setVisibility(View.VISIBLE);
                }
                if (userHolder.tvUserAttachedDocName != null) {
                    userHolder.tvUserAttachedDocName.setText(docName);
                }
            } else {
                if (userHolder.cardAttachedDocument != null) {
                    userHolder.cardAttachedDocument.setVisibility(View.GONE);
                }
            }

            if (message.getText() != null && !message.getText().trim().isEmpty()) {
                userHolder.messageText.setVisibility(View.VISIBLE);
                markwon.setMarkdown(userHolder.messageText, formatSymbols(message.getText()));
            } else if ((imgUriStr != null && !imgUriStr.isEmpty()) || (docName != null && !docName.isEmpty())) {
                userHolder.messageText.setVisibility(View.GONE);
            } else {
                userHolder.messageText.setVisibility(View.VISIBLE);
                markwon.setMarkdown(userHolder.messageText, "");
            }

            View.OnLongClickListener userLongClick = v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < messages.size()) {
                    Message msg = messages.get(pos);
                    if (msg.getText() != null && !msg.getText().isEmpty()) {
                        triggerHaptic(v, android.view.HapticFeedbackConstants.LONG_PRESS);
                        copyToClipboard(v.getContext(), msg.getText());
                        return true;
                    }
                }
                return false;
            };

            userHolder.itemView.setOnLongClickListener(userLongClick);
            if (userHolder.messageText != null) {
                userHolder.messageText.setOnLongClickListener(userLongClick);
            }
        } else if (holder instanceof AiViewHolder) {
            bindAiHolder((AiViewHolder) holder, message, position);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && holder instanceof AiViewHolder) {
            AiViewHolder aiHolder = (AiViewHolder) holder;
            Message message = messages.get(position);

            if (payloads.contains("actions_visibility_update")) {
                boolean isLastMessage = (position == messages.size() - 1);
                boolean showActions = message.isActionsVisible() || (isLastMessage && !isGenerating);
                if (aiHolder.aiActionContainer != null) {
                    aiHolder.aiActionContainer.setVisibility(showActions ? View.VISIBLE : View.GONE);
                }
                bindAiClickListeners(aiHolder, message);
                return;
            }

            if (payloads.contains("text_update")) {
                bindAiStreaming(aiHolder, message);
                return;
            }

            bindAiHolder(aiHolder, message, position);
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

    private void bindAiContent(AiViewHolder holder, Message message, boolean isStreaming) {
        String text = message.getText();
        if (text == null) text = "";

        ParsedMessage parsed = parseMessageContent(text);

        // 1. Handle Thinking Accordion
        if (holder.cardThinkingContainer != null) {
            if (parsed.hasThinking && !parsed.thinkingContent.isEmpty()) {
                holder.cardThinkingContainer.setVisibility(View.VISIBLE);

                if (parsed.isThinking) {
                    // Actively streaming thinking: auto-open to show live reasoning tokens
                    if (holder.tvThinkingTitle != null) {
                        holder.tvThinkingTitle.setText("Thinking...");
                    }
                    if (holder.tvThinkingContent != null) {
                        holder.tvThinkingContent.setVisibility(View.VISIBLE);
                        markwon.setMarkdown(holder.tvThinkingContent, formatSymbols(parsed.thinkingContent));
                    }
                    if (holder.ivThinkingChevron != null) {
                        holder.ivThinkingChevron.setRotation(180f);
                    }
                } else {
                    // Thinking complete
                    if (holder.tvThinkingTitle != null) {
                        holder.tvThinkingTitle.setText("Thought Process");
                    }
                    boolean isExpanded = message.isThinkingExpanded();
                    // If generation completed but the model only produced reasoning (no final answer), keep open
                    if (!isStreaming && (parsed.responseContent == null || parsed.responseContent.trim().isEmpty())) {
                        isExpanded = true;
                    }
                    if (holder.tvThinkingContent != null) {
                        holder.tvThinkingContent.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
                        if (isExpanded) {
                            markwon.setMarkdown(holder.tvThinkingContent, formatSymbols(parsed.thinkingContent));
                        }
                    }
                    if (holder.ivThinkingChevron != null) {
                        holder.ivThinkingChevron.setRotation(isExpanded ? 180f : 0f);
                    }
                }

                if (holder.layoutThinkingHeader != null) {
                    holder.layoutThinkingHeader.setOnClickListener(v -> {
                        triggerHaptic(v, android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                        boolean nextExpanded = !message.isThinkingExpanded();
                        message.setThinkingExpanded(nextExpanded);
                        if (holder.tvThinkingContent != null) {
                            holder.tvThinkingContent.setVisibility(nextExpanded ? View.VISIBLE : View.GONE);
                            if (nextExpanded) {
                                markwon.setMarkdown(holder.tvThinkingContent, formatSymbols(parsed.thinkingContent));
                            }
                        }
                        if (holder.ivThinkingChevron != null) {
                            holder.ivThinkingChevron.animate().rotation(nextExpanded ? 180f : 0f).setDuration(200).start();
                        }
                    });
                }
            } else {
                holder.cardThinkingContainer.setVisibility(View.GONE);
            }
        }

        // 2. Handle Main Response Content & Typing Animation
        String responseText = parsed.responseContent;
        if (responseText == null || responseText.trim().isEmpty()) {
            if (holder.messageText != null) {
                holder.messageText.setVisibility(View.GONE);
                holder.messageText.setText("");
            }
            if (holder.messageContainer != null) {
                holder.messageContainer.setVisibility(View.GONE);
                holder.messageContainer.removeAllViews();
            }
            // If thinking has finished but response hasn't started streaming yet, show typing indicator
            if (isStreaming && parsed.hasThinking && !parsed.isThinking) {
                if (holder.typingIndicator != null) {
                    holder.typingIndicator.setVisibility(View.VISIBLE);
                    startTypingAnimation(holder);
                }
            } else if (!isStreaming) {
                if (holder.typingIndicator != null) {
                    holder.typingIndicator.setVisibility(View.GONE);
                    stopTypingAnimation(holder);
                }
            }
        } else {
            if (holder.typingIndicator != null) {
                holder.typingIndicator.setVisibility(View.GONE);
                stopTypingAnimation(holder);
            }
            if (!responseText.contains("```")) {
                if (holder.messageContainer != null) {
                    holder.messageContainer.setVisibility(View.GONE);
                    holder.messageContainer.removeAllViews();
                }
                if (holder.messageText != null) {
                    holder.messageText.setVisibility(View.VISIBLE);
                    markwon.setMarkdown(holder.messageText, formatSymbols(responseText));
                }
            } else {
                if (holder.messageText != null) {
                    holder.messageText.setVisibility(View.GONE);
                }
                if (holder.messageContainer != null) {
                    renderMessageBlocks(holder, responseText);
                }
            }
        }
    }

    private void bindAiStreaming(AiViewHolder holder, Message message) {
        if (message.getType() == Message.TYPE_TYPING) {
            if (holder.cardThinkingContainer != null) holder.cardThinkingContainer.setVisibility(View.GONE);
            if (holder.messageText != null) holder.messageText.setVisibility(View.GONE);
            if (holder.messageContainer != null) holder.messageContainer.setVisibility(View.GONE);
            if (holder.typingIndicator != null) {
                holder.typingIndicator.setVisibility(View.VISIBLE);
                startTypingAnimation(holder);
            }
            if (holder.aiActionContainer != null) holder.aiActionContainer.setVisibility(View.GONE);
            return;
        }

        if (holder.aiActionContainer != null) {
            holder.aiActionContainer.setVisibility(View.GONE);
        }

        bindAiContent(holder, message, true);
        bindAiModelDisplay(holder, message);
        bindAiClickListeners(holder, message);
    }

    private void bindAiModelDisplay(AiViewHolder holder, Message message) {
        String mName = message.getModelName();
        if (holder.tvAiModelLabel != null) {
            if (mName != null && !mName.trim().isEmpty()) {
                holder.tvAiModelLabel.setText(mName.toUpperCase(java.util.Locale.US));
            } else {
                holder.tvAiModelLabel.setText(R.string.nex_ai_label);
            }
        }
        if (holder.aiAvatar != null) {
            if (mName != null) {
                String mLower = mName.toLowerCase(java.util.Locale.US);
                if (mLower.contains("vision")) {
                    holder.aiAvatar.setImageResource(R.drawable.ic_vision);
                } else if (mLower.contains("ultra")) {
                    holder.aiAvatar.setImageResource(R.drawable.ic_persona);
                } else if (mLower.contains("pro")) {
                    holder.aiAvatar.setImageResource(R.drawable.app_icon);
                } else {
                    holder.aiAvatar.setImageResource(R.drawable.ic_bolt);
                }
            } else {
                holder.aiAvatar.setImageResource(R.drawable.ic_bolt);
            }
        }
    }

    private void bindAiHolder(AiViewHolder holder, Message message, int position) {
        bindAiModelDisplay(holder, message);

        if (message.getType() == Message.TYPE_TYPING) {
            if (holder.cardThinkingContainer != null) {
                holder.cardThinkingContainer.setVisibility(View.GONE);
            }
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
            if (holder.typingIndicator != null) {
                holder.typingIndicator.setVisibility(View.GONE);
                stopTypingAnimation(holder);
            }

            bindAiContent(holder, message, false);

            if (holder.memoryIndicator != null) {
                if (message.getMemoryTag() != null && !message.getMemoryTag().isEmpty()) {
                    holder.memoryIndicator.setText("• " + message.getMemoryTag());
                    holder.memoryIndicator.setVisibility(View.VISIBLE);
                } else {
                    holder.memoryIndicator.setVisibility(View.GONE);
                }
            }

            if (holder.aiActionContainer != null) {
                boolean isLastMessage = (position == messages.size() - 1);
                boolean showActions = message.isActionsVisible() || (isLastMessage && !isGenerating);
                holder.aiActionContainer.setVisibility(showActions ? View.VISIBLE : View.GONE);
            }

            bindAiClickListeners(holder, message);
        }
    }

    private void bindAiClickListeners(AiViewHolder holder, Message message) {
        View.OnLongClickListener aiLongClickListener = v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos < messages.size()) {
                Message msg = messages.get(pos);
                if (msg.getType() == Message.TYPE_AI) {
                    triggerHaptic(v, android.view.HapticFeedbackConstants.LONG_PRESS);
                    toggleActionsWithTimeout(msg, pos);
                    return true;
                }
            }
            return false;
        };

        holder.itemView.setOnLongClickListener(aiLongClickListener);
        if (holder.messageText != null) {
            holder.messageText.setOnLongClickListener(aiLongClickListener);
        }
        if (holder.messageContainer != null) {
            holder.messageContainer.setOnLongClickListener(aiLongClickListener);
        }
        if (holder.tvThinkingContent != null) {
            holder.tvThinkingContent.setOnLongClickListener(aiLongClickListener);
        }

        if (holder.btnCopy != null) {
            holder.btnCopy.setOnClickListener(v -> {
                triggerHaptic(v, android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                ParsedMessage parsed = parseMessageContent(message.getText());
                String copyText = (parsed.responseContent != null && !parsed.responseContent.isEmpty())
                        ? parsed.responseContent
                        : (message.getText() != null ? message.getText() : "");
                copyToClipboard(v.getContext(), copyText);
            });
        }

        if (holder.btnShare != null) {
            holder.btnShare.setOnClickListener(v -> {
                triggerHaptic(v, android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                ParsedMessage parsed = parseMessageContent(message.getText());
                String shareText = (parsed.responseContent != null && !parsed.responseContent.isEmpty())
                        ? parsed.responseContent
                        : (message.getText() != null ? message.getText() : "");
                shareText(v.getContext(), shareText);
            });
        }

        if (holder.btnRegenerate != null) {
            holder.btnRegenerate.setOnClickListener(v -> {
                triggerHaptic(v, android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                int pos = holder.getBindingAdapterPosition();
                if (actionListener != null && pos != RecyclerView.NO_POSITION) {
                    actionListener.onRegenerate(pos);
                }
            });
        }

        if (holder.btnMore != null) {
            holder.btnMore.setOnClickListener(v -> {
                triggerHaptic(v, android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    showMoreOptions(v.getContext(), holder.btnMore, message, pos);
                }
            });
        }
    }

    private void renderMessageBlocks(AiViewHolder holder, String text) {
        holder.messageContainer.setVisibility(View.VISIBLE);

        View.OnLongClickListener blockLongClick = v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos < messages.size()) {
                Message msg = messages.get(pos);
                if (msg.getType() == Message.TYPE_AI) {
                    triggerHaptic(v, android.view.HapticFeedbackConstants.LONG_PRESS);
                    toggleActionsWithTimeout(msg, pos);
                    return true;
                }
            }
            return false;
        };

        List<MessageBlock> blocks = parseBlocks(text);
        float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;

        int currentChildCount = holder.messageContainer.getChildCount();
        boolean canReuse = (currentChildCount == blocks.size());
        if (canReuse) {
            for (int i = 0; i < blocks.size(); i++) {
                View child = holder.messageContainer.getChildAt(i);
                MessageBlock block = blocks.get(i);
                if (block.type == MessageBlock.TYPE_TEXT && !(child instanceof TextView)) {
                    canReuse = false;
                    break;
                } else if (block.type == MessageBlock.TYPE_CODE && (child instanceof TextView || child.findViewById(R.id.tvCode) == null)) {
                    canReuse = false;
                    break;
                }
            }
        }

        if (canReuse) {
            // Fast in-place update without view recreation / inflation
            for (int i = 0; i < blocks.size(); i++) {
                View child = holder.messageContainer.getChildAt(i);
                MessageBlock block = blocks.get(i);
                if (block.type == MessageBlock.TYPE_TEXT) {
                    TextView tv = (TextView) child;
                    markwon.setMarkdown(tv, block.content);
                } else if (block.type == MessageBlock.TYPE_CODE) {
                    TextView tvLanguage = child.findViewById(R.id.tvLanguage);
                    TextView tvCode = child.findViewById(R.id.tvCode);
                    if (tvLanguage != null) {
                        tvLanguage.setText(block.language.toUpperCase(java.util.Locale.US));
                    }
                    if (tvCode != null) {
                        tvCode.setText(SyntaxHighlighter.formatCode(block.content, block.language));
                    }
                }
            }
            return;
        }

        holder.messageContainer.removeAllViews();
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
                tv.setOnLongClickListener(blockLongClick);
                holder.messageContainer.addView(tv);
            } else if (block.type == MessageBlock.TYPE_CODE) {
                View codeBlockView = LayoutInflater.from(holder.itemView.getContext())
                    .inflate(R.layout.item_message_code_block, holder.messageContainer, false);

                TextView tvLanguage = codeBlockView.findViewById(R.id.tvLanguage);
                TextView tvCode = codeBlockView.findViewById(R.id.tvCode);
                View btnCopyCode = codeBlockView.findViewById(R.id.btnCopyCode);
                TextView tvCopyStatus = codeBlockView.findViewById(R.id.tvCopyStatus);

                if (tvLanguage != null) {
                    tvLanguage.setText(block.language.toUpperCase(java.util.Locale.US));
                }
                if (tvCode != null) {
                    tvCode.setText(SyntaxHighlighter.formatCode(block.content, block.language));
                }

                if (btnCopyCode != null) {
                    btnCopyCode.setOnClickListener(v -> {
                        triggerHaptic(v, android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                        ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Code block", block.content);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clip);
                            if (tvCopyStatus != null) {
                                tvCopyStatus.setText("Copied!");
                            }
                            v.postDelayed(() -> {
                                if (tvCopyStatus != null) {
                                    tvCopyStatus.setText("Copy code");
                                }
                            }, 2000);
                        }
                    });
                }

                codeBlockView.setOnLongClickListener(blockLongClick);
                holder.messageContainer.addView(codeBlockView);
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
        // Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void shareText(Context context, String text) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            context.startActivity(Intent.createChooser(intent, "Share response"));
        } catch (Exception e) {
            e.printStackTrace();
            // Toast.makeText(context, "Failed to share", Toast.LENGTH_SHORT).show();
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

    private boolean isHapticsEnabled(Context context) {
        if (preferenceManager != null) {
            return preferenceManager.isHapticFeedbackEnabled();
        }
        return new PreferenceManager(context).isHapticFeedbackEnabled();
    }

    private void triggerHaptic(View view, int type) {
        if (view != null && isHapticsEnabled(view.getContext())) {
            view.performHapticFeedback(type, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        ImageView ivUserAttachedImage;
        View cardAttachedImage;
        View cardAttachedDocument;
        TextView tvUserAttachedDocName;

        UserViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            ivUserAttachedImage = itemView.findViewById(R.id.ivUserAttachedImage);
            cardAttachedImage = itemView.findViewById(R.id.card_attached_image);
            cardAttachedDocument = itemView.findViewById(R.id.card_attached_document);
            tvUserAttachedDocName = itemView.findViewById(R.id.tvUserAttachedDocName);
        }
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        ImageView aiAvatar;
        TextView tvAiModelLabel;
        View cardThinkingContainer;
        View layoutThinkingHeader;
        ImageView ivThinkingIcon;
        TextView tvThinkingTitle;
        ImageView ivThinkingChevron;
        TextView tvThinkingContent;
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
            aiAvatar = itemView.findViewById(R.id.aiAvatar);
            tvAiModelLabel = itemView.findViewById(R.id.tvAiModelLabel);
            cardThinkingContainer = itemView.findViewById(R.id.cardThinkingContainer);
            layoutThinkingHeader = itemView.findViewById(R.id.layoutThinkingHeader);
            ivThinkingIcon = itemView.findViewById(R.id.ivThinkingIcon);
            tvThinkingTitle = itemView.findViewById(R.id.tvThinkingTitle);
            ivThinkingChevron = itemView.findViewById(R.id.ivThinkingChevron);
            tvThinkingContent = itemView.findViewById(R.id.tvThinkingContent);
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