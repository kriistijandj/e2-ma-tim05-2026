package com.example.slagalica.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Stack;

/**
 * Pomoćna klasa za igru "Moj Broj":
 *  - Generisanje traženog broja (100-999)
 *  - Generisanje 6 dostupnih brojeva (4 jednocifrena + 1 srednji + 1 veliki)
 *  - Evaluacija matematičkog izraza bez vanjskih biblioteka
 */
public class MojBrojHelper {

    private final Random random = new Random();

    // Srednji brojevi (10, 15, 20)
    private static final int[] SREDNJI = {10, 15, 20};

    // Veliki brojevi (25, 50, 75, 100)
    private static final int[] VELIKI = {25, 50, 75, 100};

    /**
     * Generiše traženi broj u opsegu 100-999.
     */
    public int generateTargetNumber() {
        return 100 + random.nextInt(900);
    }

    /**
     * Generiše listu od 6 dostupnih brojeva:
     *  - 4 jednocifrena (1-9)
     *  - 1 srednji (10, 15 ili 20)
     *  - 1 veliki (25, 50, 75 ili 100)
     */
    public List<Integer> generateAvailableNumbers() {
        List<Integer> numbers = new ArrayList<>();

        // 4 jednocifrena
        for (int i = 0; i < 4; i++) {
            numbers.add(1 + random.nextInt(9));
        }

        // 1 srednji
        numbers.add(SREDNJI[random.nextInt(SREDNJI.length)]);

        // 1 veliki
        numbers.add(VELIKI[random.nextInt(VELIKI.length)]);

        return numbers;
    }

    /**
     * Evaluira matematički izraz dat kao String.
     * Podržava +, -, *, /, zagrade.
     * Vraća rezultat kao int, ili Integer.MIN_VALUE ako je izraz neispravan.
     * Dijeljenje je cjelobrojno (bez ostatka), i nije dozvoljeno dijeljenje
     * ako ne dijeli bez ostatka (pravilo igre).
     */
    public int evaluate(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return Integer.MIN_VALUE;
        }
        try {
            return (int) evalExpression(expression.trim());
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    // ===== Interna implementacija parsera (Shunting-yard algorithm) =====

    private double evalExpression(String expr) {
        // Ukloni razmake
        expr = expr.replaceAll("\\s+", "");

        Stack<Double> values = new Stack<>();
        Stack<Character> ops = new Stack<>();

        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);

            // Broj (višecifren)
            if (Character.isDigit(c)) {
                int start = i;
                while (i < expr.length() && Character.isDigit(expr.charAt(i))) i++;
                values.push((double) Integer.parseInt(expr.substring(start, i)));
                i--;
            }
            // Otvorena zagrada
            else if (c == '(') {
                ops.push(c);
            }
            // Zatvorena zagrada
            else if (c == ')') {
                while (!ops.isEmpty() && ops.peek() != '(') {
                    values.push(applyOp(ops.pop(), values.pop(), values.pop()));
                }
                if (!ops.isEmpty()) ops.pop(); // ukloni '('
            }
            // Operator
            else if (c == '+' || c == '-' || c == '*' || c == '/') {
                // Unarni minus (npr. početak ili iza operatora)
                if (c == '-' && (i == 0 || expr.charAt(i - 1) == '(')) {
                    values.push(0.0);
                }
                while (!ops.isEmpty() && hasPrecedence(c, ops.peek())) {
                    values.push(applyOp(ops.pop(), values.pop(), values.pop()));
                }
                ops.push(c);
            }
        }

        while (!ops.isEmpty()) {
            values.push(applyOp(ops.pop(), values.pop(), values.pop()));
        }

        double result = values.pop();

        // Provjera da li je rezultat cijeli broj (dijeljenje mora biti bez ostatka)
        if (result != Math.floor(result)) {
            throw new ArithmeticException("Result is not integer");
        }

        return result;
    }

    private boolean hasPrecedence(char op1, char op2) {
        if (op2 == '(' || op2 == ')') return false;
        if ((op1 == '*' || op1 == '/') && (op2 == '+' || op2 == '-')) return false;
        return true;
    }

    private double applyOp(char op, double b, double a) {
        switch (op) {
            case '+': return a + b;
            case '-': return a - b;
            case '*': return a * b;
            case '/':
                if (b == 0) throw new ArithmeticException("Division by zero");
                double result = a / b;
                if (result != Math.floor(result))
                    throw new ArithmeticException("Non-integer division");
                return result;
        }
        throw new IllegalArgumentException("Unknown operator: " + op);
    }

    /**
     * Izračunava razliku između rezultata i traženog broja.
     * Koristi se za određivanje ko je bliže.
     * Vraća Integer.MAX_VALUE ako igrač nije uneo ništa.
     */
    public int distanceFromTarget(int result, int target) {
        if (result == Integer.MIN_VALUE) return Integer.MAX_VALUE;
        return Math.abs(result - target);
    }
}
