package com.yaseriesapps.vonageam;

import java.util.Date;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

public class VonageOptions extends PreferenceActivity
{
//	private ProgressDialog mProgressDialog = null; 
	
	private final String LOGTAG = "VonageOptions";
    Credentials credentials;
	ProgressDialog progressDialog;
	Preference setcallfwdPref, callfromvonagePref, networkavailabilityPref, bwsaverPref, calleridPref;
	Preference intlcallingPref, dndPref, voicemailPref;
	boolean rememberMe = false; // Default value false; Flag to store creds. Usually updated from UI when user logs in.
	boolean autoLoginFromUI = true; // Default value true; Flags if creds are entered from UI by user. Not used right now.
	boolean loginFromDB = false; // Default value false; Flags if the creds are from the DB
	boolean loginOther = false; // Default value false; Flag to keep track of user's intent to log into another account
	private String username, phoneNumber, deviceID;
	long lastLogin;
	private VHTTPComm vHTTPComm = null;
	GoogleAnalyticsTracker vonageOptionsTracker;
	
    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        
        vonageOptionsTracker = GoogleAnalyticsTracker.getInstance();
        vHTTPComm = VHTTPComm.getInstance(getApplicationContext()); // new VHTTPComm();

        // Get the Shared Preferences for a sneak peek at what's in there
    	SharedPreferences prefsPeek = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    	SharedPreferences.Editor prefsPeekEditor = prefsPeek.edit();
    	
    	// Get the installed version number from Prefs and if it doesn't match what is installed, empty the prefs
    	float prevVersionNumber = prefsPeek.getFloat("PrevVersionNumber", -1); // -1 is the flag for non-existent value
    	Log.d(LOGTAG, "Previously installed version from Prefs : " + prevVersionNumber);
    	
    	// Now get the installed/current version number
    	PackageManager pm = getPackageManager();
    	ApplicationInfo applicationInfo;
		try 
		{
			applicationInfo = pm.getApplicationInfo("com.yaseriesapps.vonageam", PackageManager.GET_META_DATA);
			String curVersionNumber = (String) pm.getText("com.yaseriesapps.vonageam", R.string.versionCode, applicationInfo); 
			Log.d(LOGTAG, "Current version number, as extracted : " + curVersionNumber);
	    	if (curVersionNumber != null)
	    	{
	    		float curVerNum = Float.parseFloat(curVersionNumber);
	    		if(curVerNum > prevVersionNumber) // If new version installed, blank out the prefs.
	    		{
	    			Log.d(LOGTAG, "A new version of the app is just installed. Clearing irrelevant prefs");
	    			// prefsPeekEditor.clear().commit(); // We just cleared all the prefs.
	    			prefsPeekEditor.remove("LastLogin").commit(); // Only this pref has changed from the last version. Remove it.
	    			prefsPeekEditor.putFloat("PrevVersionNumber", curVerNum).commit(); // The new version number is now previous
	    		}
	    	}
	    	else // We couldn't get the current version number. So set it to unknown.
	    	{
	    		prefsPeekEditor.putFloat("PrevVersionNumber", -2).commit(); // The new version is unavailable. So name it -2
	    	}
		} 
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	
        // Start the tracker in manual dispatch mode...
        vonageOptionsTracker.start("UA-20721251-7", this);
        
        // Init the Unique ID
        deviceID = Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID);
        
        //Start with this page tracking
        vonageOptionsTracker.trackPageView("VonageOptions");

        addPreferencesFromResource(R.xml.vonage_options_prefs);
        credentials = new Credentials(getApplicationContext());
        progressDialog = new ProgressDialog(this);
        
        // Check for login and show Login View
        setupLoginView();
    	
    	Log.d(LOGTAG, "Value for setcallfwd_pref : " + prefsPeek.getString("setcallfwd_pref", "Bleh"));
    	Log.d(LOGTAG, "Value for networkavailability_pref : " + prefsPeek.getString("networkavailability_pref", "Bleh"));    	
    	Log.d(LOGTAG, "Value for intlcalling_pref : " + prefsPeek.getBoolean("intlcalling_pref", false));
    	Log.d(LOGTAG, "Value for callerid_pref : " + prefsPeek.getBoolean("callerid_pref", false));
    	Log.d(LOGTAG, "Value for bwsaver_pref : " + prefsPeek.getString("bwsaver_pref", "Bleh"));
    	Log.d(LOGTAG, "Value for autointlcalls_pref : " + prefsPeek.getBoolean("autointlcalls_pref", false));
    	Log.d(LOGTAG, "Value for calldelay_pref : " + prefsPeek.getBoolean("calldelay_pref", false));
    	Log.d(LOGTAG, "Value for dnd_pref : " + prefsPeek.getBoolean("dnd_pref", false));
    	Log.d(LOGTAG, "Value for voicemail_stats_pref : " + prefsPeek.getString("voicemail_stats_pref", "NA::NA"));
    	Log.d(LOGTAG, "Value for revertsettings_pref : " + prefsPeek.getBoolean("revertsettings_pref", false));
        
        if(phoneNumber.equalsIgnoreCase("0000000000"))
        {
        	setTitle("Vonage AM - Not logged in");
        }
        else
        {
        	setTitle("Options for " + phoneNumber);
        }
        
        // Initialize all Preference objects
        Log.d(LOGTAG, "Initializing all preferences");
        setcallfwdPref = (Preference) findPreference("setcallfwd_pref");
        callfromvonagePref = (Preference) findPreference("callfromvonage_pref");
        networkavailabilityPref = (Preference) findPreference("networkavailability_pref");
        bwsaverPref = (Preference) findPreference("bwsaver_pref");
        calleridPref = (Preference) findPreference("callerid_pref");
        intlcallingPref = (Preference) findPreference("intlcalling_pref");
        dndPref = (Preference) findPreference("dnd_pref");
        voicemailPref = (Preference) findPreference("voicemail_pref");

        updateSummary();
        
        // Set up all onClickListeners
        Log.d(LOGTAG, "Setting up onClickListeners");
        setcallfwdPref.setOnPreferenceClickListener(setcallfwdListener);
        callfromvonagePref.setOnPreferenceClickListener(callfromvonageListener);
        networkavailabilityPref.setOnPreferenceClickListener(networkavailabilityListener);
//        voicemailPref.setOnPreferenceClickListener(voicemailListener);
        
        // Set up all onChangeListeners
        Log.d(LOGTAG, "Setting up onChangeListeners");
        bwsaverPref.setOnPreferenceChangeListener(bwsaverChangeListener);
        intlcallingPref.setOnPreferenceChangeListener(intlcallingChangeListener);
        calleridPref.setOnPreferenceChangeListener(calleridChangeListener);
        dndPref.setOnPreferenceChangeListener(dndChangeListener);
        
    }

    public void updateSummary()
    {
    	// Get the Shared Preferences for the Application so we can update summaries
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    	
    	// Caller ID setting summary
    	if(prefs.getBoolean("callerid_pref", false))
    	{
    		calleridPref.setSummary(R.string.calleridenabled);
    	}
    	else
    	{
    		calleridPref.setSummary(R.string.calleriddisabled);
    	}
    	
    	// Set Call Forward summary
    	String curCFSetting = prefs.getString("setcallfwd_pref", null);
    	if("Disabled".equalsIgnoreCase(curCFSetting))
        {
        	setcallfwdPref.setSummary(R.string.callfwddisabled);
        }
    	else if(curCFSetting == null)
    	{
    		setcallfwdPref.setSummary("Unable to determine setting. Please log in.");
    	}
        else
        {
        	setcallfwdPref.setSummary("Currently forwarding to " + curCFSetting);        	
        }
    	
    	// International Calling Summary
    	if(prefs.getBoolean("intlcalling_pref", false))
    	{
    		intlcallingPref.setSummary(R.string.intlcallingenabled);
    	}
    	else
    	{
    		intlcallingPref.setSummary(R.string.intlcallingdisabled);
    	}
    	
    	// Do Not Disturb Summary
    	if(prefs.getBoolean("dnd_pref", false))
    	{
    		dndPref.setSummary(R.string.donotdisturbenabled);
    	}
    	else
    	{
    		dndPref.setSummary(R.string.donotdisturbdisabled);
    	}
    	
    	// NaN Setting
		String nanSetting = prefs.getString("networkavailability_pref", null);
		if("Disabled".equalsIgnoreCase(nanSetting))
		{
			networkavailabilityPref.setSummary(R.string.nanenabled);
		}
		else if(nanSetting == null)
    	{
			networkavailabilityPref.setSummary("Unable to determine setting. Please log in.");
    	}
		else
		{
			networkavailabilityPref.setSummary("Forwards to " + nanSetting + " when disconnected");
		}
		
		// Voicemail Setting
		String voicemailSetting = prefs.getString("voicemail_stats_pref", "NA::NA");
		if("NA::NA".equalsIgnoreCase(voicemailSetting))
		{
			voicemailPref.setSummary("No Voicemails, or Voicemail not set up");
		}
		else
		{
			String[] vms = voicemailSetting.split("::");
			voicemailPref.setSummary(vms[0] + " New, " + vms[1] + " Total");
		}
    }
    
    OnPreferenceClickListener setcallfwdListener = new OnPreferenceClickListener()
    {
		@Override
		public boolean onPreferenceClick(Preference preference)
		{
			Log.d(LOGTAG, "onClick for Preference with key : " + preference.getKey());
			Intent callFwd = new Intent("com.yaseriesapps.vonageam.SET_FORWARD");
			callFwd.addCategory("android.intent.category.DEFAULT");
			startActivity(callFwd);
			return true;
		}
	};
	
	OnPreferenceClickListener callfromvonageListener = new OnPreferenceClickListener()
    {
		@Override
		public boolean onPreferenceClick(Preference preference)
		{
			Log.d(LOGTAG, "onClick for Preference with key : " + preference.getKey());
			Intent callFromVon = new Intent("com.yaseriesapps.vonageam.CALL_FROM_VONAGE");
			callFromVon.addCategory("android.intent.category.DEFAULT");
			startActivity(callFromVon);
			return true;
		}
	};

    OnPreferenceClickListener networkavailabilityListener = new OnPreferenceClickListener()
    {
		@Override
		public boolean onPreferenceClick(Preference preference)
		{
			Log.d(LOGTAG, "onClick for Preference with key : " + preference.getKey());
			Intent setNAN = new Intent("com.yaseriesapps.vonageam.SET_NAN");
			setNAN.addCategory("android.intent.category.DEFAULT");
			startActivity(setNAN);
			return true;
		}
	};

	OnPreferenceClickListener voicemailListener = new OnPreferenceClickListener()
    {
		@Override
		public boolean onPreferenceClick(Preference preference)
		{
			Log.d(LOGTAG, "onClick for Preference with key : " + preference.getKey());
			Intent voicemailDetails = new Intent("com.yaseriesapps.vonageam.SHOW_VOICEMAIL_DETAILS");
			voicemailDetails.addCategory("android.intent.category.DEFAULT");
			startActivity(voicemailDetails);
//			SharedPreferences vmPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
//			Toast.makeText(getApplicationContext(), "Voicemails : " + vmPrefs.getString("voicemail_stats_pref", "NA::NA"), Toast.LENGTH_SHORT).show();
			return true;
		}
	};
	
	OnPreferenceChangeListener bwsaverChangeListener = new OnPreferenceChangeListener()
    {
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue) 
		{
			vonageOptionsTracker.trackEvent("VonageOptions", "BWSaverPrefChange", deviceID, -1);
			String bwSaverValue = String.valueOf(newValue);
			
			if(bwSaverValue.equalsIgnoreCase("30")) // Edit Call Quality Option in Options Menu
	    	{
	    		Log.d(LOGTAG, "Setting Bandwidth Saver to NORMAL(30 kbps)");
	    		new SettingsTask().execute("editCallQuality", "NORMAL");
	    		return true;
	    	}
	    	else if(bwSaverValue.equalsIgnoreCase("50")) // Edit Call Quality Option in Options Menu
	    	{
	    		Log.d(LOGTAG, "Setting Bandwidth Saver to HIGH(50 kbps)");
	    		new SettingsTask().execute("editCallQuality", "HIGH");
	    		return true;
	    	}
	    	else if(bwSaverValue.equalsIgnoreCase("90")) // Edit Call Quality Option in Options Menu
	    	{
	    		Log.d(LOGTAG, "Setting Bandwidth Saver to HIGHEST(90 kbps)");
	    		new SettingsTask().execute("editCallQuality", "HIGHEST");
	    		return true;
	    	}
			
			return false;
		}
	};

    OnPreferenceChangeListener calleridChangeListener = new OnPreferenceChangeListener()
    {
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue)
		{
			vonageOptionsTracker.trackEvent("VonageOptions", "CallerIDPrefChange", deviceID, -1);
			boolean callerIDSetting = Boolean.valueOf(String.valueOf(newValue));
			
			if(callerIDSetting)
			{
				Log.d(LOGTAG, "Enabling Caller ID");
				new SettingsTask().execute("enablecallerid");
				calleridPref.setSummary(R.string.calleridenabled);
			}
			else
			{
				Log.d(LOGTAG, "Disabling Caller ID");
				new SettingsTask().execute("disablecallerid");
				calleridPref.setSummary(R.string.calleriddisabled);
			}
			
			return true;
		}
	};

	OnPreferenceChangeListener dndChangeListener = new OnPreferenceChangeListener()
    {
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue)
		{
			vonageOptionsTracker.trackEvent("VonageOptions", "DNDPrefChange", deviceID, -1);
			boolean dndSetting = Boolean.valueOf(String.valueOf(newValue));
			
			if(dndSetting)
			{
				Log.d(LOGTAG, "Enabling Do Not Disturb Setting");
				new SettingsTask().execute("enablednd");
				dndPref.setSummary(R.string.donotdisturbenabled);
			}
			else
			{
				Log.d(LOGTAG, "Disabling Do Not Disturb Setting");
				new SettingsTask().execute("disablednd");
				dndPref.setSummary(R.string.donotdisturbdisabled);
			}
			
			return true;
		}
	};
	
    OnPreferenceChangeListener intlcallingChangeListener = new OnPreferenceChangeListener()
    {
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue)
		{
			vonageOptionsTracker.trackEvent("VonageOptions", "IntlCallingPrefChange", deviceID, -1);
			boolean intlCallingSetting = Boolean.valueOf(String.valueOf(newValue));
			
			if(intlCallingSetting)
	        {
				Log.d(LOGTAG, "Enabling International Calling");
				new SettingsTask().execute("enableintlcalling");
				intlcallingPref.setSummary(R.string.intlcallingenabled);
	        }
	        else
	        {
	        	Log.d(LOGTAG, "Disabling International Calling");
	        	new SettingsTask().execute("disableintlcalling");
	        	intlcallingPref.setSummary(R.string.intlcallingdisabled);
	        }
			return true;
		}
	};
	
    public void logout()
    {
    	if(vHTTPComm.logout().equalsIgnoreCase("Success"))
		{
			Log.d(LOGTAG, "Logged out successfully");
			finish();
		}
    	else
    	{
    		Log.d(LOGTAG, "Logging out failed");
    		finish();
    	}
    }
   
    public String cleanNumber(String number)
    {
    	int numLength = number.length();
//    	Log.d(LOGTAG, "Dirty Number : " + number + ", Number Length : " + numLength);
    	
    	StringBuffer sb = new StringBuffer("");
    	char[] dirtyNumber = number.toCharArray();
		int dirtyNumCount = 0; // Start count at 0
		int temp;
    	
    	if(dirtyNumber[0] == '+') // If the first char of number is '+'
    	{
    		dirtyNumCount++; // Increment the count for the loop
    		sb.append("+"); // Start off the string with "+"
    	}
    	
    	for( ; dirtyNumCount < dirtyNumber.length; dirtyNumCount++) // Loop through the length of the dirty number to clean it
    	{
    		temp = (int) dirtyNumber[dirtyNumCount]; // Get the ASCII value. It's easier to compare that way
//    		Log.d(LOGTAG, "Precessing dirty number, " + dirtyNumber[dirtyNumCount] + ", " + temp);
    		if(temp > 47 && temp < 58) // 48 = 0 and 57 = 9 in ASCII. Filter those values
    		{
    			sb.append(dirtyNumber[dirtyNumCount]);
    		}
    	}
    	
    	number = sb.toString();
//    	Log.d(LOGTAG, "Cleaned number : " + number);
    	numLength = number.length();
    	
    	if(numLength <= 9) // Number length less than 10 digits, It's a nonsense number
    	{
    		return "Nonsense";
    	}
    	else if(numLength >= 12 && !(number.contains("+") && !(number.startsWith("011")))) // If number too long with no + for ISD calls, it's improper
    	{
    		return "Nonsense";
    	}
    	else if(number.contains("+"))
    	{
    		number = number.replace("+", "011"); // Replace the + with the dialing 011 code
    		Log.d(LOGTAG, "Forwardable International Numnber : " + number);
    		return number;
    	}
    	else if(numLength >= 10 && numLength <= 11)
    	{
    		if(number.length()==10) // Add '1' to the number to standardize it
    			number = "1"+number;
    		
    		Log.d(LOGTAG, "Forwardable Numnber : " + number);
    		return number;
    	}
    	else // We dont know what we received. Return Nonsense.
    	{
    		return "Nonsense";
    	}
    }
    
    private class SettingsTask extends AsyncTask<String, String, String>
    {
    	@Override
		protected void onPreExecute()
		{
    		progressDialog.setCancelable(false);
    		progressDialog.setMessage("Initializing...");
    		progressDialog.show();
		}
    	
		@Override
		protected String doInBackground(String... params)
		{
			if(params[0].equalsIgnoreCase("enablecallerid"))
   			{
   				publishProgress("SetMessage", "Enabling Caller ID");
   				Log.d(LOGTAG, "Enabling Caller ID");
   				String setCallerIDStatus = vHTTPComm.setCallerID(true, false); // Login before changing anything
				if("Success".equalsIgnoreCase(setCallerIDStatus)) // Setting change status
				{
					Log.d(LOGTAG, "Setting caller ID Success");
					return "calleridsucc";
				}
				else if("NotLoggedIn".equalsIgnoreCase(setCallerIDStatus))
		        {
		        	// Currently not logged in. finish() the Activity.
		        	Log.d(LOGTAG, "Not currently logged in. Exit this Activity");
		        	return "NotLoggedIn";
		        }
				else
				{
					Log.d(LOGTAG, "Setting caller ID Failure");
					return "calleridfail";
				}
   			}
			else if(params[0].equalsIgnoreCase("disablecallerid"))
   			{
				publishProgress("SetMessage", "Disabling Caller ID");
   				Log.d(LOGTAG, "Disabling Caller ID");
   				String setCallerIDStatus = vHTTPComm.setCallerID(false, false); // Login before changing anything);
				if("Success".equalsIgnoreCase(setCallerIDStatus)) // Setting change status
				{
					Log.d(LOGTAG, "Setting caller ID Success");
					return "calleridsucc";
				}
				else if("NotLoggedIn".equalsIgnoreCase(setCallerIDStatus))
		        {
					// Currently not logged in. finish() the Activity.
		        	Log.d(LOGTAG, "Not currently logged in. Exit this Activity");
		        	return "NotLoggedIn";
		        }
				else
				{
					Log.d(LOGTAG, "Setting caller ID Failure");
					return "calleridfail";
				}
   			}
			else if(params[0].equalsIgnoreCase("enableintlcalling"))
   			{
				publishProgress("SetMessage", "Enabling International Calls");
				String callerIDChangeStatus = vHTTPComm.intlcalling(true, false); // Login before changing anything);
				if("Success".equalsIgnoreCase(callerIDChangeStatus)) // Setting change status
				{
					Log.d(LOGTAG, "Enabling International Calling Success");
					return "enableintlcallingsucc";
				}
				else if("NotLoggedIn".equalsIgnoreCase(callerIDChangeStatus))
		        {
					// Currently not logged in. finish() the Activity.
		        	Log.d(LOGTAG, "Not currently logged in. Exit this Activity");
		        	return "NotLoggedIn";
		        }
				else
				{
					Log.d(LOGTAG, "Setting NAN Failure");
					return "enableintlcallingfail";
				}
   			}
			else if(params[0].equalsIgnoreCase("disableintlcalling"))
   			{
				publishProgress("SetMessage", "Disabling International Calls");
				String callerIDChangeStatus = vHTTPComm.intlcalling(false, false); // Login before changing anything);
				if("Success".equalsIgnoreCase(callerIDChangeStatus)) // Setting change status
				{
					Log.d(LOGTAG, "Disabling International Calling Success");
					return "disableintlcallingsucc";
				}
				else if("NotLoggedIn".equalsIgnoreCase(callerIDChangeStatus))
		        {
					// Currently not logged in. finish() the Activity.
		        	Log.d(LOGTAG, "Not currently logged in. Exit this Activity");
		        	return "NotLoggedIn";
		        }
				else
				{
					Log.d(LOGTAG, "Disabling International Calling Failed");
					return "disableintlcallingfail";
				}
   			}
			else if(params[0].equalsIgnoreCase("enablednd"))
   			{
				publishProgress("SetMessage", "Enabling Do Not Disturb Setting...");
				String dndChangeStatus = vHTTPComm.setDND(true, false); // Login before changing anything
				if("Success".equalsIgnoreCase(dndChangeStatus)) // Setting change status
				{
					Log.d(LOGTAG, "Enabling Do Not Disturb Success");
					return "enabledndsucc";
				}
				else if("NotLoggedIn".equalsIgnoreCase(dndChangeStatus))
		        {
					// Currently not logged in. finish() the Activity.
		        	Log.d(LOGTAG, "Not currently logged in. Exit this Activity");
		        	return "NotLoggedIn";
		        }
				else
				{
					Log.d(LOGTAG, "Setting Do Not Disturb Failure");
					return "enabledndfail";
				}
   			}
			else if(params[0].equalsIgnoreCase("disablednd"))
   			{
				publishProgress("SetMessage", "Disabling Do Not Disturb Setting...");
				String dndChangeStatus = vHTTPComm.setDND(false, false); // Login before changing anything
				if("Success".equalsIgnoreCase(dndChangeStatus)) // Setting change status
				{
					Log.d(LOGTAG, "Disabling Do Not Disturb Success");
					return "disabledndsucc";
				}
				else if("NotLoggedIn".equalsIgnoreCase(dndChangeStatus))
		        {
					// Currently not logged in. finish() the Activity.
		        	Log.d(LOGTAG, "Not currently logged in. Exit this Activity");
		        	return "NotLoggedIn";
		        }
				else
				{
					Log.d(LOGTAG, "Disabling International Calling Failed");
					return "disabledndfail";
				}
   			}
			else if(params[0].equalsIgnoreCase("editCallQuality"))
			{
				publishProgress("SetMessage", "Setting call quality to " + params[1]);
				if("Success".equalsIgnoreCase(vHTTPComm.editCallQuality(params[1], false))) // Login before changing anything
				{
					Log.d(LOGTAG, "Updating Bandwidth successful");
					return "bwsaverchangesucc";
				}
				else
				{
					Log.d(LOGTAG, "Updating Bandwidth Failed");
					return "bwsaverchangefail";
				}
   			}
			else if(params[0].equalsIgnoreCase("logout"))
			{
				publishProgress("SetMessage", "Please wait, logging out...");
				if(vHTTPComm.logout().equalsIgnoreCase("Success"))
				{
					Log.d(LOGTAG, "Logged out successfully");
					return "logoutsucc";
				}
		    	else
		    	{
		    		Log.d(LOGTAG, "Logging out failed");
		    		return "logoutfail";
		    	}
   			}
			else if(params[0].equalsIgnoreCase("RefreshSettings"))
			{
				publishProgress("SetMessage", "Getting Account Settings. Please wait..");
				
				// Refresh settings. One login and one fell swoop of gathering details
				SharedPreferences currentLoginPrefs = getApplicationContext().getSharedPreferences("CURRENT_LOGIN", 0);
				String tempuserName = currentLoginPrefs.getString("userName", "LoggedOut");
				String tempPassword = currentLoginPrefs.getString("password", "LoggedOut");
				if(tempuserName.equalsIgnoreCase("LoggedOut") || tempPassword.equalsIgnoreCase("LoggedOut"))
					Log.d(LOGTAG, "Got no creds to login and refresh settings in SettingsTask");
				else
					vHTTPComm.login(tempuserName, tempPassword, false); // Dont write to Prefs. This is just a temp login
				
				// Dont login for every detail.
				getAccountSettings(true);
				
				return "refreshSettingsSuccess";
   			}
			else if(params[0].equalsIgnoreCase("RefreshFirstTime"))
			{
				publishProgress("ShowToast", "Getting your account settings. Please wait.");
				publishProgress("Dismiss");
				
				// Refreshing settings immediately after login. Dont need to login again
				getAccountSettings(true);
				
				return "refreshSettingsSuccess";
   			}
			else
			{
				return "Nothing";
			}
		}
		
		@Override
		protected void onProgressUpdate(String... values)
		{
			if(values[0].equalsIgnoreCase("Dismiss"))
				progressDialog.dismiss();
			else if(values[0].equalsIgnoreCase("SetMessage"))
				progressDialog.setMessage(values[1]);
			else if(values[0].equalsIgnoreCase("ShowToast"))
				Toast.makeText(getApplicationContext(), values[1], Toast.LENGTH_SHORT).show();
		}

		@Override
		protected void onPostExecute(String result)
		{
			progressDialog.dismiss();
			
			if(result.equalsIgnoreCase("disablefwdsucc"))
				Toast.makeText(getApplicationContext(), "Call Forwarding disabled", Toast.LENGTH_SHORT).show();
			else if(result.equalsIgnoreCase("disablefwdfail"))
				Toast.makeText(getApplicationContext(), "Call Forwarding disable failed", Toast.LENGTH_LONG).show();
			else if(result.equalsIgnoreCase("calleridsucc"))
			{
				Toast.makeText(getApplicationContext(), "Caller ID setting change successful", Toast.LENGTH_SHORT).show();
			}
			else if(result.equalsIgnoreCase("calleridfail"))
			{
				Toast.makeText(getApplicationContext(), "Caller ID setting change failed", Toast.LENGTH_LONG).show();
			}
			else if(result.equalsIgnoreCase("editCallQualSucc"))
				Toast.makeText(getApplicationContext(), "Call Quality Setting Successful", Toast.LENGTH_SHORT).show();
			else if(result.equalsIgnoreCase("editCallQualFail"))
				Toast.makeText(getApplicationContext(), "Call Quality Setting Failed", Toast.LENGTH_LONG).show();
			else if(result.equalsIgnoreCase("enableintlcallingsucc"))
				Toast.makeText(getApplicationContext(), "International Calls Enabled", Toast.LENGTH_SHORT).show();
			else if(result.equalsIgnoreCase("enableintlcallingfail"))
				Toast.makeText(getApplicationContext(), "Enabling International Calls Failed", Toast.LENGTH_LONG).show();
			else if(result.equalsIgnoreCase("disableintlcallingsucc"))
				Toast.makeText(getApplicationContext(), "International Calls Disabled", Toast.LENGTH_SHORT).show();
			else if(result.equalsIgnoreCase("disableintlcallingfail"))
				Toast.makeText(getApplicationContext(), "Disabling International Calls Failed", Toast.LENGTH_LONG).show();
			else if(result.equalsIgnoreCase("enabledndsucc"))
				Toast.makeText(getApplicationContext(), "Do Not Disturb Enabled", Toast.LENGTH_SHORT).show();
			else if(result.equalsIgnoreCase("enabledndfail"))
				Toast.makeText(getApplicationContext(), "Enabling Do Not Disturb Failed", Toast.LENGTH_LONG).show();
			else if(result.equalsIgnoreCase("disabledndsucc"))
				Toast.makeText(getApplicationContext(), "Do Not Disturb Disabled", Toast.LENGTH_SHORT).show();
			else if(result.equalsIgnoreCase("disabledndfail"))
				Toast.makeText(getApplicationContext(), "Disabling Do Not Disturb Failed", Toast.LENGTH_LONG).show();
			else if(result.equalsIgnoreCase("bwsaverchangesucc"))
				Toast.makeText(getApplicationContext(), "Updating Call Quality successful", Toast.LENGTH_SHORT).show();
			else if(result.equalsIgnoreCase("bwsaverchangefail"))
				Toast.makeText(getApplicationContext(), "Updating Call Quality Failed", Toast.LENGTH_LONG).show();
			else if(result.equalsIgnoreCase("logoutsucc"))
				finish();
			else if(result.equalsIgnoreCase("logoutfail"))
				finish();
			else if(result.equalsIgnoreCase("refreshSettingsSuccess"))
			{
				onContentChanged();
				updateSummary();
				Log.d(LOGTAG, "Successfully refreshed settings from SettingsTask");
			}
			else if(result.equalsIgnoreCase("NotLoggedIn"))
			{
				Toast.makeText(getApplicationContext(), "Logged out due to inactivity. Please log in again.", Toast.LENGTH_LONG).show();
				finish();
			}
			
	    }
    	
    }

    private void showGeneralSettings()
	{
		Intent settingsMenu = new Intent("com.yaseriesapps.vonageam.SHOW_GENERAL_SETTINGS");
		settingsMenu.addCategory("android.intent.category.DEFAULT");
		startActivity(settingsMenu);
	}
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.general_settings_menu, menu);
        
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId())
        {
        	case R.id.refresh:
    			// Check Logged In status and update UI
    	    	SharedPreferences currentLogin = getSharedPreferences("CURRENT_LOGIN", 0);
    	        username = currentLogin.getString("userName", null);
    	        phoneNumber = currentLogin.getString("phoneNumber", null);
    	        
    	        // Check if any user is logged in. Else, ask to login
    	        if("LoggedOut".equalsIgnoreCase(username)) // No user is logged in
    	        {
    	        	Log.d(LOGTAG, "Refreshing settings. Now logging in");
            		Intent loginIntent = new Intent("com.yaseriesapps.vonageam.LOGIN");
        			loginIntent.addCategory("android.intent.category.DEFAULT");
        			startActivity(loginIntent);
    	        }
    	        else
    	        {
        			Log.d(LOGTAG, "Currently logged in. Get settings and Update UI");
        			
        			// Get the settings from Vonage
        			new SettingsTask().execute("RefreshSettings");
        			
    	        	// Redraw the UI so the prefs get updated
    	        	setTitle("Options for " + phoneNumber);
    	        	onContentChanged();
    	        }
    	        
    	    	updateSummary();
    	    	
        		return true;
	        case R.id.general_settings:
	        	showGeneralSettings();
	            return true;
	        case R.id.deletecreds:
	        	Log.d(LOGTAG, "Deleting all saved Credentials");
				credentials.clearAllCredentials();
				Toast.makeText(getApplicationContext(), "All saved Credentials deleted", Toast.LENGTH_SHORT).show();
	        	return true;
	        case R.id.quit:
	        	Log.d(LOGTAG, "Logging out");
				// logout();
	        	new SettingsTask().execute("logout");
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
        }
    }
    
    @Override
    public void onDestroy()
    {
        super.onDestroy();
        credentials.cleanUp();
        credentials = null;
        
        logout(); // Just make sure that you're logged off when destroying
        
    	// Dispatch the stats and stop the tracker when it is no longer needed.
        vonageOptionsTracker.dispatch();
        vonageOptionsTracker.stop();
    }


    public void getAccountSettings(boolean justLoggedIn)
    {
//    	Toast.makeText(getApplicationContext(), "Getting Account Settings...", Toast.LENGTH_SHORT).show();
    	
    	// Get the Shared Preferences for the Application so we dont have to go to the site for everything
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    	SharedPreferences.Editor prefsEditor = prefs.edit();
    	
        // Call Forwarding setting
        String curCFSetting = vHTTPComm.getCurrentCFSetting(justLoggedIn); // if true, dont attempt to login again
        if("Disabled".equalsIgnoreCase(curCFSetting))
        {
        	prefsEditor.putString("setcallfwd_pref", curCFSetting);
        	prefsEditor.commit();
        }
        else if("NotLoggedIn".equalsIgnoreCase(curCFSetting))
        {
        	// Currently not logged in. Restart the LoginView again.
        	Log.d(LOGTAG, "Not currently logged in. Log in again.");
        	setupLoginView();
        }
        else if("Failure".equalsIgnoreCase(curCFSetting))
        {
        	// Some problem getting the data. Restart the login process
        	Log.d(LOGTAG, "Not currently logged in. Log in again.");
        	Toast.makeText(getApplicationContext(), "Could not get some information. Please login again.", Toast.LENGTH_SHORT).show();
        	setupLoginView();
        }
        else
        {
//        	setcallfwdPref.setSummary("Currently forwarding to " + curCFSetting);        	
        	prefsEditor.putString("setcallfwd_pref", curCFSetting);
        	prefsEditor.commit();
        }
        
        // CallerID setting - enabled/disabled
		if(vHTTPComm.getCallerIDSetting(justLoggedIn)) // if true, dont attempt to login again
		{
//			calleridPref.setSummary("Caller ID is enabled");
			prefsEditor.putBoolean("callerid_pref", true);
        	prefsEditor.commit();
		}
		else
		{
//			calleridPref.setSummary("Caller ID currently disabled");
			prefsEditor.putBoolean("callerid_pref", false);
        	prefsEditor.commit();
		}

		// Do Not Disturb setting - enabled/disabled
		if(vHTTPComm.getDNDSetting(justLoggedIn)) // if true, dont attempt to login again
		{
//			calleridPref.setSummary("Caller ID is enabled");
			prefsEditor.putBoolean("dnd_pref", true);
        	prefsEditor.commit();
		}
		else
		{
//			calleridPref.setSummary("Caller ID currently disabled");
			prefsEditor.putBoolean("dnd_pref", false);
        	prefsEditor.commit();
		}

		// NaN Setting
		String nanSetting = vHTTPComm.getNaNSetting(justLoggedIn); // if true, dont attempt to login again
		if("Disabled".equalsIgnoreCase(nanSetting))
		{
			//networkavailabilityPref.setSummary("Network Availability Number disabled");
			prefsEditor.putString("networkavailability_pref", nanSetting);
        	prefsEditor.commit();
		}
		else if("NotLoggedIn".equalsIgnoreCase(nanSetting))
        {
        	// Currently not logged in. Restart the LoginView again.
        	Log.d(LOGTAG, "Not currently logged in. Log in again.");
        	setupLoginView();
        }
        else if("Failure".equalsIgnoreCase(nanSetting))
        {
        	// Some problem getting the data. Restart the login process
        	Log.d(LOGTAG, "Not currently logged in. Log in again.");
        	Toast.makeText(getApplicationContext(), "Could not get some information. Please login again.", Toast.LENGTH_SHORT).show();
        	setupLoginView();
        }
		else
		{
			//networkavailabilityPref.setSummary("Forwards to " + nanSetting + " when disconnected");
			prefsEditor.putString("networkavailability_pref", nanSetting);
        	prefsEditor.commit();
		}

		// International calling setting - Enabled/disabled
		if(vHTTPComm.getIntlCallingEnabled(justLoggedIn)) // if true, dont attempt to login again
		{
//			intlcallingPref.setSummary("International calling enabled");
			prefsEditor.putBoolean("intlcalling_pref", true);
        	prefsEditor.commit();
		}
		else
		{
//			intlcallingPref.setSummary("International calling disabled");
			prefsEditor.putBoolean("intlcalling_pref", false);
        	prefsEditor.commit();
		}
		
		// Voicemail Stats
		String voicemailStats = vHTTPComm.getVoiceMailStats(justLoggedIn); // if true, dont attempt to login again
		if(voicemailStats.contains("::"))
		{
			//networkavailabilityPref.setSummary("Network Availability Number disabled");
			prefsEditor.putString("voicemail_stats_pref", voicemailStats);
        	prefsEditor.commit();
		}
		else if("HTTPError".equalsIgnoreCase(voicemailStats))
        {
        	// Currently not logged in. Restart the LoginView again.
        	Log.d(LOGTAG, "HTTPError. Log in again.");
        	Toast.makeText(getApplicationContext(), "Error communicating with Vonage Servers. Please log in again", Toast.LENGTH_LONG).show();
        	setupLoginView();
        }
		else if("NotLoggedIn".equalsIgnoreCase(voicemailStats))
        {
        	// Currently not logged in. Restart the LoginView again.
        	Log.d(LOGTAG, "Not currently logged in. Log in again.");
        	setupLoginView();
        }
        else if("Failure".equalsIgnoreCase(voicemailStats))
        {
        	// Some problem getting the data. Restart the login process
        	Log.d(LOGTAG, "Not currently logged in. Log in again.");
        	Toast.makeText(getApplicationContext(), "Could not get some information. Please login again.", Toast.LENGTH_SHORT).show();
        	setupLoginView();
        }
		else
		{
        	Log.d(LOGTAG, "Unknown Exception. Log in again.");
        	Toast.makeText(getApplicationContext(), "Problem retrieving information. Please log in again", Toast.LENGTH_LONG).show();
        	setupLoginView();
		}
		
    }
    
    private void setupLoginView()
    {
    	// Check Logged In status and update UI
    	SharedPreferences currentLogin = getSharedPreferences("CURRENT_LOGIN", 0);
        lastLogin = currentLogin.getLong("LastLogin", new Date(0).getTime());
        
        // If the last login was more than 5 minutes ago, clear prefs, meaning you logged out
        if(new Date().getTime() - lastLogin > 300000)
        {
        	Log.d(LOGTAG, "Been more than 5 minutes since last login, soft logging out");
		    SharedPreferences.Editor currentLoginEditor = currentLogin.edit();
		    currentLoginEditor.putBoolean("LoggedIn", false);
		    currentLoginEditor.putString("userName", "LoggedOut");
		    currentLoginEditor.putString("phoneNumber", "0000000000");
		    currentLoginEditor.commit();
        }

        username = currentLogin.getString("userName", null);
        phoneNumber = currentLogin.getString("phoneNumber", null);
        
        // Check if any user is logged in. Else, ask to login
        if("LoggedOut".equalsIgnoreCase(username)) // No user is logged in
        {
        	Log.d(LOGTAG, "Not logged in. Starting Login View");
    		Intent loginIntent = new Intent("com.yaseriesapps.vonageam.LOGIN");
			loginIntent.addCategory("android.intent.category.DEFAULT");
			startActivity(loginIntent);
        }
    }
    
    @Override
    public void onStop()
    {
    	super.onStop();
    	Log.d(LOGTAG,"Main thread, onStop()");
    }
    
    @Override
    public void onRestart()
    {
    	super.onRestart();
    	Log.d(LOGTAG,"Main thread, onRestart()");
    }
    
    @Override
    public void onPause()
    {
    	super.onPause();
    	Log.d(LOGTAG,"Main thread, onPause()");
    }
    
    @Override
    public void onResume()
    {
    	super.onResume();
    	Log.d(LOGTAG,"Main thread, onResume()");
    	
    	// Check Logged In status and update UI
    	SharedPreferences currentLogin = getSharedPreferences("CURRENT_LOGIN", 0);
        username = currentLogin.getString("userName", null);
        phoneNumber = currentLogin.getString("phoneNumber", null);
        lastLogin = currentLogin.getLong("LastLogin", new Date(0).getTime());
        
        // If it is less than 2 seconds since the last successful login, get Account Settings. Refresh follows automatically.
        if(new Date().getTime() - lastLogin <= 2000)
        {
//        	getAccountSettings(true); // Just logged in. So, will not attempt to log in again.
        	// Ask SettingsTask to refresh settings so it is run on a thread
        	new SettingsTask().execute("RefreshFirstTime");
        }
        
        // Update UI if anyone is logged in.
        if(!("LoggedOut".equalsIgnoreCase(username))) // No user is logged in
        {
        	Log.d(LOGTAG, "Currently logged in. Update UI");
        	// Redraw the UI so the prefs get updated
        	setTitle("Options for " + phoneNumber);
        	onContentChanged();
        	
        	updateSummary();
        }
    }
}