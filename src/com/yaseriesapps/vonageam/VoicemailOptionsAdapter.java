package com.yaseriesapps.vonageam;

import java.io.File;
import java.util.ArrayList;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class VoicemailOptionsAdapter extends ArrayAdapter<VoicemailOption>
{
    private ArrayList<VoicemailOption> items;
    Context ctx;

    public VoicemailOptionsAdapter(Context context, int textViewResourceId, ArrayList<VoicemailOption> items) 
    {
	    super(context, textViewResourceId, items);
	    ctx = context;
	    this.items = items;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) 
    {
        View v = convertView;
        
        if (v == null) 
        {
//        	Log.d("VonageOptions", "v is null in VoicemailOptionsAdapter; Inflating a voicemail row");
            LayoutInflater vi = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = vi.inflate(R.layout.voicemail, null);
        }
        
        VoicemailOption option = items.get(position);
        if (option != null) 
        {
            TextView voicemailFrom = (TextView) v.findViewById(R.id.voicemailfrom);
            TextView voicemailWhen = (TextView) v.findViewById(R.id.voicemailwhen);
            TextView voicemailLength = (TextView) v.findViewById(R.id.voicemaillength);
            ImageView voicemailPlay = (ImageView) v.findViewById(R.id.voicemailplay);
            
            // Set the Voicemail From field
            if (voicemailFrom != null) 
            {
            	voicemailFrom.setText(option.getvoicemailFrom());                            
            }
            
            // Set the Voicemail Timestamp
            if(voicemailWhen != null)
            {
            	voicemailWhen.setText(option.getvoicemailWhen());
            }
            
            // Set the Voicemail Length
            if(voicemailLength != null)
            {
            	voicemailLength.setText(option.getvoicemailLength());
            }
            
            // Set the play button to the Direct Link
            if(voicemailPlay != null)
            {
				final String directLink = option.getvoicemailDirectLink();
            	voicemailPlay.setOnClickListener(
            			new OnClickListener() 
            			{
							@Override
							public void onClick(View v) 
							{
								// TODO Get the direct link, and pass it to the Music Player
								Intent intent = new Intent();  
								intent.setAction(android.content.Intent.ACTION_VIEW);  
//								File file = new File(directLink);  
								intent.setDataAndType(Uri.parse(directLink), "audio/*");  
								ctx.startActivity(intent);
							}
            			}
            		);
            }
            
            if(option.getisVMUnread())
            {
            	v.setBackgroundColor(123);
            }
            else
            {
            	v.setBackgroundColor(231);
            }
        }
        return v;
    }
}