# Upstream level-up experience review (2026-10-06)

Research only. No code/cache changes from this commit were imported.

Source: [commit 779b81b602e8611029137a3f704ede5082deeaa1](https://github.com/OpenRune/OpenRune-Server/commit/779b81b602e8611029137a3f704ede5082deeaa1),
"Add the OSRS level-up experience: dialogue, message, jingles and fireworks";
[merged PR #286](https://github.com/OpenRune/OpenRune-Server/pull/286).
Twenty changed files, +612/-26. Reviewed the actual merge diff and PR-head checks
(`93e83aafbe835bb2510f56794b93cafbb2c3b1b5`).

## What it adds

- A native `AdvanceStat` queue consumer displays the skill's level-up chatbox, message,
  jingle, fireworks and synth; combat increases use a new `AdvanceCombat(9)` queue.
- `ProtectedAccess.levelUpDisplay` opens native `levelup_display` and waits for Continue,
  using protected access rather than an independent interface controller.
- Skill labels, artwork layers and jingle rules are columns in `stat_components`. Unlock
  levels come from `skill_features`; special rules cover level 50, parity, tens and max level.
- Existing popup and guide-list preferences are respected. Level 99 and max-total fireworks
  are selected separately. Cache tests include 24 skills, including Sailing; this does not
  implement Sailing gameplay.
- `synth.firework = 2396` is an API-owned mapping. Unit/cache tests exercise text, rule
  selection and unlock tuples, rather than the full native queue/dialogue lifecycle.

## Local compatibility and caveats

Our XP code already queues `AdvanceStat`, but `api/stats-plugin` has no level-up consumer.
It has initialization, regeneration and combat-level initialization scripts only. The proposed
addition fills that gap and could use native dialogue artwork alongside our custom skill-guide
style. It should preserve our XP rates, initial stats and guide handlers.

Adoption needs the new engine queue, player/protected UI helpers, stats-plugin classes,
DB-table columns and regenerated revision-240 cache/table sources together. It is not a
single-file drop-in. API-owned mappings are already included in this fork's pack/runtime
scan; keep that integration and verify interface layers, jingles, varbits and synth against
the paired client. Do not import generated files or overwrite existing custom guide data.

The PR description promises a warning/fallback if unlock data cannot load. In the reviewed
merge, `SkillUnlocks.load()` directly calls `SkillFeaturesRow.all()` and the lazy script
property does not catch failures. That fallback is not established by the actual code.
Review it explicitly if porting. Also validate multiple XP increases, interruptions/combat,
logout, Continue handling and disabled settings through native queues, then visually test
normal, 99 and max-total celebrations with the existing client.

## CI evidence and recommendation

PR-head gameval-conflicts and advisory Spotless passed, but
[build & boot failed](https://github.com/OpenRune/OpenRune-Server/actions/runs/37401641414/job/112069923774).
The downloaded job log reports `:api:combat:combat-formulas:integration`: 42 cases,
two PvP Torva matchups failing with Guice `ProvisionException` caused by `MockKException`.
Boot was skipped. The merge commit itself has a separate failed update-workflow check.
The inspected log does not prove that level-up code caused the integration failures, nor
does the PR author's claimed local pass substitute for green CI on the checked head.

Recommendation: useful and appropriate to adapt as a separate level-up feature, after
reviewing cache requirements and lifecycle tests. Avoid merging the whole upstream branch
or importing the [test harness](upstream-pr-282-test-harness.md) as an incidental dependency.
Current task remains guardian progression plus the requested Collection Log news filter.
