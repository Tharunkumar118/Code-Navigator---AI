package com.codenavigator.ai.service;

import java.io.File;
import org.eclipse.jgit.api.errors.GitAPIException;

public interface GitService {
    /**
     * Clones a public repository from the specified URL to the destination folder.
     *
     * @param repoUrl     the remote HTTP/HTTPS Git repository URL
     * @param destination the local folder where the repository should be cloned
     * @throws GitAPIException if JGit fails to clone
     */
    void cloneRepository(String repoUrl, File destination) throws GitAPIException;
}
