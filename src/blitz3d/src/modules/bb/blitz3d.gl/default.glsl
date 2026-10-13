precision highp float;
precision highp int;

uniform mat4 bbProjMatrix;
uniform mat4 bbViewMatrix;
uniform mat4 bbWorldMatrix;

uniform sampler2D bbTexture[8];
uniform samplerCube bbTextureCube[8];

#define FOG_NONE   0
#define FOG_LINEAR 1

struct BBLightData {
  mat4 TForm;
  vec4 Color;
  vec4 Params;
} ;

layout(std140) uniform BBLightState {
  BBLightData Light[8];

  int LightsUsed;
} LS;

struct BBTextureState {
  mat4 TForm;
  int Blend,SphereMap,Flags,CubeMap;
} ;

layout(std140) uniform BBRenderState {
  vec4 Ambient;
  vec4 BrushColor;
  vec4 FogColor;

  BBTextureState Texture[8];

  vec2 FogRange;

  int TexturesUsed;
  int UseVertexColor;
  float BrushShininess;
  int FullBright;
  int FogMode;
  int AlphaTest;
  float AlphaRef;
} RS;

#ifdef VERTEX
#define varying out
#else
#define varying in
#endif

varying vec3 bbVertex_Position;
varying vec4 bbVertex_Color;
varying vec3 bbVertex_Normal;
varying vec2 bbVertex_TexCoord[8];
varying float bbVertex_FogFactor;

mat4 rotationMatrix(vec3 axis, float angle){
  axis = normalize(axis);
  float s = sin(angle);
  float c = cos(angle);
  float oc = 1.0 - c;

  return mat4(oc * axis.x * axis.x + c,           oc * axis.x * axis.y - axis.z * s,  oc * axis.z * axis.x + axis.y * s,  0.0,
              oc * axis.x * axis.y + axis.z * s,  oc * axis.y * axis.y + c,           oc * axis.y * axis.z - axis.x * s,  0.0,
              oc * axis.z * axis.x - axis.y * s,  oc * axis.y * axis.z + axis.x * s,  oc * axis.z * axis.z + c,           0.0,
              0.0,                                0.0,                                0.0,                                1.0);
}

float fogFactorLinear(
  const float dist,
  const float start,
  const float end
) {
  return 1.0 - clamp((end - dist) / (end - start), 0.0, 1.0);
}

#ifdef VERTEX

layout(location = 0) in vec3 bbPosition;
layout(location = 1) in vec3 bbNormal;
layout(location = 2) in vec4 bbColor;
layout(location = 3) in vec2 bbTexCoord0;
layout(location = 4) in vec2 bbTexCoord1;

void main() {
  mat4 bbModelViewMatrix = bbViewMatrix * bbWorldMatrix;
  mat4 bbModelViewProjectionMatrix=bbProjMatrix * bbModelViewMatrix;
  mat3 bbNormalMatrix=transpose(inverse(mat3(bbModelViewMatrix)));

  bbVertex_Position = bbPosition;

  vec3 ecPosition = (bbModelViewMatrix * vec4(bbPosition, 1.0)).xyz;

  gl_Position = bbModelViewProjectionMatrix * vec4(bbPosition, 1.0);

  vec3 EyeNormal = bbNormalMatrix * bbNormal;
  float bbNsq = dot(EyeNormal, EyeNormal);
  vec3 UnitNormal = bbNsq > 0.0 ? EyeNormal*inversesqrt(bbNsq) : vec3(0.0);

  for( int i=0;i<RS.TexturesUsed;i++ ){
    vec2 coord;
    if( RS.Texture[i].SphereMap==1 ) {
      coord = UnitNormal.xy*vec2(0.5,-0.5)+vec2(0.5,0.5);
    }else if( (RS.Texture[i].Flags & 1)!=0 ) {
      coord=bbTexCoord1;
    }else {
      coord=bbTexCoord0;
    }
    bbVertex_TexCoord[i] = (RS.Texture[i].TForm * vec4(coord, 1.0, 1.0)).xy;
  }


  vec4 bbMaterialColor;
  if( RS.UseVertexColor>0 ){
    bbMaterialColor = bbColor;
  }else{
    bbMaterialColor = RS.BrushColor;
  }

  if( RS.FullBright==0 ){
    bbVertex_Normal = UnitNormal;

    vec4 Diffuse=vec4( 0.0 ),Specular=vec4( 0.0 );

    for( int i=0;i<LS.LightsUsed;i++ ){
      float nDotVP,nDotHV,pf;

      vec3 LightPos=normalize( mat3( bbViewMatrix*LS.Light[i].TForm*rotationMatrix( vec3(1.0,0.0,0.0), 1.5708 ) )*vec3(0.0,1.0,0.0) );
      float atten=1.0;

      if( LS.Light[i].Params.x>1.5 ){
        vec3 lightPosEye=( bbViewMatrix*vec4( LS.Light[i].TForm[3].xyz,1.0 ) ).xyz;
        vec3 toLight=lightPosEye-ecPosition;
        float dist=max( length( toLight ),0.0001 );
        vec3 dirToLight=toLight/dist;
        atten=LS.Light[i].Params.y/dist;
        if( LS.Light[i].Params.x>2.5 ){
          float cosA=dot( dirToLight,LightPos );
          atten*=clamp( (cosA-LS.Light[i].Params.z)/max( LS.Light[i].Params.w-LS.Light[i].Params.z,0.0001 ),0.0,1.0 );
        }
        LightPos=dirToLight;
      }

      vec3 halfVector = normalize( LightPos+vec3( 0.0,0.0,-1.0 ));

      nDotVP = max( 0.0,dot( bbVertex_Normal,LightPos ) );
      nDotHV = max( 0.0,dot( bbVertex_Normal,vec3( halfVector )));
      pf = pow( nDotHV,100.0 )*float(nDotVP!=0.0);

      Diffuse  += LS.Light[i].Color * (nDotVP*atten);
      Specular += LS.Light[i].Color * (pf*atten);
    }

    bbVertex_Color = RS.Ambient * bbMaterialColor +
                     Diffuse    * bbMaterialColor +
                     Specular   * vec4( 1.0 );
    bbVertex_Color = clamp( bbVertex_Color, 0.0, 1.0 );
    bbVertex_Color.a = bbMaterialColor.a;
  }else{
    bbVertex_Color = bbMaterialColor;
  }

  switch( RS.FogMode ){
  case FOG_NONE: break;
  case FOG_LINEAR: bbVertex_FogFactor=fogFactorLinear( abs( ecPosition.z ),RS.FogRange.x,RS.FogRange.y );break;
  }
}
#endif

#ifdef FRAGMENT

out vec4 bbFragColor;

#define BLEND_REPLACE   0
#define BLEND_ALPHA     1
#define BLEND_MULTIPLY  2
#define BLEND_ADD       3
#define BLEND_DOT3      4
#define BLEND_MULTIPLY2 5

vec4 Sample2D( sampler2D tex,int i ){
  return texture( tex,bbVertex_TexCoord[i] );
}

vec4 SampleCube( samplerCube tex ){
  vec3 coord = normalize(vec3(bbVertex_Position.x,-bbVertex_Position.y,bbVertex_Position.z));
  return texture( tex,coord );
}

vec4 Blend( vec4 t0,vec4 t1,int i ){
  switch( RS.Texture[i].Blend ){
  default:
  case BLEND_REPLACE:  return t0;
  case BLEND_ALPHA:    return mix(t0, t1, t1.a);
  case BLEND_MULTIPLY: return t0*t1;
  case BLEND_ADD:      return t0+t1;
  case BLEND_DOT3: {
    float d = clamp( 4.0*dot( t0.rgb-vec3(0.5),t1.rgb-vec3(0.5) ),0.0,1.0 );
    return vec4( d,d,d,t0.a );
  }
  case BLEND_MULTIPLY2: return vec4( clamp( t0.rgb*t1.rgb*2.0,vec3(0.0),vec3(1.0) ),t0.a*t1.a );
  }
}

void main() {
  bbFragColor=bbVertex_Color;

  #define ProcessTexture(i) if( i<RS.TexturesUsed ) bbFragColor=Blend( bbFragColor,RS.Texture[i].CubeMap!=1?Sample2D(bbTexture[i],i):SampleCube(bbTextureCube[i]),i );
  ProcessTexture(0);
  ProcessTexture(1);
  ProcessTexture(2);
  ProcessTexture(3);
  ProcessTexture(4);
  ProcessTexture(5);
  ProcessTexture(6);
  ProcessTexture(7);

  if( RS.FogMode>0 ){
    vec4 fogColor=vec4( RS.FogColor.rgb,bbFragColor.a );
    bbFragColor=mix( bbFragColor,fogColor,bbVertex_FogFactor );
  }

  if( RS.AlphaTest==1 && bbFragColor.a*255.0<=RS.AlphaRef ){
    discard;
  }
}
#endif
