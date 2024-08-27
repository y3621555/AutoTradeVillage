package com.johnson.autotradevillage.client.config;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.options.ConfigHotkey;

import java.util.List;

public class Hotkeys {
    public static final ConfigHotkey TOGGLE_KEY = new ConfigHotkey("啟動", "",
            "啟用/禁用自動交易");
    public static final ConfigHotkey SET_SELL_KEY = new ConfigHotkey("設定販賣的物品", "",
            "從快捷欄設置要出售的物品");
    public static final ConfigHotkey SET_BUY_KEY = new ConfigHotkey("設定購買的物品", "",
            "從快捷欄設置要購買的物品");
    public static final ConfigHotkey SET_INPUT_KEY = new ConfigHotkey("設置輸入容器", "",
            "設置輸入（要出售物品的）容器");
    public static final ConfigHotkey SET_OUTPUT_KEY = new ConfigHotkey("設置輸出容器", "",
            "設置輸出（要購買物品的）容器");
    public static final ConfigHotkey OPEN_GUI_SETTINGS = new ConfigHotkey("打開GUI", "RIGHT_SHIFT,T",
            "開啟配置介面");

    public static final List<ConfigHotkey> HOTKEY_LIST = ImmutableList.of(TOGGLE_KEY, SET_SELL_KEY, SET_BUY_KEY,
            SET_INPUT_KEY, SET_OUTPUT_KEY, OPEN_GUI_SETTINGS);
}
