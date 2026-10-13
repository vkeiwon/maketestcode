package com.example.calc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public class Calculator {
    private int memory;

    public int add(int a, int b) { return a + b; }

    public double add(double a, double b) { return a + b; }

    public int divide(int a, int b) throws ArithmeticException {
        if (b == 0) throw new ArithmeticException("/ by zero");
        return a / b;
    }

    public BigDecimal scale(BigDecimal value, LocalDate date, Map<String, Integer> options) {
        return value;
    }

    public void reset() { memory = 0; }

    public boolean isPositive(long value) { return value > 0; }
}
