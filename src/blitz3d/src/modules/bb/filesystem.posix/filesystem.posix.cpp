
#include "../stdutil/stdutil.h"
#include "filesystem.posix.h"

#include <sys/types.h>
#include <sys/stat.h>
#include <dirent.h>
#include <unistd.h>
#include <cstdio>

class PosixDir : public BBDir{
public:
	PosixDir( DIR *dp ):dp(dp){
	}

	~PosixDir(){
		closedir( dp );
	}

private:
	DIR *dp;

public:
	std::string getNextFile(){
		struct dirent *ep=readdir( dp );
		if( ep ){
			return std::string( ep->d_name );
		}
		return "";
	}
};


PosixFileSystem::PosixFileSystem(){
}

PosixFileSystem::~PosixFileSystem(){
}

bool PosixFileSystem::createDir( const std::string &dir ){
	std::string t=canonicalpath( dir );
	if( t.size() && t.back()=='/' ) t=t.substr( 0,t.size()-1 );
	if( t.empty() ) return false;
	for( size_t i=1;i<=t.size();i++ ){
		if( i==t.size() || t[i]=='/' ){
			std::string part=t.substr( 0,i );
			mkdir( part.c_str(),0777 );
		}
	}
	struct stat st; return stat( t.c_str(),&st )==0 && S_ISDIR( st.st_mode );
}

bool PosixFileSystem::deleteDir( const std::string &dir ){
	std::string t=canonicalpath( dir );
	if( t.size() && t.back()=='/' ) t=t.substr( 0,t.size()-1 );
	return rmdir( t.c_str() )==0;
}

bool PosixFileSystem::createFile( const std::string &file ){
	std::string t=canonicalpath( file );
	FILE *f=fopen( t.c_str(),"ab" );
	if( f ){ fclose( f ); return true; }
	return false;
}

bool PosixFileSystem::deleteFile( const std::string &file ){
	std::string t=canonicalpath( file );
	return remove( t.c_str() )==0;
}

bool PosixFileSystem::copyFile( const std::string &src,const std::string &dest ){
	std::string s=canonicalpath( src ),d=canonicalpath( dest );
	FILE *in=fopen( s.c_str(),"rb" );
	if( !in ) return false;
	FILE *out=fopen( d.c_str(),"wb" );
	if( !out ){ fclose( in );return false; }
	char buff[16384];
	size_t n;bool ok=true;
	while( (n=fread( buff,1,sizeof(buff),in ))>0 ){
		if( fwrite( buff,1,n,out )!=n ){ ok=false;break; }
	}
	if( ferror( in ) ) ok=false;
	fclose( in );
	if( fclose( out )!=0 ) ok=false;
	return ok;
}

bool PosixFileSystem::renameFile( const std::string &src,const std::string &dest ){
	std::string s=canonicalpath( src ),d=canonicalpath( dest );
	struct stat st;
	if( stat( d.c_str(),&st )==0 ) return false;
	return rename( s.c_str(),d.c_str() )==0;
}

bool PosixFileSystem::setCurrentDir( const std::string &dir ){
	std::string t=canonicalpath( dir );
	return chdir( t.c_str() )==0;
}

std::string PosixFileSystem::getCurrentDir()const{
	char buff[PATH_MAX];
	if( getcwd( buff,sizeof(buff) ) ){
		std::string t=buff;
		if( t.size() && t[t.size()-1]!='/' ) t+='/';
		return t;
	}
	return "";
}

int PosixFileSystem::getFileSize( const std::string &name )const{
	std::string t=canonicalpath(name);
	struct stat fstat;
	if( stat( t.c_str(),&fstat )==0 ){
		return fstat.st_size;
	}
	return 0;
}

int PosixFileSystem::getFileType( const std::string &name )const{
	std::string t=canonicalpath(name);
	struct stat fstat;
	if( stat( t.c_str(),&fstat )==0 ){
		if( S_ISREG( fstat.st_mode ) ){
			return 1;
		}else if( S_ISDIR( fstat.st_mode ) ){
			return 2;
		}
	}

	return 0;
}

BBDir *PosixFileSystem::openDir( const std::string &name,int flags ){
	std::string t=canonicalpath( name );
	if( t.empty() ) return 0;
	if( t.back()=='/' ) t=t.substr( 0,t.size()-1 );

	DIR *dp = opendir( t.c_str() );
	if( !dp ) return 0;

	return d_new PosixDir( dp );
}

BBDir *PosixFileSystem::verifyDir( BBDir *d ){
	return d;
}

void PosixFileSystem::closeDir( BBDir *dir ){
	delete dir;
}

BBMODULE_CREATE( filesystem_posix ){
	if( !gx_filesys ){
		gx_filesys=d_new PosixFileSystem();
	}
	return true;
}

BBMODULE_DESTROY( filesystem_posix ){
	return true;
}
