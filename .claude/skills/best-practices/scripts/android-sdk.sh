#!/usr/bin/env bash
# Installs the Android SDK pieces these repositories build with (compileSdk 37) into a fresh cloud
# container, and points a repository at it through local.properties, which is gitignored. Takes
# about 20 seconds. Safe to run again.
#
#   bash .claude/skills/best-practices/scripts/android-sdk.sh [repository folder]
#
# local.properties instead of ANDROID_HOME in ~/.bashrc: the safety classifier refuses edits to
# shell profiles as persistence, and an exported variable doesn't outlive the shell anyway.
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
REPO="${1:-$PWD}"
PACKAGES=("platforms;android-37.0" "build-tools;36.1.0" "platform-tools")
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
  index="https://dl.google.com/android/repository/repository2-3.xml"
  version=$(curl -sSf "$index" | grep -o 'commandlinetools-linux-[0-9]*_latest\.zip' \
    | grep -o '[0-9][0-9]*' | sort -n | tail -1)
  tmp=$(mktemp -d)
  curl -sSfLo "$tmp/tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${version}_latest.zip"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  mkdir -p "$SDK/cmdline-tools"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -r "$tmp"
fi

# Newer command-line tools say licenses no longer need accepting; older ones still ask.
yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses > /dev/null 2>&1 || true
"$SDKMANAGER" --sdk_root="$SDK" "${PACKAGES[@]}" > /dev/null 2>&1

props="$REPO/local.properties"
if ! grep -q '^sdk\.dir=' "$props" 2>/dev/null; then
  echo "sdk.dir=$SDK" >> "$props"
fi

echo "Android SDK ready in $SDK: $(ls "$SDK/platforms" | tr '\n' ' ')"
echo "$props: $(grep '^sdk\.dir=' "$props")"
