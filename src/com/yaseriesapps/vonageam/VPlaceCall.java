package com.yaseriesapps.vonageam;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Contacts.People;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

public class VPlaceCall extends Activity 
{
	Intent incomingIntent; 
	SharedPreferences prefs = null; 
	String fwdNumber, username, password, phoneNumber, cleanedNumber, deviceID;
	final String LOGTAG = "VonageOptions";
	Credentials credentials;
	ProgressDialog progressDialog;
	private VHTTPComm vHTTPComm = null;
	private final int CALL_CONTACT = 1;
	SharedPreferences currentLoginPrefs = null;
	GoogleAnalyticsTracker vPlaceCallTracker;
	
	@Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        vPlaceCallTracker = GoogleAnalyticsTracker.getInstance();
        currentLoginPrefs = getSharedPreferences("CURRENT_LOGIN", 0);
        
        credentials = new Credentials(getApplicationContext());
        vHTTPComm = VHTTPComm.getInstance(getApplicationContext()); // new VHTTPComm();
        progressDialog = new ProgressDialog(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        
        // Start the tracker in manual dispatch mode...
        vPlaceCallTracker.start("UA-20721251-7", this);
        
        // Init the Unique ID
        deviceID = Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID);
        
        //Start with this page tracking
        vPlaceCallTracker.trackPageView("AutoPlaceCall");
        
		// Read the SharedPreferences. Need the Phone Number to call later. Dont need the username though.
		// currentLogin = getApplicationContext().getSharedPreferences("CURRENT_LOGIN", 0);
		//username = currentLogin.getString("userName", null);
		//phoneNumber = currentLogin.getString("phoneNumber", null);
       
		//prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		
        setContentView(R.layout.placecall);
        
        // Get the intent and the number to forward to
        incomingIntent = getIntent();
        fwdNumber = incomingIntent.getStringExtra("FwdNumber");
        Log.d(LOGTAG, "From Intent : Please forward to : " + fwdNumber);
        
        // Set the title so it doesn't look awkward
        setTitle("Calling " + fwdNumber);
        
        new PlaceCallTask().execute("CheckLogin");
    }
	

	private class PlaceCallTask extends AsyncTask<String, String, String>
	{
		@Override
		protected void onPreExecute()
		{
//			progressDialog.setMessage("Initializing...");
			progressDialog.setCancelable(false);
			progressDialog.show();
		}
		
		@Override
		protected String doInBackground(String... params)
		{
			if (params[0].equalsIgnoreCase("CheckLogin"))
			{
				// Login process start
				vPlaceCallTracker.trackEvent("APCLogin", "Start", deviceID, -1);
				publishProgress("SetMessage", "Checking login status...");
				
				// Check if there's any saved credentials
				if(credentials.autoLoginCredPresent())
				{
					String[] autoLoginCreds = credentials.getAutoLoginCreds();
					
					if(autoLoginCreds == null) // If the credentials returned are null, we dont have anything to work on. Finish up.
					{
						publishProgress("ShowToast", "No auto login credentials set. Please set your credentials to auto login and try again");
						Intent settingsMenu = new Intent("com.yaseriesapps.vonageam.SHOW_GENERAL_SETTINGS");
						settingsMenu.addCategory("android.intent.category.DEFAULT");
						startActivity(settingsMenu);
						finish();
					}
					
					username = autoLoginCreds[0];
					password = autoLoginCreds[1];
					publishProgress("SetMessage", "Logging in as " + username);
					
					// Start the login process
					String loggedInAs = vHTTPComm.login(username, password, true); // First Login. Write data to Prefs
						
					//Check if login is successful
					if(loggedInAs.equalsIgnoreCase("Success"))
					{
						publishProgress("SetMessage", "Logged in as " + username);
						// Get the Phone Number
						phoneNumber = vHTTPComm.getPhoneNumber();
					    
						// Sanity check on the Phone number
						if(phoneNumber.equalsIgnoreCase("0000000000")) // Improper phone number received; That's weird
		            	{
		        	        Log.d(LOGTAG,"Improper number received : " + phoneNumber);
							vPlaceCallTracker.trackEvent("APCLogin", "PhoneNumUnavailable", deviceID, -1);
							publishProgress("ShowToast", "Login failed. Couldn't get your Vonage Phone Number. Please try again later.");
							finish();
		            	}
						
						vPlaceCallTracker.trackEvent("APCLogin", "Success", deviceID, -1);
						return "LoginSuccess";
					}
					// Failure means Wrong user name or password.
					else if(loggedInAs.equalsIgnoreCase("Failure")) 
					{
						// Login Failed with wrong user name
						vPlaceCallTracker.trackEvent("APCLogin", "WrongUnamePwd", deviceID, -1);
						
						publishProgress("ShowToast", "Login failed. Wrong username or password. Please try again later.");
						finish();
					}
					// HTTPError means Communication Error
					else if(loggedInAs.equalsIgnoreCase("HTTPError")) 
					{
						// Login Failed with non 200 response
						vPlaceCallTracker.trackEvent("APCLogin", "HTTPError", deviceID, -1);
						
						publishProgress("ShowToast", "Login failed. Cannot communicate with Vonage Server. Please try again later.");
						finish();
					}
					else // Unknown error
					{
						Log.d(LOGTAG,"Vonage Login failed, unknown error");
						publishProgress("ShowToast", "Login failed, unknown error");
						vPlaceCallTracker.trackEvent("APCLogin", "UnknownError", deviceID, -1);
						finish();
					}
				}
				else // There is no saved login credentials. Ask the user to log in to the main app and save the credentials
				{
					// Login Failed with wrong user name
					vPlaceCallTracker.trackEvent("APCLogin", "NoSavedUsername", deviceID, -1);
					
					publishProgress("ShowToast", "No saved credentials. Please log in to the app to save credentials and try again.");
					finish();
				}
			}
			else if (params[0].equalsIgnoreCase("SetForward"))
			{
				cleanedNumber = cleanNumber(fwdNumber);
				vPlaceCallTracker.trackEvent("APCForward", "Start", deviceID + "::" + cleanedNumber, -1);
				
				if("Nonsense".equalsIgnoreCase(cleanedNumber)) // If the number can be forwarded to, forward to it
				{
					Log.d(LOGTAG, "Improper number to forward to");
					publishProgress("ShowToast", "Call Forwarding failed. Please check the number you selected.");
					vPlaceCallTracker.trackEvent("APCForward", "WrongCleanedNumber", deviceID + "::" + cleanedNumber, -1);
					finish();
				}
				else // The number can be forwarded to.
   				{
					publishProgress("SetMessage", "Setting Call Forward to " + cleanedNumber);
					Log.d(LOGTAG, "Setting forward to " + cleanedNumber);

   					String enablefwdstatus = vHTTPComm.setForwardTo(cleanedNumber, false); // Login before setting Forward
   					if("Success".equalsIgnoreCase(enablefwdstatus)) // Setting change status
   	   				{
   						publishProgress("ShowToast", "Calls now forwarding to " + cleanedNumber);
   	   					vPlaceCallTracker.trackEvent("APCForward", "VPCCFSuccess", deviceID, -1);
   	   					return "ForwardSuccess";
   	   				}
   	   				else if("HTTPError".equalsIgnoreCase(enablefwdstatus)) // Setting change status
   					{
   						publishProgress("ShowToast", "Call Forwarding failed. Cannot communicate with Vonage Servers");
   						vPlaceCallTracker.trackEvent("APCForward", "HTTPCommError", deviceID, -1);
   						finish();
   					}
   					else if("NotSaved".equalsIgnoreCase(enablefwdstatus)) // Setting change status
   					{
   						publishProgress("ShowToast", "Call Forwarding failed. Could not save changes to settings");
   						vPlaceCallTracker.trackEvent("APCForward", "NotSaved", deviceID, -1);
   						finish();
   					}
   					else if("NotLoggedIn".equalsIgnoreCase(enablefwdstatus)) // This shouldn't actually happen.
   			        {
   			        	// Currently not logged in. finish() the Activity.
   			        	Log.d(LOGTAG, "Not currently logged in. Exit this Activity");
   			        	vPlaceCallTracker.trackEvent("APCForward", "NotLoggedIn", deviceID, -1);
   			        	return "NotLoggedIn";
   			        }
   					else
   					{
   						Log.d(LOGTAG, "Call Forwarding Failure");
   						publishProgress("ShowToast", "Call Forwarding failed. Try again later.");
   						vPlaceCallTracker.trackEvent("AutoPlaceCall", "Unknown", deviceID, -1);
   						finish();
   					}
   				}
			}
			else if (params[0].equalsIgnoreCase("PlaceCall"))
			{
				vPlaceCallTracker.trackEvent("APCCallNumber", "Start", deviceID, -1);
				if(prefs.getBoolean("calldelay_pref", true)) // If the call is to be delayed
				{
					Log.d(LOGTAG, "We should be placing a call with a 5 second delay. Hang tight.");
					publishProgress("SetMessage", "Waiting for 5 seconds before calling");
					try 
					{
						Thread.sleep(6000);
					} 
					catch (InterruptedException e)
					{
						e.printStackTrace();
					}
				}
   				
				publishProgress("SetMessage", "Calling " + cleanedNumber + ", please wait...");

				Log.d(LOGTAG, "Calling Vonage number " + phoneNumber + ", with forwarding to " + cleanedNumber);
				Intent intent = new Intent(Intent.ACTION_CALL); // Intent to call a number
				intent.setData(Uri.parse("tel://" + phoneNumber)); // The number to call
				startActivityForResult(intent, CALL_CONTACT); // Start the activity to pick data, and return with CALL_CONTACT
				
				if(prefs.getBoolean("revertsettings_pref", true)) // If the call forwarding is to be reverted
   				{
					// If we're reverting settings, change the ProgressDialog message before hand so it'll display the new msg
					publishProgress("SetMessage", "Reverting call forwarding in 10 seconds. Please wait...");
   				}
				
				return "RevertCallFwd";
			}
			else if (params[0].equalsIgnoreCase("RevertSettings"))
			{
				Log.d(LOGTAG, "Back from the call; Waiting 10 seconds for network to warm up before attempting to revert settings");
				try 
				{
					Thread.sleep(10000);
				} 
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
				
				String[] autoLoginCreds = credentials.getAutoLoginCreds();
				
				// Start the login process
				vHTTPComm.login(autoLoginCreds[0], autoLoginCreds[1], false); // Temp Login. Dont write to Prefs
				
				String revertTo = prefs.getString("setcallfwd_pref", null); // Get the old settings
   				String revertbackstatus = "";
   				vPlaceCallTracker.trackEvent("APCRevertNumber", "Start", deviceID + "::" + revertTo, -1);
				
   				if(prefs.getBoolean("revertsettings_pref", true)) // If the call forwarding is to be reverted
   				{
   					Log.d(LOGTAG, "Yes, we are reverting settings");
					if(revertTo == null)
					{
						publishProgress("ShowToast", "Disabling Call Forwarding"); // in 10 seconds");
						
						try
		   				{
							Thread.sleep(10000); // Sleep for 10 seconds and then disable call forwarding
						} 
		   				catch (InterruptedException e) 
		   				{
							e.printStackTrace();
						}
						
		   				Log.d(LOGTAG, "Disabling CF after call as old setting was at disabled");
	   	   				revertbackstatus = vHTTPComm.disableForward(true); // We just logged in. Just disable forward
	   	   				Log.d(LOGTAG, "Revert CF disable Status : " + revertbackstatus); // Success/Failure
					}
					else
					{
						publishProgress("ShowToast", "Reverting Call Forwarding to " + revertTo); // + " in 10 seconds");
						
						try
		   				{
							Thread.sleep(10000); // Sleep for 10 seconds and then disable call forwarding
						} 
		   				catch (InterruptedException e) 
		   				{
							e.printStackTrace();
						}
						
		   				Log.d(LOGTAG, "Reverting back to old CF, " + revertTo + " after a call");
	   	   				revertbackstatus = vHTTPComm.setForwardTo(revertTo, false); // We logged in before the call. Login again
	   	   				Log.d(LOGTAG, "Revert Call Forward Status : " + revertbackstatus); // Success/Failure
	
					}
   				}
   				else
   				{
//   					publishProgress("ShowToast", "Reverting to old settings not enabled. Vonage still forwarding to " + cleanedNumber);
   					Log.d(LOGTAG, "Reverting to old settings not enabled. Vonage still forwarding to " + cleanedNumber);
   					vPlaceCallTracker.trackEvent("APCRevertNumber", "SuccessNoRevert", deviceID, -1);
   					return "CallNumberSuccess"; // No reverting required. We're done here. Dismiss the dialog and finish()
   				}

   				if(revertbackstatus.equalsIgnoreCase("success"))
   				{
   					publishProgress("ShowToast", "Call Forwarding reverted to " + revertTo);
   					Log.d(LOGTAG, "Call Forwarding reverted to " + revertTo);
   					vPlaceCallTracker.trackEvent("APCRevertNumber", "Success", deviceID, -1);
   					return "CallNumberSuccess"; // We're done here. Dismiss the dialog and finish()
   				}
   				else if(revertbackstatus.equalsIgnoreCase("NotLoggedIn"))
		        {
   					// Currently not logged in. finish() the Activity.
			        Log.d(LOGTAG, "Not currently logged in. Exit this Activity");
			        publishProgress("ShowToast", "Could not login. Call Forwarding not reverted");
			        vPlaceCallTracker.trackEvent("APCRevertNumber", "NotLoggedIn", deviceID, -1);
   					return "CallNumberSuccess"; // All this does is to dismiss the dialog and finish()
		        }
   				else if(revertbackstatus.equalsIgnoreCase("HTTPError"))
		        {
			        Log.d(LOGTAG, "Cannot communicate with Vonage Servers. Call Forwarding not reverted");
			        publishProgress("ShowToast", "Cannot communicate with Vonage Servers. Call Forwarding not reverted");
			        vPlaceCallTracker.trackEvent("APCRevertNumber", "HTTPError", deviceID, -1);
			        return "CallNumberSuccess"; // All this does is to dismiss the dialog and finish()
		        }
   				else
   				{
   					publishProgress("ShowToast", "Reverting to old settings failed. Vonage still forwarding to " + cleanedNumber);
   					Log.d(LOGTAG, "Reverting to old settings failed. Vonage still forwarding to " + cleanedNumber);
   					vPlaceCallTracker.trackEvent("APCRevertNumber", "FailedUnknown", deviceID, -1);
   					return "CallNumberSuccess"; // All this does is to dismiss the dialog and finish()
   				}
				
			}
			return "WrongCode";
		}
		
		@Override
		protected void onProgressUpdate(String... values)
		{
			if("SetMessage".equalsIgnoreCase(values[0]))
			{
				progressDialog.setMessage(values[1]);
			}
			else if("ShowToast".equalsIgnoreCase(values[0]))
			{
				Toast.makeText(getApplicationContext(), values[1], Toast.LENGTH_LONG).show();
			}
		}

		@Override
		protected void onPostExecute(String result)
		{
			if(result.equalsIgnoreCase("LoginSuccess"))
			{
				// Once logged in, proceed to setting the Call Forward
				new PlaceCallTask().execute("SetForward");
			}
			else if(result.equalsIgnoreCase("ForwardSuccess"))
			{
				progressDialog.dismiss(); // We dont need the dialog anymore since the active call is on top
				
				// Call forwarding is successful. Now place the call.
				new PlaceCallTask().execute("PlaceCall");
			}
			else if(result.equalsIgnoreCase("RevertCallFwd"))
			{
				progressDialog.dismiss(); // We dont need the dialog anymore since the active call is on top
				/*
				// Outgoing call successful. Revert Call Forwarding settings
				progressDialog.dismiss(); // We dont need the dialog anymore since the active call is on top
				new PlaceCallTask().execute("RevertSettings");
				*/
			}
			else if(result.equalsIgnoreCase("CallNumberSuccess"))
			{
				// All done. Dismiss the dialog, and finish.
				Log.d(LOGTAG, "Finishing up in PlaceCall");
				progressDialog.dismiss(); // We dont need the dialog anymore since the active call is on top
				finish();
			}
			else if(result.equalsIgnoreCase("WrongCode"))
			{
				Log.d(LOGTAG, "Hey, we have the Wrong Code !");
			}
			else if(result.equalsIgnoreCase("NotLoggedIn"))
			{
				Toast.makeText(getApplicationContext(), "Failed to login. Please log in again.", Toast.LENGTH_LONG).show();
				finish();
			}	
	    }
		
	}
    
	@Override
    public void onActivityResult(int reqCode, int resultCode, Intent data)
    {
    	  super.onActivityResult(reqCode, resultCode, data);

    	  switch (reqCode)
    	  {
    	  	case (CALL_CONTACT) :
    	    	Log.d(LOGTAG, "Calling Contact returned with result code " + resultCode);
    	    	if (resultCode == Activity.RESULT_CANCELED) // You return to call logs after the call, and you cancel that to return to the app
				{
					Log.d(LOGTAG, "Back from making a call");
					vPlaceCallTracker.trackEvent("APCCallNumber", "Success", deviceID, -1);
					
					// Outgoing call successful. Revert Call Forwarding settings
					new PlaceCallTask().execute("RevertSettings");
				}
    	    	else
    	    	{
    	    		Log.d(LOGTAG, "Something went wrong trying to make a call");
    	    		vPlaceCallTracker.trackEvent("APCCallNumber", "UnknownError", deviceID, -1);
//    	    		selectSetForward("", false);
    	    	}
    	    	break;
    	    	
    	    default :
    	    	Log.d(LOGTAG, "Some other activity returned us reqCode and resultCode : " + reqCode + ", " + resultCode ); 
    	    	Log.d(LOGTAG, "Also returned us these : " + data.getAction() + ", " + data.getDataString());
    	    	break;
    	  }
    	}	
	
	@Override
	protected void onDestroy() 
	{
	    super.onDestroy();
	    
	    credentials.cleanUp();
        credentials = null;
        
	    // Dispatch the stats and stop the tracker when it is no longer needed.
	    vPlaceCallTracker.dispatch();
	    vPlaceCallTracker.stop();
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
//    		Log.d(LOGTAG, "Processing dirty number, " + dirtyNumber[dirtyNumCount] + ", " + temp);
    		if(temp > 47 && temp < 58) // 48 = 0 and 57 = 9 in ASCII. Filter those values
    		{
    			sb.append(dirtyNumber[dirtyNumCount]);
    		}
    	}

    	number = sb.toString();
    	if(number.startsWith("011")) // Normalize the number so the next conditions dont freak out
    		number = number.replace("011", "+"); // Replace  011 with +
    	
//    	Log.d(LOGTAG, "Cleaned number : " + number);
    	numLength = number.length();
    	
    	if(numLength <= 9) // Number length less than 10 digits, It's a nonsense number
    	{
    		Log.d(LOGTAG, "Length less than 9");
    		return "Nonsense";
    	}
    	else if(numLength >= 12 && !(number.contains("+"))) // If number too long with no + for ISD calls, it's improper
    	{
    		Log.d(LOGTAG, "Length >= 12, no +");
    		return "Nonsense";
    	}
    	else if(number.contains("+"))
    	{
    		number = number.replace("+", "011"); // Replace the + with the dialing 011 code
    		Log.d(LOGTAG, "Forwardable International Number : " + number);
    		return number;
    	}
    	else if(numLength >= 10 && numLength <= 11)
    	{
    		if(number.length()==10) // Add '1' to the number to standardize it
    			number = "1"+number;
    		
    		Log.d(LOGTAG, "Forwardable Number : " + number);
    		return number;
    	}
    	else // We dont know what we received. Return Nonsense.
    	{
    		Log.d(LOGTAG, "Holy crap ! What just happened ??");
    		return "Nonsense";
    	}
    }
	
}
