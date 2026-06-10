package net.ragdot.gestaltresonance.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltMappings;

import java.util.List;
import net.ragdot.gestaltresonance.common.GestaltSounds;
import net.ragdot.gestaltresonance.common.MobSeededData;

/**
 * Right-click a LivingEntity to "seed" it with the soul vessel.
 * Consumes the vessel. The mob's entity type key is stored so the
 * resulting gestalt reflects the mob type.
 */
public class SoulVesselFragileItem extends Item {

    public SoulVesselFragileItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.literal("use on mob to seed").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, net.minecraft.world.InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // Don't seed players
        if (target instanceof Player) {
            return InteractionResult.PASS;
        }

        MobSeededData data = target.getData(GestaltAttachments.MOB_SEEDED_DATA.get());
        if (data.isSeeded()) {
            player.displayClientMessage(Component.literal("This creature is already seeded."), true);
            return InteractionResult.FAIL;
        }

        // Only allow seeding mobs that have a valid gestalt mapping
        ResourceLocation gestaltId = GestaltMappings.gestaltFromHost(target.getType());
        if (gestaltId == null) {
            player.displayClientMessage(Component.literal("This creature cannot hold a gestalt."), true);
            return InteractionResult.FAIL;
        }

        data.setSeeded(true);
        data.setSeedTypeKey(gestaltId.toString());
        target.setData(GestaltAttachments.MOB_SEEDED_DATA.get(), data);

        stack.shrink(1);
        player.setItemInHand(hand, stack);

        if (player instanceof ServerPlayer sp) {
            sp.playNotifySound(GestaltSounds.GESTALT_BIRTH.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        player.displayClientMessage(Component.literal("Soul vessel shattered — creature seeded."), true);
        return InteractionResult.SUCCESS;
    }
}
