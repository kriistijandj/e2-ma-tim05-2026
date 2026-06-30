package com.example.slagalica.adapters;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.models.NotificationModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private List<NotificationModel> modelList;

    public NotificationAdapter(List<NotificationModel> list) {
        this.modelList = list;
    }

    // Bezbedna metoda za ažuriranje liste iz Fragmenta bez direktnog čišćenja referenci
    public void updateData(List<NotificationModel> newList) {
        this.modelList = newList;
        notifyDataSetChanged();
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
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationModel notif = modelList.get(position);

        holder.title.setText(notif.getTitle());
        holder.message.setText(notif.getMessage());

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        holder.time.setText(sdf.format(new Date(notif.getTimestamp())));

        // Podešavanje ikonica na osnovu tipa notifikacije (Zahtev a.)
        if (holder.icon != null && notif.getType() != null) {
            switch (notif.getType()) {
                case "CHAT":
                    holder.icon.setImageResource(R.drawable.avatar); // Ubaci svoje resurse
                    break;
                case "RANKING":
                    holder.icon.setImageResource(R.drawable.ic_skocko);
                    break;
                case "REWARDS":
                    holder.icon.setImageResource(R.drawable.ic_srce);
                    break;
                default:
                    holder.icon.setImageResource(R.drawable.ic_skocko); // Ostalo
                    break;
            }
        }

        // Vizuelni stil (Pročitano vs Nepročitano)
        if (!notif.getIsRead()) {
            holder.title.setTypeface(null, Typeface.BOLD);
            holder.title.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.textDark));
            holder.message.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.textDark));
            holder.itemView.setAlpha(1.0f);
            if (holder.icon != null) holder.icon.setAlpha(1.0f);
        } else {
            holder.title.setTypeface(null, Typeface.NORMAL);
            holder.title.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.textGray));
            holder.message.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.textGray));
            holder.itemView.setAlpha(0.6f); // Malo više izbledelo radi jasnijeg kontrasta
            if (holder.icon != null) holder.icon.setAlpha(0.4f);
        }

        // Klik na notifikaciju menja stanje u Firestore bazi (Zahtev c.)
        holder.itemView.setOnClickListener(v -> {
            if (!notif.getIsRead()) {
                String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                        FirebaseAuth.getInstance().getCurrentUser().getUid() : "test_user_id";

                if (notif.getId() != null) {
                    // Ažuriramo direktno u bazi polje isRead na true
                    FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(currentUserId)
                            .collection("notifications")
                            .document(notif.getId())
                            .update("isRead", true);

                    // Pošto u Fragmentu imamo SnapshotListener, on će sam detektovati
                    // ovu promenu u bazi i osvežiti interfejs automatski!
                }
            }

            if ("CHAT".equals(notif.getType())) {
                Navigation.findNavController(holder.itemView)
                        .navigate(R.id.nav_chat);
            }
        });
    }

    @Override
    public int getItemCount() {
        return modelList.size();
    }
}