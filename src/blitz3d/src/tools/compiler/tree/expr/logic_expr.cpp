#include "logic_expr.h"
#include "../../codegen.h"

////////////////////////////////////////////////////////////
// short-circuit boolean expression (Lor)                   //
// unlike BinExprNode (And/Or), the rhs is only evaluated   //
// when the lhs is false                                     //
////////////////////////////////////////////////////////////
ExprNode *LogicExprNode::semant( Environ *e ){
	lhs=lhs->semant(e);lhs=lhs->castTo( Type::int_type,e );
	rhs=rhs->semant(e);rhs=rhs->castTo( Type::int_type,e );
	sem_type=Type::int_type;
	return this;
}

TNode *LogicExprNode::translate( Codegen *g ){
	TNode *l=lhs->translate( g );
	TNode *r=rhs->translate( g );
	return d_new TNode( IR_OR,l,r );
}

#ifdef USE_LLVM
llvm::Value *LogicExprNode::translate2( Codegen_LLVM *g ){
	auto *func=g->builder->GetInsertBlock()->getParent();
	auto *i32=Type::int_type->llvmType( g->context.get() );

	auto *rhsBB=llvm::BasicBlock::Create( *g->context,"lor.rhs",func );
	auto *mergeBB=llvm::BasicBlock::Create( *g->context,"lor.end" );

	llvm::Value *lhsVal=lhs->translate2( g );
	llvm::Value *lhsCmp=lhsVal;
	if( !llvm::dyn_cast<llvm::CmpInst>(lhsCmp) ){
		lhsCmp=compare2( NE,lhsVal,0,lhs->sem_type,g );
	}
	llvm::BasicBlock *lhsBlock=g->builder->GetInsertBlock();

	g->builder->CreateCondBr( lhsCmp,mergeBB,rhsBB );

	g->builder->SetInsertPoint( rhsBB );
	llvm::Value *rhsVal=rhs->translate2( g );
	llvm::Value *rhsCmp=rhsVal;
	if( !llvm::dyn_cast<llvm::CmpInst>(rhsCmp) ){
		rhsCmp=compare2( NE,rhsVal,0,rhs->sem_type,g );
	}
	llvm::Value *rhsBool=g->builder->CreateZExt( rhsCmp,i32 );
	llvm::BasicBlock *rhsEndBlock=g->builder->GetInsertBlock();
	g->builder->CreateBr( mergeBB );

	func->insert( func->end(),mergeBB );
	g->builder->SetInsertPoint( mergeBB );

	auto *phi=g->builder->CreatePHI( i32,2 );
	llvm::Value *trueVal=llvm::ConstantInt::get( i32,1 );
	phi->addIncoming( trueVal,lhsBlock );
	phi->addIncoming( rhsBool,rhsEndBlock );

	return phi;
}
#endif

json LogicExprNode::toJSON( Environ *e ){
	json tree;tree["@class"]="LogicExprNode";
	tree["sem_type"]=sem_type->toJSON();
	tree["op"]="LOR";
	tree["lhs"]=lhs->toJSON( e );
	tree["rhs"]=rhs->toJSON( e );
	return tree;
}
