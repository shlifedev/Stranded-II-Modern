#ifndef LOGIC_EXPR_NODE_H
#define LOGIC_EXPR_NODE_H

#include "node.h"

// Lor - short-circuiting logical or (unlike Or, which is an eager bitwise op)
struct LogicExprNode : public ExprNode{
	ExprNode *lhs,*rhs;
	LogicExprNode( ExprNode *lhs,ExprNode *rhs ):lhs( lhs ),rhs( rhs ){}
	~LogicExprNode(){ delete lhs;delete rhs; }
	ExprNode *semant( Environ *e );
	TNode *translate( Codegen *g );
#ifdef USE_LLVM
	llvm::Value *translate2( Codegen_LLVM *g );
#endif

	json toJSON( Environ *e );
};

#endif
