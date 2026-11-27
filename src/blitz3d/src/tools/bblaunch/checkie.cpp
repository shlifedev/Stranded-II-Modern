
#include "checkie.h"
#include <windows.h>

int getIEVersion(){
	int version=0;

	HKEY key=HKEY_LOCAL_MACHINE;
	if( RegOpenKey( key,L"Software\\Microsoft\\Internet Explorer",&key )==ERROR_SUCCESS ){
		char buff[MAX_PATH];DWORD type,sz=MAX_PATH;
		if( RegQueryValueEx( key,L"Version",0,&type,(PBYTE)buff,&sz )==ERROR_SUCCESS ){
			version=4;
		}
		RegCloseKey( key );
	}
	return version;
}
