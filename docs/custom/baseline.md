# Runtime and recovery checkpoints

Recorded evidence through **2026-10-06**. Runtime revision remains **240**.
Current work is tracked in [PROGRESS.md](../../PROGRESS.md).

## Protected Doom / HUD package

| Part | Recorded reference |
|---|---|
| Accepted server source | `f40f4d951fb7e08df715952879a15314a4f85639` |
| Paired Nero Studio | `b29d93cabe7b6d064430bba688f3a9e7903267fb` |
| Package | `outputs/doom-update-20261006/INSTALLEREN.cmd` |
| Server merge | PR #23, `c2ccd5c762f98fa24568b66458edad814be1c1f0` |
| Nero merge | PR #11, `85af63cedb2067b3b82c63ed86b0e55048c0399d` |
| Recorded validation | 326 selected server tests, 89 Nero tests, cache/JAR/plugin contracts and isolated startup |

The user accepted this paired package and authorized merge on 2026-10-06.
The package manifest records exact JAR/cache/plugin hashes and the software rollback checkpoint.
This remains a recovery point after newer changes enter main.

## Later approved source: four extensions and clue guardians

| Part | Recorded reference |
|---|---|
| Reviewed PR #25 head | `4211eca06` |
| PR #24 merge | `b289741bc` |
| PR #25 merge into parent | `e6b1702fa` |
| Approved code integrated into main | `474296f2b`, identical tree to the validated candidate |
| Combined package | `outputs/clue-guardian-fix-20261006/INSTALLEREN.cmd` |
| Software rollback | `outputs/checkpoint-20261006-voor-clue-guardian-fix` |
| Runtime JAR SHA-256 | `0de5d5bdfa79d8890d6b3775dfd4d2f3466b80938b7c2f67a6cd3eefb03bc84a` |
| Recorded validation | 134 selected tests, runtime build and `gameplay-smoke-abca7326` isolated startup |

The user approved this code integration. Advisory formatting checks still reported
violations. The existing database-close ordering warning remains separate; process
cleanup does not certify save/drain ordering. See [extensions](small-extensions.md)
and [guardian fix](clue-guardians.md).

**Approved source, packaged software and installed software are separate records.**
Before this combined package was prepared, the installed `collection-chat-news-20261006`
baseline matched 821 file hashes. That historical check does not establish what is
installed now. The merge performed no live installation. Verify the installed manifest
and current hashes before a future installation; preserve player data and paired artifacts.

## Earlier recovery references

| Checkpoint | Source | Package / recovery reference |
|---|---|---|
| Guides and max cape | `444ead71bec435e3eba0c378aa6718f7a7ac57d4` | `outputs/guides-cape-update-20261003`; rollback `outputs/checkpoint-20261003-voor-guides-cape-update`; tag `server-r240-accepted-20261003` |
| Weapon / final FX | `6168204ee3992a3e00f6906e34200d4cff3e95f4` | `outputs/special-fx-update-20261004`; rollback `outputs/checkpoint-20261004-voor-special-fx-update`; PR #15 |
| Initial Araxxor | `b87051471a7b023d3a57f066adf4434563cde1bd` | `outputs/araxxor-test-20261004`; tag `server-araxxor-accepted-20261004` |
| Completed Araxxor | `f7c349c0edff7b4b9c9194b4c2d78fa80a777760` | `outputs/araxxor-completion-20261004`; tag `server-araxxor-complete-20261004`; PR #16 |
| Kraken | `dab74feae` | `outputs/kraken-fix-20261004`; PR #19 |
| Corporeal Beast | `1e4f7f7e4` | `outputs/corporeal-beast-update-20261004`; PR #20 |

The guides/weapon/initial Araxxor records pair with Nero
`0efcb039c539a469a1dab2c4c654c61ecf41aa51`. The guides checkpoint also records JAR
SHA-256 `41cb04e279533408aea110291d7ff10157f01be9714f283edd7a2cf2a5039c82`
and cache/mapping fingerprint `61657fde9e8f2372b610772b4f04e6c936923dd0cc6cb7747b04e3b6276b8a5d`.
Each package's own manifest is the recovery authority; paths refer to the original local workspace.

Historical branches are retained under `archive/20261003/<old-branch-name>`;
the original ref manifest and Git bundle were saved to `outputs/repository-cleanup-20261003`.
Review an archive in isolation. The briefly committed lifecycle change `d7d5a5e5b`
was fully reverted by `2bd931b55` and was never installed.
