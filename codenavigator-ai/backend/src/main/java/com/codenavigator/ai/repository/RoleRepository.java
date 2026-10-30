package com.codenavigator.ai.repository;

import com.codenavigator.ai.entity.ERole;
import com.codenavigator.ai.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(ERole name);
}
