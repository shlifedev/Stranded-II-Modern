#pragma once
#include <windows.h>
#include <filesystem>
#include <string>

inline std::wstring bbWide(const std::string &text){
    int count=MultiByteToWideChar(CP_UTF8,0,text.data(),int(text.size()),nullptr,0);
    std::wstring out(count,L'\0');
    if(count) MultiByteToWideChar(CP_UTF8,0,text.data(),int(text.size()),out.data(),count);
    return out;
}
inline std::string bbUtf8(const std::wstring &text){
    int count=WideCharToMultiByte(CP_UTF8,0,text.data(),int(text.size()),nullptr,0,nullptr,nullptr);
    std::string out(count,'\0');
    if(count) WideCharToMultiByte(CP_UTF8,0,text.data(),int(text.size()),out.data(),count,nullptr,nullptr);
    return out;
}
// Narrow CRT/STL file APIs read the ANSI code page, and MSVC's u8path throws
// on stray bytes that legacy text input can leave in a typed name. Use the
// same lenient conversion as the Win32 file calls so every path agrees.
inline std::filesystem::path bbWidePath(const std::string &text){
    return std::filesystem::path(bbWide(text));
}
