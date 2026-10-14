package dev.dexdroid;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** An uncluttered first-launch desktop; its parent supplies rotation and Fit. */
final class IdleDesktop extends FrameLayout {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final ControllerStyle style;
 private Shader backdrop;
 IdleDesktop(Context context,Runnable openApps){
  super(context);style=new ControllerStyle(context);setTag("monitor-idle");
  setWillNotDraw(false);setClickable(true);setForceDarkAllowed(false);
  ImageButton apps=style.iconAction(R.drawable.ic_apps,"Apps",openApps);
  apps.setTag("monitor-idle-apps");apps.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
  apps.setPadding(style.dp(22),style.dp(22),style.dp(22),style.dp(22));
  apps.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
  apps.setBackground(style.ripple(style.surface(18,24,45)));
  addView(apps,new FrameLayout.LayoutParams(style.dp(76),style.dp(76),Gravity.CENTER));
 }
 @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
  super.onSizeChanged(w,h,oldw,oldh);
  backdrop=new LinearGradient(0,0,w,h,new int[]{Color.rgb(7,7,7),Color.rgb(19,19,19)},null,Shader.TileMode.CLAMP);
 }
 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);paint.setShader(backdrop);paint.setStyle(Paint.Style.FILL);canvas.drawRect(0,0,getWidth(),getHeight(),paint);paint.setShader(null);
  // Sparse architectural lines keep the first-launch surface calm and monochrome.
  paint.setColor(Color.rgb(28,28,28));paint.setStrokeWidth(style.dp(1));
  float inset=style.dp(32),line=Math.min(getWidth(),getHeight())*.16f;
  canvas.drawLine(inset,inset,inset+line,inset,paint);
  canvas.drawLine(inset,inset,inset,inset+line,paint);
  canvas.drawLine(getWidth()-inset,getHeight()-inset,getWidth()-inset-line,getHeight()-inset,paint);
  canvas.drawLine(getWidth()-inset,getHeight()-inset,getWidth()-inset,getHeight()-inset-line,paint);
  paint.setColor(Color.rgb(96,96,96));paint.setTextSize(style.dp(12));paint.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));paint.setTextAlign(Paint.Align.CENTER);
  canvas.drawText("DexDroid",getWidth()/2f,getHeight()-style.dp(30),paint);
 }
}
