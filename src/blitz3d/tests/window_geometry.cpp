// Real SDL/Win32 geometry; creates only this fixture's window, sends no OS input.
// Run separately at the DPI settings to be verified. Not an Alt-Tab UI test.
#include <bb/platform.windows/platform.windows.h>
#include <SDL.h>
#include <SDL_syswm.h>
#include <cstdio>

static int failures=0;
static void expect(bool ok,const char *name){
    std::printf("%s: %s\n",ok?"PASS":"FAIL",name);
    if(!ok)++failures;
}
static void pump(){
    SDL_Event event;
    while(SDL_PollEvent(&event)){}
}
static void geometry(SDL_Window *win,HWND hwnd){
    pump();
    MONITORINFO monitor={};monitor.cbSize=sizeof(monitor);
    POINT primary={0,0};
    expect(GetMonitorInfoW(MonitorFromPoint(primary,MONITOR_DEFAULTTOPRIMARY),&monitor)!=0,"primary monitor bounds");
    RECT outer={},client={};POINT origin={0,0};
    expect(GetWindowRect(hwnd,&outer)!=0,"window rectangle available");
    expect(GetClientRect(hwnd,&client)!=0 && ClientToScreen(hwnd,&origin)!=0,"client rectangle available");
    expect(EqualRect(&outer,&monitor.rcMonitor)!=0,"outer rectangle fills monitor, including taskbar area");
    expect(origin.x==monitor.rcMonitor.left && origin.y==monitor.rcMonitor.top,"client origin has no titlebar/border offset");
    expect(client.right==monitor.rcMonitor.right-monitor.rcMonitor.left &&
           client.bottom==monitor.rcMonitor.bottom-monitor.rcMonitor.top,"client area fills monitor");
    int w=0,h=0;SDL_GetWindowSize(win,&w,&h);
    expect(w==client.right && h==client.bottom,"SDL and Win32 client sizes agree");
    expect((SDL_GetWindowFlags(win)&SDL_WINDOW_FULLSCREEN_DESKTOP)==SDL_WINDOW_FULLSCREEN_DESKTOP,"SDL knows desktop fullscreen state");
    expect((GetWindowLongPtrW(hwnd,GWL_STYLE)&(WS_CAPTION|WS_THICKFRAME))==0,"native window has no frame");
}
int main(){
    s2WinDpiAware();
    if(SDL_Init(SDL_INIT_VIDEO)!=0)return 2;
    expect(s2WinBorderless()==0,"no-window request fails");
    SDL_DisplayMode before={};
    if(SDL_GetCurrentDisplayMode(0,&before)!=0)return 2;
    SDL_Window *win=SDL_CreateWindow("Stranded II - fullscreen geometry verification",
        SDL_WINDOWPOS_CENTERED,SDL_WINDOWPOS_CENTERED,800,600,0);
    if(!win)return 2;
    SDL_SysWMinfo info={};SDL_VERSION(&info.version);
    if(!SDL_GetWindowWMInfo(win,&info))return 2;
    for(int iteration=0;iteration<3;++iteration){
        expect(s2WinBorderless()!=0,"enter desktop fullscreen");
        geometry(win,info.info.win.window);
        // Exercise SDL geometry updates after the transition, not just the
        // immediate SetWindowPos result that previously appeared correct.
        SDL_SetWindowSize(win,800+iteration*64,600+iteration*32);
        SDL_SetWindowTitle(win,"Stranded II - fullscreen geometry recheck");
        expect(s2WinBorderless()!=0,"repeated request is idempotent");
        geometry(win,info.info.win.window);
        SDL_DisplayMode current={};
        expect(SDL_GetCurrentDisplayMode(0,&current)==0 && current.w==before.w &&
               current.h==before.h && current.refresh_rate==before.refresh_rate &&
               current.format==before.format,"desktop video mode is unchanged");
        expect(SDL_SetWindowFullscreen(win,0)==0,"leave fullscreen");
        SDL_SetWindowSize(win,800,600);
        SDL_SetWindowPosition(win,40+iteration*16,60+iteration*16);
        pump();
        expect((GetWindowLongPtrW(info.info.win.window,GWL_STYLE)&WS_CAPTION)==WS_CAPTION,"windowed titlebar restored");
    }
    SDL_DestroyWindow(win);SDL_Quit();
    return failures?1:0;
}
