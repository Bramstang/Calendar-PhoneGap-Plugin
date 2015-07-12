package nl.xservices.plugins;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.os.Build;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.util.Log;
import nl.xservices.plugins.accessor.AbstractCalendarAccessor;
import nl.xservices.plugins.accessor.CalendarProviderAccessor;
import android.database.Cursor;
import android.net.Uri;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Calendar extends CordovaPlugin {

	public static final String ACTION_LIST_CALENDARS = "listCalendars";
	public static final String ACTION_GET_CALENDAR_WITH_ID = "getCalendarWithId";
	public static final String ACTION_SAVE_CALENDAR = "saveCalendar";
	public static final String ACTION_DELETE_CALENDAR_WITH_ID = "deleteCalendarWithId";
	public static final String ACTION_LIST_EVENTS = "listEvents";
	public static final String ACTION_GET_EVENT_WITH_ID = "getEventWithId";
	public static final String ACTION_SAVE_EVENT = "saveEvent";
	public static final String ACTION_DELETE_EVENT_WITH_ID = "deleteEventWithId";
	public static final String ACTION_FIND_MATCHING_EVENTS = "findMatchingEvents";
	public static final String ACTION_DELETE_MATCHING_EVENTS = "deleteMatchingEvents";

	public static final String ACTION_SET_EVENT_STORE_CHANGED_CALLBACK = "setEventStoreChangedCallback";

	public static final Integer RESULT_CODE_CREATE = 0;
	private AbstractCalendarAccessor calendarAccessor;
	// private static CallbackContext callback;
	private CallbackContext callback;
	private static final String LOG_TAG = AbstractCalendarAccessor.LOG_TAG;

	@Override
	public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext)
			throws JSONException {

		callback = callbackContext;

		// if we need support for older android later
		final boolean hasLimitedSupport = Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH;

		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				System.out.println(action);
				if (ACTION_LIST_CALENDARS.equals(action)) {
					listCalendars();
				} else if (ACTION_GET_CALENDAR_WITH_ID.equals(action)) {
					getCalendarWithId(args);
				} else if (ACTION_SAVE_CALENDAR.equals(action)) {
					saveCalendar(args);
				} else if (ACTION_DELETE_CALENDAR_WITH_ID.equals(action)) {
					deleteCalendarWithId(args);
				} else if (ACTION_LIST_EVENTS.equals(action)) {
					listEvents(args);
				} else if (ACTION_GET_EVENT_WITH_ID.equals(action)) {
					getEventWithId(args);
				} else if (ACTION_SAVE_EVENT.equals(action)) {
					saveEvent(args);
				} else if (ACTION_DELETE_EVENT_WITH_ID.equals(action)) {
					deleteEventWithId(args);
				} else if (ACTION_FIND_MATCHING_EVENTS.equals(action)) {
					findMatchingEvents(args);
				} else if (ACTION_DELETE_MATCHING_EVENTS.equals(action)) {
					deleteMatchingEvents(args);
				} else if (ACTION_SET_EVENT_STORE_CHANGED_CALLBACK.equals(action)) {
					setEventStoreChangedCallback();
				}
			}
		});

		return stringContainsItemFromList(action, new String[] { ACTION_LIST_CALENDARS, ACTION_GET_CALENDAR_WITH_ID,
				ACTION_SAVE_CALENDAR, ACTION_DELETE_CALENDAR_WITH_ID, ACTION_LIST_EVENTS, ACTION_GET_EVENT_WITH_ID,
				ACTION_SAVE_EVENT, ACTION_DELETE_EVENT_WITH_ID, ACTION_FIND_MATCHING_EVENTS,
				ACTION_DELETE_MATCHING_EVENTS, ACTION_SET_EVENT_STORE_CHANGED_CALLBACK }); // so beautiful
	}

	// TODO dummy function for now
	private boolean setEventStoreChangedCallback() {

		PluginResult res = new PluginResult(PluginResult.Status.OK, true);
		res.setKeepCallback(true);
		callback.sendPluginResult(res);

		return true;
	}

	private boolean listCalendars() {
		JSONArray jsonObject;
		try {
			jsonObject = getCalendarAccessor().getActiveCalendars();
			PluginResult res = new PluginResult(PluginResult.Status.OK, jsonObject);
			res.setKeepCallback(true);
			callback.sendPluginResult(res);

		} catch (JSONException e) {

			e.printStackTrace();
		}

		return true;
	}

	private boolean getCalendarWithId(JSONArray args) {
		JSONArray jsonObject;
		try {
			jsonObject = getCalendarAccessor().getCalendarWithId(args.get(0).toString());
			PluginResult res = new PluginResult(PluginResult.Status.OK, jsonObject);
			res.setKeepCallback(true);
			callback.sendPluginResult(res);
			return true;
		} catch (JSONException e) {

			e.printStackTrace();

		}
		return false;
	}

	private boolean saveCalendar(JSONArray args) {
		if (args.length() == 0) {
			System.err.println("Exception: No Arguments passed");
		} else {
			try {
				JSONObject jsonFilter = args.getJSONObject(0);
				final String calendarName = jsonFilter.getString("calendarName");

				getCalendarAccessor().createCalendar(calendarName);

				PluginResult res = new PluginResult(PluginResult.Status.OK, true);
				res.setKeepCallback(true);
				callback.sendPluginResult(res);
				return true;
			} catch (JSONException e) {
				System.err.println("Exception: " + e.getMessage());
			}
		}
		return false;
	}

	private boolean deleteCalendarWithId(JSONArray args) {
		if (args.length() == 0) {
			System.err.println("Exception: No Arguments passed");
		} else {
			try {

				ContentResolver resolver = this.cordova.getActivity().getApplicationContext().getContentResolver();
				Uri deleteUri = null;
				deleteUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI,
						Long.valueOf(args.get(0).toString()).longValue());
				int nrDeletedRecords = resolver.delete(deleteUri, null, null);

				boolean deleteResult = nrDeletedRecords > 0;
				PluginResult res = new PluginResult(PluginResult.Status.OK, deleteResult);
				res.setKeepCallback(true);
				callback.sendPluginResult(res);
				return true;
			} catch (JSONException e) {
				System.err.println("Exception: " + e.getMessage());
			}
		}
		return false;
	}

	private boolean listEvents(JSONArray args) {
		try {
			Uri l_eventUri;
			if (Build.VERSION.SDK_INT >= 8) {
				l_eventUri = Uri.parse("content://com.android.calendar/events");
			} else {
				l_eventUri = Uri.parse("content://calendar/events");
			}
			ContentResolver contentResolver = this.cordova.getActivity().getContentResolver();
			JSONObject jsonFilter = args.getJSONObject(0);
			JSONArray result = new JSONArray();
			long input_start_date = jsonFilter.optLong("startDate");
			long input_end_date = jsonFilter.optLong("endDate");

			JSONArray calendarArray = (jsonFilter.isNull("calendars") ? null : jsonFilter.getJSONArray("calendars"));
			JSONObject calendarObject = (JSONObject) calendarArray.get(0);
			String calendarId = calendarObject.getString("id");
			String title = jsonFilter.isNull("title") ? null : jsonFilter.optString("title");
			String location = jsonFilter.isNull("location") ? null : jsonFilter.optString("location");
			String notes = jsonFilter.isNull("notes") ? null : jsonFilter.optString("notes");
			Integer allDay = jsonFilter.isNull("allDay") ? null : (jsonFilter.optBoolean("allDay") == true ? 1 : 0);

			// prepare start date
			java.util.Calendar calendar_start = java.util.Calendar.getInstance();
			Date date_start = new Date(input_start_date);
			calendar_start.setTime(date_start);

			// prepare end date
			java.util.Calendar calendar_end = java.util.Calendar.getInstance();
			Date date_end = new Date(input_end_date);
			calendar_end.setTime(date_end);

			// projection of DB columns
			String[] l_projection = new String[] { "calendar_id", "eventColor", "title", "description", "dtstart",
					"dtend", "eventLocation", "allDay", "_id" };

			// filter
			String selection = "( dtstart >=" + calendar_start.getTimeInMillis() + " AND dtend <="
					+ calendar_end.getTimeInMillis() + " AND deleted = 0";
			List<String> selectionList = new ArrayList<String>();
			String[] selectionArgs;

			if (title != null) {
				if (!"".equals(selection)) {
					selection += " AND ";
				}
				selection += Events.TITLE + "=?";
				selectionList.add(title);
			}
			if (calendarId!= null) {
				if (!"".equals(selection)) {
					selection += " AND ";
				}
				selection += Events.CALENDAR_ID + "=?";
				selectionList.add(calendarId);
			}
			if (location != null) {
				if (!"".equals(selection)) {
					selection += " AND ";
				}
				selection += Events.EVENT_LOCATION + "=?";
				selectionList.add(location);
			}
			if (notes != null) {
				if (!"".equals(selection)) {
					selection += " AND ";
				}
				selection += Events.DESCRIPTION + "=?";
				selectionList.add(notes);
			}
			if (allDay != null) {
				if (!"".equals(selection)) {
					selection += " AND ";
				}
				selection += Events.ALL_DAY + "=?";
				selectionList.add(Integer.toString(allDay));
			}
			selection += ")";

			selectionArgs = new String[selectionList.size()];
			// actual query
			Cursor cursor = contentResolver.query(l_eventUri, l_projection, selection,
					selectionList.toArray(selectionArgs), "dtstart ASC");

			int i = 0;
			while (cursor.moveToNext()) {

				result.put(
						i++,
						new JSONObject()
								.put("calendarId", cursor.getString(cursor.getColumnIndex("calendar_id")))
								.put("id", cursor.getString(cursor.getColumnIndex("_id")))
								.put("title", cursor.getString(cursor.getColumnIndex("title")))
								.put("notes", cursor.getString(cursor.getColumnIndex("description")))
								.put("startDate", cursor.getLong(cursor.getColumnIndex("dtstart")))
								.put("endDate", cursor.getLong(cursor.getColumnIndex("dtend")))
								.put("location",
										cursor.getString(cursor.getColumnIndex("eventLocation")) != null ? cursor
												.getString(cursor.getColumnIndex("eventLocation")) : "")
								.put("allDay", cursor.getInt(cursor.getColumnIndex("allDay")) == 1 ? true : false));
			}
//			System.err.println("From ListEvents");
//			System.err.println(result);
			
			PluginResult res = new PluginResult(PluginResult.Status.OK, result);
			res.setKeepCallback(true);
			callback.sendPluginResult(res);
			return true;
		} catch (JSONException e) {
			System.err.println("Exception: " + e.getMessage());
		}
		return false;
	}

	private boolean getEventWithId(JSONArray args) {
		if (args.length() == 0) {
			System.err.println("Exception: No Arguments passed");
		}
		try {
			JSONArray jsonEvents = getCalendarAccessor().findEventWithId(args.get(0).toString());

//			System.err.println("From GetEventWithId");
//			System.err.println(jsonEvents.toString());
			
			PluginResult res = new PluginResult(PluginResult.Status.OK, jsonEvents);
			res.setKeepCallback(true);
			callback.sendPluginResult(res);
			return true;

		} catch (JSONException e) {
			System.err.println("Exception: " + e.getMessage());
		}
		return false;
	}

	private boolean saveEvent(JSONArray args) {
		try {
			final JSONObject argObject = args.getJSONObject(0);

			if (!argObject.isNull("id")) { // if it's an already existing event

				String eventId = argObject.getString("id");
				String newName = argObject.optString("title");
				String newLocation = argObject.optString("location");
				String newNotes = argObject.optString("notes");
				Long newStartDate = argObject.optLong("startDate");
				Long newEndDate = argObject.optLong("endDate");
				String newCalendarId = argObject.optString("calendarId");

				ContentResolver contentResolver = this.cordova.getActivity().getContentResolver();
				ContentValues values = new ContentValues();
				Uri updateUri = null;

				if (newName != "")
					values.put(Events.TITLE, newName);
				if (newLocation != "")
					values.put(Events.EVENT_LOCATION, newLocation);
				if (newNotes != "")
					values.put(Events.DESCRIPTION, newNotes);
				if (newStartDate != 0)
					values.put(Events.DTSTART, newStartDate);
				if (newEndDate != 0)
					values.put(Events.DTEND, newEndDate);
				if (newCalendarId != "")
					values.put(Events.CALENDAR_ID, Long.valueOf(newCalendarId).longValue());

				updateUri = ContentUris.withAppendedId(Events.CONTENT_URI, Long.valueOf(eventId).longValue());
				int rows = contentResolver.update(updateUri, values, null, null);

				PluginResult res = new PluginResult(PluginResult.Status.OK, rows > 0);
				res.setKeepCallback(true);
				callback.sendPluginResult(res);
				// callback.success("" + (rows > 0));
				return rows > 0;
			}

			boolean status = getCalendarAccessor().createEvent(null, argObject.getString("title"),
					argObject.getLong("startDate"), argObject.getLong("endDate"),
					argObject.isNull("notes") ? null : argObject.getString("notes"),
					argObject.isNull("location") ? null : argObject.getString("location"),
					argObject.isNull("calendarId") ? null : argObject.getString("calendarId"),
					argObject.isNull("firstReminderMinutes") ? null : argObject.getLong("firstReminderMinutes"),
					argObject.isNull("secondReminderMinutes") ? null : argObject.getLong("secondReminderMinutes"),
					argObject.isNull("recurrence") ? null : argObject.getString("recurrence"),
					argObject.isNull("recurrenceEndTime") ? null : argObject.getLong("recurrenceEndTime"));

			PluginResult res = new PluginResult(PluginResult.Status.OK, status);
			res.setKeepCallback(true);
			callback.sendPluginResult(res);
			// callback.success("" + (rows > 0));

			// callback.success("" + status);
			return true;
		} catch (Exception e) {
			System.err.println("Exception: " + e.getMessage());
		}
		return false;
	}

	private boolean deleteEventWithId(JSONArray args) {
		if (args.length() == 0) {
			System.err.println("Exception: No Arguments passed");
		} else {
			try {
				boolean deleteResult = getCalendarAccessor().deleteEventWithId(
						Long.valueOf(args.get(0).toString()).longValue());
				PluginResult res = new PluginResult(PluginResult.Status.OK, deleteResult);
				res.setKeepCallback(true);
				callback.sendPluginResult(res);
				return true;
			} catch (JSONException e) {
				System.err.println("Exception: " + e.getMessage());
			}
		}
		return false;
	}

	private boolean findMatchingEvents(JSONArray args) {
		if (args.length() == 0) {
			System.err.println("Exception: No Arguments passed");
		}
		try {
			JSONObject jsonFilter = args.getJSONObject(0);
			JSONArray jsonEvents = getCalendarAccessor().findEvents(null,
					jsonFilter.isNull("title") ? null : jsonFilter.optString("title"),
					jsonFilter.isNull("location") ? null : jsonFilter.optString("location"),
					jsonFilter.isNull("notes") ? null : jsonFilter.optString("notes"),
					jsonFilter.isNull("allDay") ? null : (jsonFilter.optBoolean("allDay") == true ? 1 : 0),
					jsonFilter.optLong("startDate"), jsonFilter.optLong("endDate"));

//			System.err.println("From FindMatchingEvents");
			PluginResult res = new PluginResult(PluginResult.Status.OK, jsonEvents);
			res.setKeepCallback(true);
			callback.sendPluginResult(res);
			return true;

		} catch (JSONException e) {
			System.err.println("Exception: " + e.getMessage());
		}
		return false;
	}

	private boolean deleteMatchingEvents(JSONArray args) {
		if (args.length() == 0) {
			System.err.println("Exception: No Arguments passed");
		}
		try {
			JSONObject jsonFilter = args.getJSONObject(0);
			JSONArray jsonEvents = getCalendarAccessor().findEvents(null,
					jsonFilter.isNull("title") ? null : jsonFilter.optString("title"),
					jsonFilter.isNull("location") ? null : jsonFilter.optString("location"),
					jsonFilter.isNull("notes") ? null : jsonFilter.optString("notes"),
					jsonFilter.isNull("allDay") ? null : (jsonFilter.optBoolean("allDay") == true ? 1 : 0),
					jsonFilter.optLong("startDate"), jsonFilter.optLong("endDate"));

			boolean flag = true;
			for (int i = 0; i < jsonEvents.length(); i++) {
				String eventId = jsonEvents.getJSONObject(i).getString("id");
				if (flag)
					flag = getCalendarAccessor().deleteEventWithId(Long.valueOf(eventId).longValue());
			}

			PluginResult res = new PluginResult(PluginResult.Status.OK, flag);
			res.setKeepCallback(true);
			callback.sendPluginResult(res);
			return true;

		} catch (JSONException e) {
			System.err.println("Exception: " + e.getMessage());
		}
		return false;
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == RESULT_CODE_CREATE) {
			if (resultCode == Activity.RESULT_OK || resultCode == Activity.RESULT_CANCELED) {
				// resultCode may be 0 (RESULT_CANCELED) even when it was
				// created, so passing nothing is the clearest option here
				callback.success();
			}
		} else {
			callback.error("Unable to add event (" + resultCode + ").");
		}
	}

	// In case we need to re-introduce the LegacyCalendarAccessor
	private AbstractCalendarAccessor getCalendarAccessor() {
		if (this.calendarAccessor == null) {

			this.calendarAccessor = new CalendarProviderAccessor(this.cordova);

			if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
				Log.d(LOG_TAG, "Using old unsupported version of Android. Several functions won't work");
			}
		}
		return this.calendarAccessor;
	}

	public static boolean stringContainsItemFromList(String inputString, String[] items) {
		for (int i = 0; i < items.length; i++) {
			if (inputString.contains(items[i])) {
				return true;
			}
		}
		return false;
	}
}