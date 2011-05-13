package com.yaseriesapps.vonageam;

import java.util.ArrayList;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.ListView;
import android.widget.TextView;

public class VoicemailDetails extends ListActivity 
{

	private ProgressDialog mProgressDialog = null; 
    private ArrayList<VoicemailOption> mVoicemailOptions = null;
    private VoicemailOptionsAdapter mVoicemailOptionsAdapter;
    private Runnable viewOptions;
    private VHTTPComm vHTTPComm = null;
    private TextView statusTextView = null;
    private final String LOGTAG = "VonageOptions";
    
    ListView optionsList;
    
    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        vHTTPComm = VHTTPComm.getInstance(getApplicationContext()); // new VHTTPComm();
        
        setContentView(R.layout.voicemails);
        mVoicemailOptions = new ArrayList<VoicemailOption>();
        this.mVoicemailOptionsAdapter = new VoicemailOptionsAdapter(this, R.layout.voicemail, mVoicemailOptions);
        setListAdapter(this.mVoicemailOptionsAdapter);
        
        statusTextView = (TextView) findViewById(R.id.status);
        
        viewOptions = new Runnable()
        {
            @Override
            public void run()
            {
            	populateVoicemails();
            }
        };
        
//        initOptionsMenuListeners();
        
//        optionsList = getListView();
//        optionsList.setOnItemClickListener(optionsMenuItemClickListener);
        
        Thread thread =  new Thread(null, viewOptions, "MagentoBackground");
        thread.start();
        
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setMessage("Retrieving Voicemail Info. Please wait...");
        mProgressDialog.show();
        
        Log.d(LOGTAG, "Finishing up onCreate()");
    }
    
    private void populateVoicemails()
    {
        try
        {
        	mVoicemailOptions = new ArrayList<VoicemailOption>();
        	TextView vmStatusTextView = (TextView) findViewById(R.id.status);
        	String getVMStatus = null;
        	
        	// Get the number of Voicemail details to be fetched
        	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    		String voicemailNumbers = prefs.getString("voicemail_stats_pref", "NA::NA");
    		Log.d(LOGTAG, "Voicemail numbers : " + voicemailNumbers);
    		if("NA::NA".equalsIgnoreCase(voicemailNumbers))
    		{
    			vmStatusTextView.setText("No voicemails to fetch");
    		}
    		else
    		{
    			String[] vms = voicemailNumbers.split("::");
    			int totalVMs = Integer.parseInt(vms[1].trim());
    			getVMStatus = vHTTPComm.getVoiceMails(getBaseContext(), totalVMs, false); // Log in before fetching voicemail details
    		}

    		if(getVMStatus.equalsIgnoreCase("Success"))
    		{
    			// TODO Start reading the Prefs and populate the Voicemails
    			SharedPreferences vmPrefs = getSharedPreferences("VOICEMAIL_DETAILS", 0);
    			int voicemailCount = vmPrefs.getInt("totalvoicemails", 0);
    			
    			for(int i =1; i <= voicemailCount; i++)
    			{
        			VoicemailOption vm = new VoicemailOption();
        			// Get the From and set it
        			vm.setvoicemailFrom(vmPrefs.getString("vm-" + i + "-from", "1234567890"));
        			
        			// Get the Timestamp and set it
        			vm.setvoicemailWhen(vmPrefs.getString("vm-" + i + "-timestamp", "Jan 1, 1970 00:00 AM"));
        			
        			// Get the Voicemail duration and set it
        			vm.setvoicemailLength(vmPrefs.getString("vm-" + i + "-duration", "00:00"));
                	
        			// Get the read status of the duration and set it
        			vm.setisVMUnread(vmPrefs.getBoolean("vm-" + i + "-isUnread", true));
        			
        			// Get the Voicemail direct play link and set it
        			vm.setvoicemailDirectLink(vmPrefs.getString("vm-" + i + "-directLink", "https://secure.vonage.com"));
        			
        			// Add the Voicemail to the grand list
                	mVoicemailOptions.add(vm);
                	vm = null;
    			}
    		}
    		else if(getVMStatus.equalsIgnoreCase("HTTPError"))
    		{
    			Log.d(LOGTAG, "Cannot communicate with Vonage Servers for Voicemails");
    			statusTextView.setText("Cannot contact Vonage");
    		}
    		else if(getVMStatus.equalsIgnoreCase("NotLoggedIn"))
    		{
    			Log.d(LOGTAG, "Not Logged In for Voicemails");
    			statusTextView.setText("Cannot login into Vonage");
    		}
    		else if(getVMStatus.equalsIgnoreCase("NoVoiceMails"))
    		{
    			Log.d(LOGTAG, "No Voicemails for this account");
    			statusTextView.setText("No Voicemails for you");
    		}
    		else 
    		{
    			Log.d(LOGTAG, "Unknown error fetching voicemails");
    			statusTextView.setText("Could not get voicemails");
    		}
    		
            Log.i(LOGTAG, "Voicemails fetched : "+ mVoicemailOptions.size());
            
        } 
        catch (Exception e)
        { 
            Log.e(LOGTAG, e.getMessage());
        }
        runOnUiThread(returnRes);
    }
    
    private Runnable returnRes = new Runnable()
    {
        @Override
        public void run() 
        {
            if(mVoicemailOptions != null && mVoicemailOptions.size() > 0)
            {
            	mVoicemailOptionsAdapter.notifyDataSetChanged();
                for(int i=0; i < mVoicemailOptions.size(); i++)
                {
                	mVoicemailOptionsAdapter.add(mVoicemailOptions.get(i));
//                	mVoicemailOptionsAdapter.notifyDataSetChanged();
                }
            }
            mProgressDialog.dismiss();
            mVoicemailOptionsAdapter.notifyDataSetChanged();
        }
	};
}
