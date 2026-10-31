#define WIN32_LEAN_AND_MEAN
#include "../../../stdutil/stdutil.h"
#include <windows.h>
#include <imm.h>
#include <algorithm>
#include <cstring>
#include <string>
#include <bb/blitz/legacy32.h>
#include "platform.windows.h"
#include "../../../stdutil/windows_utf8.h"
#ifdef S2_WINDOWS_GRAPHICS
#include <SDL.h>
#include <SDL_syswm.h>
#endif

extern "C" int __stdcall QolInputAttach(HWND, HWND);
extern "C" void __stdcall QolInputDetach(void);

namespace {
HWND searchWindow=nullptr;
DWORD fileError=0;
HWND window(bb_int_t id){ return id>0?static_cast<HWND>(s2Pointer(id)):nullptr; }
HANDLE handle(bb_int_t id){ return id>0?static_cast<HANDLE>(s2Pointer(id)):INVALID_HANDLE_VALUE; }
int result(BOOL ok){fileError=ok?0:GetLastError();return ok!=FALSE;}
void *buffer(bbBank *bank,size_t bytes){
    if(!bank || bank->size<0 || size_t(bank->size)<bytes) RTEX("Windows service buffer is too small");
    return bank->data;
}
// Match WriteFile/FileType: a stray byte from legacy text input must name the
// same file there, and a clipboard copy must not raise a runtime error.
std::wstring consume(BBStr *value){std::string text=*value;delete value;return bbWide(text);}
std::string utf8(const wchar_t *value){
    int size=WideCharToMultiByte(CP_UTF8,0,value,-1,nullptr,0,nullptr,nullptr);
    std::string out(size,'\0');
    if(size) WideCharToMultiByte(CP_UTF8,0,value,-1,out.data(),size,nullptr,nullptr);
    if(!out.empty()) out.pop_back();return out;
}
std::wstring bankText(bbBank *bank){
    auto data=static_cast<const wchar_t*>(buffer(bank,2));
    size_t count=size_t(bank->size)/sizeof(wchar_t),length=0;
    while(length<count && data[length]) ++length;
    return std::wstring(data,length);
}
#ifdef S2_WINDOWS_GRAPHICS
SDL_Window *mainSDLWindow(){
    SDL_Window *win=SDL_GetKeyboardFocus();if(!win) win=SDL_GetWindowFromID(1);
    return win;
}
#endif
HWND mainWindow(){
#ifdef S2_WINDOWS_GRAPHICS
    SDL_Window *win=mainSDLWindow();
    if(win){SDL_SysWMinfo info={};SDL_VERSION(&info.version);if(SDL_GetWindowWMInfo(win,&info)) return info.info.win.window;}
#endif
    return nullptr;
}
}

bb_int_t s2WindowsWindowHandle(){return s2Handle(mainWindow());}
bool s2WindowsSearchFocused(){return searchWindow && GetFocus()==searchWindow;}

void BBCALL s2WinInterruptTime(bbBank *bank){
    ULONGLONG value=0;QueryUnbiasedInterruptTimePrecise(&value);memcpy(buffer(bank,8),&value,8);
}
bb_int_t BBCALL s2WinCounter(bbBank *bank){LARGE_INTEGER value;BOOL ok=QueryPerformanceCounter(&value);memcpy(buffer(bank,8),&value,8);return ok;}
bb_int_t BBCALL s2WinCounterFrequency(bbBank *bank){LARGE_INTEGER value;BOOL ok=QueryPerformanceFrequency(&value);memcpy(buffer(bank,8),&value,8);return ok;}
void BBCALL s2WinSleep(bb_int_t value){Sleep(value>0?DWORD(value):0);}
bb_int_t BBCALL s2WinDpiAware(){return SetProcessDPIAware();}
bb_int_t BBCALL s2WinMouseCapture(bb_int_t enabled){
#ifdef S2_WINDOWS_GRAPHICS
    extern bool s2WindowsMouseCapture(bool);
    return s2WindowsMouseCapture(enabled!=0);
#else
    return 0;
#endif
}
bb_int_t BBCALL s2WinFileMove(BBStr *from,BBStr *to,bb_int_t flags){auto a=consume(from),b=consume(to);return result(MoveFileExW(a.c_str(),b.c_str(),DWORD(flags)));}
bb_int_t BBCALL s2WinFileCopy(BBStr *from,BBStr *to,bb_int_t fail){auto a=consume(from),b=consume(to);return result(CopyFileW(a.c_str(),b.c_str(),fail));}
bb_int_t BBCALL s2WinFileOpen(BBStr *name,bb_int_t access,bb_int_t share,bb_int_t,bb_int_t creation,bb_int_t attributes,bb_int_t){
    auto path=consume(name);HANDLE file=CreateFileW(path.c_str(),DWORD(access),DWORD(share),nullptr,DWORD(creation),DWORD(attributes),nullptr);
    if(file==INVALID_HANDLE_VALUE){fileError=GetLastError();return -1;}fileError=0;return s2Handle(file);
}
bb_int_t BBCALL s2WinFileFlush(bb_int_t id){return result(FlushFileBuffers(handle(id)));}
bb_int_t BBCALL s2WinHandleClose(bb_int_t id){int ok=result(CloseHandle(handle(id)));if(ok) s2ForgetHandle(id);return ok;}
bb_int_t BBCALL s2WinFileFind(BBStr *name,bbBank *bank){
    auto path=consume(name);WIN32_FIND_DATAW data={};HANDLE file=FindFirstFileW(path.c_str(),&data);
    if(file==INVALID_HANDLE_VALUE){fileError=GetLastError();return -1;}
    // The game reads the original 32-bit WIN32_FIND_DATA timestamps only.
    WIN32_FIND_DATAA legacy={};memcpy(&legacy,&data,44);memcpy(buffer(bank,sizeof(legacy)),&legacy,sizeof(legacy));
    fileError=0;return s2Handle(file);
}
bb_int_t BBCALL s2WinFindClose(bb_int_t id){int ok=result(FindClose(handle(id)));if(ok) s2ForgetHandle(id);return ok;}
bb_int_t BBCALL s2WinLastError(){return bb_int_t(fileError);}
bb_int_t BBCALL s2WinForeground(){HWND app=mainWindow();return app && GetForegroundWindow()==app?s2Handle(app):0;}
bb_int_t BBCALL s2WinIconic(bb_int_t id){return IsIconic(window(id));}
bb_int_t BBCALL s2WinBorderless(){
#ifdef S2_WINDOWS_GRAPHICS
    SDL_Window *win=mainSDLWindow();
    if(!win)return 0;
    // Keep SDL's fullscreen/windowed geometry in sync with the HWND. Raw
    // SetWindowLong/SetWindowPos leaves SDL treating this as a decorated window
    // during later size, DPI and restore events.
    if((SDL_GetWindowFlags(win)&SDL_WINDOW_FULLSCREEN_DESKTOP)==SDL_WINDOW_FULLSCREEN_DESKTOP)return 1;
    // Match the game's primary-monitor desktop resolution selection. Let SDL
    // use the full display bounds (not the taskbar work area) without a mode switch.
    SDL_SetWindowPosition(win,SDL_WINDOWPOS_CENTERED_DISPLAY(0),SDL_WINDOWPOS_CENTERED_DISPLAY(0));
    return SDL_SetWindowFullscreen(win,SDL_WINDOW_FULLSCREEN_DESKTOP)==0;
#else
    return 0;
#endif
}
bb_int_t BBCALL s2WinStyle(bb_int_t id,bb_int_t index,bb_int_t style){return bb_int_t(SetWindowLongPtrW(window(id),index,LONG_PTR(uint32_t(style))));}
bb_int_t BBCALL s2WinPosition(bb_int_t id,bb_int_t after,bb_int_t x,bb_int_t y,bb_int_t w,bb_int_t h,bb_int_t flags){
    HWND next=after>0?window(after):reinterpret_cast<HWND>(intptr_t(after));
    return SetWindowPos(window(id),next,x,y,w,h,UINT(flags));
}
bb_int_t BBCALL s2WinMetric(bb_int_t index){return GetSystemMetrics(index);}
bb_int_t BBCALL s2WinCreate(bb_int_t exstyle,bbBank *className,bbBank *title,bb_int_t style,bb_int_t x,bb_int_t y,bb_int_t w,bb_int_t h,bb_int_t parent,bb_int_t,bb_int_t,bb_int_t){
    auto type=bankText(className),text=bankText(title);
    searchWindow=CreateWindowExW(DWORD(exstyle),type.c_str(),text.c_str(),DWORD(style),x,y,w,h,window(parent),nullptr,GetModuleHandleW(nullptr),nullptr);
    return s2Handle(searchWindow);
}
bb_int_t BBCALL s2WinDestroy(bb_int_t id){HWND win=window(id);BOOL ok=DestroyWindow(win);if(ok){s2ForgetHandle(id);if(win==searchWindow)searchWindow=nullptr;}return ok;}
bb_int_t BBCALL s2WinText(bb_int_t id,bbBank *bank,bb_int_t length){int count=std::max(0,std::min(length,bank->size/2));return GetWindowTextW(window(id),static_cast<wchar_t*>(buffer(bank,size_t(count)*2)),count);}
bb_int_t BBCALL s2WinSetText(bb_int_t id,bbBank *bank){auto text=bankText(bank);return SetWindowTextW(window(id),text.c_str());}
bb_int_t BBCALL s2WinMessage(bb_int_t id,bb_int_t message,bb_int_t w,bb_int_t l){return bb_int_t(SendMessageW(window(id),UINT(message),WPARAM(intptr_t(w)),LPARAM(l)));}
bb_int_t BBCALL s2WinFocus(bb_int_t id){return s2Handle(SetFocus(window(id)));}
bb_int_t BBCALL s2WinGetFocus(){return s2Handle(GetFocus());}
bb_int_t BBCALL s2WinClient(bb_int_t id,bbBank *bank){return GetClientRect(window(id),static_cast<RECT*>(buffer(bank,sizeof(RECT))));}
bb_int_t BBCALL s2WinInvalidate(bb_int_t id,bb_int_t,bb_int_t erase){return InvalidateRect(window(id),nullptr,erase);}
bb_int_t BBCALL s2WinUpdate(bb_int_t id){return UpdateWindow(window(id));}
bb_int_t BBCALL s2WinAttach(bb_int_t edit,bb_int_t parent){return QolInputAttach(window(edit),window(parent));}
void BBCALL s2WinDetach(){QolInputDetach();}
bb_int_t BBCALL s2WinOpenClipboard(bb_int_t id){return OpenClipboard(window(id));}
bb_int_t BBCALL s2WinCloseClipboard(){return CloseClipboard();}
bb_int_t BBCALL s2WinExamineClipboard(bb_int_t){return IsClipboardFormatAvailable(CF_UNICODETEXT);}
bb_int_t BBCALL s2WinEmptyClipboard(){return EmptyClipboard();}
BBStr *BBCALL s2WinGetClipboard(bb_int_t){
    HANDLE data=GetClipboardData(CF_UNICODETEXT);if(!data)return d_new BBStr("");
    auto text=static_cast<const wchar_t*>(GlobalLock(data));std::string result=text?utf8(text):"";
    if(text)GlobalUnlock(data);return d_new BBStr(result);
}
bb_int_t BBCALL s2WinSetClipboard(bb_int_t,BBStr *value){
    auto text=consume(value);size_t bytes=(text.size()+1)*sizeof(wchar_t);
    HGLOBAL data=GlobalAlloc(GMEM_MOVEABLE,bytes);if(!data)return 0;
    void *memory=GlobalLock(data);if(!memory){GlobalFree(data);return 0;}
    memcpy(memory,text.c_str(),bytes);GlobalUnlock(data);
    if(!SetClipboardData(CF_UNICODETEXT,data)){GlobalFree(data);return 0;}return 1;
}
BBMODULE_EMPTY(platform_windows);
