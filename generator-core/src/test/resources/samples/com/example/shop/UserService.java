package com.example.shop;

import java.util.List;
import java.util.stream.Collectors;

public class UserService {
    private final UserRepository userRepository;
    private final Notifier notifier;
    private final String prefix;

    public UserService(UserRepository userRepository, Notifier notifier, String prefix) {
        this.userRepository = userRepository;
        this.notifier = notifier;
        this.prefix = prefix;
    }

    public User getUser(Long id) throws UserNotFoundException {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    public User register(String name, Grade grade) {
        User user = new User();
        user.setName(prefix + name);
        user.setGrade(grade);
        User saved = userRepository.save(user);
        notifier.send(saved);
        return saved;
    }

    public void remove(Long id) {
        userRepository.deleteById(id);
    }

    public int countUsers() {
        return userRepository.findAll().size();
    }

    public List<String> names(List<User> users, int[] limits) {
        return users.stream().map(User::getName).limit(limits.length + 1L).collect(Collectors.toList());
    }

    public static String normalize(String name) {
        return name.trim().toLowerCase();
    }
}
