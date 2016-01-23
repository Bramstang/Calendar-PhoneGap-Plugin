#import <Foundation/Foundation.h>
#import <Cordova/CDVPlugin.h>
#import <EventKitUI/EventKitUI.h>
#import <EventKit/EventKit.h>

@interface Calendar : CDVPlugin

@property (nonatomic, strong) EKEventStore* eventStore;
@property (nonatomic, strong) NSString* eventStoreChangedCallbackId;

- (void)initEventStoreWithCalendarCapabilities;

- (void)setEventStoreChangedCallback:(CDVInvokedUrlCommand*)command;
- (void)refreshEventStore:(CDVInvokedUrlCommand*)command;

//Calendar

- (void)listCalendars:(CDVInvokedUrlCommand*)command;
- (void)getCalendarWithId:(CDVInvokedUrlCommand*)command;
- (void)saveCalendar:(CDVInvokedUrlCommand*)command;
- (void)deleteCalendarWithId:(CDVInvokedUrlCommand*)command;

//Event

- (void)listEvents:(CDVInvokedUrlCommand*)command;
- (void)getEventWithId:(CDVInvokedUrlCommand*)command;
- (void)saveEvent:(CDVInvokedUrlCommand*)command;
- (void)deleteEventWithId:(CDVInvokedUrlCommand*)command;

- (void)findMatchingEvents:(CDVInvokedUrlCommand*)command;

@end
