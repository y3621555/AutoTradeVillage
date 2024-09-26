package com.johnson.autotradevillage.client.config;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.IConfigValue;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigString;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;
import com.johnson.autotradevillage.client.Reference;


import java.io.File;

public class Configs implements IConfigHandler {
    private static final String CONFIG_FILE_NAME = Reference.MOD_ID + ".json";

    public static class Generic {
        public static final ConfigBoolean ENABLED = new ConfigBoolean("啟動", false,
                "是否自動與範圍內的村民進行交易");
        public static final ConfigBoolean ITEM_FRAME = new ConfigBoolean("使用物品展示框選擇買賣物品", true,
                "使用物品展示框選擇買賣物品（最大距離3）,物品需命名為\"buy\"或\"sell\"");
        public static final ConfigBoolean GLASS_BLOCK = new ConfigBoolean("選擇染色玻璃", false,
                "通過在容器上方（或下方如果為負數）<selectionBlockOffset>個方塊處放置紅色（輸入）和藍色（輸出）染色玻璃塊來選擇輸入和輸出容器");
        public static final ConfigInteger SELECTOR_OFFSET = new ConfigInteger("方塊偏移量", 3, -10, 10, "");
        public static final ConfigBoolean ENABLE_SELL = new ConfigBoolean("啟動販售", false,
                "啟用售賣（如果禁用,綠寶石將從輸入容器中取出）");
        public static final ConfigString SELL_ITEM = new ConfigString("販賣的物品", "minecraft:gold_ingot",
                "用於換取綠寶石的物品");
        public static final ConfigInteger SELL_LIMIT = new ConfigInteger("販賣最高價格", 64, 1, 64,
                "售賣的最高價格");
        public static final ConfigBoolean ENABLE_BUY = new ConfigBoolean("啟動購買", false,
                "啟用購買（如果禁用,綠寶石將放入輸出容器）");
        public static final ConfigString BUY_ITEM = new ConfigString("購買的物品", "minecraft:redstone",
                "使用綠寶石購買的物品");
        public static final ConfigInteger BUY_LIMIT = new ConfigInteger("購買的最高價格", 64, 1, 64, "購買的最高價格");
        public static final ConfigInteger MAX_INPUT_ITEMS = new ConfigInteger("最高取出量", 9, 1, 35,
                "從輸入容器（或僅購買模式下的綠寶石容器）中取出的堆疊數量");
        public static final ConfigInteger INPUT_CONTAINER_X = new ConfigInteger("輸入容器(X)", 0, -30000000,
                30000000, "輸入容器X坐標（僅在啟用售賣時使用）");
        public static final ConfigInteger INPUT_CONTAINER_Y = new ConfigInteger("輸入容器(Y)", 0, -64, 320,
                "輸入容器Y坐標（僅在啟用售賣時使用）");
        public static final ConfigInteger INPUT_CONTAINER_Z = new ConfigInteger("輸入容器(Z)", 0, -30000000,
                30000000, "輸入容器Z坐標（僅在啟用售賣時使用）");
        public static final ConfigInteger OUTPUT_CONTAINER_X = new ConfigInteger("輸出容器(X)", 0, -30000000,
                30000000, "輸出容器X坐標（僅在啟用購買時使用）");
        public static final ConfigInteger OUTPUT_CONTAINER_Y = new ConfigInteger("輸出容器(Y)", 0, -64, 320,
                "輸出容器Y坐標（僅在啟用購買時使用）");
        public static final ConfigInteger OUTPUT_CONTAINER_Z = new ConfigInteger("輸出容器(Z)", 0, -30000000,
                30000000, "輸出容器Z坐標（僅在啟用購買時使用）");
        public static final ConfigInteger VOID_TRADING_DELAY = new ConfigInteger("虛空交易延遲", 0, 0, 30000000,
                "虛空交易延遲（以遊戲刻為單位）");
        public static final ConfigBoolean VOID_TRADING_DELAY_AFTER_TELEPORT = new ConfigBoolean("交易延遲",
                true,
                "true: 在村民被卸載後開始延遲; false: 在交易開始後開始延遲");
        public static final ConfigInteger CONTAINER_CLOSE_DELAY = new ConfigInteger("延遲（以遊戲刻為單位）", 0, 0,
                30000000, "延遲（以遊戲刻為單位）; 用於從陷阱箱獲取信號");

        public static final ConfigString INPUT_CONTAINER_WARP = new ConfigString("輸入容器傳送指令", "warp input_storage",
                "傳送到輸入容器附近的指令");
        public static final ConfigString OUTPUT_CONTAINER_WARP = new ConfigString("輸出容器傳送指令", "warp output_storage",
                "傳送到輸出容器附近的指令");

        public static final ImmutableList<IConfigValue> OPTIONS = ImmutableList.of(
                ENABLED, ITEM_FRAME, GLASS_BLOCK, SELECTOR_OFFSET, ENABLE_SELL, SELL_ITEM, SELL_LIMIT,
                ENABLE_BUY, BUY_ITEM, BUY_LIMIT, MAX_INPUT_ITEMS,
                INPUT_CONTAINER_X, INPUT_CONTAINER_Y, INPUT_CONTAINER_Z,
                OUTPUT_CONTAINER_X, OUTPUT_CONTAINER_Y, OUTPUT_CONTAINER_Z,
                VOID_TRADING_DELAY, VOID_TRADING_DELAY_AFTER_TELEPORT, CONTAINER_CLOSE_DELAY,
                INPUT_CONTAINER_WARP, OUTPUT_CONTAINER_WARP  // 新增的 warp 指令配置
        );
    }

    public static void loadFromFile() {
        File configFile = new File(FileUtils.getConfigDirectory(), CONFIG_FILE_NAME);

        if (configFile.exists() && configFile.isFile() && configFile.canRead()) {
            JsonElement element = JsonUtils.parseJsonFile(configFile);

            if (element != null && element.isJsonObject()) {
                JsonObject root = element.getAsJsonObject();

                ConfigUtils.readConfigBase(root, "Generic", Generic.OPTIONS);
                ConfigUtils.readConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);
            }
        }
    }

    public static void saveToFile() {
        File dir = FileUtils.getConfigDirectory();

        if ((dir.exists() && dir.isDirectory()) || dir.mkdirs()) {
            JsonObject root = new JsonObject();

            ConfigUtils.writeConfigBase(root, "Generic", Generic.OPTIONS);
            ConfigUtils.writeConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);

            JsonUtils.writeJsonToFile(root, new File(dir, CONFIG_FILE_NAME));
        }
    }

    @Override
    public void load() {
        loadFromFile();
    }

    @Override
    public void save() {
        saveToFile();
    }
}
