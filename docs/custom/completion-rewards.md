# Completionist cape and hooded Slayer helmets

Design brief from the owner's request on **2026-10-07**. Current status: [PROGRESS.md](../../PROGRESS.md).

## Completionist cape

Create a custom OSRS-style cape with particles as the reward for **100% completion** of the defined permanent main-world requirements.

### Unlock requirements

All requirements must be satisfied together:

| Area | Required completion |
|---|---|
| Skills | Base level 99 in every required skill. |
| Experience | **200,000,000 XP in every required skill.** This is the working interpretation of the request; reaching this also covers level 99. |
| Achievements | Every required achievement, including the applicable diary and Combat Achievement categories. |
| Collection Log | Every required unique entry obtained, including pets. Duplicate obtains and item value do not affect completion. |

- Define one explicit, versioned requirement set. Show missing requirements in-game with current/required values and a link to the relevant category.
- Required content must be playable and its rewards obtainable. Missing implementations, placeholder tasks and cache-only items cannot pass a requirement or silently reduce the target.
- Keep the permanent main-world profile separate from seasonal Leagues. Review unavailable/retired entries explicitly when defining the required set.
- Exclude the completionist reward itself from its unlock requirements to prevent a circular Collection Log dependency. Count each required item once even if multiple log categories contain it.
- Read native XP, achievement and Collection Log records through one eligibility service. Recheck when claiming and equipping; use committed gameplay events for updates instead of scanning every player each tick.
- Decide the requalification policy for newly released requirements before launch. Preserve earned items and existing progress when requirements change.

### Appearance and particles

- Give the cape its own silhouette, emblem, colours and particle effect while retaining the server's OSRS visual style. Final stats and perks remain a design decision.
- Verify inventory and worn models, both player body types, cape movement, clipping and item mappings on the accepted **revision-240** server/cache/Nero pair.
- Confirm cape-bound particle support in the paired client first. Server code alone does not establish working particles. Check the native renderer and existing 117 HD setup.
- Render effects client-side from equipment state, with bounded emitters, distance culling and a viewer setting to reduce/disable particles. Avoid particle entities or per-particle server packets.
- Validate a crowded scene with effects on/off and cleanup on unequip, logout and scene changes. Disabling visuals must not alter eligibility or item bonuses.

### Optional completionist monkey

The monkey remains an **undecided cosmetic alternative**, not an additional committed feature. The cape is the primary direction because the owner wants a distinct identity.

If selected, use an original back-slot design and the same completion requirements. A separate follower/pet system is outside this proposal.

Reference: [Alora's completionist cape/monkey guide](https://www.alora.io/forums/topic/81703-completionist-cape-completionist-monkey-information/), reviewed 2026-10-07. It demonstrates milestone rewards and appearance variants; its thresholds, combined cape bonuses, prices and donor variants are not adopted by this brief.

## Hooded Slayer helmets

Add a hooded Slayer helmet and cosmetic variants based on the owner's visual reference. The **bottom-centre helmet** shows the requested black hood around the visible mask. The other tiles provide colour and ornament directions; their exact item names and model IDs are unconfirmed.

<details>
<summary>Helmet visual reference</summary>

![Owner-supplied Helmet Colour reference: nine helmet styles, including a black hood in the bottom-centre tile](assets/hooded-slayer-helm-reference.png)

</details>

- Preserve the recognisable Slayer mask inside the hood. Reference palettes include black/red, green/orange, teal/ivory, light blue/white, olive/silver, red/gold, purple/silver and orange/green.
- Treat variants as cosmetic by default and define the final supported models before implementation.
- Use the Slayer-master **Tasks | Equipment | Rewards | Unlocks | Extend** flow as the menu reference. Previewing a style must not bypass its unlock or crafting ingredients.
- Existing server support, pictured unlocks, required recipes, imbue preservation, atomic conversion/reversion and acceptance checks are defined in the [Slayer audit and crafting contract](slayer.md#variant-creation-contract).

## Implementation starting points

- [Max cape](../../content/other/max-cape) supplies existing equipment/menu patterns. Its [documented perks](../max-cape.md) are not a complete automatic perk bundle for the new cape.
- [PlayerStatMap](../../engine/game/src/main/kotlin/org/rsmod/game/stat/PlayerStatMap.kt) already caps each skill at 200m XP; [CharacterStatPipeline](../../api/account/src/main/kotlin/org/rsmod/api/account/character/stats/CharacterStatPipeline.kt) owns native persistence. Respect fine-XP units; use wide totals when aggregating skills.
- [Collection Log](../../content/interfaces/collection-log) owns obtains and category tracking. Expose a narrow read-only query if needed; its internal helpers are not automatically accessible from another module. Reuse the planned achievement framework.
- [Slayer source audit](slayer.md) identifies the existing menu/recipe paths, correctness gaps and native integrations to reuse for helmets.

First verify assets and a small visual prototype, then implement the requirements and reward flow. Apply [AGENTS.md](../../AGENTS.md), including the upstream/PR comparison, when either roadmap item is selected for development.
