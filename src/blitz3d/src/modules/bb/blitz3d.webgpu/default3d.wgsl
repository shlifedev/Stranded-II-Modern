
const FOG_NONE : i32 = 0;
const FOG_LINEAR : i32 = 1;

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
};

struct BBTexState {
  tform : mat4x4<f32>,
  blend : i32,
  sphere_map : i32,
  flags : i32,
  alpha : i32,
};

struct BBEntityState {
  world : mat4x4<f32>,
  normal_mat : mat4x4<f32>,
  ambient : vec4<f32>,
  brush_color : vec4<f32>,
  fog_color : vec4<f32>,
  fog_range : vec2<f32>,
  texs_used : i32,
  use_vertex_color : i32,
  brush_shininess : f32,
  fullbright : i32,
  fog_mode : i32,
  alpha_test : i32,
  alpha_ref : f32,
  texs : array<BBTexState,8>,
};

@group(0) @binding(0) var<uniform> FS : BBFrameState;
@group(1) @binding(0) var<uniform> RS : BBEntityState;

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
            @location(11) normal2 : vec4<f32> ) -> BBVertexOut {
  var v : BBVertexOut;

  let modelview = FS.view * mat4x4<f32>( world0, world1, world2, world3 );
  let eye_pos = modelview * vec4<f32>( bbPosition, 1.0 );
  let position = FS.proj * eye_pos;
  v.position = position;

  let eye_normal = mat3x3<f32>( normal0.xyz, normal1.xyz, normal2.xyz ) * bbNormal;

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
    mat_color = RS.brush_color;
  }

  v.specular = vec3<f32>( 0.0 );
  if( RS.fullbright==0 ){
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

@fragment
fn fs_main( v : BBVertexOut ) -> @location(0) vec4<f32> {
  var color = v.color;

  if( 0<RS.texs_used ){ color = combine( color, textureSampleBias( bbTexture0,bbSampler0,v.tc01.xy,FS.lod_bias ), RS.texs[0].blend, RS.texs[0].alpha ); }
  if( 1<RS.texs_used ){ color = combine( color, textureSampleBias( bbTexture1,bbSampler1,v.tc01.zw,FS.lod_bias ), RS.texs[1].blend, RS.texs[1].alpha ); }
  if( 2<RS.texs_used ){ color = combine( color, textureSampleBias( bbTexture2,bbSampler2,v.tc23.xy,FS.lod_bias ), RS.texs[2].blend, RS.texs[2].alpha ); }
  if( 3<RS.texs_used ){ color = combine( color, textureSampleBias( bbTexture3,bbSampler3,v.tc23.zw,FS.lod_bias ), RS.texs[3].blend, RS.texs[3].alpha ); }
  if( 4<RS.texs_used ){ color = combine( color, textureSampleBias( bbTexture4,bbSampler4,v.tc45.xy,FS.lod_bias ), RS.texs[4].blend, RS.texs[4].alpha ); }
  if( 5<RS.texs_used ){ color = combine( color, textureSampleBias( bbTexture5,bbSampler5,v.tc45.zw,FS.lod_bias ), RS.texs[5].blend, RS.texs[5].alpha ); }
  if( 6<RS.texs_used ){ color = combine( color, textureSampleBias( bbTexture6,bbSampler6,v.tc67.xy,FS.lod_bias ), RS.texs[6].blend, RS.texs[6].alpha ); }
  if( 7<RS.texs_used ){ color = combine( color, textureSampleBias( bbTexture7,bbSampler7,v.tc67.zw,FS.lod_bias ), RS.texs[7].blend, RS.texs[7].alpha ); }

  // Fixed-function specular is added after texturing, before fog/blending.
  color = vec4<f32>( clamp( color.rgb + v.specular, vec3<f32>( 0.0 ), vec3<f32>( 1.0 ) ), color.a );

  if( RS.fog_mode>0 ){
    let fog_color = vec4<f32>( RS.fog_color.rgb, color.a );
    color = mix( color, fog_color, v.fog_factor );
  }

  if( RS.alpha_test==1 && color.a*255.0<=RS.alpha_ref ){
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
