
const FOG_NONE : i32 = 0;
const FOG_LINEAR : i32 = 1;
override pixel_lighting : bool = false;
override atmosphere : bool = false;
override sun_shadows : bool = false;
override shadow_capture : bool = false;

struct BBLightData {
  dir : vec4<f32>,
  pos : vec4<f32>,
  color : vec4<f32>,
  params : vec4<f32>,
};

struct BBFrameState {
  proj : mat4x4<f32>,
  view : mat4x4<f32>,
  lights : array<BBLightData,8>,
  lights_used : i32,
  lod_bias : f32,
  clip_plane : vec4<f32>,
  reflection : mat4x4<f32>,
  eye : vec4<f32>,
  effects : vec4<f32>,
  shadow : array<mat4x4<f32>,2>,
  shadow_depth : vec4<f32>,
  shadow_params : vec4<f32>,
};

struct BBTexState {
  tform : mat4x4<f32>,
  blend : i32,
  sphere_map : i32,
  flags : i32,
  alpha : i32,
};

struct BBEntityState {
  ambient : vec4<f32>,
  fog_color : vec4<f32>,
  fog_range : vec2<f32>,
  texs_used : i32,
  use_vertex_color : i32,
  brush_shininess : f32,
  fullbright : i32,
  fog_mode : i32,
  alpha_test : i32,
  alpha_ref : f32,
  water : i32,
  clip_enabled : i32,
  padding : i32,
  texs : array<BBTexState,8>,
};

@group(0) @binding(0) var<uniform> FS : BBFrameState;
@group(1) @binding(0) var<uniform> RS : BBEntityState;
@group(3) @binding(0) var sunDepth : texture_depth_2d_array;
@group(3) @binding(1) var sunSampler : sampler_comparison;

@group(2) @binding(0)  var bbTexture0 : texture_2d<f32>;
@group(2) @binding(1)  var bbSampler0 : sampler;
@group(2) @binding(2)  var bbTexture1 : texture_2d<f32>;
@group(2) @binding(3)  var bbSampler1 : sampler;
@group(2) @binding(4)  var bbTexture2 : texture_2d<f32>;
@group(2) @binding(5)  var bbSampler2 : sampler;
@group(2) @binding(6)  var bbTexture3 : texture_2d<f32>;
@group(2) @binding(7)  var bbSampler3 : sampler;
@group(2) @binding(8)  var bbTexture4 : texture_2d<f32>;
@group(2) @binding(9)  var bbSampler4 : sampler;
@group(2) @binding(10) var bbTexture5 : texture_2d<f32>;
@group(2) @binding(11) var bbSampler5 : sampler;
@group(2) @binding(12) var bbTexture6 : texture_2d<f32>;
@group(2) @binding(13) var bbSampler6 : sampler;
@group(2) @binding(14) var bbTexture7 : texture_2d<f32>;
@group(2) @binding(15) var bbSampler7 : sampler;

fn mat3of( m : mat4x4<f32> ) -> mat3x3<f32> {
  return mat3x3<f32>( m[0].xyz, m[1].xyz, m[2].xyz );
}

fn fogFactorLinear( dist : f32, start : f32, end : f32 ) -> f32 {
  return 1.0 - clamp( (end - dist) / (end - start), 0.0, 1.0 );
}

fn sphereMap( normal : vec3<f32>, ecPosition3 : vec3<f32> ) -> vec2<f32> {
  let u = normalize( ecPosition3 );
  let r = reflect( u, normal );
  let m = 2.0 * sqrt( r.x*r.x + r.y*r.y + (r.z + 1.0)*(r.z + 1.0) );
  return vec2<f32>( r.x/m + 0.5, r.y/m + 0.5 );
}

struct BBVertexOut {
  @builtin(position) position : vec4<f32>,
  @location(0) color : vec4<f32>,
  @location(1) fog_factor : f32,
  @location(2) tc01 : vec4<f32>,
  @location(3) tc23 : vec4<f32>,
  @location(4) tc45 : vec4<f32>,
  @location(5) tc67 : vec4<f32>,
  @location(6) specular : vec3<f32>,
  @location(7) @interpolate(flat) alpha_ref : f32,
  @location(8) world_pos : vec3<f32>,
  @location(9) eye_pos : vec3<f32>,
  @location(10) eye_normal : vec3<f32>,
};

fn layerCoord( i : i32, eye_normal : vec3<f32>, ndc : vec3<f32>,
               uv0 : vec2<f32>, uv1 : vec2<f32> ) -> vec2<f32> {
  if( i>=RS.texs_used ){
    return vec2<f32>( 0.0 );
  }
  var coord : vec2<f32>;
  if( RS.texs[i].sphere_map==1 ){
    coord = sphereMap( eye_normal, ndc );
  }else if( RS.texs[i].flags==1 ){
    coord = uv1;
  }else{
    coord = uv0;
  }
  return ( RS.texs[i].tform * vec4<f32>( coord, 0.0, 1.0 ) ).xy;
}

@vertex
fn vs_main( @location(0) bbPosition : vec3<f32>,
            @location(1) bbNormal : vec3<f32>,
            @location(2) bbColor : vec4<f32>,
            @location(3) bbTexCoord0 : vec2<f32>,
            @location(4) bbTexCoord1 : vec2<f32>,
            @location(5) world0 : vec4<f32>,
            @location(6) world1 : vec4<f32>,
            @location(7) world2 : vec4<f32>,
            @location(8) world3 : vec4<f32>,
            @location(9) normal0 : vec4<f32>,
            @location(10) normal1 : vec4<f32>,
            @location(11) normal2 : vec4<f32>,
            @location(12) brush_color : vec4<f32> ) -> BBVertexOut {
  var v : BBVertexOut;
  v.alpha_ref = RS.alpha_ref * brush_color.a;

  let modelview = FS.view * mat4x4<f32>( world0, world1, world2, world3 );
  let eye_pos = modelview * vec4<f32>( bbPosition, 1.0 );
  let position = FS.proj * eye_pos;
  v.position = position;

  let eye_normal = mat3x3<f32>( normal0.xyz, normal1.xyz, normal2.xyz ) * bbNormal;
  v.world_pos = (mat4x4<f32>(world0,world1,world2,world3) * vec4<f32>(bbPosition,1.0)).xyz;
  v.eye_pos = eye_pos.xyz;
  v.eye_normal = eye_normal;

  var w = position.w;
  if( w==0.0 ){ w = 1.0; }
  var ndc = position.xyz / w;
  ndc.z = ndc.z * 2.0 - 1.0;

  v.tc01 = vec4<f32>( layerCoord( 0, eye_normal, ndc, bbTexCoord0, bbTexCoord1 ),
                      layerCoord( 1, eye_normal, ndc, bbTexCoord0, bbTexCoord1 ) );
  v.tc23 = vec4<f32>( layerCoord( 2, eye_normal, ndc, bbTexCoord0, bbTexCoord1 ),
                      layerCoord( 3, eye_normal, ndc, bbTexCoord0, bbTexCoord1 ) );
  v.tc45 = vec4<f32>( layerCoord( 4, eye_normal, ndc, bbTexCoord0, bbTexCoord1 ),
                      layerCoord( 5, eye_normal, ndc, bbTexCoord0, bbTexCoord1 ) );
  v.tc67 = vec4<f32>( layerCoord( 6, eye_normal, ndc, bbTexCoord0, bbTexCoord1 ),
                      layerCoord( 7, eye_normal, ndc, bbTexCoord0, bbTexCoord1 ) );

  var mat_color : vec4<f32>;
  if( RS.use_vertex_color>0 ){
    mat_color = bbColor;
  }else{
    mat_color = brush_color;
  }

  v.specular = vec3<f32>( 0.0 );
  if( RS.fullbright==0 && !shadow_capture && ((!pixel_lighting && !atmosphere) || RS.clip_enabled==0) ){
    let n = normalize( eye_normal );

    var diffuse  = vec4<f32>( 0.0 );
    var specular = vec3<f32>( 0.0 );

    for( var i : i32 = 0; i<FS.lights_used; i++ ){
      var light_pos = FS.lights[i].dir.xyz;
      var atten = 1.0;

      if( FS.lights[i].params.x>1.5 ){
        let light_pos_eye = FS.lights[i].pos.xyz;
        let to_light = light_pos_eye - eye_pos.xyz;
        let dist = max( length( to_light ), 0.0001 );
        let dir_to_light = to_light / dist;
        atten = FS.lights[i].params.y / dist;
        if( FS.lights[i].params.x>2.5 ){
          let cos_a = dot( dir_to_light, light_pos );
          atten *= clamp( (cos_a - FS.lights[i].params.z) / max( FS.lights[i].params.w - FS.lights[i].params.z, 0.0001 ), 0.0, 1.0 );
        }
        light_pos = dir_to_light;
      }

      let n_dot_vp = max( 0.0, dot( n, light_pos ) );
      diffuse  += FS.lights[i].color * (n_dot_vp*atten);
      // D3D7 disables specular for matte brushes, uses a local viewer,
      // material power = shininess*128, and white light specular colour.
      if( RS.brush_shininess>0.0 && n_dot_vp>0.0 ){
        let view_dir = -eye_pos.xyz / max( length( eye_pos.xyz ), 0.0001 );
        let half_dir = light_pos + view_dir;
        let half_vector = half_dir / max( length( half_dir ), 0.0001 );
        let n_dot_hv = max( 0.0, dot( n, half_vector ) );
        let pf = pow( n_dot_hv, RS.brush_shininess*128.0 );
        specular += vec3<f32>( pf*atten*min( RS.brush_shininess, 1.0 ) );
      }
    }

    v.color = RS.ambient * mat_color +
              diffuse    * mat_color;
    v.color = clamp( v.color, vec4<f32>( 0.0 ), vec4<f32>( 1.0 ) );
    v.color.a = mat_color.a;
    v.specular = clamp( specular, vec3<f32>( 0.0 ), vec3<f32>( 1.0 ) );
  }else{
    v.color = mat_color;
  }

  v.fog_factor = 0.0;
  if( RS.fog_mode==FOG_LINEAR ){
    v.fog_factor = fogFactorLinear( abs( eye_pos.z ), RS.fog_range.x, RS.fog_range.y );
  }

  return v;
}

fn combine( t0 : vec4<f32>, t1 : vec4<f32>, mode : i32, has_alpha : i32 ) -> vec4<f32> {
  var rgb : vec3<f32>;
  switch( mode ){
    case 1: {
      rgb = mix( t0.rgb, t1.rgb, t1.a );
    }
    case 2: {
      rgb = t0.rgb*t1.rgb;
    }
    case 3: {
      rgb = t0.rgb+t1.rgb;
    }
    case 4: {
      let d = dot( t0.rgb*2.0-vec3<f32>(1.0), t1.rgb*2.0-vec3<f32>(1.0) );
      rgb = vec3<f32>( d );
    }
    case 5: {
      rgb = t0.rgb*t1.rgb*2.0;
    }
    default: {
      rgb = t0.rgb;
    }
  }
  var a = t0.a;
  if( has_alpha==1 ){
    a = t0.a*t1.a;
  }
  return clamp( vec4<f32>( rgb, a ), vec4<f32>( 0.0 ), vec4<f32>( 1.0 ) );
}

struct PixelLight {
  color : vec4<f32>,
  specular : vec3<f32>,
};

fn safeNormal(p : vec3<f32>) -> vec3<f32> {
  return p / max(length(p), 0.0001);
}

fn cascadeShadow(p : vec3<f32>, cascade : i32, bias : f32) -> f32 {
  let projected = FS.shadow[cascade]*vec4<f32>(p,1.0);
  let uv = vec2<f32>(projected.x,-projected.y)*0.5+0.5;
  if(any(uv<vec2<f32>(0.002)) || any(uv>vec2<f32>(0.998)) ||
     projected.z<=0.0 || projected.z>=1.0) { return 1.0; }
  var visibility = 0.0;
  for(var y=-1;y<=1;y++) {
    for(var x=-1;x<=1;x++) {
      visibility += textureSampleCompareLevel(sunDepth,sunSampler,
        uv+vec2<f32>(f32(x),f32(y))/1024.0,cascade,projected.z-bias);
    }
  }
  return visibility/9.0;
}

fn sunShadow(p : vec3<f32>, ndl : f32) -> f32 {
  let depth = dot(FS.shadow_depth,vec4<f32>(p,1.0));
  let split = FS.shadow_params.x;
  let far = FS.shadow_params.y;
  if(FS.shadow_params.z<0.5 || depth<0.0 || depth>=far) { return 1.0; }
  let bias = 0.00012+0.0003*(1.0-ndl);
  var visibility = 1.0;
  if(depth<split*0.85) {
    visibility = cascadeShadow(p,0,bias);
  } else if(depth<split) {
    visibility = mix(cascadeShadow(p,0,bias),cascadeShadow(p,1,bias),
      smoothstep(split*0.85,split,depth));
  } else {
    visibility = cascadeShadow(p,1,bias);
  }
  return mix(visibility,1.0,smoothstep(far*0.85,far,depth));
}

fn pixelLight(v : BBVertexOut) -> PixelLight {
  let n = safeNormal(v.eye_normal);
  var diffuse = vec3<f32>(0.0);
  var specular = vec3<f32>(0.0);
  for(var i=0; i<FS.lights_used; i++) {
    var direction = FS.lights[i].dir.xyz;
    var attenuation = 1.0;
    if(FS.lights[i].params.x>1.5) {
      let delta = FS.lights[i].pos.xyz-v.eye_pos;
      let distance = max(length(delta),0.0001);
      let to_light = delta/distance;
      attenuation = FS.lights[i].params.y/distance;
      if(FS.lights[i].params.x>2.5) {
        attenuation *= clamp((dot(to_light,direction)-FS.lights[i].params.z) /
          max(FS.lights[i].params.w-FS.lights[i].params.z,0.0001),0.0,1.0);
      }
      direction = to_light;
    }
    let ndl = max(dot(n,direction),0.0);
    if(sun_shadows && ndl>0.0 && f32(i)==FS.shadow_params.w) { attenuation *= sunShadow(v.world_pos,ndl); }
    diffuse += FS.lights[i].color.rgb * (ndl*attenuation);
    if(RS.brush_shininess>0.0 && ndl>0.0) {
      let h = safeNormal(direction-safeNormal(v.eye_pos));
      specular += vec3<f32>(pow(max(dot(n,h),0.0),RS.brush_shininess*128.0) *
        attenuation*min(RS.brush_shininess,1.0));
    }
  }
  var ambient = RS.ambient.rgb;
  if(atmosphere) {
    let up = clamp(dot(n,safeNormal(FS.view[1].xyz))*0.5+0.5,0.0,1.0);
    ambient *= mix(vec3<f32>(0.64,0.66,0.69),vec3<f32>(1.0,1.0,1.0),up);
  }
  var result : PixelLight;
  result.color = vec4<f32>(clamp((ambient+diffuse)*v.color.rgb,vec3<f32>(0.0),vec3<f32>(1.0)),v.color.a);
  result.specular = clamp(specular,vec3<f32>(0.0),vec3<f32>(1.0));
  return result;
}

fn waterNormal(p : vec3<f32>) -> vec3<f32> {
  let a = dot(p.xz,vec2<f32>(0.035,0.021)) + FS.effects.z*1.15;
  let b = dot(p.xz,vec2<f32>(-0.018,0.051)) - FS.effects.z*0.83;
  return normalize(vec3<f32>(0.11*cos(a)-0.045*cos(b),1.0,0.066*cos(a)+0.128*cos(b)));
}

@fragment
fn fs_shadow(v : BBVertexOut) {
  var alpha = v.color.a;
  if(0<RS.texs_used && RS.texs[0].alpha==1) { alpha *= textureSampleBias(bbTexture0,bbSampler0,v.tc01.xy,FS.lod_bias).a; }
  if(1<RS.texs_used && RS.texs[1].alpha==1) { alpha *= textureSampleBias(bbTexture1,bbSampler1,v.tc01.zw,FS.lod_bias).a; }
  if(2<RS.texs_used && RS.texs[2].alpha==1) { alpha *= textureSampleBias(bbTexture2,bbSampler2,v.tc23.xy,FS.lod_bias).a; }
  if(3<RS.texs_used && RS.texs[3].alpha==1) { alpha *= textureSampleBias(bbTexture3,bbSampler3,v.tc23.zw,FS.lod_bias).a; }
  if(4<RS.texs_used && RS.texs[4].alpha==1) { alpha *= textureSampleBias(bbTexture4,bbSampler4,v.tc45.xy,FS.lod_bias).a; }
  if(5<RS.texs_used && RS.texs[5].alpha==1) { alpha *= textureSampleBias(bbTexture5,bbSampler5,v.tc45.zw,FS.lod_bias).a; }
  if(6<RS.texs_used && RS.texs[6].alpha==1) { alpha *= textureSampleBias(bbTexture6,bbSampler6,v.tc67.xy,FS.lod_bias).a; }
  if(7<RS.texs_used && RS.texs[7].alpha==1) { alpha *= textureSampleBias(bbTexture7,bbSampler7,v.tc67.zw,FS.lod_bias).a; }
  if(RS.alpha_test==1 && alpha*255.0<=v.alpha_ref) { discard; }
}

@fragment
fn fs_main( v : BBVertexOut ) -> @location(0) vec4<f32> {
  var color = v.color;
  var specular = v.specular;
  var uv0 = v.tc01.xy;
  if(RS.water==2) { uv0 += waterNormal(v.world_pos).xz*0.055; }
  // Sample before discarding: preserve derivatives and avoid lighting invisible foliage.
  var texel0 = vec4<f32>(1.0);
  if(0<RS.texs_used) { texel0 = textureSampleBias(bbTexture0,bbSampler0,uv0,FS.lod_bias); }
  var texel1 = vec4<f32>(1.0);
  if(1<RS.texs_used) { texel1 = textureSampleBias(bbTexture1,bbSampler1,v.tc01.zw,FS.lod_bias); }
  var texel2 = vec4<f32>(1.0);
  if(2<RS.texs_used) { texel2 = textureSampleBias(bbTexture2,bbSampler2,v.tc23.xy,FS.lod_bias); }
  var texel3 = vec4<f32>(1.0);
  if(3<RS.texs_used) { texel3 = textureSampleBias(bbTexture3,bbSampler3,v.tc23.zw,FS.lod_bias); }
  var texel4 = vec4<f32>(1.0);
  if(4<RS.texs_used) { texel4 = textureSampleBias(bbTexture4,bbSampler4,v.tc45.xy,FS.lod_bias); }
  var texel5 = vec4<f32>(1.0);
  if(5<RS.texs_used) { texel5 = textureSampleBias(bbTexture5,bbSampler5,v.tc45.zw,FS.lod_bias); }
  var texel6 = vec4<f32>(1.0);
  if(6<RS.texs_used) { texel6 = textureSampleBias(bbTexture6,bbSampler6,v.tc67.xy,FS.lod_bias); }
  var texel7 = vec4<f32>(1.0);
  if(7<RS.texs_used) { texel7 = textureSampleBias(bbTexture7,bbSampler7,v.tc67.zw,FS.lod_bias); }
  var coverage = v.color.a;
  if(0<RS.texs_used && RS.texs[0].alpha==1) { coverage *= texel0.a; }
  if(1<RS.texs_used && RS.texs[1].alpha==1) { coverage *= texel1.a; }
  if(2<RS.texs_used && RS.texs[2].alpha==1) { coverage *= texel2.a; }
  if(3<RS.texs_used && RS.texs[3].alpha==1) { coverage *= texel3.a; }
  if(4<RS.texs_used && RS.texs[4].alpha==1) { coverage *= texel4.a; }
  if(5<RS.texs_used && RS.texs[5].alpha==1) { coverage *= texel5.a; }
  if(6<RS.texs_used && RS.texs[6].alpha==1) { coverage *= texel6.a; }
  if(7<RS.texs_used && RS.texs[7].alpha==1) { coverage *= texel7.a; }
  if(RS.water!=1 && RS.alpha_test==1 && coverage*255.0<=v.alpha_ref) { discard; }
  if(RS.fullbright==0 && RS.clip_enabled!=0 && (pixel_lighting || atmosphere)) {
    let light = pixelLight(v);
    color = light.color;
    specular = light.specular;
  }

  if( 0<RS.texs_used ){ color = combine( color, texel0, RS.texs[0].blend, RS.texs[0].alpha ); }
  if( 1<RS.texs_used ){ color = combine( color, texel1, RS.texs[1].blend, RS.texs[1].alpha ); }
  if( 2<RS.texs_used ){ color = combine( color, texel2, RS.texs[2].blend, RS.texs[2].alpha ); }
  if( 3<RS.texs_used ){ color = combine( color, texel3, RS.texs[3].blend, RS.texs[3].alpha ); }
  if( 4<RS.texs_used ){ color = combine( color, texel4, RS.texs[4].blend, RS.texs[4].alpha ); }
  if( 5<RS.texs_used ){ color = combine( color, texel5, RS.texs[5].blend, RS.texs[5].alpha ); }
  if( 6<RS.texs_used ){ color = combine( color, texel6, RS.texs[6].blend, RS.texs[6].alpha ); }
  if( 7<RS.texs_used ){ color = combine( color, texel7, RS.texs[7].blend, RS.texs[7].alpha ); }

  // Fixed-function specular is added after texturing, before fog/blending.
  color = vec4<f32>( clamp( color.rgb + specular, vec3<f32>( 0.0 ), vec3<f32>( 1.0 ) ), color.a );

  if(RS.water==1) {
    let normal = waterNormal(v.world_pos);
    let view = safeNormal(FS.eye.xyz-v.world_pos);
    // The capture represents the mean horizontal surface. Wave slopes distort
    // its coordinates, not its coverage: at grazing angles their oscillating
    // dot product made dark foliage reflections flash in bright/dark bands.
    let fresnel = 0.02 + 0.98*pow(1.0-clamp(view.y,0.0,1.0),5.0);
    let reflected = FS.reflection*vec4<f32>(v.world_pos,1.0);
    let uv = vec2<f32>(reflected.x,-reflected.y)/max(reflected.w,0.001)*0.5+0.5;
    let warped = clamp(uv+normal.xz*0.012,vec2<f32>(0.002),vec2<f32>(0.998));
    let reflection = textureSampleLevel(bbTexture0,bbSampler0,warped,0.0).rgb;
    color = vec4<f32>(reflection,clamp(0.06+fresnel*0.88,0.0,0.94)*v.color.a);
  }

  if( RS.fog_mode>0 ){
    let fog_color = vec4<f32>( RS.fog_color.rgb, color.a );
    var factor = v.fog_factor;
    if(atmosphere && RS.clip_enabled!=0) { factor = fogFactorLinear(abs(v.eye_pos.z),RS.fog_range.x,RS.fog_range.y); }
    color = mix( color, fog_color, factor );
  }

  if(RS.clip_enabled!=0 && dot(vec4<f32>(v.world_pos,1.0),FS.clip_plane)<0.0) {
    discard;
  }
  if( RS.alpha_test==1 && color.a*255.0<=v.alpha_ref ){
    discard;
  }

  return color;
}


struct BBClearState {
  color : vec4<f32>,
  z : f32,
};

@group(0) @binding(0) var<uniform> CS : BBClearState;

struct BBClearOut {
  @builtin(position) position : vec4<f32>,
};

@vertex
fn vs_clear( @builtin(vertex_index) vid : u32 ) -> BBClearOut {
  var p : vec2<f32>;
  switch( vid ){
    case 0u:      { p = vec2<f32>( -1.0,-3.0 ); }
    case 1u:      { p = vec2<f32>(  3.0, 1.0 ); }
    default:      { p = vec2<f32>( -1.0, 1.0 ); }
  }
  var o : BBClearOut;
  o.position = vec4<f32>( p, CS.z, 1.0 );
  return o;
}

@fragment
fn fs_clear() -> @location(0) vec4<f32> {
  return CS.color;
}
