package com.campusguard.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserRepository extends JpaRepository<User, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> findForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findAllByRole(UserRole role);
}
