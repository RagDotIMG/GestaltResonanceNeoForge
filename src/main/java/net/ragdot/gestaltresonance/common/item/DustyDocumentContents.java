package net.ragdot.gestaltresonance.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.List;

/**
 * Static content factories for each Dusty Documents item.
 * Add a new method here for each new gestalt document.
 * Pages with empty text ("") render as ripped/missing pages.
 */
public class DustyDocumentContents {

    public static WrittenBookContent amenBreakFile117() {
        return new WrittenBookContent(
                Filterable.passThrough("FILE 117 - AMEN BREAK -"),
                "Unknown",
                0,
                List.of(
                        page(Component.literal("General Profile\n\nOrigin:\nCrepper\n\nProfile:\nSTR, 3\nSPE, 3\nDUR, 3\nRNG, 2\nRES, 1\n\nNotes:\nMediocre physical attributes, but makes up for it in ability power.\nStrong against single targets.\nProvides medium area control and evasive options.\n\nFirst encountered:\nJungle")),
                        page(Component.literal("The following pages contain records of the entities performance in lab testing and observations\nabout the function of it's abilities.\n\nIf any one ability has two listed costs, it means the ability can be activated a second time during it's runtime for an additional effect.\n\nSome abilities caused the user to become out of sync with their gestalt, either instead of or if no sufficient EXP was provided. \nThis will be shown as RES costs.")),
                        page(Component.literal("- Jungle Bomber -\n\nCOST - 1 EXP\nCOOLDOWN - 0.5 SEC\n\nThe Gestalt is able to grow plant buds on its forearms and launch said buds as projectiles.\nThese buds explode on contact with living beings.\n\nOn contact with any solid surfaces, the buds sprouted into a variety of different plants depending on the surface.\n\n\nsaftey notes:\ndo not let the buds come in contact with high temperature materials!")),
                        page(Component.literal("- Twisted Firestarter -\n\nCOST - 40 EXP\nCOOLDOWN - 3 SEC\n\nThe Gestalt seems to be able to implant it's arm buds into solid enough materials directly.\n\nIf a cohesive enough material is chosen the block will rupture violently under the explosive pressure of the bud.\n\n\nnotes:\nThe test subject reported less exhaustion using this ability if the Gestalt was provided with ingnitable substances like gunpower, coal or flint.")),
                        page(Component.literal("- Queen Killer -\n\nCOST - 10 EXP\nCOOLDOWN - 3 SEC\n\nThe Gestalt launches a heavy strike dealing high single target damage and imbuing the struck target with a bud.\n\nThe test subject reported they where able to detonate the imbued bud manually at a chosen time.")),
                        page(Component.literal("- P3T -\n\nCOST - 15 EXP / 5 EXP\nCOOLDOWN - 30 SEC\n\nThe Gestalt produces an illusion of the user.\n\nThe illusion was very effective against simple minded entities but failed to convince more intelligent foes.\n\nAfter a short time the illusion faded away and left behind an explosion.\n\n\nnotes:\nthe illusion had somewhat of their own mind and seemed to be weirdly attracted by the sprouts of the bud.")),
                        page(Component.literal("- What bombs at Midnight -\n\nCOST - 20 EXP\nCOOLDOWN - NA\n\nThe gestalt places down a blossom at the ground.\n\nThe placed blossom acts like a mine and will be activated by the next entity coming in contact with it.\nThe affected entity leaves behind images of it self during a short amount of time.\n\nAfter that time the affected entity will be dragged back along the path it traversed, triggering an explosion at every image it left.")),
                        page(Component.literal("- Rave Step -\n\nCOST - 10 EXP/RES\nCOOLDOWN - 60 SEC\n\nThe test subject reported that this ability did not have an immediate effect but rather was something they would prepare in advance.\n\nThe effect would be triggered if the user was subjected to any intentional directed harm towards them, upon which they would automatically seem to phase out of reality partially, avoiding any harm for a short moment.\n\nThe test subject noted that once prepared the ability would fire even if the Gestalt was not present.")),
                        page(Component.literal("- Banned Forever -\n\nCOST - 30 EXP/RES\nCOOLDOWN - 2 SEC\n\nThe Gestalt would place another blossom at a surface effecting a small area where the plant would take root.\n\nThe affected are would be partially taking out of reality, allowing entities to phase right through.\n\n\nnotes:\nThe test subjected described a clear dissociation when using these new reality altering abilities and requested testing to be halted.\n\nThe request was denied.")),
                        page(Component.literal("- Futurama -\n\nCOST - 25 RES\nCOOLDOWN - 100 SEC\n\nTest results were unclear on this one.\n\nThe test subject claimed to be able to foresee near future events and even to skip time forward to this predicted future.\nThe only observable effect the ability had was to dislocate nearby entities occasionally.\n\nnotes: \nThe test subject was clearly delusional at this point.\nThe Gestalts reality altering abilities seem to have taken a great toll on the subjects mental well being.")),
                        page(Component.literal("- Court of the Purple Prince -\n\nCOST - 50 RES\nCOOLDOWN - 120 SEC\n\nThe test subject claimed an out of body experience and the ability to avoid foreseen future events.\n\nOutside observable accounts recorded that the gestalt produced a illusion of the user while phasing the user and itself mostly out of our reality.\n\n\nnotes:\nSince the mental state of the test subject has degraded to far, the subject will be taken out of commission and the testing of the Gestalt will be halted for the time."))
                ),
                true
        );
    }

    public static WrittenBookContent theGestaltFile42069() {
        return new WrittenBookContent(
                Filterable.passThrough("FILE 42069 - The Gestalt"),
                "Unknown",
                0,
                List.of(
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
                ),
                true
        );
    }

    private static Filterable<Component> page(Component text) {
        return Filterable.passThrough(text);
    }
}
