#include "struct_decl.h"

void StructDeclNode::proto( DeclSeq *d,Environ *e ){
	sem_type=d_new StructType( ident,d_new DeclSeq() );
	if( !d->insertDecl( ident,sem_type,DECL_STRUCT ) ){
		delete sem_type;ex( "Duplicate identifier" );
	}
	e->types.push_back( sem_type );
}

void StructDeclNode::semant( Environ *e ){
	fields->proto( sem_type->fields,e );
	for( int k=0;k<sem_type->fields->size();++k ) sem_type->fields->decls[k]->offset=k*4;
}

void StructDeclNode::translate( Codegen *g ){

	fields->translate( g );

	g->align_data( 4 );
	g->i_data( 5,"_t"+ident );

	int k;
	for( k=0;k<2;++k ){
		std::string lab=genLabel();
		g->i_data( 0,lab );
		g->p_data( lab );
		g->p_data( lab );
		g->i_data( 0 );
		g->i_data( -1 );
	}

	g->i_data( sem_type->fields->size() );

	for( k=0;k<sem_type->fields->size();++k ){
		Decl *field=sem_type->fields->decls[k];
		Type *type=field->type;
		std::string t;
		if( type==Type::int_type ) t="__bbIntType";
		else if( type==Type::float_type ) t="__bbFltType";
		else if( type==Type::string_type ) t="__bbStrType";
		else if( StructType *s=type->structType() ) t="_t"+s->ident;
		else if( VectorType *v=type->vectorType() ) t=v->label;
		g->p_data( t );
	}

}

#ifdef USE_LLVM
void StructDeclNode::translate2( Codegen_LLVM *g ){
	auto template_typesary=llvm::ArrayType::get( llvm::PointerType::get( g->bbType,0 ),sem_type->fields->size() );
	std::vector<llvm::Constant*> template_types;
	std::vector<llvm::Constant*> template_defs;
	std::vector<llvm::Type*> fields;
	fields.push_back( g->bbObj );
	for( int k=0;k<sem_type->fields->size();++k ){
		Decl *field=sem_type->fields->decls[k];
		Type *type=field->type;
		fields.push_back( type->llvmType( g->context.get() ) );

		llvm::GlobalVariable* gt=0;
		std::string t;
		if( type==Type::int_type ) t="_bbIntType";
		else if( type==Type::float_type ) t="_bbFltType";
		else if( type==Type::string_type ) t="_bbStrType";
		else if( StructType *s=type->structType() ) gt=(llvm::GlobalVariable*)s->llvmTypeDef( g );
		else if( VectorType *v=type->vectorType() ) gt=v->llvmDef( g );

		if( !gt ) {
			gt=(llvm::GlobalVariable*)g->module->getOrInsertGlobal( t,g->bbType );
		}

		template_types.push_back( gt );
	}

	sem_type->llvmTypeDef( g );
	sem_type->llvmType( g->context.get() );

	auto ty2=sem_type->deftype;

	llvm::StructType* ty=(llvm::StructType*)sem_type->structtype;
	ty->setBody( fields );

	std::vector<llvm::Type*> templatefields;
	templatefields.push_back( g->bbObjType );
	templatefields.push_back( template_typesary );
	ty2->setBody( templatefields );

	auto temp=(llvm::GlobalVariable*)sem_type->objty;

	std::vector<llvm::Constant*> objdata;
	objdata.push_back( llvm::ConstantPointerNull::get( llvm::PointerType::get( g->bbField,0 ) ) );
	objdata.push_back( llvm::ConstantPointerNull::get( llvm::PointerType::get( g->bbObj,0 ) ) );
	objdata.push_back( llvm::ConstantPointerNull::get( llvm::PointerType::get( g->bbObj,0 ) ) );
	objdata.push_back( llvm::ConstantPointerNull::get( llvm::PointerType::get( g->bbObjType,0 ) ) );
	objdata.push_back( llvm::ConstantInt::get( Type::int_type->llvmType( g->context.get() ),-1 ) );
	auto emptyobj=llvm::ConstantStruct::get( g->bbObj,objdata );

	std::vector<llvm::Constant*> objtypedata;
	objtypedata.push_back( llvm::ConstantInt::get( Type::int_type->llvmType( g->context.get() ),5 ) );

	for( int k=0;k<2;++k ){
		std::vector<llvm::Value*> indices;
		indices.push_back( llvm::ConstantInt::get( *g->context,llvm::APInt(32, 0) ) );
		indices.push_back( llvm::ConstantInt::get( *g->context,llvm::APInt(32, 0) ) );
		indices.push_back( llvm::ConstantInt::get( *g->context,llvm::APInt(32, k+1) ) );
		auto objptr=llvm::ConstantExpr::getGetElementPtr( ty2,temp,indices );

		std::vector<llvm::Constant*> objdata;
		objdata.push_back( llvm::ConstantPointerNull::get( llvm::PointerType::get( g->bbField,0 ) ) );
		objdata.push_back( objptr );
		objdata.push_back( objptr );
		objdata.push_back( llvm::ConstantPointerNull::get( llvm::PointerType::get( g->bbObjType,0 ) ) );
		objdata.push_back( llvm::ConstantInt::get( Type::int_type->llvmType( g->context.get() ),-1 ) );
		auto defaultobj=llvm::ConstantStruct::get( g->bbObj,objdata );

		objtypedata.push_back( defaultobj );
	}

	objtypedata.push_back( llvm::ConstantInt::get( Type::int_type->llvmType( g->context.get() ),sem_type->fields->size( ) ) );

	std::vector<llvm::Constant*> structfields;
	structfields.push_back( llvm::ConstantStruct::get( g->bbObjType,objtypedata ) );
	structfields.push_back( llvm::ConstantArray::get( template_typesary,template_types ) );
	auto init=llvm::ConstantStruct::get( ty2,structfields );
	temp->setInitializer( init );
}
#endif

json StructDeclNode::toJSON( Environ *e ){
	json tree;tree["@class"]="StructDeclNode";
	tree["pos"]=pos;
	tree["file"]=file;
	tree["ident"]=ident;
	tree["fields"]=fields->toJSON( e );
	tree["sem_type"]=sem_type->toJSON();
	return tree;
}
