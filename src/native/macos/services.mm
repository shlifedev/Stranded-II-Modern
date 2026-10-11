#import <Cocoa/Cocoa.h>
#include <SDL.h>
#include <SDL_syswm.h>
#include <mach/mach_time.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <copyfile.h>
#include <cerrno>
#include <cstdint>
#include <string>
#include <vector>
#include <set>
#include <algorithm>

namespace {
NSTextField *searchField=nil;
int fileError=0;
std::set<int> files;
SDL_Window *gameWindow(){
  SDL_Window *window=SDL_GetKeyboardFocus();
  return window?window:SDL_GetWindowFromID(1);
}
NSWindow *nativeWindow(){
  SDL_Window *window=gameWindow();
  if(!window) return nil;
  SDL_SysWMinfo info;SDL_VERSION(&info.version);
  return SDL_GetWindowWMInfo(window,&info)?info.info.cocoa.window:nil;
}
std::string utf8(NSString *value){return value?[value UTF8String]:"";}
NSString *ns(const std::string &value){return [NSString stringWithUTF8String:value.c_str()];}
std::vector<std::string> split(const std::string &value){
  std::vector<std::string> parts;size_t start=0,end;
  while((end=value.find('\x1f',start))!=std::string::npos){parts.push_back(value.substr(start,end-start));start=end+1;}
  parts.push_back(value.substr(start));return parts;
}
std::string path(std::string value){std::replace(value.begin(),value.end(),'\\','/');return value;}
std::string words(uint64_t value){return std::to_string(int32_t(value))+"\x1f"+std::to_string(int32_t(value>>32));}
uint64_t nanos(bool continuous){
  static mach_timebase_info_data_t scale=[](){mach_timebase_info_data_t value;mach_timebase_info(&value);return value;}();
  return (unsigned __int128)(continuous?mach_continuous_time():mach_absolute_time())*scale.numer/scale.denom;
}
int result(int rc){if(rc<0){fileError=errno;return 0;}fileError=0;return 1;}
}

bool s2MacSearchFocused(){
  NSWindow *window=nativeWindow();
  return searchField && window && [window firstResponder]==[searchField currentEditor];
}

std::string strandedMacProperty(const std::string &request){
 @autoreleasepool {
  auto arguments=split(request);const auto &op=arguments[0];
  const std::string value=arguments.size()>1?arguments[1]:"";
  SDL_Window *window=gameWindow();
  if(op=="counter") return words(nanos(false)/1000);
  if(op=="walltime") return words(nanos(true)/100);
  if(op=="error") return std::to_string(fileError);
  if(op=="focus") return window && (SDL_GetWindowFlags(window)&SDL_WINDOW_INPUT_FOCUS) && !(SDL_GetWindowFlags(window)&SDL_WINDOW_MINIMIZED)?"1":"0";
  if(op=="width" || op=="height"){
    SDL_DisplayMode mode={};if(SDL_GetDesktopDisplayMode(window?SDL_GetWindowDisplayIndex(window):0,&mode)<0) return op=="width"?"1280":"720";
    return std::to_string(op=="width"?mode.w:mode.h);
  }
  if(op=="canvas-width" || op=="canvas-height"){
    int w=0,h=0;if(window) SDL_GetWindowSize(window,&w,&h);return std::to_string(op=="canvas-width"?w:h);
  }
  if(op=="borderless"){if(window) SDL_SetWindowBordered(window,SDL_FALSE);return window?"1":"0";}
  if(op=="window-position" && arguments.size()==5){
    if(!window) return "0";
    SDL_SetWindowPosition(window,std::stoi(arguments[1]),std::stoi(arguments[2]));
    SDL_SetWindowSize(window,std::stoi(arguments[3]),std::stoi(arguments[4]));return "1";
  }
  if(op=="file-open"){
    int fd=open(path(value).c_str(),O_WRONLY);if(fd<0) fileError=errno;else files.insert(fd);return std::to_string(fd);
  }
  if(op=="file-flush" || op=="file-close"){
    int fd=std::stoi(value);if(!files.count(fd)){fileError=EBADF;return "0";}
    if(op=="file-close"){files.erase(fd);return std::to_string(result(close(fd)));}
    int rc;do{rc=fsync(fd);}while(rc<0 && errno==EINTR);
    if(rc==0) rc=fcntl(fd,F_FULLFSYNC);
    return std::to_string(result(rc));
  }
  if(op=="copy" && arguments.size()==4){
    int flags=COPYFILE_DATA;if(arguments[3]!="0") flags|=COPYFILE_EXCL;
    return std::to_string(result(copyfile(path(value).c_str(),path(arguments[2]).c_str(),nullptr,flags)));
  }
  if(op=="move" && arguments.size()==3){
    std::string destination=path(arguments[2]);
    if(!result(rename(path(value).c_str(),destination.c_str()))) return "0";
    size_t slash=destination.rfind('/');std::string parent=slash==std::string::npos?".":destination.substr(0,slash);
    int fd=open(parent.c_str(),O_RDONLY);if(fd<0){fileError=errno;return "0";}
    int rc=fsync(fd),saved=errno;close(fd);errno=saved;return std::to_string(result(rc));
  }
  if(op=="stamp"){
    struct stat info;if(stat(path(value).c_str(),&info)<0){fileError=errno;return "";}
    uint64_t time=(uint64_t(info.st_mtimespec.tv_sec)+11644473600ull)*10000000ull+info.st_mtimespec.tv_nsec/100;
    return words(time);
  }
  if(op=="clipboard-get") return utf8([[NSPasteboard generalPasteboard] stringForType:NSPasteboardTypeString]);
  if(op=="clipboard-set"){
    NSPasteboard *pasteboard=[NSPasteboard generalPasteboard];[pasteboard clearContents];
    return [pasteboard setString:ns(value) forType:NSPasteboardTypeString]?"1":"0";
  }
  if(op=="search-open"){
    if(searchField){[searchField removeFromSuperview];[searchField release];}
    searchField=[[NSTextField alloc] initWithFrame:NSMakeRect(0,0,250,28)];
    [searchField setStringValue:ns(value)];[searchField setFont:[NSFont systemFontOfSize:16]];
    [[nativeWindow() contentView] addSubview:searchField];return "2";
  }
  if(op=="search-close"){
    if(searchField){[nativeWindow() makeFirstResponder:nil];[searchField removeFromSuperview];[searchField release];searchField=nil;}return "1";
  }
  if(op=="search-text") return utf8([searchField stringValue]);
  if(op=="search-set"){[searchField setStringValue:ns(value)];return "1";}
  if(op=="search-position" && arguments.size()==5){
    CGFloat x=std::stod(arguments[1]),y=std::stod(arguments[2]),w=std::stod(arguments[3]),h=std::stod(arguments[4]);
    NSView *view=[nativeWindow() contentView];[searchField setFrame:NSMakeRect(x,view.bounds.size.height-y-h,w,h)];return "1";
  }
  if(op=="search-select" && arguments.size()==3){
    if(arguments[1]=="0" && arguments[2]=="-1") [searchField selectText:nil];return "1";
  }
  if(op=="search-focus"){
    NSWindow *native=nativeWindow();[native makeFirstResponder:value=="2"?(NSResponder*)searchField:(NSResponder*)[native contentView]];return "1";
  }
  if(op=="control-focus") return s2MacSearchFocused()?"2":"1";
  return "";
 }
}

void strandedMacError(const char *message);
#include <filesystem>
#include <fstream>

std::string strandedMacAppDirectory(const std::string &executable){
 namespace fs=std::filesystem;
 @autoreleasepool {
  NSString *resources=[[NSBundle mainBundle] resourcePath];
  fs::path bundle=fs::path(utf8(resources))/"game";
  if(!fs::is_directory(bundle)) return fs::path(executable).parent_path().string()+"/";
  try {
    NSData *data=[NSData dataWithContentsOfFile:[resources stringByAppendingPathComponent:@"native-assets.json"]];
    NSDictionary *manifest=data?[NSJSONSerialization JSONObjectWithData:data options:0 error:nullptr]:nil;
    if(![manifest isKindOfClass:[NSDictionary class]] || ![manifest[@"files"] isKindOfClass:[NSArray class]])
      throw std::runtime_error("Missing native asset manifest");
    const char *overrideRoot=getenv("S2_NATIVE_DATA_DIR");
    fs::path destination;
    if(overrideRoot && *overrideRoot) destination=overrideRoot;
    else {
      NSString *support=NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory,NSUserDomainMask,YES).firstObject;
      destination=fs::path(utf8(support))/"Stranded II Native Preview";
    }
    if(!destination.is_absolute() || destination==destination.root_path())
      throw std::runtime_error("Native data directory must be an absolute application directory");
    fs::create_directories(destination);destination=fs::canonical(destination);
    std::string fingerprint=utf8(manifest[@"fingerprint"]),previous;
    std::ifstream(destination/".native-assets")>>previous;
    for(NSString *entry in manifest[@"files"]){
      fs::path relative=utf8(entry);
      if(relative.empty() || relative.is_absolute()) throw std::runtime_error("Invalid asset path");
      for(const auto &component:relative) if(component=="..") throw std::runtime_error("Invalid asset path");
      fs::path target=destination/relative;
      auto resolved=fs::weakly_canonical(target).lexically_relative(destination);
      if(resolved.empty() || *resolved.begin()=="..") throw std::runtime_error("Asset target leaves the native data directory");
      bool exists=fs::exists(target);
      bool settings=relative.extension()==".cfg" || relative.filename()=="settings.inf" || relative==fs::path("core/modern.ini");
      if(exists && (settings || previous==fingerprint)) continue;
      fs::create_directories(target.parent_path());
      fs::path temporary=target;temporary+=".native-install";
      if(fs::exists(temporary) || fs::is_symlink(temporary)) throw std::runtime_error("Unfinished asset installation; preserve and inspect .native-install");
      fs::copy_file(bundle/relative,temporary);
      fs::rename(temporary,target);
    }
    fs::create_directories(destination/"mods/Stranded II/saves");
    if(fs::is_symlink(destination/".native-assets")) throw std::runtime_error("Native asset marker must not be a symbolic link");
    std::ofstream marker(destination/".native-assets",std::ios::trunc);marker<<fingerprint<<'\n';
    if(!marker) throw std::runtime_error("Cannot record native asset installation");
    if(chdir(destination.c_str())!=0) throw std::runtime_error("Cannot enter native data directory");
    return destination.string()+"/";
  }catch(const std::exception &error){
    strandedMacError(error.what());return "";
  }
 }
}
