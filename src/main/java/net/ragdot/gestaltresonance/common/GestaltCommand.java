package net.ragdot.gestaltresonance.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;

import java.util.Map;

public final class GestaltCommand {

    private static final Map<String, ResourceLocation> GESTALT_NAMES = Map.of(
            "amen_break",  GestaltIds.AMEN_BREAK,
            "spillways",   GestaltIds.SPILLWAYS,
            "float_play",  GestaltIds.FLOAT_PLAY
    );

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_GESTALT =
            (ctx, builder) -> {
                GESTALT_NAMES.keySet().forEach(builder::suggest);
                return builder.buildFuture();
            };

    private GestaltCommand() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("gestalt")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("give")
                                .then(Commands.argument("gestalt", StringArgumentType.word())
                                        .suggests(SUGGEST_GESTALT)
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "gestalt");
                                            return give(ctx.getSource(), name);
                                        })))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clear(ctx.getSource())))
                        .then(Commands.literal("lvl")
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, PlayerGestaltState.MAX_GESTALT_LEVEL))
                                        .executes(ctx -> setLevel(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "level")))))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("clearlight")
                                        .executes(ctx -> clearOrphanedLights(ctx.getSource()))))
        );
    }

    private static int give(CommandSourceStack src, String gestaltName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ResourceLocation gestaltId = GESTALT_NAMES.get(gestaltName);
        if (gestaltId == null) {
            src.sendFailure(Component.literal("Unknown gestalt: " + gestaltName));
            return 0;
        }

        ServerPlayer player = src.getPlayerOrException();
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        boolean hadGestalt = !state.getGestaltId().equals(PlayerGestaltState.NONE);

        state.setDormant(false);
        state.setPendingGestaltType("");
        state.setConsumedXpPoints(0);
        state.setSummoned(false);
        state.setAwakened(true);
        state.setAwakenedGestaltType(gestaltId.toString());
        state.setGestaltId(gestaltId);
        state.setGestaltLevel(1);
        state.setGestaltXp(0);
        state.setSelectedSkin(PlayerGestaltState.NONE);

        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncToTracking(player);
        GestaltNetworking.syncGestaltXpToPlayer(player);

        String verb = hadGestalt ? "Switched" : "Gave";
        src.sendSuccess(() -> Component.literal(verb + " gestalt: " + gestaltName), false);
        return 1;
    }

    private static int setLevel(CommandSourceStack src, int level) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        if (state.getGestaltId().equals(PlayerGestaltState.NONE)) {
            src.sendFailure(Component.literal("Player has no gestalt."));
            return 0;
        }

        state.setGestaltLevel(level);
        state.setGestaltXp(0);

        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncToTracking(player);
        GestaltNetworking.syncGestaltXpToPlayer(player);

        src.sendSuccess(() -> Component.literal("Set gestalt level to " + level + "."), false);
        return 1;
    }

    private static int clearOrphanedLights(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        int total = 0;
        for (ServerLevel level : server.getAllLevels()) {
            int minY = level.getMinBuildHeight();
            // Collect chunk coords from all players in this level, plus a 10-chunk radius
            java.util.Set<Long> visited = new java.util.HashSet<>();
            for (ServerPlayer p : level.players()) {
                int pcx = p.chunkPosition().x;
                int pcz = p.chunkPosition().z;
                for (int dx = -10; dx <= 10; dx++) {
                    for (int dz = -10; dz <= 10; dz++) {
                        int cx = pcx + dx;
                        int cz = pcz + dz;
                        long key = (long) cx << 32 | (cz & 0xFFFFFFFFL);
                        if (!visited.add(key)) continue;
                        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                        if (chunk == null) continue;
                        LevelChunkSection[] sections = chunk.getSections();
                        int chunkBaseX = chunk.getPos().getMinBlockX();
                        int chunkBaseZ = chunk.getPos().getMinBlockZ();
                        for (int si = 0; si < sections.length; si++) {
                            LevelChunkSection section = sections[si];
                            if (section.hasOnlyAir()) continue;
                            if (!section.getStates().maybeHas(s -> s.is(Blocks.LIGHT))) continue;
                            int baseY = minY + si * 16;
                            for (int x = 0; x < 16; x++) {
                                for (int y = 0; y < 16; y++) {
                                    for (int z = 0; z < 16; z++) {
                                        BlockPos pos = new BlockPos(chunkBaseX + x, baseY + y, chunkBaseZ + z);
                                        if (level.getBlockState(pos).is(Blocks.LIGHT)) {
                                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                                            total++;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        final int count = total;
        src.sendSuccess(() -> Component.literal("Removed " + count + " orphaned light block(s) across all loaded levels."), false);
        return count;
    }

    private static int clear(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        if (state.getGestaltId().equals(PlayerGestaltState.NONE)) {
            src.sendFailure(Component.literal("Player has no gestalt to clear."));
            return 0;
        }

        state.setAwakened(false);
        state.setDormant(false);
        state.setAwakenedGestaltType("");
        state.setPendingGestaltType("");
        state.setConsumedXpPoints(0);
        state.setSummoned(false);
        state.setGestaltId(PlayerGestaltState.NONE);
        state.setGestaltLevel(1);
        state.setGestaltXp(0);
        state.setSelectedSkin(PlayerGestaltState.NONE);

        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncToTracking(player);

        src.sendSuccess(() -> Component.literal("Cleared gestalt."), false);
        return 1;
    }
}
