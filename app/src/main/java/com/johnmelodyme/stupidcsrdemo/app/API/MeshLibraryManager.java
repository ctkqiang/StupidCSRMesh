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
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.csr.csrmesh2.MeshConstants;
import com.csr.csrmesh2.MeshService;
import com.csr.internal.mesh.client.api.AuthListener;
import com.csr.internal.mesh.client.api.MeshSecurityApi;
import com.csr.internal.mesh.client.api.common.Config;
import com.johnmelodyme.stupidcsrdemo.app.Const.Const;
import com.johnmelodyme.stupidcsrdemo.app.Util.LocalLog;
import com.johnmelodyme.stupidcsrdemo.app.Util.LogLevel;
import com.johnmelodyme.stupidcsrdemo.app.Util.UUIDUTILS;
import com.johnmelodyme.stupidcsrdemo.app.event.MeshRequestEvent;
import com.johnmelodyme.stupidcsrdemo.app.interfaces.AppComponent;
import com.johnmelodyme.stupidcsrdemo.app.model.ActionModel;
import com.johnmelodyme.stupidcsrdemo.app.model.ActuatorModel;
import com.johnmelodyme.stupidcsrdemo.app.model.AttentionModel;
import com.johnmelodyme.stupidcsrdemo.app.model.BatteryModel;
import com.johnmelodyme.stupidcsrdemo.app.model.BearerModel;
import com.johnmelodyme.stupidcsrdemo.app.model.ConfigCloud;
import com.johnmelodyme.stupidcsrdemo.app.model.ConfigGateway;
import com.johnmelodyme.stupidcsrdemo.app.model.ConfigModel;
import com.johnmelodyme.stupidcsrdemo.app.model.DataModel;
import com.johnmelodyme.stupidcsrdemo.app.model.FirmwareModel;
import com.johnmelodyme.stupidcsrdemo.app.model.Gateway;
import com.johnmelodyme.stupidcsrdemo.app.model.GroupModel;
import com.johnmelodyme.stupidcsrdemo.app.model.LightModel;
import com.johnmelodyme.stupidcsrdemo.app.model.PingModel;
import com.johnmelodyme.stupidcsrdemo.app.model.PowerModel;
import com.johnmelodyme.stupidcsrdemo.app.model.SensorModel;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
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

    private String selectedGatewayUUID = "";

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

    public boolean isChannelReady()
    {
        return isChannelReady;
    }

    AuthListener mAuthListener = state ->
    {
        Log.d(TAG, "Authentication State update: " + state.toString());

        switch (state)
        {

            case NOT_AUTHENTICATED:
            {
                Log.e(TAG, "Not Authenticated");
                break;
            }
            case AUTHENTICATION_INPROGRESS:
            {
                Log.e(TAG, "Authenticating...");
                break;
            }
            case AUTHENTICATION_FAILED:
            {
                Log.e(TAG, "Authentication FAILED");
                break;
            }
            case AUTHENTICATED:
            {
                Log.i(TAG, "Authenticated");
                break;
            }
            case AUTHENTICATION_EXPIRED:
            {
                Log.e(TAG, "AUTHENTICATION_EXPIRED - Can not authenticate if a Gateway is not selected.");
                break;
            }
            default:
            {
                break;
            }
        }
    };

    public void authenticateRest(Gateway gateway)
    {
        if (!(isRestAuthenticated()))
        {
            String controlUUID;
            controlUUID = UUIDUTILS.getControllerUUID(meshService).toString();
            RestChannel.setRestParametres(
                    MeshService.ServerComponent.AUTH,
                    gateway.getHost(),
                    gateway.getPort(),
                    RestChannel.BASE_PATH_CGI +
                            RestChannel.BASE_PATH_AUTH,
                    RestChannel.URI_SCHEMA_HTTP
            );
            meshService.authenticateRest(controlUUID, gateway.getDhmKey(), mAuthListener);
        }
    }

    public boolean isRestAuthenticated()
    {
        return meshService.isRestAuthenticated();
    }

    public void setRestChannelEnabled()
    {
        setRest(null, null);
    }

    public String getMeshId()
    {
        return meshService.getMeshId();
    }

    public MeshChannel getChannel()
    {
        return currentChannel;
    }

    public void restartBonjour()
    {
        meshService.restartNSDManager();
    }

    public void setNetworkPassPhrase(String networkPassPhrase)
    {
        meshService.setNetworkPassPhrase(networkPassPhrase);
    }

    public void setControllerAddress(int address)
    {
        meshService.setControllerAddress(address);
    }

    public void setApplicationCode(String applicationCode)
    {
        meshService.setApplicationCode(applicationCode);
    }

    public void setIsInternetAvailable(boolean state)
    {
        isInternetAvailable = state;
    }

    public void setNetworkKey(byte[] networkKey)
    {
        meshService.setNetworkKey(networkKey);
    }

    public int getNextRequestId()
    {
        return requestId.incrementAndGet();
    }

    public void setRequestIdMapping(int libraryId, int internalId)
    {
        requestIds.put(libraryId, internalId);
    }

    int getRequestId(int libraryId)
    {
        if (requestIds.containsKey(libraryId))
        {
            return requestIds.get(libraryId);
        } else
        {
            return 0;
        }
    }

    /**
     * Given the request databaseId that is used in the wrapper, find the mesh request databaseId that
     * was returned from the library.
     *
     * @param requestId Wrapper request databaseId.
     * @return Mesh request databaseId from library, or zero if none found.
     */
    /*package*/ int getMeshRequestId(int requestId)
    {
        for (int libId : requestIds.keySet())
        {
            if (requestId == requestIds.get(libId))
            {
                return libId;
            }
        }
        return 0;
    }

    /**
     * Set bluetooth channel as bearer and connect to a bridge
     */
    private void connectBluetooth()
    {
        meshService.setMeshListeningMode(
                true,
                false
        );
        meshService.startAutoConnect(1);
        meshService.setContinuousLeScanEnabled(true);
    }

    /**
     * Automatically connect to numBridges bridges.
     *
     * @param numDevices Number of devices desired to be connected to.
     */
    public void startAutoConnect(int numDevices)
    {
        meshService.startAutoConnect(numDevices);
    }

    /**
     * Return if autoconnect to bridges is enabled or not.
     *
     * @return True if autoconnect is enabled
     */
    public boolean isAutoConnectEnabled()
    {
        return meshService.isAutoConnectEnabled();
    }

    /**
     * Stop the autoconnection with bridges.
     */
    public void stopAutoconnect()
    {
        meshService.stopAutoConnect();
    }

    /**
     * Connect to a specific bluetooth device.
     *
     * @param device
     */
    public void connectDevice(BluetoothDevice device)
    {
        meshService.connectBridge(device);
    }

    /**
     * Disconnect from all connected Bluetooth LE bridge devices.
     * Bridges will not be automatically reconnected after disconnect.
     */
    public void disconnectAllDevices()
    {
        meshService.disconnectAllBridges();
    }

    /**
     * Disconnect from a specific bluetooth device.
     *
     * @param btAddress Bluetooth device address to disconnect.
     */
    public void disconnectDevice(String btAddress)
    {
        meshService.disconnectBridge(btAddress);
    }

    private static class MeshApiMessageHandler extends Handler
    {
        private WeakReference<MeshLibraryManager> parent;

        MeshApiMessageHandler(MeshLibraryManager machine)
        {
            this.parent = new WeakReference<MeshLibraryManager>(machine);
        }

        @Override
        public void handleMessage(Message msg)
        {
            // If the message contains a mesh request databaseId then translate it to our internal databaseId.
            int meshRequestId = msg.getData().getInt(MeshConstants.EXTRA_MESH_REQUEST_ID);
            Bundle data = msg.getData();
            if (!(meshRequestId == 0))
            {
                int internalId = parent.get().getRequestId(meshRequestId);
                if (!(internalId == 0))
                {
                    data.putInt(MeshLibraryManager.EXTRA_REQUEST_ID, internalId);
                    // No longer need to keep this mapping now that we have the response.
                    if (!(msg.what == MeshConstants.MESSAGE_ASSOCIATING_DEVICE))
                    {
                        parent.get().requestIds.remove(meshRequestId);
                    }
                }
                // Remove from the bundle as the client isn't interested in the library databaseId,
                // only the wrapper databaseId.
                data.remove(MeshConstants.EXTRA_MESH_REQUEST_ID);
            }

            // Handle mesh API messages and notify. Use the data variable NOT msg.getData() when retrieving data.
            switch (msg.what)
            {
                case MeshConstants.MESSAGE_LE_BEARER_READY:
                {
                    parent.get().connectBluetooth();
                    break;
                }
                case MeshConstants.MESSAGE_REST_BEARER_READY:
                {
                    if (parent.get().meshService.isRestConfigured())
                    {
                        parent.get().isChannelReady = true;
                    }
                    break;
                }
                case MeshConstants.MESSAGE_LE_CONNECTED:
                {
                    Log.d(TAG, "MeshConstants.MESSAGE_LE_CONNECTED " + msg.getData().getString(MeshConstants.EXTRA_DEVICE_ADDRESS));
                    if (parent.get().getChannel() == MeshChannel.BLUETOOTH)
                    {
                        parent.get().isChannelReady = true;
                    }
                    break;
                }
                case MeshConstants.MESSAGE_LE_DISCONNECTED:
                {
                    LocalLog.warning(TAG, "Response MESSAGE_LE_DISCONNECT");
                    if (parent.get().getChannel() == MeshChannel.BLUETOOTH)
                    {
                        parent.get().isChannelReady = false;
                    }
                    break;
                }
                case MeshConstants.MESSAGE_LE_DISCONNECT_COMPLETE:
                {
                    LocalLog.warning(TAG, "Response MESSAGE_LE_DISCONNECT_COMPLETE");
                    if (!parent.get().shutdown)
                    {
                        // Check if network is available.
                        if (parent.get().isInternetAvailable)
                        {
                            parent.get().isChannelReady = true;
                        } else
                        {
                            LocalLog.warning(TAG, "Response MESSAGE_LE_TEST");
                            // TODO: handle scenario of Bluetooth disconnected and network not available - notify user he needs at least bluetooth or internet to control?
                        }
                    } else
                    {
                        parent.get().isChannelReady = false;
                        parent.get().currentChannel = MeshChannel.INVALID;
                    }
                    break;
                }

                case MeshConstants.MESSAGE_GATEWAY_SERVICE_DISCOVERED:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_GATEWAY_SERVICE_DISCOVERED");
//                    App.bus.post(new MeshSystemEvent(MeshSystemEvent.SystemEvent.GATEWAY_DISCOVERED, data));
                    break;
                }
                case MeshConstants.MESSAGE_DEVICE_APPEARANCE:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_DEVICE_APPEARANCE");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.DEVICE_APPEARANCE, data));
                    break;
                }
                case MeshConstants.MESSAGE_RESET_DEVICE:
                    /* The application can handle a request to reset here.
                     * It should calculate the signature using ConfigModelApi.computeResetDeviceSignatureWithDeviceHash(long, byte[])
                     * to check the signature is valid.
                     */
                    break;
                case MeshConstants.MESSAGE_BATTERY_STATE:
                {
                    Log.i(TAG, "handleMessage: BATTERY");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.BATTERY_STATE, data));
                    break;
                }
                case MeshConstants.MESSAGE_LIGHT_STATE:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_LIGHT_STATE");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.LIGHT_STATE, data));
                    break;
                }
                case MeshConstants.MESSAGE_POWER_STATE:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_POWER_STATE");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.POWER_STATE, data));
                    break;
                }
                case MeshConstants.MESSAGE_ACTION_SENT:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_ACTION_SENT");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.ACTION_SENT, data));
                    break;
                }
                case MeshConstants.MESSAGE_ACTION_DELETE:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_ACTION_DELETE");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.ACTION_DELETED, data));
                    break;
                }
                case MeshConstants.MESSAGE_ASSOCIATING_DEVICE:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_ASSOCIATING_DEVICE");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.ASSOCIATION_PROGRESS, data));
                    break;
                }
                case MeshConstants.MESSAGE_LOCAL_DEVICE_ASSOCIATED:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_LOCAL_DEVICE_ASSOCIATED");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.LOCAL_DEVICE_ASSOCIATED, data));
                    break;
                }
                case MeshConstants.MESSAGE_LOCAL_ASSOCIATION_FAILED:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_LOCAL_ASSOCIATION_FAILED");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.LOCAL_DEVICE_FAILED, data));
                    break;
                }
                case MeshConstants.MESSAGE_ASSOCIATION_PROGRESS:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_ASSOCIATION_PROGRESS");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.LOCAL_ASSOCIATION_PROGRESS, data));
                    break;
                }
                case MeshConstants.MESSAGE_NETWORK_SECURITY_UPDATE:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_NETWORK_SECURITY_UPDATE");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.MESSAGE_NETWORK_SECURITY_UPDATE, data));
                    break;
                }
                case MeshConstants.MESSAGE_REQUEST_BT:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_REQUEST_BT");
//                    App.bus.post(new MeshSystemEvent(MeshSystemEvent.SystemEvent.BT_REQUEST));
                    break;
                }
                case MeshConstants.MESSAGE_DEVICE_ASSOCIATED:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_DEVICE_ASSOCIATED");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.DEVICE_ASSOCIATED, data));
                    break;
                }
                case MeshConstants.MESSAGE_TIMEOUT:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_TIMEOUT");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.TIMEOUT, data));
                    break;
                }
                case MeshConstants.MESSAGE_ATTENTION_STATE:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_ATTENTION_STATE");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.ATTENTION_STATE, data));
                    break;
                }
                case MeshConstants.MESSAGE_ACTUATOR_VALUE_ACK:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_ACTUATOR_VALUE_ACK");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.ACTUATOR_VALUE, data));
                    break;
                }
                case MeshConstants.MESSAGE_ACTUATOR_TYPES:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_ACTUATOR_TYPES");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.ACTUATOR_TYPES, data));
                    break;
                }
                case MeshConstants.MESSAGE_BEARER_STATE:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_BEARER_STATE");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.BEARER_STATE, data));
                    break;
                }
                case MeshConstants.MESSAGE_SENSOR_STATE:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_SENSOR_STATE");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.SENSOR_STATE, data));
                    break;
                }
                case MeshConstants.MESSAGE_SENSOR_VALUE:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_SENSOR_VALUE");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.SENSOR_VALUE, data));
                    break;
                }
                case MeshConstants.MESSAGE_SENSOR_TYPES:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_SENSOR_TYPES");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.SENSOR_TYPES, data));
                    break;
                }
                case MeshConstants.MESSAGE_PING_RESPONSE:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_PING_RESPONSE");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.PING_RESPONSE, data));
                    break;
                }
                case MeshConstants.MESSAGE_GROUP_NUM_GROUPIDS:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_GROUP_NUM_GROUPIDS");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.GROUP_NUMBER_OF_MODEL_GROUPIDS, data));
                    break;
                }
                case MeshConstants.MESSAGE_GROUP_MODEL_GROUPID:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_GROUP_MODEL_GROUPID");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.GROUP_MODEL_GROUPID, data));
                    break;
                }
                case MeshConstants.MESSAGE_FIRMWARE_VERSION:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_FIRMWARE_VERSION");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.FIRMWARE_VERSION_INFO, data));
                    break;
                }
                case MeshConstants.MESSAGE_DATA_SENT:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_DATA_SENT");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.DATA_SENT, data));
                    break;
                }
                case MeshConstants.MESSAGE_RECEIVE_BLOCK_DATA:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_RECEIVE_BLOCK_DATA");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.DATA_RECEIVE_BLOCK, data));
                    break;
                }
                case MeshConstants.MESSAGE_RECEIVE_STREAM_DATA:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_RECEIVE_STREAM_DATA");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.DATA_RECEIVE_STREAM, data));
                    break;
                }
                case MeshConstants.MESSAGE_RECEIVE_STREAM_DATA_END:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_RECEIVE_STREAM_DATA_END");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.DATA_RECEIVE_STREAM_END, data));
                    break;
                }
                case MeshConstants.MESSAGE_DEVICE_ID:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_DEVICE_ID");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.CONFIG_DEVICE_IDENTIFIER, data));
                    break;
                }
                case MeshConstants.MESSAGE_CONFIG_DEVICE_INFO:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_CONFIG_DEVICE_INFO");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.CONFIG_INFO, data));
                    break;
                }
                case MeshConstants.MESSAGE_DEVICE_DISCOVERED:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_DEVICE_DISCOVERED");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.DEVICE_UUID, data));
                    break;
                }
                case MeshConstants.MESSAGE_PARAMETERS:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_PARAMETERS");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.CONFIG_PARAMETERS, data));
                    break;
                }
                case MeshConstants.MESSAGE_GATEWAY_PROFILE:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_GATEWAY_PROFILE");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.GATEWAY_PROFILE, data));
                    break;
                }
                case MeshConstants.MESSAGE_GATEWAY_REMOVE_NETWORK:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_GATEWAY_REMOVE_NETWORK");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.GATEWAY_REMOVE_NETWORK, data));
                    break;
                }
                case MeshConstants.MESSAGE_TENANT_RESULTS:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_TENANT_RESULTS");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.TENANT_RESULTS, data));
                    break;
                }
                case MeshConstants.MESSAGE_TENANT_CREATED:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_TENANT_CREATED");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.TENANT_CREATED, data));
                    break;
                }
                case MeshConstants.MESSAGE_TENANT_INFO:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_TENANT_INFO");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.TENANT_INFO, data));
                    break;
                }
                case MeshConstants.MESSAGE_TENANT_DELETED:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_TENANT_DELETED");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.TENANT_DELETED, data));
                    break;
                }
                case MeshConstants.MESSAGE_TENANT_UPDATED:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_TENANT_UPDATED");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.TENANT_UPDATED, data));
                    break;
                }
                case MeshConstants.MESSAGE_SITE_RESULTS:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_SITE_RESULTS");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.SITE_RESULTS, data));
                    break;
                }
                case MeshConstants.MESSAGE_SITE_CREATED:
                {
                    if (parent.get().getChannel() == MeshChannel.REST)
                    {
                        Log.i(TAG, "handleMessage: MESSAGE_SITE_CREATED");
//                        App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.SITE_CREATED, data));
                    }
                    break;
                }
                case MeshConstants.MESSAGE_SITE_INFO:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_SITE_INFO");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.SITE_INFO, data));
                    break;
                }
                case MeshConstants.MESSAGE_SITE_DELETED:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_SITE_DELETED");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.SITE_DELETED, data));
                    break;
                }
                case MeshConstants.MESSAGE_SITE_UPDATED:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_SITE_UPDATED");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.SITE_UPDATED, data));
                    break;
                }
                case MeshConstants.MESSAGE_GATEWAY_FILE_INFO:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_GATEWAY_FILE_INFO");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.GATEWAY_FILE_INFO, data));
                    break;
                }
                case MeshConstants.MESSAGE_GATEWAY_FILE:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_GATEWAY_FILE");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.GATEWAY_FILE, data));
                    break;
                }
                case MeshConstants.MESSAGE_GATEWAY_FILE_CREATED:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_GATEWAY_FILE_CREATED");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.GATEWAY_FILE_CREATED, data));
                    break;
                }
                case MeshConstants.MESSAGE_GATEWAY_FILE_DELETED:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_GATEWAY_FILE_DELETED");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.GATEWAY_FILE_DELETED, data));
                    break;
                }
                case MeshConstants.MESSAGE_FIRMWARE_UPDATE_ACKNOWLEDGED:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_FIRMWARE_UPDATE_ACKNOWLEDGED");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.FIRMWARE_UPDATE_ACKNOWLEDGED, data));
                    break;
                }
                case MeshConstants.MESSAGE_TRANSACTION_NOT_CANCELLED:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_TRANSACTION_NOT_CANCELLED");
                    break;
                }
                case MeshConstants.MESSAGE_REST_ERROR:
                {
                    Log.i(TAG, "handleMessage: MESSAGE_REST_ERROR ");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.ERROR, data));
                    break;
                }
                case MeshConstants.MESSAGE_TIME_STATE:
                {
                    int timeInterval = data.getInt(MeshConstants.EXTRA_TIME_INTERVAL);
                    Log.i(TAG, "handleMessage: MESSAGE_TIME_STATE");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.TIME_STATE, data));
                    break;
                }
                case MeshConstants.MESSAGE_LOT_ANNOUNCE:
                {
                    LocalLog.warning(TAG, "Lot announce response");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.LOT_INTEREST, data));
                    break;
                }
                case MeshConstants.MESSAGE_LOT_INTEREST:
                {
                    LocalLog.warning(TAG, "Lot interest response");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.LOT_INTEREST, data));
                    break;
                }
                case MeshConstants.MESSAGE_DIAGNOSTIC_STATE:
                {
                    LocalLog.warning(TAG, "response for DIAGNOSTIC_STATE ");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.DIAGNOSTIC_STATE, data));
                    break;
                }
                case MeshConstants.MESSAGE_DIAGNOSTIC_STATS:
                {
                    LocalLog.warning(TAG, "response for DIAGNOSTIC_STATES");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.DIAGNOSTIC_STATS, data));
                    break;
                }
                case MeshConstants.MESSAGE_TRACKER_REPORT:
                {
                    LocalLog.warning(TAG, "response for TRACKER_REPORT");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.TRACKER_REPORT, data));
                    break;
                }
                case MeshConstants.MESSAGE_WATCHDOG_INTERVAL:
                {
                    LocalLog.warning(TAG, "response for WATCHDOG_INTERVAL");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.WATCHDOG_INTERVAL, data));
                    break;
                }
                case MeshConstants.MESSAGE_WATCHDOG_MESSAGE:
                {
                    LocalLog.warning(TAG, "response for WATCHDOG_MESSAGE");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.WATCHDOG_MESSAGE, data));
                    break;
                }
                case MeshConstants.MESSAGE_TRACKER_FOUND:
                {
                    LocalLog.warning(TAG, "response for TRACKER_FOUND");
//                    App.bus.post(new MeshResponseEvent(MeshResponseEvent.ResponseEvent.TRACKER_FOUND, data));
                    break;
                }
                default:
                {
                    break;
                }
            }
        }
    }

    @Override
    public void run()
    {
        // Just process the message loop.
        Looper.prepare();
        synchronized (this)
        {
            meshHandler = new MeshApiMessageHandler(this);
            notifyAll();
        }
        Looper.loop();
    }

    /**
     * @param event
     * @Subscribe
     */
    public void onEvent(MeshRequestEvent event)
    {
        switch (event.what)
        {

            // Killing transactions
            case KILL_TRANSACTION:

                int id = getMeshRequestId(event.data.getInt(MeshConstants.EXTRA_DATA));
                meshService.cancelTransaction(id);
                break;
            // Association:
            case DISCOVER_DEVICES:
            case ATTENTION_PRE_ASSOCIATION:
            case ASSOCIATE_DEVICE:
            case ASSOCIATE_GATEWAY:
            case START_ADVERTISING:
            case STOP_ADVERTISING:
                Association.handleRequest(event);
                break;

            case MASP_RESET:
                Association.handleRequest(event);
                break;

            // Bonjour
            case START_BROWSING_GATEWAYS:
                meshService.startBrowsing();
                break;
            case STOP_BROWSING_GATEWAYS:
                meshService.stopBrowsing();
                break;

            // MCP events. These are all handled by the model classes.
            case POWER_GET_STATE:
            case POWER_SET_STATE:
            case POWER_TOGGLE_STATE:
                PowerModel.handleRequest(event);
                break;

            case LIGHT_GET_STATE:
            case LIGHT_SET_LEVEL:
            case LIGHT_SET_COLOR_TEMPERATURE:
            case LIGHT_SET_POWER_LEVEL:
            case LIGHT_SET_RGB:
            case LIGHT_SET_WHITE:
                LightModel.handleRequest(event);
                break;

            case ATTENTION_SET_STATE:
                AttentionModel.handleRequest(event);
                break;

            case ACTION_SET_ACTION:
            case ACTION_DELETE_ACTION:
                ActionModel.handleRequest(event);
                break;

            case ACTUATOR_GET_TYPES:
            case ACTUATOR_SET_VALUE:
                ActuatorModel.handleRequest(event);
                break;

            case BEARER_SET_STATE:
            case BEARER_GET_STATE:
                BearerModel.handleRequest(event);
                break;

            case BATTERY_GET_STATE:

            case TIME_SET_STATE:
            case TIME_GET_STATE:
            case TIME_BROADCAST:
                BatteryModel.handleRequest(event);
                break;

            case SENSOR_GET_VALUE:
            case SENSOR_SET_VALUE:
            case SENSOR_GET_TYPES:
            case SENSOR_SET_STATE:
            case SENSOR_GET_STATE:
                SensorModel.handleRequest(event);
                break;

            case PING_REQUEST:
                PingModel.handleRequest(event);
                break;

            case GROUP_GET_NUMBER_OF_MODEL_GROUP_IDS:
            case GROUP_SET_MODEL_GROUP_ID:
            case GROUP_GET_MODEL_GROUP_ID:
                GroupModel.handleRequest(event);
                break;

            case FIRMWARE_UPDATE_REQUIRED:
            case FIRMWARE_GET_VERSION:
                FirmwareModel.handleRequest(event);
                break;

            case DATA_SEND_DATA:
                DataModel.handleRequest(event);
                break;

            case CONFIG_DISCOVER_DEVICE:
            case CONFIG_GET_INFO:
            case CONFIG_RESET_DEVICE:
            case CONFIG_SET_DEVICE_IDENTIFIER:
            case CONFIG_GET_PARAMETERS:
            case CONFIG_SET_PARAMETERS:
                ConfigModel.handleRequest(event);
                break;

            // Gateway REST events.
            case GATEWAY_GET_PROFILE:
            case GATEWAY_REMOVE_NETWORK:
                ConfigGateway.handleRequest(event);
                break;

            // Cloud REST events.
            case CLOUD_GET_TENANTS:
            case CLOUD_CREATE_TENANT:
            case CLOUD_GET_TENANT_INFO:
            case CLOUD_DELETE_TENANT:
            case CLOUD_UPDATE_TENANT:
            case CLOUD_GET_SITES:
            case CLOUD_CREATE_SITE:
            case CLOUD_GET_SITE_INFO:
            case CLOUD_DELETE_SITE:
            case CLOUD_UPDATE_SITE:
                ConfigCloud.handleRequest(event);
                break;

            // Configuration
            case SET_CONTINUOUS_SCANNING:
                if (getChannel() == MeshChannel.BLUETOOTH)
                {
                    boolean enable = event.data.getBoolean(MeshConstants.EXTRA_DATA);
                    boolean wear = event.data.getBoolean(MeshConstants.EXTRA_WEAR);
                    if (wear)
                    {
                        Log.i(TAG, "onEvent: WEAR ?");
                    }

                    /*
                    if ((mContinuousScanningWear == true || mContinuousScanningMobile == true) && !mCurrentContinuousScanning)
                    {
                        // enabling continuous scan.
                        mCurrentContinuousScanning = true;
                        BluetoothChannel.setContinuousScanEnabled(mCurrentContinuousScanning);
                    } else if (mContinuousScanningWear == false && mContinuousScanningMobile == false && mCurrentContinuousScanning)
                    {
                        // disabling continuous scan.
                        mCurrentContinuousScanning = false;
                        BluetoothChannel.setContinuousScanEnabled(mCurrentContinuousScanning);
                    }
                     */
                }
                break;

            case SET_CONTROLLER_ADDRESS:
                int address = event.data.getInt(MeshConstants.EXTRA_DATA);
                meshService.setControllerAddress(address);
                break;

            case LOT_ANNOUNCE:
            case LOT_INTEREST:
            case DIAGNOSTIC_GET_STATS:
                break;
        }
    }

    /**
     * Determine if bluetooth bridge is connected
     *
     * @return true if bridge is connected, false if not.
     */
    public static boolean isBluetoothBridgeReady()
    {
        MeshLibraryManager.MeshChannel channel = MeshLibraryManager.getInstance().getChannel();
        if (channel == MeshLibraryManager.MeshChannel.BLUETOOTH && (MeshLibraryManager.getInstance().isChannelReady()))
        {
            return true;
        } else
        {
            return false;
        }
    }

    /**
     * Set selected gateway UUID to be used in local gateway control
     *
     * @param uuid selected gateway uuid
     */
    public void setSelectedGatewayUUID(final String uuid)
    {
        Log.d(TAG, "*** GW SELECTED: " + uuid);
        selectedGatewayUUID = uuid;
    }

    public String getSelectedGatewayUUID()
    {
        return selectedGatewayUUID;
    }

    private void initializeInjector(AppComponent appComponent)
    {
        /*
        DaggerMeshLibraryManagerComponent.builder()
                .appComponent(appComponent)
                .build()
                .inject(this);
         */
    }
}
