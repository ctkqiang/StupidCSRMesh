package com.johnmelodyme.stupidcsrdemo.app.model;

import android.os.Bundle;

import com.csr.csrmesh2.MeshConstants;
import com.csr.csrmesh2.PingModelApi;
import com.johnmelodyme.stupidcsrdemo.app.API.MeshLibraryManager;
import com.johnmelodyme.stupidcsrdemo.app.event.MeshRequestEvent;

/**
 * The Ping Model is used to ensure that the transport layer can provide reliable message delivery to a peer device.
 */
public class PingModel
{

    public static int request(int deviceId, byte[] data, byte rspTTL)
    {
        Bundle databundle = new Bundle();
        int id = MeshLibraryManager.getInstance().getNextRequestId();
        databundle.putInt(MeshLibraryManager.EXTRA_REQUEST_ID, id);
        databundle.putInt(MeshConstants.EXTRA_DEVICE_ID, deviceId);
        databundle.putByteArray(MeshConstants.EXTRA_PING_DATA, data);
        databundle.putByte(MeshConstants.EXTRA_PING_RSP_TTL, rspTTL);
//        App.bus.post(new MeshRequestEvent(MeshRequestEvent.RequestEvent.PING_REQUEST, databundle));
        return id;
    }

    public static void handleRequest(MeshRequestEvent event)
    {
        int deviceId = event.data.getInt(MeshConstants.EXTRA_DEVICE_ID);
        int libId = 0;
        switch (event.what)
        {
            case PING_REQUEST:
                byte[] data = event.data.getByteArray(MeshConstants.EXTRA_PING_DATA);
                byte rspTTL = event.data.getByte(MeshConstants.EXTRA_PING_RSP_TTL);
                libId = PingModelApi.request(deviceId, data, rspTTL);
                break;
        }
        if (libId != 0)
        {
            int internalId = event.data.getInt(MeshLibraryManager.EXTRA_REQUEST_ID);
            MeshLibraryManager.getInstance().setRequestIdMapping(libId, internalId);
        }
    }
}
