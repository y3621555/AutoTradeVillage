package com.johnson.autotradevillage.client;

import fi.dy.masa.malilib.event.InitializationHandler;
import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class AutotradevillageClient implements ClientModInitializer {
    public static final Logger logger = LogManager.getLogger(Reference.MOD_ID);
    public static long sessionStart = 0;
    public static int sold = 0;
    public static int bought = 0;

    @Override
    public void onInitializeClient() {
        InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());
    }
}
