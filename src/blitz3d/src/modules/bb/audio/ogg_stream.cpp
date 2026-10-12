
#include "ogg_stream.h"

#define BITS 16
#define SIGN 1
#define ENDIAN 0

size_t OGGAudioStream::oread( void *ptr, size_t size, size_t nmemb, void *datasource ){
	OGGAudioStream *stream=(OGGAudioStream*)datasource;
	stream->in.read( (char*)ptr,size*nmemb );
	size_t s=stream->in.gcount();
	stream->in.clear();
	return s;
}

int OGGAudioStream::oseek( void *datasource, ogg_int64_t offset, int whence ){
	OGGAudioStream *stream=(OGGAudioStream*)datasource;
	std::ios_base::seekdir way=whence==SEEK_SET?(std::ios_base::beg):(whence==SEEK_CUR?(std::ios_base::cur):std::ios_base::end);
	stream->in.clear();
	stream->in.seekg( offset,way );
	return stream->in.good() ? 0 : -1;
}

int OGGAudioStream::oclose( void *datasource ){
	return 0;
}

long OGGAudioStream::otell( void *datasource ){
	OGGAudioStream *stream=(OGGAudioStream*)datasource;
	return stream->in.tellg();
}

OGGAudioStream::OGGAudioStream( int buf_size ):AudioStream(buf_size),opened(false){
	bits=BITS;
}

OGGAudioStream::~OGGAudioStream(){
	if( opened ) ov_clear( &vfile );
}

bool OGGAudioStream::readHeader(){
	callbacks.read_func=oread;
	callbacks.seek_func=oseek;
	callbacks.close_func=oclose;
	callbacks.tell_func=otell;

	if ( ov_open_callbacks( this,&vfile,0,0,callbacks )<0 ){
		return false;
	}
	opened=true;

	vorbis_info *vi=ov_info( &vfile,-1 );
	if( !vi ) return false;

	channels=vi->channels;
	frequency=vi->rate;
	samples=(unsigned int)( ov_pcm_total( &vfile,-1 )*channels );

	return true;
}

void OGGAudioStream::seek( long pos ){
	ov_pcm_seek( &vfile,pos );
}

long OGGAudioStream::pos(){
	return ov_pcm_tell( &vfile );
}

size_t OGGAudioStream::decode(){
	int res,bs,bytes=0;
	while( bytes<buf_size ){
		res=ov_read( &vfile,(char *)buf+bytes,buf_size-bytes,ENDIAN,bits/8,SIGN,&bs );
		if( res==0 ) break;
		if( res==OV_HOLE ) continue;
		if( res<0 ) break;

		bytes+=res;
	}
	return bytes;
}

double OGGAudioStream::toSeconds( long pos ){
	if( !frequency ) return 0;
	return pos/(double)frequency;
}
