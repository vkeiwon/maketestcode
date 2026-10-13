package com.example.shop;

public interface Notifier {
    void send(User user);
    void send(String message);
}
