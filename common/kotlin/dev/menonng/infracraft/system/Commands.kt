package dev.menonng.infracraft.system

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.fmt1
import dev.menonng.infracraft.lit
import dev.menonng.infracraft.msg
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

/** /infra stations | route <출발> <도착> | network | help */
object InfraCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("infra")
                .executes { help(it) }
                .then(Commands.literal("help").executes { help(it) })
                .then(Commands.literal("stations").executes { stations(it) })
                .then(Commands.literal("network").executes { network(it) })
                .then(Commands.literal("guide").executes { guide(it) })
                .then(
                    Commands.literal("route").then(
                        Commands.argument("from", StringArgumentType.string()).then(
                            Commands.argument("to", StringArgumentType.string()).executes { route(it) },
                        ),
                    ),
                ),
        )
    }

    private fun help(ctx: CommandContext<CommandSourceStack>): Int {
        for (k in listOf("help.stations", "help.route", "help.network", "help.guide")) Compat.sendSuccess(ctx.source, msg(k, Palette.SKY))
        return 1
    }

    private fun guide(ctx: CommandContext<CommandSourceStack>): Int {
        Compat.sendSuccess(ctx.source, msg("guide.title", Palette.SKY))
        for (i in 1..Subway.GUIDE_LINES) Compat.sendSuccess(ctx.source, msg("guide.$i", if (i % 2 == 0) Palette.YELLOW else Palette.PINK))
        return 1
    }

    private fun stations(ctx: CommandContext<CommandSourceStack>): Int {
        val data = Network.data(ctx.source.server)
        if (data.stations.isEmpty()) {
            Compat.sendSuccess(ctx.source, msg("ticket.no_stations", Palette.ORANGE)); return 0
        }
        for (st in data.stations.values.sortedBy { it.name }) {
            Compat.sendSuccess(ctx.source, lit("${st.name}  ", Palette.YELLOW).append(lit("${st.dim}  ${st.x}, ${st.y}, ${st.z}  ", Palette.BLUE))
                .append(msg("station.route_count", Palette.SKY, data.edgesOf(st.key).size.toString())))
        }
        return data.stations.size
    }

    private fun network(ctx: CommandContext<CommandSourceStack>): Int {
        val data = Network.data(ctx.source.server)
        val byMode = data.edges.groupBy { it.mode }
        Compat.sendSuccess(ctx.source, msg("network.summary", Palette.SKY, data.stations.size.toString(), data.edges.size.toString(),
            fmt1(data.edges.sumOf { it.distance } / 1000.0)))
        for ((mode, list) in byMode) {
            Compat.sendSuccess(ctx.source, lit("  ", Palette.SKY).append(msg("mode.$mode", Palette.PINK)).append(lit(" × ${list.size}", Palette.YELLOW)))
        }
        return data.edges.size
    }

    private fun route(ctx: CommandContext<CommandSourceStack>): Int {
        val data = Network.data(ctx.source.server)
        val fromName = StringArgumentType.getString(ctx, "from")
        val toName = StringArgumentType.getString(ctx, "to")
        val a = data.stationByName(fromName)
        val b = data.stationByName(toName)
        if (a == null || b == null) {
            Compat.sendSuccess(ctx.source, msg("ticket.unknown", Palette.RED, if (a == null) fromName else toName)); return 0
        }
        val r = Network.route(data, a.key, b.key)
        if (r == null) {
            Compat.sendSuccess(ctx.source, msg("express.no_route", Palette.RED, a.name, b.name)); return 0
        }
        Compat.sendSuccess(ctx.source, Network.describeRoute(r))
        return r.edges.size
    }
}
