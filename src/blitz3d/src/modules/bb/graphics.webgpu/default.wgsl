
struct BBRenderState {
  xywh : vec4<f32>,
  res : vec2<f32>,
  texscale : vec2<f32>,
  color : vec3<f32>,
  texenabled : i32,
  scale : vec2<f32>,
  image_mask : u32,
  image_mode : u32,
};

@group(0) @binding(0) var<uniform> RS : BBRenderState;
@group(0) @binding(1) var u_sampler : sampler;
@group(0) @binding(2) var u_tex : texture_2d<f32>;

struct BBPerVertex {
  @builtin(position) position : vec4<f32>,
  @location(0) color : vec3<f32>,
  @location(1) texcoord : vec2<f32>,
};

@vertex
fn vs_main( @location(0) a_position : vec2<f32>,
            @location(1) a_texcoord : vec2<f32> ) -> BBPerVertex {
  var v : BBPerVertex;

  let xy = RS.xywh.xy;
  let wh = RS.xywh.zw;

  var p = (a_position * wh) + xy;

  p = p * RS.scale;

  p.y = RS.res.y - p.y;
  p = p / RS.res;

  p = p * 2.0 - vec2<f32>( 1.0,1.0 );

  v.position = vec4<f32>( p,0.0,1.0 );
  v.color = RS.color;
  v.texcoord = a_texcoord * RS.texscale;
  return v;
}

fn ditherNoise( p : vec2<f32> ) -> f32 {
  let k = vec2<f32>( 0.06711056, 0.00583715 );
  let a = fract( 52.9829189 * fract( dot( p, k ) ) );
  let b = fract( 52.9829189 * fract( dot( p + vec2<f32>( 17.0, 43.0 ), k ) ) );
  return a + b - 1.0;
}

fn luma( c : vec3<f32> ) -> f32 {
  return dot( c, vec3<f32>( 0.299, 0.587, 0.114 ) );
}

fn fxaa( uv : vec2<f32> ) -> vec4<f32> {
  let texel = 1.0 / vec2<f32>( textureDimensions( u_tex ) );
  let m = textureSampleLevel( u_tex, u_sampler, uv, 0.0 );
  let nw = textureSampleLevel( u_tex, u_sampler, uv + vec2<f32>( -1.0, -1.0 ) * texel, 0.0 ).rgb;
  let ne = textureSampleLevel( u_tex, u_sampler, uv + vec2<f32>( 1.0, -1.0 ) * texel, 0.0 ).rgb;
  let sw = textureSampleLevel( u_tex, u_sampler, uv + vec2<f32>( -1.0, 1.0 ) * texel, 0.0 ).rgb;
  let se = textureSampleLevel( u_tex, u_sampler, uv + vec2<f32>( 1.0, 1.0 ) * texel, 0.0 ).rgb;
  let lnw = luma( nw );
  let lne = luma( ne );
  let lsw = luma( sw );
  let lse = luma( se );
  let lm = luma( m.rgb );
  let lmin = min( lm, min( min( lnw, lne ), min( lsw, lse ) ) );
  let lmax = max( lm, max( max( lnw, lne ), max( lsw, lse ) ) );
  if( lmax - lmin < max( 1.0 / 16.0, lmax * ( 1.0 / 8.0 ) ) ){
    return m;
  }
  var dir = vec2<f32>( -( ( lnw + lne ) - ( lsw + lse ) ), ( lnw + lsw ) - ( lne + lse ) );
  let reduce = max( ( lnw + lne + lsw + lse ) * 0.03125, 1.0 / 128.0 );
  let rcp = 1.0 / ( min( abs( dir.x ), abs( dir.y ) ) + reduce );
  dir = clamp( dir * rcp, vec2<f32>( -8.0 ), vec2<f32>( 8.0 ) ) * texel;
  let a = 0.5 * ( textureSampleLevel( u_tex, u_sampler, uv + dir * ( 1.0 / 3.0 - 0.5 ), 0.0 ).rgb
                + textureSampleLevel( u_tex, u_sampler, uv + dir * ( 2.0 / 3.0 - 0.5 ), 0.0 ).rgb );
  let b = a * 0.5 + 0.25 * ( textureSampleLevel( u_tex, u_sampler, uv + dir * -0.5, 0.0 ).rgb
                           + textureSampleLevel( u_tex, u_sampler, uv + dir * 0.5, 0.0 ).rgb );
  let lb = luma( b );
  if( lb < lmin || lb > lmax ){
    return vec4<f32>( a, m.a );
  }
  return vec4<f32>( b, m.a );
}

@fragment
fn fs_main( v : BBPerVertex ) -> @location(0) vec4<f32> {
  if( RS.texenabled>=1 ){
    var c : vec4<f32>;
    if( RS.texenabled==3 || RS.texenabled==5 ){
      c = fxaa( v.texcoord ) * vec4<f32>( v.color,1.0 );
    }else if( RS.texenabled==4 ){
      c = vec4<f32>( v.color, textureSample( u_tex,u_sampler,v.texcoord ).r );
    }else{
      c = textureSample( u_tex,u_sampler,v.texcoord ) * vec4<f32>( v.color,1.0 );
    }
    if( RS.image_mode!=0u ){
      let rgb = vec3<u32>(round(clamp(c.rgb, vec3<f32>(0.0), vec3<f32>(1.0))*255.0));
      let key = vec3<u32>((RS.image_mask>>16u)&255u, (RS.image_mask>>8u)&255u, RS.image_mask&255u);
      if( RS.image_mode==1u && all(rgb==key) ){ discard; }
      // DirectDraw's DrawImage/DrawBlock copy RGB; the key is their only alpha.
      c.a = 1.0;
    }
    if( RS.texenabled==2 || RS.texenabled==3 ){
      c = vec4<f32>( c.rgb + ditherNoise( v.position.xy ) / 255.0, c.a );
    }
    return c;
  }
  return vec4<f32>( v.color,1.0 );
}
