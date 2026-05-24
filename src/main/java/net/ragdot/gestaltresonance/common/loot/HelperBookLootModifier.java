package net.ragdot.gestaltresonance.common.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import java.util.List;

public class HelperBookLootModifier extends LootModifier {
    private static final ResourceLocation RUINED_PORTAL =
            ResourceLocation.withDefaultNamespace("chests/ruined_portal");
    private static final ResourceLocation SPAWN_BONUS =
            ResourceLocation.withDefaultNamespace("chests/spawn_bonus_chest");

    public static final MapCodec<HelperBookLootModifier> CODEC = RecordCodecBuilder.mapCodec(
            inst -> LootModifier.codecStart(inst).apply(inst, HelperBookLootModifier::new));

    public HelperBookLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ResourceLocation table = context.getQueriedLootTableId();
        if (RUINED_PORTAL.equals(table) || SPAWN_BONUS.equals(table)) {
            generatedLoot.add(createHelperBook());
        }
        return generatedLoot;
    }

    private static ItemStack createHelperBook() {
        List<Filterable<Component>> pages = List.of(
            page(Component.literal("HOW TO GESTALT:\n\nThis book is a temporary helper until I figure out a better way to teach you the mechanics of the mod.\n\nAll explanations here use the default keybinds, you can change those in the keybind menu.")),
            page(Component.literal("About Gestalten\n\nGestalten are parasitic souls that use you as a host.\nOnce you awakened your Gestalt press G to manifest or dismiss them.\nWhile they're parasitic in nature your survival matters to them.")),
            page(Component.literal("The Gestalt will protect you from danger and help you travers your world.\n\nBut their help comes not for free!\nMany of their actions will drain from your hunger, in fact the very act of having them manifested has a little toll on you.")),
            page(Component.literal("Guarding\n\nNow that you have your Gestalt awakened and manifested you can hold R-Click to let them guard you.\n\nGuarding will reduce incomming damage to a certain amount, how much depends on the Gestalts DUR stat.")),
            page(Component.literal("Crash Out\n\nCrash outs can happen due to a few factors.\nwhen ever your hunger drops below 3,\nbreaking a very long fall or letting ")
                .append(Component.literal("dissonance").withStyle(ChatFormatting.DARK_BLUE))
                .append(Component.literal(" fully fill will all trigger a crash out and put your Gestalt in a CD timer.\n"))),
            page(Component.literal("EXP Siphon\n\nWhen holding G when crouching you start to feed your own EXP points to your Gestalt.\nFirst slowly but the longer you feed, the greedier they become.\n\nBe warned tho, EXP transfered this way will diminish in amount.")),
            page(Component.literal("")
                .append(Component.literal("Resonance").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n\nYour "))
                .append(Component.literal("resonance").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" is displayed by the Blue/Gold bar that appears on the screen when your "))
                .append(Component.literal("resonance").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" is affected.\n\n"))
                .append(Component.literal("Resonance").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" shows you how well in sync you are with your Gestalt."))),
            page(Component.literal("The higher you resonate with them, the stronger they become.\nBut if their ")
                .append(Component.literal("dissonance").withStyle(ChatFormatting.DARK_BLUE))
                .append(Component.literal(" fills they might crash out.\n\nSome abilities might cost "))
                .append(Component.literal("resonance").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" to use, or will drain it before using up Gestalt EXP."))),
            page(Component.literal("Gestalt Throw\n\nJump when crouching to let your Gestalt throw you up.\nIf you travel high or far will depend on your viewing angle when thrown.\n\nYour Gestalts STR stat determines how far they can throw you.")),
            page(Component.literal("Fall Break\n\nGuarding downwards when falling lets your Gestalt take most of the force for you, but be carful, they can only take so much before crashing out.\n\nPro Tip:\nTry landing on someone else ...")),
            page(Component.literal("Ledge Grab\n\nPressing and holding jump midair while looking at a block/ledge will let your Gestalt hold on to it.\n\nRelease jump to get a little upwards boost.")),
            page(Component.literal("Wall Slide\n\nHolding crouch when descending and touching the side of a wall makes you slide along it, breaking your momentum.\n\nJumping when wall sliding will trigger a Gestalt throw.")),
            page(Component.literal("")),
            page(Component.literal("-> craft a brush \n-> use on crying obsidian  \n-> put in furnace \n-> combine resonant dust + amethyst on anvil  \n-> use on soul fire  -> use on creeper\n-> kill\n-> 15 LVL later \n= Amen Break"))
        );

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("HOW TO GESTALT"),
                "Rag",
                0,
                pages,
                true
        ));
        return book;
    }

    private static Filterable<Component> page(Component component) {
        return Filterable.passThrough(component);
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
