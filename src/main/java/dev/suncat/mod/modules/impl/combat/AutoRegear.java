/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.block.BedBlock
 *  net.minecraft.block.Blocks
 *  net.minecraft.block.PistonBlock
 *  net.minecraft.block.ShulkerBoxBlock
 *  net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen
 *  net.minecraft.component.DataComponentTypes
 *  net.minecraft.component.type.PotionContentsComponent
 *  net.minecraft.entity.effect.StatusEffect
 *  net.minecraft.entity.effect.StatusEffectInstance
 *  net.minecraft.entity.effect.StatusEffects
 *  net.minecraft.entity.player.PlayerEntity
 *  net.minecraft.item.BlockItem
 *  net.minecraft.item.Item
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.screen.ScreenHandler
 *  net.minecraft.screen.ShulkerBoxScreenHandler
 *  net.minecraft.screen.slot.Slot
 *  net.minecraft.screen.slot.SlotActionType
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.util.math.Direction
 *  net.minecraft.util.math.MathHelper
 */
package dev.suncat.mod.modules.impl.combat;

import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.UpdateEvent;
import dev.suncat.api.utils.math.Timer;
import dev.suncat.api.utils.player.InventoryUtil;
import dev.suncat.api.utils.world.BlockUtil;
import dev.suncat.core.impl.KitManager;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.impl.player.PacketMine;
import dev.suncat.mod.modules.settings.impl.BindSetting;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.Registries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

public class AutoRegear
extends Module {
    public static AutoRegear INSTANCE;
    public final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true));
    public final Timer timeoutTimer = new Timer();
    final int[] stealCountList = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private final BooleanSetting autoDisable = this.add(new BooleanSetting("AutoDisable", true));
    private final SliderSetting disableTime = this.add(new SliderSetting("DisableTime", 500, 0, 1000));
    private final BooleanSetting place = this.add(new BooleanSetting("Place", true));
    private final BooleanSetting inventory = this.add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting preferOpen = this.add(new BooleanSetting("PerferOpen", true));
    private final BooleanSetting open = this.add(new BooleanSetting("Open", false));
    private final SliderSetting range = this.add(new SliderSetting("MaxRange", 4.0, 0.0, 6.0, 0.1));
    private final SliderSetting minRange = this.add(new SliderSetting("MinRange", 1.0, 0.0, 3.0, 0.1));
    private final BooleanSetting mine = this.add(new BooleanSetting("Mine", true));
    private final BooleanSetting take = this.add(new BooleanSetting("Take", true));
    private final BooleanSetting smart = this.add(new BooleanSetting("Smart", true, this.take::getValue).setParent());
    private final BooleanSetting forceMove = this.add(new BooleanSetting("ForceQuickMove", true, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting takeSpeed = this.add(new SliderSetting("TakeSpeed", 1, 1, 10, 1, this.take::getValue));
    private final SliderSetting clickDelay = this.add(new SliderSetting("ClickDelay", 150, 50, 500, 5, this.take::getValue));
    private final BooleanSetting instantTake = this.add(new BooleanSetting("InstantTake", false, this.take::getValue));
    private final BindSetting placeKey = this.add(new BindSetting("PlaceKey", -1));
    private final BooleanSetting onlyGround = this.add(new BooleanSetting("OnlyGround", true));
    private final BooleanSetting onlyHotbar = this.add(new BooleanSetting("OnlyHotbar", false));

    // Kit settings
    public String currentKitName = null;
    private final BooleanSetting replaceItem = this.add(new BooleanSetting("ReplaceItem", false));

    // Take speed control
    private int takeProgress = 0;
    private final Timer clickTimer = new Timer();
    private int takenThisCycle = 0;
    
    private final Timer timer = new Timer();
    private final List<BlockPos> openList = new ArrayList<BlockPos>();
    public BlockPos placePos = null;
    private BlockPos openPos;
    private boolean opend = false;
    private boolean on = false;
    private boolean placeKeyPressed = false;

    private boolean hasShownNoShulkerMessage = false;
    private boolean waitingForTransfer = false;
    private final Timer transferTimer = new Timer();

    public AutoRegear() {
        super("AutoRegear", Module.Category.Combat);
        this.setChinese("\u81ea\u52a8\u8865\u7ed9");
        INSTANCE = this;
    }

    public int findShulker() {
        if (this.inventory.getValue()) {
            int start = 0;
            int end = 36;
            if (this.onlyHotbar.getValue()) {
                start = 0;
                end = 9;
            }
            for (int i = start; i < end; ++i) {
                BlockItem blockItem;
                Item item;
                ItemStack stack = AutoRegear.mc.player.getInventory().getStack(i);
                if (stack.isEmpty() || !((item = stack.getItem()) instanceof BlockItem) || !((blockItem = (BlockItem)item).getBlock() instanceof ShulkerBoxBlock)) continue;
                return i < 9 ? i + 36 : i;
            }
            return -1;
        }
        return InventoryUtil.findClass(ShulkerBoxBlock.class);
    }

    @Override
    public void onEnable() {
        this.opend = false;
        this.openPos = null;
        this.timeoutTimer.reset();
        this.placePos = null;
        this.takeProgress = 0;
        this.takenThisCycle = 0;
        this.clickTimer.reset();
        this.placeKeyPressed = false;
        this.hasShownNoShulkerMessage = false;
        this.waitingForTransfer = false;
        this.transferTimer.reset();

        if (AutoRegear.nullCheck()) {
            return;
        }

        if (this.onlyGround.getValue() && !AutoRegear.mc.player.isOnGround()) {
            this.sendMessage("\u00a74AutoRegear disabled: Player is not on ground");
            this.disable();
            return;
        }

        if (this.open.getValue()) {
            for (BlockPos pos : BlockUtil.getSphere((float)this.range.getValue())) {
                if (AutoRegear.mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock &&
                    AutoRegear.mc.world.isAir(pos.up())) {
                    if (isValidShulkerBox(pos)) {
                        this.openPos = pos;
                        this.sendMessage("\u00a72Found shulker box at: " + pos.toShortString());
                        break;
                    }
                }
            }
        }

        if (this.openPos == null && this.place.getValue()) {
            if (this.findShulker() == -1) {
                this.sendMessage("\u00a74No shulkerbox found in inventory. AutoRegear disabled.");
                this.disable();
                return;
            }
            this.doPlace();
        } else if (this.openPos != null) {
            this.timer.reset();
        } else {
            this.sendMessage("\u00a74No shulker box found nearby. AutoRegear disabled.");
            this.disable();
        }
    }

    private void doPlace() {
        if (AutoRegear.nullCheck()) {
            return;
        }
        
        int oldSlot = AutoRegear.mc.player.getInventory().selectedSlot;
        double getDistance = 100.0;
        BlockPos bestPos = null;

        for (BlockPos pos : BlockUtil.getSphere((float)this.range.getValue())) {
            BlockPos belowPos = pos.offset(Direction.DOWN);
            BlockPos abovePos = pos.offset(Direction.UP);

            if (!AutoRegear.mc.world.isAir(pos)) continue;
            if (!AutoRegear.mc.world.isAir(abovePos) && !BlockUtil.canReplace(abovePos)) continue;

            if (AutoRegear.mc.world.isAir(belowPos)) continue;
            if (!AutoRegear.mc.world.getBlockState(belowPos).isSolid()) continue;

            if (!AutoRegear.mc.world.getFluidState(belowPos).isEmpty()) continue;

            double dist = AutoRegear.mc.player.squaredDistanceTo(pos.toCenterPos());
            if (dist < this.minRange.getValue() * this.minRange.getValue()) continue;
            if (dist > this.range.getValue() * this.range.getValue()) continue;

            if (!BlockUtil.clientCanPlace(pos, false)) continue;

            if (!AutoRegear.mc.world.getFluidState(pos).isEmpty()) continue;

            if (!canPlayerSeePosition(pos)) continue;

            if (bestPos == null || dist < getDistance) {
                getDistance = dist;
                bestPos = pos;
            }
        }
        
        if (bestPos != null) {
            if (this.findShulker() == -1) {
                this.sendMessage("\u00a74No shulkerbox found. AutoRegear disabled.");
                this.disable();
                return;
            }

            if (AutoRegear.mc.world.getBlockState(bestPos).getBlock() instanceof ShulkerBoxBlock) {
                this.openPos = bestPos;
                this.placePos = bestPos;
                this.sendMessage("\u00a72Found existing shulker at: " + bestPos.toShortString());
                return;
            }

            BlockPos abovePos = bestPos.offset(Direction.UP);
            if (!AutoRegear.mc.world.isAir(abovePos) && !BlockUtil.canReplace(abovePos)) {
                this.sendMessage("\u00a74No enough space above placement position. AutoRegear disabled.");
                this.disable();
                return;
            }

            if (this.inventory.getValue()) {
                int slot = this.findShulker();
                InventoryUtil.inventorySwap(slot, oldSlot);
                this.placeBlock(bestPos);
                this.placePos = bestPos;
                this.openPos = bestPos;
                InventoryUtil.inventorySwap(slot, oldSlot);
            } else {
                InventoryUtil.switchToSlot(this.findShulker());
                this.placeBlock(bestPos);
                this.placePos = bestPos;
                this.openPos = bestPos;
                InventoryUtil.switchToSlot(oldSlot);
            }
            this.timer.reset();
            this.sendMessage("\u00a72Placed shulker box at: " + bestPos.toShortString());
        } else {
            this.sendMessage("\u00a74No valid place position found. AutoRegear disabled.");
            this.disable();
        }
    }

    @Override
    public void onDisable() {
        this.opend = false;
        this.openPos = null;
        this.placePos = null;
        this.placeKeyPressed = false;

        if (this.mine.getValue() && this.placePos != null && AutoRegear.mc.world != null) {
            if (AutoRegear.mc.world.getBlockState(this.placePos).getBlock() instanceof ShulkerBoxBlock) {
                if (isValidShulkerBox(this.placePos)) {
                    PacketMine.INSTANCE.mine(this.placePos);
                }
            }
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (AutoRegear.nullCheck()) {
            return;
        }

        if (this.onlyGround.getValue() && !AutoRegear.mc.player.isOnGround()) {
            return;
        }

        boolean currentKeyState = this.placeKey.isPressed();
        
        if (currentKeyState && !this.placeKeyPressed && AutoRegear.mc.currentScreen == null) {
            this.placeKeyPressed = true;
            this.opend = false;
            this.openPos = null;
            this.timeoutTimer.reset();
            this.placePos = null;
            this.doPlace();
            this.on = true;
        } else if (!currentKeyState) {
            this.placeKeyPressed = false;
            this.on = false;
        }
        
        this.openList.removeIf(pos -> !(AutoRegear.mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock));
        
        if (this.openPos == null && this.open.getValue()) {
            for (BlockPos pos : BlockUtil.getSphere((float)this.range.getValue())) {
                if (AutoRegear.mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock &&
                    AutoRegear.mc.world.isAir(pos.up())) {
                    if (isValidShulkerBox(pos)) {
                        this.openPos = pos;
                        break;
                    }
                }
            }
        }
        
        if (!(AutoRegear.mc.currentScreen instanceof ShulkerBoxScreen)) {
            if (this.waitingForTransfer) {
                if (this.transferTimer.passed(1000)) {
                    this.waitingForTransfer = false;
                    this.takeProgress = 0;
                }
                return;
            }

            if (this.opend) {
                this.opend = false;
                if (this.autoDisable.getValue()) {
                    this.timeoutToDisable();
                }
                if (this.mine.getValue() && this.openPos != null) {
                    if (AutoRegear.mc.world.getBlockState(this.openPos).getBlock() instanceof ShulkerBoxBlock) {
                        if (isValidShulkerBox(this.openPos)) {
                            PacketMine.INSTANCE.mine(this.openPos);
                        }
                    } else {
                        this.openPos = null;
                    }
                }
                return;
            }
            if (this.open.getValue()) {
                if (AutoRegear.mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler) {
                    return;
                }
                
                if (this.placePos != null && (double)MathHelper.sqrt((float)((float)AutoRegear.mc.player.squaredDistanceTo(this.placePos.toCenterPos()))) <= this.range.getValue() && AutoRegear.mc.world.isAir(this.placePos.up()) && (!this.timer.passed(500L) || AutoRegear.mc.world.getBlockState(this.placePos).getBlock() instanceof ShulkerBoxBlock)) {
                    if (AutoRegear.mc.world.getBlockState(this.placePos).getBlock() instanceof ShulkerBoxBlock) {
                        if (isValidShulkerBox(this.placePos)) {
                            this.openPos = this.placePos;
                            BlockUtil.clickBlock(this.placePos, BlockUtil.getClickSide(this.placePos), this.rotate.getValue());
                        }
                    }
                } else if (this.openPos != null && AutoRegear.mc.world.getBlockState(this.openPos).getBlock() instanceof ShulkerBoxBlock) {
                    if (isValidShulkerBox(this.openPos)) {
                        BlockUtil.clickBlock(this.openPos, BlockUtil.getClickSide(this.openPos), this.rotate.getValue());
                    }
                } else {
                    boolean found = false;
                    for (BlockPos pos2 : BlockUtil.getSphere((float)this.range.getValue())) {
                        if (this.openList.contains(pos2) || !AutoRegear.mc.world.isAir(pos2.up()) && !BlockUtil.canReplace(pos2.up()) || !(AutoRegear.mc.world.getBlockState(pos2).getBlock() instanceof ShulkerBoxBlock)) continue;
                        if (isValidShulkerBox(pos2)) {
                            this.openPos = pos2;
                            BlockUtil.clickBlock(pos2, BlockUtil.getClickSide(pos2), this.rotate.getValue());
                            found = true;
                            break;
                        }
                    }
                    if (!found && this.autoDisable.getValue()) {
                        this.doPlace();
                    }
                }
            } else if (!this.take.getValue() && this.autoDisable.getValue()) {
                this.timeoutToDisable();
            }
            return;
        }
        this.opend = true;
        if (this.openPos != null) {
            this.openList.add(this.openPos);
        }
        if (!this.take.getValue()) {
            if (this.autoDisable.getValue()) {
                this.timeoutToDisable();
            }
            return;
        }

        if (!this.instantTake.getValue() && !this.clickTimer.passed(this.clickDelay.getValueInt())) {
            return;
        }

        ScreenHandler screenHandler = AutoRegear.mc.player.currentScreenHandler;
        if (!(screenHandler instanceof ShulkerBoxScreenHandler)) {
            return;
        }
        ShulkerBoxScreenHandler shulkerCheck = (ShulkerBoxScreenHandler)screenHandler;
        if (shulkerCheck.slots == null || shulkerCheck.slots.isEmpty()) {
            return;
        }

        boolean take = false;
        if (screenHandler instanceof ShulkerBoxScreenHandler) {
            ShulkerBoxScreenHandler shulker = (ShulkerBoxScreenHandler)screenHandler;

            String kitName = this.currentKitName;
            KitManager.Kit kit = kitName != null ? KitManager.getKit(kitName) : null;

            if (kit != null) {
                this.takenThisCycle = 0;
                int maxTakes = this.instantTake.getValue() ? 36 : this.takeSpeed.getValueInt();

                boolean hasTaken = false;
                
                for (int i = 0; i < 36; i++) {
                    if (kit.mainInventory[i] == null || kit.mainInventory[i].isEmpty()) {
                        continue;
                    }

                    String kitItemId = kit.mainInventory[i];
                    int needCount = kit.mainInventoryCounts[i];
                    
                    int currentCount = getPlayerItemCount(kitItemId);

                    if (currentCount >= needCount) {
                        continue;
                    }

                    int needed = needCount - currentCount;

                    for (Slot slot : shulker.slots) {
                        if (slot.id >= 27 || slot.getStack().isEmpty()) continue;

                        ItemStack stack = slot.getStack();
                        String shulkerItemId = Registries.ITEM.getId(stack.getItem()).toString();

                        if (shulkerItemId.equals(kitItemId)) {
                            AutoRegear.mc.interactionManager.clickSlot(shulker.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, (PlayerEntity)AutoRegear.mc.player);
                            
                            take = true;
                            hasTaken = true;
                            this.takenThisCycle++;
                            
                            currentCount += stack.getCount();
                            needed -= stack.getCount();

                            if (!this.instantTake.getValue()) {
                                this.clickTimer.reset();
                            }
                            
                            if (needed <= 0) {
                                break;
                            }
                            
                            if (this.takenThisCycle >= maxTakes) {
                                break;
                            }
                        }
                    }
                    
                    if (this.takenThisCycle >= maxTakes) {
                        break;
                    }
                }
                
                if (!hasTaken && this.autoDisable.getValue()) {
                    if (this.mine.getValue() && this.openPos != null) {
                        if (AutoRegear.mc.world.getBlockState(this.openPos).getBlock() instanceof ShulkerBoxBlock) {
                            if (isValidShulkerBox(this.openPos)) {
                                PacketMine.INSTANCE.mine(this.openPos);
                            }
                        }
                    }
                    this.disable();
                }
            } else {
                for (Slot slot : shulker.slots) {
                    if (slot.id < 27 && !slot.getStack().isEmpty()) {
                        AutoRegear.mc.interactionManager.clickSlot(shulker.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, (PlayerEntity)AutoRegear.mc.player);
                        take = true;
                        this.takenThisCycle++;
                        if (this.takenThisCycle >= this.takeSpeed.getValueInt()) break;
                    }
                }
            }
        }
        if (this.autoDisable.getValue() && !take) {
            this.timeoutToDisable();
        }
    }

    private void timeoutToDisable() {
        if (this.timeoutTimer.passed(this.disableTime.getValueInt())) {
            this.disable();
        }
    }

    private Type needSteal(ItemStack i) {
        if (i.getItem().equals(Items.END_CRYSTAL) && this.stealCountList[0] > 0) {
            this.stealCountList[0] = this.stealCountList[0] - i.getCount();
            if (this.stealCountList[0] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Items.EXPERIENCE_BOTTLE) && this.stealCountList[1] > 0) {
            this.stealCountList[1] = this.stealCountList[1] - i.getCount();
            if (this.stealCountList[1] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Items.TOTEM_OF_UNDYING) && this.stealCountList[2] > 0) {
            this.stealCountList[2] = this.stealCountList[2] - i.getCount();
            if (this.stealCountList[2] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Items.ENCHANTED_GOLDEN_APPLE) && this.stealCountList[3] > 0) {
            this.stealCountList[3] = this.stealCountList[3] - i.getCount();
            if (this.stealCountList[3] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.OBSIDIAN.asItem()) && this.stealCountList[4] > 0) {
            this.stealCountList[4] = this.stealCountList[4] - i.getCount();
            if (this.stealCountList[4] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.COBWEB.asItem()) && this.stealCountList[5] > 0) {
            this.stealCountList[5] = this.stealCountList[5] - i.getCount();
            if (this.stealCountList[5] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.GLOWSTONE.asItem()) && this.stealCountList[6] > 0) {
            this.stealCountList[6] = this.stealCountList[6] - i.getCount();
            if (this.stealCountList[6] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.RESPAWN_ANCHOR.asItem()) && this.stealCountList[7] > 0) {
            this.stealCountList[7] = this.stealCountList[7] - i.getCount();
            if (this.stealCountList[7] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Items.ENDER_PEARL) && this.stealCountList[8] > 0) {
            this.stealCountList[8] = this.stealCountList[8] - i.getCount();
            if (this.stealCountList[8] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem() instanceof BlockItem && ((BlockItem)i.getItem()).getBlock() instanceof PistonBlock && this.stealCountList[9] > 0) {
            this.stealCountList[9] = this.stealCountList[9] - i.getCount();
            if (this.stealCountList[9] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.REDSTONE_BLOCK.asItem()) && this.stealCountList[10] > 0) {
            this.stealCountList[10] = this.stealCountList[10] - i.getCount();
            if (this.stealCountList[10] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem() instanceof BlockItem && ((BlockItem)i.getItem()).getBlock() instanceof BedBlock && this.stealCountList[11] > 0) {
            this.stealCountList[11] = this.stealCountList[11] - i.getCount();
            if (this.stealCountList[11] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem() == Items.SPLASH_POTION) {
            PotionContentsComponent potionContentsComponent = (PotionContentsComponent)i.getOrDefault(DataComponentTypes.POTION_CONTENTS, (Object)PotionContentsComponent.DEFAULT);
            for (StatusEffectInstance effect : potionContentsComponent.getEffects()) {
                if (effect.getEffectType().value() == StatusEffects.SPEED.value()) {
                    if (this.stealCountList[12] <= 0) continue;
                    this.stealCountList[12] = this.stealCountList[12] - i.getCount();
                    if (this.stealCountList[12] < 0) {
                        return Type.Stack;
                    }
                    return Type.QuickMove;
                }
                if (effect.getEffectType().value() == StatusEffects.RESISTANCE.value()) {
                    if (this.stealCountList[13] <= 0) continue;
                    this.stealCountList[13] = this.stealCountList[13] - i.getCount();
                    if (this.stealCountList[13] < 0) {
                        return Type.Stack;
                    }
                    return Type.QuickMove;
                }
                if (effect.getEffectType().value() != StatusEffects.STRENGTH.value() || this.stealCountList[14] <= 0) continue;
                this.stealCountList[14] = this.stealCountList[14] - i.getCount();
                if (this.stealCountList[14] < 0) {
                    return Type.Stack;
                }
                return Type.QuickMove;
            }
        }
        return Type.None;
    }

    private void placeBlock(BlockPos pos) {
        if (pos == null || AutoRegear.mc.world == null || AutoRegear.mc.player == null) {
            return;
        }
        
        if (AutoRegear.mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler) {
            return;
        }
        
        AntiRegear.INSTANCE.safe.add(pos);
        BlockUtil.clickBlock(pos.offset(Direction.DOWN), Direction.UP, this.rotate.getValue());
    }

    private int findItemInInventory(String itemId) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = AutoRegear.mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            String stackId = Registries.ITEM.getId(stack.getItem()).toString();
            if (stackId.equals(itemId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 获取玩家背包中指定物品的总数量
     * @param itemId 物品 ID，必须包含命名空间（如 minecraft:obsidian）
     * @return 物品总数
     */
    private int getPlayerItemCount(String itemId) {
        int count = 0;
        // Main inventory (0-35)
        for (int i = 0; i < 36; i++) {
            ItemStack stack = AutoRegear.mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                String stackId = Registries.ITEM.getId(stack.getItem()).toString();
                if (stackId.equals(itemId)) {
                    count += stack.getCount();
                }
            }
        }
        // Offhand
        ItemStack offhand = AutoRegear.mc.player.getInventory().offHand.get(0);
        if (!offhand.isEmpty()) {
            String stackId = Registries.ITEM.getId(offhand.getItem()).toString();
            if (stackId.equals(itemId)) {
                count += offhand.getCount();
            }
        }
        // Armor (36-39)
        for (int i = 0; i < 4; i++) {
            ItemStack stack = AutoRegear.mc.player.getInventory().getStack(36 + i);
            if (!stack.isEmpty()) {
                String stackId = Registries.ITEM.getId(stack.getItem()).toString();
                if (stackId.equals(itemId)) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    private void swapInventorySlots(int slot1, int slot2) {
        ScreenHandler screenHandler = AutoRegear.mc.player.currentScreenHandler;
        if (screenHandler == null) return;
        
        if (!(screenHandler instanceof ShulkerBoxScreenHandler)) {
            return;
        }
        ShulkerBoxScreenHandler shulker = (ShulkerBoxScreenHandler)screenHandler;
        if (shulker.slots == null || shulker.slots.isEmpty()) {
            return;
        }

        AutoRegear.mc.interactionManager.clickSlot(screenHandler.syncId, slot1, 0, SlotActionType.PICKUP, (PlayerEntity)AutoRegear.mc.player);
        AutoRegear.mc.interactionManager.clickSlot(screenHandler.syncId, slot2, 0, SlotActionType.PICKUP, (PlayerEntity)AutoRegear.mc.player);
        AutoRegear.mc.interactionManager.clickSlot(screenHandler.syncId, slot1, 0, SlotActionType.PICKUP, (PlayerEntity)AutoRegear.mc.player);
    }

    private boolean canPlayerSeePosition(BlockPos pos) {
        if (AutoRegear.mc.player == null || AutoRegear.mc.world == null) {
            return false;
        }
        Vec3d eyesPos = AutoRegear.mc.player.getEyePos();
        Vec3d targetPos = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        
        double dist = eyesPos.distanceTo(targetPos);
        if (dist > this.range.getValue()) {
            return false;
        }
        
        Vec3d direction = targetPos.subtract(eyesPos).normalize();
        int steps = (int)(dist * 2);
        for (int i = 0; i < steps; i++) {
            double checkDist = i * 0.5;
            Vec3d checkPos = eyesPos.add(direction.multiply(checkDist));
            BlockPos checkBlock = new BlockPos((int)checkPos.getX(), (int)checkPos.getY(), (int)checkPos.getZ());
            if (!AutoRegear.mc.world.isAir(checkBlock) && !BlockUtil.canReplace(checkBlock)) {
                return false;
            }
        }
        
        return true;
    }

    private boolean isValidShulkerBox(BlockPos pos) {
        if (pos == null || AutoRegear.mc.world == null) {
            return false;
        }
        try {
            if (!(AutoRegear.mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock)) {
                return false;
            }
            var blockEntity = AutoRegear.mc.world.getBlockEntity(pos);
            if (blockEntity == null) {
                return false;
            }
            if (!(blockEntity instanceof ShulkerBoxBlockEntity)) {
                return false;
            }
            if (blockEntity.isRemoved()) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static enum Type {
        None,
        Stack,
        QuickMove;

    }
}
