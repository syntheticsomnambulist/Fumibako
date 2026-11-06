# Before making the repository public

The repository is private for now, and a few things in it are only acceptable while it stays that
way. Work through this list before changing its visibility. CI enforces the first item: on a
public repository, the build fails while `app/debug.keystore` is still committed.

## 1. Replace the shared debug signing key (required)

**Why:** `app/debug.keystore` (password `android`) signs every debug build, so builds from CI and
from Claude sessions install over each other. Once the repository is public, anyone could sign an
APK with it that installs over your build as an update. That update would run as the same app,
with access to its data, including the API keys the app encrypts with the Android Keystore.

Deleting the file isn't enough, because git history keeps it. Treat the old key as public and
stop trusting it:

1. **Make a new key** on a computer with a JDK, or ask Claude in a session to make one and send it
   to you as a file:
   ```
   keytool -genkeypair -keystore harness-debug.keystore -storetype PKCS12 -alias androiddebugkey \
     -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=AI Harness Debug"
   ```
   Keep the file somewhere private, outside the repository.
2. **Encode it:** `base64 -w0 harness-debug.keystore` (on macOS: `base64 -i harness-debug.keystore`).
3. **Give it to CI:** on GitHub, go to **Settings → Secrets and variables → Actions → New repository
   secret**. Name it `DEBUG_KEYSTORE_B64` and paste the base64 text.
4. **Give it to Claude sessions:** in the cloud environment's settings (the environment menu in a
   session's title bar, then **Edit**), add an environment variable `HARNESS_DEBUG_KEYSTORE_B64`
   with the same value. New sessions pick it up. Don't paste the key into a chat.
5. If you chose a password other than `android`, also set `HARNESS_DEBUG_KEYSTORE_PASSWORD` in both
   places. (`HARNESS_DEBUG_KEY_ALIAS` exists too, if you changed the alias.)
6. **Remove the old key:** delete `app/debug.keystore`, remove its `!app/debug.keystore` line from
   `.gitignore`, and commit.
7. **On your phone:** uninstall the app (it was signed with the old key) and install a build signed
   with the new one. This wipes app data, which is fine before 1.0.

How the build picks a key is documented in `app/build.gradle.kts` (the `signingConfigs` block).

## 2. Choose a license

There is no `LICENSE` file yet, so the code is "all rights reserved" by default. Two parts already
carry their own licenses, and whatever you pick must sit alongside them:

- **The madoromi palette** (MPL-2.0): `docs/design/madoromi-palette.json` and
  `app/src/main/kotlin/dev/aiharness/app/ui/theme/MadoromiPalette.kt`.
- **The bundled placeholder fonts** (SIL OFL 1.1): `app/src/main/res/font/`, with the notice in
  `app/src/main/assets/licenses/fonts.txt`.

## 3. Look over the history

- **API keys:** none have been committed; they live only in the app's Keystore and in environment
  variables. Run GitHub's secret scanning once the repository is public to confirm.
- **Session links:** commit messages carry `Claude-Session:` links. They only open for your
  account, but they'll be visible.
- **Design links:** the docs link design canvases published as private claude.ai artifacts. They
  say so where they appear, since they only open for your account.

## 4. Later, before distributing to other people

Release builds are unsigned today. A release signing key is a different key from the debug one,
and it must never be committed. Set it up the same way as the debug key, through secrets.
