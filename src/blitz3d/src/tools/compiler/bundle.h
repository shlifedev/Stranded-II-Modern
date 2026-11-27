#ifndef BUNDLE_H
#define BUNDLE_H

#include <string>
#include <vector>

struct BundleFile {
	std::string relativePath;
	std::string absolutePath;

	BundleFile( std::string relpath,std::string abspath ):relativePath(relpath),absolutePath(abspath){};
};

struct BundleInfo {
	bool enabled=false;
	std::string identifier;
	std::string appName;
	std::vector<BundleFile> files;
	std::string signerId,teamId;
};

void bundleFiles( const BundleInfo &bundle,const std::string &path );

#endif
