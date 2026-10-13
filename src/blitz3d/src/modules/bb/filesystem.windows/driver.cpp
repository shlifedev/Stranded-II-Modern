
#include <set>

#include "../stdutil/stdutil.h"
#include "driver.h"
#include "dir.h"
#include "../../../stdutil/windows_utf8.h"

static std::set<WindowsDir*> dir_set;

WindowsFileSystem::WindowsFileSystem(){
	dir_set.clear();
}

WindowsFileSystem::~WindowsFileSystem(){
	while( dir_set.size() ) closeDir( *dir_set.begin() );
}

bool WindowsFileSystem::createDir( const std::string &dir ){
	return CreateDirectoryW( bbWide(dir).c_str(),0 ) ? true : false;
}

bool WindowsFileSystem::deleteDir( const std::string &dir ){
	return RemoveDirectoryW( bbWide(dir).c_str() ) ? true : false;
}

bool WindowsFileSystem::createFile( const std::string &file ){
	return false;
}

bool WindowsFileSystem::deleteFile( const std::string &file ){
	return DeleteFileW( bbWide(file).c_str() ) ? true : false;
}

bool WindowsFileSystem::copyFile( const std::string &src,const std::string &dest ){
	return CopyFileW( bbWide(src).c_str(),bbWide(dest).c_str(),false ) ? true : false;
}

bool WindowsFileSystem::renameFile( const std::string &src,const std::string &dest ){
	return MoveFileW( bbWide(src).c_str(),bbWide(dest).c_str() ) ? true : false;
}

bool WindowsFileSystem::setCurrentDir( const std::string &dir ){
	return SetCurrentDirectoryW( bbWide(dir).c_str()) ? true : false;
}

std::string WindowsFileSystem::getCurrentDir()const{
	wchar_t buff[32768];
	if( !GetCurrentDirectoryW( 32768,buff ) ) return "";
	std::string t=bbUtf8(buff);if( t.size() && t[t.size()-1]!='\\' ) t+='\\';
	return t;
}

int WindowsFileSystem::getFileSize( const std::string &name )const{
	WIN32_FIND_DATAW findData;
	HANDLE h=FindFirstFileW( bbWide(name).c_str(),&findData );
	if( h==INVALID_HANDLE_VALUE ) return 0;
	int n=findData.dwFileAttributes,sz=findData.nFileSizeLow;
	FindClose( h );return n & FILE_ATTRIBUTE_DIRECTORY ? 0 : sz;
}

int WindowsFileSystem::getFileType( const std::string &name )const{
	DWORD t=GetFileAttributesW( bbWide(name).c_str() );
	return t==-1 ? FILE_TYPE_NONE :
	(t & FILE_ATTRIBUTE_DIRECTORY ? FILE_TYPE_DIR : FILE_TYPE_FILE);
}

BBDir *WindowsFileSystem::openDir( const std::string &name,int flags ){
	std::string t=name;
	if( !t.empty() && t.back()=='\\' ) t+="*";
	else t+="\\*";
	WIN32_FIND_DATAW f;
	HANDLE h=FindFirstFileW( bbWide(t).c_str(),&f );
	if( h!=INVALID_HANDLE_VALUE ){
		WindowsDir *d=d_new WindowsDir( h,f );
		dir_set.insert( d );
		return d;
	}
	return 0;
}
BBDir *WindowsFileSystem::verifyDir( BBDir *d ){
	return dir_set.count( (WindowsDir*)d ) ? d : 0;
}

void WindowsFileSystem::closeDir( BBDir *d ){
	if( dir_set.erase((WindowsDir*) d ) ) delete d;
}
