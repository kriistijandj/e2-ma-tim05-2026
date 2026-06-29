package com.example.slagalica.fragments;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.slagalica.R;
import com.example.slagalica.region.SerbiaRegions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SignupFragment extends Fragment {

    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private String mParam1;
    private String mParam2;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private Spinner spinnerRegion;

    public SignupFragment() {}

    public static SignupFragment newInstance(String param1, String param2) {
        SignupFragment fragment = new SignupFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_signup, container, false);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        spinnerRegion = view.findViewById(R.id.spinnerRegion);
        List<String> regionOptions = new ArrayList<>();
        regionOptions.add("Izaberi region...");
        regionOptions.addAll(SerbiaRegions.ALL);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, regionOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRegion.setAdapter(adapter);

        Button btnSignup = view.findViewById(R.id.btnSignup);
        btnSignup.setOnClickListener(v -> registerUser(view));

        TextView txtLogin = view.findViewById(R.id.tvLogin);
        txtLogin.setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.nav_login)
        );

        return view;
    }

    private void registerUser(View view) {

        String email          = ((TextView) view.findViewById(R.id.etEmail)).getText().toString().trim();
        String username       = ((TextView) view.findViewById(R.id.etUsername)).getText().toString().trim();
        String region         = (String) spinnerRegion.getSelectedItem();
        String password       = ((TextView) view.findViewById(R.id.etPassword)).getText().toString().trim();
        String repeatPassword = ((TextView) view.findViewById(R.id.etRepeatPassword)).getText().toString().trim();

        if (email.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(getContext(), "Potrebno je popuniti sva polja", Toast.LENGTH_LONG).show();
            return;
        }

        if (region == null || region.equals("Izaberi region...")) {
            Toast.makeText(getContext(), "Molimo izaberite region", Toast.LENGTH_LONG).show();
            return;
        }

        if (!password.equals(repeatPassword)) {
            Toast.makeText(getContext(), "Lozinke se ne poklapaju", Toast.LENGTH_LONG).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(getContext(), "Lozinka je prekratka", Toast.LENGTH_LONG).show();
            return;
        }

        checkUsernameAndRegister(email, username, region, password, view);
    }

    private void checkUsernameAndRegister(String email, String username,
                                          String region, String password, View view) {
        db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        Toast.makeText(getContext(),
                                "Greška: Postoji korisnik sa datim korisničkim imenom",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    createFirebaseUser(email, username, region, password, view);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(),
                                "Greška: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
    }

    private void createFirebaseUser(String email, String username,
                                    String region, String password, View view) {

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (!task.isSuccessful()) {
                        Toast.makeText(getContext(),
                                "Greška: Postoji korisnik sa datim email-om",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) return;

                    String uid = user.getUid();
                    user.sendEmailVerification();

                    // ── Osnovni podaci ────────────────────────────────────────
                    Map<String, Object> userMap = new HashMap<>();


                    userMap.put("uid",      uid);
                    userMap.put("email",    email);
                    userMap.put("username", username);
                    userMap.put("region",   region);
                    userMap.put("tokens", 5);   // bonus za registraciju
                    userMap.put("stars",  0);
                    userMap.put("online",   false);
                    userMap.put("inMatch",  false);
                    userMap.put("isEnabled", false);  // čeka email verifikaciju
                    userMap.put("lastTokenClaimTimestamp", System.currentTimeMillis());

                    userMap.put("avatarId", 0);

                    // ── Statistika – sve na nulama ────────────────────────────
                    Map<String, Object> globalStats = new HashMap<>();
                    globalStats.put("totalGames", 0);
                    globalStats.put("wins",       0);
                    globalStats.put("losses",     0);

                    Map<String, Object> koznaZnaStats = new HashMap<>();
                    koznaZnaStats.put("correct", 0);
                    koznaZnaStats.put("wrong",   0);
                    koznaZnaStats.put("wins",    0);
                    koznaZnaStats.put("losses",  0);

                    Map<String, Object> spojniceStats = new HashMap<>();
                    spojniceStats.put("connected", 0);
                    spojniceStats.put("total",     0);
                    spojniceStats.put("wins",      0);
                    spojniceStats.put("losses",    0);

                    Map<String, Object> skockoStats = new HashMap<>();
                    skockoStats.put("attempt1", 0);
                    skockoStats.put("attempt2", 0);
                    skockoStats.put("attempt3", 0);
                    skockoStats.put("attempt4", 0);
                    skockoStats.put("attempt5", 0);
                    skockoStats.put("attempt6", 0);
                    skockoStats.put("failed",   0);
                    skockoStats.put("wins",     0);
                    skockoStats.put("losses",   0);

                    Map<String, Object> asocijacijeStats = new HashMap<>();
                    asocijacijeStats.put("solved",   0);
                    asocijacijeStats.put("unsolved", 0);
                    asocijacijeStats.put("wins",     0);
                    asocijacijeStats.put("losses",   0);

                    Map<String, Object> mojBrojStats = new HashMap<>();
                    mojBrojStats.put("correct", 0);
                    mojBrojStats.put("total",   0);

                    Map<String, Object> korakStats = new HashMap<>();
                    korakStats.put("step1", 0);
                    korakStats.put("step2", 0);
                    korakStats.put("step3", 0);
                    korakStats.put("total", 0);

                    Map<String, Object> stats = new HashMap<>();
                    stats.put("global",      globalStats);
                    stats.put("koznaZna",    koznaZnaStats);
                    stats.put("spojnice",    spojniceStats);
                    stats.put("skocko",      skockoStats);
                    stats.put("asocijacije", asocijacijeStats);
                    stats.put("mojBroj",     mojBrojStats);
                    stats.put("korak",       korakStats);

                    userMap.put("stats", stats);
                    // ─────────────────────────────────────────────────────────

                    db.collection("users")
                            .document(uid)
                            .set(userMap)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(getContext(),
                                        "Registracija uspešna! Aktivirajte nalog putem linka na emailu.",
                                        Toast.LENGTH_LONG).show();
                                auth.signOut();
                                Navigation.findNavController(view).navigate(R.id.nav_login);
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(getContext(),
                                            "Firestore greška: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show()
                            );
                });
    }
}