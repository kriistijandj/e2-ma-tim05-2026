package com.example.slagalica.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.adapters.ChatAdapter;
import com.example.slagalica.viewmodel.ChatViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ChatFragment extends Fragment {
    private ChatViewModel viewModel;
    private ChatAdapter adapter;
    private RecyclerView recyclerView;
    private EditText etMessage;
    private String currentUserId;
    private String currentUsername;
    private String userRegion;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        recyclerView = view.findViewById(R.id.rv_chat);
        etMessage = view.findViewById(R.id.et_message);
        Button btnSend = view.findViewById(R.id.btn_send);

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);

        // Učitaj region i username korisnika iz Firestore-a pa tek onda startuj listener
        FirebaseFirestore.getInstance().collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(doc -> {
                    userRegion = doc.getString("region");
                    currentUsername = doc.getString("username");

                    adapter = new ChatAdapter(new java.util.ArrayList<>(), currentUserId);
                    recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
                    recyclerView.setAdapter(adapter);

                    viewModel.startListening(userRegion);
                    viewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
                        adapter = new ChatAdapter(messages, currentUserId);
                        recyclerView.setAdapter(adapter);
                        recyclerView.scrollToPosition(messages.size() - 1);
                    });
                });

        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (!text.isEmpty() && userRegion != null) {
                viewModel.sendMessage(userRegion, currentUserId, currentUsername, text);
                etMessage.setText("");
                handleChatMission(currentUserId);
            }
        });
    }
    private void handleChatMission(String uid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        com.google.firebase.firestore.DocumentReference userRef = db.collection("users").document(uid);

        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(userRef);
            Boolean alreadySent = snapshot.getBoolean("dailyMissions.sentChatMessage");

            if (alreadySent == null || !alreadySent) {
                transaction.update(userRef, "dailyMissions.sentChatMessage", true);
                transaction.update(userRef, "stars", com.google.firebase.firestore.FieldValue.increment(3));
                transaction.update(userRef, "weeklyStars", com.google.firebase.firestore.FieldValue.increment(3));
                transaction.update(userRef, "monthlyStars", com.google.firebase.firestore.FieldValue.increment(3));
            }
            return null;
        });
    }
}