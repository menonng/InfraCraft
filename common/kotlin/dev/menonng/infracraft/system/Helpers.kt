package dev.menonng.infracraft.system

import net.minecraft.core.Holder
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.level.Level

object Enchants {
    fun holder(level: Level, key: ResourceKey<Enchantment>): Holder<Enchantment>? =
        level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).get(key).orElse(null)

    /** 장착한 장비 중 가장 높은 레벨 */
    fun level(entity: LivingEntity, key: ResourceKey<Enchantment>): Int {
        val h = holder(entity.level(), key) ?: return 0
        return EnchantmentHelper.getEnchantmentLevel(h, entity)
    }

    fun level(level: Level, stack: ItemStack, key: ResourceKey<Enchantment>): Int {
        val h = holder(level, key) ?: return 0
        return EnchantmentHelper.getItemEnchantmentLevel(h, stack)
    }
}

object Inventories {
    /** 양손을 먼저, 그다음 인벤토리를 본다. */
    fun find(player: Player, predicate: (ItemStack) -> Boolean): ItemStack? {
        if (predicate(player.mainHandItem)) return player.mainHandItem
        if (predicate(player.offhandItem)) return player.offhandItem
        val inv = player.inventory
        for (i in 0 until inv.containerSize) {
            val s = inv.getItem(i)
            if (!s.isEmpty && predicate(s)) return s
        }
        return null
    }

    /** 조건에 맞는 아이템을 count 개 소모한다. 창작 모드는 소모하지 않는다. */
    fun consume(player: Player, count: Int, predicate: (ItemStack) -> Boolean): Boolean {
        if (player.isCreative) return true
        val inv = player.inventory
        var have = 0
        for (i in 0 until inv.containerSize) {
            val s = inv.getItem(i)
            if (!s.isEmpty && predicate(s)) have += s.count
        }
        if (have < count) return false
        var left = count
        for (i in 0 until inv.containerSize) {
            if (left == 0) break
            val s = inv.getItem(i)
            if (!s.isEmpty && predicate(s)) {
                val n = minOf(left, s.count)
                s.shrink(n)
                left -= n
            }
        }
        return true
    }

    fun give(player: Player, stack: ItemStack) {
        if (!player.inventory.add(stack)) player.drop(stack, false)
    }
}
