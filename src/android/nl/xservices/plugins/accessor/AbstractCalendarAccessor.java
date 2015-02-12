/**
 * Copyright (c) 2012, Twist and Shout, Inc. http://www.twist.com/
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * @author yvonne@twist.com (Yvonne Yip)
 */

package nl.xservices.plugins.accessor;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Instances;
import android.text.TextUtils;
import android.util.Log;
import org.apache.cordova.CordovaInterface;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

import static android.provider.CalendarContract.Events;

public abstract class AbstractCalendarAccessor {

	public static final String LOG_TAG = "Calendar";
	public static final String CONTENT_PROVIDER = "content://com.android.calendar";
	public static final String CONTENT_PROVIDER_PRE_FROYO = "content://calendar";

	public static final String CONTENT_PROVIDER_PATH_CALENDARS = "/calendars";
	public static final String CONTENT_PROVIDER_PATH_EVENTS = "/events";
	public static final String CONTENT_PROVIDER_PATH_REMINDERS = "/reminders";
	public static final String CONTENT_PROVIDER_PATH_INSTANCES_WHEN = "/instances/when";
	public static final String CONTENT_PROVIDER_PATH_ATTENDEES = "/attendees";

	public static final long year2000 = 946681200000L;
	public static final long year2100 = 4102441200000L;

	protected static class Event {
		String id;

		String eventId; // this is the one we care about
		String message; // notes
		String calendarId;
		String location;
		String title;
		
		String startDate;
		String endDate;
		
		// attribute DOMString status;
		// attribute DOMString transparency;
		// attribute CalendarRepeatRule recurrence;
		// attribute DOMString reminder;

		boolean recurring = false;
		boolean allDay;
		ArrayList<Attendee> attendees;

		public JSONObject toJSONObject() {
			JSONObject obj = new JSONObject();
			try {
				// obj.put("id", this.id);
				obj.put("id", this.eventId);
				obj.put("calendarId", this.calendarId);
				obj.putOpt("notes", this.message);
				obj.putOpt("location", this.location);
				obj.putOpt("title", this.title);
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				sdf.setTimeZone(TimeZone.getDefault());
				if (this.startDate != null) {
					obj.put("readableStartDate", sdf.format(new Date(Long.parseLong(this.startDate))));
					obj.put("startDate", Long.parseLong(this.startDate));
				}
				if (this.endDate != null) {
					obj.put("readableEndDate", sdf.format(new Date(Long.parseLong(this.endDate))));
					obj.put("endDate", Long.parseLong(this.endDate));
				}
				obj.put("allday", this.allDay);
				if (this.attendees != null) {
					JSONArray arr = new JSONArray();
					for (Attendee attendee : this.attendees) {
						arr.put(attendee.toJSONObject());
					}
					obj.put("attendees", arr);
				}
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
			return obj;
		}
	}

	protected static class Attendee {
		String id;
		String name;
		String email;
		String status;

		public JSONObject toJSONObject() {
			JSONObject obj = new JSONObject();
			try {
				obj.put("id", this.id);
				obj.putOpt("name", this.name);
				obj.putOpt("email", this.email);
				obj.putOpt("status", this.status);
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
			return obj;
		}
	}

	protected CordovaInterface cordova;

	private EnumMap<KeyIndex, String> calendarKeys;

	public AbstractCalendarAccessor(CordovaInterface cordova) {
		this.cordova = cordova;
		this.calendarKeys = initContentProviderKeys();
	}

	protected enum KeyIndex {
		CALENDARS_ID, CALENDARS_NAME, CALENDARS_VISIBLE, EVENTS_ID, EVENTS_CALENDAR_ID, EVENTS_DESCRIPTION, EVENTS_LOCATION, EVENTS_SUMMARY, EVENTS_START, EVENTS_END, EVENTS_RRULE, EVENTS_ALL_DAY, INSTANCES_ID, INSTANCES_EVENT_ID, INSTANCES_BEGIN, INSTANCES_END, ATTENDEES_ID, ATTENDEES_EVENT_ID, ATTENDEES_NAME, ATTENDEES_EMAIL, ATTENDEES_STATUS
	}

	protected abstract EnumMap<KeyIndex, String> initContentProviderKeys();

	protected String getKey(KeyIndex index) {
		return this.calendarKeys.get(index);
	}

	protected abstract Cursor queryAttendees(String[] projection, String selection, String[] selectionArgs,
			String sortOrder);

	protected abstract Cursor queryCalendars(String[] projection, String selection, String[] selectionArgs,
			String sortOrder);

	protected abstract Cursor queryEvents(String[] projection, String selection, String[] selectionArgs,
			String sortOrder);

	protected abstract Cursor queryEventsFromId(long searchedId, String[] projection, String selection,
			String[] selectionArgs, String sortOrder);

	protected abstract Cursor queryEventInstances(long startFrom, long startTo, String[] projection, String selection,
			String[] selectionArgs, String sortOrder);

	protected abstract Cursor queryEventInstancesFromId(String[] projection, String selection, String[] selectionArgs,
			String sortOrder);

	private Event[] fetchEventInstances(String searchedId, String title, String location, String notes, Integer allDay,
			long startFrom, long startTo) {

		String[] projection = { this.getKey(KeyIndex.INSTANCES_ID), this.getKey(KeyIndex.INSTANCES_EVENT_ID),
				this.getKey(KeyIndex.INSTANCES_BEGIN), this.getKey(KeyIndex.INSTANCES_END) };

		String sortOrder = this.getKey(KeyIndex.INSTANCES_BEGIN) + " ASC, " + this.getKey(KeyIndex.INSTANCES_END)
				+ " ASC";
		// Fetch events from instances table in ascending order by time.

		// filter
		String selection = "";
		List<String> selectionList = new ArrayList<String>();
		String[] selectionArgs;

		Cursor cursor;
		if (searchedId == null) { // if used for listEvents

			if (title != null) {
				selection += Events.TITLE + "=?";
				selectionList.add(title);
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
			if (startFrom != 0) {
				if (!"".equals(selection)) {
					selection += " AND ";
				}
				selection += Events.DTSTART + "=?";
				selectionList.add(Long.toString(startFrom));
			}
			if (startTo != 0) {
				if (!"".equals(selection)) {
					selection += " AND ";
				}
				selection += Events.DTEND + "=?";
				selectionList.add(Long.toString(startTo));
			}

			// if no dates were given I set them to year2000 and year2100 respectively
			startFrom = startFrom == 0 ? year2000 : startFrom;
			startTo = startTo == 0 ? year2100 : startTo;

			selectionArgs = new String[selectionList.size()];
			cursor = queryEventInstances(startFrom, startTo, projection, selection,
					selectionList.toArray(selectionArgs), sortOrder);
		} else { // if used for findEventWithId
			selection = Instances.EVENT_ID + " = ?";
			selectionArgs = new String[] { searchedId };
			cursor = queryEventInstancesFromId(projection, selection, selectionArgs, sortOrder);
		}
		Event[] instances = null;
		if (cursor.moveToFirst()) {
			int idCol = cursor.getColumnIndex(this.getKey(KeyIndex.INSTANCES_ID));
			int eventIdCol = cursor.getColumnIndex(this.getKey(KeyIndex.INSTANCES_EVENT_ID));
			int beginCol = cursor.getColumnIndex(this.getKey(KeyIndex.INSTANCES_BEGIN));
			int endCol = cursor.getColumnIndex(this.getKey(KeyIndex.INSTANCES_END));
			int count = cursor.getCount();
			int i = 0;
			instances = new Event[count];
			do {
				// Use the startDate/endDate time from the instances table. For recurring
				// events the events table contain the startDate/endDate time for the
				// origin event (as you would expect).
				instances[i] = new Event();
				instances[i].id = cursor.getString(idCol);
				instances[i].eventId = cursor.getString(eventIdCol);
				instances[i].startDate = cursor.getString(beginCol);
				instances[i].endDate = cursor.getString(endCol);
				i += 1;
			} while (cursor.moveToNext());
		}
		return instances;
	}

	private String[] getActiveCalendarIds() {
		Cursor cursor = queryCalendars(new String[] { this.getKey(KeyIndex.CALENDARS_ID) },
				this.getKey(KeyIndex.CALENDARS_VISIBLE) + "=1", null, null);
		String[] calendarIds = null;
		if (cursor.moveToFirst()) {
			calendarIds = new String[cursor.getCount()];
			int i = 0;
			do {
				int col = cursor.getColumnIndex(this.getKey(KeyIndex.CALENDARS_ID));
				calendarIds[i] = cursor.getString(col);
				i += 1;
			} while (cursor.moveToNext());
		}
		return calendarIds;
	}

	public final JSONArray getActiveCalendars() throws JSONException {
		
		Cursor cursor = queryCalendars(
				new String[] { this.getKey(KeyIndex.CALENDARS_ID), this.getKey(KeyIndex.CALENDARS_NAME) },
				this.getKey(KeyIndex.CALENDARS_VISIBLE) + "=1", null, null);
		JSONArray calendarsWrapper = new JSONArray();
		if (cursor.moveToFirst()) {
			do {
				JSONObject calendar = new JSONObject();
				calendar.put("id", cursor.getString(cursor.getColumnIndex(this.getKey(KeyIndex.CALENDARS_ID))));
				calendar.put("name", cursor.getString(cursor.getColumnIndex(this.getKey(KeyIndex.CALENDARS_NAME))));
				calendarsWrapper.put(calendar);
			} while (cursor.moveToNext());
		}
		return calendarsWrapper;
	}

	public final JSONArray getCalendarWithId(String calendarId) throws JSONException {
		Cursor cursor = queryCalendars(
				new String[] { this.getKey(KeyIndex.CALENDARS_ID), this.getKey(KeyIndex.CALENDARS_NAME) }, "_id=?",
				new String[] { calendarId }, null);
		JSONArray calendarsWrapper = new JSONArray();
		if (cursor.moveToFirst()) {
			do {
				JSONObject calendar = new JSONObject();
				calendar.put("id", cursor.getString(cursor.getColumnIndex(this.getKey(KeyIndex.CALENDARS_ID))));
				calendar.put("name", cursor.getString(cursor.getColumnIndex(this.getKey(KeyIndex.CALENDARS_NAME))));
				calendarsWrapper.put(calendar);
			} while (cursor.moveToNext());
		}
		return calendarsWrapper;
	}

	private Map<String, Event> fetchEventsAsMap(String searchedId, Event[] instances) {
		// Only selecting from active calendars, no active calendars = no events.
		String[] activeCalendarIds = getActiveCalendarIds();
		if (activeCalendarIds.length == 0) {
			return null;
		}
		String[] projection = new String[] { this.getKey(KeyIndex.EVENTS_ID), this.getKey(KeyIndex.EVENTS_DESCRIPTION),
				this.getKey(KeyIndex.EVENTS_LOCATION), this.getKey(KeyIndex.EVENTS_SUMMARY),
				this.getKey(KeyIndex.EVENTS_START), this.getKey(KeyIndex.EVENTS_END),
				this.getKey(KeyIndex.EVENTS_RRULE), this.getKey(KeyIndex.EVENTS_ALL_DAY),
				this.getKey(KeyIndex.EVENTS_CALENDAR_ID) };
		// Get all the ids at once from active calendars.
		StringBuffer select = new StringBuffer();
		select.append(this.getKey(KeyIndex.EVENTS_ID) + " IN (");
		select.append(instances[0].eventId);
		for (int i = 1; i < instances.length; i++) {
			select.append(",");
			select.append(instances[i].eventId);
		}
		select.append(") AND " + this.getKey(KeyIndex.EVENTS_CALENDAR_ID) + " IN (");
		select.append(activeCalendarIds[0]);
		for (int i = 1; i < activeCalendarIds.length; i++) {
			select.append(",");
			select.append(activeCalendarIds[i]);
		}
		select.append(")");
		Cursor cursor;
		if (searchedId == null) {
			cursor = queryEvents(projection, select.toString(), null, null);
		} else {
			cursor = queryEventsFromId(Long.valueOf(searchedId).longValue(), projection, select.toString(), null, null);
		}

		Map<String, Event> eventsMap = new HashMap<String, Event>();
		if (cursor.moveToFirst()) {
			int[] cols = new int[projection.length];
			for (int i = 0; i < cols.length; i++) {
				cols[i] = cursor.getColumnIndex(projection[i]);
			}
			do {
				Event event = new Event();
				event.id = cursor.getString(cols[0]);
				event.message = cursor.getString(cols[1]);
				event.location = cursor.getString(cols[2]);
				event.title = cursor.getString(cols[3]);
				event.startDate = cursor.getString(cols[4]);
				event.endDate = cursor.getString(cols[5]);
				event.recurring = !TextUtils.isEmpty(cursor.getString(cols[6]));
				event.allDay = cursor.getInt(cols[7]) != 0;
				event.calendarId = cursor.getString(cols[8]);
				eventsMap.put(event.id, event);
			} while (cursor.moveToNext());
		}
		return eventsMap;
	}

	private Map<String, ArrayList<Attendee>> fetchAttendeesForEventsAsMap(String[] eventIds) {
		// At least one id.
		if (eventIds.length == 0) {
			return null;
		}
		String[] projection = new String[] { this.getKey(KeyIndex.ATTENDEES_EVENT_ID),
				this.getKey(KeyIndex.ATTENDEES_ID), this.getKey(KeyIndex.ATTENDEES_NAME),
				this.getKey(KeyIndex.ATTENDEES_EMAIL), this.getKey(KeyIndex.ATTENDEES_STATUS) };
		StringBuffer select = new StringBuffer();
		select.append(this.getKey(KeyIndex.ATTENDEES_EVENT_ID) + " IN (");
		select.append(eventIds[0]);
		for (int i = 1; i < eventIds.length; i++) {
			select.append(",");
			select.append(eventIds[i]);
		}
		select.append(")");
		// Group the events together for easy iteration.
		Cursor cursor = queryAttendees(projection, select.toString(), null, this.getKey(KeyIndex.ATTENDEES_EVENT_ID)
				+ " ASC");
		Map<String, ArrayList<Attendee>> attendeeMap = new HashMap<String, ArrayList<Attendee>>();
		if (cursor.moveToFirst()) {
			int[] cols = new int[projection.length];
			for (int i = 0; i < cols.length; i++) {
				cols[i] = cursor.getColumnIndex(projection[i]);
			}
			ArrayList<Attendee> array = null;
			String currentEventId = null;
			do {
				String eventId = cursor.getString(cols[0]);
				if (currentEventId == null || !currentEventId.equals(eventId)) {
					currentEventId = eventId;
					array = new ArrayList<Attendee>();
					attendeeMap.put(currentEventId, array);
				}
				Attendee attendee = new Attendee();
				attendee.id = cursor.getString(cols[1]);
				attendee.name = cursor.getString(cols[2]);
				attendee.email = cursor.getString(cols[3]);
				attendee.status = cursor.getString(cols[4]);
				array.add(attendee);
			} while (cursor.moveToNext());
		}
		return attendeeMap;
	}

	public JSONArray findEvents(String searchedId, String title, String location, String notes, Integer allDay,
			long startFrom, long startTo) {
		JSONArray result = new JSONArray();

		// Fetch events from the instance table.
		Event[] instances = fetchEventInstances(searchedId, title, location, notes, allDay, startFrom, startTo);

		if (instances == null) {
			return result;
		}
		// Fetch events from the events table for more event info.
		Map<String, Event> eventMap = fetchEventsAsMap(searchedId, instances);
		// Fetch event attendees
		Map<String, ArrayList<Attendee>> attendeeMap = fetchAttendeesForEventsAsMap(eventMap.keySet().toArray(
				new String[0]));
		// Merge the event info with the instances and turn it into a JSONArray.
		for (Event instance : instances) {
			Event event = eventMap.get(instance.eventId);
			if (event != null) {
				instance.message = event.message;
				instance.location = event.location;
				instance.title = event.title;
				instance.calendarId = event.calendarId;

				if (!event.recurring) {
					instance.startDate = event.startDate;
					instance.endDate = event.endDate;
				}
				instance.allDay = event.allDay;
				instance.attendees = attendeeMap.get(instance.eventId);
				result.put(instance.toJSONObject());
			}
		}
		return result;
	}

	public boolean deleteEventWithId(long eventId) {
		ContentResolver resolver = this.cordova.getActivity().getApplicationContext().getContentResolver();
		Uri deleteUri = null;
		deleteUri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId);
		int nrDeletedRecords = resolver.delete(deleteUri, null, null);
		return nrDeletedRecords > 0;
	}

	public JSONArray findEventWithId(String eventId) {

		JSONArray jsonEvents = findEvents(eventId, null, null, null, null, AbstractCalendarAccessor.year2000,
				AbstractCalendarAccessor.year2100);

		return jsonEvents;

	}

	public boolean createEvent(Uri eventsUri, String title, long startTime, long endTime, String description,
			String location, String calendarId, Long firstReminderMinutes, Long secondReminderMinutes,
			String recurrence, Long recurrenceEndTime) {
		try {
			ContentResolver cr = this.cordova.getActivity().getContentResolver();
			ContentValues values = new ContentValues();
			final boolean allDayEvent = isAllDayEvent(new Date(startTime), new Date(endTime));
			values.put(Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
			values.put(Events.ALL_DAY, allDayEvent ? 1 : 0);
			values.put(Events.DTSTART, allDayEvent ? startTime + (1000 * 60 * 60 * 24) : startTime);
//			values.put(Events.DTSTART, startTime);
//			values.put(Events.DTEND, endTime);
			values.put(Events.DTEND, allDayEvent ? endTime + (1000 * 60 * 60 * 24) : endTime);
			values.put(Events.TITLE, title);
			values.put(Events.DESCRIPTION, description);
			values.put(Events.HAS_ALARM, 1);
			values.put(Events.CALENDAR_ID, calendarId == null ? 1 : Integer.valueOf(calendarId).intValue());
			values.put(Events.EVENT_LOCATION, location);

			if (recurrence != null) {
				if (recurrenceEndTime == null) {
					values.put(Events.RRULE, "FREQ=" + recurrence.toUpperCase());
				} else {
					final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
					values.put(Events.RRULE,
							"FREQ=" + recurrence.toUpperCase() + ";UNTIL=" + sdf.format(new Date(recurrenceEndTime)));
				}
			}

			Uri uri = cr.insert(eventsUri, values);

			long newEventId = Long.parseLong(uri.getLastPathSegment()); // the Id of the new event

			Log.d(LOG_TAG, "Added to ContentResolver");

			// TODO ?
			getActiveCalendarIds();

			if (firstReminderMinutes != null) {
				ContentValues reminderValues = new ContentValues();
				reminderValues.put("event_id", Long.parseLong(uri.getLastPathSegment()));
				reminderValues.put("minutes", firstReminderMinutes);
				reminderValues.put("method", 1);
				cr.insert(Uri.parse(CONTENT_PROVIDER + CONTENT_PROVIDER_PATH_REMINDERS), reminderValues);
			}

			if (secondReminderMinutes != null) {
				ContentValues reminderValues = new ContentValues();
				reminderValues.put("event_id", Long.parseLong(uri.getLastPathSegment()));
				reminderValues.put("minutes", secondReminderMinutes);
				reminderValues.put("method", 1);
				cr.insert(Uri.parse(CONTENT_PROVIDER + CONTENT_PROVIDER_PATH_REMINDERS), reminderValues);
			}
		} catch (Exception e) {
			Log.e("Calendar", e.getMessage(), e);
			return false;
		}

		return true;
	}

	public void createCalendar(String calendarName) {
		Uri calUri = CalendarContract.Calendars.CONTENT_URI;
		ContentValues cv = new ContentValues();
		cv.put(CalendarContract.Calendars.ACCOUNT_NAME, "LocalUser");
		cv.put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL);
		cv.put(CalendarContract.Calendars.NAME, calendarName);
		cv.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, calendarName);
		cv.put(CalendarContract.Calendars.CALENDAR_COLOR, 0x99FF99);
		cv.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER);
		cv.put(CalendarContract.Calendars.OWNER_ACCOUNT, true);

		calUri = calUri.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
				.appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "LocalUser")
				.appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
				.build();

		Uri result = this.cordova.getActivity().getApplicationContext().getContentResolver().insert(calUri, cv);
		int i = 0;
	}

	public static boolean isAllDayEvent(final Date startDate, final Date endDate) {
		return endDate.getTime() - startDate.getTime() == (24 * 60 * 60 * 1000) && startDate.getHours() == 0
				&& startDate.getMinutes() == 0 && startDate.getSeconds() == 0 && endDate.getHours() == 0
				&& endDate.getMinutes() == 0 && endDate.getSeconds() == 0;
	}
}
