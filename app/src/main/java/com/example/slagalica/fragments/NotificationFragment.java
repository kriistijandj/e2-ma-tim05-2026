package com.example.slagalica.fragments;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
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
import java.util.stream.Collectors;

public class NotificationFragment extends Fragment {

    private RecyclerView recyclerView;
    private List<NotificationModel> fullList;
    private List<NotificationModel> filteredList;
    private NotificationAdapter adapter;

    public NotificationFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notification, container, false);

        initData();
        initViews(view);
        setupFilters(view);
        setupSearch(view);

        return view;
    }

    private void initViews(View v) {
        recyclerView = v.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        filteredList = new ArrayList<>(fullList);
        adapter = new NotificationAdapter(filteredList);
        recyclerView.setAdapter(adapter);
    }

    private void initData() {
        fullList = new ArrayList<>();
        fullList.add(new NotificationModel("Chat", "Marko: Aj igraj", "2 min", false, NotificationType.CHAT));
        fullList.add(new NotificationModel("Rang", "Osvojio si 1. mesto", "10 min", true, NotificationType.RANK));
        fullList.add(new NotificationModel("Nagrada", "Dobio si 5 tokena", "1h", false, NotificationType.REWARD));
    }

    private void setupFilters(View v) {
        v.findViewById(R.id.btnAll).setOnClickListener(view -> updateList(new ArrayList<>(fullList)));

        v.findViewById(R.id.btnRead).setOnClickListener(view ->
                updateList(fullList.stream().filter(NotificationModel::isRead).collect(Collectors.toList()))
        );

        v.findViewById(R.id.btnUnread).setOnClickListener(view ->
                updateList(fullList.stream().filter(n -> !n.isRead()).collect(Collectors.toList()))
        );
    }

    private void setupSearch(View v) {
        SearchView searchView = v.findViewById(R.id.search_text);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) { return false; }

            @Override
            public boolean onQueryTextChange(String newText) {
                List<NotificationModel> searched = fullList.stream()
                        .filter(n -> n.getTitle().toLowerCase().contains(newText.toLowerCase()) ||
                                n.getMessage().toLowerCase().contains(newText.toLowerCase()))
                        .collect(Collectors.toList());
                updateList(searched);
                return true;
            }
        });
    }

    private void updateList(List<NotificationModel> newList) {
        filteredList.clear();
        filteredList.addAll(newList);
        adapter.notifyDataSetChanged();
    }
}