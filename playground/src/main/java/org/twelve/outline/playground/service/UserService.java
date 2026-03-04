package org.twelve.outline.playground.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.twelve.outline.playground.db.UserRepository;
import org.twelve.outline.playground.model.User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final int BCRYPT_COST = 10;

    private final UserRepository userRepo;

    public UserService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    /**
     * Registers a new user. Username must be unique.
     * @return [token, User] on success
     */
    public Optional<Map.Entry<String, User>> register(String username, String password) {
        if (username == null || (username = username.trim()).isBlank() || username.length() > 32) return Optional.empty();
        if (password == null || password.length() < 4 || password.length() > 64) return Optional.empty();
        if (!username.matches("[a-zA-Z0-9_]+")) return Optional.empty();

        if (userRepo.findByUsername(username).isPresent()) return Optional.empty();

        String hash = BCrypt.withDefaults().hashToString(BCRYPT_COST, password.toCharArray());
        User user = userRepo.create(username, hash);
        String token = UUID.randomUUID().toString().replace("-", "");
        userRepo.saveSession(token, user.getId());
        return Optional.of(Map.entry(token, user));
    }

    /**
     * Logs in with username and password.
     * @return [token, User] on success
     */
    public Optional<Map.Entry<String, User>> login(String username, String password) {
        if (username == null || password == null) return Optional.empty();

        return userRepo.findByUsername(username.trim())
                .flatMap(user -> userRepo.findPasswordHashByUsername(user.getUsername())
                        .filter(hash -> BCrypt.verifyer().verify(password.toCharArray(), hash).verified)
                        .map(__ -> {
                            String token = UUID.randomUUID().toString().replace("-", "");
                            userRepo.saveSession(token, user.getId());
                            return Map.entry(token, user);
                        }));
    }

    public Optional<User> getByToken(String token) {
        if (token == null) return Optional.empty();
        return userRepo.findUserIdByToken(token)
                       .flatMap(userRepo::findById);
    }

    public void logout(String token) {
        if (token != null) userRepo.deleteSession(token);
    }
}
