package com.johnmelodyme.stupidcsrdemo.app.model;

import android.os.Bundle;

import com.csr.csrmesh2.BatteryModelApi;
import com.csr.csrmesh2.MeshConstants;
import com.johnmelodyme.stupidcsrdemo.app.API.MeshLibraryManager;
import com.johnmelodyme.stupidcsrdemo.app.event.MeshRequestEvent;

public final class BatteryModel
{

    public static int getState(int deviceId)
    {
        Bundle data = new Bundle();
        int id = MeshLibraryManager.getInstance().getNextRequestId();
        data.putInt(MeshLibraryManager.EXTRA_REQUEST_ID, id);
        data.putInt(MeshConstants.EXTRA_DEVICE_ID, deviceId);
//        App.bus.post(new MeshRequestEvent(MeshRequestEvent.RequestEvent.BATTERY_GET_STATE, data));
        return id;
    }

    public static void handleRequest(MeshRequestEvent event)
    {
        switch (event.what)
        {
            case BATTERY_GET_STATE:
                int deviceId = event.data.getInt(MeshConstants.EXTRA_DEVICE_ID);
                int libId = BatteryModelApi.getState(deviceId);
                int internalId = event.data.getInt(MeshLibraryManager.EXTRA_REQUEST_ID);
                MeshLibraryManager.getInstance().setRequestIdMapping(libId, internalId);
                break;
            default:
                break;
        }
    }

}

