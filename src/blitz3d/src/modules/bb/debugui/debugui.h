#ifndef BB_DEBUGUI_H
#define BB_DEBUGUI_H
#include <bb/blitz/blitz.h>
#include <SDL.h>
#include <webgpu/webgpu.h>
class BBCanvas;
void s2DebugUiAttach(SDL_Window*,WGPUDevice,WGPUTextureFormat);
void s2DebugUiDetach(SDL_Window*);
void s2DebugUiRender(WGPURenderPassEncoder,int,int);
bool s2DebugUiEvent(const SDL_Event&);
bool s2DebugUiVisible();
void s2DebugUiResetInput();
void s2DebugUiForgetCanvas(BBCanvas*);
#include <bb/debugui/commands.h>
#endif
