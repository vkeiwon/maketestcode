package com.example.shop;

public abstract class AbstractPolicy {
    protected abstract int base();

    public String describe(Grade grade) {
        return grade.name();
    }
}
