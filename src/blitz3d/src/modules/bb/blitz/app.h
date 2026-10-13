#ifndef BLITZ_APP_H
#define BLITZ_APP_H

#include "basic.h"
#include <bb/hook/hook.h>
#include <string>

struct BBApp{
  std::string title,close;
  std::string executable_path, cmd_line;
};

extern BBHook bbAppOnChange;

void BBCALL bbStartup( const char *executable_path,const char *params );
BBApp BBCALL bbApp();

void bbSetLogHandler(void (*handler)(int,const char*));
void bbRuntimeLog(int level,const char *format,...);
#define LOGI( fmt,... ) bbRuntimeLog(0,fmt,__VA_ARGS__)
#define LOGE( fmt,... ) bbRuntimeLog(2,fmt,__VA_ARGS__)
#define LOGD( fmt,... ) bbRuntimeLog(3,fmt,__VA_ARGS__)

#endif
