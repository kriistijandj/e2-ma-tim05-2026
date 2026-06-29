package com.example.slagalica.fragments;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.adapters.FriendAdapter;
import com.example.slagalica.models.FriendModel;
import com.example.slagalica.repository.FriendRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.List;

public class FriendsFragment extends Fragment {

    private RecyclerView rvFriends;
    private EditText etSearchUsername;
    private FriendAdapter adapter;
    private final List<FriendModel> friendList = new ArrayList<>();
    private FriendRepository friendRepo;
    private String myUid;
    private String myUsername;

    private AlertDialog sendInviteDialog;
    private final Handler inviteHandler = new Handler(Looper.getMainLooper());
    private Runnable inviteTimeoutRunnable;
    private String pendingInviteFriendUid;

    private final ActivityResultLauncher<ScanOptions> qrScanLauncher =
            registerForActivityResult(new ScanContract(), this::onQrScanResult);

    public FriendsFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends, container, false);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return view;
        myUid = user.getUid();
        friendRepo = new FriendRepository();

        etSearchUsername = view.findViewById(R.id.etSearchUsername);
        rvFriends = view.findViewById(R.id.rvFriends);
        rvFriends.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new FriendAdapter(friendList, new FriendAdapter.FriendActionListener() {
            @Override
            public void onPlay(FriendModel friend) {
                sendGameInvite(friend);
            }

            @Override
            public void onRemove(FriendModel friend) {
                confirmRemoveFriend(friend);
            }
        });
        rvFriends.setAdapter(adapter);

        view.findViewById(R.id.btnSearchUser).setOnClickListener(v -> searchAndAddFriend());
        view.findViewById(R.id.btnScanQr).setOnClickListener(v -> launchQrScanner());
        view.findViewById(R.id.btnMyQr).setOnClickListener(v -> showMyQrCode());

        FirebaseFirestore.getInstance().collection("users").document(myUid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) myUsername = doc.getString("username");
                });

        loadFriends();
        return view;
    }

    private void loadFriends() {
        friendRepo.loadFriends(myUid, friends -> {
            if (!isAdded()) return;
            friendList.clear();
            friendList.addAll(friends);
            friendList.sort((a, b) -> {
                if (a.online != b.online) return a.online ? -1 : 1;
                return 0;
            });
            requireActivity().runOnUiThread(() -> adapter.notifyDataSetChanged());
        });
    }

    // ─── Search & Add ─────────────────────────────────────────────────────────

    private void searchAndAddFriend() {
        String username = etSearchUsername.getText().toString().trim();
        if (username.isEmpty()) {
            Toast.makeText(requireContext(), "Unesite korisničko ime", Toast.LENGTH_SHORT).show();
            return;
        }
        if (username.equals(myUsername)) {
            Toast.makeText(requireContext(), "Ne možete dodati sebe", Toast.LENGTH_SHORT).show();
            return;
        }

        friendRepo.searchByUsername(username, new FriendRepository.SearchCallback() {
            @Override
            public void onFound(FriendModel user) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> confirmAddFriend(user));
            }

            @Override
            public void onNotFound() {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Korisnik nije pronađen", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(String msg) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Greška: " + msg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void confirmAddFriend(FriendModel user) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Dodaj prijatelja")
                .setMessage("Dodati " + user.username + " u prijatelje?")
                .setPositiveButton("Dodaj", (d, w) ->
                        friendRepo.addFriend(myUid, user, (success, msg) ->
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                                    if (success) {
                                        etSearchUsername.setText("");
                                        loadFriends();
                                    }
                                })))
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void confirmRemoveFriend(FriendModel friend) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Ukloni prijatelja")
                .setMessage("Ukloniti " + friend.username + " iz prijatelja?")
                .setPositiveButton("Ukloni", (d, w) ->
                        friendRepo.removeFriend(myUid, friend.uid, (success, msg) ->
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                                    if (success) loadFriends();
                                })))
                .setNegativeButton("Otkaži", null)
                .show();
    }

    // ─── Game Invite ──────────────────────────────────────────────────────────

    private void sendGameInvite(FriendModel friend) {
        if (myUsername == null) {
            Toast.makeText(requireContext(), "Podaci nisu učitani, pokušajte ponovo", Toast.LENGTH_SHORT).show();
            return;
        }

        pendingInviteFriendUid = friend.uid;

        friendRepo.sendGameInvite(myUid, myUsername, friend.uid, (success, msg) -> {
            if (!isAdded()) return;
            if (!success) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Greška: " + msg, Toast.LENGTH_SHORT).show());
                pendingInviteFriendUid = null;
                return;
            }

            requireActivity().runOnUiThread(() -> showSendingInviteDialog(friend));

            friendRepo.listenToSentInvite(friend.uid, new FriendRepository.SentInviteListener() {
                @Override
                public void onAccepted(String matchId) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        dismissSendInviteDialog();
                        friendRepo.stopListeningToSentInvite(friend.uid);
                        Bundle args = new Bundle();
                        args.putString("MATCH_ID", matchId);
                        args.putString("PLAYER_ROLE", "player1");
                        Navigation.findNavController(requireView()).navigate(R.id.nav_game, args);
                    });
                }

                @Override
                public void onRejected() {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        dismissSendInviteDialog();
                        friendRepo.stopListeningToSentInvite(friend.uid);
                        Toast.makeText(requireContext(),
                                friend.username + " je odbio/la poziv", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
    }

    private void showSendingInviteDialog(FriendModel friend) {
        if (sendInviteDialog != null && sendInviteDialog.isShowing()) {
            sendInviteDialog.dismiss();
        }
        sendInviteDialog = new AlertDialog.Builder(requireContext())
                .setTitle("Poziv za igru")
                .setMessage("Čekam odgovor od " + friend.username + "...")
                .setNegativeButton("Otkaži", (d, w) -> {
                    friendRepo.cancelGameInvite(friend.uid);
                    friendRepo.stopListeningToSentInvite(friend.uid);
                    dismissSendInviteDialog();
                })
                .setCancelable(false)
                .show();
    }

    private void dismissSendInviteDialog() {
        if (sendInviteDialog != null && sendInviteDialog.isShowing()) {
            sendInviteDialog.dismiss();
        }
        if (inviteTimeoutRunnable != null) {
            inviteHandler.removeCallbacks(inviteTimeoutRunnable);
            inviteTimeoutRunnable = null;
        }
        pendingInviteFriendUid = null;
    }

    // ─── QR Code ─────────────────────────────────────────────────────────────

    private void showMyQrCode() {
        if (myUsername == null) {
            Toast.makeText(requireContext(), "Korisničko ime nije učitano", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(myUsername, BarcodeFormat.QR_CODE, 512, 512);
            Bitmap bitmap = bitMatrixToBitmap(bitMatrix);

            ImageView imageView = new ImageView(requireContext());
            imageView.setImageBitmap(bitmap);
            int padding = (int) (16 * getResources().getDisplayMetrics().density);
            imageView.setPadding(padding, padding, padding, padding);

            new AlertDialog.Builder(requireContext())
                    .setTitle("Moj QR kod")
                    .setMessage("Neka prijatelj skenira ovaj kod da te doda")
                    .setView(imageView)
                    .setPositiveButton("OK", null)
                    .show();
        } catch (WriterException e) {
            Toast.makeText(requireContext(), "Greška pri generisanju QR koda", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap bitMatrixToBitmap(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bmp.setPixel(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return bmp;
    }

    private void launchQrScanner() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("Skenirajte QR kod prijatelja");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        qrScanLauncher.launch(options);
    }

    private void onQrScanResult(ScanIntentResult result) {
        if (result.getContents() == null) return;
        etSearchUsername.setText(result.getContents());
        searchAndAddFriend();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFriends();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingInviteFriendUid != null) {
            friendRepo.stopListeningToSentInvite(pendingInviteFriendUid);
        }
        if (inviteTimeoutRunnable != null) {
            inviteHandler.removeCallbacks(inviteTimeoutRunnable);
        }
    }
}
