#ifdef BB_LEGACY32
#include <bb/blitz/legacy32.h>
#endif
#include "../stdutil/stdutil.h"
#include "font.h"
#include <bb/filesystem/filesystem.h>
#include <utf8.h>
#include <cstring>

struct BBFontData{
	long size;
	unsigned char *data;

	BBFontData():size(0),data(0){}
};

FT_Library ft;
std::map<std::string,BBFontData> bbFontCache;

BBFont::~BBFont(){
#ifdef BB_LEGACY32
	s2ForgetPointer(this);
#endif
}

BBImageFont::BBImageFont( FT_Face f,int h,float d ):atlas(0),dirty(false),height(h),face(f){
	if( !(d>0.0f) ) d=1.0f;

	if( !FT_IS_SCALABLE( face ) && face->num_fixed_sizes>0 ){
		int want=(int)(height*d),best=0;
		long diff=0x7fffffff;
		for( int i=0;i<face->num_fixed_sizes;i++ ){
			long dd=labs( (long)(face->available_sizes[i].y_ppem>>6)-want );
			if( dd<diff ){ diff=dd; best=i; }
		}
		FT_Select_Size( face,best );
	}else{
		FT_Size_RequestRec req={ FT_SIZE_REQUEST_TYPE_REAL_DIM,0,(long)(height*64*d),0,0 };
		FT_Request_Size( face,&req );
	}

	baseline=height*d+(face->size->metrics.descender/64);
	density=1.0f/d;
}

BBImageFont::~BBImageFont(){
	FT_Done_Face( face );
	delete atlas;
}

BBImageFont *BBImageFont::load( const std::string &name,int height,float density,int flags ){
	BBFontData font;
	std::map<std::string,BBFontData>::iterator it=bbFontCache.find( name );
	if( it==bbFontCache.end() ){
		size_t n=name.rfind( '.' );
		if( n==std::string::npos ){
#ifdef BB_EMSCRIPTEN
			if( name=="Blitz" ){
				BBImageFont *f=load( "GFX/font/blitz/Blitz.fon",height,density,flags );
				if( f ) return f;
			}
			if( name!="GFX/cour.ttf" ) return load( "GFX/cour.ttf",height,density,flags );
#endif
			return 0;
		}
		std::string ext=tolower( name.substr( n+1 ) );
		if( ext!="ttf" && ext!="fon" ){ fprintf( stderr,"[font] bad ext: %s\n",name.c_str() );return 0; }
		std::streambuf *sb=gx_filesys->openFile( name,std::ios_base::in );
		if( !sb ){ fprintf( stderr,"[font] open failed: %s\n",name.c_str() );return 0; }
		std::string bytes; char tmp[8192]; std::streamsize got;
		while( (got=sb->sgetn(tmp,sizeof tmp))>0 ) bytes.append( tmp,(size_t)got );
		delete sb;
		if( bytes.empty() ){ fprintf( stderr,"[font] empty: %s\n",name.c_str() );return 0; }
		font.size=(long)bytes.size();
		font.data=new unsigned char[font.size];
		memcpy( font.data,bytes.data(),font.size );
		bbFontCache.insert( std::make_pair( name,font ) );
	}else{
		font=it->second;
	}

	FT_Face face;
	if( FT_New_Memory_Face( ft,font.data,font.size,0,&face ) ){
		fprintf( stderr,"[font] FT face failed: %s (%ld bytes)\n",name.c_str(),font.size );
		return 0;
	}

	if( !face->charmap && face->num_charmaps>0 ){
		FT_Set_Charmap( face,face->charmaps[0] );
	}

	return d_new BBImageFont( face,height,density );
}

bool BBImageFont::loadChar( uint32_t c )const{
	if( c<32 ) return false;
	if( characters.count( c ) ) return false;

	Char chr;
	chr.index=FT_Get_Char_Index( face,c );
	int err=FT_Load_Glyph( face,chr.index,FT_LOAD_RENDER );

	chr.width=face->glyph->bitmap.width;
	chr.height=face->glyph->bitmap.rows;
	chr.bearing_x=face->glyph->bitmap_left;
	chr.bearing_y=face->glyph->bitmap_top;
	chr.advance=face->glyph->advance.x>>6;
	(void)err;

	characters.insert( std::make_pair( c,chr ) );

	return (dirty=true);
}

bool BBImageFont::loadChars( const std::string &t )const{
	const char *s=t.c_str();
	while( *s ){
		utf8_int32_t chr;
		s=utf8codepoint( s,&chr );
		loadChar( chr );
	}
	return dirty;
}

void BBImageFont::rebuildAtlas(){
	if( !dirty ) return;

	int total=0,maxw=1;
	for( std::map<uint32_t,Char>::iterator it=characters.begin();it!=characters.end();++it ){
		Char &c=it->second;
		total += ((int)c.width+1)*((int)c.height+1);
		if( (int)c.width+1>maxw ) maxw=(int)c.width+1;
	}

	int aw=256;
	while( aw<maxw && aw<4096 ) aw*=2;
	while( (double)total*1.6>(double)aw*aw && aw<4096 ) aw*=2;

	int ox=0,oy=0,my=0;
	for( std::map<uint32_t,Char>::iterator it=characters.begin();it!=characters.end();++it ){
		Char &c=it->second;
		if( ox+(int)c.width+1>aw ){ oy+=my; ox=0; my=0; }
		c.x=ox; c.y=oy;
		if( (int)c.height+1>my ) my=(int)c.height+1;
		ox+=(int)c.width+1;
	}
	int need_h=oy+my; if( need_h<1 ) need_h=1;
	int ah=1; while( ah<need_h && ah<4096 ) ah*=2;

	delete atlas;
	atlas=d_new BBPixmap;
	atlas->format=PF_I8;
	atlas->width=aw; atlas->height=ah;
	atlas->depth=8; atlas->pitch=aw; atlas->bpp=1;
	atlas->bits=new unsigned char[aw*ah];
	memset( atlas->bits,0,aw*ah );

	for( std::map<uint32_t,Char>::iterator it=characters.begin();it!=characters.end();++it ){
		Char &c=it->second;

		FT_Load_Glyph( face,c.index,FT_LOAD_RENDER );
		FT_Bitmap bm=face->glyph->bitmap;

		for( int y=0;y<(int)bm.rows;y++ ){
			int ay=c.y+y; if( ay<0||ay>=ah ) continue;
			unsigned char *in=bm.buffer+y*bm.pitch;
			unsigned char *out=atlas->bits+(size_t)ay*aw;
			for( int xp=0;xp<(int)bm.width;xp++ ){
				int ax=c.x+xp; if( ax<0||ax>=aw ) continue;
				unsigned char v;
				if( bm.pixel_mode==FT_PIXEL_MODE_MONO )
					v=(in[xp>>3]>>(7-(xp&7)))&1 ? 0xff : 0;
				else if( bm.pixel_mode==FT_PIXEL_MODE_GRAY )
					v=in[xp];
				else
					v=0;
				out[ax]=v;
			}
		}
	}

	dirty=false;
}

BBImageFont::Char &BBImageFont::getChar( uint32_t c ){
	loadChar( c );
	return characters[c];
}

float BBImageFont::getKerning( uint32_t l,uint32_t r ){
	if( !FT_HAS_KERNING(face) ) return 0;
	Char lc=getChar( l ),rc=getChar( r );

	FT_Vector delta;
	FT_Get_Kerning( face,lc.index,rc.index,FT_KERNING_DEFAULT,&delta );
	return (delta.x>>6)*density;
}

int BBImageFont::getWidth()const{
	return 0;
}

int BBImageFont::getHeight()const{
	return height;
}

int BBImageFont::getWidth( const std::string &text )const{
	loadChars( text );

	const char *t=text.c_str();
	int w=0;
	while( *t ){
		utf8_int32_t c;
		t=utf8codepoint( t,&c );
		if( c<32 ) continue;
		w+=characters[c].advance*density;
	}
	return w;
}

bool BBImageFont::isPrintable( int chr )const{
	return FT_Get_Char_Index( face,chr )!=0;
}
