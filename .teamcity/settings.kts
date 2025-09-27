import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.*

version = "2022.10"

project {
    description = "Build & release Android via Fastlane (pull branch, enter release notes, upload/rollout)."

    params {
        text("REPO_URL", "https://github.com/your-org/your-android-repo.git", label = "Git repo URL", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("BRANCH", "develop", label = "Branch to build", display = ParameterDisplay.PROMPT, allowEmpty = false)
        select("MODE", "internal", label = "Mode", options = listOf("internal", "production"), display = ParameterDisplay.PROMPT, allowEmpty = false)
        checkbox("ALL_COUNTRIES", "true", label = "All countries?", checked = "true", unchecked = "false", display = ParameterDisplay.PROMPT)
        text("COUNTRY", "", label = "One flavor (ng/ke/ug/...)", display = ParameterDisplay.PROMPT, allowEmpty = true)
        text("VERSION", "", label = "Version code (e.g. 14_003_15)", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("NOTES", "Bug fixes and performance improvements", label = "Release notes (Internal only)", display = ParameterDisplay.PROMPT, allowEmpty = true)
        text("ROLLOUT", "0.2", label = "Rollout fraction (Production only, 0.0<r<=1.0)", display = ParameterDisplay.PROMPT, allowEmpty = true)
        select("ROLLOUT_MODE", "promote", label = "Production rollout mode", options = listOf("promote", "direct"), display = ParameterDisplay.PROMPT, allowEmpty = true)
        text("ANDROID_PROJECT_SUBDIR", ".", label = "Android project subdir", display = ParameterDisplay.PROMPT, allowEmpty = false)
        password("JSON_KEY_FILE", "", label = "Path to Google Play JSON key (on agent)", display = ParameterDisplay.PROMPT)
    }

    val vcsRoot = vcsRoot {
        id("AndroidRepo")
        name = "Android Repo (parameterised)"
        url = "%REPO_URL%"
        branch = "refs/heads/%BRANCH%"
        branchSpec = "+:refs/heads/*"
        checkoutPolicy = CheckoutPolicy.ON_AGENT
    }

    vcsRoot(vcsRoot)

    buildType(FastlaneRelease(vcsRoot))
}

class FastlaneRelease(private val root: VcsRoot) : BuildType({
    name = "Android • Fastlane Build/Release"

    vcs {
        root(root)
        checkoutMode = CheckoutMode.ON_AGENT
        cleanCheckout = true
    }

    artifactRules = "workspace/** => artifacts.zip"

    requirements {
        exists("env.JAVA_HOME")
        exists("env.ANDROID_HOME")
    }

    steps {
        script {
            name = "Prepare workspace & kit"
            scriptContent = """set -euxo pipefail
mkdir -p workspace
KIT_DIR="$(pwd)/../android-release-kit"
if [ ! -d "$KIT_DIR" ]; then
  echo "ERROR: android-release-kit not found next to checkout dir"; exit 1
fi
cp -R "$KIT_DIR" ./
ls -la
"""
        }
        script {
            name = "Run release.sh"
            scriptContent = """set -euxo pipefail
cd android-release-kit
cat > .env <<EOF
REPO_URL=%REPO_URL%
BRANCH=%BRANCH%
JSON_KEY_FILE=%JSON_KEY_FILE%
ANDROID_PROJECT_SUBDIR=%ANDROID_PROJECT_SUBDIR%
MODE=%MODE%
COUNTRY=%COUNTRY%
VERSION=%VERSION%
NOTES=%NOTES%
ROLLOUT=%ROLLOUT%
PROMOTE=$([ "%ROLLOUT_MODE%" = "promote" ] && echo true || echo false)
EOF
if [ "%MODE%" = "internal" ]; then
  if [ "%ALL_COUNTRIES%" = "true" ]; then
    ./release.sh --mode internal --all --version "%VERSION%" --notes "%NOTES%"
  else
    ./release.sh --mode internal --country "%COUNTRY%" --version "%VERSION%" --notes "%NOTES%"
  fi
else
  if [ "%ALL_COUNTRIES%" = "true" ]; then
    if [ "%ROLLOUT_MODE%" = "direct" ]; then
      ./release.sh --mode production --all --direct --rollout "%ROLLOUT%" --version "%VERSION%"
    else
      ./release.sh --mode production --all --promote --rollout "%ROLLOUT%" --version "%VERSION%"
    fi
  else
    if [ "%ROLLOUT_MODE%" = "direct" ]; then
      ./release.sh --mode production --country "%COUNTRY%" --direct --rollout "%ROLLOUT%" --version "%VERSION%"
    else
      ./release.sh --mode production --country "%COUNTRY%" --promote --rollout "%ROLLOUT%" --version "%VERSION%"
    fi
  fi
fi
"""
        }
    }

    triggers {
        // Uncomment to enable VCS trigger on the chosen branch
        // vcs {
        //     branchFilter = "+:%BRANCH%"
        // }
    }
})