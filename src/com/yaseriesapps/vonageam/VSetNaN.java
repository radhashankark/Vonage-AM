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

public class VSetNaN extends Activity
{
	private EditText setNaNNum;
	private Button setNaN, pickContacts;
	private OnClickListener setNaNBtnListener, setNaNContactsBtnListener;
	ProgressDialog progressDialog;
	private final String LOGTAG = "VonageOptions";
	private final int PICK_CONTACT = 2;
	private VHTTPComm vHTTPComm = null;
	private String username, phoneNumber, displayContact, deviceID;
	private String nanNumber = null;
	GoogleAnalyticsTracker vSetNANTracker;
	
	@Override public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        
        vSetNANTracker = GoogleAnalyticsTracker.getInstance();
        vHTTPComm = VHTTPComm.getInstance(getApplicationContext()); // new VHTTPComm();

        // Start the tracker in manual dispatch mode...
        vSetNANTracker.start("UA-20721251-7", this);

        // Init the Unique ID
        deviceID = Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID);
        
        //Start with this page tracking
        vSetNANTracker.trackPageView("SetNAN");
        
        setContentView(R.layout.setnan);
        
        initView();
    }
	
	private void initView()
	{
		progressDialog = new ProgressDialog(this);
		
        setNaNNum = (EditText) findViewById(R.id.setnannumber);
        setNaN = (Button) findViewById(R.id.setnanbtn);
        pickContacts = (Button) findViewById(R.id.setnancontactsbtn);
        
        setNaNBtnListener = new OnClickListener() 
        {
			@Override public void onClick(View v) 
			{
				displayContact = setNaNNum.getText().toString();
	   			if(displayContact.equalsIgnoreCase("")) // Display is blank. Disable call forwarding
	   			{
	   				new VSetNANTask().execute("disablenan", "");
	   			}
	   			else
	   			{
	   				new VSetNANTask().execute("enablenan", displayContact);
	   			}
			}
		};
		
		setNaNContactsBtnListener = new OnClickListener()
		{			
			@Override public void onClick(View v)
			{
				Log.d(LOGTAG, "Sending Intent to pick a number from Contacts");
				Intent intent = new Intent(Intent.ACTION_PICK); // Intent to pick something
				intent.setType(Contacts.Phones.CONTENT_TYPE); // Type of data to pick, Contacts
				startActivityForResult(intent, PICK_CONTACT); // Start the activity to pick data, and return with PICK_CONTACT
				
			}
		};
        
        setNaN.setOnClickListener(setNaNBtnListener);
        pickContacts.setOnClickListener(setNaNContactsBtnListener);
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
    	else if(numLength >= 12 && !(number.contains("+") && !(number.startsWith("011")))) // If number too long with no + for ISD calls, it's improper
    	{
    		return "Nonsense";
    	}
    	else if(number.contains("+"))
    	{
    		number = number.replace("+", "011"); // Replace the + with the dialing 011 code
//    		Log.d(LOGTAG, "Forwardable International Numnber : " + number);
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
    
    private class VSetNANTask extends AsyncTask<String, String, String>
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
			if(params[0].equalsIgnoreCase("disablenan"))
			{
				vSetNANTracker.trackEvent("DisableNAN", "Start", deviceID, -1);
				
				publishProgress("SetMessage", "Disabling Network Availability Number...");
   				Log.d(LOGTAG, "NAN field blank. Disabling NAN for " + phoneNumber);
   				String disablenanstatus = vHTTPComm.disableNaN(false); // Login beforeDisable Forward
   				
   				Log.d(LOGTAG, "Disable NAN Status : " + disablenanstatus); // Success/Failure
   				if("Success".equalsIgnoreCase(disablenanstatus))
   				{
   					publishProgress("ShowToast", "Disabling Network Availability Number Success");
   					vSetNANTracker.trackEvent("DisableNAN", "Success", deviceID, -1);
   					return "disablenansucc";
   				}
   				else if("HTTPError".equalsIgnoreCase(disablenanstatus)) // Setting change status
				{
					Log.d(LOGTAG, "Disable Network Availability Number Failure. Response null");
					publishProgress("ShowToast", "Disabling Network Availability Number Failed. Cannot communicate with Vonage Servers");
					vSetNANTracker.trackEvent("DisableNAN", "HTTPCommError", deviceID, -1);
					return "disablenanfail";
				}
				else if("NotSaved".equalsIgnoreCase(disablenanstatus)) // Setting change status
				{
					Log.d(LOGTAG, "Disable Network Availability Number Failure. Settings not saved");
					publishProgress("ShowToast", "Disabling Network Availability Number Failed. Could not save changes to settings");
					vSetNANTracker.trackEvent("DisableNAN", "NotSaved", deviceID, -1);
					return "disablenanfail";
				}
				else if("NotLoggedIn".equalsIgnoreCase(disablenanstatus))
		        {
		        	// Currently not logged in. finish() the Activity.
		        	Log.d(LOGTAG, "Not currently logged in. Exit this Activity");
		        	return "NotLoggedIn";
		        }
   				else
   				{
   					publishProgress("ShowToast", "Disabling Network Availability Number Failed");
   					vSetNANTracker.trackEvent("DisableNAN", "UnknownError", deviceID, -1);
   					return "disablenanfail";
   				}
			}
   			else if(params[0].equalsIgnoreCase("enablenan"))
   			{
   				publishProgress("SetMessage", "Getting the number");
   				Log.d(LOGTAG, "Cleaning number " + displayContact);
   				String cleanedNumber = cleanNumber(displayContact); // Clean the number and get it back
   				
   				vSetNANTracker.trackEvent("EnableNAN", "EnableNANStart", deviceID + "::" + cleanedNumber, -1);
   				Log.d(LOGTAG, "Cleaned Number : " + cleanedNumber);
   				if(!"Nonsense".equalsIgnoreCase(cleanedNumber)) // If the number can be forwarded to, forward to it
   				{
   					publishProgress("SetMessage", "Enabling Network Availability Number to " + cleanedNumber);
   					nanNumber = cleanedNumber;
   					String enableNANStaus = vHTTPComm.enableNaN(cleanedNumber, false); // Login before setting NAN
   					if("Success".equalsIgnoreCase(enableNANStaus)) // Setting change status
   					{
   						Log.d(LOGTAG, "Enable Network Availability Number Success");
   						publishProgress("ShowToast", "Enabling Network Availability Number Success");
   						vSetNANTracker.trackEvent("EnableNAN", "Success", deviceID, -1);
   						return "enablenansucc";
   					}
   					else if("HTTPError".equalsIgnoreCase(enableNANStaus)) // Setting change status
   					{
   						Log.d(LOGTAG, "Enable Network Availability Number Failure");
   						publishProgress("ShowToast", "Enabling Network Availability Number Failed. Cannot communicate with Vonage Servers");
   						vSetNANTracker.trackEvent("EnableNAN", "HTTPCommError", deviceID, -1);
   						return "enablenanfail";
   					}
   					else if("NotSaved".equalsIgnoreCase(enableNANStaus)) // Setting change status
   					{
   						Log.d(LOGTAG, "Enable Network Availability Number Failure");
   						publishProgress("ShowToast", "Enabling Network Availability Number Failed. Could not save changes to settings");
   						vSetNANTracker.trackEvent("EnableNAN", "NotSaved", deviceID, -1);
   						return "enablenanfail";
   					}
   					else if("NotLoggedIn".equalsIgnoreCase(enableNANStaus))
   			        {
   			        	// Currently not logged in. finish() the Activity.
   			        	Log.d(LOGTAG, "Not currently logged in. Exit this Activity");
   			        	return "NotLoggedIn";
   			        }
   					else
   					{
   						Log.d(LOGTAG, "Enable Network Availability Number Failure");
   						publishProgress("ShowToast", "Enabling Network Availability Number Failed");
   						vSetNANTracker.trackEvent("EnableNAN", "UnknownError", deviceID, -1);
   						return "enablenanfail";
   					}
   				}
   				else // If we get back Nonsense as cleaned number
   				{
   					return "enablenanfail";
   				}
   			}
   			else
   			{
   				return "UnknownCode";
   			}
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
			progressDialog.dismiss();
			
			if(result.equalsIgnoreCase("disablenansucc"))
			{
				// Get the Shared Preferences for the Application so we can update the status
		    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		    	SharedPreferences.Editor prefsEditor = prefs.edit();
		    	
				prefsEditor.putString("networkavailability_pref", "Disabled");
	        	prefsEditor.commit();
	        	
				//Toast.makeText(getApplicationContext(), "Network Availability Number disabled", Toast.LENGTH_SHORT).show();
			}
			else if(result.equalsIgnoreCase("disablenanfail"))
			{
				//Toast.makeText(getApplicationContext(), "Network Availability Number disable failed", Toast.LENGTH_LONG).show();
			}
			else if(result.equalsIgnoreCase("enablenansucc"))
			{
				// Get the Shared Preferences for the Application so we can update the status
		    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		    	SharedPreferences.Editor prefsEditor = prefs.edit();
		    	
		    	prefsEditor.putString("networkavailability_pref", nanNumber);
	        	prefsEditor.commit();
	        	
				//Toast.makeText(getApplicationContext(), "Network Availability Number enable successful", Toast.LENGTH_SHORT).show();
			}
			else if(result.equalsIgnoreCase("enablenanfail"))
			{
				//Toast.makeText(getApplicationContext(), "Network Availability Number enable failed", Toast.LENGTH_LONG).show();
				setNaNNum.setText(displayContact);
			}
			else if(result.equalsIgnoreCase("NotLoggedIn"))
			{
				Toast.makeText(getApplicationContext(), "Logged out due to inactivity. Please log in again.", Toast.LENGTH_LONG).show();
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
						setNaNNum.setText(displayContact);

						Log.d(LOGTAG, "Back from contacts, we have a display number : " + displayContact);
						
					}
				}
    	    	else
    	    	{
    	    		Log.d(LOGTAG, "Couldn't get back a number after picking contacts");
//    	    		selectSetForward("");
    	    		setNaNNum.setText("");
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
        vSetNANTracker.dispatch();
        vSetNANTracker.stop();
    }

}
