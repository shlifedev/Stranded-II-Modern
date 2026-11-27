#ifndef FRAME_FRAME_H
#define FRAME_FRAME_H

#include <windows.h>
#include <string>

class Frame{
public:
  Frame( HWND hwnd );
public:
  HWND hwnd;

protected:
  int gfx_mode;

  RECT t_rect;
  int t_style;

	bool pointer_visible;

  void backupWindowState();
  void restoreWindowState();

  void resize( int w,int h );
  void fullscreen();

  static void _refreshTitle( void *data,void *context );
public:
  void setTitle( const std::string &title );
	void setPointerVisible( bool vis );
};

#endif
