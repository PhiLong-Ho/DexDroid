package dev.dexdroid;

/** Pure coordinate model shared by the rendered surface and the cursor. */
public final class Geometry {
 public static float[] map(float u,float v,int rotation,float width,float height,int desktopW,int desktopH){
  float aspect=(rotation%2==0)?(float)desktopW/desktopH:(float)desktopH/desktopW;
  float contentW=Math.min(width,height*aspect),contentH=contentW/aspect;
  float x=u,y=v;
  switch(rotation){case 1:x=1-v;y=u;break;case 2:x=1-u;y=1-v;break;case 3:x=v;y=1-u;break;}
  return new float[]{(width-contentW)/2+x*contentW,(height-contentH)/2+y*contentH};
 }
 public static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
 /** Physical monitor point to virtual pixels; bars return null. */
 public static float[] unmap(float x,float y,int rotation,float width,float height,int desktopW,int desktopH){
  if(width<=0||height<=0)return null;
  float aspect=(rotation%2==0)?(float)desktopW/desktopH:(float)desktopH/desktopW;
  float contentW=Math.min(width,height*aspect),contentH=contentW/aspect;
  float u=(x-(width-contentW)/2)/contentW,v=(y-(height-contentH)/2)/contentH;
  if(u<0||v<0||u>=1||v>=1)return null;
  float a=u,b=v;
  switch(rotation){case 1:a=v;b=1-u;break;case 2:a=1-u;b=1-v;break;case 3:a=1-v;b=u;break;}
  return new float[]{clamp(a*desktopW,0,desktopW-1),clamp(b*desktopH,0,desktopH-1)};
 }
 public static int recommendedDpi(int shortEdge){return Math.max(120,Math.min(480,Math.round(shortEdge/5f/20)*20));}
 private Geometry(){}
}
