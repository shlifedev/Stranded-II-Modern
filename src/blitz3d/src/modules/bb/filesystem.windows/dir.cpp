
#include <windows.h>
#include "dir.h"
#include "../../../stdutil/windows_utf8.h"

WindowsDir::WindowsDir( HANDLE h,const WIN32_FIND_DATAW &f ):handle(h),findData(f){
}

WindowsDir::~WindowsDir(){
	if( handle!=INVALID_HANDLE_VALUE ) FindClose( handle );
}

std::string WindowsDir::getNextFile(){
	if( handle==INVALID_HANDLE_VALUE ) return "";
	std::string t=bbUtf8(findData.cFileName);
	if( !FindNextFileW( handle,&findData ) ){
		FindClose( handle );
		handle=INVALID_HANDLE_VALUE;
	}
	return t;
}
