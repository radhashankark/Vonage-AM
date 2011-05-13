package com.yaseriesapps.vonageam;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpParams;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class VHTTPComm 
{
	private static final String LOGTAG = "HTTPInteraction";
	private DefaultHttpClient httpClient; // = null;
	private HttpResponse response = null;
	private HttpEntity entity = null;
	private static VHTTPComm vHTTPComm; // = null;
	private String phoneNumber = "0000000000";
	private String loggedInAs = "";
	private String username, password;
	private static Context appContext = null;
	protected boolean loggedIn = false;
	
	private VHTTPComm()
	{
		if(httpClient == null)
			httpClient = getHttpClient();
	}
	
	public static VHTTPComm getInstance(Context mContext)
	{
		if (vHTTPComm == null)
		{
			Log.d(LOGTAG, "vHTTPComm is null, creating new instance");
			vHTTPComm = new VHTTPComm();
		}

		if(appContext == null)
		{
			appContext = mContext;
		}
		
		return vHTTPComm;
	}
	
	private DefaultHttpClient getHttpClient()
	{
		Log.d(LOGTAG, "Preparing HttpClient in getHttpClient()");
		
		if(httpClient == null)
		{
			httpClient = new DefaultHttpClient();

			LinkedList<BasicHeader> headers = new LinkedList<BasicHeader>();
	
			headers.add(new BasicHeader("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US) Chrome/6.0.472.55 Safari/534.3"));
			headers.add(new BasicHeader("Accept", "application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8"));
			headers.add(new BasicHeader("Accept-Language","en-US,en;q=0.8"));
			headers.add(new BasicHeader("Accept-Encoding","gzip,deflate"));
			headers.add(new BasicHeader("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7"));
			
			HttpParams httpParams = httpClient.getParams();
			httpParams.setParameter("http.default-headers", headers);
			httpClient.setParams(httpParams);
			//Log.d(LOGTAG, "Added all headers to HttpClient, returning it now.");
		}
		return httpClient;
	}
	
	public String login(String username, String password, boolean writeToPrefs) // Returns logged in user name
	{
		// Copy the username and password to local variables for future use
		// This may need a check, and not do it all the time without reason, but this works.
		this.username = username;
		this.password = password;
//		Log.d(LOGTAG, "Logging in as " + this.username + " with pwd " + this.password);
		
		try
		{
			// Create the POST object to send the request
			HttpPost httpPost = new HttpPost("https://secure.vonage.com/vonage-web/public/login.htm");
//			Log.d(LOGTAG, "Created HttpPost object for Login");
			
			// Set up all the POST params
			ArrayList<NameValuePair> vonageloginparams = new ArrayList<NameValuePair>();
			
			vonageloginparams.add(new BasicNameValuePair("username", username));
			vonageloginparams.add(new BasicNameValuePair("password", password));
			vonageloginparams.add(new BasicNameValuePair("goToSelection", "callforwarding"));
			vonageloginparams.add(new BasicNameValuePair("redirectingURL", "/webaccount/dashboard/choose.htm"));
			//Log.d(LOGTAG, "Added all vonageloginparams to ArrayList");
			
			httpPost.setEntity(new UrlEncodedFormEntity(vonageloginparams,"UTF-8"));
			//Log.d(LOGTAG, "Set all params to httpPost");
			
			// Send the POST request to login
			response = httpClient.execute(httpPost);
			Log.d(LOGTAG, "Executed POST request for login()");
			if(response == null)
			{
				Log.d(LOGTAG, "Response object in getStatus is null");
				return "HTTPError";
			}
			else
			{
				Log.d(LOGTAG, "Response from Server : " + response.getStatusLine().toString());
			}
			
			// Get the response and check for a successful login
			entity = response.getEntity();
			
			BufferedReader input = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"),4096);

			String line = input.readLine();
			int lineNumber = 1;
			
			while (line != null)
			{
//				Log.d(LOGTAG, "Processing for verifyLogin, line #" + lineNumber + ", " + line);
				if(line.contains("User name: ")) // User Name comes before phone number
				{
					int start = line.indexOf("User name: ") + (new String("User name: ").length());
					int end = line.indexOf("</span></div>");
					loggedInAs = line.substring(start, end);
					Log.d(LOGTAG, "login() : User Name : " + loggedInAs);
					break;
				}

/*				
				else if(line.contains("url = url + 1")) // Last thing we want. So return.
				{
					int start = line.indexOf("url = url + ") + (new String("url = url + ").length());
					phoneNumber = line.substring(start, start+11);
					Log.d(LOGTAG, "login() : User Phone Number :  " + phoneNumber);
					break;
				}
*/
				line = input.readLine();
				lineNumber ++;
			}
			
			entity.consumeContent();
			//Log.d(LOGTAG, "Entity consumed.");
			
			// Check for a successful login
			// if loggedInAs is empty, it's a failed login
			// If it is username, we're in.
			// Shouldn't be the case, but anything else, we just got back a wrong user name from the site
			if(loggedInAs.equalsIgnoreCase(""))
			{
				loggedIn = false;
				Log.d(LOGTAG, "Wrong user name or password");
				return "Failure";
			}
			else if(loggedInAs.equalsIgnoreCase(username))
			{
				loggedIn = true;
				// Log.d(LOGTAG, "Logged in as " + loggedInAs);
				if(writeToPrefs)
				{
					getUserName();
					Log.d(LOGTAG, "It's the first login. Writing UserName " + loggedInAs + " and Phone Number " + phoneNumber + " to Prefs");
					// Write the username, phone number and current timestamp to Prefs
					SharedPreferences currentLoginPrefs = appContext.getSharedPreferences("CURRENT_LOGIN", 0);
				    SharedPreferences.Editor prefsEditor = currentLoginPrefs.edit();
				    prefsEditor.putBoolean("LoggedIn", true);
				    prefsEditor.putString("userName", loggedInAs);
				    prefsEditor.putString("password", password);
				    prefsEditor.putString("phoneNumber", phoneNumber);
				    prefsEditor.putLong("LastLogin", new Date().getTime());
				    prefsEditor.commit();
				}			    
				return "Success";
			}
			else
			{
				loggedIn = false;
				Log.d(LOGTAG, "User name doesn't match; Returning WrongUserName");
				return "Failure";
			}
		}
		catch (UnknownHostException uhe)
		{
			Log.d(LOGTAG, "UnknownHostException in login() : " + uhe.getMessage() + "; Stack Trace follows. ");
			uhe.printStackTrace();
			return "HTTPError";
		}
		catch (Exception e) 
		{
			Log.d(LOGTAG, "Exception in login() : " + e.getMessage() + "; Stack Trace follows. ");
			e.printStackTrace();
			return "Exception";
		} 
	}

	private String getUserName() // Use as isLoggedIn() and verifyLogin(). Returns logged in username and sets phoneNumber
	{
		try
		{
			HttpGet httpGet = new HttpGet("https://secure.vonage.com/webaccount/dashboard/index.htm");
			//Log.d(LOGTAG, "Created HttpGet object for getUserName()");
			
			response = httpClient.execute(httpGet);
//			Log.d(LOGTAG, "Executed GET request for getUserName()");
			if(response == null)
			{
				Log.d(LOGTAG, "Response object in getUserName() is null");
				return "HTTPError";
			}
			else
			{
				Log.d(LOGTAG, "Response from Server : " + response.getStatusLine().toString());
			}
			
			entity = response.getEntity();
			//Log.d(LOGTAG, "Got Entity, reading response");
			
			String user = "";
			BufferedReader input = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"),4096);

			String line = input.readLine();
			int lineNumber = 1;
			
			while (line != null)
			{
//				Log.d(LOGTAG, "Processing for verifyLogin, line #" + lineNumber + ", " + line);
				if(line.contains("User name: ")) // User Name comes before phone number
				{
					int start = line.indexOf("User name: ") + (new String("User name: ").length());
					int end = line.indexOf("</span></div>");
					user = line.substring(start, end);
					Log.d(LOGTAG, "getUserName() : Logged in as " + user);
				}
				
				if(line.contains("url = url + 1")) // Last thing we want. So return.
				{
					int start = line.indexOf("url = url + ") + (new String("url = url + ").length());
					phoneNumber = line.substring(start, start+11);
					Log.d(LOGTAG, "getUserName() : User Phone Number :  " + phoneNumber);
				
					entity.consumeContent();
					//Log.d(LOGTAG, "Entity consumed. End of getUserName()");
					
					loggedIn = true;
					return user;
				}
				line = input.readLine();
				lineNumber ++;
			}
			
			entity.consumeContent();
			//Log.d(LOGTAG, "Entity consumed. End of getUserName()");
			
			Log.d(LOGTAG, "getUserName() : Login failed. Setting number to 0s and loggedIn to false");
			loggedIn = false;
			phoneNumber = "0000000000";
			return "NotLoggedIn";
		}
		catch (UnknownHostException uhe)
		{
			Log.d(LOGTAG, "UnknownHostException in verifyLogin() : " + uhe.getMessage() + "; Stack Trace follows. ");
			uhe.printStackTrace();
			return "HTTPError";
		}
		catch (Exception e) 
		{
			Log.d(LOGTAG, "Exception in verifyLogin() : " + e.getMessage() + "; Stack Trace follows. ");
			e.printStackTrace();
			phoneNumber = "0000000000";
			return "Failure";
		}
	}

	public String getPhoneNumber() // Take the already set phoneNumber and return it
	{
		return phoneNumber;
	}
	
	public String logout()
	{
//		httpClient = getHttpClient();
		try 
		{
			HttpPost httpPost = new HttpPost("https://secure.vonage.com/webaccount/public/logoff.htm");
			//Log.d(LOGTAG, "Created HttpPost object for Logout");
			
//			HttpResponse response;
			response = httpClient.execute(httpPost);
//			Log.d(LOGTAG, "Executed POST request for logout()");
			if(response == null)
			{
				Log.d(LOGTAG, "Response object in logout() is null");
				return "HTTPError";
			}
			else
			{
				Log.d(LOGTAG, "Logging out. Response from Server : " + response.getStatusLine().toString());
			}
			
			entity = response.getEntity();
//			Log.d(LOGTAG, "Got Entity, reading response");
			
			int statusCode = response.getStatusLine().getStatusCode();
//			Log.d(LOGTAG, "Response code from Server : " + statusCode);
			
			entity.consumeContent();
			
			if(statusCode == 200)
			{
				loggedIn = false;
				
				// Write the username, phone number and current timestamp to Prefs
				SharedPreferences currentLoginPrefs = appContext.getSharedPreferences("CURRENT_LOGIN", 0);
			    SharedPreferences.Editor prefsEditor = currentLoginPrefs.edit();
			    prefsEditor.putBoolean("LoggedIn", false);
			    prefsEditor.putString("userName", "LoggedOut");
			    prefsEditor.putString("password", "LoggedOut");
			    prefsEditor.putString("phoneNumber", "0000000000");
			    // prefsEditor.putLong("LastLogin", new Date().getTime());
			    prefsEditor.commit();
			    
			    // Get the Shared Preferences for the Application and clear them out
		    	// PreferenceManager.getDefaultSharedPreferences(appContext).edit().clear().commit();
			    
				return "Success";
			}
			else 
			{
				loggedIn = true;
				return "Failure";
			}
		} 
		catch (UnknownHostException uhe)
		{
			Log.d(LOGTAG, "UnknownHostException in logout() : " + uhe.getMessage() + "; Stack Trace follows. ");
			uhe.printStackTrace();
			return "HTTPError";
		}
		catch (Exception e) 
		{
			Log.d(LOGTAG, "Exception in logout() : " + e.getMessage() + "; Stack Trace follows. ");
			e.printStackTrace();
			return "Failure";
		} 
	}

	public String setForwardTo(String forwardNumber, boolean dontLogin)
	{
		String logIn = "Success"; // Assume login is a success for first log in
		
		if(!dontLogin) // Log in if it is not the first log in.
		{
			logIn = login(username, password, false); // Not the first login. Dont write to Prefs
		}
		
		if(logIn.equalsIgnoreCase("HTTPError"))
		{
			Log.d(LOGTAG, "Not logged in before calling setForwardTo()");
			return "HTTPError";
		}
		else if(logIn.equalsIgnoreCase("Failure"))
		{
			Log.d(LOGTAG, "Not logged in before calling setForwardTo()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Success"))
		{
			try
			{
				HttpPost httpPost = new HttpPost("https://secure.vonage.com/webaccount/features/callforwarding/edit.htm");
				//Log.d(LOGTAG, "Created HttpPost object for setForwardTo()");
		
				ArrayList<NameValuePair> setCFParams = new ArrayList<NameValuePair>();
				
				setCFParams.add(new BasicNameValuePair("did", phoneNumber));
				setCFParams.add(new BasicNameValuePair("action", "save"));
				setCFParams.add(new BasicNameValuePair("enableCallForwarding", "true"));
				setCFParams.add(new BasicNameValuePair("simulRingEnabled", "false"));
				setCFParams.add(new BasicNameValuePair("currTab", "CF"));
				setCFParams.add(new BasicNameValuePair("currTab", "CF"));
				setCFParams.add(new BasicNameValuePair("callForwardingSeconds", "1"));
				setCFParams.add(new BasicNameValuePair("singleAddress", forwardNumber));
				setCFParams.add(new BasicNameValuePair("address[0]", forwardNumber));
				setCFParams.add(new BasicNameValuePair("address[1]", ""));
				setCFParams.add(new BasicNameValuePair("address[2]", ""));
				setCFParams.add(new BasicNameValuePair("address[3]", ""));
				setCFParams.add(new BasicNameValuePair("address[4]", ""));
				//Log.d(LOGTAG, "Added all setCFParams to ArrayList");
				
				httpPost.setEntity(new UrlEncodedFormEntity(setCFParams,"UTF-8"));
				//Log.d(LOGTAG, "Set all params to httpPost for setForwardTo()");
				
				response = httpClient.execute(httpPost);
				Log.d(LOGTAG, "Executed POST request for setForwardTo()");
				if(response == null)
				{
					Log.d(LOGTAG, "Response object in setForwardTo() is null");
					return "HTTPError";
				}
				else
				{
					Log.d(LOGTAG, "Response from Server : " + response.getStatusLine().toString());
				}
				
				entity = response.getEntity();
				//Log.d(LOGTAG, "Got Entity, checking if CF settings are saved");
				
				BufferedReader input = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"),4096);

				String line = input.readLine();
				int lineNumber = 1;
				
				while (line != null)
				{
//					Log.d(LOGTAG, "Processing for FwdUpdStatus, line #" + lineNumber + ", " + line);
					if(line.contains("Your settings have been saved"))
					{
						Log.d(LOGTAG, "Call Forward settings saved");
						entity.consumeContent();
						return "Success";
					}
					line = input.readLine();
					lineNumber ++;
				}
				
				entity.consumeContent();
				//Log.d(LOGTAG, "Entity consumed. End of setForwardTo()");
				
				Log.d(LOGTAG, "Settings not saved in setForwardTo()");
				return "NotSaved";
			}
			catch (UnknownHostException uhe)
			{
				Log.d(LOGTAG, "UnknownHostException in setForwardTo() : " + uhe.getMessage() + "; Stack Trace follows. ");
				uhe.printStackTrace();
				return "HTTPError";
			}
			catch (Exception e) 
			{
				Log.d(LOGTAG, "Exception in setForwardTo() : " + e.getMessage() + "; Stack Trace follows. ");
				e.printStackTrace();
				return "Failure";
			} 
		}
		else
		{
			// Discardable state, where we dont know what happened
			Log.d(LOGTAG, "We have no idea what just happened in setForwardTo()");
			return "UnknownException";
		}
	}
	
	public String disableForward(boolean dontLogin)
	{
		String logIn = "Success"; // Assume login is a success for first log in
		
		if(!dontLogin) // Log in if it is not the first log in.
		{
			logIn = login(username, password, false); // Not the first login. Dont write to Prefs
		}
		
		if(logIn.equalsIgnoreCase("HTTPError"))
		{
			Log.d(LOGTAG, "Not logged in before calling disableForward()");
			return "HTTPError";
		}
		else if(logIn.equalsIgnoreCase("Failure"))
		{
			Log.d(LOGTAG, "Not logged in before calling disableForward()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Success"))
		{
			try
			{
				HttpPost httpPost = new HttpPost("https://secure.vonage.com/webaccount/features/callforwarding/edit.htm");
				//Log.d(LOGTAG, "Created HttpPost object for disableForward()");
		
				ArrayList<NameValuePair> disableCFParams = new ArrayList<NameValuePair>();
				
				disableCFParams.add(new BasicNameValuePair("phoneNumber", phoneNumber));
				disableCFParams.add(new BasicNameValuePair("did", phoneNumber));
				disableCFParams.add(new BasicNameValuePair("enableCallForwarding", "false"));
				//Log.d(LOGTAG, "Added all disableCFParams to ArrayList");
				
				httpPost.setEntity(new UrlEncodedFormEntity(disableCFParams,"UTF-8"));
				//Log.d(LOGTAG, "Set all params to httpPost for disableForward()");
				
				response = httpClient.execute(httpPost);
				Log.d(LOGTAG, "Executed POST request for disableForward()");
				if(response == null)
				{
					Log.d(LOGTAG, "Response object in getStatus is null");
					return "HTTPError";
				}
				else
				{
					Log.d(LOGTAG, "Response from Server : " + response.getStatusLine().toString());
				}
				
				entity = response.getEntity();
				//Log.d(LOGTAG, "Got Entity, checking if CF is disabled");
				
				BufferedReader input = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"),4096);

				String line = input.readLine();
				int lineNumber = 1;
				
				while (line != null)
				{
//					Log.d(LOGTAG, "Processing for FwdUpdStatus, line #" + lineNumber + ", " + line);
					if(line.contains("Your settings have been saved"))
					{
						Log.d(LOGTAG, "Call Forward settings saved");
						entity.consumeContent();
						return "Success";
					}
					line = input.readLine();
					lineNumber ++;
				}
				
				entity.consumeContent();
				Log.d(LOGTAG, "Entity consumed. End of disableForward()");
				
				Log.d(LOGTAG, "disableForward() failed");
				return "NotSaved";
			}
			catch (UnknownHostException uhe)
			{
				Log.d(LOGTAG, "UnknownHostException in disableForward() : " + uhe.getMessage() + "; Stack Trace follows. ");
				uhe.printStackTrace();
				return "HTTPError";
			}
			catch (Exception e) 
			{
				Log.d(LOGTAG, "Exception in disableForward() : " + e.getMessage() + "; Stack Trace follows. ");
				e.printStackTrace();
				return "Failure";
			} 
		}
		else
		{
			// Discardable state, where we dont know what happened
			Log.d(LOGTAG, "We have no idea what just happened in disableForward()");
			return "UnknownException";
		}
	}
	
	public String enableNaN(String forwardNumber, boolean dontLogin)
	{
		String logIn = "Success"; // Assume login is a success for first log in
		
		if(!dontLogin) // Log in if it is not the first log in.
		{
			logIn = login(username, password, false); // Not the first login. Dont write to Prefs
		}

		if(logIn.equalsIgnoreCase("HTTPError"))
		{
			Log.d(LOGTAG, "Not logged in before calling enableNaN()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Failure"))
		{
			Log.d(LOGTAG, "Not logged in before calling enableNaN()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Success"))
		{
			try
			{
				HttpPost httpPost = new HttpPost("https://secure.vonage.com/webaccount/features/nan/edit.htm");
				//Log.d(LOGTAG, "Created HttpPost object for enableNaN()");
		
				ArrayList<NameValuePair> enableNaNParams = new ArrayList<NameValuePair>();

				enableNaNParams.add(new BasicNameValuePair("forwardNumber", forwardNumber));
				enableNaNParams.add(new BasicNameValuePair("did", phoneNumber));
				enableNaNParams.add(new BasicNameValuePair("action", "save"));
				//Log.d(LOGTAG, "Added all enableNaNParams to ArrayList");
				
				httpPost.setEntity(new UrlEncodedFormEntity(enableNaNParams,"UTF-8"));
				//Log.d(LOGTAG, "Set all params to httpPost for enableNaN()");
				
				response = httpClient.execute(httpPost);
				Log.d(LOGTAG, "Executed POST request for enableNaN()");
				if(response == null)
				{
					Log.d(LOGTAG, "Response object in getStatus is null");
					return "ResponseNull";
				}
				else
				{
					Log.d(LOGTAG, "Response from Server : " + response.getStatusLine().toString());
				}
				
				entity = response.getEntity();
				//Log.d(LOGTAG, "Got Entity, checking if NaN is enabled");
				
				BufferedReader input = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"),4096);

				String line = input.readLine();
				int lineNumber = 1;
				
				while (line != null)
				{
//					Log.d(LOGTAG, "Processing for FwdUpdStatus, line #" + lineNumber + ", " + line);
					if(line.contains("Your changes will take effect within minutes."))
					{
						Log.d(LOGTAG, "NaN set to " + forwardNumber);
						entity.consumeContent();
						return "Success";
					}
					line = input.readLine();
					lineNumber ++;
				}
				
				entity.consumeContent();
				Log.d(LOGTAG, "Entity consumed. End of enableNaN()");
				
				Log.d(LOGTAG, "enableNaN() failed");
				return "NotSaved";
			}
			catch (UnknownHostException uhe)
			{
				Log.d(LOGTAG, "UnknownHostException in enableNaN() : " + uhe.getMessage() + "; Stack Trace follows. ");
				uhe.printStackTrace();
				return "HTTPError";
			}
			catch (Exception e) 
			{
				Log.d(LOGTAG, "Exception in enableNaN() : " + e.getMessage() + "; Stack Trace follows. ");
				e.printStackTrace();
				return "Failure";
			} 
		}
		else
		{
			// Discardable state, where we dont know what happened
			Log.d(LOGTAG, "We have no idea what just happened in enableNaN()");
			return "UnknownException";
		}
	}
	
	public String disableNaN(boolean dontLogin)
	{
		String logIn = "Success"; // Assume login is a success for first log in
		
		if(!dontLogin) // Log in if it is not the first log in.
		{
			logIn = login(username, password, false); // Not the first login. Dont write to Prefs
		}

		if(logIn.equalsIgnoreCase("HTTPError"))
		{
			Log.d(LOGTAG, "Not logged in before calling disableNaN()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Failure"))
		{
			Log.d(LOGTAG, "Not logged in before calling disableNaN()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Success"))
		{
			try
			{
				HttpPost httpPost = new HttpPost("https://secure.vonage.com/webaccount/features/nan/edit.htm");
				//Log.d(LOGTAG, "Created HttpPost object for disableNaN()");
		
				ArrayList<NameValuePair> disableNaNParams = new ArrayList<NameValuePair>();
				
				disableNaNParams.add(new BasicNameValuePair("forwardNumber", ""));
				disableNaNParams.add(new BasicNameValuePair("did", phoneNumber));
				disableNaNParams.add(new BasicNameValuePair("action", "false"));
				//Log.d(LOGTAG, "Added all disableNaNParams to ArrayList");
				
				httpPost.setEntity(new UrlEncodedFormEntity(disableNaNParams,"UTF-8"));
				//Log.d(LOGTAG, "Set all params to httpPost for disableNaN()");
				
				response = httpClient.execute(httpPost);
				Log.d(LOGTAG, "Executed POST request for disableNaN()");
				if(response == null)
				{
					Log.d(LOGTAG, "Response object in getStatus is null");
					return "ResponseNull";
				}
				else
				{
					Log.d(LOGTAG, "Response from Server : " + response.getStatusLine().toString());
				}
				
				entity = response.getEntity();
				//Log.d(LOGTAG, "Got Entity, checking if NaN is disabled");
				
				BufferedReader input = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"),4096);

				String line = input.readLine();
				int lineNumber = 1;
				
				while (line != null)
				{
//					Log.d(LOGTAG, "Processing for FwdUpdStatus, line #" + lineNumber + ", " + line);
					if(line.contains("Your changes will take effect within minutes."))
					{
						Log.d(LOGTAG, "NaN set to disabled");
						entity.consumeContent();
						return "Success";
					}
					line = input.readLine();
					lineNumber ++;
				}
				
				entity.consumeContent();
				Log.d(LOGTAG, "Entity consumed. End of disableNaN()");
				
				Log.d(LOGTAG, "disableNaN() failed");
				return "NotSaved";
			}
			catch (UnknownHostException uhe)
			{
				Log.d(LOGTAG, "UnknownHostException in disableNaN() : " + uhe.getMessage() + "; Stack Trace follows. ");
				uhe.printStackTrace();
				return "HTTPError";
			}
			catch (Exception e) 
			{
				Log.d(LOGTAG, "Exception in disableNaN() : " + e.getMessage() + "; Stack Trace follows. ");
				e.printStackTrace();
				return "Failure";
			} 
		}
		else
		{
			// Discardable state, where we dont know what happened
			Log.d(LOGTAG, "We have no idea what just happened in disableNaN()");
			return "UnknownException";
		}
	}

	public String intlcalling(boolean enable, boolean dontLogin)
	{
		String logIn = "Success"; // Assume login is a success for first log in
		
		if(!dontLogin) // Log in if it is not the first log in.
		{
			logIn = login(username, password, false); // Not the first login. Dont write to Prefs
		}

		if(logIn.equalsIgnoreCase("HTTPError"))
		{
			Log.d(LOGTAG, "Not logged in before calling intlcalling()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Failure"))
		{
			Log.d(LOGTAG, "Not logged in before calling intlcalling()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Success"))
		{
			try
			{
				HttpPost httpPost = new HttpPost("https://secure.vonage.com/webaccount/features/internationalcalling/edit.htm");
				//Log.d(LOGTAG, "Created HttpPost object for intlcalling()");
		
				ArrayList<NameValuePair> intlcallingParams = new ArrayList<NameValuePair>();
				
				intlcallingParams.add(new BasicNameValuePair("did", phoneNumber));
				
				if(enable) // Set the parameters for enabling or disabling intl calling
					intlcallingParams.add(new BasicNameValuePair("enabled", "true"));
				else
					intlcallingParams.add(new BasicNameValuePair("enabled", "false"));
				
				//Log.d(LOGTAG, "Added all intlcallingParams to ArrayList");
				
				httpPost.setEntity(new UrlEncodedFormEntity(intlcallingParams,"UTF-8"));
				//Log.d(LOGTAG, "Set all params to httpPost for intlcalling()");
				
				response = httpClient.execute(httpPost);
				Log.d(LOGTAG, "Executed POST request for intlcalling()");
				if(response == null)
				{
					Log.d(LOGTAG, "Response object in getStatus is null");
					return "Failure";
				}
				else
				{
					Log.d(LOGTAG, "Response from Server : " + response.getStatusLine().toString());
				}
				
				entity = response.getEntity();
				//Log.d(LOGTAG, "Got Entity, checking if intl calling settings are saved");
				
				BufferedReader input = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"),4096);

				String line = input.readLine();
				int lineNumber = 1;
				
				while (line != null)
				{
//					Log.d(LOGTAG, "Processing for FwdUpdStatus, line #" + lineNumber + ", " + line);
					if(line.contains("Your settings will take effect within a few minutes"))
					{
						Log.d(LOGTAG, "International calling settings saved");
						entity.consumeContent();
						return "Success";
					}
					
					if(line.contains("The following error(s) occurred"))
					{
						Log.d(LOGTAG, "International calling settings not saved");
						entity.consumeContent();
						return "Failure";
					}
					line = input.readLine();
					lineNumber ++;
				}
				
				entity.consumeContent();
				Log.d(LOGTAG, "Entity consumed. End of intlcalling()");
				
				Log.d(LOGTAG, "intlcalling() failed");
				return "Failure";
			}
			catch (UnknownHostException uhe)
			{
				Log.d(LOGTAG, "UnknownHostException in intlcalling() : " + uhe.getMessage() + "; Stack Trace follows. ");
				uhe.printStackTrace();
				return "HTTPError";
			}
			catch (Exception e) 
			{
				Log.d(LOGTAG, "Exception in intlcalling() : " + e.getMessage() + "; Stack Trace follows. ");
				e.printStackTrace();
				return "Failure";
			} 
		}
		else
		{
			// Discardable state, where we dont know what happened
			Log.d(LOGTAG, "We have no idea what just happened in intlcalling()");
			return "UnknownException";
		}
	}

	public String setCallerID(boolean enable, boolean dontLogin)
	{
		String logIn = "Success"; // Assume login is a success for first log in
		
		if(!dontLogin) // Log in if it is not the first log in.
		{
			logIn = login(username, password, false); // Not the first login. Dont write to Prefs
		}

		if(logIn.equalsIgnoreCase("HTTPError"))
		{
			Log.d(LOGTAG, "Not logged in before calling setCallerID()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Failure"))
		{
			Log.d(LOGTAG, "Not logged in before calling setCallerID()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Success"))
		{
			try
			{
				HttpPost httpPost = new HttpPost("https://secure.vonage.com/webaccount/features/callername/edit.htm");
				//Log.d(LOGTAG, "Created HttpPost object for setCallerID()");
		
				ArrayList<NameValuePair> callerIDParams = new ArrayList<NameValuePair>();
				
				callerIDParams.add(new BasicNameValuePair("phoneNumber", phoneNumber));
				
				if(enable) // Set the parameters for enabling or disabling intl calling
					callerIDParams.add(new BasicNameValuePair("callerIdFlagOn", "true"));
				else
					callerIDParams.add(new BasicNameValuePair("callerIdFlagOn", "false"));
				
				//Log.d(LOGTAG, "Added all callerIDParams to ArrayList");
				
				httpPost.setEntity(new UrlEncodedFormEntity(callerIDParams,"UTF-8"));
				//Log.d(LOGTAG, "Set all params to httpPost for setCallerID()");
				
				response = httpClient.execute(httpPost);
				Log.d(LOGTAG, "Executed POST request for setCallerID()");
				if(response == null)
				{
					Log.d(LOGTAG, "Response object in getStatus is null");
					return "Failure";
				}
				else
				{
					Log.d(LOGTAG, "Response from Server : " + response.getStatusLine().toString());
				}
				
				entity = response.getEntity();
				//Log.d(LOGTAG, "Got Entity, checking if callerID settings are saved");
				
				BufferedReader input = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"),4096);

				String line = input.readLine();
				int lineNumber = 1;
				
				while (line != null)
				{
//					Log.d(LOGTAG, "Processing for setCallerID, line #" + lineNumber + ", " + line);
					if(line.contains("Your settings have been saved"))
					{
						Log.d(LOGTAG, "Caller ID settings saved");
						entity.consumeContent();
						return "Success";
					}
					
					if(line.contains("The following error(s) occurred"))
					{
						Log.d(LOGTAG, "Caller ID settings not saved");
						entity.consumeContent();
						return "Failure";
					}
					line = input.readLine();
					lineNumber ++;
				}
				
				entity.consumeContent();
				Log.d(LOGTAG, "Entity consumed. End of setCallerID()");
				
				Log.d(LOGTAG, "setCallerID() failed");
				return "Failure";
			}
			catch (UnknownHostException uhe)
			{
				Log.d(LOGTAG, "UnknownHostException in setCallerID() : " + uhe.getMessage() + "; Stack Trace follows. ");
				uhe.printStackTrace();
				return "HTTPError";
			}
			catch (Exception e) 
			{
				Log.d(LOGTAG, "Exception in setCallerID() : " + e.getMessage() + "; Stack Trace follows. ");
				e.printStackTrace();
				return "Failure";
			} 
		}
		else
		{
			// Discardable state, where we dont know what happened
			Log.d(LOGTAG, "We have no idea what just happened in setCallerID()");
			return "UnknownException";
		}
	}

	public String setDND(boolean enable, boolean dontLogin)
	{
		String logIn = "Success"; // Assume login is a success for first log in
		
		if(!dontLogin) // Log in if it is not the first log in.
		{
			logIn = login(username, password, false); // Not the first login. Dont write to Prefs
		}

		if(logIn.equalsIgnoreCase("HTTPError"))
		{
			Log.d(LOGTAG, "Not logged in before calling setDND()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Failure"))
		{
			Log.d(LOGTAG, "Not logged in before calling setDND()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Success"))
		{
			try
			{
				HttpPost httpPost = new HttpPost("https://secure.vonage.com/webaccount/features/DoNotDisturb/edit.htm");
				//Log.d(LOGTAG, "Created HttpPost object for setDND()");
		
				ArrayList<NameValuePair> callerIDParams = new ArrayList<NameValuePair>();
				
				callerIDParams.add(new BasicNameValuePair("phoneNumber", phoneNumber));
				
				if(enable) // Set the parameters for enabling or disabling intl calling
					callerIDParams.add(new BasicNameValuePair("on", "true"));
				else
					callerIDParams.add(new BasicNameValuePair("on", "false"));
				
				//Log.d(LOGTAG, "Added all callerIDParams to ArrayList");
				
				httpPost.setEntity(new UrlEncodedFormEntity(callerIDParams,"UTF-8"));
				//Log.d(LOGTAG, "Set all params to httpPost for setDND()");
				
				response = httpClient.execute(httpPost);
				Log.d(LOGTAG, "Executed POST request for setDND()");
				if(response == null)
				{
					Log.d(LOGTAG, "Response object in getStatus is null");
					return "Failure";
				}
				else
				{
					Log.d(LOGTAG, "Response from Server : " + response.getStatusLine().toString());
				}
				
				entity = response.getEntity();
				//Log.d(LOGTAG, "Got Entity, checking if callerID settings are saved");
				
				BufferedReader input = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"),4096);

				String line = input.readLine();
				int lineNumber = 1;
				
				while (line != null)
				{
//					Log.d(LOGTAG, "Processing for setCallerID, line #" + lineNumber + ", " + line);
					if(line.contains("Your settings have been saved"))
					{
						Log.d(LOGTAG, "DND settings saved");
						entity.consumeContent();
						return "Success";
					}
					
					if(line.contains("The following error(s) occurred"))
					{
						Log.d(LOGTAG, "DND settings not saved");
						entity.consumeContent();
						return "NotSaved";
					}
					line = input.readLine();
					lineNumber ++;
				}
				
				entity.consumeContent();
				Log.d(LOGTAG, "Entity consumed. End of setDND()");
				
				Log.d(LOGTAG, "setCallerID() failed");
				return "Failure";
			}
			catch (UnknownHostException uhe)
			{
				Log.d(LOGTAG, "UnknownHostException in setDND() : " + uhe.getMessage() + "; Stack Trace follows. ");
				uhe.printStackTrace();
				return "HTTPError";
			}
			catch (Exception e) 
			{
				Log.d(LOGTAG, "Exception in setDND() : " + e.getMessage() + "; Stack Trace follows. ");
				e.printStackTrace();
				return "Failure";
			} 
		}
		else
		{
			// Discardable state, where we dont know what happened
			Log.d(LOGTAG, "We have no idea what just happened in setDND()");
			return "UnknownException";
		}
	}
	
	public String editCallQuality(String callQuality, boolean dontLogin)
	{
		// callQuality permitted values are NORMAL, HIGH, and HIGHEST
		String logIn = "Success"; // Assume login is a success for first log in
		
		if(!dontLogin) // Log in if it is not the first log in.
		{
			logIn = login(username, password, false); // Not the first login. Dont write to Prefs
		}

		if(logIn.equalsIgnoreCase("HTTPError"))
		{
			Log.d(LOGTAG, "Not logged in before calling editCallQuality()");
			return "HTTPError";
		}
		else if(logIn.equalsIgnoreCase("Failure"))
		{
			Log.d(LOGTAG, "Not logged in before calling editCallQuality()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Success"))
		{
			try
			{
				String deviceID = "";
				String pageUrl = "https://secure.vonage.com/webaccount/features/bandwidthsaver/edit.htm?did="+phoneNumber+"&bandwidthSaverButton=Configure";

				HttpGet httpGet = new HttpGet(pageUrl);
				//Log.d(LOGTAG, "Created HttpGet object for editCallQuality(), for Device ID");
				
				response = httpClient.execute(httpGet);
				Log.d(LOGTAG, "Executed GET request for editCallQuality(), for Device ID");
				if(response == null)
				{
					Log.d(LOGTAG, "Response object in getStatus is null");
					return "HTTPError";
				}
				else
				{
					Log.d(LOGTAG, "Response from Server : " + response.getStatusLine().toString());
				}
				
				entity = response.getEntity();
				//Log.d(LOGTAG, "Got Entity, starting to search for DeviceID");
				
				BufferedReader input = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"),4096);

				String line = input.readLine();
				int lineNumber = 1;
				
				while (line != null)
				{
//					Log.d(LOGTAG, "Processing line #" + lineNumber + ", " + line);
					if(line.contains("name=\"deviceId\" value=\""))
					{
						int start = line.indexOf("name=\"deviceId\" value=\"") + (new String("name=\"deviceId\" value=\"").length());
						int end = line.indexOf("\" >");
						deviceID = line.substring(start, end);
						Log.d(LOGTAG, "Found Device ID, " + deviceID);
						break;
					}
					line = input.readLine();
					lineNumber ++;
				}
//				Log.d(LOGTAG, "Found Device ID : " + deviceID);

				entity.consumeContent(); // Release the data so far. We'll get a fresh set anyway
				
				ArrayList<NameValuePair> editCallQualityParams = new ArrayList<NameValuePair>();

				editCallQualityParams.add(new BasicNameValuePair("deviceId", deviceID));
				editCallQualityParams.add(new BasicNameValuePair("did", phoneNumber));
				editCallQualityParams.add(new BasicNameValuePair("voiceQualityLevel", callQuality));
				//Log.d(LOGTAG, "Added all editCallQualityParams to ArrayList");

				HttpPost httpPost = new HttpPost(pageUrl);
				//Log.d(LOGTAG, "Created HttpPost object for editCallQuality()");
				
				httpPost.setEntity(new UrlEncodedFormEntity(editCallQualityParams,"UTF-8"));
				//Log.d(LOGTAG, "Set all params to httpPost for editCallQuality()");
				
				response = httpClient.execute(httpPost);
				Log.d(LOGTAG, "Executed POST request for editCallQuality()");
				if(response == null)
				{
					Log.d(LOGTAG, "Response object in getStatus is null");
					return "HTTPError";
				}
				else
				{
					Log.d(LOGTAG, "Response from Server : " + response.getStatusLine().toString());
				}
				
				entity = response.getEntity();
				//Your new settings should take effect within the hour
				//Log.d(LOGTAG, "Got Entity, checking if BW settings are updated");
				
				input = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"),4096);

				line = input.readLine();
				lineNumber = 1;
				
				while (line != null)
				{
//					Log.d(LOGTAG, "Processing for FwdUpdStatus, line #" + lineNumber + ", " + line);
					if(line.contains("Your new settings should take effect within the hour"))
					{
						Log.d(LOGTAG, "New BW setting updated");
						entity.consumeContent();
						return "Success";
					}
					line = input.readLine();
					lineNumber ++;
				}
				
				entity.consumeContent();
				Log.d(LOGTAG, "Entity consumed. End of editCallQuality()");
				
				Log.d(LOGTAG, "editCallQuality() failed");
				return "Failure";
			}
			catch (UnknownHostException uhe)
			{
				Log.d(LOGTAG, "UnknownHostException in editCallQuality() : " + uhe.getMessage() + "; Stack Trace follows. ");
				uhe.printStackTrace();
				return "HTTPError";
			}
			catch (Exception e) 
			{
				Log.d(LOGTAG, "Exception in editCallQuality() : " + e.getMessage() + "; Stack Trace follows. ");
				e.printStackTrace();
				return "Failure";
			} 
		}
		else
		{
			// Discardable state, where we dont know what happened
			Log.d(LOGTAG, "We have no idea what just happened in editCallQuality()");
			return "UnknownException";
		}
	}

	public String getVoiceMails (Context context, int totalVMs, boolean dontLogin)
	{
		// Initialize the Prefs Editor
		SharedPreferences vmprefs = context.getSharedPreferences("VOICEMAIL_DETAILS", 0);
		SharedPreferences.Editor vmPrefsEditor = vmprefs.edit();
		
		String logIn = "Success"; // Assume login is a success for first log in
		
		if(!dontLogin) // Log in if it is not the first log in.
		{
			logIn = login(username, password, false); // Not the first login. Dont write to Prefs
		}

		if(logIn.equalsIgnoreCase("HTTPError"))
		{
			Log.d(LOGTAG, "Not logged in before calling getVoiceMails()");
			return "HTTPError";
		}
		else if(logIn.equalsIgnoreCase("Failure"))
		{
			Log.d(LOGTAG, "Not logged in before calling getVoiceMails()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Success"))
		{
			try
			{
//				String deviceID = "";
				String pageUrl = "https://secure.vonage.com/webaccount/features/voicemail/messages/view.htm"; // ?did="+phoneNumber+"&bandwidthSaverButton=Configure";

				HttpGet httpGet = new HttpGet(pageUrl);
				//Log.d(LOGTAG, "Created HttpGet object for editCallQuality(), for Device ID");
				
				response = httpClient.execute(httpGet);
				Log.d(LOGTAG, "Executed GET request for getVoiceMails(), for VoiceMails");
				if(response == null)
				{
					Log.d(LOGTAG, "Response object in getVoiceMails is null");
					return "HTTPError";
				}
				else
				{
					Log.d(LOGTAG, "Response from Server : " + response.getStatusLine().toString());
				}
				
				entity = response.getEntity();
				//Log.d(LOGTAG, "Got Entity, starting to search for DeviceID");
				
				// TODO This is where you go get the voicemail details. Start at "sortableColumn "
				BufferedReader input = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"),4096);

				String line = input.readLine();
				int lineNumber = 1;
				int voicemailsCollected = 1;
				
				while (line != null)
				{
//					Log.d(LOGTAG, "Processing line #" + lineNumber + ", " + line);
					// We probably will have to process through the entire page on this one 
					// because we dont really know how the section ends
					String from = null;
					String timestamp = null;
					String duration = null;
					String directLink = null;
					
					if(line.contains("sortableColumn ")) // That's the start of our first section
					{
						// Leave the line with "sortableColumn "; No useful info there
						line = input.readLine();
						lineNumber ++;
						
						// Whether new or old
						if(line.length() > 20) // Means the Voicemail is unread.
						{
							Log.d(LOGTAG, "Voicemail " + voicemailsCollected + " is unread ? " + true);
							vmPrefsEditor.putBoolean("vm-" + voicemailsCollected + "-isUnread", true);
							vmPrefsEditor.commit();
						}
						else
						{
							Log.d(LOGTAG, "Voicemail " + voicemailsCollected + " is unread ? " + false);							
						}
						line = input.readLine();
						lineNumber ++;
						
						// Now collect the From info
						int start = line.indexOf("<td>") + 4; // 4 is the length of <td>.
						int end = line.indexOf("</td>");
						from = line.substring(start, end);
						vmPrefsEditor.putString("vm-" + voicemailsCollected + "-from", from);
						vmPrefsEditor.commit();
						line = input.readLine();
						lineNumber ++;
						Log.d(LOGTAG, "Voicemail " + voicemailsCollected + " from " + from);
						
						// Now for the Timestamp
						start = line.indexOf("<td>") + 4; // 4 is the length of <td>.
						end = line.indexOf("</td>");
						timestamp = line.substring(start, end);
						vmPrefsEditor.putString("vm-" + voicemailsCollected + "-timestamp", timestamp);
						vmPrefsEditor.commit();
						line = input.readLine();
						lineNumber ++;
						Log.d(LOGTAG, "Voicemail " + (voicemailsCollected + 1) + " timestamp " + timestamp);

						// Now for the duration
						start = line.indexOf("<td>") + 4; // 4 is the length of <td>.
						end = line.indexOf("</td>");
						duration = line.substring(start, end);
						vmPrefsEditor.putString("vm-" + voicemailsCollected + "-duration", duration);
						vmPrefsEditor.commit();
						line = input.readLine();
						lineNumber ++;
						Log.d(LOGTAG, "Voicemail " + (voicemailsCollected + 1) + " duration " + duration);

						// Now for the directLink
						start = line.indexOf("<a href=\"") + 9; // 9 is the length of unwanted chars
						end = line.indexOf("\">Listen");
						directLink = line.substring(start, end);
						directLink = "https://secure.vonage.com" + directLink;
						vmPrefsEditor.putString("vm-" + voicemailsCollected + "-directLink", directLink);
						vmPrefsEditor.commit();
//						line = input.readLine();
//						lineNumber ++;
						Log.d(LOGTAG, "Voicemail " + (voicemailsCollected + 1) + " directLink " + directLink);
						
						voicemailsCollected++;
						// break;
					}
					line = input.readLine();
					lineNumber ++;
					
					if((voicemailsCollected > 1) && line.contains("</table>"))
					{
						Log.d(LOGTAG, "Voicemails collected : " + (voicemailsCollected - 1));
						vmPrefsEditor.putInt("totalvoicemails", (voicemailsCollected - 1));
						vmPrefsEditor.commit();
						entity.consumeContent();
						return "Success";
					}
				}
//				Log.d(LOGTAG, "Found Device ID : " + deviceID);

				entity.consumeContent(); // Release the data so far. We'll get a fresh set anyway
				
				if ((voicemailsCollected - 1) == 0)
				{
					Log.d(LOGTAG, "editCallQuality() failed");
					return "NoVoiceMails";
				}
				else
				{
					return "Failure";
				}
			}
			catch (UnknownHostException uhe)
			{
				Log.d(LOGTAG, "UnknownHostException in editCallQuality() : " + uhe.getMessage() + "; Stack Trace follows. ");
				uhe.printStackTrace();
				return "HTTPError";
			}
			catch (Exception e) 
			{
				Log.d(LOGTAG, "Exception in editCallQuality() : " + e.getMessage() + "; Stack Trace follows. ");
				e.printStackTrace();
				return "Failure";
			} 
		}
		else
		{
			// Discardable state, where we dont know what happened
			Log.d(LOGTAG, "We have no idea what just happened in editCallQuality()");
			return "UnknownException";
		}
	}
	
	public String getNaNSetting(boolean dontLogin)
	{
		String logIn = "Success"; // Assume log in is a success for first log in
		
		if(!dontLogin) // Log in if it is not the first log in.
		{
			logIn = login(username, password, false); // Not the first login. Dont write to Prefs
		}
		
		if(logIn.equalsIgnoreCase("HTTPError"))
		{
			Log.d(LOGTAG, "Not logged in before calling getNaNSetting()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Failure"))
		{
			Log.d(LOGTAG, "Not logged in before calling getNaNSetting()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Success"))
		{
			try
			{
				HttpGet httpGet = new HttpGet("https://secure.vonage.com/webaccount/features/nan/edit.htm?did=" + phoneNumber);
				//Log.d(LOGTAG, "Created HttpGet object for getNaNSetting()");
				
				response = httpClient.execute(httpGet);
				Log.d(LOGTAG, "Executed GET request for getNaNSetting()");
				if(response == null)
				{
					Log.d(LOGTAG, "Response object in getStatus is null");
					return "Failure";
				}
				else
				{
					Log.d(LOGTAG, "Response from Server : " + response.getStatusLine().toString());
				}
				
				entity = response.getEntity();
				Log.d(LOGTAG, "Got Entity in getNaNSetting()");
				
				BufferedReader input = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"),4096);

				String line = input.readLine();
				int lineNumber = 1;
				
				while (line != null)
				{
//					Log.d(LOGTAG, "Processing for NaN status, line #" + lineNumber + ", " + line);
					if(line.contains("name=\"forwardNumber\""))
					{
						int offset = line.indexOf("name=\"forwardNumber\" type=\"text\" value=\"") + (new String("name=\"forwardNumber\" type=\"text\" value=\"").length());
						int end = line.indexOf("\" maxlength=");
						String temp = line.substring(offset, end);
						if(temp.equalsIgnoreCase(""))
						{
							// intlCallingEnabled = true;
							Log.d(LOGTAG, "NaN disabled");
							entity.consumeContent();
							return "Disabled";
						}
						else
						{
							Log.d(LOGTAG, "NaN set to : " + temp);
							entity.consumeContent();
							return temp;
						}
//						Log.d(LOGTAG, "Extracted string : " + temp);
//						break;
					}
					line = input.readLine();
					lineNumber ++;
				}
				
				entity.consumeContent();
				Log.d(LOGTAG, "Entity consumed. End of getNaNSetting()");
				
				Log.d(LOGTAG, "NaN Setting : disabled");
				return "Disabled";
			}
			catch (UnknownHostException uhe)
			{
				Log.d(LOGTAG, "UnknownHostException in getNaNSetting() : " + uhe.getMessage() + "; Stack Trace follows. ");
				uhe.printStackTrace();
				return "HTTPError";
			}
			catch (Exception e) 
			{
				Log.d(LOGTAG, "Exception in getNaNSetting() : " + e.getMessage() + "; Stack Trace follows. ");
				e.printStackTrace();
				return "Failure";
			} 
		}
		else
		{
			// Discardable state, where we dont know what happened
			Log.d(LOGTAG, "We have no idea what just happened in getNaNSetting()");
			return "UnknownException";
		}
	}
	
	public String getCurrentCFSetting(boolean dontLogin)
	{
		String logIn = "Success"; // Assume log in is a success for first log in
		
		if(!dontLogin) // Log in if it is not the first log in.
		{
			logIn = login(username, password, false); // Not the first login. Dont write to Prefs
		}

		if(logIn.equalsIgnoreCase("HTTPError"))
		{
			Log.d(LOGTAG, "Not logged in before calling getCurrentCFSetting()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Failure"))
		{
			Log.d(LOGTAG, "Not logged in before calling getCurrentCFSetting()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Success"))
		{
			try
			{
				HttpGet httpGet = new HttpGet("https://secure.vonage.com/webaccount/features/callforwarding/edit.htm?did=" + phoneNumber);
				//Log.d(LOGTAG, "Created HttpPost object for getCurrentCFSetting()");
				
				response = httpClient.execute(httpGet);
				Log.d(LOGTAG, "Executed POST request for getCurrentCFSetting()");
				if(response == null)
				{
					Log.d(LOGTAG, "Response object in getStatus is null");
					return "Failure";
				}
				else
				{
					Log.d(LOGTAG, "Response from Server : " + response.getStatusLine().toString());
				}
				
				entity = response.getEntity();
				Log.d(LOGTAG, "Got Entity in getCurrentCFSetting()");
				
				BufferedReader input = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"),4096);

				String line = input.readLine();
				int lineNumber = 1;
				
				while (line != null)
				{
//					Log.d(LOGTAG, "Processing for CF status, line #" + lineNumber + ", " + line);
					if(line.contains("Call forwarding is disabled"))
					{
						Log.d(LOGTAG, "Call Forwarding currently disabled");
						return "Disabled";
					}
					
					if(line.contains("Before Forwarding to"))
					{
						int offset = line.indexOf(" value=\"") + (new String(" value=\"").length());
						int end = line.indexOf("\">");
						String temp = line.substring(offset, end);
						
						Log.d(LOGTAG, "Call Forwarding currently set to : " + temp);
						
						entity.consumeContent();
						Log.d(LOGTAG, "Entity consumed. End of getCurrentCFSetting()");
						
						return temp;
					}
					line = input.readLine();
					lineNumber ++;
				}
				
				entity.consumeContent();
				Log.d(LOGTAG, "Entity consumed. End of getCurrentCFSetting()");
				
				Log.d(LOGTAG, "Current Call Forwarding Setting : Disabled");
				return "Disabled";
			}
			catch (UnknownHostException uhe)
			{
				Log.d(LOGTAG, "UnknownHostException in getCurrentCFSetting() : " + uhe.getMessage() + "; Stack Trace follows. ");
				uhe.printStackTrace();
				return "HTTPError";
			}
			catch (Exception e) 
			{
				Log.d(LOGTAG, "Exception in getCurrentCFSetting() : " + e.getMessage() + "; Stack Trace follows. ");
				e.printStackTrace();
				return "Failure";
			} 
		}
		else
		{
			// Discardable state, where we dont know what happened
			Log.d(LOGTAG, "We have no idea what just happened in getCurrentCFSetting()");
			return "UnknownException";
		}
	}
	
	public boolean getIntlCallingEnabled(boolean dontLogin)
	{
		String logIn = "Success"; // Assume log in is a success for first log in
		
		if(!dontLogin) // Log in if it is not the first log in.
		{
			logIn = login(username, password, false); // Not the first login. Dont write to Prefs
		}

		if(logIn.equalsIgnoreCase("HTTPError"))
		{
			Log.d(LOGTAG, "Not logged in before calling getIntlCallingEnabled()");
			return false;
		}
		else if(logIn.equalsIgnoreCase("Failure"))
		{
			Log.d(LOGTAG, "Not logged in before calling getIntlCallingEnabled()");
			return false;
		}
		else if(logIn.equalsIgnoreCase("Success"))
		{
			try
			{
				HttpGet httpGet = new HttpGet("https://secure.vonage.com/webaccount/features/internationalcalling/edit.htm?did=" + phoneNumber);
				//Log.d(LOGTAG, "Created HttpGet object for getIntlCallingEnabled()");
				
				response = httpClient.execute(httpGet);
				Log.d(LOGTAG, "Executed GET request for getIntlCallingEnabled()");
				if(response == null)
				{
					Log.d(LOGTAG, "Response object in getStatus is null");
					return false;
				}
				else
				{
					Log.d(LOGTAG, "Response from Server : " + response.getStatusLine().toString());
				}
				
				entity = response.getEntity();
				Log.d(LOGTAG, "Got Entity in getIntlCallingEnabled()");
				
				BufferedReader input = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"),4096);

				String line = input.readLine();
				int lineNumber = 1;
				
				while (line != null)
				{
//					Log.d(LOGTAG, "Processing for intl calling status, line #" + lineNumber + ", " + line);
					if(line.contains("checked"))
					{
						int offset = line.indexOf("\" checked"); 
						String temp = line.substring(offset-4, offset);						
						Log.d(LOGTAG, "Extracted string : " + temp);
						if(temp.equalsIgnoreCase("true"))
						{
							// intlCallingEnabled = true;
							Log.d(LOGTAG, "International Calling enabled : " + true);
							entity.consumeContent();
							return true;
						}
						else
						{
							// intlCallingEnabled = false;
							Log.d(LOGTAG, "International Calling enabled : " + false);
							entity.consumeContent();
							return false;
						}
					}
					line = input.readLine();
					lineNumber ++;
				}
				
				entity.consumeContent();
				Log.d(LOGTAG, "Entity consumed. End of getIntlCallingEnabled()");
				
				Log.d(LOGTAG, "International Calling enabled : " + false);
				return false;
			}
			catch (UnknownHostException uhe)
			{
				Log.d(LOGTAG, "UnknownHostException in getIntlCallingEnabled() : " + uhe.getMessage() + "; Stack Trace follows. ");
				uhe.printStackTrace();
				return false;
			}
			catch (Exception e) 
			{
				Log.d(LOGTAG, "Exception in getIntlCallingEnabled() : " + e.getMessage() + "; Stack Trace follows. ");
				e.printStackTrace();
				return false;
			} 
		}
		else
		{
			// Discardable state, where we dont know what happened
			Log.d(LOGTAG, "We have no idea what just happened in getIntlCallingEnabled()");
			return false;
		}
	}
	
	public boolean getCallerIDSetting(boolean dontLogin)
	{
		String logIn = "Success"; // Assume log in is a success for first log in
		
		if(!dontLogin) // Log in if it is not the first log in.
		{
			logIn = login(username, password, false); // Not the first login. Dont write to Prefs
		}

		if(logIn.equalsIgnoreCase("HTTPError"))
		{
			Log.d(LOGTAG, "Not logged in before calling getCallerIDSetting()");
			return false;
		}
		else if(logIn.equalsIgnoreCase("Failure"))
		{
			Log.d(LOGTAG, "Not logged in before calling getCallerIDSetting()");
			return false;
		}
		else if(logIn.equalsIgnoreCase("Success"))
		{
			try
			{
				HttpGet httpGet = new HttpGet("https://secure.vonage.com/webaccount/features/callername/edit.htm?did=" + phoneNumber);
				//Log.d(LOGTAG, "Created HttpPost object for getCallerIDSetting()");
				
				response = httpClient.execute(httpGet);
				Log.d(LOGTAG, "Executed POST request for getCallerIDSetting()");
				if(response == null)
				{
					Log.d(LOGTAG, "Response object in getStatus is null");
					return false;
				}
				else
				{
					Log.d(LOGTAG, "Response from Server : " + response.getStatusLine().toString());
				}
				
				entity = response.getEntity();
				Log.d(LOGTAG, "Got Entity in getCallerIDSetting()");
				
				BufferedReader input = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"),4096);

				String line = input.readLine();
				int lineNumber = 1;
				
				while (line != null)
				{
//					Log.d(LOGTAG, "Processing for caller ID status, line #" + lineNumber + ", " + line);
					if(line.contains("checked"))
					{
						int offset = line.indexOf("\" checked"); 
						String temp = line.substring(offset-4, offset);		
						Log.d(LOGTAG, "Extracted string : " + temp);
						if(temp.equalsIgnoreCase("true"))
						{
							// intlCallingEnabled = true;
							Log.d(LOGTAG, "Caller ID enabled : " + true);
							entity.consumeContent();
							return true;
						}
						
						break;
					}
					line = input.readLine();
					lineNumber ++;
				}
				
				entity.consumeContent();
				Log.d(LOGTAG, "Entity consumed. End of getCallerIDSetting()");
				
				Log.d(LOGTAG, "Caller ID enabled : " + false);
				return false;
			}
			catch (UnknownHostException uhe)
			{
				Log.d(LOGTAG, "UnknownHostException in getCallerIDSetting() : " + uhe.getMessage() + "; Stack Trace follows. ");
				uhe.printStackTrace();
				return false;
			}
			catch (Exception e) 
			{
				Log.d(LOGTAG, "Exception in getCallerIDSetting() : " + e.getMessage() + "; Stack Trace follows. ");
				e.printStackTrace();
				return false;
			} 
		}
		else
		{
			// Discardable state, where we dont know what happened
			Log.d(LOGTAG, "We have no idea what just happened in getCallerIDSetting()");
			return false;
		}
	}
	
	public boolean getDNDSetting(boolean dontLogin)
	{
		String logIn = "Success"; // Assume log in is a success for first log in
		
		if(!dontLogin) // Log in if it is not the first log in.
		{
			logIn = login(username, password, false); // Not the first login. Dont write to Prefs
		}

		if(logIn.equalsIgnoreCase("HTTPError"))
		{
			Log.d(LOGTAG, "Not logged in before calling getDNDSetting()");
			return false;
		}
		else if(logIn.equalsIgnoreCase("Failure"))
		{
			Log.d(LOGTAG, "Not logged in before calling getDNDSetting()");
			return false;
		}
		else if(logIn.equalsIgnoreCase("Success"))
		{
			try
			{
				HttpGet httpGet = new HttpGet("https://secure.vonage.com/webaccount/features/DoNotDisturb/edit.htm?did=" + phoneNumber);
				//Log.d(LOGTAG, "Created HttpPost object for getDNDSetting()");
				
				response = httpClient.execute(httpGet);
				Log.d(LOGTAG, "Executed POST request for getDNDSetting()");
				if(response == null)
				{
					Log.d(LOGTAG, "Response object in getStatus is null");
					return false;
				}
				else
				{
					Log.d(LOGTAG, "Response from Server : " + response.getStatusLine().toString());
				}
				
				entity = response.getEntity();
				Log.d(LOGTAG, "Got Entity in getDNDSetting()");
				
				BufferedReader input = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"),4096);

				String line = input.readLine();
				int lineNumber = 1;
				
				while (line != null)
				{
//					Log.d(LOGTAG, "Processing for DND status, line #" + lineNumber + ", " + line);
					if(line.contains("checked"))
					{
						int offset = line.indexOf("\" checked"); 
						String temp = line.substring(offset-4, offset);		
						Log.d(LOGTAG, "Extracted string : " + temp);
						if(temp.equalsIgnoreCase("true"))
						{
							// intlCallingEnabled = true;
							Log.d(LOGTAG, "DND enabled : " + true);
							entity.consumeContent();
							return true;
						}

						break;
					}
					line = input.readLine();
					lineNumber ++;
				}
				
				entity.consumeContent();
				Log.d(LOGTAG, "Entity consumed. End of getDNDSetting()");
				
				Log.d(LOGTAG, "DND enabled : " + false);
				return false;
			}
			catch (UnknownHostException uhe)
			{
				Log.d(LOGTAG, "UnknownHostException in getDNDSetting() : " + uhe.getMessage() + "; Stack Trace follows. ");
				uhe.printStackTrace();
				return false;
			}
			catch (Exception e) 
			{
				Log.d(LOGTAG, "Exception in getDNDSetting() : " + e.getMessage() + "; Stack Trace follows. ");
				e.printStackTrace();
				return false;
			} 
		}
		else
		{
			// Discardable state, where we dont know what happened
			Log.d(LOGTAG, "We have no idea what just happened in getCallerIDSetting()");
			return false;
		}
	}
	
	public String getVoiceMailStats(boolean dontLogin)
	{
		String logIn = "Success"; // Assume log in is a success for first log in
		
		if(!dontLogin) // Log in if it is not the first log in.
		{
			logIn = login(username, password, false); // Not the first login. Dont write to Prefs
		}

		if(logIn.equalsIgnoreCase("HTTPError"))
		{
			Log.d(LOGTAG, "Not logged in before calling getVoiceMailStats()");
			return "HTTPError";
		}
		else if(logIn.equalsIgnoreCase("Failure"))
		{
			Log.d(LOGTAG, "Not logged in before calling getVoiceMailStats()");
			return "NotLoggedIn";
		}
		else if(logIn.equalsIgnoreCase("Success"))
		{
			try
			{
				HttpGet httpGet = new HttpGet("https://secure.vonage.com/webaccount/features/voicemail/messages/view.htm?did=" + phoneNumber);
				//Log.d(LOGTAG, "Created HttpPost object for getVoiceMailStats()");
				
				response = httpClient.execute(httpGet);
				Log.d(LOGTAG, "Executed POST request for getVoiceMailStats()");
				if(response == null)
				{
					Log.d(LOGTAG, "Response object in getVoiceMailStats is null");
					return "Failure";
				}
				else
				{
					Log.d(LOGTAG, "Response from Server : " + response.getStatusLine().toString());
				}
				
				entity = response.getEntity();
				Log.d(LOGTAG, "Got Entity in getVoiceMailStats()");
				
				BufferedReader input = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8"),4096);

				String line = input.readLine();
				int lineNumber = 1;
				
				while (line != null)
				{
//					Log.d(LOGTAG, "Processing for CF status, line #" + lineNumber + ", " + line);
					if(line.contains("Summary:"))
					{
						Log.d(LOGTAG, "Call Forwarding currently disabled");
						
						// Extract the New VMs
						int offset = line.indexOf("Summary:") + (new String("Summary:").length());
						int end = line.indexOf(" New");
						String newVMs = line.substring(offset, end);
						
						// Extract the Total VMs
						offset = line.indexOf(" (") + (new String(" (").length());
						end = line.indexOf("Total)");
						String totalVMs = line.substring(offset, end);

						entity.consumeContent();
						Log.d(LOGTAG, "Voicemails : " + newVMs + "::" + totalVMs);
						
						return newVMs + "::" + totalVMs;
					}
					
					line = input.readLine();
					lineNumber ++;
				}
				
				entity.consumeContent();
				Log.d(LOGTAG, "Entity consumed. End of getVoiceMailStats()");
				
				Log.d(LOGTAG, "Voicemail details not found");
				return "NA::NA";
			}
			catch (UnknownHostException uhe)
			{
				Log.d(LOGTAG, "UnknownHostException in getVoiceMailStats() : " + uhe.getMessage() + "; Stack Trace follows. ");
				uhe.printStackTrace();
				return "HTTPError";
			}
			catch (Exception e) 
			{
				Log.d(LOGTAG, "Exception in getVoiceMailStats() : " + e.getMessage() + "; Stack Trace follows. ");
				e.printStackTrace();
				return "Failure";
			} 
		}
		else
		{
			// Discardable state, where we dont know what happened
			Log.d(LOGTAG, "We have no idea what just happened in getVoiceMailStats()");
			return "UnknownException";
		}
	}
	
}
