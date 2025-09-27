import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.*

version = "2022.10"

project {
    description = "Build & release Android via Fastlane"

    params {
        param("REPO_URL", "https://github.com/YarikKrutian/teamcity-android-ci.git")
        param("BRANCH", "main")
        param("MODE", "internal")           // internal | production
        param("ALL_COUNTRIES", "true")
        param("COUNTRY", "")
        param("VERSION", "")
        param("NOTES", "Bug fixes and performance improvements")
        param("ROLLOUT", "0.2")
        param("ROLLOUT_MODE", "promote")    // promote | direct
        param("ANDROID_PROJECT_SUBDIR", ".")
        password("JSON_KEY_FILE", "", label = "Google Play JSON key path")
    }

    vcsRoot(GitVcsRoot({
        id("AndroidRepo")
        name = "Android Repo"
        url = "%REPO_URL%"
        branch = "refs/heads/%BRANCH%"
        branchSpec = "+:refs/heads/*"
        checkoutPolicy = GitVcsRoot.CheckoutPolicy.ON_AGENT
    }))

    buildType(FastlaneRelease)
}

object FastlaneRelease : BuildType({
    name = "Android • Fastlane Build/Release"

    vcs {
        root(DslContext.settingsRoot)   // використовуємо поточний репозиторій
        checkoutMode = CheckoutMode.ON_AGENT
    }

    artifactRules = "workspace/** => artifacts.zip"

    steps {
        script {
            name = "Prepare workspace & kit"
            scriptContent = """
                set -euxo pipefail
                mkdir -p workspace
                cp -R ./android-release-kit ./workspace/
                ls -la workspace/
            """.trimIndent()
        }
        script {
            name = "Run release.sh"
            scriptContent = """
                set -euxo pipefail
                cd workspace/android-release-kit
                echo "REPO_URL=%REPO_URL%" > .env
                echo "BRANCH=%BRANCH%" >> .env
                echo "JSON_KEY_FILE=%JSON_KEY_FILE%" >> .env
                echo "ANDROID_PROJECT_SUBDIR=%ANDROID_PROJECT_SUBDIR%" >> .env
                echo "MODE=%MODE%" >> .env
                echo "COUNTRY=%COUNTRY%" >> .env
                echo "VERSION=%VERSION%" >> .env
                echo "NOTES=%NOTES%" >> .env
                echo "ROLLOUT=%ROLLOUT%" >> .env
                echo "PROMOTE=%ROLLOUT_MODE%" >> .env

                ./release.sh --mode "%MODE%" --version "%VERSION%"
            """.trimIndent()
        }
    }
})
