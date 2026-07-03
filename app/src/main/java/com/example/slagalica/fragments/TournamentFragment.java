package com.example.slagalica.fragments;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.slagalica.R;
import com.example.slagalica.repository.TournamentRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;
import java.util.Map;

public class TournamentFragment extends Fragment {

    private String tournamentId;
    private String myUid;
    private FirebaseFirestore db;
    private ListenerRegistration tournamentListener;
    private TournamentRepository tournamentRepo;
    private ProgressDialog loadingDialog;

    private static final int[] AVATAR_RES = {
            android.R.drawable.ic_menu_myplaces,
            android.R.drawable.ic_menu_compass,
            android.R.drawable.ic_menu_gallery,
            android.R.drawable.ic_menu_camera,
            android.R.drawable.ic_menu_manage,
            android.R.drawable.ic_menu_help
    };

    // UI Elementi
    private View player1, player2, player3, player4;
    private View finalist1, finalist2;
    private TextView tvTourTitle;
    private Button btnEnterMatch;
    private Button btnJoinQueue; // Dodato dugme za ulazak u red za čekanje

    private String currentPhase = "";
    private String myMatchId = "";
    private String myRole = "";

    public TournamentFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tournament, container, false);

        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db = FirebaseFirestore.getInstance();
        tournamentRepo = new TournamentRepository();

        // Čitanje argumenata (ako dolazimo direktno u već pokrenut turnir)
        if (getArguments() != null) {
            tournamentId = getArguments().getString("TOURNAMENT_ID");
        }

        tvTourTitle = view.findViewById(R.id.tvTourTitle);
        btnEnterMatch = view.findViewById(R.id.btnEnterMatch);
        btnJoinQueue = view.findViewById(R.id.btnJoinQueue); // Povezivanje novog dugmeta

        // Mapiranje UI slotova
        player1 = view.findViewById(R.id.player1);
        player2 = view.findViewById(R.id.player2);
        player3 = view.findViewById(R.id.player3);
        player4 = view.findViewById(R.id.player4);
        finalist1 = view.findViewById(R.id.finalist1);
        finalist2 = view.findViewById(R.id.finalist2);

        // Progress Dialog za vizuelno čekanje 4 igrača
        // Progress Dialog koji sada ima dugme "Otkaži"
        loadingDialog = new ProgressDialog(getContext());
        loadingDialog.setMessage("Plaćanje (3 žetona) i čekanje ostalih igrača (potrebno: 4)...");
        loadingDialog.setCancelable(true); // Promenjeno na true da korisnik može kliknuti van ili "Nazad"

        // Dodajemo eksplicitno dugme za otkazivanje na sam dijalog
        loadingDialog.setButton(ProgressDialog.BUTTON_NEGATIVE, "Otkaži", (dialog, which) -> {
            cancelTournamentQueue();
        });

        // Ako korisnik pritisne back dugme na telefonu, takođe otkaži red
        loadingDialog.setOnCancelListener(dialog -> {
            cancelTournamentQueue();
        });

        // Postavljanje osluškivača za klikove
        btnEnterMatch.setOnClickListener(v -> enterMatch());

        if (btnJoinQueue != null) {
            btnJoinQueue.setOnClickListener(v -> joinTournamentQueue());
        }

        // Ako imamo ID turnira, odmah slušaj promene; ako nemamo, dugme za ulazak je vidljivo
        if (tournamentId != null && !tournamentId.isEmpty()) {
            if (btnJoinQueue != null) btnJoinQueue.setVisibility(View.GONE);
            listenToTournament();
        } else {
            tvTourTitle.setText("Turnir (Potrebna 4 igrača)");
            if (btnJoinQueue != null) btnJoinQueue.setVisibility(View.VISIBLE);
            btnEnterMatch.setVisibility(View.GONE);
        }

        return view;
    }

    private void joinTournamentQueue() {
        if (loadingDialog != null) loadingDialog.show();

        tournamentRepo.joinTournament(new TournamentRepository.OnTournamentStatusListener() {
            @Override
            public void onJoinedQueue() {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Uspešno ste ušli u red za turnir!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNoTokens() {
                if (loadingDialog != null) loadingDialog.dismiss();
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Nemate dovoljno tokena (potrebno 3)!", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onTournamentStarted(String startedTournamentId, String startMatchId, String role) {
                if (loadingDialog != null) loadingDialog.dismiss();

                tournamentId = startedTournamentId;
                myMatchId = startMatchId;
                myRole = role;

                if (btnJoinQueue != null) btnJoinQueue.setVisibility(View.GONE);

                // Pokreni aktivno slušanje kreiranog turnira i osvežavanje stabla
                listenToTournament();
            }
        });
    }

    private void listenToTournament() {
        if (tournamentId == null || tournamentId.isEmpty()) return;

        tournamentListener = db.collection("tournaments").document(tournamentId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e("TournamentFragment", "Greška u osluškivanju turnira", error);
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) return;

                    String phase = snapshot.getString("status");
                    if (phase == null) phase = "semi_finals";

                    // Osiguraj stabilnu detekciju i pokretanje animacija na promenu faze
                    if (!currentPhase.equals(phase)) {
                        currentPhase = phase;
                        handlePhaseChange(snapshot);
                    } else {
                        // Ako se faza nije promenila ali jesu podaci (npr. upisan prvi finalista), osveži slotove
                        refreshTournamentUI(snapshot);
                    }
                });
    }

    private void handlePhaseChange(DocumentSnapshot snapshot) {
        refreshTournamentUI(snapshot);

        // Pokreni animaciju pojavljivanja finalista isključivo kada turnir pređe u fazu finala
        if ("finals".equals(currentPhase)) {
            animateWinnerToFinals(finalist1);
            animateWinnerToFinals(finalist2);
        }
    }

    private void refreshTournamentUI(DocumentSnapshot snapshot) {
        // 1. Popunjavanje polufinalnih slotova
        List<String> players = (List<String>) snapshot.get("players");
        if (players != null && players.size() == 4) {
            populatePlayerSlot(player1, players.get(0));
            populatePlayerSlot(player2, players.get(1));
            populatePlayerSlot(player3, players.get(2));
            populatePlayerSlot(player4, players.get(3));
        }

        // 2. Popunjavanje finalnih slotova
        Map<String, String> finalsData = (Map<String, String>) snapshot.get("finals");
        if (finalsData != null) {
            String winner1 = finalsData.get("player1Id");
            String winner2 = finalsData.get("player2Id");
            populatePlayerSlot(finalist1, winner1);
            populatePlayerSlot(finalist2, winner2);
        }

        // 3. Upravljanje naslovima i proverom prava igranja meča
        if ("semi_finals".equals(currentPhase)) {
            tvTourTitle.setText("Polufinale u toku!");
            checkIfMyTurn(snapshot, "semiFinals");

        } else if ("finals".equals(currentPhase)) {
            tvTourTitle.setText("Finale turnira!");
            checkIfMyTurn(snapshot, "finals");

        } else if ("finished".equals(currentPhase)) {
            tvTourTitle.setText("Turnir je završen!");
            btnEnterMatch.setVisibility(View.GONE);

            if (finalsData != null) {
                String champion = finalsData.get("winnerId");
                if (myUid.equals(champion)) {
                    Toast.makeText(getContext(), "ČESTITAMO! Osvojio si turnir!", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void populatePlayerSlot(View slotView, String uid) {
        if (slotView == null) return;

        if (uid == null || uid.isEmpty()) {
            slotView.setVisibility(View.INVISIBLE);
            return;
        }
        slotView.setVisibility(View.VISIBLE);

        android.widget.ImageView ivAvatar = slotView.findViewById(R.id.ivTourAvatar);
        TextView tvUsername = slotView.findViewById(R.id.tvTourUsername);
        android.widget.ImageView ivLeague = slotView.findViewById(R.id.ivTourLeague);

        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists() || getContext() == null || !isAdded()) return;

                    String username = documentSnapshot.getString("username");
                    Long avatarId = documentSnapshot.getLong("avatarId");
                    Long stars = documentSnapshot.getLong("stars");
                    long starCount = stars != null ? stars : 0;

                    if (tvUsername != null) tvUsername.setText(username != null ? username : "—");

                    if (ivAvatar != null && avatarId != null) {
                        int idx = avatarId.intValue();
                        if (idx >= 0 && idx < AVATAR_RES.length) {
                            ivAvatar.setImageResource(AVATAR_RES[idx]);
                        }
                    }

                    if (ivLeague != null) {
                        int leagueIdx = com.example.slagalica.services.LeagueManager.getLeagueIndex(starCount);
                        int leagueImageRes = android.R.drawable.btn_star_big_on; // Default

                        switch (leagueIdx) {
                            case 0:
                                leagueImageRes = android.R.drawable.btn_star_big_on;
                                break;
                            case 1:
                                leagueImageRes = android.R.drawable.btn_star_big_off;
                                break;
                            case 2:
                                // leagueImageRes = R.drawable.ic_gold_league;
                                break;
                        }
                        ivLeague.setImageResource(leagueImageRes);
                    }
                });
    }

    private void checkIfMyTurn(DocumentSnapshot snapshot, String phaseKey) {
        btnEnterMatch.setVisibility(View.GONE);

        if ("semiFinals".equals(phaseKey)) {
            Map<String, String> semiFinals = (Map<String, String>) snapshot.get("semiFinals");
            List<String> players = (List<String>) snapshot.get("players");

            if (semiFinals != null && players != null && players.size() == 4) {
                if (myUid.equals(players.get(0)) || myUid.equals(players.get(1))) {
                    myMatchId = semiFinals.get("match1Id");
                    myRole = myUid.equals(players.get(0)) ? "player1" : "player2";
                    btnEnterMatch.setVisibility(View.VISIBLE);
                } else if (myUid.equals(players.get(2)) || myUid.equals(players.get(3))) {
                    myMatchId = semiFinals.get("match2Id");
                    myRole = myUid.equals(players.get(2)) ? "player1" : "player2";
                    btnEnterMatch.setVisibility(View.VISIBLE);
                }
            }
        } else if ("finals".equals(phaseKey)) {
            Map<String, String> finals = (Map<String, String>) snapshot.get("finals");
            if (finals != null) {
                String p1 = finals.get("player1Id");
                String p2 = finals.get("player2Id");

                if (myUid.equals(p1) || myUid.equals(p2)) {
                    myMatchId = finals.get("matchId");
                    myRole = myUid.equals(p1) ? "player1" : "player2";

                    // Dugme se prikazuje samo ako je generisan RTDB meč za finale
                    String finalMatchId = finals.get("matchId");
                    if (finalMatchId != null && !finalMatchId.isEmpty()) {
                        btnEnterMatch.setVisibility(View.VISIBLE);
                    } else {
                        tvTourTitle.setText("Čeka se drugi polufinalista...");
                    }
                } else {
                    tvTourTitle.setText("Ispao si! Gledaj finale.");
                }
            }
        }
    }
    private void cancelTournamentQueue() {
        if (tournamentRepo != null) {
            // Pozivamo metodu u repozitorijumu koja briše korisnika iz reda
            tournamentRepo.cancelTournamentMatchmaking(myUid, new TournamentRepository.OnCancelListener() {
                @Override
                public void onCancelSuccess() {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Izašli ste iz reda za turnir.", Toast.LENGTH_SHORT).show();
                    }
                    // Ponovo prikaži dugme za ulazak u red
                    if (btnJoinQueue != null) btnJoinQueue.setVisibility(View.VISIBLE);
                    tvTourTitle.setText("Turnir (Potrebna 4 igrača)");
                }

                @Override
                public void onCancelFailure(Exception e) {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Greška pri napuštanju reda.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }
    private void enterMatch() {
        if (myMatchId == null || myMatchId.isEmpty()) return;

        Bundle args = new Bundle();
        args.putString("MATCH_ID", myMatchId);
        args.putString("PLAYER_ROLE", myRole);
        args.putBoolean("IS_TOURNAMENT", true);
        args.putString("TOURNAMENT_ID", tournamentId);

        if (getView() != null) {
            Navigation.findNavController(getView()).navigate(R.id.nav_game, args);
        }
    }

    private void animateWinnerToFinals(View finalView) {
        if (finalView == null || finalView.getVisibility() != View.VISIBLE) return;
        finalView.setAlpha(0f);
        finalView.setTranslationY(-50f);
        finalView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(800)
                .start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (tournamentListener != null) {
            tournamentListener.remove();
        }
    }
}