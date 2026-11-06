# Before making the repository public

The repository is private for now. Work through this list before changing its visibility.

## 1. The debug signing key (done in 0.4.1, except the secrets)

**Why it changed:** debug builds up to 0.4.0, including the v0.2.2 testers have, were signed with a
key committed while the repository was private (password `android`). Git history keeps it, so it
counts as public: anyone could sign an APK with it that installs over those builds as an update,
running as the same app with access to its data, including the API keys the app encrypts with the
Android Keystore.

**What 0.4.1 does (key rotation, APK Signature Scheme v3):**

- **A new shared key, never committed.** Builds get it from `HARNESS_DEBUG_KEYSTORE_B64` (or
  `HARNESS_DEBUG_KEYSTORE`, a path). Without it, a build is signed with the machine's own debug key
  and won't install over anyone's copy.
- **`app/signing/debug-lineage`:** the old key's signed word that the new key replaces it. It holds
  certificates and signatures, no private keys. Every debug build carries it, so on Android 9 and
  newer a new build installs over an old-key build and keeps the letters. Once a phone has a rotated
  build, an APK signed with only the old key can't update it: the lineage gives the old key no
  "rollback" right.
- **`app/signing/retired-debug.keystore`:** the old key, kept on purpose. Android 8 only reads the
  older signature scheme, which must come from the lineage's first key, so the old key still signs
  that part. It's public already; nothing relies on it being secret. (Raising the minimum to
  Android 9 would let it go.)
- **`app/debug.keystore` is gone.** CI still fails on a public repository if it comes back.

**Never delete or regenerate the lineage**, and never change the debug key without extending it
(`apksigner rotate --in app/signing/debug-lineage --out <new lineage> --old-signer --ks <current key>
--new-signer --ks <next key>`). Testers' phones need the chain to update without losing their
letters.

**Left for you:**

1. **Keep `fumibako-debug.keystore`** (sent by Claude) somewhere private, outside the repository. It's
   the only copy.
2. **Give it to CI:** on GitHub, go to **Settings → Secrets and variables → Actions → New repository
   secret**. Name it `DEBUG_KEYSTORE_B64` and paste the base64 text
   (`fumibako-debug.keystore.b64.txt`, or make it yourself: `base64 -w0 fumibako-debug.keystore`; on
   macOS, `base64 -i fumibako-debug.keystore`).
3. **Give it to Claude sessions:** in the cloud environment's settings (the environment menu in a
   session's title bar, then **Edit**), add an environment variable `HARNESS_DEBUG_KEYSTORE_B64`
   with the same text. New sessions pick it up. Don't paste the key into a chat.
4. **On your phone:** install 0.4.1 over your current build and check your letters are all there,
   before friends get a rotated build.

**When to flip the switch:** testers' phones can still take an old-key APK until they've installed a
rotated build (v0.5), though someone would have to talk them into installing a fake one. For no gap
at all, send v0.5 before making the repository public.

## 2. License (chosen: GPL-3.0-or-later)

`LICENSE` holds the GPL-3.0 text, and the README's License section says "version 3 or (at your
option) any later version". Two parts keep their own licenses, as the README lists:

- **The madoromi palette** (MPL-2.0): `docs/design/madoromi-palette.json` and
  `app/src/main/kotlin/dev/aiharness/app/ui/theme/MadoromiPalette.kt`.
- **The bundled placeholder fonts** (SIL OFL 1.1): `app/src/main/res/font/`, with the notice in
  `app/src/main/assets/licenses/fonts.txt`.

Still to do: the copyright line in the README's License section, once the studio name is chosen.

## 3. Look over the history

- **API keys:** none have been committed; they live only in the app's Keystore and in environment
  variables. Run GitHub's secret scanning once the repository is public to confirm.
- **Session links:** commit messages carry `Claude-Session:` links. They only open for your
  account, but they'll be visible.
- **Design links:** the docs link design canvases published as private claude.ai artifacts. They
  say so where they appear, since they only open for your account.

## 4. Later, before distributing to other people

Release builds are unsigned today. A release signing key is a different key from the debug one,
and it must never be committed. Set it up the same way as the debug key, through secrets. (If the
app goes on the Play Store, Play App Signing holds the key that phones see.)
