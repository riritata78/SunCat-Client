package dev.suncat.mod.modules.impl.misc;

import dev.suncat.core.impl.KitManager;
import dev.suncat.api.events.eventbus.EventListener;
import dev.suncat.api.events.impl.TickEvent;
import dev.suncat.api.events.Event;
import dev.suncat.api.utils.math.Timer;
import dev.suncat.api.utils.player.InventoryUtil;
import dev.suncat.api.utils.world.BlockUtil;
import dev.suncat.mod.modules.Module;
import dev.suncat.mod.modules.impl.combat.AutoRegear;
import dev.suncat.mod.modules.impl.player.PacketMine;
import dev.suncat.mod.modules.settings.impl.BooleanSetting;
import dev.suncat.mod.modules.settings.impl.EnumSetting;
import dev.suncat.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.registry.Registries;

public class ChestStealer extends Module {
    public static ChestStealer INSTANCE;

    // Kit 设置
    private final EnumSetting<Mode> mode = add(new EnumSetting<Mode>("Mode", Mode.Steal));

    // 自动放置/打开/挖掘
    private final BooleanSetting place = add(new BooleanSetting("Place", true));
    private final BooleanSetting open = add(new BooleanSetting("Open", true));
    private final BooleanSetting mine = add(new BooleanSetting("Mine", true));
    private final BooleanSetting inventorySwap = add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting preferOpen = add(new BooleanSetting("PreferOpen", true));
    public final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    private final SliderSetting range = add(new SliderSetting("Range", 4.0f, 0.0f, 6f, .1));
    private final SliderSetting minRange = add(new SliderSetting("MinRange", 1.0f, 0.0f, 3f, .1));

    // 偷取设置
    private final BooleanSetting take = add(new BooleanSetting("Take", true));

    // 延迟和自动关闭
    private final SliderSetting clickDelay = add(new SliderSetting("ClickDelay", 100, 0, 1000, 1));
    private final BooleanSetting doubleClicks = add(new BooleanSetting("Double-Clicks", false));
    private final SliderSetting sequenceDelay = add(new SliderSetting("SequenceDelay", 50, 0, 1000, 1, () -> !doubleClicks.getValue()));
    private final BooleanSetting autoDisable = add(new BooleanSetting("AutoDisable", true));
    private final SliderSetting disableTime = add(new SliderSetting("DisableTime", 500, 0, 1000));

    private final Timer timer = new Timer();
    private final Timer disableTimer = new Timer();
    private final Timer clickTimer = new Timer();
    private final Timer sequenceTimer = new Timer();

    BlockPos placePos = null;
    BlockPos openPos = null;
    boolean opend = false;
    int sequenced = -1;

    // Kit 相关
    private final java.util.Map<String, Integer> kitItems = new java.util.HashMap<>();
    private boolean kitLoaded = false;

    public ChestStealer() {
        super("ChestStealer", "Steal items from containers automatically", Category.Misc);
        this.setChinese("箱子偷取");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        openPos = null;
        disableTimer.reset();
        placePos = null;
        kitLoaded = false;

        if (nullCheck()) {
            return;
        }

        // 加载 Kit
        loadKit();

        // 自动放置潜影盒
        int oldSlot = mc.player.getInventory().selectedSlot;
        if (this.place.getValue()) {
            double distance = 100;
            BlockPos bestPos = null;

            for (BlockPos pos : BlockUtil.getSphere((float) range.getValue())) {
                if (!mc.world.isAir(pos.up())) continue;
                if (preferOpen.getValue() && mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock) return;
                if (MathHelper.sqrt((float) mc.player.squaredDistanceTo(pos.toCenterPos())) < minRange.getValue()) continue;
                if (!BlockUtil.clientCanPlace(pos, false)
                        || !BlockUtil.isStrictDirection(pos.offset(Direction.DOWN), Direction.UP)
                        || !BlockUtil.canClick(pos.offset(Direction.DOWN))
                ) continue;
                if (bestPos == null || MathHelper.sqrt((float) mc.player.squaredDistanceTo(pos.toCenterPos())) < distance) {
                    distance = MathHelper.sqrt((float) mc.player.squaredDistanceTo(pos.toCenterPos()));
                    bestPos = pos;
                }
            }

            if (bestPos != null) {
                int shulkerSlot = findShulker();
                if (shulkerSlot == -1) {
                    return;
                }
                if (inventorySwap.getValue()) {
                    InventoryUtil.inventorySwap(shulkerSlot, oldSlot);
                    placeBlock(bestPos);
                    placePos = bestPos;
                    InventoryUtil.inventorySwap(shulkerSlot, oldSlot);
                } else {
                    InventoryUtil.switchToSlot(shulkerSlot);
                    placeBlock(bestPos);
                    placePos = bestPos;
                    InventoryUtil.switchToSlot(oldSlot);
                }
                timer.reset();
            }
        }
    }

    @Override
    public void onDisable() {
        opend = false;
        if (mine.getValue() && placePos != null) {
            mineBlock(placePos);
        }
        reset();
    }

    private void reset() {
        sequenced = -1;
        clickTimer.reset();
        sequenceTimer.reset();
    }

    private void loadKit() {
        kitItems.clear();
        kitLoaded = false;

        String kitName = AutoRegear.INSTANCE.currentKitName;
        if (kitName == null || kitName.isEmpty()) {
            return;
        }

        KitManager.Kit kit = KitManager.getKit(kitName);
        if (kit == null) {
            return;
        }

        for (int i = 0; i < kit.mainInventory.length; i++) {
            String itemId = kit.mainInventory[i];
            if (itemId != null && !itemId.isEmpty()) {
                kitItems.put(itemId, kit.mainInventoryCounts[i]);
            }
        }

        for (int i = 0; i < kit.armorInventory.length; i++) {
            String itemId = kit.armorInventory[i];
            if (itemId != null && !itemId.isEmpty()) {
                kitItems.put(itemId, kit.armorInventoryCounts[i]);
            }
        }

        if (kit.offHand != null && !kit.offHand.isEmpty()) {
            kitItems.put(kit.offHand, kit.offHandCount);
        }

        kitLoaded = true;
    }

    public int findShulker() {
        if (inventorySwap.getValue()) return InventoryUtil.findClassInventorySlot(ShulkerBoxBlock.class);
        return InventoryUtil.findClass(ShulkerBoxBlock.class);
    }

    private void placeBlock(BlockPos pos) {
        BlockUtil.clickBlock(pos.offset(Direction.DOWN), Direction.UP, rotate.getValue());
    }

    private void mineBlock(BlockPos pos) {
        if (mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock) {
            PacketMine.INSTANCE.mine(pos);
        }
    }

    @EventListener
    public void onTickPre(TickEvent event) {
        if (event.stage != Event.Stage.Pre) {
            return;
        }

        if (nullCheck()) {
            reset();
            return;
        }

        runClicks();

        // 检查是否在容器界面
        boolean inContainer = mc.currentScreen instanceof ShulkerBoxScreen || mc.currentScreen instanceof GenericContainerScreen;

        if (!inContainer) {
            if (opend) {
                opend = false;
                if (autoDisable.getValue()) disable2();
                if (mine.getValue()) {
                    if (openPos != null) {
                        if (mc.world.getBlockState(openPos).getBlock() instanceof ShulkerBoxBlock) {
                            mineBlock(openPos);
                        } else {
                            openPos = null;
                        }
                    }
                }
                return;
            }

            if (open.getValue()) {
                if (placePos != null && MathHelper.sqrt((float) mc.player.squaredDistanceTo(placePos.toCenterPos())) <= range.getValue()
                        && mc.world.isAir(placePos.up()) && (!timer.passedMs(500) || mc.world.getBlockState(placePos).getBlock() instanceof ShulkerBoxBlock)) {
                    if (mc.world.getBlockState(placePos).getBlock() instanceof ShulkerBoxBlock) {
                        openPos = placePos;
                        BlockUtil.clickBlock(placePos, BlockUtil.getClickSide(placePos), rotate.getValue());
                    }
                } else {
                    boolean found = false;
                    for (BlockPos pos : BlockUtil.getSphere((float) range.getValue())) {
                        if (!mc.world.isAir(pos.up())) continue;
                        if (mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock) {
                            openPos = pos;
                            BlockUtil.clickBlock(pos, BlockUtil.getClickSide(pos), rotate.getValue());
                            found = true;
                            break;
                        }
                    }
                    if (!found && autoDisable.getValue()) this.disable2();
                }
            } else if (!this.take.getValue()) {
                if (autoDisable.getValue()) this.disable2();
            }
            return;
        }

        opend = true;
        if (!this.take.getValue()) {
            if (autoDisable.getValue()) this.disable2();
            return;
        }

        boolean took = false;

        if (mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler shulker) {
            for (Slot slot : shulker.slots) {
                if (slot.id < 27 && !slot.getStack().isEmpty()) {
                    boolean shouldSteal = shouldStealItem(slot.getStack());
                    if (shouldSteal) {
                        mc.interactionManager.clickSlot(shulker.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                        took = true;
                    }
                }
            }
        } else if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler container) {
            for (Slot slot : container.slots) {
                if (slot.id < container.getInventory().size() && !slot.getStack().isEmpty()) {
                    boolean shouldSteal = shouldStealItem(slot.getStack());
                    if (shouldSteal) {
                        mc.interactionManager.clickSlot(container.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                        took = true;
                    }
                }
            }
        }

        if (autoDisable.getValue() && !took) this.disable2();
    }

    private boolean shouldStealItem(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // Kit 模式：使用 kit load 命令加载的 kit
        if (kitLoaded) {
            if (mode.getValue() == Mode.Steal) {
                // 偷取模式：只偷取 Kit 中有的物品
                Identifier itemId = Registries.ITEM.getId(stack.getItem());
                String idString = itemId.toString();

                if (!kitItems.containsKey(idString)) {
                    return false;
                }

                // 检查数量是否需要
                int kitCount = kitItems.get(idString);
                int playerCount = getPlayerItemCount(stack.getItem());
                return playerCount < kitCount;

            } else if (mode.getValue() == Mode.Drop) {
                // 丢弃模式：丢弃不在 Kit 中的物品
                Identifier itemId = Registries.ITEM.getId(stack.getItem());
                String idString = itemId.toString();
                return !kitItems.containsKey(idString);
            }
        }

        // 没有加载 Kit：全部偷取
        return true;
    }

    private int getPlayerItemCount(Item item) {
        int count = 0;
        Identifier itemId = Registries.ITEM.getId(item);
        String idString = itemId.toString();

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).toString().equals(idString)) {
                count += stack.getCount();
            }
        }

        ItemStack offhand = mc.player.getInventory().offHand.get(0);
        if (!offhand.isEmpty() && Registries.ITEM.getId(offhand.getItem()).toString().equals(idString)) {
            count += offhand.getCount();
        }

        return count;
    }

    private void disable2() {
        if (disableTimer.passedMs(disableTime.getValueInt()))
            disable();
    }

    public void runClicks() {
        if (mc.player.currentScreenHandler == null) {
            return;
        }

        int syncId = mc.player.currentScreenHandler.syncId;

        if (sequenced != -1 && sequenceTimer.passed(sequenceDelay.getValueInt())) {
            mc.interactionManager.clickSlot(syncId, sequenced, 0, SlotActionType.PICKUP, mc.player);
            sequenced = -1;
            return;
        }

        if (clickTimer.passed(clickDelay.getValueInt()) && sequenced == -1) {
            // Clicks are handled by the container loop above
        }
    }

    @Override
    public String getInfo() {
        String kitName = AutoRegear.INSTANCE.currentKitName;
        return (kitName != null ? kitName : "None") + " | " + mode.getValue().name();
    }

    public enum Mode {
        Steal,  // 只偷取 Kit 中定义的物品
        Drop    // 丢弃不在 Kit 中的物品
    }
}
