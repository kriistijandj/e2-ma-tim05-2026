package com.example.slagalica.viewmodel;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalica.models.RegionModel;
import com.example.slagalica.repository.RegionRepository;

import java.util.List;

public class RegionViewModel extends ViewModel {

    private final RegionRepository repository = new RegionRepository();
    private final MutableLiveData<List<RegionModel>> regionList = new MutableLiveData<>();
    private final MutableLiveData<RegionModel> selectedRegion = new MutableLiveData<>();
    private final MutableLiveData<List<double[]>> userLocations = new MutableLiveData<>();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long REFRESH_INTERVAL = 2 * 60 * 1000L;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            loadRegions();
            handler.postDelayed(this, REFRESH_INTERVAL);
        }
    };

    public LiveData<List<RegionModel>> getRegionList() { return regionList; }
    public LiveData<RegionModel> getSelectedRegion() { return selectedRegion; }
    public LiveData<List<double[]>> getUserLocations() { return userLocations; }

    public void startAutoRefresh() {
        handler.removeCallbacks(refreshRunnable);
        handler.post(refreshRunnable);
    }

    public void stopAutoRefresh() {
        handler.removeCallbacks(refreshRunnable);
    }

    public void loadRegions() {
        repository.loadRegionList(regionList::setValue);
    }

    public void loadUserLocations() {
        repository.loadUserLocations(userLocations::setValue);
    }

    public void loadRegionDetail(String regionName) {
        repository.loadRegionDetail(regionName, selectedRegion::setValue);
    }

    public String getCurrentCycleId() {
        return repository.getCurrentCycleId();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        handler.removeCallbacks(refreshRunnable);
    }
}
