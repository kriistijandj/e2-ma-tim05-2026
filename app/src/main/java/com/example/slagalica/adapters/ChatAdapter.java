package com.example.slagalica.adapters;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.models.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {
    private final List<ChatMessage> messages;
    private final String currentUserId;

    public ChatAdapter(List<ChatMessage> messages, String currentUserId) {
        this.messages = messages;
        this.currentUserId = currentUserId;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        boolean isMe = msg.getSenderId().equals(currentUserId);

        holder.tvText.setText(msg.getText());
        holder.tvSender.setText(isMe ? "" : msg.getSenderUsername());
        holder.tvSender.setVisibility(isMe ? View.GONE : View.VISIBLE);

        if (msg.getTimestamp() != null) {
            Date date = msg.getTimestamp().toDate();
            String formatted = new SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(date);
            holder.tvTime.setText(formatted);
        }

        // levo/desno pozicioniranje
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.tvText.getLayoutParams();
        holder.container.setGravity(isMe ? Gravity.END : Gravity.START);
        holder.tvText.setBackgroundResource(isMe ? R.drawable.bg_bubble_me : R.drawable.bg_bubble_other);
    }

    @Override
    public int getItemCount() { return messages.size(); }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView tvSender, tvText, tvTime;
        LinearLayout container;

        ChatViewHolder(View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tv_sender);
            tvText = itemView.findViewById(R.id.tv_message_text);
            tvTime = itemView.findViewById(R.id.tv_time);
            container = itemView.findViewById(R.id.message_container);
        }
    }
}