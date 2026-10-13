#include <bb/debugui/widgets.h>
#include <cstdio>
#include <cmath>

static int failures=0;
static void expect(bool ok,const char *name){
  std::printf("%s: %s\n",ok?"PASS":"FAIL",name);if(!ok)++failures;
}
static ImVec2 origin;
static float slider(float value){
  ImGui::NewFrame();
  ImGui::SetNextWindowPos(ImVec2(0,0));ImGui::SetNextWindowSize(ImVec2(400,200));
  ImGui::Begin("widgets",nullptr,ImGuiWindowFlags_NoTitleBar);
  ImGui::SetNextItemWidth(200);
  s2debugui::floatControl("##volume",&value,0.0f,1.0f);
  origin=ImGui::GetItemRectMin();
  ImGui::End();ImGui::Render();return value;
}
int main(int argc,char **argv){
  if(argc!=2)return 2;
  ImGui::CreateContext();auto &io=ImGui::GetIO();
  io.IniFilename=nullptr;io.LogFilename=nullptr;io.DisplaySize=ImVec2(800,600);io.DeltaTime=1.0f/60;
  io.BackendFlags|=ImGuiBackendFlags_RendererHasTextures;
  ImGui::GetPlatformIO().Renderer_TextureMaxWidth=4096;
  ImGui::GetPlatformIO().Renderer_TextureMaxHeight=4096;
  s2debugui::loadFonts(argv[1]);
  slider(0.5f);
  auto *font=io.FontDefault->GetFontBaked(17.0f);
  expect(font->FindGlyphNoFallback(0x41)!=nullptr,"Proggy ASCII glyph");
  expect(font->FindGlyphNoFallback(0xac00)!=nullptr && font->FindGlyphNoFallback(0xd7a3)!=nullptr,"full Korean syllable range");
  expect(font->FindGlyphNoFallback(0x1100)!=nullptr && font->FindGlyphNoFallback(0x314e)!=nullptr,"Korean composition Jamo");
  expect(font->FindGlyphNoFallback(0xfc)!=nullptr && font->FindGlyphNoFallback(0x65e5)!=nullptr,"German umlaut and CJK fallback");
  io.AddInputCharactersUTF8("\xed\x95\x9c\xea\xb8\x80\xf0\x9f\x99\x82");
  ImGui::NewFrame();
  expect(io.InputQueueCharacters.Size==3 && io.InputQueueCharacters[0]==0xd55c && io.InputQueueCharacters[2]==0x1f642,"UTF-8 input preserves supplementary Unicode codepoints");
  ImGui::EndFrame();
  slider(0.5f); // Restore the widget window after the input-only frame.
  io.AddMousePosEvent(origin.x+150,origin.y+8);slider(0.5f);
  io.AddMouseButtonEvent(0,true);float value=slider(0.5f);
  expect(value>0.6f && value<0.9f,"normalized slider selects value on track");
  io.AddMousePosEvent(origin.x+152,origin.y+8);float next=slider(value);
  expect(next>value && next-value<0.03f,"normalized slider permits fine adjustments");
  io.AddMouseButtonEvent(0,false);slider(next);
  s2debugui::LogScroll log;
  expect(log.update(true,0,500),"enabling follow jumps to latest");
  expect(log.update(true,500,500),"new messages follow while at bottom");
  expect(!log.update(true,300,500),"scrolling upwards stops follow");
  expect(!log.update(false,300,700),"new messages preserve manual scroll");
  expect(log.update(true,300,700),"follow can be explicitly resumed");
  ImGui::DestroyContext();return failures?1:0;
}
