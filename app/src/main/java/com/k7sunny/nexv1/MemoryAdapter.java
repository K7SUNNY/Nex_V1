package com.k7sunny.nexv1;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class MemoryAdapter extends RecyclerView.Adapter<MemoryAdapter.ViewHolder> {

    private List<Memory> memories;
    private OnMemoryLongClickListener listener;

    public interface OnMemoryLongClickListener {
        void onMemoryLongClick(Memory memory, int position);
    }

    public MemoryAdapter(List<Memory> memories, OnMemoryLongClickListener listener) {
        this.memories = memories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_memory, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Memory memory = memories.get(position);
        holder.tvTitle.setText(memory.getTitle());
        holder.tvContent.setText(memory.getContent());
        
        PreferenceManager pm = new PreferenceManager(holder.card.getContext());
        holder.card.setHapticFeedbackEnabled(pm.isHapticFeedbackEnabled());
        
        holder.card.setOnLongClickListener(v -> {
            listener.onMemoryLongClick(memory, position);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return memories.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView tvTitle, tvContent;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.memoryCard);
            tvTitle = itemView.findViewById(R.id.memoryTitle);
            tvContent = itemView.findViewById(R.id.memoryContent);
        }
    }
}
