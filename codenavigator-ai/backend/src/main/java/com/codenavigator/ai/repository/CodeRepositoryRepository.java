package com.codenavigator.ai.repository;

import com.codenavigator.ai.entity.CodeRepository;
import com.codenavigator.ai.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodeRepositoryRepository extends JpaRepository<CodeRepository, Long> {

    List<CodeRepository> findByOwnerOrderByCreatedAtDesc(User owner, Pageable pageable);

    long countByOwner(User owner);
}
