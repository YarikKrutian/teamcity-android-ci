#!/usr/bin/env bash
set -euo pipefail

if [[ -f ".env" ]]; then
  set -a
  source .env
  set +a
fi

usage() {
  cat <<'USAGE'
Usage:
  ./release.sh --mode internal   --all|--country <flavor> --version <code> [--notes "text"]
  ./release.sh --mode production --all|--country <flavor> --version <code> --rollout <0..1> (--promote|--direct)
USAGE
}

REPO_URL="${REPO_URL:-}"
BRANCH="${BRANCH:-main}"
MODE="${MODE:-}"
ALL=false
COUNTRY="${COUNTRY:-}"
VERSION="${VERSION:-}"
NOTES="${NOTES:-}"
ROLLOUT="${ROLLOUT:-}"
ROLLOUT_MODE="${ROLLOUT_MODE:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo) REPO_URL="$2"; shift 2;;
    --branch) BRANCH="$2"; shift 2;;
    --mode) MODE="$2"; shift 2;;
    --all) ALL=true; shift;;
    --country) COUNTRY="$2"; shift 2;;
    --version) VERSION="$2"; shift 2;;
    --notes) NOTES="$2"; shift 2;;
    --rollout) ROLLOUT="$2"; shift 2;;
    --promote) ROLLOUT_MODE="promote"; shift;;
    --direct) ROLLOUT_MODE="direct"; shift;;
    -h|--help) usage; exit 0;;
    *) echo "Unknown arg: $1"; usage; exit 1;;
  esac
done

if [[ -z "$REPO_URL" ]]; then echo "ERROR: REPO_URL is required"; exit 1; fi
if [[ -z "$MODE" ]]; then echo "ERROR: --mode required"; exit 1; fi
if [[ -z "$VERSION" ]]; then echo "ERROR: --version required"; exit 1; fi

APP_VERSION_CODE="$(echo "$VERSION" | tr -d '_')"
export APP_VERSION_CODE

mkdir -p workspace
cd workspace

repo_name=$(basename -s .git "$REPO_URL")
if [[ ! -d "$repo_name" ]]; then
  git clone "$REPO_URL" "$repo_name"
fi
cd "$repo_name"
git fetch --all --prune
git checkout "$BRANCH"
git pull --rebase origin "$BRANCH"

if [[ "$MODE" == "internal" ]]; then
  if $ALL; then
    fastlane android releaseAll
  else
    fastlane android "release_${COUNTRY}"
  fi
else
  if [[ "$ROLLOUT_MODE" == "promote" ]]; then
    fastlane android "rollout_${COUNTRY}" version_code="$APP_VERSION_CODE" mode="promote" rollout="$ROLLOUT"
  else
    fastlane android "rollout_${COUNTRY}" version_code="$APP_VERSION_CODE" mode="direct" rollout="$ROLLOUT"
  fi
fi
