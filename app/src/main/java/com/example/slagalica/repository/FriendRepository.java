package com.example.slagalica.repository;

import androidx.annotation.NonNull;

import com.example.slagalica.models.FriendModel;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FriendRepository {

    private final FirebaseFirestore fs = FirebaseFirestore.getInstance();
    private final DatabaseReference rtdb = FirebaseDatabase.getInstance().getReference();

    private ValueEventListener sentInviteListener;

    // ─── Interfaces ───────────────────────────────────────────────────────────

    public interface FriendsCallback {
        void onLoaded(List<FriendModel> friends);
    }

    public interface SearchCallback {
        void onFound(FriendModel user);
        void onNotFound();
        void onError(String msg);
    }

    public interface SimpleCallback {
        void onResult(boolean success, String message);
    }

    public interface MatchCreatedCallback {
        void onMatchCreated(String matchId, String role);
        void onError(String msg);
    }

    public interface SentInviteListener {
        void onAccepted(String matchId);
        void onRejected();
    }

    public interface IncomingInviteListener {
        void onInviteReceived(String inviterId, String inviterUsername);
        void onInviteCancelled();
    }

    // ─── Friend list ──────────────────────────────────────────────────────────

    public void loadFriends(String myUid, FriendsCallback callback) {
        fs.collection("users").document(myUid).collection("friends")
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        callback.onLoaded(new ArrayList<>());
                        return;
                    }
                    List<String> uids = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        uids.add(doc.getId());
                    }
                    fetchFriendProfiles(uids, callback);
                })
                .addOnFailureListener(e -> callback.onLoaded(new ArrayList<>()));
    }

    private void fetchFriendProfiles(List<String> uids, FriendsCallback callback) {
        List<FriendModel> result = new ArrayList<>();
        AtomicInteger remaining = new AtomicInteger(uids.size());

        for (String uid : uids) {
            fs.collection("users").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            FriendModel m = new FriendModel();
                            m.uid = uid;
                            m.username = doc.getString("username");
                            m.stars = doc.getLong("stars") != null ? doc.getLong("stars") : 0;
                            m.league = doc.getLong("league") != null ? doc.getLong("league").intValue() : 0;
                            m.avatarId = doc.getLong("avatarId") != null ? doc.getLong("avatarId").intValue() : 0;
                            m.online = Boolean.TRUE.equals(doc.getBoolean("online"));

                            // inMatch se sada čita iz RTDB-a, ne iz Firestore-a
                            rtdb.child("players").child(uid).child("inMatch")
                                    .get()
                                    .addOnSuccessListener(snap -> {
                                        m.inMatch = Boolean.TRUE.equals(snap.getValue(Boolean.class));
                                        synchronized (result) { result.add(m); }
                                        if (remaining.decrementAndGet() == 0) callback.onLoaded(result);
                                    })
                                    .addOnFailureListener(e -> {
                                        m.inMatch = false;
                                        synchronized (result) { result.add(m); }
                                        if (remaining.decrementAndGet() == 0) callback.onLoaded(result);
                                    });
                        } else {
                            if (remaining.decrementAndGet() == 0) callback.onLoaded(result);
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (remaining.decrementAndGet() == 0) callback.onLoaded(result);
                    });
        }
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    public void searchByUsername(String username, SearchCallback callback) {
        fs.collection("users")
                .whereEqualTo("username", username)
                .whereEqualTo("isEnabled", true)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        callback.onNotFound();
                        return;
                    }
                    DocumentSnapshot doc = snap.getDocuments().get(0);
                    FriendModel m = new FriendModel();
                    m.uid = doc.getId();
                    m.username = doc.getString("username");
                    m.stars = doc.getLong("stars") != null ? doc.getLong("stars") : 0;
                    m.league = doc.getLong("league") != null ? doc.getLong("league").intValue() : 0;
                    m.avatarId = doc.getLong("avatarId") != null ? doc.getLong("avatarId").intValue() : 0;
                    m.online = Boolean.TRUE.equals(doc.getBoolean("online"));
                    m.inMatch = Boolean.TRUE.equals(doc.getBoolean("inMatch"));
                    callback.onFound(m);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ─── Add / Remove ─────────────────────────────────────────────────────────

    public void addFriend(String myUid, FriendModel friend, SimpleCallback callback) {
        fs.collection("users").document(myUid).collection("friends").document(friend.uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        callback.onResult(false, "Već ste prijatelji");
                        return;
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("addedAt", System.currentTimeMillis());

                    fs.collection("users").document(myUid).collection("friends").document(friend.uid)
                            .set(data)
                            .addOnSuccessListener(v -> {
                                // Mutual friendship
                                fs.collection("users").document(friend.uid)
                                        .collection("friends").document(myUid).set(data);
                                callback.onResult(true, "Prijatelj dodat!");
                            })
                            .addOnFailureListener(e -> callback.onResult(false, e.getMessage()));
                });
    }

    public void removeFriend(String myUid, String friendUid, SimpleCallback callback) {
        fs.collection("users").document(myUid).collection("friends").document(friendUid)
                .delete()
                .addOnSuccessListener(v -> {
                    fs.collection("users").document(friendUid)
                            .collection("friends").document(myUid).delete();
                    callback.onResult(true, "Prijatelj uklonjen");
                })
                .addOnFailureListener(e -> callback.onResult(false, e.getMessage()));
    }

    // ─── Game Invites (sender side) ───────────────────────────────────────────

    public void sendGameInvite(String myUid, String myUsername, String friendUid, SimpleCallback callback) {
        Map<String, Object> invite = new HashMap<>();
        invite.put("inviterId", myUid);
        invite.put("inviterUsername", myUsername);
        invite.put("status", "pending");
        invite.put("timestamp", ServerValue.TIMESTAMP);

        rtdb.child("gameInvites").child(friendUid).setValue(invite)
                .addOnSuccessListener(v -> {
                    sendInviteNotificationIfOffline(myUsername, friendUid);
                    callback.onResult(true, "Poziv poslat");
                })
                .addOnFailureListener(e -> callback.onResult(false, e.getMessage()));
    }

    // Isti obrazac kao ChatRepository.sendMessage(): sistemska notifikacija se upisuje
    // u Firestore SAMO ako korisnik trenutno nije online (isti "online" flag koji se
    // ažurira u HomeActivity kad je korisnik ulogovan/u aplikaciji).
    private void sendInviteNotificationIfOffline(String myUsername, String friendUid) {
        fs.collection("users").document(friendUid).get()
                .addOnSuccessListener(doc -> {
                    boolean online = Boolean.TRUE.equals(doc.getBoolean("online"));
                    if (online) return; // korisnik je u aplikaciji, dijalog za poziv će iskočiti uživo

                    Map<String, Object> notif = new HashMap<>();
                    notif.put("title", myUsername + " (poziv za igru)");
                    notif.put("message", myUsername + " te poziva na prijateljsku partiju!");
                    notif.put("timestamp", System.currentTimeMillis());
                    notif.put("isRead", false);
                    notif.put("type", "INVITE");

                    fs.collection("users").document(friendUid)
                            .collection("notifications")
                            .add(notif);
                });
    }

    public void cancelGameInvite(String friendUid) {
        rtdb.child("gameInvites").child(friendUid).removeValue();
        if (sentInviteListener != null) {
            rtdb.child("gameInvites").child(friendUid).removeEventListener(sentInviteListener);
            sentInviteListener = null;
        }
    }

    public void listenToSentInvite(String friendUid, SentInviteListener listener) {
        sentInviteListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    listener.onRejected();
                    return;
                }
                String status = snapshot.child("status").getValue(String.class);
                if ("accepted".equals(status)) {
                    String matchId = snapshot.child("matchId").getValue(String.class);
                    listener.onAccepted(matchId);
                } else if ("rejected".equals(status)) {
                    listener.onRejected();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        rtdb.child("gameInvites").child(friendUid).addValueEventListener(sentInviteListener);
    }

    public void stopListeningToSentInvite(String friendUid) {
        if (sentInviteListener != null) {
            rtdb.child("gameInvites").child(friendUid).removeEventListener(sentInviteListener);
            sentInviteListener = null;
        }
    }

    // ─── Game Invites (receiver side) ─────────────────────────────────────────

    public void acceptInvite(String myUid, String inviterId, MatchCreatedCallback callback) {
        String matchId = rtdb.child("matches").push().getKey();

        Map<String, Object> matchData = new HashMap<>();
        matchData.put("player1Id", inviterId);
        matchData.put("player2Id", myUid);
        matchData.put("status", "in_progress");
        matchData.put("isFriendly", true);
        matchData.put("currentGame", 0);
        matchData.put("createdAt", ServerValue.TIMESTAMP);
        Map<String, Object> scores = new HashMap<>();
        scores.put(inviterId, 0);
        scores.put(myUid, 0);
        matchData.put("scores", scores);

        rtdb.child("matches").child(matchId).setValue(matchData)
                .addOnSuccessListener(v -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("status", "accepted");
                    update.put("matchId", matchId);
                    rtdb.child("gameInvites").child(myUid).updateChildren(update);

                    rtdb.child("players").child(inviterId).child("inMatch").setValue(true);
                    rtdb.child("players").child(myUid).child("inMatch").setValue(true);

                    callback.onMatchCreated(matchId, "player2");
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void rejectInvite(String myUid) {
        rtdb.child("gameInvites").child(myUid).removeValue();
    }

    public ValueEventListener listenForIncomingInvites(String myUid, IncomingInviteListener listener) {
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    listener.onInviteCancelled();
                    return;
                }
                String status = snapshot.child("status").getValue(String.class);
                if ("pending".equals(status)) {
                    String inviterId = snapshot.child("inviterId").getValue(String.class);
                    String inviterUsername = snapshot.child("inviterUsername").getValue(String.class);
                    listener.onInviteReceived(inviterId, inviterUsername);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        rtdb.child("gameInvites").child(myUid).addValueEventListener(vel);
        return vel;
    }

    public void stopListeningForIncomingInvites(String myUid, ValueEventListener listener) {
        if (listener != null) {
            rtdb.child("gameInvites").child(myUid).removeEventListener(listener);
        }
    }
}