package com.example.slagalica.helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;


public class KorakHelper {

    public static class KorakQuestion {
        public final String answer;
        public final List<String> hints; // index 0 = najtezi, index 6 = najlaksi

        public KorakQuestion(String answer, String[] hints) {
            this.answer = answer;
            this.hints = Arrays.asList(hints);
        }
    }

    private static final List<KorakQuestion> QUESTIONS = new ArrayList<>();

    static {
        QUESTIONS.add(new KorakQuestion("NIKOLA TESLA", new String[]{
                "Rodjen 1856. godine",
                "Srpsko-američki izumitelj",
                "Patenat broj 645.576",
                "Radio stanica Wardenclyffe",
                "Rat struja sa Edisonom",
                "Izmislio je naizmjeničnu struju",
                "Njegovo ime je mjerna jedinica za magnetno polje"
        }));

        QUESTIONS.add(new KorakQuestion("EIFFELOV TORANJ", new String[]{
                "Otvoren 1889. godine",
                "Visok je 330 metara",
                "Nalazi se na Champ de Mars",
                "Projektovao ga Gustave",
                "Simbol jednog evropskog grada",
                "Nalazi se u Parizu",
                "Čelična rešetkasta konstrukcija - turistička atrakcija Francuske"
        }));

        QUESTIONS.add(new KorakQuestion("BASKETBALL", new String[]{
                "Izumio ga James Naismith 1891.",
                "Prvobitan koš je bila korpa za breskve",
                "NBA liga ima 30 timova",
                "Igra se na parketu 28x15m",
                "Michael Jordan je legenda ove igre",
                "Igra se sa narandžastom loptom",
                "Sport u kom se ubacuje lopta u koš"
        }));

        QUESTIONS.add(new KorakQuestion("AMAZON", new String[]{
                "Kompanija osnovana 1994.",
                "Jeff Bezos je osnivač",
                "Sjedište u Seattlu",
                "AWS je jedan od njenih servisa",
                "Najduža rijeka na svijetu nosi isto ime",
                "Prodaje knjige, elektroniku i još milion stvari",
                "Najveća svjetska online prodavnica"
        }));

        QUESTIONS.add(new KorakQuestion("TITANIC", new String[]{
                "Potopio se 1912. godine",
                "Udario je u ledeni brijeg",
                "Gradjen u Belfastu",
                "Kompanija White Star Line",
                "Film iz 1997. sa Leonardom DiCapriom",
                "Potonuo na prvom putovanju",
                "Slavni brod koji je 'bio nepotopiv'"
        }));
    }

    private final Random random = new Random();


    public KorakQuestion getRandomQuestion() {
        return QUESTIONS.get(random.nextInt(QUESTIONS.size()));
    }


    public int calculateScore(int hintIndex) {
        int score = 20 - (hintIndex - 1) * 2;
        return Math.max(score, 0);
    }
}
