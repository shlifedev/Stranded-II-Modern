#pragma once
#include "imgui.h"
#include <algorithm>
#include <cstdio>
#include <string>

namespace s2debugui {
inline void applyStyle(){
  ImGui::StyleColorsDark();
  auto &style=ImGui::GetStyle();
  style.WindowPadding=ImVec2(16,14);style.FramePadding=ImVec2(10,6);
  style.ItemSpacing=ImVec2(10,8);style.ItemInnerSpacing=ImVec2(8,5);
  style.WindowRounding=8;style.ChildRounding=6;style.FrameRounding=4;
  style.TabRounding=4;style.ScrollbarRounding=6;style.WindowBorderSize=1;
  style.Colors[ImGuiCol_WindowBg]=ImVec4(.075f,.09f,.115f,.98f);
  style.Colors[ImGuiCol_ChildBg]=ImVec4(.06f,.075f,.095f,.5f);
  style.Colors[ImGuiCol_TitleBgActive]=ImVec4(.11f,.16f,.20f,1);
  style.Colors[ImGuiCol_FrameBg]=ImVec4(.14f,.18f,.22f,1);
  style.Colors[ImGuiCol_Button]=ImVec4(.16f,.25f,.29f,1);
  style.Colors[ImGuiCol_ButtonHovered]=ImVec4(.19f,.38f,.42f,1);
  style.Colors[ImGuiCol_ButtonActive]=ImVec4(.18f,.46f,.48f,1);
  style.Colors[ImGuiCol_Header]=ImVec4(.14f,.24f,.28f,1);
  style.Colors[ImGuiCol_HeaderHovered]=ImVec4(.18f,.34f,.38f,1);
  style.Colors[ImGuiCol_HeaderActive]=ImVec4(.18f,.42f,.44f,1);
  style.Colors[ImGuiCol_Tab]=ImVec4(.11f,.16f,.20f,1);
  style.Colors[ImGuiCol_TabSelected]=ImVec4(.16f,.31f,.35f,1);
  style.Colors[ImGuiCol_TabHovered]=ImVec4(.19f,.38f,.42f,1);
  style.Colors[ImGuiCol_CheckMark]=ImVec4(.39f,.83f,.73f,1);
  style.Colors[ImGuiCol_SliderGrab]=style.Colors[ImGuiCol_CheckMark];
  style.Colors[ImGuiCol_SliderGrabActive]=ImVec4(.55f,.95f,.84f,1);
}

// Keep translated labels visible instead of squeezing them after a wide input.
inline void beginControl(const char *label){
  ImGui::PushID(label);
  std::string caption(label);
  caption=caption.substr(0,caption.find("##"));
  ImGui::TextUnformatted(caption.c_str());
  ImGui::SetNextItemWidth(-1);
}
inline void endControl(){ImGui::PopID();}

inline void loadFonts(std::string path){
  std::replace(path.begin(),path.end(),'\\','/');
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
