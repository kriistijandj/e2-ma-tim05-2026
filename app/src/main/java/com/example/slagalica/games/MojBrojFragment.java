package com.example.slagalica.games;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.example.slagalica.R;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MojBrojFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MojBrojFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public MojBrojFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment MojBrojFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static MojBrojFragment newInstance(String param1, String param2) {
        MojBrojFragment fragment = new MojBrojFragment();
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
        return inflater.inflate(R.layout.fragment_moj_broj, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText etExpression = view.findViewById(R.id.etExpression);

        View.OnClickListener insertListener = v -> {
            Button btn = (Button) v;
            etExpression.append(btn.getText().toString());
        };

        view.findViewById(R.id.btnNum1).setOnClickListener(insertListener);
        view.findViewById(R.id.btnNum2).setOnClickListener(insertListener);
        view.findViewById(R.id.btnNum3).setOnClickListener(insertListener);
        view.findViewById(R.id.btnNum4).setOnClickListener(insertListener);
        view.findViewById(R.id.btnNum5).setOnClickListener(insertListener);
        view.findViewById(R.id.btnNum6).setOnClickListener(insertListener);

        view.findViewById(R.id.btnPlus).setOnClickListener(insertListener);
        view.findViewById(R.id.btnMinus).setOnClickListener(insertListener);
        view.findViewById(R.id.btnMul).setOnClickListener(insertListener);
        view.findViewById(R.id.btnDiv).setOnClickListener(insertListener);
        view.findViewById(R.id.btnOpen).setOnClickListener(insertListener);
        view.findViewById(R.id.btnClose).setOnClickListener(insertListener);

        view.findViewById(R.id.btnDelete).setOnClickListener(v -> {
            String text = etExpression.getText().toString();

            if (!text.isEmpty()) {
                etExpression.setText(text.substring(0, text.length() - 1));
                etExpression.setSelection(etExpression.getText().length());
            }
        });
    }
}