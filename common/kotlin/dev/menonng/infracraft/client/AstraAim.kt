package dev.menonng.infracraft.client

import dev.menonng.infracraft.ClientHooks
import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.content.ModItems
import dev.menonng.infracraft.content.id
import dev.menonng.infracraft.msg
import dev.menonng.infracraft.net.Payloads
import dev.menonng.infracraft.system.Astra
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.DustColorTransitionOptions
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * 아스트라 오브리터레이터 조준(클라이언트).
 *  - 왼손에 들고 우클릭: 조준 모드 켜기/끄기. 시선이 닿는 땅(최대 320칸)에 낙하 범위가 별빛 고리로 보인다.
 *  - 조준 중 좌클릭: 그 지점에 발동(서버가 거리·쿨다운을 다시 확인한다).
 *  - 조준 중 M: 지금까지 불러온 지도를 열고, 원하는 곳을 좌클릭해 발동.
 *  - 오른손에 들고 공격: 암흑 별빛 검격을 서버에 요청한다.
 */
object AstraAim {
    private const val RANGE = 320.0
    private const val RADIUS = 12.0
    var aiming = false
        private set
    var target: Vec3? = null
        private set
    private var ticks = 0

    private val CATEGORY: KeyMapping.Category = KeyMapping.Category.register(id("infracraft"))
    val MAP_KEY: KeyMapping = KeyMapping("key.infracraft.astra_map", 77 /* M */, CATEGORY)

    fun register() {
        KeyMappingHelper.registerKeyMapping(MAP_KEY)
        ClientHooks.astraAim = { toggle() }
        ClientTickEvents.END_CLIENT_TICK.register { mc -> tick(mc) }
        ClientPreAttackCallback.EVENT.register { _, player, clicks ->
            if (clicks <= 0) return@register false
            if (aiming) {
                val t = target
                if (t == null) Compat.overlay(player, msg("astra.aim_none", Palette.ORANGE))
                else {
                    ClientPlayNetworking.send(Payloads.AstraStrike(Payloads.AstraStrike.VIEW, t.x, t.y, t.z))
                    stop()
                }
                return@register true
            }
            if (player.mainHandItem.`is`(ModItems.ASTRA_OBLITERATOR)) ClientPlayNetworking.send(Payloads.AstraSlash())
            false
        }
    }

    private fun toggle() {
        val p = Minecraft.getInstance().player ?: return
        if (aiming) {
            stop(); Compat.overlay(p, msg("astra.aim_off", Palette.ORANGE))
        } else {
            aiming = true; ticks = 0
            Compat.overlay(p, msg("astra.aim_on", Palette.PINK))
        }
    }

    fun stop() {
        aiming = false
        target = null
    }

    /** 지도 화면에서 고른 곳에 발동 */
    fun strikeMap(x: Int, z: Int) {
        ClientPlayNetworking.send(Payloads.AstraStrike(Payloads.AstraStrike.MAP, x + 0.5, 0.0, z + 0.5))
        stop()
    }

    private fun tick(mc: Minecraft) {
        val p = mc.player
        if (!aiming) {
            while (MAP_KEY.consumeClick()) { /* 조준 중이 아닐 때는 버린다 */ }
            return
        }
        if (p == null || !p.offhandItem.`is`(ModItems.ASTRA_OBLITERATOR)) {
            stop(); return
        }
        ticks++
        // 화면이 열려 있을 때는 키 매핑이 눌리지 않으므로 따로 확인하지 않는다
        if (MAP_KEY.consumeClick()) {
            mc.setScreenAndShow(AstraMapScreen())
            return
        }
        val level = mc.level ?: return
        val eye = p.eyePosition
        val hit = level.clip(ClipContext(eye, eye.add(p.lookAngle.scale(RANGE)), ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, p))
        target = if (hit.type == HitResult.Type.MISS) null else hit.location
        if (ticks % 30 == 1) Compat.overlay(p, msg("astra.aim_on", Palette.PINK))
        val t = target ?: return
        // 낙하 범위: 바깥 고리(보라→암흑), 안쪽 별빛 고리, 아홉 갈래 바퀴살, 가운데 기둥
        if (ticks % 2 == 0) {
            val spin = ticks * 0.05
            for (k in 0 until 48) {
                val a = k / 48.0 * Math.PI * 2 + spin
                level.addParticle(DustColorTransitionOptions(0x8A4DFF, 0x14002E, 1.2f), t.x + cos(a) * RADIUS, t.y + 0.2, t.z + sin(a) * RADIUS, 0.0, 0.0, 0.0)
            }
            for (k in 0 until 24) {
                val a = k / 24.0 * Math.PI * 2 - spin * 1.6
                level.addParticle(DustParticleOptions(0xF2F7FF, 0.7f), t.x + cos(a) * RADIUS * 0.5, t.y + 0.2, t.z + sin(a) * RADIUS * 0.5, 0.0, 0.0, 0.0)
            }
            for ((i, s) in Astra.STARS.withIndex()) {
                val a = i / 9.0 * Math.PI * 2 + spin * 0.5
                val r = RADIUS * (0.25 + 0.7 * ((ticks / 2 + i) % 10) / 10.0)
                level.addParticle(DustParticleOptions(s.color, 0.8f), t.x + cos(a) * r, t.y + 0.25, t.z + sin(a) * r, 0.0, 0.0, 0.0)
            }
            level.addParticle(ParticleTypes.END_ROD, t.x, t.y + 0.2 + (ticks % 20) * 0.3, t.z, 0.0, 0.02, 0.0)
        }
    }
}
