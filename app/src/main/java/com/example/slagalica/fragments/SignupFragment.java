package com.example.slagalica.fragments;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.example.slagalica.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SignupFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SignupFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    public SignupFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment SignupFragment.
     */
    // TODO: Rename and change types and number of parameters
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

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_signup, container, false);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        Button btnSignup = view.findViewById(R.id.btnSignup);

        btnSignup.setOnClickListener(v -> registerUser(view));

        TextView txtLogin = view.findViewById(R.id.tvLogin);

        txtLogin.setOnClickListener(v -> {
            Navigation.findNavController(view).navigate(R.id.nav_login);
        });





        return view;
    }

    private void registerUser(View view) {

        String email = ((TextView) view.findViewById(R.id.etEmail)).getText().toString().trim();
        String username = ((TextView) view.findViewById(R.id.etUsername)).getText().toString().trim();
        String region = ((TextView) view.findViewById(R.id.etRegion)).getText().toString().trim();
        String password = ((TextView) view.findViewById(R.id.etPassword)).getText().toString().trim();
        String repeatPassword = ((TextView) view.findViewById(R.id.etRepeatPassword)).getText().toString().trim();

        // 1. VALIDACIJA
        if (email.isEmpty() || username.isEmpty() || region.isEmpty() || password.isEmpty()) {
            Toast.makeText(getContext(),
                    "Potrebno je popuniti sva polja",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!password.equals(repeatPassword)) {
            Toast.makeText(getContext(),
                    "Lozinke se ne poklapaju",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(getContext(),
                    "Lozinka je prekratka",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // 2. FIREBASE CREATE USER
        checkUsernameAndRegister(
                email,
                username,
                region,
                password,
                view
        );
    }

    private void checkUsernameAndRegister(
            String email,
            String username,
            String region,
            String password,
            View view) {

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

                    createFirebaseUser(
                            email,
                            username,
                            region,
                            password,
                            view
                    );
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(),
                                "Greška: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
    }

    private void createFirebaseUser(
            String email,
            String username,
            String region,
            String password,
            View view) {

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        FirebaseUser user = auth.getCurrentUser();

                        if (user == null) {
                            return;
                        }

                        String uid = user.getUid();

                        user.sendEmailVerification();

                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("email", email);
                        userMap.put("username", username);
                        userMap.put("region", region);
                        userMap.put("createdAt", System.currentTimeMillis());
                        userMap.put("isEnabled", false);

                        db.collection("users")
                                .document(uid)
                                .set(userMap)
                                .addOnSuccessListener(unused -> {

                                    Toast.makeText(getContext(),
                                            "Registracija uspešna! Aktivirajte nalog putem linka na emailu.",
                                            Toast.LENGTH_LONG).show();

                                    auth.signOut();

                                    Navigation.findNavController(view)
                                            .navigate(R.id.nav_login);
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(getContext(),
                                                "Firestore greška: " + e.getMessage(),
                                                Toast.LENGTH_LONG).show()
                                );

                    } else {

                        Toast.makeText(getContext(),
                                "Greška: Postoji korisnik sa datim email-om",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

}