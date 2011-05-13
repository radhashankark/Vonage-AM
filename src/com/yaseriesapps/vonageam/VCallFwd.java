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
import android.provider.Contacts;
import android.provider.Contacts.People;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class VCallFwd extends Activity
{
	private EditText setCallFwdNum;
	private Button setCallFwd, pickContacts;
	private OnClickListener setCallFwdBtnListener, callFwdContactsBtnListener;
	ProgressDialog progressDialog;
	private final String LOGTAG = "VonageOptions";
	private final int PICK_CONTACT = 2;
	private VHTTPComm vHTTPComm = null;
	private String username, phoneNumber, displayContact, deviceID;
	private String fwdNumber = null;
	GoogleAnalyticsTracker vCallFwdTracker;
	
	@Override public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        
        vCallFwdTracker = GoogleAnalyticsTracker.getInstance();
        vHTTPComm = VHTTPComm.getInstance(getApplicationContext()); // new VHTTPComm();
        
        // Start the tracker in manual dispatch mode...
        vCallFwdTracker.start("UA-20721251-7", this);
        
        // Init the Unique ID
        deviceID = Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID);
        
        //Start with this page tracking
        vCallFwdTracker.trackPageView("CallFwd");
        
        setContentView(R.layout.setcallfwd);
        
        initView();
        
    }
	
	private void initView()
	{
		progressDialog = new ProgressDialog(this);
		
        setCallFwdNum = (EditText) findViewById(R.id.setcallfwdnumber);
        setCallFwd = (Button) findViewById(R.id.setcallfwdbtn);
        pickContacts = (Button) findViewById(R.id.callfwdcontactsbtn);
        
        setCallFwdBtnListener = new OnClickListener() 
        {
			@Override public void onClick(View v) 
			{
				displayContact = setCallFwdNum.getText().toString();
	   			if(displayContact.equalsIgnoreCase("")) // Display is blank. Disable call forwarding
	   			{
	   				new VCallFwdTask().execute("disableforward", "");
	   			}
	   			else
	   			{
	   				new VCallFwdTask().execute("enableForward", displayContact);
	   			}
			}
		};
		
		callFwdContactsBtnListener = new OnClickListener()
		{			
			@Override public void onClick(View v)
			{
				Log.d(LOGTAG, "Sending Intent to pick a number from Contacts");
				Intent intent = new Intent(Intent.ACTION_PICK); // Intent to pick something
				intent.setType(Contacts.Phones.CONTENT_TYPE); // Type of data to pick, Contacts
				startActivityForResult(intent, PICK_CONTACT); // Start the activity to pick data, and return with PICK_CONTACT
				
			}
		};
        
        setCallFwd.setOnClickListener(setCallFwdBtnListener);
        pickContacts.setOnClickListener(callFwdContactsBtnListener);
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
    	if(number.startsWith("011")) // Normalize the number so the next conditions dont freak out
    		number = number.replace("011", "+"); // Replace  011 with +

//    	Log.d(LOGTAG, "Cleaned number : " + number);
    	numLength = number.length();
    	
    	if(numLength <= 9) // Number length less than 10 digits, It's a nonsense number
    	{
    		return "Nonsense";
    	}
    	else if(numLength >= 12 && !(number.contains("+"))) // If number too long with no + for ISD calls, it's improper
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
    
    private class VCallFwdTask extends AsyncTask<String, String, String>
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
			if(params[0].equalsIgnoreCase("disableforward"))
			{
				publishProgress("Disabling Call Forwarding...");
				vCallFwdTracker.trackEvent("CFDisableFwd", "Start", deviceID, -1);
   				Log.d(LOGTAG, "Forward field blank. Disabling Call Forward for " + phoneNumber);
   				String disablefwdstatus = vHTTPComm.disableForward(false); // Login and Disable Forward
   				
   				Log.d(LOGTAG, "Disable Call Forward Status : " + disablefwdstatus); // Success/Failure
   				if("Success".equalsIgnoreCase(disablefwdstatus))
   				{
   					publishProgress("ShowToast", "Disabling Call Forwarding Success");
   					vCallFwdTracker.trackEvent("CFDisableFwd", "Success", deviceID, -1);
   					return "disablefwdsucc";
   				}
   				else if("HTTPError".equalsIgnoreCase(disablefwdstatus)) // Setting change status
				{
					Log.d(LOGTAG, "Disabling Call Forwarding Failure. Response null");
					publishProgress("ShowToast", "Disabling Call Forwarding Failed. Cannot communicate with Vonage Servers");
					vCallFwdTracker.trackEvent("CFDisableFwd", "HTTPCommError", deviceID, -1);
					return "disablefwdfail";
				}
				else if("NotSaved".equalsIgnoreCase(disablefwdstatus)) // Setting change status
				{
					Log.d(LOGTAG, "Disabling Call Forwarding Failure. Settings not saved");
					publishProgress("ShowToast", "Disabling Call Forwarding Failed. Could not save changes to settings");
					vCallFwdTracker.trackEvent("CFDisableFwd", "NotSaved", deviceID, -1);
					return "disablefwdfail";
				}
				else if("NotLoggedIn".equalsIgnoreCase(disablefwdstatus))
		        {
		        	// Currently not logged in. finish() the Activity.
		        	Log.d(LOGTAG, "Not currently logged in. Exit this Activity");
		        	return "NotLoggedIn";
		        }
   				else
   				{
   					publishProgress("ShowToast", "Disabling Call Forwarding Failed");
   					vCallFwdTracker.trackEvent("CFDisableFwd", "UnknownError", deviceID, -1);
   					return "disablefwdfail";
   				}
			}
   			else if(params[0].equalsIgnoreCase("enableforward"))
   			{
   				publishProgress("SetMessage", "Getting the number");
   				Log.d(LOGTAG, "Cleaning number " + displayContact);
   				String cleanedNumber = cleanNumber(displayContact); // Clean the number and get it back
   				vCallFwdTracker.trackEvent("CFEnableFwd", "Start", deviceID + "::" + cleanedNumber, -1);
   				
   				Log.d(LOGTAG, "Cleaned Number : " + cleanedNumber);
   				if(!"Nonsense".equalsIgnoreCase(cleanedNumber)) // If the number can be forwarded to, forward to it
   				{
   					publishProgress("Setting Call Forward to " + cleanedNumber);
   					fwdNumber = cleanedNumber;
   					String enablefwdstatus = vHTTPComm.setForwardTo(cleanedNumber, false); // Login and set Forward
   					if("Success".equalsIgnoreCase(enablefwdstatus))
   	   				{
   	   					publishProgress("ShowToast", "Enabling Call Forwarding Success");
   	   					vCallFwdTracker.trackEvent("CFEnableFwd", "Success", deviceID, -1);
   	   					return "enablefwdsucc";
   	   				}
   	   				else if("HTTPError".equalsIgnoreCase(enablefwdstatus)) // Setting change status
   					{
   						Log.d(LOGTAG, "Disabling Call Forwarding Failure. Response null");
   						publishProgress("ShowToast", "Enabling Call Forwarding Failed. Cannot communicate with Vonage Servers");
   						vCallFwdTracker.trackEvent("CFEnableFwd", "HTTPCommError", deviceID, -1);
   						return "enablefwdfail";
   					}
   					else if("NotSaved".equalsIgnoreCase(enablefwdstatus)) // Setting change status
   					{
   						Log.d(LOGTAG, "Disabling Call Forwarding Failure. Settings not saved");
   						publishProgress("ShowToast", "Enabling Call Forwarding Failed. Could not save changes to settings");
   						vCallFwdTracker.trackEvent("CFEnableFwd", "NotSaved", deviceID, -1);
   						return "enablefwdfail";
   					}
   					else if("NotLoggedIn".equalsIgnoreCase(enablefwdstatus))
   			        {
   			        	// Currently not logged in. finish() the Activity.
   			        	Log.d(LOGTAG, "Not currently logged in. Exit this Activity");
   			        	return "NotLoggedIn";
   			        }
   	   				else
   	   				{
   	   					publishProgress("ShowToast", "Enabling Call Forwarding Failed");
   	   					vCallFwdTracker.trackEvent("CFEnableFwd", "UnknownError", deviceID, -1);
   	   					return "enablefwdfail";
   	   				}
   				}
   				else // If we get back Nonsense as cleaned number
   				{
   					return "enablefwdfail";
   				}
   			}
   			else
   			{
   				return null;
   			}
		}
		
		@Override
		protected void onProgressUpdate(String... values)
		{
			progressDialog.setMessage(values[0]);
		}

		@Override
		protected void onPostExecute(String result)
		{
			progressDialog.dismiss();
			
			if(result.equalsIgnoreCase("disablefwdsucc"))
			{
				// Get the Shared Preferences for the Application so we can update the status
		    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		    	SharedPreferences.Editor prefsEditor = prefs.edit();
		    	
				prefsEditor.putString("setcallfwd_pref", "Disabled");
	        	prefsEditor.commit();
				
				Toast.makeText(getApplicationContext(), "Call Forwarding disabled", Toast.LENGTH_SHORT).show();
			}
			else if(result.equalsIgnoreCase("disablefwdfail"))
			{
				Toast.makeText(getApplicationContext(), "Call Forwarding disable failed", Toast.LENGTH_LONG).show();
			}
			else if(result.equalsIgnoreCase("enablefwdsucc"))
			{
				// Get the Shared Preferences for the Application so we can update the status
		    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		    	SharedPreferences.Editor prefsEditor = prefs.edit();
		    	
				prefsEditor.putString("setcallfwd_pref", fwdNumber);
	        	prefsEditor.commit();
				
				Toast.makeText(getApplicationContext(), "Call Forwarding successful", Toast.LENGTH_SHORT).show();
			}
			else if(result.equalsIgnoreCase("enablefwdfail"))
			{
				Toast.makeText(getApplicationContext(), "Call forwarding enable failed", Toast.LENGTH_LONG).show();
//				selectSetForward(displayContact); // Give back the number they wanted to set the forward to so they can try again
				setCallFwdNum.setText(displayContact);
			}
			else if(result.equalsIgnoreCase("NotLoggedIn"))
			{
				Toast.makeText(getApplicationContext(), "Failed to log in. Please try again.", Toast.LENGTH_LONG).show();
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
    	    case (PICK_CONTACT) :
    	    	Log.d(LOGTAG, "Picking contact returned with result code " + resultCode);
    	    	if (resultCode == Activity.RESULT_OK)
				{
					Uri contactData = data.getData();
					Cursor c =  managedQuery(contactData, null, null, null, null);
					if (c.moveToFirst()) 
					{
						displayContact = c.getString(c.getColumnIndexOrThrow(People.NUMBER));
//						selectSetForward(displayContact);
						setCallFwdNum.setText(displayContact);

						Log.d(LOGTAG, "Back from contacts, we have a display number : " + displayContact);
						
					}
				}
    	    	else
    	    	{
    	    		Log.d(LOGTAG, "Couldn't get back a number after picking contacts");
//    	    		selectSetForward("");
    	    		setCallFwdNum.setText("");
    	    	}
    	    	break;

    	    default :
    	    	Log.d(LOGTAG, "Some other activity returned us reqCode and resultCode : " + reqCode + ", " + resultCode ); 
    	    	Log.d(LOGTAG, "Also returned us these : " + data.getAction() + ", " + data.getDataString());
    	    	break;
    	  }
	}	

	@Override public void onRestart()
	{
		super.onRestart();
	}

	@Override public void onResume()
	{
		super.onResume();
	}

	@Override public void onDestroy()
    {
        super.onDestroy();
        
        // Dispatch the stats and stop the tracker when it is no longer needed.
        vCallFwdTracker.dispatch();
        vCallFwdTracker.stop();
    }

}
