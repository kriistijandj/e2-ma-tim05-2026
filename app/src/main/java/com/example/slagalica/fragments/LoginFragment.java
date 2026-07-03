package com.example.slagalica.fragments;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import com.example.slagalica.R;

public class LoginFragment extends Fragment {

    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private String mParam1;
    private String mParam2;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    public LoginFragment() {}

    public static LoginFragment newInstance(String param1, String param2) {
        LoginFragment fragment = new LoginFragment();
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
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        EditText etEmailOrUsername = view.findViewById(R.id.etUsername);
        EditText etPassword = view.findViewById(R.id.etPassword);

        Button btnLogin = view.findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v -> loginUser(view));

        TextView txtSignup = view.findViewById(R.id.tvSignup);
        txtSignup.setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.nav_signup));

        return view;
    }

    private void loginUser(View view) {
        String input = ((TextView) view.findViewById(R.id.etUsername))
                .getText().toString().trim();
        String password = ((TextView) view.findViewById(R.id.etPassword))
                .getText().toString().trim();

        if (input.isEmpty() || password.isEmpty()) {
            Toast.makeText(getContext(), "Potrebno je popuniti sva polja", Toast.LENGTH_LONG).show();
            return;
        }

        if (input.contains("@")) {
            loginWithEmail(input, password, view);
        } else {
            findEmailByUsername(input, password, view);
        }
    }

    private void loginWithEmail(String email, String password, View view) {
        if (email == null || email.isEmpty()) {
            Toast.makeText(getContext(), "Email nije validan", Toast.LENGTH_LONG).show();
            return;
        }

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(getContext(),
                                "Greška prilikom prijave: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        Toast.makeText(getContext(), "Greška: korisnik ne postoji", Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (!user.isEmailVerified()) {
                        auth.signOut();
                        Toast.makeText(getContext(),
                                "Potrebno je da aktivirate nalog putem linka na mejlu",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    db.collection("users").document(user.getUid()).update("isEnabled", true);

                    db.collection("users").document(user.getUid()).get()
                            .addOnSuccessListener(doc -> {
                                if (!doc.exists()) {
                                    auth.signOut();
                                    Toast.makeText(getContext(), "Korisnik ne postoji u bazi", Toast.LENGTH_LONG).show();
                                    return;
                                }

                                Boolean isEnabled = doc.getBoolean("isEnabled");
                                if (isEnabled == null || !isEnabled) {
                                    auth.signOut();
                                    Toast.makeText(getContext(), "Nalog nije aktiviran", Toast.LENGTH_LONG).show();
                                    return;
                                }

                                Toast.makeText(getContext(), "Uspešna prijava", Toast.LENGTH_SHORT).show();
                                checkAndGrantDailyTokens(user.getUid(), view);
                            })
                            .addOnFailureListener(e -> {
                                auth.signOut();
                                Toast.makeText(getContext(), "Greška: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                });
    }

    private void findEmailByUsername(String username, String password, View view) {
        db.collection("users").whereEqualTo("username", username).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (!task.getResult().isEmpty()) {
                            for (QueryDocumentSnapshot doc : task.getResult()) {
                                String email = doc.getString("email");
                                if (email == null || email.isEmpty()) {
                                    Toast.makeText(getContext(), "Neispravan email u bazi", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                loginWithEmail(email, password, view);
                                return;
                            }
                        } else {
                            Toast.makeText(getContext(), "Korisnik ne postoji", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(getContext(), "Greška: " + task.getException(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void checkAndGrantDailyTokens(String uid, View view) {
        NavController navController = Navigation.findNavController(view);

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    long lastClaim = 0;
                    Long stored = doc.getLong("lastTokenClaimTimestamp");
                    if (stored != null) lastClaim = stored;

                    long now = System.currentTimeMillis();
                    boolean claimedToday = (now - lastClaim) < 24 * 60 * 60 * 1000L;

                    if (!claimedToday) {
                        long currentTokens = 0;
                        Long storedTokens = doc.getLong("tokens");
                        if (storedTokens != null) currentTokens = storedTokens;

                        long stars = doc.getLong("stars") != null ? doc.getLong("stars") : 0;
                        int leagueIdx = doc.getLong("league") != null
                                ? doc.getLong("league").intValue()
                                : com.example.slagalica.services.LeagueManager.getLeagueIndex(stars);
                        int bonus = com.example.slagalica.services.LeagueManager.getBonusTokens(leagueIdx);

                        db.collection("users").document(uid)
                                .update("tokens", currentTokens + 5 + bonus, "lastTokenClaimTimestamp", now)
                                .addOnSuccessListener(unused ->
                                        Toast.makeText(getContext(), "Dobili ste 5 dnevnih tokena!", Toast.LENGTH_SHORT).show()
                                );
                    }

                    navController.navigate(R.id.nav_home);
                })
                .addOnFailureListener(e -> navController.navigate(R.id.nav_home));
    }
}
