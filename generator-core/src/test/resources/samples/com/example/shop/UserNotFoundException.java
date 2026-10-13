package com.example.shop;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) { super("user not found: " + id); }
}
