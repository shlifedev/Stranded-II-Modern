
#include "wav_stream.h"
#include <string.h>
#include <bb/blitz/app.h>

WAVAudioStream::WAVAudioStream( int buf_size):AudioStream( buf_size),data_end(0){
}

bool WAVAudioStream::readHeader(){
	read( buf, 4 );
	if( buf[0]!='R' || buf[1]!='I' || buf[2]!='F' || buf[3]!='F' ){
		LOGD( "%s","missing RIFF" );
		return false;
	}

	read( buf, 4 );

	read( buf, 4 );
	if( buf[0]!='W' || buf[1]!='A' || buf[2]!='V' || buf[3]!='E' ){
		LOGD( "%s","missing WAVE" );
		return false;
	}

	read( buf,4 );
	if( buf[0]!='f' || buf[1]!='m' || buf[2]!='t' || buf[3]!=' ' ){
		LOGD( "%s","missing 'fmt '" );
		return false;
	}

	read( buf,4 );
	unsigned int fmt_len=buf[0]|(buf[1]<<8)|(buf[2]<<16)|((unsigned int)buf[3]<<24);

	read( buf,2 );
	if( buf[1]!=0 || buf[0]!=1 ){
		LOGD( "unsupported format type %i %i",buf[0],buf[1] );
		return false;
	}

	read( buf,2 );
	channels  = buf[1]<<8;
	channels |= buf[0];

	read( buf,4 );
	frequency  = buf[3]<<24;
	frequency |= buf[2]<<16;
	frequency |= buf[1]<<8;
	frequency |= buf[0];

	read( buf,4 );

	read( buf,2 );

	read( buf,2 );
	bits  = buf[1]<<8;
	bits |= buf[0];

	if( fmt_len>16 ) in.seekg( fmt_len-16,std::ios_base::cur );

	for( int guard=0;;guard++ ){
		if( read( buf,4 )<4 || guard>=64 ){
			LOGD( "%s","missing 'data'" );
			return false;
		}
		bool is_data = buf[0]=='d' && buf[1]=='a' && buf[2]=='t' && buf[3]=='a';
		read( buf,4 );
		unsigned int size=buf[0]|(buf[1]<<8)|(buf[2]<<16)|((unsigned int)buf[3]<<24);
		if( is_data ){
			if( bits>=8 ) samples=size/(bits/8);
			data_end=(std::streamoff)in.tellg()+(std::streamoff)size;
			break;
		}
		if( size&1 ) size++;
		in.seekg( size,std::ios_base::cur );
	}

	return true;
}

void WAVAudioStream::seek( long pos ){
	in.clear();
	in.seekg( pos,std::ios_base::beg );
}

long WAVAudioStream::pos(){
	return in.tellg();
}

size_t WAVAudioStream::decode(){
	std::streamoff p=in.tellg();
	if( p<0 || p>=data_end ) return 0;
	size_t n=buf_size;
	if( data_end-p<(std::streamoff)n ) n=(size_t)(data_end-p);
	return read( buf,n );
}

double WAVAudioStream::toSeconds( long pos ){
	unsigned int bytes_per_frame=channels*(bits/8);
	if( !bytes_per_frame || !frequency ) return 0;
	long off=pos-(long)start;
	if( off<0 ) off=0;
	return off/(double)bytes_per_frame/(double)frequency;
}
