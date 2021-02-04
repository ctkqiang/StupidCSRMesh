package com.johnmelodyme.stupidcsrdemo.app.API;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanSettings;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.csr.csrmesh2.MeshConstants;
import com.csr.csrmesh2.MeshService;
import com.csr.internal.mesh.client.api.common.Config;
import com.johnmelodyme.stupidcsrdemo.app.Const.Const;
import com.johnmelodyme.stupidcsrdemo.app.Util.LogLevel;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Class MeshLibraryManager :
 * an instance of Mesh Library
 */
public class MeshLibraryManager extends Thread
{
    protected static final String TAG = Const.meshlibrarymanager;
    private Handler meshHandler;
    public MeshService meshService;

    public static LogLevel _loglevel = LogLevel.NONE;

    public static MeshLibraryManager ___instance;

    public static final String EXTRA_TENANT_ID;
    public static final String EXTRA_MESH_ID;
    public static final String EXTRA_SITE_ID;
    public static final String EXTRA_NETWORK_PASS_PHRASE;
    public static final String EXTRA_REQUEST_ID;
    public static final String BRIDGE_ADDRESS;

    static
    {
        BRIDGE_ADDRESS = Const.meshbridgeaddress;
        EXTRA_SITE_ID = Const.meshsiteid;
        EXTRA_REQUEST_ID = Const.meshrequestid;
        EXTRA_MESH_ID = Const.meshid;
        EXTRA_TENANT_ID = Const.meshtenantid;
        EXTRA_NETWORK_PASS_PHRASE = Const.meshnetworkpassphrase;
    }

    private static final AtomicInteger requestId = new AtomicInteger(Const.MESH_REQUEST_ID);
    private final HashMap<Integer, Integer> requestIds = new HashMap<Integer, Integer>();
    private boolean isInternetAvailable;
    private boolean shutdown = false;
    private boolean isChannelReady = false;
    private boolean continuousScanning = false;
    public final WeakReference<Context> contextWeakReference;

    private final String selectedGatewayUUID = "";

    public enum MeshChannel
    {
        BLUETOOTH,
        INVALID,
        REST
    }

    public MeshChannel currentChannel = MeshChannel.INVALID;
    private RestChannel.RestMode currentRestMode;

    public static MeshLibraryManager getInstance()
    {
        return ___instance;
    }

    public boolean isServiceAvailable()
    {
        return (!(meshService == null));
    }

    private MeshLibraryManager(Context context, MeshChannel meshChannel, LogLevel logLevel)
    {
        contextWeakReference = new WeakReference<Context>(context);
        currentChannel = meshChannel;
        Intent bindIntent;
        bindIntent = new Intent(contextWeakReference.get(), MeshService.class);
        context.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public final ServiceConnection serviceConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service)
        {
            meshService = ((MeshService.LocalBinder) service).getService();
            if (!(meshService == null))
            {
                meshService.setHandler(getHandler());
                meshService.setLeScanCallback(scanCallBack);
                if ((currentChannel == MeshChannel.BLUETOOTH))
                {
                    /**
                     * Set the Bluetooth Bearer. This will start the stack, but
                     * we don't connect until we receive a stupid  MESSAGE_LE_BEARER_READY
                     */
                    enableBluetooth();
                } else if ((currentChannel == MeshChannel.REST))
                {
                    // Retrieve and Set appropriate REST parametre
                    RestChannel.RestMode restMode = getRestMode();
                    if (!(restMode == null))
                    {
                        String tenantId = "";
                        String siteId = "";
                        // TODO Database
                        setRestChannelEnabled(restMode, tenantId, siteId);
                    }
                } else
                {
                    Log.e(TAG, "onServiceConnected: NOTHING");
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name)
        {
            meshService = null;
        }
    };


    /**
     * Enabled REST Channel
     *
     * @param restMode
     * @param tenantId
     * @param siteId
     */
    public void setRestChannelEnabled(RestChannel.RestMode restMode, String tenantId, String siteId)
    {
        currentRestMode = restMode;
        if ((restMode == RestChannel.RestMode.GATEWAY))
        {
            meshService.setRestChannel(Config.Channel.CHANNEL_GATEWAY);
        } else
        {
            meshService.setRestChannel(Config.Channel.CHANNEL_CLOUD);
        }

        setRest(tenantId, siteId);
    }

    private void setRest(String tenantId, String siteId)
    {
        isChannelReady = false;
        currentChannel = MeshChannel.REST;
        meshService.setRestBearerEnabled(tenantId, siteId);
    }

    public RestChannel.RestMode getRestMode()
    {
        return currentRestMode;
    }

    private void enableBluetooth()
    {
        currentChannel = MeshChannel.BLUETOOTH;
        isChannelReady = false;
        if ((Build.VERSION.SDK_INT >= 21))
        {
            meshService.setBluetoothBearerEnabled(ScanSettings.SCAN_MODE_LOW_LATENCY);
        } else
        {
            meshService.setBluetoothBearerEnabled();
        }
    }

    private synchronized Handler getHandler()
    {
        while (meshHandler == null)
        {
            try
            {
                wait();
            } catch (InterruptedException exception)
            {
                Log.e(TAG, "getHandler: ", exception);
            }
        }

        return meshHandler;
    }

    public BluetoothAdapter.LeScanCallback scanCallBack = new BluetoothAdapter.LeScanCallback()
    {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord)
        {
            if (!(meshService == null))
            {
                if (meshService.processMeshAdvert(device, scanRecord, rssi))
                {
                    Bundle data;
                    data = new Bundle();
                    data.putParcelable(MeshConstants.EXTRA_DEVICE, device);
                    data.putInt(MeshConstants.EXTRA_RSSI, rssi);
                }
            }
        }
    };

    public static void initInstance(Context context, MeshChannel channel, LogLevel logLevel)
    {
        if ((___instance == null))
        {
            ___instance = new MeshLibraryManager(context, channel, logLevel);
            ___instance.start();
        }
    }

    public void shutdown()
    {
        shutdown = true;
        if ((currentChannel == MeshChannel.BLUETOOTH))
        {
            meshService.shutDown();
        } else
        {
            Log.d(TAG, "shutdown: ==> MESH SHUTTING DOWN....");
        }
    }

    public void onStart()
    {
        contextWeakReference.get().unbindService(serviceConnection);
        ___instance = null;
    }

    public ArrayList<String> getConnectedBridges()
    {
        return meshService.getConnectedBridges();
    }

    /**
     * Package
     */
    MeshService getMeshService()
    {
        return meshService;
    }

    /**
     * Send a message to a device to see if it needs to update it's security parameters.
     * If the device requests an update then this will be handled internally by the library, and
     * when it is complete the app will receive MESSAGE_DEVICE_KEY_IV_CHANGED.
     *
     * @param deviceId          The id of the device.
     * @param resetKey          The key returned when the device was associated.
     * @param networkPassPhrase The network pass phrase to respond with. If this doesn't match then the device will request an update.
     * @param networkIV         The network IV to respond with. If this doesn't match then the device will request an update.
     * @return Unique id to identify the request. Included in the response or timeout message as EXTRA_MESH_REQUEST_ID.
     */
    public int updateNetworkSecurity(int deviceId, byte[] resetKey, String networkPassPhrase, byte[] networkIV)
    {
        return meshService.updateNetworkSecurity(deviceId, resetKey, networkPassPhrase, networkIV);
    }

    /**
     * Enable Bluetooth channel. Calls to model APIs made after this will be sent via Bluetooth.
     * Event CHANNEL_CHANGE is sent to indicate channel has changed.
     * There will be a delay before a bridge is connected. When that completes the event
     * BRIDGE_CONNECTED will be sent.
     */
    public void setBluetoothChannelEnabled()
    {
        if (!(currentChannel == MeshChannel.BLUETOOTH))
        {
            enableBluetooth();
        }
    }

}
