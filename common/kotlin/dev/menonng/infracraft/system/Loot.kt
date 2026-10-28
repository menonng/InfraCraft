package dev.menonng.infracraft.system

import dev.menonng.infracraft.content.ModItems
import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import net.minecraft.world.level.storage.loot.BuiltInLootTables
import net.minecraft.world.level.storage.loot.LootPool
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem
import net.minecraft.world.level.storage.loot.entries.LootItem

/**
 * 조합법이 없는 아이템의 획득처: 바닐라 구조물 상자에 한 칸(풀)을 덧붙인다.
 * 데이터팩이 바꾼 표가 아니라 바닐라 기본 표에만 붙인다.
 */
object Loot {
    /** 상자 표 → (아이템, 확률 %) */
    private val TABLES: Map<ResourceKey<LootTable>, Pair<Item, Int>> by lazy {
        mapOf(
            BuiltInLootTables.VILLAGE_WEAPONSMITH to (ModItems.DIAMOND_HORSESHOE to 12),
            BuiltInLootTables.VILLAGE_TOOLSMITH to (ModItems.DIAMOND_HORSESHOE to 10),
            BuiltInLootTables.DESERT_PYRAMID to (ModItems.DIAMOND_HORSESHOE to 8),
            BuiltInLootTables.SHIPWRECK_MAP to (ModItems.HARBOR_BLUEPRINT to 35),
            BuiltInLootTables.SHIPWRECK_SUPPLY to (ModItems.HARBOR_BLUEPRINT to 15),
            BuiltInLootTables.UNDERWATER_RUIN_BIG to (ModItems.HARBOR_BLUEPRINT to 20),
            BuiltInLootTables.VILLAGE_FISHER to (ModItems.HARBOR_BLUEPRINT to 20),
        )
    }

    /** 문서·테스트용: 표 이름 → (아이템, 확률 %) */
    fun sources(): List<Triple<String, Item, Int>> = TABLES.map { (k, v) -> Triple(k.toString(), v.first, v.second) }

    fun init() {
        LootTableEvents.MODIFY.register { key, builder, source, _ ->
            if (!source.isBuiltin) return@register
            val (item, percent) = TABLES[key] ?: return@register
            builder.withPool(
                // 굴림 수는 기본값(1번). 26.3 에서 상수 공급자 클래스가 옮겨져 직접 지정하지 않는다.
                LootPool.lootPool()
                    .add(LootItem.lootTableItem(item).setWeight(percent))
                    .add(EmptyLootItem.emptyItem().setWeight(100 - percent))
            )
        }
    }
}
