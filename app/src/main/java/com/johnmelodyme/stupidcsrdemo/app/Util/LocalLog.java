package com.johnmelodyme.stupidcsrdemo.app.Util;

import android.util.Log;

import com.johnmelodyme.stupidcsrdemo.app.API.MeshLibraryManager;

/**
 * @Logger Debugger log output for Developer mode;
 */
public class LocalLog
{
    public static void error(String i, String v)
    {
        if (MeshLibraryManager.loglevel.ordinal() >= LogLevel.ERROR.ordinal())
        {
            Log.e(i, v);
        }
    }

    public static void warning(String i, String v)
    {
        if (MeshLibraryManager.loglevel.ordinal() >= LogLevel.WARNING.ordinal())
        {
            Log.w(i, v);
        }
    }

    public static void info(String i, String v)
    {
        if (MeshLibraryManager.loglevel.ordinal() >= LogLevel.INFO.ordinal())
        {
            Log.i(i, v);
        }
    }

    public static void debug(String i, String v)
    {
        if (MeshLibraryManager.loglevel.ordinal() >= LogLevel.DEBUG.ordinal())
        {
            Log.d(i, v);
        }
    }
}
