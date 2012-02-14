package de.tudarmstadt.botnet.janus_yanai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ShutdownReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		BootCompleteReceiver.getInstance(context).sendNotification(false);
	}

}
