
#ifndef LISTENER_H
#define LISTENER_H

#include "object.h"

class Listener : public Object{
public:
	Listener( float roll,float dopp,float dist );
	Listener( const Listener &t );
	~Listener();

	Entity *clone(){ return d_new Listener( *this ); }
	Listener *getListener(){ return this; }

	void renderListener();

private:
};

#endif