
#ifndef BBSTREAM_H
#define BBSTREAM_H

#include <bb/blitz/blitz.h>

class BBStream{
public:
	enum{
		EOF_ERROR=-1,EOF_NOT=0,EOF_OK=1
	};

	BBStream();
	virtual ~BBStream();

	virtual int read( char *buff,int size )=0;

	virtual int write( const char *buff,int size )=0;

	virtual int avail()=0;

	virtual int eof()=0;
};

void debugStream( BBStream *s );

#include <bb/stream/commands.h>

#endif
