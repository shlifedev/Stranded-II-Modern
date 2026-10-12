#ifndef BB_GRAPHICS_GL_H
#define BB_GRAPHICS_GL_H

#include <bb/graphics/graphics.h>
#include <map>

#ifdef BB_DESKTOP
#include <GL/glew.h>
#endif

#ifdef BB_MACOS
	#include <OpenGL/gl.h>
	#include <OpenGL/glext.h>
#else
	#define GL_GLEXT_PROTOTYPES
	#include <GL/gl.h>
#endif

GLuint _bbGLCompileProgram( const std::string &name,const std::string &src );

void bbGLGraphicsCheckErrors( const char *file, int line );
const char * bbGLFramebufferStatusString( GLenum status );

#if (defined(NDEBUG) || defined(BB_GL_NOCHECK)) && !defined(BB_GL_FORCE_CHECK)
#define GL( func ) func;
#else
#define GL( func ) func; bbGLGraphicsCheckErrors( __FILE__,__LINE__ );
#endif

struct ContextResources{
	unsigned int ubo;
	GLuint default_program;
	GLuint plot_buffer,plot_array;
	GLuint line_buffer,line_array;
	GLuint quad_buffer[2],quad_array[2];
	GLuint oval_buffer,oval_array;
	GLuint text_buffer,text_array;
	std::map<BBImageFont*,unsigned int> font_textures;
};

#include "canvas.h"

class GLGraphics:public BBGraphics{
protected:
	BBImageFont *def_font;

	GLCanvas fb, bb;
public:
	ContextResources res={ 0 };

	GLGraphics();

	bool init();

	BBFont *getDefaultFont()const;

	BBCanvas *createCanvas( int width,int height,int flags );
	BBCanvas *loadCanvas( const std::string &file,int flags );

	BBMovie *openMovie( const std::string &file,int flags );
};


#endif
