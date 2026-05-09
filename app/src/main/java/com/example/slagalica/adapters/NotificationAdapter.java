package com.example.slagalica.adapters;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.models.NotificationModel;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private List<NotificationModel> modelList;

    public NotificationAdapter(List<NotificationModel> list) {
        this.modelList = list;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView title, message, time;
        public final ImageView icon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvNotifTitle);
            message = itemView.findViewById(R.id.tvNotifMessage);
            time = itemView.findViewById(R.id.tvNotifTime);
            icon = itemView.findViewById(R.id.tvNotifIcon);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate-ujemo beli oblačić
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationModel notif = modelList.get(position);

        holder.title.setText(notif.getTitle());
        holder.message.setText(notif.getMessage());
        holder.time.setText(notif.getTime());

        // Logika za bele oblačiće (Pročitano vs Nepročitano)
        if (!notif.isRead()) {
            // Nepročitano: Podebljan tekst i puna vidljivost
            holder.title.setTypeface(null, Typeface.BOLD);
            holder.title.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.textDark));
            holder.message.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.textDark));
            holder.itemView.setAlpha(1.0f);

            // Ako imaš ikonu, možemo joj dati jaču boju
            if (holder.icon != null) {
                holder.icon.setAlpha(1.0f);
            }
        } else {
            // Pročitano: Običan tekst i blago izbledelo (da se vidi razlika)
            holder.title.setTypeface(null, Typeface.NORMAL);
            holder.title.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.textGray));
            holder.message.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.textGray));
            holder.itemView.setAlpha(0.8f); // Ceo oblačić malo izbledi

            if (holder.icon != null) {
                holder.icon.setAlpha(0.5f);
            }
        }

        // Klik na oblačić označava poruku kao pročitanu
        holder.itemView.setOnClickListener(v -> {
            if (!notif.isRead()) {
                notif.setRead(true);
                notifyItemChanged(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return modelList.size();
    }
}