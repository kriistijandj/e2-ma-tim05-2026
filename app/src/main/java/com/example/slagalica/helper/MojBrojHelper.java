package com.example.slagalica.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Stack;


public class MojBrojHelper {

    private final Random random = new Random();


    private static final int[] SREDNJI = {10, 15, 20};


    private static final int[] VELIKI = {25, 50, 75, 100};


    public int generateTargetNumber() {
        return 100 + random.nextInt(900);
    }


    public List<Integer> generateAvailableNumbers() {
        List<Integer> numbers = new ArrayList<>();


        for (int i = 0; i < 4; i++) {
            numbers.add(1 + random.nextInt(9));
        }


        numbers.add(SREDNJI[random.nextInt(SREDNJI.length)]);


        numbers.add(VELIKI[random.nextInt(VELIKI.length)]);

        return numbers;
    }


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



    private double evalExpression(String expr) {

        expr = expr.replaceAll("\\s+", "");

        Stack<Double> values = new Stack<>();
        Stack<Character> ops = new Stack<>();

        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);


            if (Character.isDigit(c)) {
                int start = i;
                while (i < expr.length() && Character.isDigit(expr.charAt(i))) i++;
                values.push((double) Integer.parseInt(expr.substring(start, i)));
                i--;
            }

            else if (c == '(') {
                ops.push(c);
            }

            else if (c == ')') {
                while (!ops.isEmpty() && ops.peek() != '(') {
                    values.push(applyOp(ops.pop(), values.pop(), values.pop()));
                }
                if (!ops.isEmpty()) ops.pop();
            }
            // Operator
            else if (c == '+' || c == '-' || c == '*' || c == '/') {

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


    public int distanceFromTarget(int result, int target) {
        if (result == Integer.MIN_VALUE) return Integer.MAX_VALUE;
        return Math.abs(result - target);
    }
}
