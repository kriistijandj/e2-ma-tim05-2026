package com.example.slagalica.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.adapters.NotificationAdapter;
import com.example.slagalica.models.NotificationModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NotificationFragment extends Fragment {

    private RecyclerView recyclerView;
    private List<NotificationModel> fullList = new ArrayList<>();
    private NotificationAdapter adapter;
    private ListenerRegistration firestoreListener;
    private String currentFilter = "ALL"; // Prati trenutni filter ("ALL", "READ", "UNREAD")
    private String currentSearchQuery = "";

    public NotificationFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notification, container, false);

        initViews(view);
        setupFilters(view);
        setupSearch(view);
        listenForNotifications(); // Slušaj promene sa Firebase-a



        return view;
    }

    private void initViews(View v) {
        recyclerView = v.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Adapter inicijalizujemo sa praznom listom koja će se puniti sa Firebase-a
        adapter = new NotificationAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);
        //Samo za testiranje posle obrisi!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        v.findViewById(R.id.btnTest).setOnClickListener(view -> {
            generateDummyNotification();

        });
    }

    private void listenForNotifications() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : "test_user_id";
        // ⚠️ Zameni "test_user_id" sa stvarnim login sistemom kolege (Student 1) kada povežete sisteme

        // Slušamo podkolekciju specifičnog korisnika, sortirano od najnovijih
        firestoreListener = FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUserId)
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Toast.makeText(getContext(), "Greška pri učitavanju", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (value != null) {
                        List<NotificationModel> tempList = new ArrayList<>();
                        for (com.google.firebase.firestore.DocumentSnapshot doc : value.getDocuments()) {
                            NotificationModel model = doc.toObject(NotificationModel.class);
                            if (model != null) {
                                model.setId(doc.getId()); // Ručno mu prosleđujemo ID dokumenta iz baze!
                                tempList.add(model);
                            }
                        }
                        fullList = tempList;
                        applyFilterAndSearch(); // Primenjuje trenutno aktivne filtere na nove podatke
                    }
                });
    }

    private void setupFilters(View v) {
        v.findViewById(R.id.btnAll).setOnClickListener(view -> {
            currentFilter = "ALL";
            applyFilterAndSearch();
        });

        v.findViewById(R.id.btnRead).setOnClickListener(view -> {
            currentFilter = "READ";
            applyFilterAndSearch();
        });

        v.findViewById(R.id.btnUnread).setOnClickListener(view -> {
            currentFilter = "UNREAD";
            applyFilterAndSearch();
        });
    }

    private void setupSearch(View v) {
        SearchView searchView = v.findViewById(R.id.search_text);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) { return false; }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentSearchQuery = newText.toLowerCase();
                applyFilterAndSearch();
                return true;
            }
        });
    }

    // Centralizovana logika koja spaja i pretragu i tabove (Pročitano/Nepročitano)
    private void applyFilterAndSearch() {
        List<NotificationModel> processedList = fullList.stream()
                .filter(n -> {
                    // 1. Filtriranje po read/unread statusu
                    if (currentFilter.equals("READ")) return n.getIsRead();
                    if (currentFilter.equals("UNREAD")) return !n.getIsRead();
                    return true;
                })
                .filter(n -> {
                    // 2. Filtriranje po tekstualnoj pretrazi
                    if (currentSearchQuery.isEmpty()) return true;
                    return (n.getTitle() != null && n.getTitle().toLowerCase().contains(currentSearchQuery)) ||
                            (n.getMessage() != null && n.getMessage().toLowerCase().contains(currentSearchQuery));
                })
                .collect(Collectors.toList());

        // Prosleđujemo prečišćenu listu adapteru kroz bezbednu metodu
        adapter.updateData(processedList);
    }
    private void generateDummyNotification() {
        String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ?
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : "test_user_id";
        android.util.Log.d("TEST_NOTIF", "Kliknut TEST");
        // Kreiramo objekat sa nasumičnim podacima
        java.util.Map<String, Object> dummyNotif = new java.util.HashMap<>();
        dummyNotif.put("title", "Osvojena Nagrada! 🏆");
        dummyNotif.put("message", "Plasirali ste se na 1. mesto na nedeljnoj rang listi. Dobijate 5 tokena!");
        dummyNotif.put("timestamp", System.currentTimeMillis());
        dummyNotif.put("isRead", false);
        dummyNotif.put("type", "REWARDS");

        // Upisujemo direktno u Firestore
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUserId)
                .collection("notifications")
                .add(dummyNotif)
                .addOnSuccessListener(documentReference -> {

                    android.util.Log.d("TEST_NOTIF", "Firestore upis uspeo");

                    // Ovdje ujedno testiramo tvoj NotificationHelper koji si napisao!
                    // Ovo će okinuti i sistemsku push notifikaciju na telefonu sa zvukom
                    com.example.slagalica.helper.NotificationHelper.showNotification(
                            getContext(),
                            com.example.slagalica.activities.HomeActivity.REWARD_CHANNEL_ID,
                            (int) System.currentTimeMillis(), // Nasumičan ID notifikacije
                            "Osvojena Nagrada! 🏆",
                            "Dobijate 5 tokena za 1. mesto!"
                    );
                }
                )
                .addOnFailureListener(e ->
                        android.util.Log.e("TEST_NOTIF", e.getMessage()));

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Sprečavamo curenje memorije (Memory Leak) skidanjem osluškivača kada fragment umre
        if (firestoreListener != null) {
            firestoreListener.remove();
        }
    }
}