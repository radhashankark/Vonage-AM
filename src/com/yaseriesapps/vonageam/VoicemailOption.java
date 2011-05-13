package com.yaseriesapps.vonageam;

public class VoicemailOption 
{
	private String voicemailFrom;
    private String voicemailWhen;
    private String voicemailLength;
    private String voicemailDirectLink;
    private boolean isVMUnread;
    
    public String getvoicemailFrom() 
    {
        return voicemailFrom;
    }
    
    public void setvoicemailFrom(String voicemailFrom)
    {
        this.voicemailFrom = voicemailFrom;
    }
    
    public String getvoicemailWhen() 
    {
        return voicemailWhen;
    }
    
    public void setvoicemailWhen(String voicemailWhen) 
    {
        this.voicemailWhen = voicemailWhen;
    }
    
    public String getvoicemailLength() 
    {
        return voicemailLength;
    }
    
    public void setvoicemailLength(String voicemailLength) 
    {
        this.voicemailLength = voicemailLength;
    }
    
    public String getvoicemailDirectLink() 
    {
        return voicemailDirectLink;
    }
    
    public void setvoicemailDirectLink(String voicemailDirectLink) 
    {
        this.voicemailDirectLink = voicemailDirectLink;
    }
    
    public boolean getisVMUnread() 
    {
        return isVMUnread;
    }
    
    public void setisVMUnread(boolean isVMUnread) 
    {
        this.isVMUnread = isVMUnread;
    }
}