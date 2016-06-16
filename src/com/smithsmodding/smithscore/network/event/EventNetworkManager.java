/*
 * Copyright (c) 2015.
 *
 * Copyrighted by SmithsModding according to the project License
 */

package com.smithsmodding.smithscore.network.event;

import com.smithsmodding.smithscore.common.events.network.*;
import com.smithsmodding.smithscore.network.event.handlers.*;
import com.smithsmodding.smithscore.network.event.messages.*;
import com.smithsmodding.smithscore.util.*;
import com.smithsmodding.smithscore.util.common.*;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.*;

/**
 * The NetworkManager that is used to manage the Synchronising of the events in the Busses.
 */
public class EventNetworkManager {
    //Private instance of the NetworkManager
    private static SimpleNetworkWrapper INSTANCE;

    /**
     * Returns the Instance of the Channel that is used for events Synchronizing.
     *
     * @return A instance of the SimpleNetworkWrapper that describes the Channel that is used to Synchronize events.
     */
    public static SimpleNetworkWrapper getInstance() {
        return INSTANCE;
    }

    /**
     * Function used to initialize the Channel.
     * Can also be used to reset the System if Need be.
     */
    public static void Init() {
        INSTANCE = new SimpleNetworkWrapper(CoreReferences.General.MOD_ID.toLowerCase() + "-EventSyncing");

        //Register the StandardNetworkableEvent System
        INSTANCE.registerMessage(StandardNetworkableEventSyncMessageHandler.class, StandardNetworkableEventSyncMessage.class, 0, Side.CLIENT);
        INSTANCE.registerMessage(StandardNetworkableEventSyncMessageHandler.class, StandardNetworkableEventSyncMessage.class, 1, Side.SERVER);

        //Create an events to notify other Mods that the NetworkManager is being Instantiated.
        //This gets all the events and gives the depending Mods a chance to store a reference to this EventBus
        NetworkManagerInitializeEvent tEvent = new NetworkManagerInitializeEvent(INSTANCE);
        tEvent.PostCommon();

        //Loop through all additional events and register them
        int tDescriminator = 2;
        for (Pair<Class, Class> tMessageClassPair : tEvent.getAdditionalMessages().keySet()) {
            INSTANCE.registerMessage(tMessageClassPair.getKey(), tMessageClassPair.getValue(), tDescriminator, tEvent.getAdditionalMessages().get(tMessageClassPair));
            tDescriminator++;
        }
    }


}