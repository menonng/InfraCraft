package dev.menonng.infracraft.system

import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.compat.BoatEntity
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.content.Cargo
import dev.menonng.infracraft.content.ModComponents
import dev.menonng.infracraft.content.ModItems
import dev.menonng.infracraft.content.ModStats
import dev.menonng.infracraft.lit
import dev.menonng.infracraft.msg
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.FluidTags
import net.minecraft.world.Container
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import kotlin.math.sqrt

/**
 * 항구 교역. 순간이동 없이 배를 타고 실제로 다른 항구까지 가야 성립한다.
 *  - 교역소(항구 16칸 안)에 그 항구의 특산품을 들고 사용하면 화물 상자가 된다.
 *  - 화물 상자를 지닌 채(또는 상자 보트에 싣고) 다른 지역 항구에 보트로 닿으면 자동으로 정산된다.
 */
object Trade {
    private const val POST_RANGE = 16

    /** 역 블록 바로 위가 물이면 항구 */
    fun isHarbor(level: ServerLevel, st: StationRec): Boolean =
        st.source == StationRec.INFRACRAFT && level.getFluidState(st.pos.above()).`is`(FluidTags.WATER)

    fun regionAt(level: ServerLevel, pos: BlockPos): TradeRules.Region {
        val key = level.getBiome(pos).unwrapKey().orElse(null) ?: return TradeRules.Region.TEMPERATE
        return TradeRules.region(Compat.keyId(key).substringAfter(':'))
    }

    private fun item(path: String): Item = BuiltInRegistries.ITEM.getValue(Compat.id("minecraft", path))

    private fun itemPath(stack: ItemStack): String = BuiltInRegistries.ITEM.getKey(stack.item).path

    private fun regionName(r: TradeRules.Region) = msg("region.${r.id}", Palette.PINK)

    private fun specialtyList(r: TradeRules.Region): Component {
        val c = lit("", Palette.YELLOW)
        r.specialties.forEachIndexed { i, s ->
            if (i > 0) c.append(lit(", ", Palette.YELLOW))
            c.append(ItemStack(item(s)).hoverName)
        }
        return c
    }

    private fun nearestHarbor(level: ServerLevel, pos: BlockPos): StationRec? {
        val dim = Compat.dimensionId(level)
        val r2 = (POST_RANGE * POST_RANGE).toDouble()
        return Network.data(level.server).stations.values
            .filter { it.dim == dim && it.pos.distSqr(pos) <= r2 && isHarbor(level, it) }
            .minByOrNull { it.pos.distSqr(pos) }
    }

    // ───────────── 선적 ─────────────

    fun useTradingPost(level: ServerLevel, pos: BlockPos, player: ServerPlayer, stack: ItemStack) {
        val harbor = nearestHarbor(level, pos)
        if (harbor == null) {
            Compat.chat(player, msg("trade.no_harbor", Palette.ORANGE, POST_RANGE.toString())); return
        }
        val region = regionAt(level, harbor.pos)
        if (stack.isEmpty || stack.`is`(ModItems.CARGO_CRATE)) {
            Compat.chat(player, msg("trade.post_info", Palette.SKY, harbor.name).append(lit(" · ", Palette.SKY)).append(regionName(region)))
            Compat.chat(player, msg("trade.specialties", Palette.SKY).append(specialtyList(region)))
            Compat.chat(player, msg("trade.how", Palette.PINK, TradeRules.MIN_DISTANCE.toString()))
            return
        }
        val path = itemPath(stack)
        if (BuiltInRegistries.ITEM.getKey(stack.item).namespace != "minecraft" || path !in region.specialties) {
            Compat.chat(player, msg("trade.not_specialty", Palette.ORANGE, harbor.name).append(specialtyList(region))); return
        }
        val n = stack.count.coerceAtMost(64)
        val cargo = Cargo(harbor.key, harbor.name, harbor.dim, harbor.x, harbor.z, region.id, path, n)
        stack.shrink(n)
        val crate = ItemStack(ModItems.CARGO_CRATE)
        crate.set(ModComponents.CARGO, cargo)
        crate.set(DataComponents.LORE, ItemLore(listOf(
            msg("trade.lore_from", Palette.SKY, harbor.name).append(lit(" · ", Palette.SKY)).append(regionName(region)),
            lit("", Palette.YELLOW).append(ItemStack(item(path)).hoverName).append(lit(" × $n", Palette.YELLOW)),
        )))
        if (!player.inventory.add(crate)) Compat.dropItem(player, crate)
        Compat.chat(player, msg("trade.loaded", Palette.GREEN, n.toString(), harbor.name))
    }

    // ───────────── 하역·정산 ─────────────

    /** 보트가 항구 역에 닿았을 때(Water.tickBoat) */
    fun onHarborArrival(level: ServerLevel, boat: BoatEntity, st: StationRec) {
        if (!isHarbor(level, st)) return
        val region = regionAt(level, st.pos)
        for (p in boat.passengers) if (p is ServerPlayer) {
            val inv = p.inventory
            for (i in 0 until inv.containerSize) {
                val s = inv.getItem(i)
                if (!s.`is`(ModItems.CARGO_CRATE)) continue
                val rewards = settle(level, st, region, s, p) ?: continue
                inv.setItem(i, ItemStack.EMPTY)
                for (r in rewards) if (!inv.add(r)) Compat.dropItem(p, r)
            }
        }
        // 상자 보트에 실은 화물: 대가도 상자에 넣고, 넘치면 보트 옆에 떨어뜨린다
        val chest = boat as? Container ?: return
        val pilot = boat.passengers.firstOrNull { it is ServerPlayer } as? ServerPlayer
        for (i in 0 until chest.containerSize) {
            val s = chest.getItem(i)
            if (!s.`is`(ModItems.CARGO_CRATE)) continue
            val rewards = settle(level, st, region, s, pilot) ?: continue
            chest.setItem(i, ItemStack.EMPTY)
            for (r in rewards) {
                var rest = r
                for (j in 0 until chest.containerSize) {
                    if (rest.isEmpty) break
                    if (chest.getItem(j).isEmpty) { chest.setItem(j, rest); rest = ItemStack.EMPTY }
                }
                if (!rest.isEmpty) boat.spawnAtLocation(level, rest)
            }
        }
    }

    /** 화물 하나를 정산한다. 팔 수 없으면 안내만 하고 null. */
    private fun settle(level: ServerLevel, st: StationRec, region: TradeRules.Region, crate: ItemStack, player: ServerPlayer?): List<ItemStack>? {
        val cargo = crate.get(ModComponents.CARGO) ?: return null
        val origin = TradeRules.Region.byId(cargo.region) ?: return null
        val dx = (st.x - cargo.x).toDouble()
        val dz = (st.z - cargo.z).toDouble()
        val distance = sqrt(dx * dx + dz * dz)
        val why = TradeRules.refusal(origin, region, distance, cargo.dim == st.dim, cargo.harbor == st.key)
        if (why != null) {
            // 출발 항구로 돌아온 것은 조용히 넘긴다
            if (why != "same_harbor" && player != null) Compat.overlay(player, msg("trade.refuse.$why", Palette.ORANGE, st.name))
            return null
        }
        val rng = level.random
        val total = TradeRules.rewardCount(cargo.count, distance)
        val out = ArrayList<ItemStack>()
        for ((path, n) in TradeRules.split(total, region.specialties) { rng.nextInt(it) }) {
            var left = n
            val it = item(path)
            val max = ItemStack(it).maxStackSize
            while (left > 0) { val c = minOf(left, max); out.add(ItemStack(it, c)); left -= c }
        }
        var exotic: ItemStack? = null
        if (rng.nextDouble() < TradeRules.exoticChance(cargo.count, distance)) {
            val pool = TradeRules.exoticPool(distance)
            var roll = rng.nextInt(pool.sumOf { it.second })
            for ((path, w) in pool) {
                if (roll < w) { exotic = ItemStack(item(path)); break }
                roll -= w
            }
        }
        exotic?.let { out.add(it) }
        if (player != null) {
            player.awardStat(ModStats.CARGO_DELIVERED, 1)
            player.awardStat(ModStats.TRADE_ONE_CM, (distance * 100).toInt())
            Compat.chat(player, msg("trade.delivered", Palette.GREEN, cargo.harborName, st.name, distance.toInt().toString(), total.toString()))
            exotic?.let { Compat.chat(player, msg("trade.exotic", Palette.PURPLE).append(it.hoverName)) }
        }
        return out
    }
}
