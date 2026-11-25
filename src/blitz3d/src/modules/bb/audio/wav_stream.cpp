
#include "wav_stream.h"
#include <cstdint>
#include <cstring>

WAVAudioStream::WAVAudioStream( int buf_size):AudioStream( buf_size),data_end(0){
}

namespace {
uint32_t little32( const unsigned char *p ){
	return uint32_t(p[0]) | (uint32_t(p[1])<<8) | (uint32_t(p[2])<<16) | (uint32_t(p[3])<<24);
}
}

bool WAVAudioStream::readHeader(){
	unsigned char header[16];
	if( read(header,12)!=12 || std::memcmp(header,"RIFF",4) || std::memcmp(header+8,"WAVE",4) ) return false;
	in.seekg(0,std::ios_base::end);
	const std::streamoff end=in.tellg();
	in.seekg(12,std::ios_base::beg);

	bool have_format=false;
	while( read(header,8)==8 ){
		const std::streamoff size=little32(header+4);
		const std::streamoff start=in.tellg();
		if( size>end-start ) return false;
		if( !std::memcmp(header,"fmt ",4) ){
			if( size<16 || read(header,16)!=16 ) return false;
			if( header[0]!=1 || header[1]!=0 ) return false;
			channels=header[2] | (header[3]<<8);
			frequency=little32(header+4);
			bits=header[14] | (header[15]<<8);
			if( !frequency || (channels!=1 && channels!=2) || (bits!=8 && bits!=16) ) return false;
			have_format=true;
		}else if( !std::memcmp(header,"data",4) ){
			if( !have_format ) return false;
			samples=static_cast<unsigned int>(size)/(bits/8);
			data_end=start+size;
			return true;
		}
		// All RIFF chunks, including extended fmt chunks, end on an even boundary.
		in.seekg(start+size+(size&1),std::ios_base::beg);
	}
	return false;
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
