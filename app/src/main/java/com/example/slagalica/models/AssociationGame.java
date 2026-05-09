package com.example.slagalica.models;

public class AssociationGame {

    public Round[] rounds;
    public int currentRound = 0;

    private Player player1;
    private Player player2;
    private Player currentPlayer;

    public AssociationGame(Round[] rounds, Player p1, Player p2) {
        this.rounds = rounds;
        this.player1 = p1;
        this.player2 = p2;
        this.currentPlayer = p1;
    }

    // ---------- TURN LOGIC ----------

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public void switchPlayer() {
        currentPlayer = (currentPlayer == player1) ? player2 : player1;
    }

    // ---------- FIELD OPENING ----------

    public boolean openField(int colIndex, int rowIndex) {
        Round round = rounds[currentRound];
        Column column = round.columns[colIndex];

        if (column.solved || column.opened[rowIndex]) return false;

        column.opened[rowIndex] = true;
        return true;
    }

    public String getFieldValue(int colIndex, int rowIndex) {
        return rounds[currentRound].columns[colIndex].fields[rowIndex];
    }

    // ---------- COLUMN GUESS ----------

    public boolean guessColumn(int colIndex, String guess) {
        Column col = rounds[currentRound].columns[colIndex];

        if (col.solved) return false;

        if (col.solution.equalsIgnoreCase(guess.trim())) {
            col.solved = true;

            int points = 2 + col.unopenedCount();
            currentPlayer.score += points;

            return true;
        } else {
            switchPlayer();
            return false;
        }
    }

    // ---------- FINAL GUESS ----------

    public boolean guessFinal(String guess) {
        Round round = rounds[currentRound];

        if (round.finalSolution.equalsIgnoreCase(guess.trim())) {

            int score = 7;

            for (Column c : round.columns) {
                if (!c.solved) {
                    score += 6;
                } else {
                    score += 2 + c.unopenedCount();
                }
            }

            currentPlayer.score += score;
            round.finished = true;

            return true;
        } else {
            switchPlayer();
            return false;
        }
    }

    // ---------- ROUND CONTROL ----------

    public boolean isRoundFinished() {
        return rounds[currentRound].finished;
    }

    public void nextRound() {
        if (currentRound < rounds.length - 1) {
            currentRound++;
        }
    }
    public Player getPlayer1() {
        return player1;
    }

    public Player getPlayer2() {
        return player2;
    }
}