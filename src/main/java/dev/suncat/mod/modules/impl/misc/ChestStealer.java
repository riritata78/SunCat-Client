package dev.suncat.mod.modules.impl.misc;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.api.utils.math.Timer;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.EnumSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Random;

public class ChestStealer extends Module {
    public ChestStealer() {
        super("ChestStealer", Category.Misc);
        this.setChinese("自动偷箱子");
    }

    private final BooleanSetting onlyItems = this.add(new BooleanSetting("OnlyItems", false));
    private final SliderSetting delay = this.add(new SliderSetting("Delay", 100, 0, 1000, 1));
    private final BooleanSetting random = this.add(new BooleanSetting("Random", false));
    private final BooleanSetting close = this.add(new BooleanSetting("Close", true));
    private final BooleanSetting autoMyst = this.add(new BooleanSetting("AutoMyst", false));
    private final EnumSetting<Sort> sort = this.add(new EnumSetting<>("Sort", Sort.None));

    private final Timer autoMystDelay = new Timer();
    private final Timer timer = new Timer();
    private final Random rnd = new Random();

    private ArrayList<String> itemFilter = new ArrayList<>();

    @Override
    public void onEnable() {
        itemFilter.clear();
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        // AutoMyst functionality - auto open ender chests
        if (autoMyst.getValue() && mc.currentScreen == null && autoMystDelay.passedMs(3000)) {
            if (mc.world == null || mc.player == null) {
                return;
            }
            
            // Search for ender chests nearby
            BlockPos playerPos = mc.player.getBlockPos();
            for (int x = -5; x <= 5; x++) {
                for (int y = -5; y <= 5; y++) {
                    for (int z = -5; z <= 5; z++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        if (mc.world.getBlockState(pos).getBlock() == Blocks.ENDER_CHEST) {
                            if (mc.player.squaredDistanceTo(pos.toCenterPos()) > 39) {
                                continue;
                            }
                            
                            Vec3d hitVec = new Vec3d(
                                pos.getX() + 0.5 + (rnd.nextDouble() - 0.5) * 0.8,
                                pos.getY() + 0.375,
                                pos.getZ() + 0.5 + (rnd.nextDouble() - 0.5) * 0.8
                            );
                            
                            mc.interactionManager.interactBlock(
                                mc.player,
                                Hand.MAIN_HAND,
                                new BlockHitResult(hitVec, Direction.UP, pos, false)
                            );
                            mc.player.swingHand(Hand.MAIN_HAND);
                            return;
                        }
                    }
                }
            }
        }

        // Chest stealing functionality
        if (mc.currentScreen instanceof GenericContainerScreen) {
            GenericContainerScreenHandler handler = ((GenericContainerScreen) mc.currentScreen).getScreenHandler();
            
            for (int i = 0; i < handler.slots.size(); i++) {
                ItemStack stack = handler.slots.get(i).getStack();
                
                if (!stack.isEmpty() && isAllowed(stack) &&
                    timer.passedMs(delay.getValue() + (random.getValue() && delay.getValue() != 0 ? rnd.nextInt((int) delay.getValue()) : 0))) {
                    timer.reset();
                    mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    autoMystDelay.reset();
                }
            }
            
            if (isContainerEmpty(handler) && close.getValue()) {
                mc.player.closeHandledScreen();
            }
        }
    }

    private boolean isAllowed(ItemStack stack) {
        if (sort.getValue() == Sort.None) {
            return true;
        }
        
        boolean allowed = itemFilter.contains(stack.getItem().getTranslationKey()
            .replace("block.minecraft.", "")
            .replace("item.minecraft.", ""));
        
        return switch (sort.getValue()) {
            case WhiteList -> allowed;
            case BlackList -> !allowed;
            default -> true;
        };
    }

    private boolean isContainerEmpty(GenericContainerScreenHandler container) {
        for (int i = 0; i < (container.slots.size() == 90 ? 54 : 27); i++) {
            if (!container.slots.get(i).getStack().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private enum Sort {
        None,
        WhiteList,
        BlackList
    }
}