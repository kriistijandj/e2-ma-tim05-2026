package com.example.slagalica.games;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.example.slagalica.R;

import java.util.Arrays;
import java.util.List;

public class KorakPoKorakFragment extends Fragment {

    private int currentHintIndex = 1;

    public KorakPoKorakFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_korak_po_korak, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        TextView tvHint1 = view.findViewById(R.id.tvHint1);
        TextView tvHint2 = view.findViewById(R.id.tvHint2);
        TextView tvHint3 = view.findViewById(R.id.tvHint3);
        TextView tvHint4 = view.findViewById(R.id.tvHint4);
        TextView tvHint5 = view.findViewById(R.id.tvHint5);
        TextView tvHint6 = view.findViewById(R.id.tvHint6);
        TextView tvHint7 = view.findViewById(R.id.tvHint7);
        TextView tvHintNumber = view.findViewById(R.id.tvHintTitle);

        List<TextView> hints = Arrays.asList(
                tvHint1, tvHint2, tvHint3, tvHint4, tvHint5, tvHint6, tvHint7
        );


        for (int i = 0; i < hints.size(); i++) {
            hints.get(i).setVisibility(i == 0 ? View.VISIBLE : View.INVISIBLE);
        }


        Button nextButton = view.findViewById(R.id.btnStop2);

        nextButton.setOnClickListener(v -> {

            if (currentHintIndex < hints.size()) {
                hints.get(currentHintIndex).setVisibility(View.VISIBLE);
                updateTitle(tvHintNumber, currentHintIndex, hints.size());
                currentHintIndex++;

                if (currentHintIndex == hints.size()) {
                    v.setEnabled(false);
                }

            } else {
                v.setEnabled(false);
            }
        });
    }

    private void updateTitle(TextView title, int current, int total) {
        title.setText("Korak " + (current + 1) + "/" + total);
    }
}