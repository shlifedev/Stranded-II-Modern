// Compile the actual scene implementation so this fixture can supply an
// offscreen device directly. No window, swapchain, OS input or game files.
#include <webgpu/webgpu.h>
static unsigned indexed_draws=0,drawn_instances=0;
static void checkedDrawIndexed(WGPURenderPassEncoder pass,uint32_t count,uint32_t instances,
                               uint32_t first,int32_t base,uint32_t firstInstance){
    ++indexed_draws;drawn_instances+=instances;
    wgpuRenderPassEncoderDrawIndexed(pass,count,instances,first,base,firstInstance);
}
#define wgpuRenderPassEncoderDrawIndexed checkedDrawIndexed
#include "../src/modules/bb/blitz3d.webgpu/blitz3d.webgpu.cpp"
#undef wgpuRenderPassEncoderDrawIndexed
#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <bb/blitz3d/world.h>
#include <bb/blitz3d/terrainrep.h>
#include <bb/blitz3d/meshmodel.h>

static int failures=0;
static void expect(bool ok,const char *name){
    std::printf("%s: %s\n",ok?"PASS":"FAIL",name);
    if(!ok) ++failures;
}
static void checkFrustum(){
    unsigned seed=57013;
    auto random=[&seed](float lo,float hi){
        seed=seed*1664525u+1013904223u;
        return lo+(hi-lo)*(float)(seed>>8)*(1.0f/16777216.0f);
    };
    Frustum camera(1,1500,2,1.5f);
    bool boxes=true,transforms=true,legacy=true,prepared=true;
    for(int i=0;i<12000;++i){
        Transform eye(yawMatrix(random(-3,3))*pitchMatrix(random(-1,1)),
                      Vector(random(-3000,3000),random(-500,500),random(-3000,3000)));
        if(i&1) eye.m.j=-eye.m.j; // Reflected water/mirror camera.
        Frustum world(camera,eye);
        Box box(Vector(-random(1,50),-random(1,50),-random(1,50)),
                Vector(random(1,50),random(1,50),random(1,50)));
        Matrix basis=yawMatrix(random(-3,3))*pitchMatrix(random(-3,3));
        basis.i*=random(0.2f,4);basis.j*=random(0.2f,4);basis.k*=random(0.2f,4);
        if(i&2) basis.i=-basis.i;
        basis.j+=basis.i*random(-0.5f,0.5f); // Shear/non-uniform parent transforms.
        Transform model(basis,eye*Vector(random(-2000,2000),random(-1500,1500),random(-100,1700)));
        Vector corners[8];for(int k=0;k<8;++k) corners[k]=box.corner(k);
        boxes&=world.cull(box)==world.cull(corners,8);
        for(int k=0;k<8;++k) corners[k]=model*box.corner(k);
        // Independent eight-corner reference. Ignore only rounding at the
        // boundary, where the optimized method intentionally keeps a sliver.
        float margin=1e30f;
        for(int p=0;p<6;++p){
            float furthest=-1e30f;
            for(int k=0;k<8;++k) furthest=std::max(furthest,world.getPlane(p).distance(corners[k]));
            margin=std::min(margin,furthest);
        }
        bool visible=world.cull(box,model);
        prepared&=visible==world.cull(box,model,Frustum::Bounds(box,model));
        if(std::fabs(margin)>0.1f){
            transforms&=visible==world.cull(corners,8);
            Frustum oldLocal(world,-model);
            legacy&=visible==oldLocal.cull(box);
        }
    }
    expect(boxes,"box culling matches all eight corners");
    expect(transforms,"transformed culling matches world corners, including reflected/sheared scales");
    expect(legacy,"transformed culling preserves legacy frustum visibility");
    expect(prepared,"prepared bounds exactly match oriented tests, including reflected/sheared scales");
    expect(camera.cull(Box(Vector(0,0,1)),Transform()),"touching near plane remains visible");
    expect(camera.cull(Box(Vector(0,0,1500)),Transform()),"touching far plane remains visible");
    expect(!camera.cull(Box(Vector(0,0,1501)),Transform()),"beyond far plane is culled");
    // Test roundoff fallback at near/far/side planes, including flat bounds
    // and large cancelling local coordinates and translations.
    bool boundaries=true;
    for(float offset:{0.0f,10000.0f,-10000.0f}) for(float delta:{-0.001f,0.0f,0.001f}){
        for(Vector position:{Vector(0,0,1+delta),Vector(0,0,1500+delta),Vector(10+delta,0,10)}){
            Box box(Vector(offset,0,0),Vector(offset,0.1f,0.1f));
            Transform model(Matrix(),position-Vector(offset,0,0));
            boundaries&=camera.cull(box,model)==camera.cull(box,model,Frustum::Bounds(box,model));
        }
    }
    expect(boundaries,"prepared bounds retain legacy decisions on touching and nearly touching planes");
}
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
static void fillRegion(BBMesh *mesh,int vertex,int triangle,float left,float right){
    const float positions[4][3]={{left,-1,10},{right,-1,10},{right,1,10},{left,1,10}};
    const float normal[3]={0,0,-1},uv[2][2]={{0.5f,0.5f},{0.5f,0.5f}};
    mesh->lock(false);
    for(int i=0;i<4;++i) mesh->setVertex(vertex+i,positions[i],normal,uv);
    mesh->setTriangle(triangle,0,2,1);mesh->setTriangle(triangle+1,0,3,2);
    mesh->unlock();
}
static void beginTarget(WebGPUScene &scene,WebGPUCanvas &target){
    const float black[3]={0,0,0};
    gx_canvas=&target;scene.begin({});
    scene.setCanvas(32,32);scene.setViewport(0,0,32,32);
    scene.setOrthoProj(0.1f,100,-1,1,1,-1);
    scene.setViewMatrix(nullptr);scene.setWorldMatrix(nullptr);
    scene.setAmbient(black);scene.setFogMode(BBScene::FOG_NONE);
    scene.setZMode(BBScene::ZMODE_DISABLE);
    scene.clear(black,1,1,true,true);
}
static void drawRegion(WebGPUScene &scene,BBMesh *mesh,int vertex,int count,int triangle,unsigned rgb){
    BBScene::RenderState state={};
    state.color[0]=((rgb>>16)&255)/255.0f;
    state.color[1]=((rgb>>8)&255)/255.0f;state.color[2]=(rgb&255)/255.0f;
    state.alpha=1;state.fx=BBScene::FX_FULLBRIGHT|BBScene::FX_DOUBLESIDED;
    scene.setRenderState(state);scene.render(mesh,vertex,count,triangle,2);
}
static void checkBatchedRendering(WebGPUContextResources &res){
    WebGPUCanvas target(&res,32,32,BBCanvas::CANVAS_TEX_VIDMEM);
    WebGPUCanvas second(&res,32,32,BBCanvas::CANVAS_TEX_VIDMEM);
    WebGPUCanvas copy(&res,32,32,BBCanvas::CANVAS_TEX_VIDMEM);
    WebGPUScene scene(&res);BBMesh *mesh=scene.createMesh(8,4,0);

    // Two sprites occupy separate ranges of one mesh. The second upload must
    // not split the command batch or replace the first sprite's vertices.
    beginTarget(scene,target);
    fillRegion(mesh,0,0,-1,0);drawRegion(scene,mesh,0,4,0,0xff0000);
    unsigned before=scene.submit_marker;
    fillRegion(mesh,4,2,0,1);drawRegion(scene,mesh,4,4,2,0x00ff00);
    expect(scene.submit_marker==before,"disjoint mesh updates stay in one submission");
    scene.end();
    expectPixel("shared mesh left",target.getPixel(8,16),255,0,0);
    expectPixel("shared mesh right",target.getPixel(24,16),0,255,0);

    // A subsequent camera has new frame/brush uniforms, but the previous
    // camera and CopyRect must keep theirs until both are submitted.
    beginTarget(scene,target);drawRegion(scene,mesh,0,4,0,0xff0000);scene.end();
    before=scene.submit_marker;
    copy.blit(0,0,&target,0,0,32,32,true);
    beginTarget(scene,second);drawRegion(scene,mesh,0,4,0,0x0000ff);scene.end();
    expect(scene.submit_marker==before,"camera and CopyRect passes share submission");
    expectPixel("previous camera uniforms",target.getPixel(8,16),255,0,0);
    expectPixel("CopyRect before next camera",copy.getPixel(8,16),255,0,0);
    expectPixel("next camera uniforms",second.getPixel(8,16),0,0,255);

    // Rewriting a used range gets another buffer version without submission.
    // Pixels must retain each draw's original vertices and indices.
    beginTarget(scene,target);drawRegion(scene,mesh,0,4,0,0xff0000);
    before=scene.submit_marker;
    fillRegion(mesh,0,0,0,1);drawRegion(scene,mesh,0,4,0,0x00ff00);
    expect(scene.submit_marker==before,"overlapping vertex update avoids submission");
    scene.end();
    expectPixel("old vertex positions",target.getPixel(8,16),255,0,0);
    expectPixel("new vertex positions",target.getPixel(24,16),0,255,0);

    fillRegion(mesh,0,0,-1,0);
    beginTarget(scene,target);drawRegion(scene,mesh,0,8,0,0xff0000);
    before=scene.submit_marker;
    mesh->lock(false);mesh->setTriangle(0,4,6,5);mesh->setTriangle(1,4,7,6);mesh->unlock();
    drawRegion(scene,mesh,0,8,0,0x00ff00);
    expect(scene.submit_marker==before,"overlapping index update avoids submission");
    scene.end();
    expectPixel("old triangle indices",target.getPixel(8,16),255,0,0);
    expectPixel("new triangle indices",target.getPixel(24,16),0,255,0);

    // Flushes caused by 2D arenas/readback must also retire 3D offsets and
    // mesh hazards, even when the scene itself did not initiate the flush.
    beginTarget(scene,target);drawRegion(scene,mesh,0,8,0,0xff0000);
    res.flush();before=scene.submit_marker;
    fillRegion(mesh,0,0,-1,0);drawRegion(scene,mesh,0,4,0,0x0000ff);
    expect(scene.submit_marker==before,"external flush retires mesh hazards");
    scene.end();
    expectPixel("uniforms after external flush",target.getPixel(8,16),0,0,255);
    expectPixel("draw before external flush",target.getPixel(24,16),255,0,0);

    // Repeated cameras exercise the finite uniform arena, including a flush
    // while pushing the next draw's uniforms. Every earlier draw must survive.
    beginTarget(scene,target);before=scene.submit_marker;
    for(int i=0;i<6000;++i){
        scene.setOrthoProj(0.1f,100+(i&1),-1,1,1,-1);
        drawRegion(scene,mesh,0,4,0,(i&1)?0xff0000:0x00ff00);
    }
    expect(scene.submit_marker>before,"uniform arena overflow submits safely");
    scene.end();expectPixel("last draw after arena overflow",target.getPixel(8,16),255,0,0);
    scene.freeMesh(mesh);gx_canvas=nullptr;
}
static void checkMeshVersions(WebGPUContextResources &res){
    size_t initialBytes=mesh_version_bytes;
    struct ReadbackCanvas : WebGPUCanvas{
        using WebGPUCanvas::WebGPUCanvas;
        using WebGPUCanvas::readPixels;
    } target(&res,32,32,BBCanvas::CANVAS_TEX_VIDMEM);
    WebGPUScene scene(&res);
    auto mesh=static_cast<WebGPUMesh*>(scene.createMesh(4,2,0));
    // More edits than the pool can hold must submit safely. Later frames
    // reuse those versions, including poses from before the last submission.
    for(int cycle=0;cycle<3;++cycle){
        beginTarget(scene,target);unsigned marker=scene.submit_marker;
        for(int i=0;i<32;++i){
            fillRegion(mesh,0,0,-1+i/16.0f,-1+(i+1)/16.0f);
            unsigned color=((i+cycle)&1)?0xff0000:0x00ff00;
            drawRegion(scene,mesh,0,4,0,color);
        }
        scene.end();
        expect(scene.submit_marker>marker,"mesh version limit submits safely");
        expect(mesh->versions.size()==MAX_MESH_VERSIONS,"mesh versions stay bounded and are reused");
        std::vector<unsigned char> rgba;target.readPixels(rgba);
        bool pixels=true;
        for(int i=0;i<32;++i){
            auto p=rgba.data()+(16*32+i)*4;
            bool red=((i+cycle)&1)!=0;
            pixels&=p[0]==(red?255:0) && p[1]==(red?0:255) && p[2]==0;
        }
        expect(pixels,"every queued mesh pose survives recycling and overflow");
    }
    scene.freeMesh(mesh);
    expect(mesh_version_bytes==initialBytes,"mesh destruction releases version budget");

    // A partial index update must copy the unchanged high-index vertices
    // into its new buffers, including the 32-bit index path.
    mesh=static_cast<WebGPUMesh*>(scene.createMesh(65540,2,0));
    fillRegion(mesh,0,0,-1,0);fillRegion(mesh,65536,0,0,1);
    beginTarget(scene,target);drawRegion(scene,mesh,0,65540,0,0xff0000);
    unsigned marker=scene.submit_marker;
    mesh->lock(false);mesh->setTriangle(0,65536,65538,65537);mesh->setTriangle(1,65536,65539,65538);mesh->unlock();
    drawRegion(scene,mesh,0,65540,0,0x00ff00);scene.end();
    expect(scene.submit_marker==marker,"32-bit index mutation avoids submission");
    expectPixel("old 32-bit indices",target.getPixel(8,16),255,0,0);
    expectPixel("new indices retain untouched high vertices",target.getPixel(24,16),0,255,0);
    scene.freeMesh(mesh);

    mesh=static_cast<WebGPUMesh*>(scene.createMesh(4,2,0));
    beginTarget(scene,target);fillRegion(mesh,0,0,-1,0);drawRegion(scene,mesh,0,4,0,0xff0000);
    marker=scene.submit_marker;
    // Substitute only budget accounting to exercise the exhausted-budget
    // path without allocating 32 MiB just for this fallback test.
    size_t previousBytes=mesh_version_bytes;mesh_version_bytes=MAX_MESH_VERSION_BYTES;
    fillRegion(mesh,0,0,0,1);drawRegion(scene,mesh,0,4,0,0x0000ff);
    mesh_version_bytes=previousBytes;scene.end();
    expect(scene.submit_marker>marker && mesh->versions.size()==1,"exhausted shared budget falls back to submission");
    expectPixel("budget fallback old pose",target.getPixel(8,16),255,0,0);
    expectPixel("budget fallback new pose",target.getPixel(24,16),0,0,255);

    beginTarget(scene,target);scene.setWireframe(true);
    fillRegion(mesh,0,0,-1,0);drawRegion(scene,mesh,0,4,0,0xff0000);
    marker=scene.submit_marker;
    fillRegion(mesh,0,0,0,1);drawRegion(scene,mesh,0,4,0,0x00ff00);scene.end();
    expect(scene.submit_marker>marker,"wireframe preserves its separate index-cache hazard path");
    expectPixel("wireframe old diagonal",target.getPixel(7,15),255,0,0);
    expectPixel("wireframe new diagonal",target.getPixel(23,15),0,255,0);
    scene.setWireframe(false);scene.freeMesh(mesh);
    expect(mesh_version_bytes==initialBytes,"all temporary mesh versions are released");
    gx_canvas=nullptr;
}
static void setInstance(WebGPUScene &scene,float scale,float x){
    BBScene::Matrix world={};
    world.elements[0][0]=scale;world.elements[1][1]=world.elements[2][2]=1;
    world.elements[3][0]=x;scene.setWorldMatrix(&world);
}
static void checkInstancing(WebGPUContextResources &res){
    WebGPUCanvas target(&res,32,32,BBCanvas::CANVAS_TEX_VIDMEM);
    WebGPUCanvas red(&res,1,1,BBCanvas::CANVAS_TEXTURE|BBCanvas::CANVAS_TEX_RGB);
    WebGPUCanvas blue(&res,1,1,BBCanvas::CANVAS_TEXTURE|BBCanvas::CANVAS_TEX_RGB);
    const unsigned char redPixel[4]={255,0,0,255},bluePixel[4]={0,0,255,255};
    red.uploadRGBA(redPixel,1,1);blue.uploadRGBA(bluePixel,1,1);
    WebGPUScene scene(&res);BBMesh *mesh=scene.createMesh(4,2,0);
    const float front[3]={0,0,-1};fillQuad(mesh,10,front);

    beginTarget(scene,target);scene.setZMode(BBScene::ZMODE_NORMAL);
    unsigned calls=indexed_draws,instances=drawn_instances;
    setInstance(scene,0.5f,-0.5f);drawRegion(scene,mesh,0,4,0,0x00ff00);
    setInstance(scene,0.5f,0.5f);drawRegion(scene,mesh,0,4,0,0x00ff00);
    scene.end();
    expect(indexed_draws-calls==1 && drawn_instances-instances==2,"shared opaque mesh uses one instanced draw");
    expectPixel("first instance transform",target.getPixel(8,16),0,255,0);
    expectPixel("second instance transform",target.getPixel(24,16),0,255,0);

    beginTarget(scene,target);scene.setZMode(BBScene::ZMODE_NORMAL);calls=indexed_draws;
    setInstance(scene,0.5f,-0.5f);drawRegion(scene,mesh,0,4,0,0xff0000);
    setInstance(scene,0.5f,0.5f);drawRegion(scene,mesh,0,4,0,0x00ff00);
    scene.end();expect(indexed_draws-calls==1,"per-copy tint stays in one instance batch");
    expectPixel("first instance material",target.getPixel(8,16),255,0,0);
    expectPixel("second instance material",target.getPixel(24,16),0,255,0);

    // Texture handles can change without any change to material uniform bytes.
    BBScene::RenderState state={};
    state.color[0]=state.color[1]=state.color[2]=1;state.alpha=1;
    state.fx=BBScene::FX_FULLBRIGHT|BBScene::FX_DOUBLESIDED;
    state.tex_states[0].blend=BBScene::BLEND_MULTIPLY;
    beginTarget(scene,target);scene.setZMode(BBScene::ZMODE_NORMAL);calls=indexed_draws;
    setInstance(scene,0.5f,-0.5f);state.tex_states[0].canvas=&red;
    scene.setRenderState(state);scene.render(mesh,0,4,0,2);
    setInstance(scene,0.5f,0.5f);state.tex_states[0].canvas=&blue;
    scene.setRenderState(state);scene.render(mesh,0,4,0,2);
    scene.end();expect(indexed_draws-calls==2,"texture change ends instance batch");
    expectPixel("first instance texture",target.getPixel(8,16),255,0,0);
    expectPixel("second instance texture",target.getPixel(24,16),0,0,255);

    beginTarget(scene,target);scene.setZMode(BBScene::ZMODE_NORMAL);calls=indexed_draws;
    scene.setViewport(0,0,16,32);drawRegion(scene,mesh,0,4,0,0xff0000);
    scene.setViewport(16,0,16,32);drawRegion(scene,mesh,0,4,0,0xff0000);
    scene.end();expect(indexed_draws-calls==2,"viewport change ends instance batch");
    expectPixel("first instance viewport",target.getPixel(8,16),255,0,0);
    expectPixel("second instance viewport",target.getPixel(24,16),255,0,0);

    beginTarget(scene,target);scene.setZMode(BBScene::ZMODE_NORMAL);calls=indexed_draws;
    setInstance(scene,0.5f,0);
    BBScene::Matrix view={};
    view.elements[0][0]=view.elements[1][1]=view.elements[2][2]=1;
    view.elements[3][0]=-0.5f;scene.setViewMatrix(&view);
    drawRegion(scene,mesh,0,4,0,0xff0000);
    view.elements[3][0]=0.5f;scene.setViewMatrix(&view);drawRegion(scene,mesh,0,4,0,0xff0000);
    scene.end();expect(indexed_draws-calls==2,"camera change ends instance batch");
    expectPixel("first instance camera",target.getPixel(8,16),255,0,0);
    expectPixel("second instance camera",target.getPixel(24,16),255,0,0);

    // A partial clear must execute after pending instances, not before them.
    beginTarget(scene,target);scene.setZMode(BBScene::ZMODE_NORMAL);
    drawRegion(scene,mesh,0,4,0,0xff0000);scene.setViewport(16,0,16,32);
    const float green[3]={0,1,0};scene.clear(green,1,1,true,true);scene.end();
    expectPixel("instance before partial clear",target.getPixel(8,16),255,0,0);
    expectPixel("partial clear after instance",target.getPixel(24,16),0,255,0);

    beginTarget(scene,target);scene.setZMode(BBScene::ZMODE_NORMAL);
    fillRegion(mesh,0,0,-1,0);drawRegion(scene,mesh,0,4,0,0xff0000);
    unsigned marker=scene.submit_marker;
    fillRegion(mesh,0,0,0,1);drawRegion(scene,mesh,0,4,0,0xff0000);scene.end();
    expect(scene.submit_marker==marker,"mesh mutation preserves pending instances without submission");
    expectPixel("instance before mesh mutation",target.getPixel(8,16),255,0,0);
    expectPixel("instance after mesh mutation",target.getPixel(24,16),255,0,0);

    fillQuad(mesh,10,front);state.tex_states[0]={};state.blend=BBScene::BLEND_ALPHA;
    state.alpha=0.5f;state.color[1]=state.color[2]=0;
    beginTarget(scene,target);scene.setZMode(BBScene::ZMODE_CMPONLY);calls=indexed_draws;
    scene.setRenderState(state);scene.render(mesh,0,4,0,2);scene.render(mesh,0,4,0,2);
    scene.end();expect(indexed_draws-calls==1,"adjacent transparent copies share one instanced draw");
    expectPixel("overlapping transparent copies",target.getPixel(16,16),192,0,0);

    // Copies in one draw still need their own normal matrices for lighting.
    WebGPULight light;light.type=Light::LIGHT_DISTANT;light.r=light.g=light.b=0.5f;
    beginTarget(scene,target);scene.begin({&light});scene.setZMode(BBScene::ZMODE_NORMAL);
    state.blend=BBScene::BLEND_REPLACE;state.alpha=1;state.color[1]=state.color[2]=1;
    state.fx=BBScene::FX_DOUBLESIDED;scene.setRenderState(state);calls=indexed_draws;
    setInstance(scene,0.5f,-0.5f);scene.render(mesh,0,4,0,2);
    BBScene::Matrix rotated={};
    rotated.elements[0][0]=-0.5f;rotated.elements[1][1]=1;rotated.elements[2][2]=-1;
    rotated.elements[3][0]=0.5f;rotated.elements[3][2]=20;
    scene.setWorldMatrix(&rotated);scene.render(mesh,0,4,0,2);scene.end();
    expect(indexed_draws-calls==1,"rotated copies share one instanced draw");
    expectPixel("front-lit instance normal",target.getPixel(8,16),128,128,128);
    expectPixel("back-lit instance normal",target.getPixel(24,16),0,0,0);

    // The instance arena fills independently of cached material uniforms.
    // Keep all but the two visible copies offscreen to avoid excessive overdraw.
    beginTarget(scene,target);scene.setZMode(BBScene::ZMODE_NORMAL);
    calls=indexed_draws;instances=drawn_instances;marker=scene.submit_marker;
    setInstance(scene,0.5f,-0.5f);drawRegion(scene,mesh,0,4,0,0x00ff00);
    for(int i=0;i<40000;++i){
        setInstance(scene,0.5f,4);drawRegion(scene,mesh,0,4,0,0x00ff00);
    }
    setInstance(scene,0.5f,0.5f);drawRegion(scene,mesh,0,4,0,0x00ff00);scene.end();
    expect(scene.submit_marker>marker && indexed_draws-calls==2 && drawn_instances-instances==40002,
           "instance arena overflow splits into two complete batches");
    expectPixel("instance before arena overflow",target.getPixel(8,16),0,255,0);
    expectPixel("instance after arena overflow",target.getPixel(24,16),0,255,0);
    scene.freeMesh(mesh);gx_canvas=nullptr;
}
static void checkTransparentInstances(WebGPUContextResources &res){
    WebGPUCanvas target(&res,32,32,BBCanvas::CANVAS_TEX_VIDMEM);
    WebGPUCanvas texture(&res,1,1,BBCanvas::CANVAS_TEXTURE|BBCanvas::CANVAS_TEX_ALPHA);
    WebGPUScene scene(&res);BBMesh *mesh=scene.createMesh(4,2,0);
    const float front[3]={0,0,-1};fillQuad(mesh,10,front);
    BBScene::RenderState state={};
    state.fx=BBScene::FX_FULLBRIGHT|BBScene::FX_DOUBLESIDED;
    state.blend=BBScene::BLEND_ALPHA;
    const float opacity[3]={0.5f,0.25f,0.75f};

    // The world already sorted these models. Instance order must preserve
    // non-commutative blending even when each tint and fade value differs.
    for(int reverse=0;reverse<2;++reverse){
        beginTarget(scene,target);scene.setZMode(BBScene::ZMODE_CMPONLY);
        unsigned calls=indexed_draws,instances=drawn_instances;
        for(int i=0;i<3;++i){
            int c=reverse?2-i:i;
            state.color[0]=state.color[1]=state.color[2]=0;state.color[c]=1;
            state.alpha=opacity[c];scene.setRenderState(state);scene.render(mesh,0,4,0,2);
        }
        scene.end();
        expect(indexed_draws-calls==1 && drawn_instances-instances==3,"tinted transparent copies use one ordered batch");
        if(reverse) expectPixel("reverse transparent order",target.getPixel(16,16),128,32,72);
        else expectPixel("forward transparent order",target.getPixel(16,16),24,16,191);
    }

    // A different texture between copies must split their batches, without
    // gathering the matching first and last copies across the middle one.
    const unsigned char white[4]={255,255,255,255};texture.uploadRGBA(white,1,1);
    beginTarget(scene,target);scene.setZMode(BBScene::ZMODE_CMPONLY);
    unsigned calls=indexed_draws;
    state.alpha=0.5f;
    for(int i=0;i<3;++i){
        state.color[0]=state.color[1]=state.color[2]=0;state.color[i]=1;
        state.tex_states[0].canvas=(i==1)?&texture:nullptr;
        state.tex_states[0].blend=BBScene::BLEND_MULTIPLY;
        scene.setRenderState(state);scene.render(mesh,0,4,0,2);
    }
    scene.end();expect(indexed_draws-calls==3,"interleaved material keeps three ordered batches");
    expectPixel("transparent order across material changes",target.getPixel(16,16),32,64,128);

    // Alpha-tested foliage retains the same silhouette as it fades. The
    // threshold is 128*entityAlpha, not the unfaded or preceding copy's value.
    state.fx|=BBScene::FX_ALPHATEST;
    state.tex_states[0].canvas=&texture;
    for(int coverage:{192,96}){
        const unsigned char texel[4]={255,255,255,(unsigned char)coverage};
        texture.uploadRGBA(texel,1,1);
        beginTarget(scene,target);scene.setZMode(BBScene::ZMODE_CMPONLY);calls=indexed_draws;
        for(int i=0;i<2;++i){
            setInstance(scene,0.5f,i?0.5f:-0.5f);
            state.color[0]=i?1:0;state.color[1]=i?0:1;state.color[2]=0;
            state.alpha=i?0.25f:1;scene.setRenderState(state);scene.render(mesh,0,4,0,2);
        }
        scene.end();expect(indexed_draws-calls==1,"alpha-test fade shares material uniforms");
        expectPixel("unfaded alpha-test coverage",target.getPixel(8,16),0,coverage>128?192:0,0);
        expectPixel("faded alpha-test coverage",target.getPixel(24,16),coverage>128?48:0,0,0);
    }

    // Transparent draws test opaque depth but must never write their own.
    beginTarget(scene,target);scene.setZMode(BBScene::ZMODE_NORMAL);
    drawRegion(scene,mesh,0,4,0,0x0000ff);
    scene.setZMode(BBScene::ZMODE_CMPONLY);state.tex_states[0]={};
    state.fx=BBScene::FX_FULLBRIGHT|BBScene::FX_DOUBLESIDED;state.alpha=0.5f;
    for(int i=0;i<3;++i){
        BBScene::Matrix world={};world.elements[0][0]=world.elements[1][1]=world.elements[2][2]=1;
        world.elements[3][2]=(i==0)?2:(i==1)?-2:-1;
        scene.setWorldMatrix(&world);
        state.color[0]=(i==0)?1:0;state.color[1]=(i==1)?1:0;state.color[2]=(i==2)?1:0;
        scene.setRenderState(state);scene.render(mesh,0,4,0,2);
    }
    scene.end();expectPixel("transparent depth test without writes",target.getPixel(16,16),0,64,191);
    scene.freeMesh(mesh);gx_canvas=nullptr;
}
class TransparentModel : public Model{
    BBMesh *mesh;
public:
    TransparentModel(BBMesh *mesh):mesh(mesh){}
    bool render(const RenderContext &) override{ enqueue(mesh,0,4,0,2);return false; }
};
static void checkWorldTransparency(WebGPUContextResources &res){
    WebGPUCanvas front(&res,32,32,BBCanvas::CANVAS_TEX_VIDMEM);
    WebGPUCanvas back(&res,32,32,BBCanvas::CANVAS_TEX_VIDMEM);
    WebGPUScene scene(&res);BBMesh *mesh=scene.createMesh(4,2,0);
    const float normal[3]={0,0,-1};fillQuad(mesh,0,normal);
    bbScene=&scene;gx_canvas=&front;
    {
        World world;
        Camera camera,reflection;
        camera.setViewport(0,0,32,32);camera.setCanvas(&front);camera.setProjMode(Camera::PROJ_ORTHO);
        reflection.setViewport(0,0,32,32);reflection.setCanvas(&back);reflection.setProjMode(Camera::PROJ_ORTHO);
        reflection.setLocalPosition(Vector(0,0,40));reflection.setLocalRotation(Quat(0,Vector(0,1,0)));
        TransparentModel nearModel(mesh),farModel(mesh),middle(mesh);
        nearModel.setLocalPosition(Vector(0,0,10));nearModel.setColor(Vector(0,0,1));nearModel.setAlpha(0.75f);
        farModel.setLocalPosition(Vector(0,0,30));farModel.setColor(Vector(1,0,0));farModel.setAlpha(0.5f);
        middle.setLocalPosition(Vector(0,0,20));middle.setColor(Vector(0,1,0));middle.setAlpha(0.25f);
        for(Model *model:{(Model*)&nearModel,(Model*)&farModel,(Model*)&middle})
            model->setFX(BBScene::FX_FULLBRIGHT|BBScene::FX_DOUBLESIDED);
        unsigned calls=indexed_draws;
        world.render(1);
        expect(indexed_draws-calls==2,"world batches sorted transparent models once per camera");
        expectPixel("world front camera depth order",front.getPixel(16,16),24,16,191);
        expectPixel("world reverse camera depth order",back.getPixel(16,16),128,32,72);
    }
    scene.freeMesh(mesh);bbScene=nullptr;gx_canvas=nullptr;
}
class CaptureCanvas : public WebGPUCanvas{
public:
    std::vector<std::vector<unsigned>> faces;
    CaptureCanvas(WebGPUContextResources *res):WebGPUCanvas(res,32,32,
        BBCanvas::CANVAS_TEXTURE|BBCanvas::CANVAS_TEX_RGB|BBCanvas::CANVAS_TEX_CUBE){}
    void blit(int x,int y,BBCanvas *src,int sx,int sy,int w,int h,bool solid) override{
        WebGPUCanvas::blit(x,y,src,sx,sy,w,h,solid);
        // Read each completed face before the next copy. This tests all six
        // views independently of the backend's cubemap storage/sampling.
        std::vector<unsigned char> rgba;
        readPixels(rgba);
        std::vector<unsigned> pixels;
        for(size_t i=0;i<rgba.size();i+=4)
            pixels.push_back((unsigned(rgba[i+3])<<24)|(unsigned(rgba[i])<<16)|
                             (unsigned(rgba[i+1])<<8)|unsigned(rgba[i+2]));
        faces.push_back(pixels);
    }
};
class CaptureModel : public Model{
    BBMesh *mesh;
public:
    unsigned preparations=0;
    CaptureModel(BBMesh *mesh):mesh(mesh){
        setFX(BBScene::FX_FULLBRIGHT|BBScene::FX_DOUBLESIDED);
    }
    bool beginRender(float tween) override{ ++preparations;return Model::beginRender(tween); }
    bool render(const RenderContext &rc) override{
        if(rc.getWorldFrustum().cull(Box(Vector(-1,-1,-0.1f),Vector(1,1,0.1f)),getRenderTform()))
            enqueue(mesh,0,4,0,2);
        return false;
    }
};
class CheckedMeshModel : public MeshModel{
public:
    bool matching=true;
    unsigned views=0;
    Box customBounds;
    CheckedMeshModel(){
        Surface *s=createSurface(Brush());
        for(Vector p:{Vector(-1,-1,0),Vector(1,-1,0),Vector(1,1,0),Vector(-1,1,0)}){
            Surface::Vertex v;v.coords=p;v.normal=Vector(0,0,-1);s->addVertex(v);
        }
        s->addTriangle(Surface::Triangle{{0,2,1}});s->addTriangle(Surface::Triangle{{0,3,2}});
        setFX(BBScene::FX_FULLBRIGHT|BBScene::FX_DOUBLESIDED);
    }
    CheckedMeshModel(const CheckedMeshModel &other):MeshModel(other){}
    void setTestBounds(const Box &b){ customBounds=b;setCullBox(b); }
    bool render(const RenderContext &rc) override{
        const Box &b=customBounds.empty()?getBox():customBounds;
        bool visible=!b.empty() && rc.getWorldFrustum().cull(b,getRenderTform());
        bool transparent=MeshModel::render(rc);
        matching&=visible==(queueSize(QUEUE_OPAQUE)+queueSize(QUEUE_TRANSPARENT)>0);
        ++views;
        return transparent;
    }
};
static void checkCubeCapture(WebGPUContextResources &res){
    WebGPUCanvas target(&res,32,32,BBCanvas::CANVAS_TEX_VIDMEM);
    WebGPUCanvas untouched(&res,32,32,BBCanvas::CANVAS_TEX_VIDMEM);
    CaptureCanvas reference(&res),batched(&res);
    WebGPUScene scene(&res);BBMesh *mesh=scene.createMesh(4,2,0);
    const float normal[3]={0,0,-1};fillQuad(mesh,0,normal);
    bbScene=&scene;gx_canvas=&target;
    {
        World world;
        Camera camera,other;
        camera.setViewport(0,0,32,32);camera.setCanvas(&target);camera.setRange(1,100);
        other.setViewport(0,0,32,32);other.setCanvas(&untouched);
        other.setClsColor(Vector(1,1,0));
        Object group;
        CheckedMeshModel boundsMesh,boundsCopy(boundsMesh),customBoundsMesh;
        boundsMesh.setParent(&group);boundsMesh.setLocalPosition(Vector(19.9f,0,20));
        boundsCopy.setLocalPosition(Vector(-19.9f,2,20));
        customBoundsMesh.setLocalPosition(Vector(0,19.9f,20));
        customBoundsMesh.setTestBounds(Box(Vector(-2,-2,-0.1f),Vector(2,2,0.1f)));
        std::vector<CaptureModel*> models;
        const Vector colors[6]={Vector(1,0,0),Vector(0,1,0),Vector(0,0,1),
                                Vector(1,1,0),Vector(1,0,1),Vector(0,1,1)};
        const float angles[6][2]={{0,90},{0,0},{0,-90},{0,180},{-90,0},{90,0}};
        const float radians=0.01745329251994329577f;
        for(int face=0;face<6;++face){
            Quat rot=rotationQuat(angles[face][0]*radians,angles[face][1]*radians,0);
            auto model=new CaptureModel(mesh);models.push_back(model);
            model->setLocalRotation(rot);model->setLocalPosition(Matrix(rot)*Vector(0,0,20));
            model->setLocalScale(Vector(8,8,1));model->setColor(colors[face]);
        }
        models[0]->setParent(&group);
        CaptureModel nearModel(mesh),farModel(mesh),hidden(mesh);
        nearModel.setLocalPosition(Vector(0,0,10));nearModel.setColor(Vector(1,0,0));
        nearModel.setAlpha(0.5f);nearModel.setAutoFade(8,14);
        farModel.setLocalPosition(Vector(0,0,15));farModel.setColor(Vector(0,0,1));farModel.setAlpha(0.25f);
        hidden.setVisible(false);
        auto compare=[&](const char *name,float tween,unsigned expectedPreparations){
            reference.faces.clear();batched.faces.clear();other.setVisible(false);
            for(int face=0;face<6;++face){
                reference.setCubeFace(face);
                camera.setLocalRotation(rotationQuat(angles[face][0]*radians,angles[face][1]*radians,0));
                world.render(tween);
                reference.blit(0,0,&target,0,0,32,32,true);
            }
            for(auto model:models) model->preparations=0;
            nearModel.preparations=farModel.preparations=hidden.preparations=0;
            unsigned previous=untouched.getPixel(16,16);
            other.setVisible(true);
            world.renderCubeMap(&camera,&batched,tween);
            expect(reference.faces.size()==6 && reference.faces==batched.faces,name);
            expect(nearModel.preparations==expectedPreparations && farModel.preparations==expectedPreparations,
                   "capture prepares scene only as often as required");
            expect(hidden.preparations==0,"capture preserves hidden objects");
            expect(previous==untouched.getPixel(16,16),"capture renders only the selected camera");
            expect(gx_canvas==&target,"capture restores the active drawing canvas");
            expect(boundsMesh.matching && boundsCopy.matching && customBoundsMesh.matching &&
                   boundsMesh.views>0 && boundsCopy.views>0 && customBoundsMesh.views>0,
                   "shared mesh bounds preserve per-view visibility and independent copy transforms");
        };
        compare("six capture views match legacy pixels, including transparent fade/order",1,1);
        expectPixel("capture right-face orientation",reference.faces[0][16*32+16],255,0,0);
        expectPixel("capture bottom-face orientation",reference.faces[5][16*32+16],0,255,255);
        // Every call must rebuild the scene; no snapshot survives a mutation.
        models[0]->setColor(Vector(0.2f,0.4f,0.8f));group.setLocalPosition(Vector(1,0,0));
        boundsMesh.setLocalScale(Vector(-2,0.5f,1));
        boundsCopy.setLocalRotation(rotationQuat(0,0.7f,0));
        boundsMesh.getSurfaces()[0]->setCoords(0,Vector(-4,-1,0));
        customBoundsMesh.setTestBounds(Box(Vector(3,-1,-0.1f),Vector(5,1,0.1f)));
        models[2]->setVisible(false);nearModel.setLocalPosition(Vector(0,0,12));
        compare("next capture observes transform, visibility and material changes",1,1);
        // Rotating the capture camera moves its children on every face.
        CaptureModel child(mesh);child.setParent(&camera);child.setLocalPosition(Vector(0,0,6));
        boundsCopy.setParent(&camera);boundsCopy.setLocalPosition(Vector(5.9f,0,6));
        child.setColor(Vector(0.6f,0.3f,0.1f));child.setAlpha(0.5f);
        compare("camera-attached geometry retains six preparations and correct pixels",1,6);
        child.setParent(nullptr);child.setVisible(false);
        boundsCopy.setParent(nullptr);
        world.capture();nearModel.setLocalPosition(Vector(0,0,13));
        boundsCopy.setLocalPosition(Vector(-10,0,12));group.setLocalScale(Vector(2,1,0.5f));
        compare("interpolated captures preserve legacy preparation semantics",0.5f,6);
        customBoundsMesh.setTestBounds(Box());
        compare("clearing custom bounds restores edited mesh bounds",1,1);
        for(auto model:models) delete model;
    }
    scene.freeMesh(mesh);bbScene=nullptr;gx_canvas=nullptr;
}
// Exercise the real terrain tessellator and renderer without adding a testing
// interface to TerrainRep. The reference recomputes the pre-cache formula.
class NormalTerrain : public Model{
public:
    TerrainRep terrain{4};
    bool shaded=true;
    NormalTerrain(){
        terrain.setShading(true);terrain.setDetail(2000,false);
        setLocalScale(Vector(1,12,1));setColor(Vector(0.4f,0.7f,0.2f));
        setFX(BBScene::FX_DOUBLESIDED);
    }
    bool render(const RenderContext &rc) override{ terrain.render(this,rc);return false; }
    Vector referenceNormal(int x,int z)const{
        if(!shaded) return Vector(0,1,0);
        const Vector center(x,terrain.getHeight(x,z),z),
            north(x,terrain.getHeight(x,z-1),z-1),
            east(x+1,terrain.getHeight(x+1,z),z),
            south(x,terrain.getHeight(x,z+1),z+1),
            west(x-1,terrain.getHeight(x-1,z),z);
        return (Plane(center,east,north).n+Plane(center,south,east).n+
                Plane(center,west,south).n+Plane(center,north,west).n).normalized();
    }
};
class TerrainNormalScene : public WebGPUScene{
public:
    using WebGPUScene::WebGPUScene;
    NormalTerrain *terrain=nullptr;
    bool reference=false,matching=true;
    std::set<std::pair<int,int>> checked;
    void render(BBMesh *mesh,int fv,int vc,int ft,int tc) override{
        auto gpu=static_cast<WebGPUMesh*>(mesh);
        for(int i=fv;i<fv+vc;++i){
            auto v=gpu->verts[i];
            int x=(int)v.coords[0],z=(int)v.coords[2];
            Vector n=terrain->referenceNormal(x,z);
            matching&=!std::memcmp(v.normal,&n.x,sizeof(v.normal));
            checked.insert({x,z});
            if(reference){
                std::memcpy(v.normal,&n.x,sizeof(v.normal));
                gpu->writeVertex(i,v);
            }
        }
        WebGPUScene::render(mesh,fv,vc,ft,tc);
    }
};
static void checkTerrainNormals(WebGPUContextResources &res){
    struct ReadbackCanvas : WebGPUCanvas{
        using WebGPUCanvas::WebGPUCanvas;
        using WebGPUCanvas::readPixels;
    } target(&res,64,64,BBCanvas::CANVAS_TEX_VIDMEM);
    TerrainNormalScene scene(&res);bbScene=&scene;gx_canvas=&target;
    {
        World world;Camera camera;Light light(Light::LIGHT_DISTANT);
        const float ambient[3]={0.1f,0.1f,0.1f};scene.setAmbient(ambient);
        light.setColor(Vector(0.7f,0.7f,0.7f));
        light.setLocalRotation(rotationQuat(0.8f,0.4f,0));
        camera.setViewport(0,0,64,64);camera.setCanvas(&target);
        camera.setProjMode(Camera::PROJ_ORTHO);camera.setZoom(1.0f/10);
        camera.setRange(1,100);camera.setLocalPosition(Vector(8,35,8));
        camera.setLocalRotation(rotationQuat(1.57079632679f,0,0));
        NormalTerrain first,second;second.setVisible(false);scene.terrain=&first;
        for(auto model:{&first,&second})
            for(int z=0;z<16;++z) for(int x=0;x<16;++x)
                model->terrain.setHeight(x,z,(((x+z)&1?200:30)+
                    (x*37+z*61+x*z*13+(model==&second?7:0))%17)/255.0f,false);
        auto compare=[&](const char *name){
            std::vector<unsigned char> actual,reference;
            scene.matching=true;scene.checked.clear();scene.reference=false;
            world.render(1);target.readPixels(actual);
            scene.reference=true;world.render(1);target.readPixels(reference);
            expect(scene.matching && !scene.checked.empty() && actual==reference,name);
            scene.reference=false;
            return actual;
        };
        auto initial=compare("cold terrain normals and lit pixels match legacy calculation");
        expect(scene.checked.size()==17*17,"normal comparison covers every grid point and wrapped edges");
        expect(compare("warm terrain cache preserves normals and pixels")==initial,"warm terrain pixels are unchanged");
        expect(std::any_of(initial.begin(),initial.end(),[](unsigned char c){return c!=0 && c!=255;}),
               "terrain pixel comparison contains lit geometry");
        first.terrain.setHeight(8,8,0.1f,false);
        first.terrain.setHeight(7,8,0.9f,false); // Already-invalid LOD errors must not skip normal invalidation.
        auto edited=compare("batched height edits refresh all affected normals");
        expect(edited!=initial,"height edit changes rendered terrain");
        first.terrain.setHeight(8,7,0.3f,true);
        compare("realtime height edit refreshes cached normals");
        for(const auto &point:{std::pair<int,int>{0,5},{16,6},{5,0},{6,16},{16,16}}){
            first.terrain.setHeight(point.first,point.second,0.95f,false);
            compare("height edit at wrapped edge refreshes both sides");
        }
        first.shaded=false;first.terrain.setShading(false);
        first.terrain.setHeight(9,9,0.9f,false);
        compare("unshaded terrain retains upward normals");
        first.shaded=true;first.terrain.setShading(true);
        compare("re-enabling shading observes edits made while unshaded");
        for(float yaw:{0.4f,1.2f,2.4f}){
            camera.setLocalRotation(rotationQuat(1.3f,yaw,0));
            first.terrain.setDetail(100,true);
            compare("camera and morphed LOD changes preserve legacy normals and pixels");
        }
        first.setVisible(false);second.setVisible(true);scene.terrain=&second;
        compare("separate terrains use independent normal caches");
        first.setVisible(true);second.setVisible(false);scene.terrain=&first;
        compare("returning to first terrain retains its own normals");
        first.terrain.clear();
        compare("clearing terrain invalidates cached normals");
    }
    bbScene=nullptr;gx_canvas=nullptr;
}
static void checkRemaster(WebGPUContextResources &res){
    WebGPUCanvas target(&res,32,32,BBCanvas::CANVAS_TEX_VIDMEM);
    WebGPUCanvas alpha(&res,1,1,BBCanvas::CANVAS_TEXTURE|BBCanvas::CANVAS_TEX_ALPHA);
    WebGPUScene scene(&res);BBMesh *mesh=scene.createMesh(4,2,0);
    const float normal[3]={0,0,-1},ambient[3]={0.1f,0.1f,0.1f},dark[3]={};
    BBScene::RenderState state={};
    state.color[0]=state.color[1]=state.color[2]=state.alpha=1;
    state.fx=BBScene::FX_DOUBLESIDED;
    WebGPULight light;light.type=Light::LIGHT_POINT;light.range=1;light.r=light.g=light.b=0.5f;
    fillQuad(mesh,1,normal);
    unsigned legacy=draw(scene,target,mesh,state,{&light},dark);
    scene.setRenderEffects(true,false,0);
    unsigned pixel=draw(scene,target,mesh,state,{&light},dark);
    expect((pixel&255)>(legacy&255)+60,"per-pixel point light resolves the interior of a large triangle");
    scene.setRenderEffects(false,false,0);
    expect(draw(scene,target,mesh,state,{&light},dark)==legacy,"disabling enhancements restores legacy pixels");

    scene.setRenderEffects(true,false,0);
    light.type=Light::LIGHT_DISTANT;light.r=light.g=light.b=0.6f;
    const float depth[4]={0,0,1,0};
    auto capture=[&](int coverage,bool second){
        gx_canvas=&target;scene.begin({&light});scene.setCanvas(32,32);
        scene.setShadowLight(&light,depth,20,50);
        const unsigned char rgba[4]={255,255,255,(unsigned char)coverage};alpha.uploadRGBA(rgba,1,1);
        for(int cascade=0;cascade<2;++cascade){
            scene.beginShadow(cascade);
            scene.setViewMatrix(nullptr);scene.setWorldMatrix(nullptr);
            scene.setOrthoProj(0.1f,50,-1,1,-1,1);
            scene.setZMode(BBScene::ZMODE_NORMAL);
            auto caster=state;caster.fx|=BBScene::FX_ALPHATEST;
            caster.tex_states[0].canvas=&alpha;caster.tex_states[0].blend=BBScene::BLEND_MULTIPLY;
            fillQuad(mesh,5,normal);
            scene.setRenderState(caster);
            if(cascade==0 || second) scene.render(mesh,0,4,0,2);
            scene.endShadow(cascade);
        }
        scene.end();
    };
    capture(255,true);fillQuad(mesh,10,normal);
    expectPixel("sun shadow preserves ambient",draw(scene,target,mesh,state,{&light},ambient),26,26,26);
    fillQuad(mesh,30,normal);
    expectPixel("far cascade shadows receivers",draw(scene,target,mesh,state,{&light},ambient),26,26,26);
    capture(255,false);fillQuad(mesh,30,normal);
    expectPixel("far cascade is independent of the near layer",draw(scene,target,mesh,state,{&light},ambient),179,179,179);
    capture(96,true);fillQuad(mesh,10,normal);
    expectPixel("transparent leaf texels cast no shadow",draw(scene,target,mesh,state,{&light},ambient),179,179,179);
    capture(192,true);fillQuad(mesh,10,normal);
    expectPixel("opaque leaf texels cast a shadow",draw(scene,target,mesh,state,{&light},ambient),26,26,26);
    fillQuad(mesh,60,normal);
    expectPixel("sun shadow ends at configured distance",draw(scene,target,mesh,state,{&light},ambient),179,179,179);
    scene.setShadowLight(nullptr,depth,0,0);fillQuad(mesh,10,normal);
    expectPixel("disabling sun shadows removes stale captures",draw(scene,target,mesh,state,{&light},ambient),179,179,179);
    scene.freeMesh(mesh);gx_canvas=nullptr;
}

static void checkPlanarCapture(WebGPUContextResources &res){
    WebGPUCanvas target(&res,32,32,BBCanvas::CANVAS_TEX_VIDMEM);
    WebGPUCanvas reflection(&res,32,32,BBCanvas::CANVAS_TEXTURE|BBCanvas::CANVAS_TEX_RGB);
    WebGPUScene scene(&res);BBMesh *mesh=scene.createMesh(4,2,0);
    const float normal[3]={0,0,-1};fillQuad(mesh,0,normal);
    bbScene=&scene;gx_canvas=&target;
    {
        World world;Object parent;Camera camera;Object child;
        parent.setLocalPosition(Vector(10,0,0));
        camera.setParent(&parent);camera.setLocalPosition(Vector(0,2,0));
        child.setParent(&camera);child.setLocalPosition(Vector(0,1,0));
        camera.setViewport(0,0,32,32);camera.setCanvas(&target);camera.setRange(1,100);
        CaptureModel above(mesh),below(mesh);
        above.setLocalPosition(Vector(10,2,6));above.setLocalScale(Vector(2,2,1));above.setColor(Vector(1,0,0));
        below.setLocalPosition(Vector(10,-2,4));below.setColor(Vector(0,1,0));
        const Vector before=camera.getWorldPosition(),childBefore=child.getWorldPosition();
        world.renderPlanar(&camera,&reflection,0,1);
        expectPixel("planar reflection retains above-water geometry",reflection.getPixel(16,26),255,0,0);
        expectPixel("planar reflection clips submerged geometry",reflection.getPixel(16,16),0,0,0);
        expect(camera.getWorldPosition()==before && child.getWorldPosition()==childBefore,
               "planar capture preserves parented camera and children");
        expect(gx_canvas==&target,"planar capture restores active canvas");
        world.render(1);
        expectPixel("ordinary world after reflection restores camera and clip",target.getPixel(16,16),255,0,0);
    }
    scene.freeMesh(mesh);bbScene=nullptr;gx_canvas=nullptr;
}

static void checkWaterCoverage(WebGPUContextResources &res){
    struct ReadbackCanvas : WebGPUCanvas{
        using WebGPUCanvas::WebGPUCanvas;
        using WebGPUCanvas::readPixels;
    } target(&res,128,128,BBCanvas::CANVAS_TEX_VIDMEM);
    WebGPUCanvas reflection(&res,1,1,BBCanvas::CANVAS_TEXTURE|BBCanvas::CANVAS_TEX_RGB);
    const unsigned char rgba[4]={20,100,20,255};reflection.uploadRGBA(rgba,1,1);
    WebGPUScene scene(&res);BBMesh *mesh=scene.createMesh(4,2,0);
    const float positions[4][3]={{-2000,0,20},{2000,0,20},{2000,0,1800},{-2000,0,1800}};
    const float normal[3]={0,1,0},uv[2][2]={{0.5f,0.5f},{0.5f,0.5f}};
    mesh->lock(true);
    for(int i=0;i<4;++i) mesh->setVertex(i,positions[i],normal,uv);
    mesh->setTriangle(0,0,2,1);mesh->setTriangle(1,0,3,2);mesh->unlock();
    BBScene::RenderState state={};
    state.color[0]=state.color[1]=state.color[2]=state.alpha=1;
    state.fx=BBScene::FX_FULLBRIGHT|BBScene::FX_DOUBLESIDED|BBScene::FX_WATER;
    state.blend=BBScene::BLEND_ALPHA;
    state.tex_states[0].canvas=&reflection;state.tex_states[0].blend=BBScene::BLEND_MULTIPLY;
    const float background[3]={0.6f,0.8f,1.0f};
    Transform eye(Matrix(),Vector(0,30,0)),inverse=-eye;
    auto render=[&](float seconds){
        gx_canvas=&target;scene.begin({});scene.setCanvas(128,128);scene.setViewport(0,0,128,128);
        scene.setViewMatrix((BBScene::Matrix*)&inverse);scene.setWorldMatrix(nullptr);
        scene.setPerspProj(1,2000,-1,1,1,-1);scene.captureReflectionMatrix();
        scene.setFogMode(BBScene::FOG_NONE);scene.setZMode(BBScene::ZMODE_NORMAL);
        scene.clear(background,1,1,true,true);scene.setRenderEffects(false,false,seconds);
        scene.setRenderState(state);scene.render(mesh,0,4,0,2);scene.end();
        std::vector<unsigned char> pixels;target.readPixels(pixels);return pixels;
    };
    const auto first=render(0),later=render(3);
    int changed=0,maxDelta=0;
    for(int y=67;y<100;++y) for(int x=8;x<120;++x){
        const int i=(y*128+x)*4;
        if(first[i]<140) ++changed;
        for(int c=0;c<3;++c) maxDelta=std::max(maxDelta,std::abs(int(first[i+c])-int(later[i+c])));
    }
    expect(changed>3000,"water coverage fixture contains a reflected surface at grazing angles");
    std::printf("Water coverage phase delta: %d / 255\n",maxDelta);
    expect(maxDelta<=2,"wave phase distorts reflection coordinates without flashing its opacity in bands");
    scene.freeMesh(mesh);gx_canvas=nullptr;
}

int main(){
    std::setvbuf(stdout,nullptr,_IONBF,0);
    checkFrustum();
    WebGPUContextResources res;
    if(!initOffscreen(res)){ std::fprintf(stderr,"No offscreen D3D12 device\n");return 2; }
    checkRendering(res);
    checkBatchedRendering(res);
    checkMeshVersions(res);
    checkInstancing(res);
    checkTransparentInstances(res);
    checkWorldTransparency(res);
    checkCubeCapture(res);
    checkTerrainNormals(res);
    checkRemaster(res);
    checkPlanarCapture(res);
    checkWaterCoverage(res);
    res.destroy();
    std::printf("%d rendering failures (offscreen, no window)\n",failures);
    return failures?1:0;
}
