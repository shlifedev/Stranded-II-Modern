#ifndef BB_STUB_STUB_H
#define BB_STUB_STUB_H

#include <bb/blitz/blitz.h>

typedef void (*BBMAIN)();
typedef  int (*BBSTART)( int,char**,BBMAIN );
typedef bool (*BBRTFUNC)();

bool bbruntime_create();
void bbruntime_link( BBRTLINK link );
bool bbruntime_destroy();

bool bbruntime_run( void (*pc)(),bool debug );

void sue( const char *t );

extern "C" int BBCALL bbStart( int argc,char *argv[], BBMAIN bbMain );

#endif
