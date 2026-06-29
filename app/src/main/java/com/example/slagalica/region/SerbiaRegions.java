package com.example.slagalica.region;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class SerbiaRegions {
    public static final List<String> ALL = Arrays.asList(
            "Beograd", "Vojvodina", "Šumadija",
            "Zapadna Srbija", "Istočna Srbija", "Južna Srbija", "Raška"
    );

    public static final double[][] BOUNDS = {
        {44.6, 45.0, 20.2, 20.8},
        {45.0, 46.2, 18.9, 21.3},
        {43.7, 44.6, 20.4, 21.4},
        {43.5, 44.5, 19.0, 20.4},
        {43.3, 44.5, 21.4, 22.9},
        {42.2, 43.7, 20.5, 22.5},
        {43.0, 43.9, 19.8, 21.2}
    };

    public static final int[] DRAWABLE_IDS = {
        com.example.slagalica.R.drawable.ic_region_beograd,
        com.example.slagalica.R.drawable.ic_region_vojvodina,
        com.example.slagalica.R.drawable.ic_region_sumadija,
        com.example.slagalica.R.drawable.ic_region_zapadna,
        com.example.slagalica.R.drawable.ic_region_istocna,
        com.example.slagalica.R.drawable.ic_region_juzna,
        com.example.slagalica.R.drawable.ic_region_raska
    };

    public static int indexOf(String region) {
        return ALL.indexOf(region);
    }

    public static double[] randomLatLng(int regionIndex) {
        double[] b = BOUNDS[regionIndex];
        Random r = new Random();
        double lat = b[0] + r.nextDouble() * (b[1] - b[0]);
        double lng = b[2] + r.nextDouble() * (b[3] - b[2]);
        return new double[]{lat, lng};
    }

    public static boolean isValid(String region) {
        return ALL.contains(region);
    }
}
