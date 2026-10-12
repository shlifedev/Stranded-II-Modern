#import <Foundation/Foundation.h>
#import <AppKit/AppKit.h>

int bbMacOpenURL(const char *value) {
  @autoreleasepool {
    NSURL *url=[NSURL URLWithString:[NSString stringWithUTF8String:value]];
    return url && [[NSWorkspace sharedWorkspace] openURL:url];
  }
}

const char *lookupFontFile( const char *fontName ){
  NSString *fn = [NSString stringWithUTF8String:fontName];

  NSFont *font=[NSFont fontWithName:fn size:10];
  CTFontDescriptorRef fontRef = CTFontDescriptorCreateWithNameAndSize ((CFStringRef)[font fontName], [font pointSize]);
  CFURLRef url = (CFURLRef)CTFontDescriptorCopyAttribute(fontRef, kCTFontURLAttribute);
  NSString *fp = [NSString stringWithString:[(NSURL *)CFBridgingRelease(url) path]];

  return [fp UTF8String];
}
