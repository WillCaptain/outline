package org.twelve.outline.playground.service;

import org.twelve.outline.playground.db.UserRepository;
import org.twelve.outline.playground.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
public class UserService {

    private static final long CODE_TTL_MS = 5 * 60 * 1000L;

    private final UserRepository userRepo;
    private final SmsService     smsService;

    public UserService(UserRepository userRepo, SmsService smsService) {
        this.userRepo   = userRepo;
        this.smsService = smsService;
    }

    /**
     * Generates a 6-digit OTP and dispatches it.
     * Returns the code so the controller can expose it in dev mode.
     */
    public String sendCode(String phone) {
        String code = String.format("%06d", new Random().nextInt(1_000_000));
        userRepo.saveOtp(phone, code);
        smsService.send(phone, code);
        return code;
    }

    /**
     * Verifies the submitted OTP.
     * @return [token, User] pair on success, empty on failure.
     */
    public Optional<Map.Entry<String, User>> verify(String phone, String code) {
        return userRepo.findOtp(phone).flatMap(otp -> {
            if (System.currentTimeMillis() - otp.sentAt() > CODE_TTL_MS) return Optional.empty();
            if (!code.equals(otp.code())) return Optional.empty();

            userRepo.deleteOtp(phone);
            User   user  = userRepo.upsert(phone);
            String token = UUID.randomUUID().toString().replace("-", "");
            userRepo.saveSession(token, user.getId());
            return Optional.of(Map.entry(token, user));
        });
    }

    public Optional<User> getByToken(String token) {
        if (token == null) return Optional.empty();
        return userRepo.findUserIdByToken(token)
                       .flatMap(userRepo::findById);
    }

    public void logout(String token) {
        if (token != null) userRepo.deleteSession(token);
    }

    public boolean updateUsername(String token, String username) {
        return getByToken(token).map(u -> {
            userRepo.updateUsername(u.getId(), username);
            u.setUsername(username);
            return true;
        }).orElse(false);
    }
}
