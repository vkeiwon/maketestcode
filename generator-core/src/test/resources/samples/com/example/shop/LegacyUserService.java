package com.example.shop;

/** 필드 주입 방식(생성자 없음) → @InjectMocks 가 사용되어야 한다. */
public class LegacyUserService {
    private UserRepository userRepository;

    public User find(Long id) {
        return userRepository.findById(id).orElse(null);
    }
}
