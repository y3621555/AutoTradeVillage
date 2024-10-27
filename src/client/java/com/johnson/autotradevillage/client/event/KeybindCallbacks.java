package com.johnson.autotradevillage.client.event;

import com.johnson.autotradevillage.client.AutoTrade;

import com.johnson.autotradevillage.client.config.Configs;
import com.johnson.autotradevillage.client.config.Hotkeys;
import com.johnson.autotradevillage.client.gui.GuiConfigs;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.InfoUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

public class KeybindCallbacks implements IHotkeyCallback, IClientTickHandler {

    private static final KeybindCallbacks INSTANCE = new KeybindCallbacks();

    private Vector<Entity> villagersInRange = new Vector<Entity>();
    private int villagerActive = 0;

    private boolean state = false;
    private boolean inputInRange = false;
    private boolean inputOpened = false;
    private boolean outputInRange = false;
    private boolean outputOpened = false;
    private int tickCount = 0;
    private int voidDelay = 0;
    private int containerDelay = 0;
    private int screenOpened = 0;

    private long lastCommandTime = 0;
    private static final long COMMAND_COOLDOWN = 5000; // 5秒冷卻時間
    private long lastWarpTime = 0;
    private static final long WARP_COOLDOWN = 5000; // 5秒冷卻時間
    private int replenishAttempts = 0;
    private static final int MAX_REPLENISH_ATTEMPTS = 3; // 最大重試次數

    private int containerOpenAttempts = 0;
    private static final int MAX_CONTAINER_OPEN_ATTEMPTS = 5;

    private Queue<Entity> villagerQueue = new LinkedList<>();
    private Entity currentVillager = null;
    private int tradeAttempts = 0;
    private static final int MAX_TRADE_ATTEMPTS = 50; // 最大交易嘗試次數

    //村民類
    private Set<Entity> tradedVillagers = new HashSet<>();

    private enum State {
        閒置, 準備補充物品, 等待傳送完成, 檢查容器, 補充物品中, 等待補充完成, 檢查補充結果,
        準備返回, 交易中, 準備存放物品, 等待存放傳送完成, 檢查存放容器, 存放物品中, 等待存放完成, 檢查存放結果,
        搜索村民, 移動到村民
    }

    private State currentState = State.閒置;

    public static KeybindCallbacks getInstance() {
        return INSTANCE;
    }

    private KeybindCallbacks() {
    }

    public void setCallbacks() {
        for (ConfigHotkey hotkey : Hotkeys.HOTKEY_LIST) {
            hotkey.getKeybind().setCallback(this);
        }
    }

    public boolean functionalityEnabled() {
        return Configs.Generic.ENABLED.getBooleanValue();
    }

    @Override
    public boolean onKeyAction(KeyAction action, IKeybind key) {
        boolean cancel = this.onKeyActionImpl(action, key);
        return cancel;
    }

    private void processOutput(ScreenHandler handler) {
        String itemToPlace = Configs.Generic.ENABLE_BUY.getBooleanValue()
                ? Configs.Generic.BUY_ITEM.getStringValue()
                : "minecraft:emerald";

        int itemsPlaced = 0;
        for (int i = 0; i < handler.slots.size(); i++) {
            if (handler.getSlot(i).inventory instanceof PlayerInventory) {
                ItemStack stack = handler.getSlot(i).getStack();
                if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemToPlace)) {
                    try {
                        MinecraftClient.getInstance().interactionManager.clickSlot(
                                handler.syncId, i, 0, SlotActionType.QUICK_MOVE, MinecraftClient.getInstance().player);
                        itemsPlaced += stack.getCount();
                    } catch (Exception e) {
                        System.out.println("Error placing items: " + e.toString());
                    }
                }
            }
        }

        InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO, "已存放 " + itemsPlaced + " 個 " + itemToPlace);
        currentState = State.等待存放完成;
        //containerDelay = 10; // 設置一個短暫的延遲，給予足夠的時間來處理物品轉移
    }

    private void processInput(ScreenHandler handler) {
        inputOpened = false;

        String itemToTake = Configs.Generic.ENABLE_SELL.getBooleanValue()
                ? Configs.Generic.SELL_ITEM.getStringValue()
                : "minecraft:emerald";

        int desiredAmount = Configs.Generic.MAX_INPUT_ITEMS.getIntegerValue() * 64;
        int inputCount = 0;

        for (int i = 0; i < handler.slots.size(); i++) {
            if (handler.getSlot(i).inventory instanceof PlayerInventory == false) {
                ItemStack stack = handler.getSlot(i).getStack();
                if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemToTake)) {
                    if (inputCount < desiredAmount) {
                        int amountToMove = Math.min(stack.getCount(), desiredAmount - inputCount);
                        inputCount += amountToMove;
                        try {
                            MinecraftClient.getInstance().interactionManager.clickSlot(
                                    handler.syncId, i, 0,
                                    amountToMove == stack.getCount() ? SlotActionType.QUICK_MOVE : SlotActionType.PICKUP,
                                    MinecraftClient.getInstance().player
                            );
                            if (amountToMove != stack.getCount()) {
                                MinecraftClient.getInstance().interactionManager.clickSlot(
                                        handler.syncId, -999, 0, SlotActionType.PICKUP,
                                        MinecraftClient.getInstance().player
                                );
                            }
                        } catch (Exception e) {
                            System.out.println("Error moving items: " + e.toString());
                        }
                        if (inputCount >= desiredAmount) break;
                    }
                }
            }
        }

        InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO, "已補充 " + inputCount + " 個 " + itemToTake);
        currentState = State.等待補充完成;
    }

    private boolean onKeyActionImpl(KeyAction action, IKeybind key) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.world == null) {
            return false;
        }

        if (key == Hotkeys.TOGGLE_KEY.getKeybind()) {
            Configs.Generic.ENABLED.toggleBooleanValue();
            String msg = this.functionalityEnabled()
                    ? "自動交易(開)"
                    : "自動交易(關)";
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO, msg);
            if (this.functionalityEnabled()) {
                AutoTrade.sold = 0;
                AutoTrade.bought = 0;
                AutoTrade.sessionStart = System.currentTimeMillis() / 1000L;
            }
        } else if (key == Hotkeys.OPEN_GUI_SETTINGS.getKeybind()) {
            GuiBase.openGui(new GuiConfigs());
            return true;
        } else if (key == Hotkeys.SET_INPUT_KEY.getKeybind()) {
            HitResult result = mc.player.raycast(20.0D, 0.0F, false);
            if (result.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) result;
                Configs.Generic.INPUT_CONTAINER_X.setIntegerValue(blockHit.getBlockPos().getX());
                Configs.Generic.INPUT_CONTAINER_Y.setIntegerValue(blockHit.getBlockPos().getY());
                Configs.Generic.INPUT_CONTAINER_Z.setIntegerValue(blockHit.getBlockPos().getZ());
                InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO, "輸入容器設置",
                        blockHit.getBlockPos().getX(), blockHit.getBlockPos().getY(), blockHit.getBlockPos().getZ());
            }
        } else if (key == Hotkeys.SET_OUTPUT_KEY.getKeybind()) {
            HitResult result = mc.player.raycast(20.0D, 0.0F, false);
            if (result.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) result;
                Configs.Generic.OUTPUT_CONTAINER_X.setIntegerValue(blockHit.getBlockPos().getX());
                Configs.Generic.OUTPUT_CONTAINER_Y.setIntegerValue(blockHit.getBlockPos().getY());
                Configs.Generic.OUTPUT_CONTAINER_Z.setIntegerValue(blockHit.getBlockPos().getZ());
                InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO, "輸出容器設置",
                        blockHit.getBlockPos().getX(), blockHit.getBlockPos().getY(), blockHit.getBlockPos().getZ());
            }
        } else if (key == Hotkeys.SET_BUY_KEY.getKeybind()) {
            String buyItem = Registries.ITEM.getId(mc.player.getInventory().getMainHandStack().getItem()).toString();
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO, "購買物品設置為", buyItem);
            Configs.Generic.BUY_ITEM.setValueFromString(buyItem);
        } else if (key == Hotkeys.SET_SELL_KEY.getKeybind()) {
            String sellItem = Registries.ITEM.getId(mc.player.getInventory().getMainHandStack().getItem()).toString();
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO, "販賣物品設置為", sellItem);
            Configs.Generic.SELL_ITEM.setValueFromString(sellItem);
        }

        return false;
    }

    @Override
    public void onClientTick(MinecraftClient mc) {
        if (voidDelay > 0) {
            if (Configs.Generic.VOID_TRADING_DELAY_AFTER_TELEPORT.getBooleanValue()) {
                boolean found = false;
                for (Entity entity : mc.player.clientWorld.getEntities()) {
                    if (entity.getId() == villagerActive) {
                        found = true;
                    }
                }
                if (!found) {
                    voidDelay--;
                }
            } else {
                voidDelay--;
            }
            return;
        }

        if (containerDelay > 0) {
            containerDelay--;
        }

        if (this.functionalityEnabled() == false || mc.player == null) {
            return;
        }

        switch (currentState) {
            case 閒置:
                if (needToReplenishSellItem(mc.player)) {
                    currentState = State.準備補充物品;
                    replenishAttempts = 0;
                } else if (needToStoreBuyItem(mc.player)) {
                    currentState = State.存放物品中;
                    executeCommand(mc, Configs.Generic.OUTPUT_CONTAINER_WARP.getStringValue());
                } else {
                    currentState = State.交易中;
                }
                break;
            case 準備補充物品:
                if (System.currentTimeMillis() - lastWarpTime > WARP_COOLDOWN) {
                    executeCommand(mc, Configs.Generic.INPUT_CONTAINER_WARP.getStringValue());
                    currentState = State.等待傳送完成;
                    lastWarpTime = System.currentTimeMillis();
                }
                break;
            case 等待傳送完成:
                if (System.currentTimeMillis() - lastWarpTime > 2000) { // 等待2秒確保傳送完成
                    currentState = State.檢查容器;
                }
                break;
            case 檢查容器:
                if (isNearInputContainer(mc)) {
                    currentState = State.補充物品中;
                    openInputContainer(mc);
                } else {
                    InfoUtils.showGuiOrInGameMessage(Message.MessageType.ERROR, "無法找到輸入容器，重試中...");
                    currentState = State.準備補充物品;
                    replenishAttempts++;
                    if (replenishAttempts >= MAX_REPLENISH_ATTEMPTS) {
                        InfoUtils.showGuiOrInGameMessage(Message.MessageType.ERROR, "達到最大重試次數，返回交易");
                        currentState = State.準備返回;
                    }
                }
                break;
            case 補充物品中:
                inputOpened = true;
                if (GuiUtils.getCurrentScreen() instanceof GenericContainerScreen) {
                    GenericContainerScreen screen = (GenericContainerScreen) GuiUtils.getCurrentScreen();
                    GenericContainerScreenHandler handler = screen.getScreenHandler();
                    if ((containerDelay == 0) && inputOpened) {
                        processInput(handler);
                        screen.close();
                    }
                }
                else {
                    InfoUtils.showGuiOrInGameMessage(Message.MessageType.ERROR, "無法打開容器，重試中...");
                    currentState = State.準備補充物品;
                    containerOpenAttempts = 0;
                }

                if (GuiUtils.getCurrentScreen() instanceof ShulkerBoxScreen) {
                    ShulkerBoxScreen screen = (ShulkerBoxScreen) GuiUtils.getCurrentScreen();
                    ShulkerBoxScreenHandler handler = screen.getScreenHandler();
                    if ((containerDelay == 0) && inputOpened) {
                        processInput(handler);
                        screen.close();
                    }
                }
                break;

            case 等待補充完成:
                if (containerDelay > 0) {
                    containerDelay--;
                } else {
                    if (GuiUtils.getCurrentScreen() instanceof GenericContainerScreen) {
                        GuiUtils.getCurrentScreen().close();
                    }
                    currentState = State.檢查補充結果;
                }
                break;
            case 檢查補充結果:
                if (hasEnoughSellItems(mc.player)) {
                    currentState = State.準備返回;
                } else {
                    InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO, "物品不足，重新嘗試補充");
                    currentState = State.準備補充物品;
                    replenishAttempts++;
                    if (replenishAttempts >= MAX_REPLENISH_ATTEMPTS) {
                        InfoUtils.showGuiOrInGameMessage(Message.MessageType.ERROR, "達到最大重試次數，返回交易");
                        currentState = State.準備返回;
                    }
                }
                break;
            case 準備存放物品:
                if (System.currentTimeMillis() - lastWarpTime > WARP_COOLDOWN) {
                    executeCommand(mc, Configs.Generic.OUTPUT_CONTAINER_WARP.getStringValue());
                    currentState = State.等待存放傳送完成;
                    lastWarpTime = System.currentTimeMillis();
                }
                break;
            case 等待存放傳送完成:
                if (System.currentTimeMillis() - lastWarpTime > 2000) { // 等待2秒確保傳送完成
                    currentState = State.檢查存放容器;
                }
                break;
            case 檢查存放容器:
                if (isNearOutputContainer(mc)) {
                    currentState = State.存放物品中;
                    openOutputContainer(mc);
                } else {
                    InfoUtils.showGuiOrInGameMessage(Message.MessageType.ERROR, "無法找到輸出容器，重試中...");
                    currentState = State.準備存放物品;
                    replenishAttempts++;
                    if (replenishAttempts >= MAX_REPLENISH_ATTEMPTS) {
                        InfoUtils.showGuiOrInGameMessage(Message.MessageType.ERROR, "達到最大重試次數，返回交易");
                        currentState = State.準備返回;
                    }
                }
                break;
            case 存放物品中:
                outputOpened = true;
                if (GuiUtils.getCurrentScreen() instanceof GenericContainerScreen) {
                    GenericContainerScreen screen = (GenericContainerScreen) GuiUtils.getCurrentScreen();
                    GenericContainerScreenHandler handler = screen.getScreenHandler();
                    if ((containerDelay == 0) && outputOpened) {
                        processOutput(handler);
                        screen.close();
                    }
                }

                if (GuiUtils.getCurrentScreen() instanceof ShulkerBoxScreen) {
                    ShulkerBoxScreen screen = (ShulkerBoxScreen) GuiUtils.getCurrentScreen();
                    ShulkerBoxScreenHandler handler = screen.getScreenHandler();
                    if ((containerDelay == 0) && outputOpened) {
                        processOutput(handler);
                        screen.close();
                    }
                }
                break;
            case 等待存放完成:
                if (containerDelay > 0) {
                    containerDelay--;
                } else {
                    if (GuiUtils.getCurrentScreen() instanceof GenericContainerScreen ||
                            GuiUtils.getCurrentScreen() instanceof ShulkerBoxScreen) {
                        GuiUtils.getCurrentScreen().close();
                    }
                    currentState = State.檢查存放結果;
                }
                break;
            case 檢查存放結果:
                if (hasStoredEnoughItems(mc.player)) {
                    currentState = State.準備返回;
                } else {
                    InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO, "存放不足，重新嘗試存放");
                    currentState = State.準備存放物品;
                    replenishAttempts++;
                   /*if (replenishAttempts >= MAX_REPLENISH_ATTEMPTS) {
                        InfoUtils.showGuiOrInGameMessage(Message.MessageType.ERROR, "達到最大重試次數，返回交易");
                        currentState = State.準備返回;
                    }*/
                }
                break;
            case 準備返回:
                if (System.currentTimeMillis() - lastWarpTime > WARP_COOLDOWN) {
                    executeCommand(mc, "back");
                    currentState = State.交易中;
                    lastWarpTime = System.currentTimeMillis();
                }
                break;
            case 交易中:
                if (needToReplenishSellItem(mc.player)) {
                    currentState = State.準備補充物品;
                    replenishAttempts = 0;
                    break;
                } else if (needToStoreBuyItem(mc.player)) {
                    currentState = State.準備存放物品;
                    replenishAttempts = 0;
                    break;
                }

                if (currentVillager == null) {
                    if (villagerQueue.isEmpty()) {
                        List<Entity> nearbyVillagers = findNearbyTradeableVillagers(mc);
                        if (!nearbyVillagers.isEmpty()) {
                            currentVillager = nearbyVillagers.get(0);
                            villagerQueue.addAll(nearbyVillagers.subList(1, Math.min(nearbyVillagers.size(), 5)));
                        }
                    } else {
                        currentVillager = villagerQueue.poll();
                    }
                    tradeAttempts = 0;
                }

                if (currentVillager != null) {
                    if (GuiUtils.getCurrentScreen() instanceof MerchantScreen) {
                        performTrading((MerchantScreen) GuiUtils.getCurrentScreen());
                    } else if (tradeAttempts < MAX_TRADE_ATTEMPTS) {
                        if (moveTowardsVillager(mc, currentVillager)) {
                            mc.interactionManager.interactEntity(mc.player, currentVillager, Hand.MAIN_HAND);
                            tradeAttempts++;
                        }
                    } else {
                        tradedVillagers.add(currentVillager);
                        currentVillager = null;
                        tradeAttempts = 0;
                    }
                } else {
                    currentState = State.搜索村民;
                }
                break;
            case 移動到村民:
                Entity nearestVillager = findNearestVillager(mc);
                if (nearestVillager != null) {
                    if (moveTowardsVillager(mc, nearestVillager)) {
                        mc.interactionManager.interactEntity(mc.player, nearestVillager, Hand.MAIN_HAND);
                        voidDelay = Configs.Generic.VOID_TRADING_DELAY.getIntegerValue();
                        villagerActive = nearestVillager.getId();
                        state = false;
                    }
                } else {
                    currentState = State.搜索村民;
                }
                break;
        }

        if (Configs.Generic.GLASS_BLOCK.getBooleanValue()) {
            int playerX = (int) mc.player.getPos().getX();
            int playerZ = (int) mc.player.getPos().getZ();
            int playerY = (int) mc.player.getPos().getY();

            int selectorOffset = Configs.Generic.SELECTOR_OFFSET.getIntegerValue();
            int absSelectorOffset = Math.abs(selectorOffset);

            for (int x = playerX - (absSelectorOffset + 3); x < playerX + (absSelectorOffset + 3); x += 1) {
                for (int z = playerZ - (absSelectorOffset + 3); z < playerZ + (absSelectorOffset + 3); z += 1) {
                    for (int y = playerY - (absSelectorOffset + 3); y < playerY + (absSelectorOffset + 3); y += 1) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (mc.player.clientWorld.getBlockState(pos).isOf(Blocks.RED_STAINED_GLASS)) {
                            if ((x != Configs.Generic.INPUT_CONTAINER_X.getIntegerValue())
                                    || ((y - selectorOffset) != Configs.Generic.INPUT_CONTAINER_Y.getIntegerValue())
                                    || (z != Configs.Generic.INPUT_CONTAINER_Z.getIntegerValue())) {
                                Configs.Generic.INPUT_CONTAINER_X.setIntegerValue(x);
                                Configs.Generic.INPUT_CONTAINER_Y.setIntegerValue(y - selectorOffset);
                                Configs.Generic.INPUT_CONTAINER_Z.setIntegerValue(z);
                                InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO,
                                        "autotrade.message.input_container_set", x, y - selectorOffset, z);
                            }
                            break;
                        }
                        if (mc.player.clientWorld.getBlockState(pos).isOf(Blocks.BLUE_STAINED_GLASS)) {
                            if ((x != Configs.Generic.OUTPUT_CONTAINER_X.getIntegerValue())
                                    || ((y - selectorOffset) != Configs.Generic.OUTPUT_CONTAINER_Y.getIntegerValue())
                                    || (z != Configs.Generic.OUTPUT_CONTAINER_Z.getIntegerValue())) {
                                Configs.Generic.OUTPUT_CONTAINER_X.setIntegerValue(x);
                                Configs.Generic.OUTPUT_CONTAINER_Y.setIntegerValue(y - selectorOffset);
                                Configs.Generic.OUTPUT_CONTAINER_Z.setIntegerValue(z);
                                InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO,
                                        "autotrade.message.output_container_set", x, y - selectorOffset, z);
                            }
                            break;
                        }
                    }
                }
            }
        }

        if (Configs.Generic.ITEM_FRAME.getBooleanValue()) {
            for (ItemFrameEntity entity : mc.player.clientWorld.getEntitiesByClass(ItemFrameEntity.class,
                    new Box(mc.player.getPos().getX() - 3, mc.player.getPos().getY() - 3, mc.player.getPos().getZ() - 3,
                            mc.player.getPos().getX() + 3, mc.player.getPos().getY() + 3,
                            mc.player.getPos().getZ() + 3),
                    EntityPredicates.VALID_ENTITY)) {
                ItemStack stack = entity.getHeldItemStack();
                ComponentMap components = stack.getComponents();

                if (components.contains(DataComponentTypes.CUSTOM_NAME)) {
                    Text customName = components.get(DataComponentTypes.CUSTOM_NAME);
                    String nameString = customName.getString();

                    if (nameString.equals("\"sell\"")) {
                        String sellItem = Registries.ITEM.getId(stack.getItem()).toString();
                        if (!Configs.Generic.SELL_ITEM.getStringValue().equals(sellItem)) {
                            InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO,
                                    "autotrade.message.sell_item_set", sellItem);
                            Configs.Generic.SELL_ITEM.setValueFromString(sellItem);
                            break;
                        }
                    }
                    if (nameString.equals("\"buy\"")) {
                        String buyItem = Registries.ITEM.getId(stack.getItem()).toString();
                        if (!Configs.Generic.BUY_ITEM.getStringValue().equals(buyItem)) {
                            InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO,
                                    "autotrade.message.buy_item_set", buyItem);
                            Configs.Generic.BUY_ITEM.setValueFromString(buyItem);
                            break;
                        }
                    }
                }
            }
        }

        if (GuiUtils.getCurrentScreen() instanceof MerchantScreen) {
            MerchantScreen screen = (MerchantScreen) GuiUtils.getCurrentScreen();
            if (state == false) {
                String sellItemStr = Configs.Generic.SELL_ITEM.getStringValue();
                String buyItemStr = Configs.Generic.BUY_ITEM.getStringValue();
                state = true;
                MerchantScreenHandler handler = screen.getScreenHandler();
                TradeOfferList offers = handler.getRecipes();
                for (int i = 0; i < offers.size(); i++) {
                    TradeOffer offer = offers.get(i);
                    ItemStack sellItem = offer.getSellItem();
                    ItemStack buyItem = offer.getFirstBuyItem().itemStack();
                    String sellId = Registries.ITEM.getId(sellItem.getItem()).toString();
                    String buyId = Registries.ITEM.getId(buyItem.getItem()).toString();

                    if (sellId.equals(buyItemStr) && Configs.Generic.ENABLE_BUY.getBooleanValue()
                            && buyItem.getCount() <= Configs.Generic.BUY_LIMIT.getIntegerValue()) {
                        Slot slot = handler.getSlot(2);
                        handler.switchTo(i);
                        handler.setRecipeIndex(i);
                        mc.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(i));
                        AutoTrade.sold += offer.getMaxUses();
                        try {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE,
                                    mc.player);
                        } catch (Exception e) {
                            System.out.println("err " + e.toString());
                        }
                    }
                    if (buyId.equals(sellItemStr) && Configs.Generic.ENABLE_SELL.getBooleanValue()
                            && buyItem.getCount() <= Configs.Generic.SELL_LIMIT.getIntegerValue()) {
                        Slot slot = handler.getSlot(2);
                        handler.switchTo(i);
                        handler.setRecipeIndex(i);
                        AutoTrade.bought += offer.getMaxUses();
                        mc.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(i));
                        try {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE,
                                    mc.player);
                        } catch (Exception e) {
                            System.out.println("err " + e.toString());
                        }
                    }
                }
            }
            screen.close();
            inputInRange = false;
            outputInRange = false;
            return;
        }

        if (GuiUtils.getCurrentScreen() instanceof ShulkerBoxScreen) {
            ShulkerBoxScreen screen = (ShulkerBoxScreen) GuiUtils.getCurrentScreen();
            ShulkerBoxScreenHandler handler = screen.getScreenHandler();
            if ((containerDelay == 0) && inputOpened) {
                processInput(handler);
                screen.close();
            }
            if ((containerDelay == 0) && outputOpened) {
                processOutput(handler);
                screen.close();
            }
        }
        if (GuiUtils.getCurrentScreen() instanceof GenericContainerScreen) {
            GenericContainerScreen screen = (GenericContainerScreen) GuiUtils.getCurrentScreen();
            GenericContainerScreenHandler handler = screen.getScreenHandler();
            if ((containerDelay == 0) && inputOpened) {
                processInput(handler);
                screen.close();
            }
            if ((containerDelay == 0) && outputOpened) {
                processOutput(handler);
                screen.close();
            }
        }


        tickCount++;
        if (tickCount > 200) {
            tickCount = 0;
            villagersInRange = new Vector<Entity>();
            inputInRange = false;
            outputInRange = false;
            if (GuiUtils.getCurrentScreen() instanceof MerchantScreen) {
                GuiUtils.getCurrentScreen().close();
            }
            if (GuiUtils.getCurrentScreen() instanceof ShulkerBoxScreen) {
                GuiUtils.getCurrentScreen().close();
            }
            if (GuiUtils.getCurrentScreen() instanceof GenericContainerScreen) {
                GuiUtils.getCurrentScreen().close();
            }
        }
    }


    private boolean isNearOutputContainer(MinecraftClient mc) {
        BlockPos output = new BlockPos(Configs.Generic.OUTPUT_CONTAINER_X.getIntegerValue(),
                Configs.Generic.OUTPUT_CONTAINER_Y.getIntegerValue(),
                Configs.Generic.OUTPUT_CONTAINER_Z.getIntegerValue());
        return mc.player.getBlockPos().isWithinDistance(output, 5);
    }

    private void openOutputContainer(MinecraftClient mc) {
        BlockPos output = new BlockPos(Configs.Generic.OUTPUT_CONTAINER_X.getIntegerValue(),
                Configs.Generic.OUTPUT_CONTAINER_Y.getIntegerValue(),
                Configs.Generic.OUTPUT_CONTAINER_Z.getIntegerValue());
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new BlockHitResult(output.toCenterPos(), Direction.UP, output, false));
    }

    private boolean hasStoredEnoughItems(ClientPlayerEntity player) {
        String itemToCheck = Configs.Generic.ENABLE_BUY.getBooleanValue()
                ? Configs.Generic.BUY_ITEM.getStringValue()
                : "minecraft:emerald";
        int count = countItemInInventory(player, itemToCheck);
        return count <= (9 * 64); // 假設我們想要存放到剩下9組以下
    }


    private boolean isNearInputContainer(MinecraftClient mc) {
        BlockPos input = new BlockPos(Configs.Generic.INPUT_CONTAINER_X.getIntegerValue(),
                Configs.Generic.INPUT_CONTAINER_Y.getIntegerValue(),
                Configs.Generic.INPUT_CONTAINER_Z.getIntegerValue());
        return mc.player.getBlockPos().isWithinDistance(input, 5);
    }

    private void openInputContainer(MinecraftClient mc) {
        BlockPos input = new BlockPos(Configs.Generic.INPUT_CONTAINER_X.getIntegerValue(),
                Configs.Generic.INPUT_CONTAINER_Y.getIntegerValue(),
                Configs.Generic.INPUT_CONTAINER_Z.getIntegerValue());
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new BlockHitResult(input.toCenterPos(), Direction.UP, input, false));
    }

    private boolean hasEnoughSellItems(ClientPlayerEntity player) {
        String itemToCheck = Configs.Generic.ENABLE_SELL.getBooleanValue()
                ? Configs.Generic.SELL_ITEM.getStringValue()
                : "minecraft:emerald";
        int count = countItemInInventory(player, itemToCheck);
        return count >= 64; // 或者您想要的其他閾值
    }

    //檢查補充物品

    private boolean needToReplenishSellItem(ClientPlayerEntity player) {
        String itemToCheck = Configs.Generic.ENABLE_SELL.getBooleanValue()
                ? Configs.Generic.SELL_ITEM.getStringValue()
                : "minecraft:emerald";
        int count = countItemInInventory(player, itemToCheck);
        return count < 64;
    }

    //檢查放入物品
    private boolean needToStoreBuyItem(ClientPlayerEntity player) {
        String itemToCheck = Configs.Generic.ENABLE_BUY.getBooleanValue()
                ? Configs.Generic.BUY_ITEM.getStringValue()
                : "minecraft:emerald";
        int count = countItemInInventory(player, itemToCheck);
        return count > (9 * 64);
    }

    private int countItemInInventory(ClientPlayerEntity player, String itemId) {
        int count = 0;
        for (ItemStack stack : player.getInventory().main) {
            if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void executeCommand(MinecraftClient mc, String command) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCommandTime > COMMAND_COOLDOWN) {
            if (command.startsWith("/")) {
                command = command.substring(1); // 移除開頭的斜槓，如果有的話
            }
            mc.player.networkHandler.sendCommand(command);
            lastCommandTime = currentTime;
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO, "執行指令: /" + command);
        } else {
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.WARNING, "指令冷卻中，請稍後再試");
        }
    }

    private void executeWarpCommand(MinecraftClient mc, String command) {
        executeCommand(mc, command);
    }

    private void executeBackCommand(MinecraftClient mc) {
        executeCommand(mc, "back");
    }


    private void handleContainerInteraction(MinecraftClient mc) {
        BlockPos containerPos = (currentState == State.補充物品中) ?
                new BlockPos(Configs.Generic.INPUT_CONTAINER_X.getIntegerValue(),
                        Configs.Generic.INPUT_CONTAINER_Y.getIntegerValue(),
                        Configs.Generic.INPUT_CONTAINER_Z.getIntegerValue()) :
                new BlockPos(Configs.Generic.OUTPUT_CONTAINER_X.getIntegerValue(),
                        Configs.Generic.OUTPUT_CONTAINER_Y.getIntegerValue(),
                        Configs.Generic.OUTPUT_CONTAINER_Z.getIntegerValue());

        if (mc.player.squaredDistanceTo(containerPos.getX() + 0.5, containerPos.getY() + 0.5, containerPos.getZ() + 0.5) <= 64) {
            mc.interactionManager.interactBlock(
                    mc.player,
                    mc.player.getActiveHand(),
                    new BlockHitResult(containerPos.toCenterPos(), Direction.UP, containerPos, false)
            );

            containerDelay = Configs.Generic.CONTAINER_CLOSE_DELAY.getIntegerValue();
            if (currentState == State.補充物品中) {
                inputOpened = true;
            } else {
                outputOpened = true;
            }
        } else {
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.ERROR, "無法到達容器位置,返回交易");
            currentState = State.閒置;
        }
    }

    private void handleNormalTrading(MinecraftClient mc) {
        boolean found = false;
        Vector<Entity> newVillagersInRange = new Vector<Entity>(villagersInRange);

        for (Entity entity : mc.player.clientWorld.getEntities()) {
            if ((entity instanceof VillagerEntity || entity instanceof WanderingTraderEntity)
                    && !tradedVillagers.contains(entity)) {
                if (entity.getPos().distanceTo(mc.player.getPos()) < 2.5f) {
                    if (!found && !newVillagersInRange.contains(entity)) {
                        found = true;
                        newVillagersInRange.add(entity);
                        mc.interactionManager.interactEntity(mc.player, entity, Hand.MAIN_HAND);
                        voidDelay = Configs.Generic.VOID_TRADING_DELAY.getIntegerValue();
                        villagerActive = entity.getId();
                        state = false;
                        break;
                    }
                }
            }
        }

        villagersInRange = newVillagersInRange.stream()
                .filter(entity -> entity.getPos().distanceTo(mc.player.getPos()) < 4)
                .collect(Collectors.toCollection(Vector::new));

        if (found) {
            return;
        }

        handleContainers(mc);

        if (GuiUtils.getCurrentScreen() instanceof MerchantScreen) {
            performTrading((MerchantScreen) GuiUtils.getCurrentScreen());
        }
    }

    private void handleContainers(MinecraftClient mc) {
        BlockPos input = new BlockPos(Configs.Generic.INPUT_CONTAINER_X.getIntegerValue(),
                Configs.Generic.INPUT_CONTAINER_Y.getIntegerValue(),
                Configs.Generic.INPUT_CONTAINER_Z.getIntegerValue());

        BlockPos output = new BlockPos(Configs.Generic.OUTPUT_CONTAINER_X.getIntegerValue(),
                Configs.Generic.OUTPUT_CONTAINER_Y.getIntegerValue(),
                Configs.Generic.OUTPUT_CONTAINER_Z.getIntegerValue());

        if ((input.toCenterPos().distanceTo(mc.player.getPos()) < 4) && !inputInRange) {
            inputInRange = true;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(input.toCenterPos(), Direction.UP, input, false));
            containerDelay = Configs.Generic.CONTAINER_CLOSE_DELAY.getIntegerValue();
            inputOpened = true;
        } else if ((output.toCenterPos().distanceTo(mc.player.getPos()) < 4) && !outputInRange) {
            outputInRange = true;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(output.toCenterPos(), Direction.UP, output, false));
            containerDelay = Configs.Generic.CONTAINER_CLOSE_DELAY.getIntegerValue();
            outputOpened = true;
        } else {
            if (input.toCenterPos().distanceTo(mc.player.getPos()) > 5) {
                inputOpened = false;
                inputInRange = false;
            }
            if (output.toCenterPos().distanceTo(mc.player.getPos()) > 5) {
                outputOpened = false;
                outputInRange = false;
            }
        }
    }

    /*private void performTrading(MerchantScreen screen) {
        MerchantScreenHandler handler = screen.getScreenHandler();
        TradeOfferList offers = handler.getRecipes();

        String sellItemStr = Configs.Generic.SELL_ITEM.getStringValue();
        String buyItemStr = Configs.Generic.BUY_ITEM.getStringValue();

        boolean traded = false;
        boolean targetTradeLocked = true;  // 假設目標交易最初是鎖定的

        for (int i = 0; i < offers.size(); i++) {
            TradeOffer offer = offers.get(i);
            ItemStack sellItem = offer.getSellItem();
            ItemStack buyItem = offer.getFirstBuyItem().itemStack();
            String sellId = Registries.ITEM.getId(sellItem.getItem()).toString();
            String buyId = Registries.ITEM.getId(buyItem.getItem()).toString();

            boolean isTargetTrade = (sellId.equals(buyItemStr) && Configs.Generic.ENABLE_BUY.getBooleanValue()) ||
                    (buyId.equals(sellItemStr) && Configs.Generic.ENABLE_SELL.getBooleanValue());

            if (isTargetTrade) {
                targetTradeLocked = offer.isDisabled();
                if (!targetTradeLocked) {
                    // 執行交易邏輯
                    if (buyItem.getCount() <= (sellId.equals(buyItemStr) ? Configs.Generic.BUY_LIMIT.getIntegerValue() : Configs.Generic.SELL_LIMIT.getIntegerValue())) {
                        Slot slot = handler.getSlot(2);
                        handler.switchTo(i);
                        handler.setRecipeIndex(i);
                        MinecraftClient.getInstance().getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(i));
                        try {
                            MinecraftClient.getInstance().interactionManager.clickSlot(
                                    handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE,
                                    MinecraftClient.getInstance().player);
                            traded = true;
                            if (sellId.equals(buyItemStr)) {
                                AutoTrade.bought += offer.getMaxUses();
                            } else {
                                AutoTrade.sold += offer.getMaxUses();
                            }
                        } catch (Exception e) {
                            System.out.println("交易錯誤: " + e.toString());
                        }
                    }
                    break;  // 找到並嘗試了目標交易,退出循環
                }
            }
        }

        screen.close();

        if (traded || targetTradeLocked) {
            tradedVillagers.add(currentVillager);
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO,
                    targetTradeLocked ? "目標交易已鎖定,跳過此村民" : "與村民完成交易");
            currentVillager = null;
            tradeAttempts = 0;
        } else {
            // 如果沒有成功交易,可能是因為物品不足,保持當前村民不變
            tradeAttempts++;
        }

        if (tradeAttempts >= MAX_TRADE_ATTEMPTS) {
            // 如果嘗試次數過多,跳過當前村民
            tradedVillagers.add(currentVillager);
            currentVillager = null;
            tradeAttempts = 0;
        }

        if (tradedVillagers.size() >= 144) {  // 或者其他你認為合適的數字
            tradedVillagers.clear();
        }
    }*/

    private void performTrading(MerchantScreen screen) {
        MerchantScreenHandler handler = screen.getScreenHandler();
        TradeOfferList offers = handler.getRecipes();

        String sellItemStr = Configs.Generic.SELL_ITEM.getStringValue();
        String buyItemStr = Configs.Generic.BUY_ITEM.getStringValue();

        boolean traded = false;
        boolean targetTradeLocked = true;

        for (int i = 0; i < offers.size(); i++) {
            TradeOffer offer = offers.get(i);
            ItemStack sellItem = offer.getSellItem();
            ItemStack buyItem = offer.getFirstBuyItem().itemStack();
            String sellId = Registries.ITEM.getId(sellItem.getItem()).toString();
            String buyId = Registries.ITEM.getId(buyItem.getItem()).toString();

            boolean isTargetTrade = (sellId.equals(buyItemStr) && Configs.Generic.ENABLE_BUY.getBooleanValue()) ||
                    (buyId.equals(sellItemStr) && Configs.Generic.ENABLE_SELL.getBooleanValue());

            if (isTargetTrade) {
                targetTradeLocked = offer.isDisabled();
                if (!targetTradeLocked) {
                    if (buyItem.getCount() <= (sellId.equals(buyItemStr) ? Configs.Generic.BUY_LIMIT.getIntegerValue() : Configs.Generic.SELL_LIMIT.getIntegerValue())) {
                        // 檢查是否有足夠的物品進行交易
                        if (hasEnoughItemsForTrade(buyId, buyItem.getCount())) {
                            Slot slot = handler.getSlot(2);
                            handler.switchTo(i);
                            handler.setRecipeIndex(i);
                            MinecraftClient.getInstance().getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(i));
                            try {
                                MinecraftClient.getInstance().interactionManager.clickSlot(
                                        handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE,
                                        MinecraftClient.getInstance().player);
                                traded = true;
                                if (sellId.equals(buyItemStr)) {
                                    AutoTrade.bought += offer.getMaxUses();
                                } else {
                                    AutoTrade.sold += offer.getMaxUses();
                                }
                            } catch (Exception e) {
                                System.out.println("交易錯誤: " + e.toString());
                            }
                        } else {
                            // 如果物品不足，設置狀態以補充物品
                            currentState = State.準備補充物品;
                            screen.close();
                            return;
                        }
                    }
                    break;
                }
            }
        }

        screen.close();

        if (traded || targetTradeLocked) {
            tradedVillagers.add(currentVillager);
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO,
                    targetTradeLocked ? "目標交易已鎖定,跳過此村民" : "與村民完成交易");
            currentVillager = null;
            tradeAttempts = 0;
        } else {
            tradeAttempts++;
        }

        if (tradeAttempts >= MAX_TRADE_ATTEMPTS) {
            tradedVillagers.add(currentVillager);
            currentVillager = null;
            tradeAttempts = 0;
        }

        if (tradedVillagers.size() >= 144) {
            tradedVillagers.clear();
        }
    }


    private boolean hasEnoughItemsForTrade(String itemId, int requiredAmount) {
        int count = countItemInInventory(MinecraftClient.getInstance().player, itemId);
        return count >= requiredAmount;
    }

    /*private void handleNormalTrading(MinecraftClient mc) {
        boolean found = false;
        Vector<Entity> newVillagersInRange = new Vector<Entity>(villagersInRange);

        for (Entity entity : mc.player.clientWorld.getEntities()) {
            if (entity instanceof VillagerEntity || entity instanceof WanderingTraderEntity) {
                if (entity.getPos().distanceTo(mc.player.getPos()) < 2.5f) {
                    if (found == false) {
                        if (newVillagersInRange.contains(entity) == false) {
                            found = true;
                            newVillagersInRange.add(entity);
                            mc.interactionManager.interactEntity(mc.player, entity, Hand.MAIN_HAND);
                            voidDelay = Configs.Generic.VOID_TRADING_DELAY.getIntegerValue();
                            villagerActive = entity.getId();
                            state = false;
                            break;
                        }
                    }
                }
            }
        }

        for (Entity entity : villagersInRange) {
            if ((entity.getPos().distanceTo(mc.player.getPos()) < 4) == false) {
                newVillagersInRange.remove(entity);
            }
        }
        villagersInRange = newVillagersInRange;
        if (found) {
            return;
        }

        BlockPos input = new BlockPos(Configs.Generic.INPUT_CONTAINER_X.getIntegerValue(),
                Configs.Generic.INPUT_CONTAINER_Y.getIntegerValue(),
                Configs.Generic.INPUT_CONTAINER_Z.getIntegerValue());

        BlockPos output = new BlockPos(Configs.Generic.OUTPUT_CONTAINER_X.getIntegerValue(),
                Configs.Generic.OUTPUT_CONTAINER_Y.getIntegerValue(),
                Configs.Generic.OUTPUT_CONTAINER_Z.getIntegerValue());

        if ((input.toCenterPos().distanceTo(mc.player.getPos()) < 4) && (inputInRange == false)) {
            inputInRange = true;
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(input.toCenterPos(), Direction.UP, input, false));
            containerDelay = Configs.Generic.CONTAINER_CLOSE_DELAY.getIntegerValue();
            inputOpened = true;
            return;
        }
        if ((output.toCenterPos().distanceTo(mc.player.getPos()) < 4) && (outputInRange == false)) {
            outputInRange = true;
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(output.toCenterPos(), Direction.UP, output, false));
            containerDelay = Configs.Generic.CONTAINER_CLOSE_DELAY.getIntegerValue();
            outputOpened = true;
            return;
        }

        if (input.toCenterPos().distanceTo(mc.player.getPos()) > 5) {
            inputOpened = false;
            inputInRange = false;
        }
        if (output.toCenterPos().distanceTo(mc.player.getPos()) > 5) {
            outputOpened = false;
            outputInRange = false;
        }
    }*/



    public void renderHud(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !this.functionalityEnabled()) return;

        int x = 10;
        int y = 10;
        int color = 0xFFFFFF; // 白色

        String status = "當前狀態: " + currentState.toString();
        context.drawText(mc.textRenderer, status, x, y, color, true);

        y += 12;
        String sellItem = Configs.Generic.ENABLE_SELL.getBooleanValue() ? Configs.Generic.SELL_ITEM.getStringValue() : "minecraft:emerald";
        String sellCount = "賣出物品數量: " + countItemInInventory(mc.player, sellItem);
        context.drawText(mc.textRenderer, sellCount, x, y, color, true);

        y += 12;
        String buyItem = Configs.Generic.ENABLE_BUY.getBooleanValue() ? Configs.Generic.BUY_ITEM.getStringValue() : "minecraft:emerald";
        String buyCount = "購買物品數量: " + countItemInInventory(mc.player, buyItem);
        context.drawText(mc.textRenderer, buyCount, x, y, color, true);
    }


    //搜索村民類

    private List<Entity> findNearbyTradeableVillagers(MinecraftClient mc) {
        Vec3d playerPos = mc.player.getPos();
        double maxSearchDistanceSquared = 256.0 * 256.0; // 256 方塊的平方距離
        double priorityDistanceSquared = 5.0 * 5.0; // 5 方塊的平方距離

        List<Entity> priorityVillagers = new ArrayList<>();
        List<Entity> otherVillagers = new ArrayList<>();

        StreamSupport.stream(mc.player.clientWorld.getEntities().spliterator(), false)
                .filter(e -> (e instanceof VillagerEntity || e instanceof WanderingTraderEntity))
                .filter(e -> e.squaredDistanceTo(playerPos) < maxSearchDistanceSquared)
                .filter(e -> !tradedVillagers.contains(e) && !villagerQueue.contains(e) && e != currentVillager)
                .forEach(e -> {
                    if (e.squaredDistanceTo(playerPos) <= priorityDistanceSquared) {
                        priorityVillagers.add(e);
                    } else {
                        otherVillagers.add(e);
                    }
                });

        priorityVillagers.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(playerPos)));
        otherVillagers.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(playerPos)));

        priorityVillagers.addAll(otherVillagers);
        return priorityVillagers;
    }

    private Entity findNearestVillager(MinecraftClient mc) {
        return findNearbyTradeableVillagers(mc).stream()
                .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(mc.player)))
                .orElse(null);
    }

    /*private boolean moveTowardsVillager(MinecraftClient mc, Entity targetVillager) {
        List<Entity> nearbyVillagers = findNearbyTradeableVillagers(mc).stream()
                .filter(e -> e.squaredDistanceTo(targetVillager) <= 5 * 5)
                .collect(Collectors.toList());

        Vec3d groupCenter = calculateVillagerGroupCenter(nearbyVillagers);
        if (groupCenter == null) {
            groupCenter = targetVillager.getPos();
        }

        Vec3d playerPos = mc.player.getPos();
        double distance = playerPos.distanceTo(groupCenter);

        if (distance > 3) {
            Vec3d movement = groupCenter.subtract(playerPos).normalize().multiply(0.5);
            mc.player.setVelocity(movement.x, mc.player.getVelocity().y, movement.z);
            mc.player.move(MovementType.SELF, mc.player.getVelocity());
            return false;
        }
        return true;
    }*/

    private long lastMoveTime = 0;
    private static final long MOVE_COOLDOWN = 50; // 50ms cooldown between moves
    //新版移動 高速
    /*private boolean moveTowardsVillager(MinecraftClient mc, Entity targetVillager) {
        Vec3d targetPos = targetVillager.getPos();
        Vec3d playerPos = mc.player.getPos();
        double distance = playerPos.distanceTo(targetPos);

        if (distance > 3) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastMoveTime < MOVE_COOLDOWN) {
                return false;
            }

            // 計算移動距離和方向
            double moveDistance = Math.min(10, distance - 3); // 減小單次移動距離以提高穩定性
            Vec3d movement = targetPos.subtract(playerPos).normalize().multiply(moveDistance);

            // 計算新位置
            double newX = playerPos.getX() + movement.x;
            double newY = playerPos.getY(); // 保持相同的Y坐標
            double newZ = playerPos.getZ() + movement.z;

            // 發送移動數據包並更新位置
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    newX, newY, newZ, true
            ));

            // 立即更新玩家位置
            mc.player.setPosition(newX, newY, newZ);

            lastMoveTime = currentTime;
            return false;
        }
        return true;
    }*/

    private boolean moveTowardsVillager(MinecraftClient mc, Entity targetVillager) {
        Vec3d targetPos = targetVillager.getPos();
        Vec3d playerPos = mc.player.getPos();

        // 只計算水平距離（X和Z）
        double horizontalDistance = Math.sqrt(
                Math.pow(targetPos.x - playerPos.x, 2) +
                        Math.pow(targetPos.z - playerPos.z, 2)
        );

        if (horizontalDistance > 2) { // 改用2格作為觸發距離，更容易接觸到村民
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastMoveTime < MOVE_COOLDOWN) {
                return false;
            }

            // 計算X和Z方向的移動
            double moveDistance = Math.min(10, horizontalDistance - 1.5);
            double dx = targetPos.x - playerPos.x;
            double dz = targetPos.z - playerPos.z;

            // 標準化移動向量
            double length = Math.sqrt(dx * dx + dz * dz);
            dx = (dx / length) * moveDistance;
            dz = (dz / length) * moveDistance;

            // 計算新位置
            double newX = playerPos.x + dx;
            double newZ = playerPos.z + dz;

            // 發送移動數據包並更新位置
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    newX,
                    playerPos.y, // 保持原有Y坐標
                    newZ,
                    true
            ));

            // 立即更新玩家位置
            mc.player.setPosition(newX, playerPos.y, newZ);

            lastMoveTime = currentTime;
            return false;
        }
        return true;
    }

    //計算村民中間位置
    private Vec3d calculateVillagerGroupCenter(List<Entity> villagers) {
        if (villagers.isEmpty()) {
            return null;
        }

        double sumX = 0, sumY = 0, sumZ = 0;
        for (Entity villager : villagers) {
            sumX += villager.getX();
            sumY += villager.getY();
            sumZ += villager.getZ();
        }

        int count = villagers.size();
        return new Vec3d(sumX / count, sumY / count, sumZ / count);
    }

}
