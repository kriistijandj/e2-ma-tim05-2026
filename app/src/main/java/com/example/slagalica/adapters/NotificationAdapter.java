package com.example.slagalica.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.models.NotificationModel;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private List<NotificationModel> modelList;

    public NotificationAdapter(List<NotificationModel> list)
    {
        this.modelList =list;
    }
    public  static class ViewHolder extends RecyclerView.ViewHolder{
        public final TextView title,message,time;

        public ViewHolder(@NonNull View itemView)
        {
            super(itemView);
            title=itemView.findViewById(R.id.notifTitle);
            message=itemView.findViewById(R.id.notifMessage);
            time=itemView.findViewById(R.id.notifTime);

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
    public void onBindViewHolder(@NonNull NotificationAdapter.ViewHolder holder, int position) {
        NotificationModel notif = modelList.get(position);
        holder.title.setText(notif.getTitle());
        holder.message.setText((notif.getMessage()));
        holder.time.setText(notif.getTime());

        // Ako nije pročitano bice boldovano
        if (!notif.isRead()) {
            holder.title.setAlpha(1f);
        } else {
            holder.title.setAlpha(0.5f);
        }

        // Klik -> označi kao pročitano
        holder.itemView.setOnClickListener(v -> {
            notif.setRead(true);
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return modelList.size();
    }
}
