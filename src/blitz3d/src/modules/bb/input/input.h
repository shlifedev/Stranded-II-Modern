
#ifndef BBINPUT_H
#define BBINPUT_H

#include <bb/blitz/blitz.h>
#include <bb/input/driver.h>
#include <vector>

extern std::vector<BBDevice*> bbJoysticks;
extern BBInputDriver *gx_input;
extern BBDevice bbKeyboard;

int BBCALL bbEnumInput();
void bbResetMouseSpeed();

#include <bb/input/commands.h>

#endif
