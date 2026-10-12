#define MINIMP3_IMPLEMENTATION
#define MINIMP3_NO_STDIO
#include "mp3_stream.h"
#include <string.h>
#include <bb/blitz/app.h>

#define BITS 16

size_t MP3AudioStream::read_cb( void *buf,size_t size,void *user_data ){
	MP3AudioStream *stream=(MP3AudioStream*)user_data;
	stream->in.read( (char*)buf,size );
	size_t s=stream->in.gcount();
	stream->in.clear();
	return s;
}

int MP3AudioStream::seek_cb( uint64_t position,void *user_data ){
	MP3AudioStream *stream=(MP3AudioStream*)user_data;
	stream->in.clear();
	stream->in.seekg( position,std::ios_base::beg );
	return stream->in.good() ? 0 : -1;
}


MP3AudioStream::MP3AudioStream( int buf_size ):AudioStream( buf_size ),io{},dec{}{
}

MP3AudioStream::~MP3AudioStream(){
	mp3dec_ex_close( &dec );
}

bool MP3AudioStream::readHeader(){
	io=mp3dec_io_t{
		read_cb,
		this,
		seek_cb,
		this,
	};

	if( mp3dec_ex_open_cb(&dec, &io, MP3D_SEEK_TO_SAMPLE) ){
		return false;
	}

	if( dec.samples==0 ){
		return false;
	}

	samples=dec.samples;
	bits=BITS;
	channels=dec.info.channels;
	frequency=dec.info.hz;

	return true;
}

void MP3AudioStream::seek( long pos ){
	mp3dec_ex_seek( &dec,pos );
}

long MP3AudioStream::pos(){
	return dec.cur_sample;
}

size_t MP3AudioStream::decode(){
	size_t n=mp3dec_ex_read( &dec,(mp3d_sample_t*)buf,buf_size/sizeof(mp3d_sample_t) );
	return n*sizeof( mp3d_sample_t );
}
