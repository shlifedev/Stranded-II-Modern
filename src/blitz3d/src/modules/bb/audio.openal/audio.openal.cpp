
#include "../stdutil/stdutil.h"
#include "audio.openal.h"
#include <bb/audio/ogg_stream.h>
#include <bb/audio/wav_stream.h>
#include <bb/audio/mp3_stream.h>

#ifdef BB_MACOS
#include <OpenAL/al.h>
#include <OpenAL/alc.h>
#else
#include <AL/al.h>
#include <AL/alc.h>
#endif

#include <string.h>
#ifdef BB_WINDOWS
#define strcasecmp _stricmp
#endif
#include <stdio.h>
#include <cmath>
#include <set>
#include <vector>

#ifdef BB_EMSCRIPTEN
#include <emscripten.h>
#else
#include <chrono>
static double emscripten_get_now(){ return std::chrono::duration<double,std::milli>(std::chrono::steady_clock::now().time_since_epoch()).count(); }
#endif

#define NUM_BUFFERS 8
#define BUFFER_SIZE 32768
#define EM_PUMP_NORMAL_MS 30
#define EM_PUMP_URGENT_MS 10

class OpenALChannel;

static std::set<OpenALChannel*> channel_set;
static std::vector<OpenALChannel*> idle_channels;
static std::set<OpenALChannel*> em_streams;
static bool em_pump_scheduled=false;
static double sound_load_ms=0;
static int sound_load_count=0;

static float g_rolloff=1.0f;
static float g_distscale=1.0f;

static void em_register_stream( OpenALChannel *c );
static void em_unregister_stream( OpenALChannel *c );

class OpenALChannel : public BBChannel{
public:
	AudioStream::Ref *stream;
	AudioStream *src;

	ALuint source,buffers[NUM_BUFFERS];
	ALenum format;

	bool playbackRunning;
	bool paused;
	bool looping;
	bool own_src;

	OpenALChannel():stream(0),src(0),source(0),format(0),playbackRunning(false),paused(false),looping(false),own_src(false){}

	~OpenALChannel(){
		playbackRunning=false;
		em_unregister_stream( this );
		release();
	}

	bool setStream( AudioStream *s ){
		stream=s->getRef();
		src=s;

		unsigned int bits=stream->getBits(),channels=stream->getChannels();

		format=0;
		if( bits==8 ){
			if( channels==1 )
				format=AL_FORMAT_MONO8;
			else if( channels==2 )
				format=AL_FORMAT_STEREO8;
		}else if( bits==16 ){
			if( channels==1 )
				format=AL_FORMAT_MONO16;
			else if( channels==2 )
				format=AL_FORMAT_STEREO16;
		}
		// Callers release the channel and hand back a null channel, like a failed load.
		if( format==0 ) return false;

		alGetError();
		alGenBuffers( NUM_BUFFERS,buffers );
		alGenSources( 1,&source );

		return alGetError()==AL_NO_ERROR;
	}

	bool queue( ALuint buffer ){
		if( !source || !stream ) return false;
		unsigned char *buf;
		size_t size=stream->decode( &buf );
		if( size==0 && looping ){
			stream->pos=stream->stream->getStart();
			size=stream->decode( &buf );
		}
		if( size==0 ) return false;

		alBufferData( buffer,format,buf,size,stream->getFrequency() );
		alSourceQueueBuffers( source,1,&buffer );
		return true;
	}

	bool streaming(){
		return playbackRunning && stream && !stream->eof();
	}

	void detachStream(){
		playbackRunning=false;
		looping=false;
		if( source ) alSourceStop( source );
		delete stream;
		stream=0;
		src=0;
	}

	void release(){
		streamEnd();
		delete stream;
		stream=0;
		if( own_src ) delete src;
		src=0;
		own_src=false;
		paused=false;
		looping=false;
	}

	void play(){
		if( !playbackRunning ){
			playbackRunning=true;
			streamStart();
			em_register_stream( this );
		}
	}

	bool streamStart(){
		if( !source ){ playbackRunning=false; return false; }
		alGetError();
		int primed=0;
		for( int i=0;i<NUM_BUFFERS;i++ ){
			if( !queue( buffers[i] ) ) break;
			primed++;
		}
		if( primed==0 || alGetError()!=AL_NO_ERROR ){ playbackRunning=false; return false; }

		alSourcePlay( source );
		if( alGetError()!=AL_NO_ERROR ){ playbackRunning=false; return false; }
		return true;
	}

	bool streamPump( int *refilled=0 ){
		if( refilled ) *refilled=0;
		if( !source ) return false;
		ALint val;
		if( paused && playbackRunning ) return true;
		alGetError();
		if( streaming() ){
			ALuint buffer;
			bool ateof=false;
			alGetSourcei( source,AL_BUFFERS_PROCESSED,&val );
			while( val-->0 ){
				alSourceUnqueueBuffers( source,1,&buffer );
				if( ateof ) continue;
				if( queue( buffer )==false ){ ateof=true; continue; }
				if( alGetError()!=AL_NO_ERROR ){ LOGD( "%s","error buffering..." ); break; }
				if( refilled ) ++*refilled;
			}
			alGetSourcei( source,AL_SOURCE_STATE,&val );
			if( val!=AL_PLAYING ) alSourcePlay( source );
			return true;
		}
		alGetSourcei( source,AL_SOURCE_STATE,&val );
		return val==AL_PLAYING;
	}

	void streamEnd(){
		playbackRunning=false;
		if( source ){
			alDeleteSources( 1,&source );
			alDeleteBuffers( NUM_BUFFERS,buffers );
			source=0;
		}
	}

	void stop(){
		if( !playbackRunning ) return;
		playbackRunning=false;
		looping=false;
		if( source ) alSourceStop( source );
	}
	void setPaused( bool p ){
		if( !source || !playbackRunning || p==paused ) return;
		paused=p;
		if( p ){
			alSourcePause( source );
		}else{
			ALint state;
			alGetSourcei( source,AL_SOURCE_STATE,&state );
			if( state==AL_PAUSED ) alSourcePlay( source );
		}
	}
	void setPitch( int pitch ){
		if( !source || !stream ) return;
		unsigned int hz=stream->getFrequency();
		if( hz>0 && pitch>0 ) alSourcef( source,AL_PITCH,(float)pitch/(float)hz );
	}
	void setVolume( float volume ){
		if( !source ) return;
		if( volume<0.0f ) volume=0.0f;
		if( volume>1.0f ) volume=1.0f;
		alSourcef( source,AL_GAIN,volume );
	}
	void setPan( float pan ){
		if( !source ) return;
		if( pan<-1.0f ) pan=-1.0f; if( pan>1.0f ) pan=1.0f;
		alSourcei( source,AL_SOURCE_RELATIVE,AL_TRUE );
		float z=-sqrtf( 1.0f-pan*pan );
		alSource3f( source,AL_POSITION,pan,0.0f,z );
	}
	void set2d(){
		if( !source ) return;
		alSourcei( source,AL_SOURCE_RELATIVE,AL_TRUE );
		alSource3f( source,AL_POSITION,0.0f,0.0f,0.0f );
		alSourcef( source,AL_ROLLOFF_FACTOR,0.0f );
	}
	void set3d( const float pos[3],const float vel[3] ){
		if( !source ) return;
		float p[3]={ pos[0],pos[1],-pos[2] };
		float v[3]={ vel[0],vel[1],-vel[2] };

		alSourcef( source,AL_ROLLOFF_FACTOR,g_rolloff );
		alSourcef( source,AL_REFERENCE_DISTANCE,g_distscale>0.0f ? 1.0f/g_distscale : 1.0f );

		alSourcefv( source,AL_POSITION,p );
		alSourcefv( source,AL_VELOCITY,v );
		alSourcei( source,AL_SOURCE_RELATIVE,AL_FALSE );
	}
	bool isPlaying(){
		return playbackRunning;
	}
	float getDuration(){
		if( !stream || stream->getChannels()==0 || stream->getFrequency()==0 ) return 0;
		return (stream->getSamples() / (float)stream->getChannels()) / (float)stream->getFrequency();
	}
	float getPosition(){
		if( !stream ) return 0;
		double s=stream->seconds();
		if( s<0 ) return getDuration();
		return (float)s;
	}
};

static OpenALChannel *allocChannel(){
	if( !idle_channels.empty() ){
		OpenALChannel *c=idle_channels.back();
		idle_channels.pop_back();
		return c;
	}
	OpenALChannel *c=d_new OpenALChannel();
	channel_set.insert( c );
	return c;
}

static void em_pump( void* );

static void em_schedule( int ms ){
#ifdef BB_EMSCRIPTEN
	if( !em_pump_scheduled ){
		em_pump_scheduled=true;
		emscripten_async_call( em_pump,0,ms );
	}
#endif
}

static double em_pump_total_ms=0,em_pump_max_ms=0,em_pump_report=0;
static int em_pump_count=0;

static void em_pump( void* ){
	em_pump_scheduled=false;
	double pump_started=emscripten_get_now();
	alGetError();
	bool urgent=false;
	for( auto it=em_streams.begin(); it!=em_streams.end(); ){
		OpenALChannel *c=*it;
		int refilled=0;
		if( !c->streamPump( &refilled ) ){
			c->release();
			idle_channels.push_back( c );
			it=em_streams.erase( it );
		}else{
			if( refilled>NUM_BUFFERS/2 ) urgent=true;
			++it;
		}
	}
	if( !em_streams.empty() ) em_schedule( urgent ? EM_PUMP_URGENT_MS : EM_PUMP_NORMAL_MS );

	double now=emscripten_get_now();
	double spent=now-pump_started;
	em_pump_total_ms+=spent;
	if( spent>em_pump_max_ms ) em_pump_max_ms=spent;
	++em_pump_count;
	if( now-em_pump_report>=10000.0 ){
		if( em_pump_count>0 && em_pump_report>0 ){
			printf( "[perf] audio pump calls=%d avg=%.2fms max=%.2fms\n",em_pump_count,em_pump_total_ms/em_pump_count,em_pump_max_ms );
		}
		em_pump_report=now;
		em_pump_total_ms=0;
		em_pump_max_ms=0;
		em_pump_count=0;
	}
}

#ifndef BB_EMSCRIPTEN
void s2NativePumpAudio(){
 static double last=0;
 double now=emscripten_get_now();
 if(now-last>=10){last=now;em_pump(nullptr);}
}
#endif

static void em_register_stream( OpenALChannel *c ){
	em_streams.insert( c );
	em_schedule( EM_PUMP_NORMAL_MS );
}

static void em_unregister_stream( OpenALChannel *c ){
	em_streams.erase( c );
}

class OpenALSound : public BBSound{
public:
	AudioStream *stream;
	bool loop;
	int def_pitch;
	float def_volume,def_pan;

	OpenALSound():stream(0),loop(false),def_pitch(0),def_volume(-1.0f),def_pan(0.0f){
	}

	~OpenALSound(){
		for( std::set<OpenALChannel*>::iterator it=channel_set.begin();it!=channel_set.end();++it ){
			OpenALChannel *c=*it;
			if( c->src==stream ) c->detachStream();
		}
		delete stream;
		stream=0;
	}

	bool setStream( AudioStream *s ){
		stream=s;
		return true;
	}

	void applyDefaults( OpenALChannel *channel ){
		channel->looping=loop;
		if( def_volume>=0.0f ) channel->setVolume( def_volume );
		if( def_pitch>0 ) channel->setPitch( def_pitch );
		if( def_pan!=0.0f ) channel->setPan( def_pan );
	}

	BBChannel *play(){
		OpenALChannel *channel=allocChannel();
		if( !channel->setStream( stream ) ){
			channel->release();
			idle_channels.push_back( channel );
			return 0;
		}
		channel->set2d();
		applyDefaults( channel );
		channel->play();
		return channel;
	}

	BBChannel *play3d( const float pos[3],const float vel[3] ){
		OpenALChannel *channel=allocChannel();
		if( !channel->setStream( stream ) ){
			channel->release();
			idle_channels.push_back( channel );
			return 0;
		}
		applyDefaults( channel );
		channel->set3d( pos,vel );
		channel->play();
		return channel;
	}

	void setLoop( bool l ){
		loop=l;
	}

	void setPitch( int hertz ){
		def_pitch=hertz;
	}

	void setVolume( float volume ){
		def_volume=volume;
	}

	void setPan( float pan ){
		def_pan=pan;
	}
};

class OpenALAudioDriver : public BBAudioDriver{
protected:
	ALCdevice *dev;
	ALCcontext *ctx;

	AudioStream *loadStream( const std::string &filename ){
		const char *ext=strrchr( filename.c_str(),'.' );
		if( !ext ) return 0;

		AudioStream *stream=0;
		const char *exts[]={ ext,strcasecmp( ext+1,"wav" )==0?".ogg":".wav",0 };
		int tries=0;
		while( exts[tries] ){
			if( strcasecmp( exts[tries]+1,"wav" )==0 ){
				stream=d_new WAVAudioStream( BUFFER_SIZE );
			}else if( strcasecmp( exts[tries]+1,"ogg" )==0 ){
				stream=d_new OGGAudioStream( BUFFER_SIZE );
			}else if( strcasecmp( exts[tries]+1,"mp3" )==0 ){
				stream=d_new MP3AudioStream( BUFFER_SIZE );
			}

			if( !stream ) return 0;

			if( stream->init( filename.c_str() ) ){
				break;
			}else{
				delete stream;
				stream=0;
			}

			tries++;
		}

		return stream;
	}

public:
	OpenALAudioDriver():dev(0),ctx(0){
	}

	~OpenALAudioDriver(){
		idle_channels.clear();
		while( channel_set.size() ){
			OpenALChannel *c=*channel_set.begin();
			channel_set.erase( channel_set.begin() );
			delete c;
		}
		while( sound_set.size() ) freeSound( *sound_set.begin() );
		alcMakeContextCurrent( NULL );
		if( ctx ){ alcDestroyContext( ctx );ctx=0; }
		if( dev ){ alcCloseDevice( dev );dev=0; }
	}

	bool init(){
		if( !(dev=alcOpenDevice(NULL)) ){
			return false;
		}
		if( !(ctx=alcCreateContext(dev,NULL)) ){
			return false;
		}
		alcMakeContextCurrent( ctx );

		alDistanceModel( AL_INVERSE_DISTANCE_CLAMPED );

		return true;
	}

	BBSound *loadSound( const std::string &filename,bool use_3d ){
		double started=emscripten_get_now();
		AudioStream *stream=loadStream( filename );
		if( !stream ){
			return 0;
		}

		OpenALSound *sound=d_new OpenALSound();
		sound->setStream( stream );
		sound_set.insert( sound );

		sound_load_ms+=emscripten_get_now()-started;
		if( ++sound_load_count%50==0 ){
			printf( "[perf] sounds loaded=%d total=%.0fms\n",sound_load_count,sound_load_ms );
		}
		return sound;
	}

	void setPaused( bool paused ){
	}
	void setVolume( float volume ){
	}

	void set3dOptions( float roll,float dopp,float dist ){
		alDopplerFactor( dopp );
		g_rolloff=roll;
		g_distscale=dist;
	}

	void set3dListener( const float pos[3],const float vel[3],const float forward[3],const float up[3] ){
		float p[3]={ pos[0],pos[1],-pos[2] };
		float v[3]={ vel[0],vel[1],-vel[2] };
		float o[6]={ forward[0],forward[1],-forward[2],up[0],up[1],-up[2] };

		alListenerfv( AL_POSITION,p );
		alListenerfv( AL_VELOCITY,v );
		alListenerfv( AL_ORIENTATION,o );
	}

	BBChannel *playCDTrack( int track,int mode ){
		RTEX( "PlayCDTrack not implemented" );
	}

	BBChannel *playFile( const std::string &filename,bool use_3d,bool loop=false ){
		AudioStream *stream=loadStream( filename );
		if( !stream ){
			return 0;
		}

		OpenALChannel *channel=allocChannel();
		if( !channel->setStream( stream ) ){
			channel->release();
			delete stream;
			idle_channels.push_back( channel );
			return 0;
		}
		channel->own_src=true;
		channel->looping=loop;
		channel->set2d();
		channel->play();
		return channel;
	}
};

static OpenALAudioDriver *driver;

BBMODULE_CREATE( audio_openal ){
	driver=d_new OpenALAudioDriver();
	if( !driver->init() ){
		delete driver;
		driver=0;
		return true;
	}
	gx_audio=driver;
	return true;
}

BBMODULE_DESTROY( audio_openal ){
	return true;
}
