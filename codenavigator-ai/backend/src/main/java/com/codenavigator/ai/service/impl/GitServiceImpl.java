package com.codenavigator.ai.service.impl;

import com.codenavigator.ai.service.GitService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
@Slf4j
public class GitServiceImpl implements GitService {

    @Override
    public void cloneRepository(String repoUrl, File destination) throws GitAPIException {
        log.info("Starting shallow clone of repository: {} to destination: {}", repoUrl, destination.getAbsolutePath());
        
        try (Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(destination)
                .setCloneAllBranches(false)
                .setDepth(1)
                .call()) {
            log.info("Successfully cloned repository: {}", repoUrl);
        } catch (GitAPIException e) {
            log.error("Failed to clone repository: {}. Error: {}", repoUrl, e.getMessage(), e);
            throw e;
        }
    }
}
