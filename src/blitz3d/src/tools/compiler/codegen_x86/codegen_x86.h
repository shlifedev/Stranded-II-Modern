
#include "../codegen.h"

struct Tile;

class Codegen_x86 : public Codegen{
public:
	Codegen_x86( std::ostream &out,bool debug );

	virtual void enter( const std::string &l,int frameSize );
	virtual void code( TNode *code );
	virtual void leave( TNode *cleanup,int pop_sz );
	virtual void label( const std::string &l );
	virtual void i_data( int i,const std::string &l );
	virtual void s_data( const std::string &s,const std::string &l );
	virtual void p_data( const std::string &p,const std::string &l );
	virtual void align_data( int n );
	virtual void flush();

private:
	bool inCode;

	Tile *genCompare( TNode *t,std::string &func,bool negate );

	Tile *munch( TNode *t );
	Tile *munchReg( TNode *t );
	Tile *munchFP( TNode *t );

	Tile *munchCall( TNode *t );
	Tile *munchUnary( TNode *t );
	Tile *munchLogical( TNode *t );
	Tile *munchArith( TNode *t );
	Tile *munchShift( TNode *t );
	Tile *munchRelop( TNode *t );
	Tile *munchFPUnary( TNode *t );
	Tile *munchFPArith( TNode *t );
	Tile *munchFPRelop( TNode *t );
};
