#include <bb/stub/stub.h>
#include <bb/input/input.h>
#include <bb/runtime/runtime.h>
#include <bb/blitz3d/blitz3d.h>
#include <SDL.h>
#include <cstdio>
#include <cstring>
#include <cmath>

// Override capture state only: the real SDL queue, runtime polling and Blitz
// input commands still run, without requiring a window or capturing OS input.
class RelativeInputDriver : public BBInputDriver{
    BBInputDriver *original;
public:
    explicit RelativeInputDriver(BBInputDriver *original):original(original){}
    BBDevice *getJoystick(int port)const override{ return original->getJoystick(port); }
    int getJoystickType(int port)const override{ return original->getJoystickType(port); }
    int numJoysticks()const override{ return original->numJoysticks(); }
    int toAscii(int code)const override{ return original->toAscii(code); }
    bool usesRelativeMouse()const override{ return true; }
};

static int failures=0;
static void expect(const char *name,int actual,int expected){
    if(actual!=expected){
        std::fprintf(stderr,"%s: expected %d, got %d\n",name,expected,actual);
        ++failures;
    }
}
static void key(Uint32 type,SDL_Scancode scancode,Uint8 repeat=0){
    SDL_Event event={};
    event.type=type;
    event.key.keysym.scancode=scancode;
    event.key.repeat=repeat;
    expect("queue key",SDL_PushEvent(&event),1);
}
static void character(SDL_Scancode scancode,int expected){
    bbFlushKeys();
    key(SDL_KEYDOWN,scancode);
    key(SDL_KEYUP,scancode);
    // GetKey must pump pending input itself, without waiting for a Flip.
    expect(SDL_GetScancodeName(scancode),bbGetKey(true),expected);
    expect("one character per press",bbGetKey(true),0);
}
static void motion(int x,int y){
    SDL_Event event={};
    event.type=SDL_MOUSEMOTION;
    event.motion.x=x;event.motion.y=y;
    expect("queue motion",SDL_PushEvent(&event),1);
}
int main(){
    SDL_SetMainReady();
    bbStartup("runtime-input-check","");
    if(!bbruntime_create()) return 2;
    character(SDL_SCANCODE_RETURN,13);
    character(SDL_SCANCODE_RETURN2,13);
    character(SDL_SCANCODE_KP_ENTER,13);
    character(SDL_SCANCODE_BACKSPACE,8);
    character(SDL_SCANCODE_TAB,9);
    character(SDL_SCANCODE_ESCAPE,27);
    character(SDL_SCANCODE_HOME,1);
    character(SDL_SCANCODE_END,2);
    character(SDL_SCANCODE_INSERT,3);
    character(SDL_SCANCODE_DELETE,4);
    character(SDL_SCANCODE_PAGEUP,5);
    character(SDL_SCANCODE_PAGEDOWN,6);
    character(SDL_SCANCODE_UP,28);
    character(SDL_SCANCODE_DOWN,29);
    character(SDL_SCANCODE_RIGHT,30);
    character(SDL_SCANCODE_LEFT,31);

    key(SDL_KEYDOWN,SDL_SCANCODE_W);
    expect("held movement key",bbKeyDown(17),1);
    expect("movement key hit",bbKeyHit(17),1);
    key(SDL_KEYDOWN,SDL_SCANCODE_W,1);
    expect("autorepeat does not add hits",bbKeyHit(17),0);
    key(SDL_KEYUP,SDL_SCANCODE_W);
    expect("released movement key",bbKeyDown(17),0);

    SDL_Event event={};
    event.type=SDL_TEXTEDITING;
    std::strcpy(event.edit.text,"preedit");
    expect("queue composition",SDL_PushEvent(&event),1);
    expect("composition is not committed text",bbGetKey(true),0);
    event={};event.type=SDL_TEXTINPUT;
    std::strcpy(event.text.text,"help");
    expect("queue text",SDL_PushEvent(&event),1);
    for(char c: "help") if(c) expect("console text",bbGetKey(true),c);
    expect("text consumed once",bbGetKey(true),0);

    bbMoveMouse(100,200);
    motion(107,196);
    expect("same-poll horizontal motion",bbMouseXSpeed(),7);
    expect("same-poll vertical motion",bbMouseYSpeed(),-4);
    expect("horizontal motion consumed once",bbMouseXSpeed(),0);
    expect("vertical motion consumed once",bbMouseYSpeed(),0);
    bbMoveMouse(400,300);
    motion(410,302);motion(417,306);
    expect("queued horizontal motion",bbMouseXSpeed(),17);
    expect("queued vertical motion",bbMouseYSpeed(),6);

    BBInputDriver *original=gx_input;
    RelativeInputDriver relative(original);
    gx_input=&relative;
    bbMoveMouse(400,300);
    bbMouseXSpeed();bbMouseYSpeed();
    // getinput reads mouse speed first, then polls keys. A single count can
    // arrive during those key reads, just before game's MoveMouse recenter.
    motion(401,299);
    bbKeyDown(17);
    bbMoveMouse(400,300);
    expect("late horizontal count survives recenter",bbMouseXSpeed(),1);
    expect("late vertical count survives recenter",bbMouseYSpeed(),-1);
    expect("preserved horizontal count consumed once",bbMouseXSpeed(),0);
    expect("preserved vertical count consumed once",bbMouseYSpeed(),0);

    // Polling Y can receive another X count after X was already read.
    motion(401,301);
    expect("vertical count read before recenter",bbMouseYSpeed(),1);
    bbMoveMouse(400,300);
    expect("independent unread horizontal count",bbMouseXSpeed(),1);
    expect("read vertical count is not replayed",bbMouseYSpeed(),0);

    int totalX=0,totalY=0;
    for(int frame=0;frame<240;++frame){
        motion(401,299);
        bbKeyDown(17);
        bbMoveMouse(400,300);
        totalX+=bbMouseXSpeed();totalY+=bbMouseYSpeed();
    }
    expect("240 slow horizontal counts",totalX,240);
    expect("240 slow vertical counts",totalY,-240);
    for(int frame=0;frame<240;++frame){
        motion(399,301);
        bbKeyDown(17);
        bbMoveMouse(400,300);
        totalX+=bbMouseXSpeed();totalY+=bbMouseYSpeed();
    }
    expect("slow horizontal reverse has no drift",totalX,0);
    expect("slow vertical reverse has no drift",totalY,0);

    motion(403,298);bbKeyDown(17);
    bbResetMouseSpeed();
    expect("capture transition discards old horizontal motion",bbMouseXSpeed(),0);
    expect("capture transition discards old vertical motion",bbMouseYSpeed(),0);
    gx_input=original;
    motion(410,290);bbKeyDown(17);
    bbMoveMouse(400,300);
    expect("absolute-mode warp retains legacy horizontal reset",bbMouseXSpeed(),0);
    expect("absolute-mode warp retains legacy vertical reset",bbMouseYSpeed(),0);

    // The actual rotation commands must retain the game's smallest sensitivity
    // increment (0.02 degrees) through repeated Euler/quaternion conversions.
    Entity *camera=bbCreatePivot(nullptr);
    for(int step=1;step<=240;++step){
        bbRotateEntity(camera,0,bbEntityYaw(camera,0)+0.02f,0,0);
        if(std::fabs(bbEntityYaw(camera,0)-step*0.02f)>0.001f){
            std::fputs("sub-degree camera rotation was lost\n",stderr);
            ++failures;break;
        }
    }
    bbFreeEntity(camera);
    bbruntime_destroy();
    if(failures) return 1;
    std::puts("PASS: native SDL keyboard, console text, slow mouse counts and sub-degree rotation (no window)");
    return 0;
}
