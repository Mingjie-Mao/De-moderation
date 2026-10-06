package com.campusguard.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusguard.AbstractIntegrationTest;
import com.campusguard.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;

class OneTimeTokenConcurrencyIntegrationTest extends AbstractIntegrationTest {
    @Autowired private AuthService auth;
    @Autowired private RefreshTokenService refreshTokens;
    @Autowired private PasswordResetService passwordReset;
    @Autowired private PasswordResetTokenRepository resetTokens;

    @Test
    void concurrentRefreshesConsumeTheTokenOnlyOnce() throws Exception {
        User user = newUser();
        String raw = refreshTokens.issue(user);
        List<Boolean> results = race(() -> {
            try {
                auth.refresh(new RefreshTokenRequest(raw));
                return true;
            } catch (BadCredentialsException ex) {
                return false;
            }
        });
        assertThat(results).containsExactlyInAnyOrder(true, false);
    }

    @Test
    void concurrentPasswordResetsConsumeTheTokenOnlyOnce() throws Exception {
        User user = newUser();
        String raw = "reset-" + java.util.UUID.randomUUID();
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        resetTokens.saveAndFlush(new PasswordResetToken(hash, user, Instant.now().plusSeconds(1800)));

        List<Boolean> results = race(() -> {
            try {
                passwordReset.confirm(new PasswordResetConfirmRequest(raw, "different-long-password"));
                return true;
            } catch (BadCredentialsException ex) {
                return false;
            }
        });
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(passwordEncoder.matches("different-long-password",
                userRepository.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    private List<Boolean> race(Callable<Boolean> attempt) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> synchronizedAttempt = () -> {
                start.await();
                return attempt.call();
            };
            Future<Boolean> first = pool.submit(synchronizedAttempt);
            Future<Boolean> second = pool.submit(synchronizedAttempt);
            start.countDown();
            return List.of(first.get(), second.get());
        }
    }
}
