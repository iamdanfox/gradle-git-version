/*
 * Copyright 2015 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.gitversion

import groovy.transform.*

import java.util.regex.Matcher

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.gradle.api.Plugin
import org.gradle.api.Project

class GitVersionPlugin implements Plugin<Project> {

    // Gradle returns 'unspecified' when no version is set
    private static final String UNSPECIFIED_VERSION = 'unspecified'
    private static final int VERSION_ABBR_LENGTH = 10

    @Memoized
    private File gitDir(Project project) {
        return getRootGitDir(project.rootDir)
    }

    @Memoized
    private Git gitRepo(Project project) {
        return Git.wrap(new FileRepository(gitDir(project)))
    }

    @Memoized
    private String gitDesc(Project project) {
        Git git = gitRepo(project)
        try {
            String version = git.describe().call() ?: UNSPECIFIED_VERSION
            boolean isClean = git.status().call().isClean()
            return version + (isClean ? '' : '.dirty')
        } catch (Throwable t) {
            return UNSPECIFIED_VERSION
        }
    }

    @Memoized
    private String gitHash(Project project) {
        Git git = gitRepo(project)
        try {
            return git.getRepository().getRef("HEAD").getObjectId().abbreviate(VERSION_ABBR_LENGTH).name()
        } catch (Throwable t) {
            return UNSPECIFIED_VERSION
        }
    }

    void apply(Project project) {
        project.ext.gitVersion = {
            return gitDesc(project)
        }

        project.ext.versionDetails = {
            String description = gitDesc(project)
            String hash = gitHash(project)

            if (description.equals(UNSPECIFIED_VERSION)) {
                return null
            }

            if (!(description =~ /.*g.?[0-9a-fA-F]{3,}/)) {
                // Description has no git hash so it is just the tag name
                return new VersionDetails(description, 0, hash)
            }

            Matcher match = (description =~ /(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}/)
            String tagName = match[0][1]
            int commitCount = Integer.valueOf(match[0][2])

            return new VersionDetails(tagName, commitCount, hash)
        }

        project.tasks.create('printVersion') {
            group = 'Versioning'
            description = 'Prints the project\'s configured version to standard out'
            doLast {
                println project.version
            }
        }
    }

    private static File getRootGitDir(currentRoot) {
        File gitDir = scanForRootGitDir(currentRoot)
        if (!gitDir.exists()) {
            throw new IllegalArgumentException('Cannot find \'.git\' directory')
        }
        return gitDir
    }

    private static File scanForRootGitDir(File currentRoot) {
        File gitDir = new File(currentRoot, '.git')

        if (gitDir.exists()) {
            return gitDir
        }

        // stop at the root directory, return non-existing File object
        if (currentRoot.parentFile == null) {
            return gitDir
        }

        // look in parent directory
        return scanForRootGitDir(currentRoot.parentFile)
    }
}
