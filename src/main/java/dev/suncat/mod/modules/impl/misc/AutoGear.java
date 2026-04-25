package dev.suncat.mod.modules.impl.misc;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Formatting;
import dev.suncat.mod.commands.impl.KitCommand;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.SliderSetting;

import java.util.ArrayList;
import java.util.HashMap;

public class AutoGear extends Module {
    public AutoGear() {
        super("AutoGear", Category.Misc);
        this.setChinese("自动装备");
    }

    public final SliderSetting actionDelay = new SliderSetting("ActionDelay", 50, 0, 500, 1);
    public final SliderSetting clicksPerAction = new SliderSetting("Click/Action", 1, 1, 108, 1);

    private HashMap<Integer, String> expectedInv = new HashMap<>();
    private int delay = 0;

    @Override
    public void onEnable() {
        setup();
    }

    public void setup() {
        String selectedKit = KitCommand.getSelectedKit();

        if (selectedKit.isEmpty()) {
            sendMessage("No kit is selected! Use the kit command");
            disable();
            return;
        }

        sendMessage("Selected kit -> " + Formatting.AQUA + selectedKit);

        String kitItems = KitCommand.getKitItems(selectedKit);

        if (kitItems.isEmpty() || kitItems.split(" ").length != 36) {
            sendMessage("There was an error in the kit configuration! Create the kit again");
            disable();
            return;
        }

        String[] items = kitItems.split(" ");
        expectedInv = new HashMap<>();

        for (int i = 0; i < 36; i++)
            if (!items[i].equals("block.minecraft.air"))
                expectedInv.put(i, items[i]);
    }

    public void onUpdate() {
        if (delay > 0) {
            delay--;
            return;
        }

        if (expectedInv.isEmpty()) {
            setup();
            return;
        }

        int actions = 0;

        ScreenHandler handler = mc.player.currentScreenHandler;

        if (handler.slots.size() != 63 && handler.slots.size() != 90)
            return;

        ArrayList<Integer> clickSequence = buildClickSequence(handler);
        for (int s : clickSequence) {
            clickSlot(s);
            actions++;
            if (actions >= clicksPerAction.getValue())
                break;
        }
        delay = actionDelay.getValueInt();
    }

    private int searchInContainer(String name, boolean lower, ScreenHandler handler) {
        ItemStack cursorStack = handler.getCursorStack();

        if ((cursorStack.getItem() instanceof PotionItem ?
                cursorStack.getItem().getTranslationKey() + cursorStack.getItem().getComponents().get(DataComponentTypes.POTION_CONTENTS).getColor()
                : cursorStack.getItem().getTranslationKey()).equals(name))
            return -2;

        for (int i = 0; i < (lower ? 26 : 53); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if ((stack.getItem() instanceof PotionItem ?
                    stack.getItem().getTranslationKey() + stack.getItem().getComponents().get(DataComponentTypes.POTION_CONTENTS).getColor()
                    : stack.getItem().getTranslationKey()).equals(name))
                return i;
        }
        return -1;
    }

    private ArrayList<Integer> buildClickSequence(ScreenHandler handler) {
        ArrayList<Integer> clicks = new ArrayList<>();
        for (int s : expectedInv.keySet()) {
            int lower = s < 9 ? s + 54 : s + 18;
            int upper = s < 9 ? s + 81 : s + 45;

            ItemStack itemInslot = handler.slots.get((handler.slots.size() == 63 ? lower : upper)).getStack();

            if((itemInslot.getItem() instanceof PotionItem ?
                    itemInslot.getItem().getTranslationKey() + itemInslot.getItem().getComponents().get(DataComponentTypes.POTION_CONTENTS).getColor()
                    : itemInslot.getItem().getTranslationKey()).equals(expectedInv.get(s)))
                continue;

            int slot = searchInContainer(expectedInv.get(s), handler.slots.size() == 63, handler);

            if (slot == -2) {
                clicks.add(handler.slots.size() == 63 ? lower : upper);
            } else if (slot != -1) {
                clicks.add(slot);
                clicks.add(handler.slots.size() == 63 ? lower : upper);
                clicks.add(slot);
            }
        }
        return clicks;
    }

    private void clickSlot(int slot) {
        if (mc.player != null && mc.player.currentScreenHandler != null) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
        }
    }
}
