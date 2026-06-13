package com.example.slagalica.fragments;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import android.widget.TextView;
import android.widget.Toast;

import com.example.slagalica.R;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ChangePassword#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ChangePassword extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public ChangePassword() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment ChangePassword.
     */
    // TODO: Rename and change types and number of parameters
    public static ChangePassword newInstance(String param1, String param2) {
        ChangePassword fragment = new ChangePassword();
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
        View view = inflater.inflate(R.layout.fragment_change_password, container, false);

        Button changePassword = view.findViewById(R.id.btnChangePassword);

        changePassword.setOnClickListener(v ->
                changePassword(view)
        );

        return view;
    }

    private void changePassword(View view) {

        String oldPassword = ((TextView) view.findViewById(R.id.etOldPassword))
                .getText().toString().trim();

        String newPassword = ((TextView) view.findViewById(R.id.etNewPassword))
                .getText().toString().trim();

        String confirmPassword = ((TextView) view.findViewById(R.id.etConfirmPassword))
                .getText().toString().trim();

        if (oldPassword.isEmpty() ||
                newPassword.isEmpty() ||
                confirmPassword.isEmpty()) {

            Toast.makeText(getContext(),
                    "Popunite sva polja",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {

            Toast.makeText(getContext(),
                    "Nove lozinke se ne poklapaju",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (newPassword.length() < 6) {

            Toast.makeText(getContext(),
                    "Nova lozinka mora imati najmanje 6 karaktera",
                    Toast.LENGTH_LONG).show();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {

            Toast.makeText(getContext(),
                    "Korisnik nije prijavljen",
                    Toast.LENGTH_LONG).show();
            return;
        }

        String email = user.getEmail();

        AuthCredential credential =
                EmailAuthProvider.getCredential(email, oldPassword);

        user.reauthenticate(credential)
                .addOnSuccessListener(unused -> {

                    user.updatePassword(newPassword)
                            .addOnSuccessListener(unused2 -> {

                                Toast.makeText(getContext(),
                                        "Lozinka uspešno promenjena",
                                        Toast.LENGTH_LONG).show();

                                Navigation.findNavController(view)
                                        .navigate(R.id.nav_profile);

                            })
                            .addOnFailureListener(e ->

                                    Toast.makeText(getContext(),
                                            "Greška: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show()
                            );

                })
                .addOnFailureListener(e ->

                        Toast.makeText(getContext(),
                                "Stara lozinka nije tačna",
                                Toast.LENGTH_LONG).show()
                );
    }
}