package com.codenavigator.ai.config;

import com.codenavigator.ai.entity.ERole;
import com.codenavigator.ai.entity.Role;
import com.codenavigator.ai.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Runs once, right after the application context starts (see Part A.2, step 7).
 * Ensures the fixed role set always exists, idempotently.
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {
        for (ERole role : ERole.values()) {
            roleRepository.findByName(role).orElseGet(() -> {
                log.info("Seeding missing role: {}", role);
                return roleRepository.save(new Role(role));
            });
        }
    }
}
