
#ifndef BBTIMER_H
#define BBTIMER_H

#include <bb/blitz/blitz.h>

class BBTimer{
public:
	virtual ~BBTimer();
public:
	virtual int wait()=0;
};

BBTimer * BBCALL _osCreateTimer( int hertz );

#include <bb/timer/commands.h>

#endif
