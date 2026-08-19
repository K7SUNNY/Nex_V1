package com.k7sunny.nexv1;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

    private final List<ModelItem> items;
    private String selectedKey;
    private final OnModelSelectedListener listener;

    public ModelAdapter(List<ModelItem> items, String selectedKey, OnModelSelectedListener listener) {
        this.items = items;
        this.selectedKey = selectedKey;
        this.listener = listener;
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

        int activeStrokeColor = 0xFF007AFF; // Blue
        if (isSelected && "vision".equals(item.getKey())) {
            activeStrokeColor = 0xFFAF52DE; // Purple accent for vision
        }
        int inactiveStrokeColor = 0x1AFFFFFF; // Translucent white
        int activeBgColor = 0xFF1C1C1E; // Active card bg
        int inactiveBgColor = 0xFF111111; // Standard card bg

        holder.card.setStrokeColor(isSelected ? activeStrokeColor : inactiveStrokeColor);
        holder.card.setCardBackgroundColor(ColorStateList.valueOf(isSelected ? activeBgColor : inactiveBgColor));

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
        final TextView tvName;
        final TextView tvTag;
        final TextView tvSize;
        final TextView tvDesc;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_model_item);
            icon = itemView.findViewById(R.id.icon_model);
            check = itemView.findViewById(R.id.check_model);
            tvName = itemView.findViewById(R.id.tv_model_name);
            tvTag = itemView.findViewById(R.id.tv_model_tag);
            tvSize = itemView.findViewById(R.id.tv_model_size);
            tvDesc = itemView.findViewById(R.id.tv_model_desc);
        }
    }
}
