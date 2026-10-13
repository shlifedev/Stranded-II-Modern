#pragma once
#include "imgui.h"
#include <algorithm>
#include <cstdio>
#include <string>

namespace s2debugui {
inline void loadFonts(const std::string &path){
  auto &io=ImGui::GetIO();io.Fonts->Clear();
  ImFontConfig latin;latin.SizePixels=17.0f;latin.PixelSnapH=false;
  io.FontDefault=io.Fonts->AddFontDefaultVector(&latin); // Scalable ProggyClean family.
  ImFontConfig merge;merge.MergeMode=true;
  auto add=[&](const std::string &file){
    if(FILE *input=std::fopen(file.c_str(),"rb")){
      std::fclose(input);io.Fonts->AddFontFromFileTTF(file.c_str(),17.0f,&merge);
    }
  };
  add(path); // Pretendard: Korean, extended Latin and punctuation.
  auto slash=path.find_last_of("/\\");
  add(path.substr(0,slash+1)+"fallback.ttf");
  add(path.substr(0,slash+1)+"fallback-extra.ttf");
}

inline bool floatControl(const char *label,float *value,float minimum,float maximum){
  // Small bounded values use the full track. Ctrl+click also accepts an exact value.
  if(maximum>minimum && maximum-minimum<=2.0f)
    return ImGui::SliderFloat(label,value,minimum,maximum,"%.3f",ImGuiSliderFlags_AlwaysClamp);
  return ImGui::DragFloat(label,value,0.1f,minimum,maximum,"%.2f",ImGuiSliderFlags_AlwaysClamp);
}

struct LogScroll {
  bool follow=false;
  bool update(bool requested,float position,float maximum){
    bool newlyEnabled=requested && !follow;
    bool atBottom=position>=maximum-2.0f;
    follow=requested && (newlyEnabled || atBottom);
    return follow;
  }
};
}
