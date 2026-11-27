
#ifndef LABEL_H
#define LABEL_H

struct Label{
	std::string name;
	int def,ref;
	int data_sz;

	Label( const std::string &n,int d,int r,int sz ):name(n),def(d),ref(r),data_sz(sz){}

	json toJSON(){
		json tree;tree["@class"]="Label";
		tree["name"]=name;
		tree["def"]=def;
		tree["ref"]=ref;
		tree["data_sz"]=data_sz;
		return tree;
	}
};

#endif
