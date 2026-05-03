package com.example.slagalica.fragments;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.example.slagalica.R;
import com.example.slagalica.adapters.NotificationAdapter;
import com.example.slagalica.models.NotificationModel;
import com.example.slagalica.models.NotificationType;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link NotificationFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class NotificationFragment extends Fragment {


    private RecyclerView recyclerView;
    private List<NotificationModel> fullList;
    private List<NotificationModel> filteredList;
    private NotificationAdapter adapter;
    private Button btnAll, btnRead, btnUnread;

    public NotificationFragment() {
        // Required empty public constructor
    }
    public List<NotificationModel> getAll(List<NotificationModel> list) {
        return new ArrayList<>(list);
    }

    public List<NotificationModel> getUnread(List<NotificationModel> list) {
        List<NotificationModel> res = new ArrayList<>();
        for (NotificationModel n : list) {
            if (!n.isRead()) res.add(n);
        }
        return res;
    }

    public List<NotificationModel> getRead(List<NotificationModel> list) {
        List<NotificationModel> res = new ArrayList<>();
        for (NotificationModel n : list) {
            if (n.isRead()) res.add(n);
        }
        return res;
    }
    public static NotificationFragment newInstance(String param1, String param2) {
        NotificationFragment fragment = new NotificationFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_notification, container, false);

        btnAll = view.findViewById(R.id.btnAll);
        btnRead = view.findViewById(R.id.btnRead);
        btnUnread = view.findViewById(R.id.btnUnread);

        btnAll.setOnClickListener(v -> {
            filteredList.clear();
            filteredList.addAll(getAll(fullList));
            adapter.notifyDataSetChanged();
        });

        btnRead.setOnClickListener(v -> {
            filteredList.clear();
            filteredList.addAll(getRead(fullList));
            adapter.notifyDataSetChanged();
        });

        btnUnread.setOnClickListener(v -> {
            filteredList.clear();
            filteredList.addAll(getUnread(fullList));
            adapter.notifyDataSetChanged();
        });

        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        fullList = new ArrayList<>();

        fullList.add(new NotificationModel("Chat", "Marko: Aj igraj", "2 min", false, NotificationType.CHAT));
        fullList.add(new NotificationModel("Rang", "Osvojio si 1. mesto", "10 min", true, NotificationType.RANK));
        fullList.add(new NotificationModel("Nagrada", "Dobio si 5 tokena", "1h", false, NotificationType.REWARD));

        filteredList = getAll(fullList);
        adapter = new NotificationAdapter(filteredList);
        recyclerView.setAdapter(adapter);

        return view;
    }

}