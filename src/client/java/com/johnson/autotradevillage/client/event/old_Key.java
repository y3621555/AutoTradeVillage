package com.johnson.autotradevillage.client.event;

public class old_Key {
    /*
    * private static final KeybindCallbacks INSTANCE = new KeybindCallbacks();

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

    private Queue<Entity> villagerQueue = new LinkedList<>();
    private Entity currentVillager = null;
    private int tradeAttempts = 0;
    private static final int MAX_TRADE_ATTEMPTS = 50;

    private boolean inObserverMode = false;
    private long lastObserverModeToggle = 0;
    private static final long OBSERVER_MODE_COOLDOWN = 2500; // 1.8秒冷卻時間

    private Set<Entity> attemptedVillagers = new HashSet<>();
    private static final int MAX_ATTEMPTS_PER_VILLAGER = 3;
    private boolean containerOperationComplete = false;

    private int containerInteractionDelay = 0;
    private static final int CONTAINER_INTERACTION_DELAY_TICKS = 35; // 0.5 秒 (假設 20 ticks/秒)


    public enum State {
        IDLE, MOVING_TO_VILLAGER, TRADING,
        MOVING_TO_OUTPUT, TELEPORTING_TO_OUTPUT, WAITING_AFTER_TELEPORT, OPENING_OUTPUT_CONTAINER, PLACING_ITEMS,
        MOVING_TO_INPUT, TELEPORTING_TO_INPUT, WAITING_AFTER_INPUT_TELEPORT, OPENING_INPUT_CONTAINER, TAKING_ITEMS
    }

    private State currentState = State.IDLE;
    private static final int MIN_SELL_ITEM_COUNT = 64; // 設置最小 SELL_ITEM 數量閾值

    private boolean needObserverMode = false;
    private List<Entity> nearbyTradeableVillagers = new ArrayList<>();
    private int currentVillagerIndex = 0;
    private boolean tradeState = false;
    private Set<Entity> tradedVillagers = new HashSet<>();

    private int interactionAttempts = 0;
    private static final int MAX_INTERACTION_ATTEMPTS = 3;




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
        return this.onKeyActionImpl(action, key);
    }

    private boolean onKeyActionImpl(KeyAction action, IKeybind key) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.world == null) {
            return false;
        }

        if (key == Hotkeys.TOGGLE_KEY.getKeybind()) {
            Configs.Generic.ENABLED.toggleBooleanValue();
            String msg = this.functionalityEnabled() ? "自動交易(開)" : "自動交易(關)";
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
                updateInputContainer(blockHit.getBlockPos().getX(), blockHit.getBlockPos().getY(), blockHit.getBlockPos().getZ());
            }
        } else if (key == Hotkeys.SET_OUTPUT_KEY.getKeybind()) {
            HitResult result = mc.player.raycast(20.0D, 0.0F, false);
            if (result.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) result;
                updateOutputContainer(blockHit.getBlockPos().getX(), blockHit.getBlockPos().getY(), blockHit.getBlockPos().getZ());
            }
        } else if (key == Hotkeys.SET_BUY_KEY.getKeybind()) {
            updateBuyItem(mc.player.getInventory().getMainHandStack());
        } else if (key == Hotkeys.SET_SELL_KEY.getKeybind()) {
            updateSellItem(mc.player.getInventory().getMainHandStack());
        }

        return false;
    }

    @Override
    public void onClientTick(MinecraftClient mc) {
        if (!this.functionalityEnabled() || mc.player == null) {
            return;
        }

        System.out.println("當前狀態: " + currentState);

        if (voidDelay > 0) {
            voidDelay--;
            System.out.println("等待中，剩餘時間: " + voidDelay);
            return;
        }

        if (containerDelay > 0) {
            containerDelay--;
        }

        updateVillagerQueue(mc);

        switch (currentState) {
            case IDLE:
                nearbyTradeableVillagers = findNearbyTradeableVillagers(mc);
                if (!nearbyTradeableVillagers.isEmpty()) {
                    currentVillagerIndex = 0;
                    currentVillager = nearbyTradeableVillagers.get(currentVillagerIndex);
                    currentState = State.MOVING_TO_VILLAGER;
                    tradeState = false;
                }
                break;

            case MOVING_TO_VILLAGER:
                if (currentVillager != null) {
                    double distanceToVillager = currentVillager.getPos().distanceTo(mc.player.getPos());
                    System.out.println("距離村民: " + distanceToVillager);

                    if (distanceToVillager > 3) {
                        if (!inObserverMode) {
                            enterObserverMode(mc);
                        }
                        if (inObserverMode) {
                            moveTowardsEntity(mc, currentVillager, 3.0);
                        }
                    } else {
                        if (inObserverMode) {
                            exitObserverMode(mc);
                        }
                        if (!inObserverMode) {
                            System.out.println("嘗試與村民互動");
                            ActionResult interactResult = mc.interactionManager.interactEntity(mc.player, currentVillager, Hand.MAIN_HAND);
                            System.out.println("互動結果: " + interactResult);

                            if (interactResult == ActionResult.SUCCESS) {
                                voidDelay = Configs.Generic.VOID_TRADING_DELAY.getIntegerValue();
                                villagerActive = currentVillager.getId();
                                currentState = State.TRADING;
                                System.out.println("成功開啟交易介面，切換到 TRADING 狀態");
                            } else {
                                System.out.println("無法與村民互動，重試");
                                currentState = State.IDLE;  // 重置狀態，下一個 tick 再試
                            }
                        }
                    }
                } else {
                    System.out.println("當前沒有目標村民，返回 IDLE 狀態");
                    currentState = State.IDLE;
                }
                break;

            case TRADING:
                if (GuiUtils.getCurrentScreen() instanceof MerchantScreen) {
                    System.out.println("正在交易畫面中");
                    MerchantScreen screen = (MerchantScreen) GuiUtils.getCurrentScreen();
                    if (!tradeState) {
                        if (performTrading(screen)) {
                            tradeAttempts = 0;
                            tradeState = true;
                            tradedVillagers.add(currentVillager);
                            System.out.println("交易成功");
                        } else {
                            tradeAttempts++;
                            System.out.println("交易失敗，嘗試次數: " + tradeAttempts);
                            if (tradeAttempts >= MAX_ATTEMPTS_PER_VILLAGER) {
                                tradedVillagers.add(currentVillager);
                                screen.close();
                                moveToNextVillager(mc);
                                System.out.println("達到最大嘗試次數，移動到下一個村民");
                            }
                        }
                    } else {
                        screen.close();
                        moveToNextVillager(mc);
                        System.out.println("完成交易，移動到下一個村民");
                    }
                } else {
                    System.out.println("不在交易畫面中，檢查是否需要補充物品");
                    if (needToReplenishItems(mc)) {
                        currentState = State.MOVING_TO_OUTPUT;
                        System.out.println("需要補充物品，切換到 MOVING_TO_OUTPUT 狀態");
                    } else {
                        currentState = State.MOVING_TO_VILLAGER;
                        System.out.println("不需要補充物品，返回 MOVING_TO_VILLAGER 狀態");
                    }
                }
                break;

            case MOVING_TO_OUTPUT:
            case TELEPORTING_TO_OUTPUT:
            case WAITING_AFTER_TELEPORT:
            case OPENING_OUTPUT_CONTAINER:
            case PLACING_ITEMS:
                handleContainerInteraction(mc, false, State.MOVING_TO_INPUT);
                break;

            case MOVING_TO_INPUT:
            case TELEPORTING_TO_INPUT:
            case WAITING_AFTER_INPUT_TELEPORT:
            case OPENING_INPUT_CONTAINER:
            case TAKING_ITEMS:
                handleContainerInteraction(mc, true, State.MOVING_TO_VILLAGER);
                break;
        }

        if (Configs.Generic.GLASS_BLOCK.getBooleanValue()) {
            handleGlassBlockDetection(mc);
        }

        if (Configs.Generic.ITEM_FRAME.getBooleanValue()) {
            handleItemFrameDetection(mc);
        }

        handleContainerScreens(mc);
        handleInputOutputContainers(mc);

        tickCount++;
        if (tickCount > 100) {
            tickCount = 0;
            villagersInRange = new Vector<Entity>();
            inputInRange = false;
            outputInRange = false;
            closeAllScreens(mc);
        }
    }

    private List<Entity> findNearbyTradeableVillagers(MinecraftClient mc) {
        Vec3d playerPos = mc.player.getPos();
        double maxSearchDistanceSquared = 256.0 * 256.0; // 256 方塊的平方距離

        return StreamSupport.stream(mc.player.clientWorld.getEntities().spliterator(), false)
                .filter(e -> (e instanceof VillagerEntity || e instanceof WanderingTraderEntity))
                .filter(e -> e.squaredDistanceTo(playerPos) < maxSearchDistanceSquared)
                .filter(e -> !tradedVillagers.contains(e))
                .sorted(Comparator.comparingDouble(e -> e.squaredDistanceTo(playerPos))) // 按距離排序
                .collect(Collectors.toList());
    }

    private void moveToNextVillager(MinecraftClient mc) {
        currentVillagerIndex++;
        if (currentVillagerIndex < nearbyTradeableVillagers.size()) {
            currentVillager = nearbyTradeableVillagers.get(currentVillagerIndex);
            currentState = State.MOVING_TO_VILLAGER;
            tradeAttempts = 0;
            tradeState = false;
        } else {
            currentState = State.IDLE;
            nearbyTradeableVillagers.clear();
            currentVillagerIndex = 0;
            tradeState = false;
            if (tradedVillagers.size() >= 144) { // 假設我們想在交易10個村民後清理列表
                tradedVillagers.clear();
            }
        }
    }

    private void handleContainerInteraction(MinecraftClient mc, boolean isInput, State nextState) {
        String warpCommand = isInput
                ? Configs.Generic.INPUT_CONTAINER_WARP.getStringValue()
                : Configs.Generic.OUTPUT_CONTAINER_WARP.getStringValue();

        switch (currentState) {
            case MOVING_TO_OUTPUT:
            case MOVING_TO_INPUT:
                mc.player.networkHandler.sendCommand(warpCommand);
                System.out.println("執行傳送指令: " + warpCommand);
                voidDelay = 40; // 設置一個延遲，等待傳送完成
                currentState = isInput ? State.TELEPORTING_TO_INPUT : State.TELEPORTING_TO_OUTPUT;
                break;

            case TELEPORTING_TO_OUTPUT:
            case TELEPORTING_TO_INPUT:
                if (voidDelay > 0) {
                    voidDelay--;
                } else {
                    currentState = isInput ? State.WAITING_AFTER_INPUT_TELEPORT : State.WAITING_AFTER_TELEPORT;
                    containerInteractionDelay = CONTAINER_INTERACTION_DELAY_TICKS;
                }
                break;

            case WAITING_AFTER_TELEPORT:
            case WAITING_AFTER_INPUT_TELEPORT:
                if (containerInteractionDelay > 0) {
                    containerInteractionDelay--;
                } else {
                    currentState = isInput ? State.OPENING_INPUT_CONTAINER : State.OPENING_OUTPUT_CONTAINER;
                }
                break;

            case OPENING_OUTPUT_CONTAINER:
            case OPENING_INPUT_CONTAINER:
                BlockPos containerPos = isInput
                        ? new BlockPos(Configs.Generic.INPUT_CONTAINER_X.getIntegerValue(),
                        Configs.Generic.INPUT_CONTAINER_Y.getIntegerValue(),
                        Configs.Generic.INPUT_CONTAINER_Z.getIntegerValue())
                        : new BlockPos(Configs.Generic.OUTPUT_CONTAINER_X.getIntegerValue(),
                        Configs.Generic.OUTPUT_CONTAINER_Y.getIntegerValue(),
                        Configs.Generic.OUTPUT_CONTAINER_Z.getIntegerValue());

                if (interactWithContainer(mc, containerPos, isInput)) {
                    currentState = isInput ? State.TAKING_ITEMS : State.PLACING_ITEMS;
                }
                break;

            case PLACING_ITEMS:
            case TAKING_ITEMS:
                if (mc.currentScreen instanceof HandledScreen) {
                    HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;
                    if (isInput) {
                        processInput(screen.getScreenHandler());
                    } else {
                        processOutput(screen.getScreenHandler());
                    }

                    // 檢查是否需要再次與容器互動
                    boolean shouldInteract = isInput ? hasSpaceForItems(mc) : hasItemsToPlace(mc);
                    if (shouldInteract) {
                        System.out.println("需要再次與容器互動");
                        currentState = isInput ? State.OPENING_INPUT_CONTAINER : State.OPENING_OUTPUT_CONTAINER;
                    } else {
                        screen.close();
                        exitObserverMode(mc);
                        currentState = nextState;
                        System.out.println("容器交互完成，切換到狀態: " + nextState);
                    }
                }
                break;
        }
    }

    private boolean hasItemsToPlace(MinecraftClient mc) {
        String itemToPlace = Configs.Generic.ENABLE_BUY.getBooleanValue()
                ? Configs.Generic.BUY_ITEM.getStringValue()
                : "minecraft:emerald";

        System.out.println("檢查是否有物品可放置，目標物品: " + itemToPlace);

        int totalCount = 0;
        for (ItemStack stack : mc.player.getInventory().main) {
            if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemToPlace)) {
                totalCount += stack.getCount();
            }
        }

        System.out.println("玩家背包中共有 " + totalCount + " 個 " + itemToPlace);
        return totalCount > 0;
    }

    private boolean hasSpaceForItems(MinecraftClient mc) {
        String itemToTake = Configs.Generic.ENABLE_BUY.getBooleanValue()
                ? Configs.Generic.BUY_ITEM.getStringValue()
                : "minecraft:emerald";

        int emptySlots = 0;
        int partialStacks = 0;

        for (ItemStack stack : mc.player.getInventory().main) {
            if (stack.isEmpty()) {
                emptySlots++;
            } else if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemToTake) && stack.getCount() < stack.getMaxCount()) {
                partialStacks++;
            }
        }

        return emptySlots > 0 || partialStacks > 0;
    }


    private boolean needToTakeFromInputContainer(MinecraftClient mc) {
        String sellItemId = Configs.Generic.SELL_ITEM.getStringValue();
        int count = countItemsInInventory(mc, sellItemId);
        return count < MIN_SELL_ITEM_COUNT;
    }

    private boolean needToPutIntoOutputContainer(MinecraftClient mc) {
        String buyItemId = Configs.Generic.BUY_ITEM.getStringValue();
        int count = countItemsInInventory(mc, buyItemId);
        return count > 0;
    }

    private int countItemsInInventory(MinecraftClient mc, String itemId) {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
                count += stack.getCount();
            }
        }
        return count;
    }


    // 修改 moveTowardsEntity 方法
    private void moveTowardsEntity(MinecraftClient mc, Entity target, double speed) {
        Vec3d playerPos = mc.player.getPos();
        Vec3d targetPos = target.getPos();
        Vec3d movement = targetPos.subtract(playerPos).normalize().multiply(speed);

        if (!inObserverMode) {
            enterObserverMode(mc);
        }

        if (inObserverMode) {
            mc.player.setPosition(playerPos.add(movement));
        }
    }

    private void processOutput(ScreenHandler handler) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        outputOpened = false;
        String itemToPlace = Configs.Generic.ENABLE_BUY.getBooleanValue()
                ? Configs.Generic.BUY_ITEM.getStringValue()
                : "minecraft:emerald";

        System.out.println("正在處理輸出容器，要放置的物品: " + itemToPlace);

        int itemsPlaced = 0;
        boolean placedItems = false;

        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (slot.inventory instanceof PlayerInventory) {
                ItemStack stack = slot.getStack();
                if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemToPlace)) {
                    try {
                        MinecraftClient.getInstance().interactionManager.clickSlot(
                                handler.syncId,
                                slot.id,
                                0,
                                SlotActionType.QUICK_MOVE,
                                player
                        );
                        itemsPlaced += stack.getCount();
                        placedItems = true;
                    } catch (Exception e) {
                        System.out.println("放置物品時發生錯誤: " + e.toString());
                        player.sendMessage(Text.literal("放置物品時發生錯誤: " + e.toString()), false);
                    }
                }
            }
        }

        System.out.println("處理完成，總共放置: " + itemsPlaced + " 個 " + itemToPlace);

        if (placedItems) {
            player.sendMessage(Text.literal("已放置 " + itemsPlaced + " 個 " + itemToPlace), false);
        } else {
            player.sendMessage(Text.literal("沒有放置任何物品！"), false);
        }
    }

    private void processInput(ScreenHandler handler) {
        inputOpened = false;
        String itemToTake = Configs.Generic.ENABLE_SELL.getBooleanValue()
                ? Configs.Generic.SELL_ITEM.getStringValue()
                : "minecraft:emerald";

        int inputCount = 0;
        for (int i = 0; i < handler.slots.size(); i++) {
            if (!(handler.getSlot(i).inventory instanceof PlayerInventory)) {
                if (Registries.ITEM.getId(handler.getSlot(i).getStack().getItem()).toString().equals(itemToTake)) {
                    if (inputCount < (Configs.Generic.MAX_INPUT_ITEMS.getIntegerValue() * 64)) {
                        inputCount += handler.getSlot(i).getStack().getCount();
                        try {
                            MinecraftClient.getInstance().interactionManager.clickSlot(handler.syncId,
                                    handler.getSlot(i).id, 0, SlotActionType.QUICK_MOVE,
                                    MinecraftClient.getInstance().player);
                        } catch (Exception e) {
                            System.out.println("處理輸入時發生錯誤: " + e.toString());
                        }
                    }
                }
            }
        }

        if (MinecraftClient.getInstance().currentScreen instanceof HandledScreen) {
            ((HandledScreen<?>) MinecraftClient.getInstance().currentScreen).close();
        }
    }


    private boolean performTrading(MerchantScreen screen) {
        System.out.println("開始執行交易");
        MerchantScreenHandler handler = screen.getScreenHandler();
        TradeOfferList offers = handler.getRecipes();
        boolean traded = false;

        for (int i = 0; i < offers.size(); i++) {
            TradeOffer offer = offers.get(i);
            System.out.println("檢查交易選項 " + i);

            ItemStack sellItem = offer.getSellItem();
            ItemStack buyItem = offer.getFirstBuyItem().itemStack();
            String sellId = Registries.ITEM.getId(sellItem.getItem()).toString();
            String buyId = Registries.ITEM.getId(buyItem.getItem()).toString();

            boolean canSell = Configs.Generic.ENABLE_SELL.getBooleanValue() &&
                    buyId.equals(Configs.Generic.SELL_ITEM.getStringValue()) &&
                    buyItem.getCount() <= Configs.Generic.SELL_LIMIT.getIntegerValue();

            boolean canBuy = Configs.Generic.ENABLE_BUY.getBooleanValue() &&
                    sellId.equals(Configs.Generic.BUY_ITEM.getStringValue());

            if (canSell || canBuy) {
                System.out.println("找到可執行的交易: " + (canSell ? "販售" : "購買"));
                handler.setRecipeIndex(i);
                MinecraftClient.getInstance().getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(i));

                Slot slot = handler.getSlot(2);
                try {
                    System.out.println("嘗試執行交易操作");
                    MinecraftClient.getInstance().interactionManager.clickSlot(
                            handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE,
                            MinecraftClient.getInstance().player
                    );
                    traded = true;
                    updateTradeStatistics(offer);
                    System.out.println("交易成功執行");
                } catch (Exception e) {
                    System.out.println("交易錯誤: " + e.toString());
                }
            }
        }

        System.out.println("交易過程結束，是否成功交易：" + traded);
        return traded;
    }

    private boolean canPerformTrade(TradeOffer offer) {
        String sellItemStr = Configs.Generic.SELL_ITEM.getStringValue();
        String buyItemStr = Configs.Generic.BUY_ITEM.getStringValue();
        ItemStack sellItem = offer.getSellItem();
        ItemStack buyItem = offer.getFirstBuyItem().itemStack();
        String sellId = Registries.ITEM.getId(sellItem.getItem()).toString();
        String buyId = Registries.ITEM.getId(buyItem.getItem()).toString();

        return (sellId.equals(buyItemStr) && Configs.Generic.ENABLE_BUY.getBooleanValue()
                && buyItem.getCount() <= Configs.Generic.BUY_LIMIT.getIntegerValue())
                || (buyId.equals(sellItemStr) && Configs.Generic.ENABLE_SELL.getBooleanValue()
                && buyItem.getCount() <= Configs.Generic.SELL_LIMIT.getIntegerValue());
    }

    private void updateVillagerQueue(MinecraftClient mc) {
        if (villagerQueue.isEmpty()) {
            for (Entity entity : mc.player.clientWorld.getEntities()) {
                if ((entity instanceof VillagerEntity || entity instanceof WanderingTraderEntity)
                        && entity.getPos().distanceTo(mc.player.getPos()) < 20f) {
                    villagerQueue.offer(entity);
                }
            }
        }
    }

    private void moveTowardsVillager(MinecraftClient mc, Entity villager) {
        Vec3d playerPos = mc.player.getPos();
        Vec3d villagerPos = villager.getPos();
        Vec3d movement = villagerPos.subtract(playerPos).normalize().multiply(0.5);

        if (inObserverMode) {
            mc.player.setPosition(playerPos.add(movement));
        } else {
            mc.player.setVelocity(movement.x, mc.player.getVelocity().y, movement.z);
            mc.player.move(MovementType.SELF, mc.player.getVelocity());
        }
    }

    private void updateObserverMode(MinecraftClient mc, boolean needObserverMode) {
        long currentTime = System.currentTimeMillis();
        if (needObserverMode && !inObserverMode) {
            if (currentTime - lastObserverModeToggle >= OBSERVER_MODE_COOLDOWN) {
                enterObserverMode(mc);
            }
        } else if (!needObserverMode && inObserverMode) {
            if (currentTime - lastObserverModeToggle >= OBSERVER_MODE_COOLDOWN) {
                exitObserverMode(mc);
            }
        }
    }

    private Entity findNearestVillager(MinecraftClient mc) {
        Entity nearest = null;
        double minDistance = Double.MAX_VALUE;
        for (Entity entity : mc.player.clientWorld.getEntities()) {
            if (entity instanceof VillagerEntity || entity instanceof WanderingTraderEntity) {
                double distance = entity.getPos().distanceTo(mc.player.getPos());
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = entity;
                }
            }
        }
        return nearest;
    }

    private void handleGlassBlockDetection(MinecraftClient mc) {
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
                        updateInputContainer(x, y - selectorOffset, z);
                        break;
                    }
                    if (mc.player.clientWorld.getBlockState(pos).isOf(Blocks.BLUE_STAINED_GLASS)) {
                        updateOutputContainer(x, y - selectorOffset, z);
                        break;
                    }
                }
            }
        }
    }

    private void handleItemFrameDetection(MinecraftClient mc) {
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
                    updateSellItem(stack);
                }
                if (nameString.equals("\"buy\"")) {
                    updateBuyItem(stack);
                }
            }
        }
    }

    private void handleContainerScreens(MinecraftClient mc) {
        Screen currentScreen = GuiUtils.getCurrentScreen();
        if (currentScreen instanceof ShulkerBoxScreen) {
            ShulkerBoxScreen screen = (ShulkerBoxScreen) currentScreen;
            ShulkerBoxScreenHandler handler = screen.getScreenHandler();
            processContainer(handler, screen);
        }
        if (currentScreen instanceof GenericContainerScreen) {
            GenericContainerScreen screen = (GenericContainerScreen) currentScreen;
            GenericContainerScreenHandler handler = screen.getScreenHandler();
            processContainer(handler, screen);
        }
    }

    private void handleInputOutputContainers(MinecraftClient mc) {
        BlockPos input = new BlockPos(Configs.Generic.INPUT_CONTAINER_X.getIntegerValue(),
                Configs.Generic.INPUT_CONTAINER_Y.getIntegerValue(),
                Configs.Generic.INPUT_CONTAINER_Z.getIntegerValue());

        BlockPos output = new BlockPos(Configs.Generic.OUTPUT_CONTAINER_X.getIntegerValue(),
                Configs.Generic.OUTPUT_CONTAINER_Y.getIntegerValue(),
                Configs.Generic.OUTPUT_CONTAINER_Z.getIntegerValue());

        if ((input.toCenterPos().distanceTo(mc.player.getPos()) < 4) && !inputInRange) {
            interactWithContainer(mc, input, true);
        }
        if ((output.toCenterPos().distanceTo(mc.player.getPos()) < 4) && !outputInRange) {
            interactWithContainer(mc, output, false);
        }

        if (input.toCenterPos().distanceTo(mc.player.getPos()) > 5) {
            inputOpened = false;
            inputInRange = false;
        }
        if (output.toCenterPos().distanceTo(mc.player.getPos()) > 5) {
            outputOpened = false;
            outputInRange = false;
        }
    }

    private void closeAllScreens(MinecraftClient mc) {
        if (GuiUtils.getCurrentScreen() instanceof MerchantScreen
                || GuiUtils.getCurrentScreen() instanceof ShulkerBoxScreen
                || GuiUtils.getCurrentScreen() instanceof GenericContainerScreen) {
            GuiUtils.getCurrentScreen().close();
        }
    }

    private void updateTradeStatistics(TradeOffer offer) {
        String sellId = Registries.ITEM.getId(offer.getSellItem().getItem()).toString();
        String buyId = Registries.ITEM.getId(offer.getFirstBuyItem().itemStack().getItem()).toString();

        if (sellId.equals(Configs.Generic.BUY_ITEM.getStringValue())) {
            AutoTrade.bought += offer.getMaxUses();
        } else if (buyId.equals(Configs.Generic.SELL_ITEM.getStringValue())) {
            AutoTrade.sold += offer.getMaxUses();
        }
    }

    private void updateInputContainer(int x, int y, int z) {
        if ((x != Configs.Generic.INPUT_CONTAINER_X.getIntegerValue())
                || (y != Configs.Generic.INPUT_CONTAINER_Y.getIntegerValue())
                || (z != Configs.Generic.INPUT_CONTAINER_Z.getIntegerValue())) {
            Configs.Generic.INPUT_CONTAINER_X.setIntegerValue(x);
            Configs.Generic.INPUT_CONTAINER_Y.setIntegerValue(y);
            Configs.Generic.INPUT_CONTAINER_Z.setIntegerValue(z);
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO,
                    "autotrade.message.input_container_set", x, y, z);
        }
    }

    private void updateOutputContainer(int x, int y, int z) {
        if ((x != Configs.Generic.OUTPUT_CONTAINER_X.getIntegerValue())
                || (y != Configs.Generic.OUTPUT_CONTAINER_Y.getIntegerValue())
                || (z != Configs.Generic.OUTPUT_CONTAINER_Z.getIntegerValue())) {
            Configs.Generic.OUTPUT_CONTAINER_X.setIntegerValue(x);
            Configs.Generic.OUTPUT_CONTAINER_Y.setIntegerValue(y);
            Configs.Generic.OUTPUT_CONTAINER_Z.setIntegerValue(z);
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO,
                    "autotrade.message.output_container_set", x, y, z);
        }
    }

    private void updateSellItem(ItemStack stack) {
        String sellItem = Registries.ITEM.getId(stack.getItem()).toString();
        if (!Configs.Generic.SELL_ITEM.getStringValue().equals(sellItem)) {
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO,
                    "autotrade.message.sell_item_set", sellItem);
            Configs.Generic.SELL_ITEM.setValueFromString(sellItem);
        }
    }

    private void updateBuyItem(ItemStack stack) {
        String buyItem = Registries.ITEM.getId(stack.getItem()).toString();
        if (!Configs.Generic.BUY_ITEM.getStringValue().equals(buyItem)) {
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO,
                    "autotrade.message.buy_item_set", buyItem);
            Configs.Generic.BUY_ITEM.setValueFromString(buyItem);
        }
    }

    private void processContainer(ScreenHandler handler, HandledScreen<?> screen) {

        if ((containerDelay == 0) && inputOpened) {
            processInput(handler);
            screen.close();
        }
        if ((containerDelay == 0) && outputOpened) {
            processOutput(handler);
            screen.close();
        }
    }

    private boolean interactWithContainer(MinecraftClient mc, BlockPos pos, boolean isInput) {
        Vec3d interactPos = Vec3d.ofCenter(pos);
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new BlockHitResult(interactPos, Direction.UP, pos, false));

        if (result == ActionResult.SUCCESS) {
            containerDelay = Configs.Generic.CONTAINER_CLOSE_DELAY.getIntegerValue();
            if (isInput) {
                inputOpened = true;
            } else {
                outputOpened = true;
            }
            return true;
        }
        return false;
    }


    private void enterObserverMode(MinecraftClient mc) {
        if (!inObserverMode) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastObserverModeToggle >= OBSERVER_MODE_COOLDOWN) {
                mc.player.networkHandler.sendCommand("cgm");
                inObserverMode = true;
                lastObserverModeToggle = currentTime;
            }
        }
    }

    private void exitObserverMode(MinecraftClient mc) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastObserverModeToggle >= OBSERVER_MODE_COOLDOWN) {
            mc.player.networkHandler.sendCommand("cgm");
            inObserverMode = false;
            lastObserverModeToggle = currentTime;
            System.out.println("退出觀察者模式");
        }
    }

    private boolean needsObserverMode(MinecraftClient mc, Entity target) {
        Vec3d playerPos = mc.player.getPos();
        Vec3d targetPos = target.getPos();
        Vec3d direction = targetPos.subtract(playerPos).normalize();
        double distance = playerPos.distanceTo(targetPos);

        for (int i = 1; i < distance; i++) {
            Vec3d checkVec = playerPos.add(direction.multiply(i));
            BlockPos checkPos = new BlockPos(
                    (int) Math.floor(checkVec.x),
                    (int) Math.floor(checkVec.y),
                    (int) Math.floor(checkVec.z)
            );

            // 檢查是否超出世界邊界
            if (!mc.world.isInBuildLimit(checkPos)) {
                return true; // 如果超出邊界，假設需要觀察者模式
            }

            if (!mc.world.getBlockState(checkPos).isAir()) {
                return true;
            }
        }
        return false;
    }


    private boolean needToReplenishItems(MinecraftClient mc) {
        if (Configs.Generic.ENABLE_BUY.getBooleanValue()) {
            // 如果是購買模式，檢查綠寶石數量
            int emeraldCount = countItemsInInventory(mc, "minecraft:emerald");
            System.out.println("當前綠寶石數量: " + emeraldCount);
            return emeraldCount < MIN_SELL_ITEM_COUNT;  // 使用一個較小的閾值，比如 16
        } else if (Configs.Generic.ENABLE_SELL.getBooleanValue()) {
            // 如果是販售模式，檢查要販售的物品數量
            int sellItemCount = countItemsInInventory(mc, Configs.Generic.SELL_ITEM.getStringValue());
            System.out.println("當前販售物品數量: " + sellItemCount);
            return sellItemCount < MIN_SELL_ITEM_COUNT;
        }
        return false;
    }

    private int countSellItems(MinecraftClient mc) {
        String sellItemId = Configs.Generic.SELL_ITEM.getStringValue();
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (Registries.ITEM.getId(stack.getItem()).toString().equals(sellItemId)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    // 修改 moveTowardsBlock 方法
    private void moveTowardsBlock(MinecraftClient mc, BlockPos pos, double speed) {
        Vec3d playerPos = mc.player.getPos();
        Vec3d targetPos = Vec3d.ofCenter(pos);
        Vec3d movement = targetPos.subtract(playerPos).normalize().multiply(speed);

        // 確保不會越過目標點
        if (playerPos.add(movement).distanceTo(targetPos) < speed) {
            mc.player.setPosition(targetPos.x, playerPos.y, targetPos.z);
        } else {
            mc.player.setPosition(playerPos.add(movement));
        }
    }
    *
    * */
}
