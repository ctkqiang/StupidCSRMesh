package com.johnmelodyme.stupidcsrdemo.app.API;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.csr.csrmesh2.MeshService;
import com.johnmelodyme.stupidcsrdemo.R;
import com.johnmelodyme.stupidcsrdemo.app.Const.Const;

public class RestChannel
{
    public enum RestMode
    {
        GATEWAY,
        CLOUD
    }

    public static final String CLOUD_HOST = Const.meshcloudhost;
    public static final String REST_APPLICATION_CODE = Const.meshrestapplcationcode;
    public static final String CLOUD_PORT = Const.meshcloudport;
    public static final String GATEWAY_HOST = Const.meshgatewayhost;
    public static final String GATEWAY_PORT = Const.meshgatewayport;
    public static final String BASE_PATH_AAA = Const.meshbasepataaa;
    public static final String BASE_PATH_CNC = Const.meshbasepatcnc;
    public static final String BASE_PATH_CONFIG = Const.meshbasepatconfig;
    public static final String BASE_PATH_AUTH = Const.meshbasepatauth;
    public static final String TENANT_NAME = Const.meshtenantname;
    public static final String SITE_NAME = Const.meshsitename;
    public static final String MESH_ID = Const.meshmeshid;
    public static final String DEVICE_ID = Const.meshdeviceid;
    public static final String BASE_PATH_CGI = Const.meshbasepathcgi;
    public static final String URI_SCHEMA_HTTP = Const.meshurischemahttp;
    public static final String URI_SCHEMA_HTTPS = Const.meshurischemahttps;
    public static final String BASE_CSRMESH = Const.meshbasecsrmesh;

    /**
     * Method to retrieve this host stored in the cached variables or return the default value
     *
     * @param context
     * @return Host to the used by the app
     */
    public static String getCloudHost(Context context)
    {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String storedCloudPort = sharedPreferences.getString(
                context.getString(R.string.cloudport),
                ""
        );

        if (storedCloudPort.equals(""))
        {
            return CLOUD_PORT;
        } else
        {
            return storedCloudPort;
        }

    }

    public static String getCloudAppCode(Context context)
    {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String storedCloudAppCode = sharedPreferences.getString(
                context.getString(R.string.pref_cloud_key_appcode),
                ""
        );

        if (storedCloudAppCode.equals(""))
        {
            return REST_APPLICATION_CODE;
        } else
        {
            return storedCloudAppCode;
        }
    }

    public static void setRestParametres(MeshService.ServerComponent meshServerComponent, String host, String port, String basepath, String urischeme)
    {
        Log.d(Const.appname, "setRestParametres: " +
                meshServerComponent.toString() +
                "- host: " +
                host +
                "- port: " +
                port +
                "- basepath: " +
                basepath +
                "- uriScheme: " +
                urischeme
        );

        MeshLibraryManager.getInstance().getMeshService().setRestParams(
                meshServerComponent,
                host,
                port,
                basepath,
                urischeme
        );
    }

    public static void setTenant(String tenantId)
    {
        MeshLibraryManager.getInstance().getMeshService().setTenantId(tenantId);
    }

    public static void setSite(String siteId)
    {
        MeshLibraryManager.getInstance().getMeshService().setSiteId(siteId);
    }
}
