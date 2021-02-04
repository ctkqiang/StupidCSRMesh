package com.johnmelodyme.stupidcsrdemo.app.Const;

/**
 * @Constant value
 * Singletons
 */
public class Const
{
    /**
     * @String Constant { Strings }
     * \\ Global String
     */
    public static String appname;
    public static String meshlibrarymanager;
    public static String meshnetworkpassphrase;
    public static String meshtenantid;
    public static String meshsiteid;
    public static String meshid;
    public static String meshrequestid;
    public static String meshcloudhost;
    public static String meshrestapplcationcode;
    public static String meshcloudport;
    public static String meshgatewayhost;
    public static String meshgatewayport;
    public static String meshbasepataaa;
    public static String meshbasepatcnc;
    public static String meshbasepatconfig;
    public static String meshbasepatauth;
    public static String meshtenantname;
    public static String meshsitename;
    public static String meshmeshid;
    public static String meshdeviceid;
    public static String meshbasepathcgi;
    public static String meshurischemahttp;
    public static String meshurischemahttps;
    public static String meshbasecsrmesh;
    public static String meshbridgeaddress;

    static
    {
        meshbridgeaddress = "00:02:5B:00:33:01";
        meshbasecsrmesh = "/csrmesh";
        meshurischemahttps = "https";
        meshurischemahttp = "http";
        meshbasepathcgi = "/cgi-bin";
        meshdeviceid = "12";
        meshmeshid = "mesg456";
        meshsitename = "siteid_123";
        meshtenantname = "tenantid_123";
        meshbasepatauth = "/csrmesh/security";
        meshbasepatconfig = "/csrmesh/config";
        meshbasepatcnc = "/csrmesh/cnc";
        meshbasepataaa = "/csrmesh/aaa";
        meshgatewayport = "80";
        meshgatewayhost = "192.168.1.1";
        meshcloudport = "443";
        meshrestapplcationcode = "app_123";
        meshcloudhost = "csrmesh-2-1.csrlbs.com";
        appname = "CSRMESH";
        meshlibrarymanager = "MeshLibraryManager";
        meshnetworkpassphrase = "1234";
        meshtenantid = "EXTRA_TENANT_ID";
        meshsiteid = "EXTRA_SITE_ID";
        meshid = "EXTRA_MESH_ID";
        meshrequestid = "INTERNALREQUESTID";
    }

    /**
     * @Integers Constant { Integers }
     * \\ Global Integers
     */
    public static int MESH_REQUEST_ID;

    static
    {
        MESH_REQUEST_ID = 0;
    }

}
