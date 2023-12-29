plugins {
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "1.1.17"
}

gitHooks {
    commitMsg {
        conventionalCommits()
    }
    createHooks()
}

rootProject.name = "PPS-22-direct-style-experiments"