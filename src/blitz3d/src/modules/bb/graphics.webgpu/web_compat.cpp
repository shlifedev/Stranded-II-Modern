// Web-only compatibility symbols. Image decoding is shared in pixmap/stb.loader.cpp.
bool runtime_html_create(){ return true; }
bool runtime_html_destroy(){ return true; }

extern "C" {
void glDrawElements( unsigned mode,int count,unsigned type,const void *indices );
void glPolygonMode( unsigned  ,unsigned   ){}
void glDrawElementsBaseVertex( unsigned mode,int count,unsigned type,
                               const void *indices,int   ){
	glDrawElements( mode,count,type,indices );
}
}
