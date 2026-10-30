package com.codenavigator.ai.repository;

import com.codenavigator.ai.entity.ERole;
import com.codenavigator.ai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Navigates User -> roles -> name via Spring Data's property-path derivation
    long countByRoles_Name(ERole roleName);
}
