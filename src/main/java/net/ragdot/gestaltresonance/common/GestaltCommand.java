package net.ragdot.gestaltresonance.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
