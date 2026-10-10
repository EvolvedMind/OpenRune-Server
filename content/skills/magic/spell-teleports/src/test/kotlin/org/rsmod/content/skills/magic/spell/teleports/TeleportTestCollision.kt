package org.rsmod.content.skills.magic.spell.teleports

import org.rsmod.routefinder.collision.CollisionFlagMap

// Cache-backed suites are serialized by their ServerCacheManager resource lock.
internal object TeleportTestCollision { val map = CollisionFlagMap() }
