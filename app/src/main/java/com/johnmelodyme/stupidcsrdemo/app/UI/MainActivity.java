package com.johnmelodyme.stupidcsrdemo.app.UI;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import com.johnmelodyme.stupidcsrdemo.R;
import com.johnmelodyme.stupidcsrdemo.app.Const.Const;

public class MainActivity extends AppCompatActivity
{
    private static final String TAG = Const.appname;

    @Override
    protected void onStart()
    {
        super.onStart();
        Log.d(TAG, "onStart =>  MainActivity Started");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

}