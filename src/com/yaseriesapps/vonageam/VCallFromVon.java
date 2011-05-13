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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

public class VCallFromVon extends Activity
{
	private EditText callFromVonNum;
	private Button callFromVon, callFromVonContacts;
	private CheckBox delayCall, revertBack;
	private OnClickListener callFromVonBtnListener, callFromVonContactsBtnListener;
	ProgressDialog progressDialog;
	private final String LOGTAG = "VonageOptions";
	private final int PICK_CONTACT_CALL = 3, CALL_CONTACT = 4;
	private VHTTPComm vHTTPComm = null;
	private String username, phoneNumber, displayContact, revertTo, deviceID;
	GoogleAnalyticsTracker vCallFromVonTracker;
	
	@Override public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        
        vCallFromVonTracker = GoogleAnalyticsTracker.getInstance();
        vHTTPComm = VHTTPComm.getInstance(getApplicationContext()); // new VHTTPComm();

        // Start the tracker in manual dispatch mode...
        vCallFromVonTracker.start("UA-20721251-7", this);

        // Init the Unique ID
        deviceID = Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID);
        
        //Start with this page tracking
        vCallFromVonTracker.trackPageView("CallFromVon");
        
        setContentView(R.layout.callfromvonage);
        
        SharedPreferences currentLogin = getSharedPreferences("CURRENT_LOGIN", 0);
        username = currentLogin.getString("userName", null);
        phoneNumber = currentLogin.getString("phoneNumber", null);
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        revertTo = prefs.getString("setcallfwd_pref", "Bleh");
//        revertTo = vHTTPComm.getCurrentCFSetting(phoneNumber);
        
        initView();
        
    }
	
	private void initView()
	{
		progressDialog = new ProgressDialog(this);
		
        callFromVonNum = (EditText) findViewById(R.id.callfromfwdnumber);
        callFromVon = (Button) findViewById(R.id.callfromvonbtn);
        callFromVonContacts = (Button) findViewById(R.id.callfromvoncontactsbtn);
        delayCall = (CheckBox) findViewById(R.id.delaycallckbx);
        revertBack = (CheckBox) findViewById(R.id.revertbackcfckbx);
        
        callFromVonBtnListener = new OnClickListener() 
        {
			@Override public void onClick(View v) 
			{
				displayContact = callFromVonNum.getText().toString();
	   			if(displayContact.equalsIgnoreCase("")) // Display is blank. Disable call forwarding
	   			{
	   				new VCallFromVonTask().execute("disableforward", "");
	   			}
	   			else
	   			{
	   				new VCallFromVonTask().execute("callNumber", displayContact);
	   			}
			}
		};
		
		callFromVonContactsBtnListener = new OnClickListener()
		{			
			@Override public void onClick(View v)
			{
				Log.d(LOGTAG, "Sending Intent to pick a number from Contacts");
				Intent intent = new Intent(Intent.ACTION_PICK); // Intent to pick something
				intent.setType(Contacts.Phones.CONTENT_TYPE); // Type of data to pick, Contacts
				startActivityForResult(intent, PICK_CONTACT_CALL); // Start the activity to pick data, and return with PICK_CONTACT
				
			}
		};
        
		callFromVon.setOnClickListener(callFromVonBtnListener);
		callFromVonContacts.setOnClickListener(callFromVonContactsBtnListener);
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
    
    private class VCallFromVonTask extends AsyncTask<String, String, String>
    {
    	@Override
		protected void onPreExecute()
		{
    		progressDialog.setCancelable(false);
    		//progressDialog.setMessage("Initializing...");
    		progressDialog.show();
		}
    	
		@Override
		protected String doInBackground(String... params)
		{
			if(params[0].equalsIgnoreCase("disableforward"))
			{
				vCallFromVonTracker.trackEvent("CFVDisableFwd", "Start", deviceID, -1);
	   			if (params[1].equalsIgnoreCase("disableaftercall")) // Second parameter means wait 15 secs before disabling
	   			{
					Log.d(LOGTAG, "Back from the call; Waiting 10 seconds for network to warm up before attempting to disable CF");

	   				try
	   				{
						Thread.sleep(10000); // Sleep for 10 seconds and then disable call forwarding
					} 
	   				catch (InterruptedException e) 
	   				{
						e.printStackTrace();
					} 
	   				
	   				
	   			}
	   			
   				Log.d(LOGTAG, "Disabling Call Forward for " + phoneNumber + " after a call");
  				
   				String disablefwdstatus = vHTTPComm.disableForward(false); // Login and Disable Forward
   				
   				Log.d(LOGTAG, "Disable Call Forward Status : " + disablefwdstatus); // Success/Failure
   				if("Success".equalsIgnoreCase(disablefwdstatus))
   				{
   					publishProgress("ShowToast", "Disabling Call Forwarding Success");
   					vCallFromVonTracker.trackEvent("CFVDisableFwd", "Success", deviceID, -1);
   					return "disablefwdsucc";
   				}
   				else if("HTTPError".equalsIgnoreCase(disablefwdstatus)) // Setting change status
				{
					Log.d(LOGTAG, "Disabling Call Forwarding Failure. Response null");
					publishProgress("ShowToast", "Disabling Call Forwarding Failed. Cannot communicate with Vonage Servers");
					vCallFromVonTracker.trackEvent("CFVDisableFwd", "HTTPCommError", deviceID, -1);
					return "disablefwdfail";
				}
				else if("NotSaved".equalsIgnoreCase(disablefwdstatus)) // Setting change status
				{
					Log.d(LOGTAG, "Disabling Call Forwarding Failure. Settings not saved");
					publishProgress("ShowToast", "Disabling Call Forwarding Failed. Could not save changes to settings");
					vCallFromVonTracker.trackEvent("CFVDisableFwd", "NotSaved", deviceID, -1);
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
   					vCallFromVonTracker.trackEvent("CFVDisableFwd", "UnknownError", deviceID, -1);
   					return "disablefwdfail";
   				}
   			}
   			else if (params[0].equalsIgnoreCase("revertaftercall")) // Revert back to old settings
   			{
   				vCallFromVonTracker.trackEvent("CFVRevert", "Start", deviceID + "::" + revertTo, -1);
   				
   				try
   				{
					Thread.sleep(15000); // Sleep for 10 seconds and then disable call forwarding
				} 
   				catch (InterruptedException e) 
   				{
					e.printStackTrace();
				} 
   				
   				String revertbackstatus = "";
   				if(revertTo.length() > 6) // Can revert back to a number
   				{
   	   				Log.d(LOGTAG, "Reverting back to old CF, " + revertTo + " after a call");
   	   				revertbackstatus = vHTTPComm.setForwardTo(revertTo, false); // Login before setting Forward
   	   				Log.d(LOGTAG, "Revert Call Forward Status : " + revertbackstatus); // Success/Failure
   				}
   				else
   				{
   	   				Log.d(LOGTAG, "Disabling CF after call as old setting was at disabled");
   	   				revertbackstatus = vHTTPComm.disableForward(false); // Login before disabling
   	   				Log.d(LOGTAG, "Revert CF disable Status : " + revertbackstatus); // Success/Failure
   				}
   				
   				if("Success".equalsIgnoreCase(revertbackstatus))
   				{
   					publishProgress("ShowToast", "Revert to old settings successful");
   					vCallFromVonTracker.trackEvent("CFVRevert", "Success", deviceID, -1);
   					return "revertbacksucc";
   				}
   				else if("HTTPError".equalsIgnoreCase(revertbackstatus)) // Setting change status
				{
					Log.d(LOGTAG, "Disabling Call Forwarding Failure. Response null");
					publishProgress("ShowToast", "Revert to old settings Failed. Cannot communicate with Vonage Servers");
					vCallFromVonTracker.trackEvent("CFVRevert", "HTTPCommError", deviceID, -1);
					return "revertbackfail";
				}
				else if("NotSaved".equalsIgnoreCase(revertbackstatus)) // Setting change status
				{
					Log.d(LOGTAG, "Disabling Call Forwarding Failure. Settings not saved");
					publishProgress("ShowToast", "Revert to old settings Failed. Could not save changes to settings");
					vCallFromVonTracker.trackEvent("CFVRevert", "NotSaved", deviceID, -1);
					return "revertbackfail";
				}
				else if("NotLoggedIn".equalsIgnoreCase(revertbackstatus))
		        {
		        	// Currently not logged in. finish() the Activity.
		        	Log.d(LOGTAG, "Not currently logged in. Exit this Activity");
		        	return "NotLoggedIn";
		        }
   				else
   				{
   					publishProgress("ShowToast", "Revert to old settings Failed");
   					vCallFromVonTracker.trackEvent("CFVRevert", "UnknownError", deviceID, -1);
   					return "revertbackfail";
   				}
   			}
			else if(params[0].equalsIgnoreCase("callNumber"))
			{
				vCallFromVonTracker.trackEvent("CFVCallNumber", "Start", deviceID, -1);
				
   				publishProgress("Getting the number");
   				Log.d(LOGTAG, "Cleaning number " + displayContact);
   				String cleanedNumber = cleanNumber(displayContact); // Clean the number and get it back
   				
   				vCallFromVonTracker.trackEvent("CFVEnableFwd", "Start", deviceID + "::" + cleanedNumber, -1);
   				Log.d(LOGTAG, "Cleaned Number : " + cleanedNumber);
   				if(!"Nonsense".equalsIgnoreCase(cleanedNumber)) // If the number can be forwarded to, forward to it
   				{
   					publishProgress("Setting Call Forward to " + cleanedNumber);
   					String callNumCFStatus = vHTTPComm.setForwardTo(cleanedNumber, false); // Log in to set forward
   					
   					if("Success".equalsIgnoreCase(callNumCFStatus))
   	   				{
   						Log.d(LOGTAG, "Call Forwarding Success");
   						vCallFromVonTracker.trackEvent("CFVEnableFwd", "Success", deviceID, -1);
   	   				}
   	   				else if("HTTPError".equalsIgnoreCase(callNumCFStatus)) // Setting change status
   					{
   						Log.d(LOGTAG, "Enabling Call Forwarding For Calling Number Failure. Response null");
   						publishProgress("ShowToast", "Enabling Call Forwarding For Calling Number Failed. Cannot communicate with Vonage Servers");
   						vCallFromVonTracker.trackEvent("CFVEnableFwd", "HTTPCommError", deviceID, -1);
   						return "enablefwdcallfail";
   					}
   					else if("NotSaved".equalsIgnoreCase(callNumCFStatus)) // Setting change status
   					{
   						Log.d(LOGTAG, "Enabling Call Forwarding For Calling Number Failure. Settings not saved");
   						publishProgress("ShowToast", "Enabling Call Forwarding For Calling Number Failed. Could not save changes to settings");
   						vCallFromVonTracker.trackEvent("CFVEnableFwd", "NotSaved", deviceID, -1);
   						return "enablefwdcallfail";
   					}
   					else if("NotLoggedIn".equalsIgnoreCase(callNumCFStatus))
   			        {
   			        	// Currently not logged in. finish() the Activity.
   			        	Log.d(LOGTAG, "Not currently logged in. Exit this Activity");
   			        	return "NotLoggedIn";
   			        }
   	   				else
   	   				{
   	   					publishProgress("ShowToast", "Enabling Call Forwarding For Calling Number Failed");
   	   					vCallFromVonTracker.trackEvent("CFVEnableFwd", "UnknownError", deviceID, -1);
   	   					return "enablefwdcallfail";
   	   				}
   				}
   				else // If we get back Nonsense as cleaned number
   				{
   					vCallFromVonTracker.trackEvent("CallFromVon", "CFVEnableFwdFailWithWrongNum", deviceID, -1);
   					vCallFromVonTracker.trackEvent("CallFromVon", "CFVCallingFailWithWrongNum", deviceID, -1);
   					return "enablefwdcallfail";
   				}
				
   				if(delayCall.isChecked())
   				{
   					publishProgress("Waiting for 5 seconds before calling");
   					try 
   					{
						Thread.sleep(6000);
					} 
   					catch (InterruptedException e)
   					{
						e.printStackTrace();
					}
   				}
   				
				publishProgress("Calling " + cleanedNumber + ", please wait...");

				Log.d(LOGTAG, "Calling Vonage number " + phoneNumber + ", with forwarding to " + cleanedNumber);
				Intent intent = new Intent(Intent.ACTION_CALL); // Intent to pick something
				intent.setData(Uri.parse("tel://" + phoneNumber)); // Type of data to pick, Contacts
				startActivityForResult(intent, CALL_CONTACT); // Start the activity to pick data, and return with PICK_CONTACT
				return "callnumsucc";
			}
   			else
   			{
   				return "WrongParam";
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
				Toast.makeText(getApplicationContext(), "Call Forwarding disabled", Toast.LENGTH_SHORT).show();
			}
			else if(result.equalsIgnoreCase("disablefwdfail"))
			{
				Toast.makeText(getApplicationContext(), "Call Forwarding disable failed", Toast.LENGTH_LONG).show();
			}
			else if(result.equalsIgnoreCase("callnumsucc"))
			{
				/*
				if(revertBack.isChecked())
    	    	{
    	    		Log.d(LOGTAG, "Starting to revert back CF to " + revertTo);
   					Toast.makeText(getApplicationContext(), "Setting Call Forwarding to " + revertTo + " in 10 seconds", Toast.LENGTH_LONG).show();
   					new VCallFromVonTask().execute("revertaftercall", "");
   				}
				else
				{
					Log.d(LOGTAG, "Starting to disable CF");
					Toast.makeText(getApplicationContext(), "Disabling call forward in 10 seconds", Toast.LENGTH_SHORT).show();
					new VCallFromVonTask().execute("disableforward", "disableaftercall");
				}
				*/
			}
			else if(result.equalsIgnoreCase("callnumfail"))
			{
				Toast.makeText(getApplicationContext(), "Calling Number Failed", Toast.LENGTH_LONG).show();
				callFromVonNum.setText(displayContact);
			}
			else if(result.equalsIgnoreCase("NotLoggedIn"))
			{
				Toast.makeText(getApplicationContext(), "Failed to login. Please try again.", Toast.LENGTH_LONG).show();
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
    	  	case (PICK_CONTACT_CALL) :
	  	    	Log.d(LOGTAG, "Picking contact and call returned with result code " + resultCode);
	  	    	if (resultCode == Activity.RESULT_OK)
					{
						Uri contactData = data.getData();
						Cursor c =  managedQuery(contactData, null, null, null, null);
						if (c.moveToFirst()) 
						{
							displayContact = c.getString(c.getColumnIndexOrThrow(People.NUMBER));
							// selectSetForwardCall(displayContact);
							callFromVonNum.setText(displayContact);
							Log.d(LOGTAG, "Back from contacts, we have a display number : " + displayContact);
						}
					}
	  	    	else
	  	    	{
	  	    		Log.d(LOGTAG, "Couldn't get back a number after picking contacts");
	  	    		// selectSetForwardCall("");
	  	    		callFromVonNum.setText("");
	  	    	}
	  	    	break;

    	  	case (CALL_CONTACT) :
    	    	Log.d(LOGTAG, "Calling Contact returned with result code " + resultCode);
    	    	if (resultCode == Activity.RESULT_CANCELED) // You return to call logs after the call, and you cancel that to return to the app
				{
					Log.d(LOGTAG, "Back from making a call");
					vCallFromVonTracker.trackEvent("CFVCallNumber", "Success", deviceID, -1);
					
					if(revertBack.isChecked())
	    	    	{
	    	    		Log.d(LOGTAG, "Starting to revert back CF to " + revertTo + " after call");
	   					Toast.makeText(getApplicationContext(), "Reverting Call Forwarding to " + revertTo, Toast.LENGTH_LONG).show();
	   					new VCallFromVonTask().execute("revertaftercall", "");
	   				}
					else
					{
						Log.d(LOGTAG, "Starting to disable CF after call");
						Toast.makeText(getApplicationContext(), "Disabling call forwarding", Toast.LENGTH_SHORT).show();
						new VCallFromVonTask().execute("disableforward", "disableaftercall");
					}
				}
    	    	else
    	    	{
    	    		Log.d(LOGTAG, "Something went wrong trying to make a call");
    	    		vCallFromVonTracker.trackEvent("CFVCallNumber", "UnknownError", deviceID, -1);
//    	    		selectSetForward("", false);
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
        vCallFromVonTracker.dispatch();
        vCallFromVonTracker.stop();
    }


	
}
