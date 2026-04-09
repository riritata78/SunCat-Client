package dev.suncat.mod.modules.impl.player;

import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.settings.impl.*;
import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.ClientTickEvent;
import dev.suncat.api.utils.math.Timer;
import dev.suncat.api.utils.player.InventoryUtil;
import dev.suncat.api.utils.world.BlockUtil;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.ArrayList;
import java.util.List;

public class AutoEnderChest extends Module {
    public static AutoEnderChest INSTANCE;

    // 基础设置
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true));
    private final BooleanSetting autoDisable = this.add(new BooleanSetting("AutoDisable", true));
    private final SliderSetting disableDelay = this.add(new SliderSetting("DisableDelay", 500, 0, 2000, 10, this.autoDisable::getValue));
    private final BooleanSetting place = this.add(new BooleanSetting("Place", true));
    private final SliderSetting placeDelay = this.add(new SliderSetting("PlaceDelay", 100, 0, 1000, 10, this.place::getValue));
    private final BooleanSetting open = this.add(new BooleanSetting("Open", true));
    private final SliderSetting openDelay = this.add(new SliderSetting("OpenDelay", 100, 0, 1000, 10, this.open::getValue));
    
    // 拿取设置
    private final BooleanSetting take = this.add(new BooleanSetting("Take", true));
    private final BooleanSetting smart = this.add(new BooleanSetting("Smart", true, this.take::getValue).setParent());
    private final SliderSetting range = this.add(new SliderSetting("Range", 4.0, 0.0, 6.0, 0.1));
    private final SliderSetting minRange = this.add(new SliderSetting("MinRange", 1.0, 0.0, 3.0, 0.1));
    private final SliderSetting takeSpeed = this.add(new SliderSetting("TakeSpeed", 27, 1, 27, 1, this.take::getValue));
    private final SliderSetting takeGrace = this.add(new SliderSetting("TakeGrace", 200, 0, 600, 10, this.take::getValue));
    
    // 自动关闭
    private final BooleanSetting autoDisableOnOpen = this.add(new BooleanSetting("AutoDisableOnOpen", false, this.open::getValue));
    private final BooleanSetting autoDisableAfterCycle = this.add(new BooleanSetting("AutoDisableAfterCycle", true));

    // 需求物品数量设置
    private final SliderSetting crystalNeed = this.add(new SliderSetting("Crystal", 256, 0, 512, 1, () -> this.take.getValue() && this.smart.getValue()));
    private final SliderSetting expNeed = this.add(new SliderSetting("Exp", 256, 0, 512, 1, () -> this.take.getValue() && this.smart.getValue()));
    private final SliderSetting totemNeed = this.add(new SliderSetting("Totem", 6, 0, 64, 1, () -> this.take.getValue() && this.smart.getValue()));
    private final SliderSetting gappleNeed = this.add(new SliderSetting("Gapple", 128, 0, 512, 1, () -> this.take.getValue() && this.smart.getValue()));
    private final SliderSetting obsidianNeed = this.add(new SliderSetting("Obsidian", 64, 0, 512, 1, () -> this.take.getValue() && this.smart.getValue()));
    private final SliderSetting webNeed = this.add(new SliderSetting("Web", 64, 0, 512, 1, () -> this.take.getValue() && this.smart.getValue()));
    private final SliderSetting glowstoneNeed = this.add(new SliderSetting("Glowstone", 128, 0, 512, 1, () -> this.take.getValue() && this.smart.getValue()));
    private final SliderSetting anchorNeed = this.add(new SliderSetting("Anchor", 128, 0, 512, 1, () -> this.take.getValue() && this.smart.getValue()));
    private final SliderSetting pearlNeed = this.add(new SliderSetting("Pearl", 16, 0, 64, 1, () -> this.take.getValue() && this.smart.getValue()));
    private final SliderSetting pistonNeed = this.add(new SliderSetting("Piston", 64, 0, 512, 1, () -> this.take.getValue() && this.smart.getValue()));
    private final SliderSetting redstoneNeed = this.add(new SliderSetting("Redstone", 64, 0, 512, 1, () -> this.take.getValue() && this.smart.getValue()));
    private final SliderSetting bedNeed = this.add(new SliderSetting("Bed", 256, 0, 512, 1, () -> this.take.getValue() && this.smart.getValue()));
    private final SliderSetting shulkerNeed = this.add(new SliderSetting("Shulker", 6, 0, 64, 1, () -> this.take.getValue() && this.smart.getValue()));

    private final Timer placeTimer = new Timer();
    private final Timer openTimer = new Timer();
    private final Timer disableTimer = new Timer();
    private final Timer graceTimer = new Timer();

    private final List<BlockPos> opened = new ArrayList<>();
    private BlockPos placedPos;
    private BlockPos openingPos;
    private long enderChestOpenAtMs;
    private long disableAfterCycleAtMs;

    private final int[] need = new int[14];

    public AutoEnderChest() {
        super("AutoEnderChest", Module.Category.Player);
        this.setChinese("自动末影箱");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            this.disable();
            return;
        }
        opened.clear();
        placedPos = null;
        openingPos = null;
        enderChestOpenAtMs = 0;
        disableAfterCycleAtMs = 0;
        placeTimer.reset();
        openTimer.reset();
        disableTimer.reset();
        graceTimer.reset();
    }

    @Override
    public void onDisable() {
        opened.clear();
        placedPos = null;
        openingPos = null;
        enderChestOpenAtMs = 0;
        disableAfterCycleAtMs = 0;
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck()) return;
        if (event.isPost()) return; // 只在 Pre 执行

        // 自动关闭逻辑
        if (disableAfterCycleAtMs > 0 && System.currentTimeMillis() >= disableAfterCycleAtMs) {
            disableAfterCycleAtMs = 0;
            this.disable();
            return;
        }

        // 处理已打开的箱子列表
        opened.removeIf(pos -> !mc.world.getBlockState(pos).isOf(Blocks.ENDER_CHEST));

        // 如果当前在末影箱界面
        if (handleOpenEnderChestScreen()) return;

        // 如果不在游戏界面（如背包）则跳过
        if (mc.currentScreen != null) return;

        // 尝试打开附近的末影箱
        if (open.getValue() && hasOpenableEnderChestInRange()) {
            if (tryOpenNearby()) {
                disableTimer.reset();
            }
            return;
        }

        // 尝试放置末影箱
        if (place.getValue() && placeTimer.passed((long)placeDelay.getValue()) && canPlaceAnotherEnderChest()) {
            if (tryPlaceEnderChest()) {
                disableTimer.reset();
            }
        }

        // 自动禁用
        if (autoDisable.getValue() && disableTimer.passed((long)disableDelay.getValue())) {
            this.disable();
        }
    }

    private boolean handleOpenEnderChestScreen() {
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler container)) {
            enderChestOpenAtMs = 0;
            return false;
        }

        if (container.getRows() != 3) {
            enderChestOpenAtMs = 0;
            return false;
        }

        if (openingPos != null) {
            opened.add(openingPos);
            if (openingPos.equals(placedPos)) placedPos = null;
        }

        if (enderChestOpenAtMs == 0) {
            enderChestOpenAtMs = System.currentTimeMillis();
            if (autoDisableOnOpen.getValue() && !take.getValue()) {
                this.disable();
                return true;
            }
        }

        if (!take.getValue()) return true;

        if (smart.getValue()) updateNeed();

        boolean movedAny = false;
        int moved = 0;
        for (int i = 0; i < 27; i++) {
            if (moved >= takeSpeed.getValue()) break;
            Slot slot = container.getSlot(i);
            if (slot.getStack().isEmpty()) continue;

            int needIndex = smart.getValue() ? getNeedIndex(slot.getStack()) : -1;
            if (smart.getValue() && needIndex == -1) continue;

            quickMove(container, i);
            if (smart.getValue() && needIndex != -1) dec(needIndex, slot.getStack().getCount());
            movedAny = true;
            moved++;
        }

        if (!movedAny) {
            if (System.currentTimeMillis() - enderChestOpenAtMs < takeGrace.getValue()) {
                return true; // 等待同步
            }
            mc.player.closeHandledScreen();
            enderChestOpenAtMs = 0;
            disableTimer.reset();
            if (autoDisableAfterCycle.getValue()) {
                disableAfterCycleAtMs = System.currentTimeMillis();
            }
            if (autoDisableOnOpen.getValue()) {
                this.disable();
            }
        }

        return true;
    }

    private boolean hasOpenableEnderChestInRange() {
        for (BlockPos pos : BlockUtil.getSphere((float)range.getValue())) {
            if (!mc.world.getBlockState(pos).isOf(Blocks.ENDER_CHEST)) continue;
            if (opened.contains(pos)) continue;
            if (!canOpenEnderChest(pos)) continue;
            return true;
        }
        return false;
    }

    private boolean tryOpenNearby() {
        if (!openTimer.passed((long)openDelay.getValue())) return false;

        BlockPos target = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockUtil.getSphere((float)range.getValue())) {
            if (!mc.world.getBlockState(pos).isOf(Blocks.ENDER_CHEST)) continue;
            if (opened.contains(pos)) continue;
            if (!canOpenEnderChest(pos)) continue;

            double dist = mc.player.squaredDistanceTo(pos.toCenterPos());
            if (dist < bestDist) {
                bestDist = dist;
                target = pos;
            }
        }

        if (target == null) return false;

        Direction dir = BlockUtil.getClickSide(target);
        openingPos = target;
        BlockUtil.clickBlock(target, dir, rotate.getValue(), Hand.MAIN_HAND, true);
        openTimer.reset();
        return true;
    }

    private boolean canPlaceAnotherEnderChest() {
        if (placedPos == null) return true;
        if (!mc.world.getBlockState(placedPos).isOf(Blocks.ENDER_CHEST)) {
            placedPos = null;
            return true;
        }
        return false;
    }

    private boolean tryPlaceEnderChest() {
        int slot = InventoryUtil.findBlockInventorySlot(Blocks.ENDER_CHEST);
        if (slot == -1) return false;

        BlockPos placePos = findPlacePos();
        if (placePos == null) return false;

        int oldSlot = mc.player.getInventory().selectedSlot;
        InventoryUtil.switchToSlot(slot);
        BlockUtil.placedPos.add(placePos);
        BlockUtil.clickBlock(placePos.offset(BlockUtil.getPlaceSide(placePos)), BlockUtil.getPlaceSide(placePos).getOpposite(), rotate.getValue(), Hand.MAIN_HAND, true);
        InventoryUtil.switchToSlot(oldSlot);

        placedPos = placePos;
        openingPos = placePos;
        placeTimer.reset();
        return true;
    }

    private BlockPos findPlacePos() {
        int radius = (int) Math.ceil(range.getValue());
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        double minSq = minRange.getValue() * minRange.getValue();
        double maxSq = range.getValue() * range.getValue();
        BlockPos center = mc.player.getBlockPos();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x*x + y*y + z*z > radius*radius) continue;
                    BlockPos pos = center.add(x, y, z);
                    if (!BlockUtil.canPlace(pos, 0.0, false)) continue;

                    double distSq = mc.player.squaredDistanceTo(pos.toCenterPos());
                    if (distSq < minSq || distSq > maxSq || distSq >= bestDistSq) continue;
                    if (!mc.world.getBlockState(pos.up()).isAir()) continue;

                    best = pos;
                    bestDistSq = distSq;
                }
            }
        }
        return best;
    }

    private boolean canOpenEnderChest(BlockPos pos) {
        return mc.world.getBlockState(pos).isOf(Blocks.ENDER_CHEST) &&
               mc.world.getBlockState(pos.up()).getCollisionShape(mc.world, pos.up()).isEmpty();
    }

    private void quickMove(GenericContainerScreenHandler handler, int slotId) {
        mc.interactionManager.clickSlot(handler.syncId, slotId, 0, SlotActionType.QUICK_MOVE, mc.player);
    }

    private void updateNeed() {
        need[0] = crystalNeed.getValueInt() - countItem(Items.END_CRYSTAL);
        need[1] = expNeed.getValueInt() - countItem(Items.EXPERIENCE_BOTTLE);
        need[2] = totemNeed.getValueInt() - countItem(Items.TOTEM_OF_UNDYING);
        need[3] = gappleNeed.getValueInt() - (countItem(Items.ENCHANTED_GOLDEN_APPLE) + countItem(Items.GOLDEN_APPLE));
        need[4] = obsidianNeed.getValueInt() - countItem(Blocks.OBSIDIAN.asItem());
        need[5] = webNeed.getValueInt() - countItem(Blocks.COBWEB.asItem());
        need[6] = glowstoneNeed.getValueInt() - countItem(Blocks.GLOWSTONE.asItem());
        need[7] = anchorNeed.getValueInt() - countItem(Blocks.RESPAWN_ANCHOR.asItem());
        need[8] = pearlNeed.getValueInt() - countItem(Items.ENDER_PEARL);
        need[9] = pistonNeed.getValueInt() - (countItem(Blocks.PISTON.asItem()) + countItem(Blocks.STICKY_PISTON.asItem()));
        need[10] = redstoneNeed.getValueInt() - countItem(Blocks.REDSTONE_BLOCK.asItem());
        need[11] = bedNeed.getValueInt() - countBeds();
        need[12] = shulkerNeed.getValueInt() - countShulkerBoxes();
    }

    private int countItem(Item item) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) count += stack.getCount();
        }
        if (mc.player.getOffHandStack().getItem() == item) count += mc.player.getOffHandStack().getCount();
        return count;
    }

    private int countBeds() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof net.minecraft.item.BlockItem bi && bi.getBlock() instanceof net.minecraft.block.BedBlock) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private int countShulkerBoxes() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof net.minecraft.item.BlockItem bi && bi.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock) {
                count += stack.getCount();
            }
        }
        ItemStack offhand = mc.player.getOffHandStack();
        if (offhand.getItem() instanceof net.minecraft.item.BlockItem bi && bi.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock) {
            count += offhand.getCount();
        }
        return count;
    }

    private int getNeedIndex(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.END_CRYSTAL && need[0] > 0) return 0;
        if (item == Items.EXPERIENCE_BOTTLE && need[1] > 0) return 1;
        if (item == Items.TOTEM_OF_UNDYING && need[2] > 0) return 2;
        if ((item == Items.ENCHANTED_GOLDEN_APPLE || item == Items.GOLDEN_APPLE) && need[3] > 0) return 3;
        if (item == Blocks.OBSIDIAN.asItem() && need[4] > 0) return 4;
        if (item == Blocks.COBWEB.asItem() && need[5] > 0) return 5;
        if (item == Blocks.GLOWSTONE.asItem() && need[6] > 0) return 6;
        if (item == Blocks.RESPAWN_ANCHOR.asItem() && need[7] > 0) return 7;
        if (item == Items.ENDER_PEARL && need[8] > 0) return 8;
        if ((item == Blocks.PISTON.asItem() || item == Blocks.STICKY_PISTON.asItem()) && need[9] > 0) return 9;
        if (item == Blocks.REDSTONE_BLOCK.asItem() && need[10] > 0) return 10;
        if (item instanceof net.minecraft.item.BlockItem bi && bi.getBlock() instanceof net.minecraft.block.BedBlock && need[11] > 0) return 11;
        if (item instanceof net.minecraft.item.BlockItem bi && bi.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock && need[12] > 0) return 12;
        return -1;
    }

    private boolean dec(int index, int value) {
        need[index] -= value;
        return true;
    }
}