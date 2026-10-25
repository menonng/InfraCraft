package dev.menonng.infracraft.system

import dev.menonng.infracraft.Config
import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.content.ModItems
import dev.menonng.infracraft.msg
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** 겉날개 금지: 활공은 EntityElytraEvents 로 막고, 손에 들어온 겉날개는 엔더 선로 핵으로 바꾼다. */
object ElytraBan {
    fun sweep(player: ServerPlayer) {
        if (!Config.c.banElytra) return
        var converted = 0
        val chest = player.getItemBySlot(EquipmentSlot.CHEST)
        if (chest.`is`(Items.ELYTRA)) {
            converted += chest.count
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY)
            Inventories.give(player, ItemStack(ModItems.ENDER_RAIL_CORE, chest.count))
        }
        val inv = player.inventory
        for (i in 0 until inv.containerSize) {
            val s = inv.getItem(i)
            if (s.`is`(Items.ELYTRA)) {
                converted += s.count
                inv.setItem(i, ItemStack(ModItems.ENDER_RAIL_CORE, s.count))
            }
        }
        if (converted > 0) Compat.chat(player, msg("elytra.converted", Palette.PURPLE))
    }
}

/** 믹스인이 부르는 진입점(Java 에서 정적 메서드로 보인다) */
object Hooks {
    @JvmStatic
    fun extendedMaxLevel(translationKey: String, original: Int): Int {
        val ext = Config.enchantLevelByKey[translationKey] ?: return original
        return maxOf(original, ext)
    }

    @JvmStatic
    fun netherOneToOne(): Boolean = Config.c.netherOneToOne

    @JvmStatic
    fun minecartMaxSpeed(cart: net.minecraft.world.entity.Entity, original: Double): Double = RailSystem.maxSpeed(cart, original)
}
