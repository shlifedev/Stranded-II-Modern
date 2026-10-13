
#include <cstring>
#include "device.h"

BBDevice::BBDevice(){
	memset( name,0,sizeof(name) );
	reset();
}

BBDevice::~BBDevice(){
}

void BBDevice::reset(){
	memset( down_state,0,sizeof(down_state) );
	memset( axis_states,0,sizeof(axis_states) );
	memset( hit_count,0,sizeof(hit_count) );
	put=get=0;
}

void BBDevice::downEvent( int key,bool que ){
	if( (unsigned)key>=256 ) return;
	down_state[key]=true;
	++hit_count[key];
	if( que ) queue( key );
}

void BBDevice::upEvent( int key ){
	if( (unsigned)key>=256 ) return;
	down_state[key]=false;
}

void BBDevice::queue( int code ){
	if( put-get<QUE_SIZE ) que[put++&QUE_MASK]=code;
}

void BBDevice::setDownState( int key,bool down ){
	if( (unsigned)key>=256 ) return;
	if( down==down_state[key] ) return;
	if( down ) downEvent( key );
	else upEvent( key );
}

void BBDevice::flush(){
	update();
	memset( hit_count,0,sizeof(hit_count) );
	put=get=0;
}

bool BBDevice::keyDown( int key ){
	if( (unsigned)key>=256 ) return false;
	update();
	return down_state[key];
}

int BBDevice::keyHit( int key ){
	if( (unsigned)key>=256 ) return 0;
	update();
	int n=hit_count[key];
	hit_count[key]-=n;
	return n;
}

int BBDevice::getKey(){
	update();
	return get<put ? que[get++ & QUE_MASK] : 0;
}

float BBDevice::getAxisState( int axis ){
	if( (unsigned)axis>=32 ) return 0;
	update();
	return axis_states[axis];
}

const char *BBDevice::getId(){
	return id;
}

const char *BBDevice::getName(){
	return name;
}
