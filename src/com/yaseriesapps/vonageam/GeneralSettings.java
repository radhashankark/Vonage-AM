package com.yaseriesapps.vonageam;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

public class GeneralSettings extends PreferenceActivity
{
	private String deviceID;
	private final String LOGTAG = "VonageOptions";
	Preference autointlcallsPref, calldelayPref, revertsettingsPref, deleteCredsPref, addUserPref, switchAccountPref;
	ListPreference autologinuserPref;
	GoogleAnalyticsTracker GeneralSettingsTracker;
	Credentials credentials;
	
	@Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        
        GeneralSettingsTracker = GoogleAnalyticsTracker.getInstance();
        credentials = new Credentials(getApplicationContext());

        // Start the tracker in manual dispatch mode...
        GeneralSettingsTracker.start("UA-20721251-7", this);

        // Init the Unique ID
        deviceID = Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID);
        
        //Start with this page tracking
        GeneralSettingsTracker.trackPageView("GeneralSettings");

        addPreferencesFromResource(R.xml.settings_prefs);
        
        // Get the Shared Preferences for a sneak peek at what's in there
    	SharedPreferences prefsPeek = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        
        autointlcallsPref = (Preference) findPreference("autointlcalls_pref");
        calldelayPref = (Preference) findPreference("calldelay_pref");
        revertsettingsPref = (Preference) findPreference("revertsettings_pref");
        autologinuserPref = (ListPreference) findPreference("autologinuser_pref");
        addUserPref = (Preference) findPreference("adduser_pref");
        switchAccountPref =  (Preference) findPreference("switchaccount_pref");
        deleteCredsPref = (Preference) findPreference("deletecreds_pref");
        

        updateSummary();
        createListPreferenceMenu();
        
        autointlcallsPref.setOnPreferenceChangeListener(autoIntlCallsChangeListener);
        calldelayPref.setOnPreferenceChangeListener(calldelayChangeListener);
        revertsettingsPref.setOnPreferenceChangeListener(revertsettingsChangeListener);
        autologinuserPref.setOnPreferenceChangeListener(autologinuserListener);
        addUserPref.setOnPreferenceClickListener(addUserClickListener);
        switchAccountPref.setOnPreferenceClickListener(switchAccountClickListener);
        deleteCredsPref.setOnPreferenceClickListener(deleteCredsClickListener);
    }
	
	public void updateSummary()
    {
    	// Get the Shared Preferences for the Application so we can update summaries
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    	
		// Auto International Calls
		if(prefs.getBoolean("autointlcalls_pref", false))
        {
			autointlcallsPref.setSummary(R.string.autointlcallsenabled);
        }
        else
        {
        	autointlcallsPref.setSummary(R.string.autointlcallsdisabled);
        }

		// Call Delay Setting
		if(prefs.getBoolean("calldelay_pref", false))
        {
			calldelayPref.setSummary(R.string.calldelayenabled);
        }
        else
        {
        	calldelayPref.setSummary(R.string.calldelaydisabled);
        }

		// Revert Settings Setting
		if(prefs.getBoolean("revertsettings_pref", false))
        {
			revertsettingsPref.setSummary(R.string.revertsettingsenabled);
        }
        else
        {
        	revertsettingsPref.setSummary(R.string.revertsettingsdisabled);
        }
		
		// Auto Login User Setting
		String autoLoginUser =  prefs.getString("autologinuser_pref", null);
		if(autoLoginUser == null)
        {
			autologinuserPref.setSummary(R.string.autologinuserunknown);
        }
		else if(autoLoginUser.equalsIgnoreCase("Disable"))
        {
			autologinuserPref.setSummary(R.string.autologinuserdisabled);
        }
        else
        {
        	autologinuserPref.setSummary("Will auto login as " + autoLoginUser);
        }
    }

    OnPreferenceChangeListener autoIntlCallsChangeListener = new OnPreferenceChangeListener()
    {
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue)
		{
			boolean autointlcallsSetting = Boolean.valueOf(String.valueOf(newValue));
			
			if(autointlcallsSetting)
	        {
				GeneralSettingsTracker.trackEvent("GeneralSettings", "AutoIntlCallsEnabled", deviceID, -1);
				Log.d(LOGTAG, "Enabling Automatic International Calling");

				// Get the Shared Preferences for the Application so we can update the status
		    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		    	SharedPreferences.Editor prefsEditor = prefs.edit();
		    	
				prefsEditor.putBoolean("autointlcalls_pref", true);
	        	prefsEditor.commit();
				
				autointlcallsPref.setSummary(R.string.autointlcallsenabled);
	        }
	        else
	        {
	        	GeneralSettingsTracker.trackEvent("GeneralSettings", "AutoIntlCallsDisabled", deviceID, -1);
	        	Log.d(LOGTAG, "Disabling Automatic International Calling");

				// Get the Shared Preferences for the Application so we can update the status
		    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		    	SharedPreferences.Editor prefsEditor = prefs.edit();
		    	
				prefsEditor.putBoolean("autointlcalls_pref", false);
	        	prefsEditor.commit();
	        	
	        	autointlcallsPref.setSummary(R.string.autointlcallsdisabled);
	        }
			return true;
		}
	};

    OnPreferenceChangeListener calldelayChangeListener = new OnPreferenceChangeListener()
    {
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue)
		{
			boolean calldelaySetting = Boolean.valueOf(String.valueOf(newValue));
			
			if(calldelaySetting)
	        {
				GeneralSettingsTracker.trackEvent("GeneralSettings", "CallDelayEnabled", deviceID, -1);
				Log.d(LOGTAG, "Call delay for auto intl calls enabled");

				// Get the Shared Preferences for the Application so we can update the status
		    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		    	SharedPreferences.Editor prefsEditor = prefs.edit();
		    	
				prefsEditor.putBoolean("calldelay_pref", true);
	        	prefsEditor.commit();
				
				calldelayPref.setSummary(R.string.calldelayenabled);
	        }
	        else
	        {
	        	GeneralSettingsTracker.trackEvent("GeneralSettings", "CallDelayDisabled", deviceID, -1);
	        	Log.d(LOGTAG, "Call delay for auto intl calls disabled");

				// Get the Shared Preferences for the Application so we can update the status
		    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		    	SharedPreferences.Editor prefsEditor = prefs.edit();
		    	
				prefsEditor.putBoolean("calldelay_pref", false);
	        	prefsEditor.commit();
	        	
	        	calldelayPref.setSummary(R.string.calldelaydisabled);
	        }
			return true;
		}
	};

    OnPreferenceChangeListener revertsettingsChangeListener = new OnPreferenceChangeListener()
    {
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue)
		{
			boolean revertsettingsSetting = Boolean.valueOf(String.valueOf(newValue));
			
			if(revertsettingsSetting)
	        {
				GeneralSettingsTracker.trackEvent("GeneralSettings", "RevertSettingsEnabled", deviceID, -1);
				Log.d(LOGTAG, "Revert Settings enabled");

				// Get the Shared Preferences for the Application so we can update the status
		    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		    	SharedPreferences.Editor prefsEditor = prefs.edit();
		    	
				prefsEditor.putBoolean("revertsettings_pref", true);
	        	prefsEditor.commit();
	        	
				revertsettingsPref.setSummary(R.string.revertsettingsenabled);
	        }
	        else
	        {
	        	GeneralSettingsTracker.trackEvent("GeneralSettings", "RevertSettingsDisabled", deviceID, -1);
	        	Log.d(LOGTAG, "Revert Settings disabled");

				// Get the Shared Preferences for the Application so we can update the status
		    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		    	SharedPreferences.Editor prefsEditor = prefs.edit();
		    	
				prefsEditor.putBoolean("revertsettings_pref", false);
	        	prefsEditor.commit();
	        	
	        	revertsettingsPref.setSummary(R.string.revertsettingsdisabled);
	        }
			return true;
		}
	};

	private void createListPreferenceMenu()
	{
		int credCount = credentials.getSavedCredCount();
		CharSequence entries[] = new CharSequence[credCount + 1]; 
		String savedUsers[] = credentials.getSavedUserNames();
		
		if(savedUsers != null)
		{
			for(int i = 0; i < credCount; i++)
	    		entries[i] = savedUsers[i].toString();
			
			entries[credCount] = "Disable";
		}
		else
		{
			entries[0] = "Disable";
		}
		
		autologinuserPref.setEntries(entries);
		autologinuserPref.setEntryValues(entries);
	}
	
	OnPreferenceChangeListener autologinuserListener = new OnPreferenceChangeListener()
    {
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue)
		{
			// Auto Login User Setting
			String autoLoginUser = String.valueOf(newValue);
			String[] userCreds = new String[4];
			
			if(autoLoginUser.equalsIgnoreCase("Disable"))
			{
				userCreds = credentials.getAutoLoginCreds();
				
				// Disable the current auto login
				if(userCreds != null)
				{
					Log.d(LOGTAG, "Retrieved creds : " + userCreds[0] + ", " + userCreds[2] + ", " + userCreds[3]);
					
					Log.d(LOGTAG, "User " + userCreds[0] + " already in DB. Deleting before saving again");
					credentials.deleteCredentials(userCreds[0]);
					
		    		Log.d(LOGTAG, "Disabling auto login by inserting again");
		    		credentials.saveCredentials(userCreds[0], userCreds[1], userCreds[2], false);
					
		    		GeneralSettingsTracker.trackEvent("GeneralSettings", "AutoLoginDisabled", deviceID, -1);
				}
			}
			else
			{
				userCreds = credentials.getUserCreds(autoLoginUser);
				
				if(userCreds != null)
				{
					Log.d(LOGTAG, "Retrieved creds : " + userCreds[0] + ", " + userCreds[2] + ", " + userCreds[3]);
					
					Log.d(LOGTAG, "User " + userCreds[0] + " already in DB. Deleting before saving again");
					credentials.deleteCredentials(userCreds[0]);
					
		    		Log.d(LOGTAG, "Enabling auto login by inserting again");
		    		credentials.saveCredentials(userCreds[0], userCreds[1], userCreds[2], true);
					
		    		GeneralSettingsTracker.trackEvent("GeneralSettings", "AutoLoginEnabled", deviceID, -1);
				}
				else
				{
					Log.d(LOGTAG, "For some reason, creds for " + autoLoginUser + " turned up as null");
				}
			}

			// Get the Shared Preferences for the Application so we can update the status
	    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
	    	SharedPreferences.Editor prefsEditor = prefs.edit();

			// Save the user name of the Auto Login User
			prefsEditor.putString("autologinuser_pref", autoLoginUser);
			prefsEditor.commit();
			
			updateSummary();
			
			return true;
		}
	};

	OnPreferenceClickListener addUserClickListener = new OnPreferenceClickListener()
	{

		@Override
		public boolean onPreferenceClick(Preference preference)
		{
			// TODO Auto-generated method stub
			Log.d(LOGTAG, "Starting Login screen to save credentials");
			
			Intent loginIntent = new Intent("com.yaseriesapps.vonageam.LOGIN");
			loginIntent.addCategory("android.intent.category.DEFAULT");
			loginIntent.putExtra("SaveAnotherUser", true);
			startActivity(loginIntent);
			
			return true;
		}
		
	};
	
	OnPreferenceClickListener switchAccountClickListener = new OnPreferenceClickListener()
	{

		@Override
		public boolean onPreferenceClick(Preference preference)
		{
			// TODO Auto-generated method stub
			Log.d(LOGTAG, "Starting Login screen to switch accounts");
			
			Intent loginIntent = new Intent("com.yaseriesapps.vonageam.LOGIN");
			loginIntent.addCategory("android.intent.category.DEFAULT");
			loginIntent.putExtra("LoginOther", true);
			startActivity(loginIntent);
			
			return true;
		}
		
	};
	
	OnPreferenceClickListener deleteCredsClickListener = new OnPreferenceClickListener()
	{
		@Override
		public boolean onPreferenceClick(Preference preference) 
		{
			Log.d(LOGTAG, "Deleting all saved Credentials");
			credentials.clearAllCredentials();
			Toast.makeText(getApplicationContext(), "All saved Credentials deleted", Toast.LENGTH_SHORT).show();
			
			// Clear the autologin settings
	    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
	    	SharedPreferences.Editor prefsEditor = prefs.edit();

			prefsEditor.putString("autologinuser_pref", "Disable");
			prefsEditor.commit();
			
			updateSummary();
			
        	return true;
		}
	};

	@Override
    public void onDestroy()
    {
        super.onDestroy();
        credentials.cleanUp();
        credentials = null;
    }

}
