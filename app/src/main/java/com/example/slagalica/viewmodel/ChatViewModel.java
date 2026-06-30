package com.example.slagalica.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalica.models.ChatMessage;
import com.example.slagalica.repository.ChatRepository;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class ChatViewModel extends ViewModel {
    private final ChatRepository repository = new ChatRepository();
    private final MutableLiveData<List<ChatMessage>> messages = new MutableLiveData<>(new ArrayList<>());
    private ListenerRegistration listenerRegistration;

    public LiveData<List<ChatMessage>> getMessages() {
        return messages;
    }

    public void startListening(String region) {
        listenerRegistration = repository.getMessagesQuery(region)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    List<ChatMessage> current = new ArrayList<>(
                            messages.getValue() != null ? messages.getValue() : new ArrayList<>()
                    );
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            ChatMessage msg = dc.getDocument().toObject(ChatMessage.class);
                            msg.setId(dc.getDocument().getId());
                            current.add(msg);
                        }
                    }
                    messages.setValue(current);
                });
    }

    public void sendMessage(String region, String senderId, String senderUsername, String text) {
        repository.sendMessage(region, senderId, senderUsername, text);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (listenerRegistration != null) listenerRegistration.remove();
    }
}