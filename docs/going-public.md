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
  "rollback" right. Every other right stays (they're apksigner's defaults): the app declares a
  signature permission (AndroidX adds one), and without the "permission" right Android refuses the
  update with "App not installed", as the first 0.4.1 build did.
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
   before friends get a rotated build. (Done: it installed over 0.4.0 and kept them.)

**When to flip the switch:** testers' phones can still take an old-key APK until they've installed a
rotated build (v0.5), though someone would have to talk them into installing a fake one. For no gap
at all, send v0.5 before making the repository public. *(2026-11-10: the 0.5.0 debug APK is built
from `main` at `4a29f82` and checked: signed with the shared key, the retired key for Android 8,
and the lineage between them. Not yet sent to friends.)*

## 2. License (chosen: GPL-3.0-or-later)

`LICENSE` holds the GPL-3.0 text, and the README's License section says "version 3 or (at your
option) any later version". Two parts keep their own licenses, as the README lists:

- **The madoromi palette** (MPL-2.0): `docs/design/madoromi-palette.json` and
  `ui/src/commonMain/kotlin/dev/aiharness/app/ui/theme/MadoromiPalette.kt`.
- **The bundled fonts** (SIL OFL 1.1): Zen Old Mincho, Literata, Zen Kaku Gothic New, Yomogi, Yuji
  Syuku and Old Standard TT, in `ui/src/androidMain/res/font/`, with their notices in
  `ui/src/commonMain/composeResources/files/licenses/fonts.txt`.

Two more carry their own notices, both compatible with the GPL: `ui/src/androidMain/res/values/font_certs.xml`
(the Android Open Source Project, Apache 2.0, for Play services' font provider), and the libraries
both apps use, listed from About by `buildSrc/.../LibraryNotices.kt`.

Still to do: the copyright line in the README's License section, once the studio name is chosen.

## 3. Look over the history (audited 2026-11-10)

Every blob reachable from every branch (5,950) and every commit message was scanned.

- **API keys and secrets:** none. No key-shaped strings (Anthropic, OpenAI, Google, GitHub,
  NovelAI, AWS, Slack), no private-key blocks, no encoded keystores. The only key file ever
  committed is the retired debug key, public on purpose (section 1). Run GitHub's secret scanning
  once the repository is public to confirm.
- **Who wrote it:** all 327 commits are authored and committed as Claude
  (noreply@anthropic.com); no personal email or name is in the commit metadata or the files.
- **Session links:** 303 commit messages carry `Claude-Session:` links, and the docs name two
  session ids. They only open for your account, but they'll be visible.
- **Design links:** today's docs link six private claude.ai artifacts (the design canvases), and
  older versions in history link more. They say so where they appear, since they only open for
  your account.
- **Your other project's name:** an early version of the best-practices skill (in history only,
  not in today's files) names the `art-program` repository and a little about its build.
- **A place:** `ThemeClockTest` checks sunrise at Boston City Hall's coordinates (42.3601,
  -71.0589), a landmark rather than an address. Swap in another city if you'd rather not hint at
  yours.
- **Size:** 4.2 MB packed; nothing large.

Rewriting history to drop any of these would mean force-pushing every branch, which this checklist
doesn't recommend: none of it is a secret.

## 4. Right after the switch

- **Settings → Code security:** turn on secret scanning, push protection and Dependabot alerts.
- **Settings → Branches:** consider protecting `main` from force-pushes and deletion.
- **Delete the merged session branches** (every branch but `main`, the design branch
  `ccr-09dbf6b8-2c3jno`, and the ones sessions are still working on).
- **Actions:** GitHub's standard hosted runners are free for public repositories, so the
  "minutes are limited while the repository is private" reason for building on request only goes
  away. Decide whether pushes should run the tests again, then update the wording in
  `.github/workflows/build.yml`, the README's *Builds* and CLAUDE.md's *Checks*, which all say the
  repository is private.
- **CLAUDE.md** opens with "The repository is private for now": change it to say it's public.

## 5. Later, before distributing to other people

Release builds are unsigned today. A release signing key is a different key from the debug one,
and it must never be committed. Set it up the same way as the debug key, through secrets. (If the
app goes on the Play Store, Play App Signing holds the key that phones see.)
