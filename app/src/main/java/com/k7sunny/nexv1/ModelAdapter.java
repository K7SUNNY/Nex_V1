package com.k7sunny.nexv1;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.ViewHolder> {

    public interface OnModelSelectedListener {
        void onModelSelected(ModelItem item);
    }

    public interface OnModelDeleteListener {
        void onModelDelete(ModelItem item);
    }

    private final List<ModelItem> items;
    private String selectedKey;
    private final OnModelSelectedListener listener;
    private ModelManager modelManager;
    private OnModelDeleteListener deleteListener;

    public ModelAdapter(List<ModelItem> items, String selectedKey, OnModelSelectedListener listener) {
        this(items, selectedKey, listener, null, null);
    }

    public ModelAdapter(List<ModelItem> items, String selectedKey, OnModelSelectedListener listener, ModelManager modelManager, OnModelDeleteListener deleteListener) {
        this.items = items;
        this.selectedKey = selectedKey;
        this.listener = listener;
        this.modelManager = modelManager;
        this.deleteListener = deleteListener;
    }

    public void updateSelection(String key) {
        if (key != null && !key.equals(selectedKey)) {
            selectedKey = key;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_model_selection, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ModelItem item = items.get(position);
        holder.tvName.setText(item.getName());
        holder.tvSize.setText(item.getSize());
        holder.tvDesc.setText(item.getDescription());
        holder.icon.setImageResource(item.getIconRes());

        if (item.getTag() != null && !item.getTag().isEmpty()) {
            holder.tvTag.setText(item.getTag());
            holder.tvTag.setVisibility(View.VISIBLE);
        } else {
            holder.tvTag.setVisibility(View.GONE);
        }

        boolean isSelected = item.getKey().equals(selectedKey);
        holder.check.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        holder.check.setImageTintList(ColorStateList.valueOf(0xFF007AFF));

        int activeStrokeColor = 0xFF007AFF; // Blue
        if (isSelected && "vision".equals(item.getKey())) {
            activeStrokeColor = 0xFFAF52DE; // Purple accent for vision
        }
        int inactiveStrokeColor = 0xFF1E2129; // Border color
        int activeBgColor = 0xFF0D0E12; // Active card bg
        int inactiveBgColor = 0xFF0D0E12; // Standard card bg

        holder.card.setStrokeColor(ColorStateList.valueOf(isSelected ? activeStrokeColor : inactiveStrokeColor));
        holder.card.setCardBackgroundColor(ColorStateList.valueOf(isSelected ? activeBgColor : inactiveBgColor));
        holder.card.setStrokeWidth(isSelected ? 3 : 1);
        holder.card.setCardElevation(0f);

        holder.tvName.setTextColor(isSelected ? 0xFFFFFFFF : 0xFFA0A4B0);
        holder.tvDesc.setTextColor(isSelected ? 0xFF8E8E93 : 0xFF555862);
        holder.tvSize.setTextColor(isSelected ? 0xFF8E8E93 : 0xFF555862);
        holder.tvTag.setTextColor(isSelected ? 0xFFFFFFFF : 0xFF8E8E93);
        holder.tvTag.setAlpha(isSelected ? 1.0f : 0.7f);
        holder.icon.setImageAlpha(isSelected ? 255 : 160);
        holder.icon.setImageTintList(ColorStateList.valueOf(isSelected ? 0xFFFFFFFF : 0xFF7A7E8C));

        boolean isDownloaded = modelManager != null && modelManager.isModelFilePresentWithCorrectSize(item.getKey());
        if (holder.btnDelete != null) {
            holder.btnDelete.setVisibility(isDownloaded && deleteListener != null ? View.VISIBLE : View.GONE);
            holder.btnDelete.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onModelDelete(item);
                }
            });
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onModelSelected(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView icon;
        final ImageView check;
        final ImageButton btnDelete;
        final TextView tvName;
        final TextView tvTag;
        final TextView tvSize;
        final TextView tvDesc;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_model_item);
            icon = itemView.findViewById(R.id.icon_model);
            check = itemView.findViewById(R.id.check_model);
            btnDelete = itemView.findViewById(R.id.btn_delete_model);
            tvName = itemView.findViewById(R.id.tv_model_name);
            tvTag = itemView.findViewById(R.id.tv_model_tag);
            tvSize = itemView.findViewById(R.id.tv_model_size);
            tvDesc = itemView.findViewById(R.id.tv_model_desc);
        }
    }
}
