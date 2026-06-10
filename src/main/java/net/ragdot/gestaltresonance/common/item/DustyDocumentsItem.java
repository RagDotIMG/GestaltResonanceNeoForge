package net.ragdot.gestaltresonance.common.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public class DustyDocumentsItem extends Item {

    @FunctionalInterface
    public interface ScreenOpener {
        void open(WrittenBookContent content);
    }

    public static ScreenOpener openScreenCallback;

    private final WrittenBookContent defaultContent;

    public DustyDocumentsItem(WrittenBookContent defaultContent, Properties properties) {
        super(properties);
        this.defaultContent = defaultContent;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide && openScreenCallback != null) {
            openScreenCallback.open(defaultContent);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
        return 300;
    }
}
