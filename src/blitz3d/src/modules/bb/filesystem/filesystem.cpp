
#include "../stdutil/stdutil.h"
#include "filesystem.h"
#include <bb/stream/stream.h>
#include <fstream>
#include <streambuf>
#include <string>
#include <set>
#include <cstring>
#ifdef BB_WINDOWS
#include "../../../stdutil/windows_utf8.h"
#else
#include <dirent.h>
#endif
#include <sys/stat.h>

static std::string bbResolvePath( std::string want ){
	if( want.empty() ) return "";
	for( char &c : want ) if( c=='\\' ) c='/';
#ifdef BB_WINDOWS
    std::error_code error;
    return std::filesystem::exists(bbWidePath(want),error)?want:"";
#else
	{ struct stat st; if( stat( want.c_str(),&st )==0 ) return want; }
	std::string cur = want.size() && want[0]=='/' ? "/" : ".";
	size_t i = (want.size() && want[0]=='/') ? 1 : 0;
	while( i < want.size() ){
		size_t slash = want.find( '/',i );
		std::string comp = want.substr( i, slash==std::string::npos ? std::string::npos : slash-i );
		if( comp.empty() || comp=="." ){ if(slash==std::string::npos)break; i=slash+1; continue; }
		std::string next = (cur=="/"?"":cur) + "/" + comp;
		struct stat st;
		if( stat( next.c_str(),&st )!=0 ){
			DIR *d = opendir( cur.c_str() );
			bool found=false;
			if( d ){
				struct dirent *e;
				while( (e=readdir(d)) ){
					if( strcasecmp( e->d_name,comp.c_str() )==0 ){
						next = (cur=="/"?"":cur) + "/" + e->d_name; found=true; break;
					}
				}
				closedir( d );
			}
			if( !found ) return "";
		}
		cur = next;
		if( slash==std::string::npos ) break;
		i = slash+1;
	}
	return cur;
#endif
}

BBFileSystem *gx_filesys;

std::string bbResolveExistingPath( const std::string &want ){
	return bbResolvePath( want );
}

BBDir::~BBDir(){
}

BBFileSystem::~BBFileSystem(){
}

std::streambuf *BBFileSystem::openFile( const std::string &file,std::ios_base::openmode n ){
	std::string path=file;
	for( char &c : path ) if( c=='\\' ) c='/';
	std::filebuf *buf=d_new std::filebuf();
#ifdef BB_WINDOWS
    if(buf->open(bbWidePath(path),n|std::ios_base::binary)) return buf;
    // The retry must not fall back to an ANSI name: for a write it could
    // create a mojibake file beside the intended one.
    std::string real=bbResolvePath(file);
    if(!real.empty() && buf->open(bbWidePath(real),n|std::ios_base::binary)) return buf;
#else
	if( buf->open( path.c_str(),n|std::ios_base::binary ) ) return buf;
	std::string real = bbResolvePath( file );
	if( !real.empty() && buf->open( real.c_str(),n|std::ios_base::binary ) ) return buf;
#endif
	delete buf;
	return 0;
}

struct BBFile : public BBStream{
	std::streambuf *buf;
	BBFile( std::streambuf *f ):buf(f){
	}
	~BBFile(){
		delete buf;
	}
	int read( char *buff,int size ){
		return buf->sgetn( (char*)buff,size );
	}
	int write( const char *buff,int size ){
		return buf->sputn( (char*)buff,size );
	}
	int avail(){
		return buf->in_avail();
	}
	int eof(){
		return buf->sgetc()==EOF;
	}
};

static std::set<BBFile*> file_set;

static inline void debugFileSys(){
	if( bb_env.debug ){
		if( !gx_filesys ) RTEX( "Filesystem does not exist" );
	}
}

static inline void debugFile( BBFile *f ){
	if( bb_env.debug ){
		if( f && !file_set.count( f ) ) RTEX( "File does not exist" );
	}
}

static inline void debugDir( BBDir *d ){
	if( bb_env.debug ){
		if( !gx_filesys->verifyDir( d ) ) RTEX( "Directory does not exist" );
	}
}

static BBFile *open( BBStr *f,std::ios_base::openmode n ){
	std::string t=canonicalpath( *f );
	delete f;
	std::streambuf *buf=gx_filesys->openFile( t,n );
	if( buf ){
		BBFile *f=d_new BBFile( buf );
		file_set.insert( f );
		return f;
	}
	return 0;
}

BBFile* BBCALL bbReadFile( BBStr *f ){
	return open( f,std::ios_base::in );
}

BBFile* BBCALL bbWriteFile( BBStr *f ){
	return open( f,std::ios_base::out|std::ios_base::trunc );
}

BBFile* BBCALL bbOpenFile( BBStr *f ){
	return open( f,std::ios_base::in|std::ios_base::out );
}

void BBCALL bbCloseFile( BBFile *f ){
	debugFile( f );
	if( !f ) return;
	file_set.erase( f );
	delete f;
}

bb_int_t BBCALL bbFilePos( BBFile *f ){
	if( !f ) return 0;
	return f->buf->pubseekoff( 0,std::ios_base::cur );
}

bb_int_t BBCALL bbSeekFile( BBFile *f,bb_int_t pos ){
	if( !f ) return 0;
	return f->buf->pubseekoff( pos,std::ios_base::beg );
}

BBDir* BBCALL bbReadDir( BBStr *d ){
	std::string t=*d;delete d;
	std::string real=bbResolvePath( t );
	if( real.empty() ){ real=t; for( char &c : real ) if( c=='\\' ) c='/'; }
	return gx_filesys->openDir( real,0 );
}

void BBCALL bbCloseDir( BBDir *d ){
	gx_filesys->closeDir( d );
}

BBStr* BBCALL bbNextFile( BBDir *d ){
	if( !d ) return d_new BBStr( "" );
	debugDir( d );
	return d_new BBStr( d->getNextFile() );
}

BBStr* BBCALL bbCurrentDir(){
	debugFileSys();
	return d_new BBStr( gx_filesys->getCurrentDir() );
}

void BBCALL bbChangeDir( BBStr *d ){
	debugFileSys();
	gx_filesys->setCurrentDir( *d );
	delete d;
}

void BBCALL bbCreateDir( BBStr *d ){
	debugFileSys();
	gx_filesys->createDir( *d );
	delete d;
}

void BBCALL bbDeleteDir( BBStr *d ){
	debugFileSys();
	gx_filesys->deleteDir( *d );
	delete d;
}

bb_int_t BBCALL bbFileType( BBStr *f ){
	std::string t=*f;delete f;
	debugFileSys();
	int n=gx_filesys->getFileType( t );
	if( n==BBFileSystem::FILE_TYPE_NONE ){
		std::string real=bbResolvePath( t );
		if( !real.empty() ) n=gx_filesys->getFileType( real );
	}
	return n==BBFileSystem::FILE_TYPE_FILE ? 1 : (n==BBFileSystem::FILE_TYPE_DIR ? 2 : 0);
}

bb_int_t BBCALL bbFileSize( BBStr *f ){
	std::string t=*f;delete f;
	debugFileSys();
	bb_int_t sz=gx_filesys->getFileSize( t );
	if( sz<=0 ){ std::string real=bbResolvePath( t ); if( !real.empty() ) sz=gx_filesys->getFileSize( real ); }
	return sz;
}

void BBCALL bbCopyFile( BBStr *f,BBStr *to ){
	std::string src=*f,dest=*to;
	delete f;delete to;
	debugFileSys();
	gx_filesys->copyFile( src,dest );
}

void BBCALL bbDeleteFile( BBStr *f ){
	debugFileSys();
	gx_filesys->deleteFile( *f );
	delete f;
}

BBMODULE_CREATE( filesystem ){
	gx_filesys=0;
	return true;
}

BBMODULE_DESTROY( filesystem ){
	if( gx_filesys ){
		while( file_set.size() ) bbCloseFile( *file_set.begin() );

		delete gx_filesys;
		gx_filesys=0;
	}
	return true;
}
