package com.example.slagalica.fragments;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.slagalica.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.Map;

public class ProfileFragment extends Fragment {

    // ====== UI ======
    private ImageView ivAvatar, ivQrCode;
    private TextView tvUsername, tvEmail, tvTokens, tvStars, tvLeague, tvRegion;
    private TextView tvTotalGames, tvWinLoss;
    private TextView tvKoZnaZnaStat, tvMojBrojStat, tvKorakStat;
    private TextView tvAsocijacijeStat, tvSkockoStat, tvSpojniceStat;

    // ====== 6 PREDEFINISANIH AVATARA ======
    // Koristimo Android system drawables kao placeholder –
    // zamijeni sa stvarnim R.drawable.avatar1 ... avatar6 kad ih dodaš u res/drawable
    private static final int[] AVATAR_RES = {
            android.R.drawable.ic_menu_myplaces,
            android.R.drawable.ic_menu_compass,
            android.R.drawable.ic_menu_gallery,
            android.R.drawable.ic_menu_camera,
            android.R.drawable.ic_menu_manage,
            android.R.drawable.ic_menu_help
    };

    private int currentAvatarId = 0;
    private String currentUid   = null;

    public ProfileFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // ── Pronađi view-ove ──────────────────────────────────────────────
        ivAvatar          = view.findViewById(R.id.ivAvatar);
        ivQrCode          = view.findViewById(R.id.ivQrCode);
        tvUsername        = view.findViewById(R.id.tvUsername);
        tvEmail           = view.findViewById(R.id.tvEmail);
        tvTokens          = view.findViewById(R.id.tvTokens);
        tvStars           = view.findViewById(R.id.tvStars);
        tvLeague          = view.findViewById(R.id.tvLeague);
        tvRegion          = view.findViewById(R.id.tvRegion);
        tvTotalGames      = view.findViewById(R.id.tvTotalGames);
        tvWinLoss         = view.findViewById(R.id.tvWinLoss);
        tvKoZnaZnaStat    = view.findViewById(R.id.tvKoZnaZnaStat);
        tvMojBrojStat     = view.findViewById(R.id.tvMojBrojStat);
        tvKorakStat       = view.findViewById(R.id.tvKorakStat);
        tvAsocijacijeStat = view.findViewById(R.id.tvAsocijacijeStat);
        tvSkockoStat      = view.findViewById(R.id.tvSkockoStat);
        tvSpojniceStat    = view.findViewById(R.id.tvSpojniceStat);

        // ── Dugmad ────────────────────────────────────────────────────────
        view.findViewById(R.id.btnChangeAvatar).setOnClickListener(v ->
                showAvatarPicker()
        );

        view.findViewById(R.id.btnChangePassword).setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.nav_changePassword)
        );

        view.findViewById(R.id.btnLogout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Navigation.findNavController(view).navigate(
                    R.id.nav_login,
                    null,
                    new androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(R.id.nav_home, true)
                            .build()
            );
        });

        // ── Učitaj podatke iz Firestore ───────────────────────────────────
        loadProfileData();

        return view;
    }

    // ==============================
    // UČITAVANJE PROFILA IZ FIRESTORE
    // ==============================

    private void loadProfileData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        currentUid = user.getUid();

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    // ── Osnovni podaci ────────────────────────────────────
                    String username = doc.getString("username");
                    String email    = doc.getString("email");
                    String region   = doc.getString("region");
                    Long   tokens   = doc.getLong("tokens");
                    Long   stars    = doc.getLong("stars");
                    Long   avatarId = doc.getLong("avatarId");

                    tvUsername.setText(username != null ? username : "—");
                    tvEmail.setText(email != null ? email : "—");
                    tvRegion.setText(region != null ? region : "—");
                    tvTokens.setText(tokens != null ? String.valueOf(tokens) : "0");
                    tvStars.setText(stars != null ? String.valueOf(stars) : "0");
                    tvLeague.setText("Nulta"); // Liga za sljedeću kontrolnu tačku

                    // ── Avatar ────────────────────────────────────────────
                    currentAvatarId = avatarId != null ? avatarId.intValue() : 0;
                    if (currentAvatarId < 0 || currentAvatarId >= AVATAR_RES.length) {
                        currentAvatarId = 0;
                    }
                    ivAvatar.setImageResource(AVATAR_RES[currentAvatarId]);

                    if (region != null && !region.isEmpty()) {
                        loadAvatarBorder(region);
                    }

                    // ── QR kod (sadržaj = username) ───────────────────────
                    if (username != null) {
                        generateQrCode(username);
                    }

                    // ── Statistika ────────────────────────────────────────
                    Map<String, Object> stats = (Map<String, Object>) doc.get("stats");
                    if (stats != null) {
                        loadStats(stats);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(),
                                "Greška pri učitavanju profila: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
    }

    // ==============================
    // PRIKAZ STATISTIKE
    // ==============================

    @SuppressWarnings("unchecked")
    private void loadStats(Map<String, Object> stats) {

        // ── Global ────────────────────────────────────────────────────────
        Map<String, Object> global = (Map<String, Object>) stats.get("global");
        if (global != null) {
            long total  = toLong(global.get("totalGames"));
            long wins   = toLong(global.get("wins"));
            long losses = toLong(global.get("losses"));

            tvTotalGames.setText(String.valueOf(total));

            if (total > 0) {
                int winPct  = (int) (wins   * 100 / total);
                int lossPct = (int) (losses * 100 / total);
                tvWinLoss.setText(winPct + "% / " + lossPct + "%");
            } else {
                tvWinLoss.setText("0% / 0%");
            }
        }

        // ── Ko zna zna ────────────────────────────────────────────────────
        Map<String, Object> kzz = (Map<String, Object>) stats.get("koznaZna");
        if (kzz != null) {
            long correct = toLong(kzz.get("correct"));
            long wrong   = toLong(kzz.get("wrong"));
            tvKoZnaZnaStat.setText(correct + " / " + wrong);
        }

        // ── Moj broj ──────────────────────────────────────────────────────
        Map<String, Object> mb = (Map<String, Object>) stats.get("mojBroj");
        if (mb != null) {
            long correct = toLong(mb.get("correct"));
            long total   = toLong(mb.get("total"));
            if (total > 0) {
                int pct = (int) (correct * 100 / total);
                tvMojBrojStat.setText(pct + "%");
            } else {
                tvMojBrojStat.setText("0%");
            }
        }

        // ── Korak po korak ────────────────────────────────────────────────
        Map<String, Object> korak = (Map<String, Object>) stats.get("korak");
        if (korak != null) {
            long total = toLong(korak.get("total"));
            long s1    = toLong(korak.get("step1"));
            long s2    = toLong(korak.get("step2"));
            long s3    = toLong(korak.get("step3"));
            if (total > 0) {
                int p1 = (int) (s1 * 100 / total);
                int p2 = (int) (s2 * 100 / total);
                int p3 = (int) (s3 * 100 / total);
                tvKorakStat.setText("K1:" + p1 + "% K2:" + p2 + "% K3:" + p3 + "%");
            } else {
                tvKorakStat.setText("K1:0% K2:0% K3:0%");
            }
        }

        // ── Asocijacije ───────────────────────────────────────────────────
        Map<String, Object> asoc = (Map<String, Object>) stats.get("asocijacije");
        if (asoc != null) {
            long solved   = toLong(asoc.get("solved"));
            long unsolved = toLong(asoc.get("unsolved"));
            tvAsocijacijeStat.setText(solved + " / " + unsolved);
        }

        // ── Skočko ────────────────────────────────────────────────────────
        Map<String, Object> skocko = (Map<String, Object>) stats.get("skocko");
        if (skocko != null) {
            long total = toLong(skocko.get("attempt1"))
                    + toLong(skocko.get("attempt2"))
                    + toLong(skocko.get("attempt3"))
                    + toLong(skocko.get("attempt4"))
                    + toLong(skocko.get("attempt5"))
                    + toLong(skocko.get("attempt6"))
                    + toLong(skocko.get("failed"));

            if (total > 0) {
                int p1 = (int) (toLong(skocko.get("attempt1")) * 100 / total);
                int p2 = (int) (toLong(skocko.get("attempt2")) * 100 / total);
                int p3 = (int) (toLong(skocko.get("attempt3")) * 100 / total);
                int p4 = (int) (toLong(skocko.get("attempt4")) * 100 / total);
                int p5 = (int) (toLong(skocko.get("attempt5")) * 100 / total);
                int p6 = (int) (toLong(skocko.get("attempt6")) * 100 / total);
                tvSkockoStat.setText("P1:" + p1 + "% P2:" + p2 + "% P3:" + p3
                        + "%\nP4:" + p4 + "% P5:" + p5 + "% P6:" + p6 + "%");
            } else {
                tvSkockoStat.setText("P1:0% P2:0% P3:0%\nP4:0% P5:0% P6:0%");
            }
        }

        // ── Spojnice ──────────────────────────────────────────────────────
        Map<String, Object> sp = (Map<String, Object>) stats.get("spojnice");
        if (sp != null) {
            long connected = toLong(sp.get("connected"));
            long total     = toLong(sp.get("total"));
            if (total > 0) {
                int pct = (int) (connected * 100 / total);
                tvSpojniceStat.setText(pct + "%");
            } else {
                tvSpojniceStat.setText("0%");
            }
        }
    }

    // ==============================
    // AVATAR PICKER – dijalog sa 6 opcija
    // ==============================

    private void showAvatarPicker() {
        if (currentUid == null) return;

        // Pravimo dijalog sa 6 avatara
        String[] avatarNames = {"Avatar 1", "Avatar 2", "Avatar 3",
                "Avatar 4", "Avatar 5", "Avatar 6"};

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Izaberi avatar")
                .setItems(avatarNames, (dialog, which) -> {
                    currentAvatarId = which;
                    ivAvatar.setImageResource(AVATAR_RES[which]);

                    // Sačuvaj u Firestore
                    FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(currentUid)
                            .update("avatarId", which)
                            .addOnSuccessListener(unused ->
                                    Toast.makeText(getContext(),
                                            "Avatar promijenjen!",
                                            Toast.LENGTH_SHORT).show()
                            );
                })
                .show();
    }

    // ==============================
    // QR KOD GENERISANJE
    // ==============================

    private void generateQrCode(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 300, 300);

            int width  = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            ivQrCode.setImageBitmap(bitmap);

        } catch (WriterException e) {
            // Ako ne uspije, ostaje default ikona
            android.util.Log.e("ProfileFragment", "QR greška: " + e.getMessage());
        }
    }

    // ==============================
    // AVATAR BORDER – boja po rangu regiona
    // ==============================

    private void loadAvatarBorder(String myRegion) {
        com.example.slagalica.repository.RegionRepository repo =
                new com.example.slagalica.repository.RegionRepository();
        repo.getPreviousCycleRank(myRegion, regionModel -> {
            if (getActivity() == null || !isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                int rank = regionModel.getRank();
                String color = null;
                if (rank == 1) color = "#FFD700";
                else if (rank == 2) color = "#C0C0C0";
                else if (rank == 3) color = "#CD7F32";
                if (color != null) {
                    android.graphics.drawable.GradientDrawable border =
                            new android.graphics.drawable.GradientDrawable();
                    border.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                    border.setStroke(6, android.graphics.Color.parseColor(color));
                    border.setColor(android.graphics.Color.TRANSPARENT);
                    ivAvatar.setBackground(border);
                } else {
                    ivAvatar.setBackground(null);
                }
            });
        });
    }

    // ==============================
    // HELPER – sigurno čitanje Long
    // ==============================

    private long toLong(Object value) {
        if (value == null) return 0;
        if (value instanceof Long)    return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        return 0;
    }
}