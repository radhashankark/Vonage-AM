package com.yaseriesapps.vonageam;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class VonageLogin extends Activity 
{
	private final String LOGTAG = "VonageLogin";
    Credentials credentials;
	View loginView, mainAreaView;
	TextView usernameTextView, passwordTextView, descriptionTextView;
	Button loginButton, cancelButton;
	ProgressDialog progressDialog;
	OnClickListener loginOnClickListener, cancelOnClickListener;
	boolean autoLoginFromUI = false; // Default value false; Choose the auto login creds from the General Settings screen
	boolean loginFromDB = false; // Default value false; Flags if the creds are from the DB
	boolean loginOther = false; // Default value false; Flag to keep track of user's intent to log into another account
	boolean saveAnotherUser = false; // Default value false; Flag to indicate whether we're just saving another ID
	boolean abortedLogin = false; // Default value false; Flag to indicate whether the login is aborted
	String username, password, phoneNumber, deviceID;
	private VHTTPComm vHTTPComm = null;
	private AsyncTask<String, String, String> localLoginTask;
	SharedPreferences currentLoginPrefs = null;
	GoogleAnalyticsTracker vonageLoginTracker;
	
    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        vonageLoginTracker = GoogleAnalyticsTracker.getInstance();
        currentLoginPrefs = getSharedPreferences("CURRENT_LOGIN", 0);
        credentials = new Credentials(getApplicationContext());
        vHTTPComm = VHTTPComm.getInstance(getApplicationContext()); // new VHTTPComm();
        
        // Start the tracker in manual dispatch mode...
        vonageLoginTracker.start("UA-20721251-7", this);
        
        // Init the Unique ID
        deviceID = Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID);

        //Start with this page tracking
        vonageLoginTracker.trackPageView("VonageLogin");
        
        // Set the RememberMe check box based on Intent
    	Intent incomingIntent = getIntent();
    	saveAnotherUser = incomingIntent.getBooleanExtra("SaveAnotherUser", false);
    	loginOther = incomingIntent.getBooleanExtra("LoginOther", false);
    	
        setupLoginView(); 

    }

    public void setupLoginView()
    {
    	Log.d(LOGTAG, "Setting up Login View");
    	setContentView(R.layout.loginview);
    	setTitle("Vonage AM - Please Login");
        initLoginUIComponents();
        initLoginListeners();
        associateLoginListeners();
        
        if(!credentials.autoLoginCredPresent())
        {
        	descriptionTextView.setText(R.string.description);
        }
        
    	if(loginOther) // If the user asked for logging into another account
    	{
    		Log.d(LOGTAG, "User asked to login into another ID");
    		
    		if (credentials.getSavedCredCount() > 0) // If there are stored creds, ask the user to choose..
            {
            	registerForContextMenu(mainAreaView);
            	usernameTextView.setOnClickListener(new OnClickListener() // Show user the saved creds to make login easy
        			{
    					@Override
    					public void onClick(View v)
    					{
    						mainAreaView.showContextMenu(); // Show context menu on click
    					}
    				});
            }
    	}
    	else if(saveAnotherUser)
    	{
    		Log.d(LOGTAG, "Save another user");
    	}
    	else if(credentials.autoLoginCredPresent()) // If autologin creds present, populate the login screen
        {
        	String creds[] = credentials.getAutoLoginCreds();
			username = creds[0];
			password = creds[1];
			phoneNumber = creds[2];
			loginFromDB = true;
        	
        	populateLoginView(creds); // Fill in the Login View
        	Log.d(LOGTAG, "Auto Logging in now, and disabling future auto logins.");
        	loginOther = true;
        	loginButton.performClick(); // Click the Login button
        }
    }
    
    public void initLoginUIComponents()
    {
    	mainAreaView = findViewById(R.id.mainarea);
    	usernameTextView = (TextView) findViewById(R.id.vonageusername);
    	passwordTextView = (TextView) findViewById(R.id.vonagepassword);
    	descriptionTextView = (TextView) findViewById(R.id.description);
    	loginButton = (Button) findViewById(R.id.vonagelogin);
    	cancelButton = (Button) findViewById(R.id.vonagecancel);
    	progressDialog = new ProgressDialog(this);
    }
    
    public void associateLoginListeners()
    {
    	loginButton.setOnClickListener(loginOnClickListener);
    	cancelButton.setOnClickListener(cancelOnClickListener);
    }

    public void populateLoginView(String[] creds)
    {
    	usernameTextView.setText(creds[0]);
    	passwordTextView.setText(creds[1]);
    }
    
    public void initLoginListeners()
    {
    	loginOnClickListener = new OnClickListener()
        {
    		@Override
    		public void onClick(View v)
    		{
    			localLoginTask = new LoginTask().execute("login"); 
    		}
    	};
    	
    	cancelOnClickListener = new OnClickListener()
    	{
    		@Override
    		public void onClick(View v) 
    		{
    			finish();
    		}
    	};
    }

	private class LoginTask extends AsyncTask<String, String, String>
	{
		@Override
		protected void onPreExecute()
		{
			progressDialog.setMessage("Initializing...");
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(progressDialogCancelListener);
			progressDialog.show();
		}
		
		@Override
		protected String doInBackground(String... params)
		{
			if (params[0].equalsIgnoreCase("login"))
			{
				// Start of the Login process. Log it.
				vonageLoginTracker.trackEvent("Login", "Start", deviceID, -1);
				
				username = usernameTextView.getText().toString();
				password = passwordTextView.getText().toString();
				loginFromDB = credentials.isUserSaved(username);
				

				publishProgress("SetMessage", "Logging in as " + username);

				String loggedInAs = "HTTPError";
				// Start the login process.
				if(saveAnotherUser)
				{
					// Do not write to prefs if just saving another user
					loggedInAs = vHTTPComm.login(username, password, false);
				}
				else
				{
					// Save prefs and continue as normal
					loggedInAs = vHTTPComm.login(username, password, true);
				}
					
				//Check if login is successful
				if(loggedInAs.equalsIgnoreCase("Success"))
				{
					// Get the Phone Number
					phoneNumber = vHTTPComm.getPhoneNumber();
					
					if (abortedLogin)
					{
						// If the login is aborted and the login method returns, the logout undoes it.
						vHTTPComm.logout();
					}
					else
					{
						Log.d(LOGTAG,"Vonage Login successful, phone number is " + phoneNumber);
						publishProgress("ShowToast", "Logged in. Your Number : " + phoneNumber);
						vonageLoginTracker.trackEvent("Login", "Success", deviceID, -1);
					}

					// Sanity check on the Phone number
					if(phoneNumber.equalsIgnoreCase("0000000000")) // Improper phone number received; That's weird
	            	{
	        	        Log.d(LOGTAG,"Improper number received : " + phoneNumber);
	            	}

					// Save the credentials on success
					publishProgress("SetMessage", "Saving your credentials...");
					if(credentials.isUserSaved(username))
					{
   						Log.d(LOGTAG, "User details for " + username + " already in DB. Skipping save");
					}
					else
					{
						// If the just used creds are not in the DB, save them.
		        		Log.d(LOGTAG, "Saving credentials");
		        		if(credentials.autoLoginCredPresent())
		        		{
		        			// Do not set autologin flag if autologin creds present
		        			credentials.saveCredentials(username, password, phoneNumber, false);
		        		}
		        		else
		        		{
		        			// If no autologin creds present, save as autologin creds
		        			credentials.saveCredentials(username, password, phoneNumber, true);	
		        			
		        			// Write the autologin thingy to Prefs
		        	    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		        	    	SharedPreferences.Editor prefsEditor = prefs.edit();

		        			// Save the user name of the Auto Login User
		        			prefsEditor.putString("autologinuser_pref", username);
		        			prefsEditor.commit();
		        		}
		        		
					}
					
					// Finally, log out of the temp user and log back in as actual user
					if(saveAnotherUser)
					{
						vHTTPComm.logout();
						String autoLoginCreds[] = credentials.getAutoLoginCreds();
						vHTTPComm.login(autoLoginCreds[0], autoLoginCreds[1], true);
					}
					
    				return "ShowOptionsView";
				}
				// Failure means Wrong user name or password.
				else if(loggedInAs.equalsIgnoreCase("Failure")) 
				{
					Log.d(LOGTAG,"Vonage Login failed, please check the username and password");
					publishProgress("ShowToast", "Login failed, please check the username and password");
					vonageLoginTracker.trackEvent("Login", "WrongCredentials", deviceID, -1);
					
					return "LoginFailure";
				}
				// HTTPError means Communication Error
				else if(loggedInAs.equalsIgnoreCase("HTTPError")) 
				{
					Log.d(LOGTAG,"Vonage Login failed, cannot communicate with Vonage Servers");
					publishProgress("ShowToast", "Login failed, cannot communicate with Vonage Servers");
					vonageLoginTracker.trackEvent("Login", "HTTPError", deviceID, -1);
					
					return "LoginFailure";
				}
				else // Unknown error
				{
					Log.d(LOGTAG,"Vonage Login failed, unknown error, logged in as " + loggedInAs);
					publishProgress("ShowToast", "Login failed, unknown error");
					vonageLoginTracker.trackEvent("Login", "UnknownError", deviceID, -1);
					
					return "LoginFailure";
				}
			}
			else
			{
				return "NothingToDo";	
			}
		}
		
		@Override
		protected void onProgressUpdate(String... values)
		{
			if("ShowToast".equalsIgnoreCase(values[0]))
			{
				Toast.makeText(getApplicationContext(), values[1], Toast.LENGTH_SHORT).show();
			}
			else if("SetMessage".equalsIgnoreCase(values[0]))
			{
				progressDialog.setMessage(values[1]);
			}
		}

		@Override
		protected void onPostExecute(String result)
		{
			if(result.equalsIgnoreCase("ShowOptionsView"))
			{
				progressDialog.dismiss();
				setupOptionsView();
				finish();
			}
			else if(result.equalsIgnoreCase("LoginFailure"))
			{
				progressDialog.dismiss();
				setupLoginView();
			}
	    }
	}
	
	private void setupOptionsView()
	{
		Intent optionsMenu = new Intent("com.yaseriesapps.vonageam.SHOW_OPTIONS");
		optionsMenu.addCategory("android.intent.category.DEFAULT");
		optionsMenu.putExtra("JustLoggedIn", true);
		startActivity(optionsMenu);
	}

	// OnCancelListener to abort everything and exit when the dialog is canceled
	OnCancelListener progressDialogCancelListener = new OnCancelListener() 
	{
		@Override
		public void onCancel(DialogInterface dialog)
		{
			// TODO Auto-generated method stub
			Log.d(LOGTAG, "Progress Dialog canceled, aborting login.");
			localLoginTask.cancel(true); // Cancel the current running AsyncTask
			abortedLogin = true;
			finish();
		}
	};
	
	@Override
    public void onDestroy()
    {
        super.onDestroy();
        credentials.cleanUp();
        credentials = null;
        
        // Dispatch the stats and stop the tracker when it is no longer needed.
        vonageLoginTracker.dispatch();
	    vonageLoginTracker.stop();
    }
    
    @Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) 
	{
    	if(!saveAnotherUser) // Populate the Context Menu only if not saving another user
    	{
        	if(v.getId() == R.id.mainarea) // Creating Context Menu for Main Area - Login Area
        	{
    	    	Log.d(LOGTAG, "Creating Context Menu for Login screen");
    	    	String users[] = credentials.getSavedUserNames();
//    	    	Log.d(LOGTAG, "Number of saved users : " + users.length);
    			MenuInflater menuInflater = getMenuInflater();
    			menuInflater.inflate(R.menu.user_list, menu);
    			menu.setHeaderTitle("Choose User");    	
    	    	for(int i = 0; i < users.length; i++)
    	    	{
    	    		menu.add(users[i]);
    	    	}
    			menu.add(R.string.switchaccounts);
        	}
    	}
	}
	
    public boolean onContextItemSelected(MenuItem item) 
    {
    	String selectedOptionTitle = item.getTitle().toString(); 
    	if(selectedOptionTitle.equalsIgnoreCase("Use another account")) // Use another account on Login screen
    	{
			Log.d(LOGTAG, "Another Login.");
			loginFromDB = false;
			loginOther = true;
			return true;
    	}
		else
		{
			// Suppress the soft keyboard first.
			InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(usernameTextView.getWindowToken(), 0);
			
			// Now get the creds and log in.
			Log.d(LOGTAG, "Getting creds for user : " + selectedOptionTitle);				
			String creds[] = credentials.getUserCreds(selectedOptionTitle);
			if(creds == null)
			{
				Log.d(LOGTAG, "Something went wrong, and we didn't get back the creds we were expecting. Returning...");
				return false;
			}
			username = creds[0];
			password = creds[1];
			phoneNumber = creds[2];
			loginFromDB = true;
			
//			Log.d(LOGTAG, "Populating View with " + username + ", " + password + ", " + phoneNumber);
			populateLoginView(creds);
			Log.d(LOGTAG, "onContextItemSelected() : Logging in as " + selectedOptionTitle);
			loginButton.performClick();
			return true;
		}
    }


    
}