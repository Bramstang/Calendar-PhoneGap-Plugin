cordova.addConstructor(function installCalendarPlugin() {
    'use strict';

    if (!window.plugins) {
        window.plugins = {};
    }

    window.plugins.calendar = {
        listCalendars: function(successCallback, errorCallback) {
            cordova.exec(successCallback, errorCallback, 'Calendar', 'listCalendars', []);
        },

        getCalendarWithId: function(calendarId, successCallback, errorCallback) {
            cordova.exec(successCallback, errorCallback, 'Calendar', 'getCalendarWithId', [calendarId]);
        },

        saveCalendar: function(calendar, successCallback, errorCallback) {
            cordova.exec(function() {

            }, errorCallback, 'Calendar', 'saveCalendar', [calendar]);
        },

        deleteCalendarWithId: function(calendarId, successCallback, errorCallback) {
            cordova.exec(successCallback, errorCallback, 'Calendar', 'deleteCalendarWithId', [calendarId]);
        },

        listEvents: function(options, successCallback, errorCallback) {
            cordova.exec(function(events){console.log(JSON.stringify(events));}, errorCallback, 'Calendar', 'listEvents', [options]);
        },

        getEventWithId: function(eventId, successCallback, errorCallback) {
            cordova.exec(successCallback, errorCallback, 'Calendar', 'getEventWithId', [eventId]);
        },

        saveEvent: function(anEvent, successCallback, errorCallback) {
            cordova.exec(successCallback, errorCallback, 'Calendar', 'saveEvent', [anEvent]);
        },

        deleteEventWithId: function(eventId, successCallback, errorCallback) {
            cordova.exec(successCallback, errorCallback, 'Calendar', 'deleteEventWithId', [eventId]);
        },

        findMatchingEvents: function(partialEvent, successCallback, errorCallback) {
            cordova.exec(successCallback, errorCallback, 'Calendar', 'findMatchingEvents', [partialEvent]);
        },

        deleteMatchingEvents: function(partialEvent, successCallback, errorCallback) {
            cordova.exec(successCallback, errorCallback, 'Calendar', 'deleteMatchingEvents', [partialEvent]);
        },

        refreshEventStore: function(successCallback, errorCallback) {
            cordova.exec(successCallback, errorCallback, 'Calendar', 'refreshEventStore');
        }
    };

    //Require jQuery instead?
    function triggerEvent(el, eventName) {
        var event;
        if (document.createEvent) {
            event = document.createEvent('HTMLEvents');
            event.initEvent(eventName, true, true);
        } else if (document.createEventObject) { // IE < 9
            event = document.createEventObject();
            event.eventType = eventName;
        }
        event.eventName = eventName;
        if (el.dispatchEvent) {
            el.dispatchEvent(event);
        } else if (el[eventName]) {
            el[eventName]();
        } else if (el['on' + eventName]) {
            el['on' + eventName]();
        }
    }

    cordova.exec(function eventStoreChanged() {
        triggerEvent(document, 'eventStoreChanged');
    }, function eventStoreChangedError() {
        console.log('Set eventStoreChanged callback error');
    }, 'Calendar', 'setEventStoreChangedCallback', []);

    return window.plugins.calendar;
});
