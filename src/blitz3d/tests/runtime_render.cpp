// Compile the actual scene implementation so this fixture can supply an
// offscreen device directly. No window, swapchain, OS input or game files.
#include "../src/modules/bb/blitz3d.webgpu/blitz3d.webgpu.cpp"
#include <cstdio>
#include <cstdlib>

static int failures=0;
static void error(WGPUDevice const *,WGPUErrorType,WGPUStringView message,void *,void *){
    std::fprintf(stderr,"GPU error: %.*s\n",(int)message.length,message.data);
    ++failures;
}
static bool initOffscreen(WebGPUContextResources &res){
    res.instance=wgpuCreateInstance(nullptr);
    WGPURequestAdapterOptions options={};
    options.backendType=WGPUBackendType_D3D12;
    bool ready=false;
    WGPURequestAdapterCallbackInfo adapter={};
    adapter.mode=WGPUCallbackMode_AllowProcessEvents;
    adapter.userdata1=&res;adapter.userdata2=&ready;
    adapter.callback=[](WGPURequestAdapterStatus status,WGPUAdapter value,WGPUStringView,void *ctx,void *done){
        if(status==WGPURequestAdapterStatus_Success) ((WebGPUContextResources*)ctx)->adapter=value;
        *(bool*)done=true;
    };
    wgpuInstanceRequestAdapter(res.instance,&options,adapter);
    while(!ready) wgpuInstanceProcessEvents(res.instance);
    if(!res.adapter) return false;
    ready=false;
    WGPUDeviceDescriptor desc={};
    desc.uncapturedErrorCallbackInfo.callback=error;
    WGPURequestDeviceCallbackInfo device={};
    device.mode=WGPUCallbackMode_AllowProcessEvents;
    device.userdata1=&res;device.userdata2=&ready;
    device.callback=[](WGPURequestDeviceStatus status,WGPUDevice value,WGPUStringView,void *ctx,void *done){
        if(status==WGPURequestDeviceStatus_Success) ((WebGPUContextResources*)ctx)->device=value;
        *(bool*)done=true;
    };
    wgpuAdapterRequestDevice(res.adapter,&desc,device);
    while(!ready) wgpuInstanceProcessEvents(res.instance);
    if(!res.device) return false;
    res.queue=wgpuDeviceGetQueue(res.device);
    res.ensurePipelineObjects();
    return true;
}
static void expectPixel(const char *name,unsigned actual,int r,int g,int b){
    int ar=(actual>>16)&255,ag=(actual>>8)&255,ab=actual&255;
    bool pass=std::abs(ar-r)<=2 && std::abs(ag-g)<=2 && std::abs(ab-b)<=2;
    std::printf("%s: %s RGB %d,%d,%d (expected %d,%d,%d)\n",pass?"PASS":"FAIL",name,ar,ag,ab,r,g,b);
    if(!pass) ++failures;
}
static void fillQuad(BBMesh *mesh,float z,const float normal[3]){
    const float positions[4][3]={{-1,-1,z},{1,-1,z},{1,1,z},{-1,1,z}};
    const float uv[2][2]={{0.5f,0.5f},{0.5f,0.5f}};
    mesh->lock(true);
    for(int i=0;i<4;++i) mesh->setVertex(i,positions[i],normal,uv);
    mesh->setTriangle(0,0,2,1);mesh->setTriangle(1,0,3,2);
    mesh->unlock();
}
static unsigned draw(WebGPUScene &scene,WebGPUCanvas &target,BBMesh *mesh,
                     const BBScene::RenderState &state,const std::vector<BBLightRep*> &lights,
                     const float ambient[3],int fog=BBScene::FOG_NONE){
    const float background[3]={0,0,1},fogColor[3]={1,1,1};
    gx_canvas=&target;
    scene.begin(lights);
    scene.setCanvas(32,32);scene.setViewport(0,0,32,32);
    scene.setOrthoProj(0.1f,4000,-1,1,1,-1);
    scene.setViewMatrix(nullptr);scene.setWorldMatrix(nullptr);
    scene.setAmbient(ambient);scene.setFogColor(fogColor);
    scene.setFogRange(5,15);scene.setFogMode(fog);
    scene.setZMode(BBScene::ZMODE_NORMAL);
    scene.clear(background,1,1,true,true);
    scene.setRenderState(state);scene.render(mesh,0,4,0,2);scene.end();
    return target.getPixel(16,16);
}
class FadingModel : public Model{
public:
    Brush applied;
    void setRenderBrush(const Brush &brush) override{ applied=brush; }
};
static void checkRendering(WebGPUContextResources &res){
    WebGPUCanvas target(&res,32,32,BBCanvas::CANVAS_TEX_VIDMEM);
    WebGPUCanvas texture(&res,1,1,BBCanvas::CANVAS_TEXTURE|BBCanvas::CANVAS_TEX_RGB);
    const unsigned char texel[4]={128,192,64,255};
    texture.uploadRGBA(texel,1,1);
    WebGPUScene scene(&res);
    BBMesh *mesh=scene.createMesh(4,2,0);
    const float oblique[3]={0.8660254f,0,0.5f},front[3]={0,0,-1};
    const float ambient[3]={0.25f,0.25f,0.25f},dark[3]={0,0,0};
    WebGPULight light;light.type=Light::LIGHT_DISTANT;light.r=light.g=light.b=0.5f;
    light.matrix[8]=-0.8660254f;light.matrix[10]=0.5f;
    std::vector<BBLightRep*> lights={&light},noLights;
    BBScene::RenderState state={};
    state.color[0]=0.2f;state.color[1]=0.6f;state.color[2]=0.1f;
    state.alpha=1;state.fx=BBScene::FX_DOUBLESIDED;
    fillQuad(mesh,10,oblique);
    // The old D3D7 material disables specular completely at shininess zero.
    expectPixel("matte green under oblique light",draw(scene,target,mesh,state,lights,ambient),26,77,13);
    state.tex_states[0].canvas=&texture;state.tex_states[0].blend=BBScene::BLEND_MULTIPLY;
    expectPixel("textured matte green",draw(scene,target,mesh,state,lights,ambient),13,58,3);
    state.tex_states[0]={};
    fillQuad(mesh,10,front);
    state.fx|=BBScene::FX_FULLBRIGHT;
    expectPixel("fullbright colour",draw(scene,target,mesh,state,lights,ambient),51,153,26);
    expectPixel("fog changes RGB only",draw(scene,target,mesh,state,lights,ambient,BBScene::FOG_LINEAR),153,204,140);
    state.fx|=BBScene::FX_NOFOG;
    expectPixel("no-fog material",draw(scene,target,mesh,state,lights,ambient,BBScene::FOG_LINEAR),51,153,26);

    // Real Model/Brush auto-fade: opaque up to 2000, half at 2150, culled at 2300.
    FadingModel model;model.setAutoFade(2000,2300);model.setColor(Vector(1,0,0));
    model.setFX(BBScene::FX_FULLBRIGHT|BBScene::FX_DOUBLESIDED);
    for(float distance:{100.0f,1250.0f,1999.0f,2000.0f,2150.0f,2299.0f,2300.0f,100.0f}){
        model.setLocalPosition(Vector(0,0,distance));model.beginRender(1);
        bool visible=model.doAutoFade(Vector(0,0,0));
        float alpha=distance<=2000?1:(2300-distance)/300;
        if(visible!=(distance<2300) || (visible && std::fabs(model.applied.getAlpha()-alpha)>0.0001f)) ++failures;
        if(!visible) continue;
        fillQuad(mesh,distance,front);
        char label[80];std::snprintf(label,sizeof(label),"auto-fade at %.0f",distance);
        expectPixel(label,draw(scene,target,mesh,model.applied.getRenderState(),noLights,dark),
                    (int)std::lround(255*alpha),0,(int)std::lround(255*(1-alpha)));
    }
    // Shininess strength is independent of the diffuse light colour and is
    // added after texture modulation, as configured by the D3D7 backend.
    bbMat4Identity(light.matrix);light.r=0.2f;light.g=0.4f;light.b=0.6f;
    fillQuad(mesh,1000,front);
    state.fx=BBScene::FX_DOUBLESIDED;state.shininess=0.5f;
    state.tex_states[0].canvas=&texture;state.tex_states[0].blend=BBScene::BLEND_MULTIPLY;
    expectPixel("textured specular strength",draw(scene,target,mesh,state,lights,dark),133,174,131);
    scene.freeMesh(mesh);
    gx_canvas=nullptr;
}
int main(){
    WebGPUContextResources res;
    if(!initOffscreen(res)){ std::fprintf(stderr,"No offscreen D3D12 device\n");return 2; }
    checkRendering(res);
    res.destroy();
    std::printf("%d rendering failures (offscreen, no window)\n",failures);
    return failures?1:0;
}
