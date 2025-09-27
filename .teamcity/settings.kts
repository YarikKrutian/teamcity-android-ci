import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.*
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs

version = "2022.10"

project {
    description = "Build & release Android via Fastlane (pull branch, release notes, upload/rollout)."

    // Параметри (без allowEmpty, щоб не падало на старших API)
    params {
        param("REPO_URL", "https://github.com/YarikKrutian/teamcity-android-ci.git")
        param("BRANCH", "main")
        param("MODE", "internal")                // internal | production
        param("ALL_COUNTRIES", "true")           // true | false
        param("COUNTRY", "")                     // ng/ke/ug/...
        param("VERSION", "")                     // 14_003_15
        param("NOTES", "Bug fixes and performance improvements")
        param("ROLLOUT", "0.2")                  // 0.0<r<=1.0
        param("ROLLOUT_MODE", "promote")         // promote | direct
        param("ANDROID_PROJECT_SUBDIR", ".")
        password("JSON_KEY_FILE", "", label = "Path to Google Play JSON key (on agent)")
    }

    // Правильний Git VCS root
    val AndroidRepo = GitVcsRoot({
        id("AndroidRepo")
        name = "Android Repo (parameterised)"
        url = "%REPO_URL%"
        branch = "refs/heads/%BRANCH%"
        branchSpec = "+:refs/heads/*"
        checkoutPolicy = GitVcsRoot.CheckoutPolicy.ON_AGENT
    })

    vcsRoot(AndroidRepo)
    buildType(FastlaneRelease(AndroidRepo))
}

class FastlaneRelease(private val rootVcs: VcsRoot) : BuildType({
    name = "Android • Fastlane Build/Release"

    vcs {
        root(rootVcs)
        checkoutMode = CheckoutMode.ON_AGENT
        cleanCheckout = true
    }

    artifactRules = "workspace/** => artifacts.zip"

    requirements {
        exists("env.JAVA_HOME")
        exists("env.ANDROID_HOME")
        // Ruby/Fastlane — або через PATH, або постав через Gemfile
    }

    steps {
        script {
            name = "Prepare workspace & kit"
            scriptContent = """
                set -euxo pipefail
                mkdir -p workspace
                KIT_DIR="$(pwd)/../android-release-kit"
                if [ ! -d "$KIT_DIR" ]; then
                  echo "ERROR: android-release-kit not found next to checkout dir"; exit 1
                fi
                cp -R "$KIT_DIR" ./
                ls -la
            """.trimIndent()
        }
        script {
            name = "Run release.sh"
            scriptContent = """
                set -euxo pipefail
                cd android-release-kit
                cat > .env <<'EOF'
REPO_URL=%REPO_URL%
BRANCH=%BRANCH%
JSON_KEY_FILE=%JSON_KEY_FILE%
ANDROID_PROJECT_SUBDIR=%ANDROID_PROJECT_SUBDIR%
MODE=%MODE%
COUNTRY=%COUNTRY%
VERSION=%VERSION%
NOTES=%NOTES%
ROLLOUT=%ROLLOUT%
PROMOTE=%ROLLOUT_MODE%
EOF
                # Нормалізація PROMOTE -> true/false
                if [ "%ROLLOUT_MODE%" = "promote" ]; then PROMOTE_FLAG="--promote"; else PROMOTE_FLAG="--direct"; fi

                if [ "%MODE%" = "internal" ]; then
                  if [ "%ALL_COUNTRIES%" = "true" ]; then
                    ./release.sh --mode internal --all --version "%VERSION%" --notes "%NOTES%"
                  else
                    ./release.sh --mode internal --country "%COUNTRY%" --version "%VERSION%" --notes "%NOTES%"
                  fi
                else
                  if [ "%ALL_COUNTRIES%" = "true" ]; then
                    ./release.sh --mode production --all $PROMOTE_FLAG --rollout "%ROLLOUT%" --version "%VERSION%"
                  else
                    ./release.sh --mode production --country "%COUNTRY%" $PROMOTE_FLAG --rollout "%ROLLOUT%" --version "%VERSION%"
                  fi
                fi
            """.trimIndent()
        }
    }

    // Тригер за потреби:
    // triggers {
    //   vcs { branchFilter = "+:%BRANCH%" }
    // }
})
