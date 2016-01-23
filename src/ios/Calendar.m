#import "Calendar.h"
#import <Cordova/CDV.h>
#import <EventKitUI/EventKitUI.h>
#import <EventKit/EventKit.h>

#define SYSTEM_VERSION_GREATER_THAN_OR_EQUAL_TO(v)  ([[[UIDevice currentDevice] systemVersion] compare:v options:NSNumericSearch] != NSOrderedAscending)

@implementation Calendar

@synthesize eventStore, eventStoreChangedCallbackId;

#pragma mark Initialisation functions

- (CDVPlugin*) initWithWebView:(UIWebView*)theWebView {
    self = (Calendar*)[super initWithWebView:theWebView];
    if (self) {
        [self initEventStoreWithCalendarCapabilities];
    }
    return self;
}

- (void)initEventStoreWithCalendarCapabilities {
    __block BOOL accessGranted = NO;
    eventStore= [[EKEventStore alloc] init];
    if([eventStore respondsToSelector:@selector(requestAccessToEntityType:completion:)]) {
        dispatch_semaphore_t sema = dispatch_semaphore_create(0);
        [eventStore requestAccessToEntityType:EKEntityTypeEvent completion:^(BOOL granted, NSError *error) {
            accessGranted = granted;
            dispatch_semaphore_signal(sema);
        }];
        dispatch_semaphore_wait(sema, DISPATCH_TIME_FOREVER);
    } else { // we're on iOS 5 or older
    accessGranted = YES;
}

if (accessGranted) {
    self.eventStore = eventStore;

    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(eventStoreChanged:) name:EKEventStoreChangedNotification object:nil];
}
}

#pragma mark Helper Functions

+ (NSDictionary*)dictFromCGColor:(CGColorRef)color {
    const CGFloat *c = CGColorGetComponents(color);
    return @{
       @"r": @(c[0]),
       @"g": @(c[1]),
       @"b": @(c[2]),
       @"a": @(CGColorGetAlpha(color)),
   };
}

+ (CGColorRef)CGColorFromDict:(NSDictionary*)colorDict {
    CGFloat colorArr[] = {
        [colorDict[@"r"] floatValue],
        [colorDict[@"g"] floatValue],
        [colorDict[@"b"] floatValue],
        [colorDict[@"a"] floatValue]
    };
    
    CGColorSpaceRef cs = CGColorSpaceCreateDeviceRGB();
    
    CGColorRef color = CGColorCreate( cs, colorArr );
    
    CGColorSpaceRelease( cs );
    
    return color;
}

- (NSDictionary*)dictFromCalendar:(EKCalendar*)calendar {
    return @{
       @"name": [calendar title] ? [calendar title] : [NSNull null],
       @"id": calendar.calendarIdentifier ? calendar.calendarIdentifier : [NSNull null],
       @"color": [Calendar dictFromCGColor:calendar.CGColor],
       @"allowsModify": @(calendar.allowsContentModifications)
   };
}

- (NSDictionary*)dictFromEvent:(EKEvent*)event {
    NSTimeInterval start = [event.startDate timeIntervalSince1970]*1000; //Unix offset format
    NSTimeInterval end = [event.endDate timeIntervalSince1970]*1000;
    
    return @{
       @"title": event.title ? event.title : [NSNull null],
       @"location": event.location ? event.location : [NSNull null],
       @"notes": event.notes ? event.notes : [NSNull null],
       @"startDate": @(start),
       @"endDate": @(end),
       @"allDay": @(event.allDay),
       @"id": event.eventIdentifier,
             //@"alarms":
             //@"recurrenceRules":
       @"calendarId": event.calendar.calendarIdentifier ? event.calendar.calendarIdentifier : [NSNull null],
   };
}

- (NSMutableArray*)dictsFromEvents:(NSArray*)matchingEvents {

    NSMutableArray *finalResults = [[NSMutableArray alloc] initWithCapacity:matchingEvents.count];
    
    // Stringify the results - Cordova can't deal with Obj-C objects
    for (EKEvent * event in matchingEvents) {
        [finalResults addObject:[self dictFromEvent:event]];
    }
    
    return finalResults;
}

-(NSNumber*)unixOffsetFromDate:(NSDate*)date {
    if(date == nil || [[NSNull null] isEqual:date])
        return nil;
    return @([date timeIntervalSince1970]*1000); //add ms
}

-(NSDate*)dateFromUnixOffset:(NSNumber*)offset {
    if(offset == nil || [[NSNull null] isEqual:offset])
        return nil;
        return [NSDate dateWithTimeIntervalSince1970:[offset doubleValue]/1000]; //remove ms
    }
    
    - (BOOL) isAllDayFromStartDate:(NSDate*)startDate toEndDate:(NSDate*)endDate {
        return [self isAllDayFromStartDate:startDate toEndDate:endDate];
    }
    
    - (BOOL) isAllDayFromStart:(NSDate*)startDate toEnd:(NSDate*)endDate {
        int duration = [endDate timeIntervalSinceDate:startDate]/1000; //remove ms
        const int daySeconds = 60*60*24;
        
        return duration % daySeconds == 0;
    }
    
    - (EKCalendar*)calendarFromId:(NSString*)calendarId {
        //Using EventStore calendarWithIdentifier: causes a lot of errors to be fired (works though)
        NSArray *calendars = [self.eventStore calendarsForEntityType:EKEntityTypeEvent];
        for(EKCalendar *calendar in calendars) {
            if([calendar.calendarIdentifier isEqualToString:calendarId])
                return calendar;
        }
        return nil;
    }
    
    - (NSArray*)calendarsFromDicts:(NSArray*)calendarDicts {

        NSMutableArray *calendars = [NSMutableArray arrayWithCapacity:[calendarDicts count]];
        for(NSDictionary *calendarDict in calendarDicts) {
            NSString* calendarId = calendarDict[@"id"];
            EKCalendar *calendar = [self calendarFromId:calendarId];
            if(calendar)
                [calendars addObject:calendar];
        }
        return calendars;
    }
    
    -(CDVPluginResult*)modifyEvent:(EKEvent*)event withPartialEventDict:(NSDictionary*)options {

        //Event

        event.title = options[@"title"] ? options[@"title"] : event.title;
        event.location = options[@"location"] ? options[@"location"] : event.location;
        event.notes = options[@"notes"] ? options[@"notes"] : event.notes;
        
        NSNumber
        *start  = options[@"startDate"],
        *end    = options[@"endDate"];
        
        NSDate
        *startDate = [self dateFromUnixOffset:start],
        *endDate = [self dateFromUnixOffset:end];
        
        event.startDate = startDate ? startDate : event.startDate;
        event.endDate = endDate ? endDate : event.endDate;
        
        NSNumber *allDay = options[@"allDay"];
        if(allDay && ![[NSNull null] isEqual:allDay])
            event.allDay = [allDay boolValue];
        else
            event.allDay = [self isAllDayFromStart:startDate toEnd:endDate];
        
        
        //Calendar
        NSString* calendarId = options[@"calendarId"];
        if(calendarId) {
            event.calendar = [self.eventStore calendarWithIdentifier:calendarId];
            if (event.calendar == nil)
                return [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Could not find calendar id"];
        }
        
        if(!event.calendar.allowsContentModifications) {
            return [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Calendar doesn't allow content modifications"];
        }
        
        // Now save the new details back to the store
        NSError *error = nil;
        [self.eventStore saveEvent:event span:EKSpanThisEvent error:&error];
        
        // Check error code + return result
        if (error)
            return [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:error.userInfo.description];
        else
            return [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:[self dictFromEvent:event]];
        
    }
    
    
    -(NSArray*)findEKEventsWithTitle: (NSString *)title
    location: (NSString *)location
    notes: (NSString *)notes
    startDate: (NSDate *)startDate
    endDate: (NSDate *)endDate
    calendar: (EKCalendar *) calendar {

        NSMutableArray *predicateStrings = [NSMutableArray arrayWithCapacity:3];
        if (title != (id)[NSNull null] && title.length > 0) {
            title = [title stringByReplacingOccurrencesOfString:@"'" withString:@"\\'"];
            [predicateStrings addObject:[NSString stringWithFormat:@"title beginswith[c] '%@'", title]];
        }
        if (location != (id)[NSNull null] && location.length > 0) {
            location = [location stringByReplacingOccurrencesOfString:@"'" withString:@"\\'"];
            [predicateStrings addObject:[NSString stringWithFormat:@"location == '%@'", location]];
        }
        if (notes != (id)[NSNull null] && notes.length > 0) {
            notes = [notes stringByReplacingOccurrencesOfString:@"'" withString:@"\\'"];
            [predicateStrings addObject:[NSString stringWithFormat:@"notes == '%@'", notes]];
        }

        NSString *predicateString = [predicateStrings componentsJoinedByString:@" AND "];

        NSPredicate *matches;
        NSArray *calendarArray, *datedEvents, *matchingEvents;

        if (predicateString.length > 0) {
            matches = [NSPredicate predicateWithFormat:predicateString];
            calendarArray = [NSArray arrayWithObject:calendar];

            datedEvents = [self.eventStore eventsMatchingPredicate:[eventStore predicateForEventsWithStartDate:startDate endDate:endDate calendars:calendarArray]];

            matchingEvents = [datedEvents filteredArrayUsingPredicate:matches];
        } else {
            calendarArray = [NSArray arrayWithObject:calendar];

            datedEvents = [self.eventStore eventsMatchingPredicate:[eventStore predicateForEventsWithStartDate:startDate endDate:endDate calendars:calendarArray]];

            matchingEvents = datedEvents;
        }

        return matchingEvents;
    }
    
    -(NSArray*)findEKEventsWithPartialEventDict:(NSDictionary*)options andCalendar:(EKCalendar*)calendar {

        NSString* title      = options[@"title"];
        NSString* location   = options[@"location"];
        NSString* notes      = options[@"notes"];
        NSNumber
        *start  = options[@"startDate"],
        *end    = options[@"endDate"];
        
        NSDate
        *startDate = [self dateFromUnixOffset:start],
        *endDate = [self dateFromUnixOffset:end];
        
        
        return [self findEKEventsWithTitle:title location:location notes:notes startDate:startDate endDate:endDate calendar:calendar];
    }
    
    -(EKCalendar*)findEKCalendar: (NSString *)calendarName {
        for (EKCalendar *thisCalendar in [self.eventStore calendarsForEntityType:EKEntityTypeEvent]){
            NSLog(@"Calendar: %@", thisCalendar.title);
            if ([thisCalendar.title isEqualToString:calendarName]) {
                return thisCalendar;
            }
        }
        NSLog(@"No match found for calendar with name: %@", calendarName);
        return nil;
    }
    
    -(EKSource*)findEKSource {

        //Described here: http://oleb.net/blog/2012/05/creating-and-deleting-calendars-in-ios/

        EKSource *defaultSource = [[eventStore defaultCalendarForNewEvents] source];
        if(defaultSource.sourceType == EKSourceTypeCalDAV && [defaultSource.title isEqualToString:@"iCloud"])
            return defaultSource;
        
        // if iCloud is on, it hides the local calendars, so check for iCloud first
        for (EKSource *source in self.eventStore.sources) {
            if (source.sourceType == EKSourceTypeCalDAV && [source.title isEqualToString:@"iCloud"]) {
                return source;
            }
        }
        
        return defaultSource;
    }
    
#pragma mark Cordova functions
    
    - (void)setEventStoreChangedCallback:(CDVInvokedUrlCommand*)command {
        eventStoreChangedCallbackId = command.callbackId;
        
        //Not necessary to do this
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_NO_RESULT];
        [result setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:result callbackId:eventStoreChangedCallbackId];
    }
    
    - (void)eventStoreChanged:(NSNotification *)notification {
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [result setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:result callbackId:eventStoreChangedCallbackId];
    }
    
    - (void)refreshEventStore:(CDVInvokedUrlCommand*)command {

        [self.commandDelegate runInBackground:^{

            [self.eventStore refreshSourcesIfNecessary];
            
            CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Refreshing if necessary..."];
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        }];
    }
    
#pragma mark Calendar
    
    - (void)listCalendars:(CDVInvokedUrlCommand*)command {

        [self.commandDelegate runInBackground:^{

            NSArray *calendars = [self.eventStore calendarsForEntityType:EKEntityTypeEvent];
            
            NSMutableArray *finalResults = [[NSMutableArray alloc] initWithCapacity:calendars.count];
            for (EKCalendar *calendar in calendars){
                [finalResults addObject:[self dictFromCalendar:calendar]];
            }
            
            CDVPluginResult* result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK messageAsArray:finalResults];
            
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        }];
    }
    
    -(void)getCalendarWithId:(CDVInvokedUrlCommand *)command {

        [self.commandDelegate runInBackground:^{

            NSString* calendarId = [command.arguments objectAtIndex:0];
            
            EKCalendar *calendar = [self calendarFromId:calendarId];
            
            CDVPluginResult *result;
            if(calendar) {
                NSDictionary *calendarDict = [self dictFromCalendar:calendar];
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:calendarDict];
            }
            else {
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Couldn't get calendar with id"];
            }
            
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        }];
    }
    
    -(void)saveCalendar:(CDVInvokedUrlCommand*)command {

        [self.commandDelegate runInBackground:^{

            NSDictionary* calendarDict = [command.arguments objectAtIndex:0];
            NSString *calendarId = calendarDict[@"id"];
            
            CDVPluginResult *result;
            
            
            EKCalendar *calendar = [self calendarFromId:calendarId];
            
            if (calendar == nil) {
                //Creating new calendar
                calendar = [EKCalendar calendarForEntityType:EKEntityTypeEvent eventStore:self.eventStore];
                calendar.source = [self findEKSource];
            }
            
            calendar.title = calendarDict[@"name"];
            
            // if the user did not allow permission to access the calendar, the error Object will be filled
            NSError* error;
            [self.eventStore saveCalendar:calendar commit:YES error:&error];
            if (error == nil)
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:[self dictFromCalendar:calendar]];
            else
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[NSString stringWithFormat:@"Error saving calendar: %@", error.description]];
            
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        }];



}

-(void)deleteCalendarWithId:(CDVInvokedUrlCommand*)command {

    [self.commandDelegate runInBackground:^{

        NSString* calendarId = [command.arguments objectAtIndex:0];

        EKCalendar *calendar = [self.eventStore calendarWithIdentifier:calendarId];

        CDVPluginResult *result;
        if (calendar == nil) {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"No calendar matching id"];
        } else {

            NSError *error;
            [eventStore removeCalendar:calendar commit:YES error:&error];

            if (error)
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[NSString stringWithFormat:@"Error deleting calendar: %@", error.description]];
            else
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];

        }

        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];


}

#pragma mark Event

- (void)listEvents:(CDVInvokedUrlCommand*)command {

    [self.commandDelegate runInBackground:^{

        NSDictionary* options = [command.arguments objectAtIndex:0];

        NSNumber
        *start  = options[@"startDate"],
        *end    = options[@"endDate"];

        NSArray* calendarsDicts  = options[@"calendars"];


        NSDate *startDate, *endDate;

        if(start && end && ![start isEqual:[NSNull null]] && ![end isEqual:[NSNull null]]) {
            startDate = [self dateFromUnixOffset:start],
            endDate = [self dateFromUnixOffset:end];

            if(!startDate || !endDate)
                NSLog(@"Warning: Couldn't read fromDate or toDate");
        }

        if(!startDate || !endDate) {
            const double secondsInAYear = (60.0*60.0*24.0)*365.0;
            startDate = [NSDate dateWithTimeIntervalSinceNow:-2*secondsInAYear];
            endDate = [NSDate dateWithTimeIntervalSinceNow:2*secondsInAYear];

                //Bug where can only fetch events from 4 years
                //startDate = [NSDate distantPast];
                //endDate = [NSDate distantFuture];
        }


        NSArray *calendars;
        if(calendarsDicts && ![[NSNull null] isEqual:calendarsDicts])
            calendars = [self calendarsFromDicts:calendarsDicts];

        NSPredicate *predicate = [self.eventStore predicateForEventsWithStartDate:startDate endDate:endDate calendars:calendars];

        NSArray *events = [self.eventStore eventsMatchingPredicate:predicate];

        NSArray *formattedEvents = [self dictsFromEvents:events];

        CDVPluginResult* result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK messageAsArray:formattedEvents];

            // The sendPluginResult method is thread-safe.
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];

}

-(void)getEventWithId:(CDVInvokedUrlCommand*)command {

    [self.commandDelegate runInBackground:^{

        NSString* eventIdentifier = [command.arguments objectAtIndex:0];

        EKEvent *event = [self.eventStore eventWithIdentifier:eventIdentifier];

        CDVPluginResult *result;
        if (event) {
            NSDictionary *eventDict = [self dictFromEvent:event];
            result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK messageAsDictionary:eventDict];
        } else {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Couldn't get event with id"];
        }

        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];

}

- (void)saveEvent:(CDVInvokedUrlCommand*)command {
    
    [self.commandDelegate runInBackground:^{
        
        NSDictionary *eventDict = [command.arguments objectAtIndex:0];
        NSString *eventId = eventDict[@"id"];
        
        CDVPluginResult *pluginResult;
        EKEvent *event;
        if(eventId) {
            event = [self.eventStore eventWithIdentifier:eventId];
        }
        else {
            //Assume creating event
            NSLog(@"No event id, assuming creating event");
            event = [EKEvent eventWithEventStore:self.eventStore];
        }
        
        if(event)
            pluginResult = [self modifyEvent:event withPartialEventDict:eventDict];
        else
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Couldn't get event with id"];
        
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
    
}

-(void)deleteEventWithId:(CDVInvokedUrlCommand*)command {

    [self.commandDelegate runInBackground:^{

        NSString* eventId = [command.arguments objectAtIndex:0];
        EKEvent *event = [self.eventStore eventWithIdentifier:eventId];

        NSError* error;
        [self.eventStore removeEvent:event span:EKSpanThisEvent error:&error];

        CDVPluginResult *result;
        if (error) {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:error.userInfo.description];
        } else {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        }

        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];

    }];
}

-(void)findMatchingEvents:(CDVInvokedUrlCommand*)command {

    [self.commandDelegate runInBackground:^{

        EKCalendar* calendar = self.eventStore.defaultCalendarForNewEvents;

        CDVPluginResult* result;

        if (calendar == nil) {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"No default calendar found. Is access to the Calendar blocked for this app?"];
        } else {

            NSDictionary* partialEventDict = [command.arguments objectAtIndex:0];
            NSArray *matchingEvents = [self findEKEventsWithPartialEventDict:partialEventDict andCalendar:calendar];

            NSMutableArray *finalResults = [self dictsFromEvents:matchingEvents];

            result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK messageAsArray:finalResults];

        }

        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];


}

@end
