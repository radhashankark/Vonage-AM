package com.yaseriesapps.vonageam;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class Credentials extends SQLiteOpenHelper 
{
	private final String LOGTAG = "VCredentials";
	private static final int DATABASE_VERSION = 2;
	private static final String DATABASE_NAME = "VonageCredentials";
    private static final String TABLE_CREATE = "CREATE TABLE VonageCreds (USER TEXT, PASSWORD TEXT, NUMBER TEXT, AUTOLOGIN TEXT);";
    
    String updateStatement = "UPDATE VonageCreds SET AUTOLOGIN = 'false'";
    String autoLoginQuery = "SELECT * FROM VonageCreds WHERE AUTOLOGIN = 'true'";
    
    SQLiteDatabase database = null;

    Credentials(Context context) 
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        database = getWritableDatabase();
    }

	@Override
	public void onCreate(SQLiteDatabase db) 
	{
		// TODO Auto-generated method stub
		db.execSQL(TABLE_CREATE);
		Log.d(LOGTAG, "Database created, Query : " + TABLE_CREATE);
	}

	@Override
	public void onOpen(SQLiteDatabase db) 
	{
		// TODO Auto-generated method stub
		Log.d(LOGTAG, "Database opened.");
	}
	
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
	{
		// TODO Auto-generated method stub
		Log.d(LOGTAG, "Database needs to be upgraded");
	}
	
	public void saveCredentials(String username, String password, String phoneNumber, boolean autoLogin)
	{
		Log.d(LOGTAG, "Saving credentials");
		if (database == null)
			database = getWritableDatabase();
		
		String insertStatement = "INSERT INTO VonageCreds VALUES ('" + username + "', '" + password + "', '" + phoneNumber + "', '";
		
		if(autoLogin)
		{
			Cursor c = database.rawQuery(autoLoginQuery, null);
			if(c.getCount() == 0) // No autologin currently exists
			{
				Log.d(LOGTAG, "No Autologin exists");
				database.execSQL(insertStatement+"true')"); // Autologin for current creds
				Log.d(LOGTAG, "Inserted creds");
			}
			else
			{
				database.execSQL(updateStatement); // Disable all current autologins
				Log.d(LOGTAG, "Autologin exists; Updated it");
				database.execSQL(insertStatement+"true')"); // Autologin for current creds
				Log.d(LOGTAG, "Inserted creds, query : " + insertStatement + "true')");
			}
			
			c.close();
		}
		else
		{
			database.execSQL(insertStatement+"false')");
			Log.d(LOGTAG, "Not an autologin cred. Inserted cred"); //, query : " + insertStatement + "false')");
		}
	}
	
	public String deleteCredentials(String username)
	{
		Log.d(LOGTAG, "Deleting credentials for user " + username);
		
		if (database == null)
			database = getWritableDatabase();
		
		database.execSQL("DELETE FROM VonageCreds WHERE USER = '" + username + "'");
		
		return "Success";
	}
	
	public String[] getAutoLoginCreds()
	{
		if (database == null)
			database = getWritableDatabase();
		
		Cursor c = database.rawQuery(autoLoginQuery, null);
		if(!c.moveToFirst())
		{
			Log.d(LOGTAG, "No Autologin creds found. Returning null");
			c.close();
			return null;			
		}
		else
		{
			String[] autoLogin = new String[4];
			autoLogin[0] = c.getString(0); // Username
			autoLogin[1] = c.getString(1); // Password
			autoLogin[2] = c.getString(2); // Phone Number
			autoLogin[3] = c.getString(3); // Autologin flag
			Log.d(LOGTAG, "Returning autologin cred : " + autoLogin[0] ); //+ ", " + autoLogin[1] + ", " + autoLogin[2] + ", " + autoLogin[3]);
			c.close();
			return autoLogin;
		}
	}

	public String[] getSavedUserNames()
	{
		String savedUsersQuery = "SELECT USER FROM VonageCreds";
		if (database == null)
			database = getWritableDatabase();
		
		Cursor c = database.rawQuery(savedUsersQuery, null);
		if(!c.moveToFirst())
		{
			Log.d(LOGTAG, "No saved creds found. Returning null");
			c.close();
			return null;			
		}
		else
		{
			int storedCreds = c.getCount();
			Log.d(LOGTAG, "Number of stored user creds : " + storedCreds);
			String[] userNames = new String[storedCreds];
			for(int rowcount = 0; rowcount < storedCreds; rowcount ++)
			{
				userNames[rowcount] = c.getString(0);
				Log.d(LOGTAG, "Returning saved cred " + rowcount + " : " + userNames[rowcount] );
				c.moveToNext();
			}
			
			c.close();
			return userNames;
		}
	}

	public String[] getUserCreds(String username)
	{
		String getUserCredsQuery = "SELECT * FROM VonageCreds WHERE USER = '" + username + "'";
		
		if (database == null)
			database = getWritableDatabase();
		
		Cursor c = database.rawQuery(getUserCredsQuery, null);
		if(!c.moveToFirst())
		{
			Log.d(LOGTAG, "No creds found for user " + username + ". Returning null");
			c.close();
			return null;			
		}
		else
		{
			String[] userCreds = new String[4];
			userCreds[0] = c.getString(0); // Username
			userCreds[1] = c.getString(1); // Password
			userCreds[2] = c.getString(2); // Phone Number
			userCreds[3] = c.getString(3); // Autologin flag
//			Log.d(LOGTAG, "Returning user creds : " + userCreds[0] + ", " + userCreds[1] + ", " + userCreds[2] + ", " + userCreds[3]);
			c.close();
			return userCreds;
		}
	}
	
	public boolean isUserSaved(String username)
	{
		String getUserCredsQuery = "SELECT * FROM VonageCreds WHERE USER = '" + username + "'";
		
		if (database == null)
			database = getWritableDatabase();
		
		Cursor c = database.rawQuery(getUserCredsQuery, null);
		if(c.moveToFirst()) // A record exists; return true
		{
			Log.d(LOGTAG, "User " + username + " saved in DB");
			c.close();
			return true;
		}
		else
		{
			Log.d(LOGTAG, "User " + username + " not found in DB");
			c.close();
			return false;
		}
	}
	
	public int getSavedCredCount()
	{
		if (database == null)
			database = getWritableDatabase();
		
		Cursor c = database.rawQuery("SELECT * FROM VonageCreds", null);
		Log.d(LOGTAG, "Saved Credential count : " + c.getCount());
		c.close();
		return c.getCount();
	}
	
	public boolean autoLoginCredPresent()
	{
		if (database == null)
			database = getWritableDatabase();

		Cursor c = database.rawQuery(autoLoginQuery, null);
		if(c.getCount() == 0)
		{
			c.close();
			return false;
		}
		else
		{
			c.close();
			return true;
		}
	}
	
	public void clearAllCredentials()
	{
		if (database == null)
			database = getWritableDatabase();
		Log.d(LOGTAG, "Clearning all saved Credential information.");
		database.execSQL("DELETE FROM VonageCreds;");
	}
	
	public void cleanUp()
	{
		if (database.isOpen())
			database.close();
		Log.d(LOGTAG, "Closed database. Over and out.");
		database = null;
	}
	
}
