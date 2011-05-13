package com.yaseriesapps.vonageam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.sax.StartElementListener;
import android.util.Log;
import android.widget.Toast;

public class VOutboundRcvr extends BroadcastReceiver
{
	final String LOGTAG = "VonageOptions";
    
	@Override
	public void onReceive(Context context, Intent intent) 
	{
		if (intent.getAction().equals(Intent.ACTION_NEW_OUTGOING_CALL)) 
		{
			Log.d(LOGTAG, "Outgoing number as intercepted : " + getResultData());
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			if(prefs.getBoolean("autointlcalls_pref", false)) // If Auto Intl calls enabled
			{
				String number = getResultData();
				int numLength = number.length();
				
				// Since it takes too long to set Forward and stuff, we call the VPlaceCall Activity 
				// with Extra data as the number to forward to.

				// Check for what kind of number it is before shipping it off to VPlaceCall
				if(number.contains("+") || numLength > 11) // This is an international number
				{
					String autoLoginUser = prefs.getString("autologinuser_pref", null);
					Log.d(LOGTAG, "Yes, we are forwarding to " + number);
					
					// Set the Result Data to null since we are shipping the call to VPlaceCall
					// setResultData(null);
					
					// Check if autologin is enabled before sending intent
					if(autoLoginUser == null) // Settings not set yet
					{
						// Set the Result Data to null since we dont want the call to happen
						setResultData(null);
					}
					else if(autoLoginUser.equalsIgnoreCase("Disable"))
					{
						Toast.makeText(context, "No user configured to auto login. Please choose one.", Toast.LENGTH_SHORT).show();
						
						// Send the intent to show the General Settings where you can select the autologin user
						Intent settingsMenu = new Intent("com.yaseriesapps.vonageam.SHOW_GENERAL_SETTINGS");
						settingsMenu.addCategory("android.intent.category.DEFAULT");
						settingsMenu.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						context.startActivity(settingsMenu);
						
						// Set the Result Data to null since we dont want the call to happen
						setResultData(null);
					}
					else
					{
						Intent placeCallIntent = new Intent("com.yaseriesapps.vonageam.PLACE_CALL");
						placeCallIntent.putExtra("FwdNumber", number);
						placeCallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						context.startActivity(placeCallIntent);
						
						// Set the Result Data to null since we dont want the call to happen
						setResultData(null);
					}
				}
			}

			// Set the ResultData to whatever number you want the call to continue on to
			// We dont fiddle with ResultData if it is not an intl call. Hence the call continues on it's way.
		}
	}
}
