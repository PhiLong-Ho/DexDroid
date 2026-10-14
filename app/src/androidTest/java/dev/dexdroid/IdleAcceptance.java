package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real rotated monitor and phone-pointer interaction with the first-launch surface. */
final class IdleAcceptance {
 private final Instrumentation runner;
 private final StringBuilder results=new StringBuilder();
 private SessionService session;
 private ServiceConnection binding;
 private boolean bound;
 private Map<String,?> preferences;
 private long down;
 IdleAcceptance(Instrumentation runner){this.runner=runner;}
 private void main(Runnable action){runner.runOnMainSync(action);}
 private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);results.append("PASS ").append(label).append('\n');}
 private void await(BooleanSupplier predicate,String label)throws Exception{long until=SystemClock.uptimeMillis()+10000;while(!predicate.getAsBoolean()&&SystemClock.uptimeMillis()<until)Thread.sleep(50);check(predicate.getAsBoolean(),label);}
 private void restore(){if(preferences==null||session==null)return;android.content.SharedPreferences.Editor edit=session.prefs.edit().clear();for(Map.Entry<String,?> item:preferences.entrySet()){Object value=item.getValue();String key=item.getKey();if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Long)edit.putLong(key,(Long)value);}edit.commit();}
 private View view(String tag){View root=session.monitorShellView();return root==null?null:root.findViewWithTag(tag);}
 private void page(String expected)throws Exception{await(()->{boolean[] settled={false};main(()->{
  View root=session.monitorShellView();if(root==null||!expected.equals(session.monitorShellPage())||session.monitorShellAnimating()||!root.isLaidOut()||root.isLayoutRequested())return;
  if(!expected.equals("closed")){View panel=view("monitor-panel"),close=view("monitor-close");if(panel==null||close==null||!panel.isLaidOut()||panel.isLayoutRequested()||!close.isShown()||close.getWidth()==0||close.getHeight()==0||close.isLayoutRequested())return;}
  settled[0]=true;
 });return settled[0];},"idle menu settled "+expected);}

 private void fresh()throws Exception{main(session::pauseDesktop);await(()->session.id<0&&session.monitorShellView()==null,"old desktop released");main(session::ensureDesktop);await(()->session.id>=0&&!session.busy&&session.monitorShellView()!=null,"fresh desktop ready");Thread.sleep(300);check(!session.hasDesktopApp(),"fresh desktop has not launched an app");page("closed");}
 private void event(int action,float x,float y)throws Exception{long now=SystemClock.uptimeMillis();if(action==MotionEvent.ACTION_DOWN)down=now;MotionEvent event=MotionEvent.obtain(down,now,action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{MotionEvent.class.getMethod("setDisplayId",int.class).invoke(event,session.external.getDisplayId());check(runner.getUiAutomation().injectInputEvent(event,true),"idle physical monitor event "+action);}finally{event.recycle();}}
 private void tap(float x,float y)throws Exception{event(MotionEvent.ACTION_DOWN,x,y);event(MotionEvent.ACTION_UP,x,y);Thread.sleep(80);}
 private float[] physical(float x,float y){Display.Mode mode=session.external.getMode();return Geometry.map(x/session.width(),y/session.height(),session.rotation,mode.getPhysicalWidth(),mode.getPhysicalHeight(),session.width(),session.height());}
 private Rect bounds(String tag){Rect rect=new Rect();main(()->{View target=view(tag);check(target!=null&&target.isShown()&&target.getGlobalVisibleRect(rect),"idle control visible "+tag);});return rect;}
 private void click(String tag)throws Exception{Rect rect=bounds(tag);tap(rect.exactCenterX(),rect.exactCenterY());}
 private void pointerClick(String tag)throws Exception{main(()->{
  View target=view(tag),root=session.monitorShellView();check(target!=null&&target.isShown(),"idle pointer target visible "+tag);
  Rect visible=new Rect();Matrix toGlobal=new Matrix(),rootToGlobal=new Matrix(),toLogical=new Matrix();target.transformMatrixToGlobal(toGlobal);root.transformMatrixToGlobal(rootToGlobal);boolean invertible=rootToGlobal.invert(toLogical);
  check(target.getWidth()>0&&target.getHeight()>0&&target.getGlobalVisibleRect(visible)&&!visible.isEmpty()&&invertible,"idle pointer target has measured hittable bounds "+tag);
  // Include actual animation/scroll transforms rather than assuming every
  // ancestor has only its layout left/top offsets.
  float[] center={target.getWidth()/2f,target.getHeight()/2f};toGlobal.mapPoints(center);toLogical.mapPoints(center);session.cx=center[0];session.cy=center[1];int[] delivered={0,0};
  target.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN)delivered[0]++;if(event.getActionMasked()==MotionEvent.ACTION_UP)delivered[1]++;return false;});
  try{session.click();}finally{target.setOnTouchListener(null);}
  check(delivered[0]==1&&delivered[1]==1,"phone pointer dispatch reaches the target once for DOWN and UP "+tag);
 });Thread.sleep(80);}
 private void idle(boolean shown){main(()->{View idle=view("monitor-idle"),apps=view("monitor-idle-apps");check(idle!=null&&apps instanceof ImageButton,"native idle background and icon exist");check(idle.isShown()==shown&&apps.isShown()==shown,"idle visibility "+shown);if(shown){ImageButton icon=(ImageButton)apps;check(icon.getDrawable()!=null&&"Apps".contentEquals(icon.getContentDescription()),"idle icon has Apps description and drawable");float density=icon.getResources().getDisplayMetrics().density;check(Math.abs(icon.getWidth()/density-76)<1&&Math.abs(icon.getHeight()/density-76)<1,"idle Apps target is 76 dp square");check(!idle.isForceDarkAllowed(),"idle monochrome surface preserves its colors");}});}
 private void centered(){Rect rect=bounds("monitor-idle-apps");Display.Mode mode=session.external.getMode();check(Math.abs(rect.exactCenterX()-mode.getPhysicalWidth()/2f)<2&&Math.abs(rect.exactCenterY()-mode.getPhysicalHeight()/2f)<2,"idle Apps centered in physical rotation "+session.rotation);main(()->{View idle=view("monitor-idle");check(idle.getWidth()==session.width()&&idle.getHeight()==session.height(),"idle background fills logical rotated desktop");});}
 private void rendered()throws Exception{Display.Mode mode=session.external.getMode();Bitmap bitmap=Bitmap.createBitmap(mode.getPhysicalWidth(),mode.getPhysicalHeight(),Bitmap.Config.ARGB_8888);CountDownLatch copied=new CountDownLatch(1);int[] result={-1};try{main(()->PixelCopy.request(session.monitorWindow(),bitmap,value->{result[0]=value;copied.countDown();},new Handler(Looper.getMainLooper())));check(copied.await(5,TimeUnit.SECONDS)&&result[0]==PixelCopy.SUCCESS,"idle rendering captured");for(float[] logical:new float[][]{{session.width()*.1f,session.height()*.5f},{session.width()*.9f,session.height()*.5f}}){float[] p=physical(logical[0],logical[1]);int color=bitmap.getPixel(Math.round(p[0]),Math.round(p[1]));check(Color.red(color)==Color.green(color)&&Color.red(color)==Color.blue(color)&&Color.red(color)<40,"rendered idle background is dark monochrome");}Rect rect=bounds("monitor-idle-apps");int white=0,dark=0;for(int y=rect.top;y<rect.bottom;y++)for(int x=rect.left;x<rect.right;x++){int color=bitmap.getPixel(x,y);if(Color.red(color)>200&&Color.green(color)>200&&Color.blue(color)>200)white++;if(Color.red(color)<50&&Color.green(color)<50&&Color.blue(color)<50)dark++;}check(white>20&&dark>white,"rendered idle Apps icon is white on charcoal");}finally{bitmap.recycle();}}
 String run()throws Exception{try{
  runner.startActivitySync(new Intent(runner.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));CountDownLatch connected=new CountDownLatch(1);binding=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){session=((SessionService.LocalBinder)b).get();connected.countDown();}public void onServiceDisconnected(ComponentName n){}};bound=runner.getTargetContext().bindService(new Intent(runner.getTargetContext(),SessionService.class),binding,Context.BIND_AUTO_CREATE);check(connected.await(5,TimeUnit.SECONDS),"idle controller bound");await(()->session.id>=0&&!session.busy,"initial desktop ready");preferences=new HashMap<>(session.prefs.getAll());fresh();int idleDisplay=session.id;main(()->session.key(KeyEvent.KEYCODE_BACK));page("closed");check(session.id==idleDisplay&&!session.hasDesktopApp(),"idle Back preserves the unused desktop");main(()->{session.setMonitorNavigationEnabled(true);session.setMonitorTouchEnabled(true);});
  for(int r=0;r<4;r++){final int rotation=r;main(()->session.rotate(rotation,false));await(()->!session.busy&&session.rotation==rotation,"idle rotation ready "+r);Thread.sleep(250);idle(true);centered();rendered();click("monitor-idle-apps");page("home");idle(false);check(!session.hasDesktopApp(),"opening Apps does not mark an app launched");click("monitor-close");page("closed");idle(true);pointerClick("monitor-idle-apps");page("home");check(session.pointerVisible,"phone pointer restores cursor on idle launcher");pointerClick("monitor-close");page("closed");idle(true);click("monitor-idle-apps");page("home");float[] outside=physical(4*session.dpi/160f,session.height()*.5f);tap(outside[0],outside[1]);page("closed");idle(true);float[] blank=physical(session.width()*.25f,session.height()*.5f);tap(blank[0],blank[1]);page("closed");check(!session.hasDesktopApp(),"blank idle tap does not select an app");check(!session.pointerVisible,"physical idle tap hides phone pointer");main(()->{session.cx=session.width()*.25f;session.cy=session.height()*.5f;session.click();session.wheel(0,-2);});page("closed");idle(true);}
  main(()->{session.clearDiagnostics();session.launch(new ComponentName("dev.dexdroid","dev.dexdroid.DoesNotExist"),null);});await(()->session.diagnosticText().contains("Desktop operation failed:"),"rejected launch reports an error");check(!session.hasDesktopApp(),"rejected launch retains idle state");idle(true);
  click("monitor-idle-apps");page("home");click("app.dev.dexdroid");page("closed");await(()->!session.busy&&session.hasDesktopApp(),"successful monitor app selection clears first-launch state");idle(false);main(session::notifyUi);idle(false);Thread.sleep(250);int before=InputTestActivity.touchEvents;float[] tap=physical(session.width()*.5f,session.height()*.5f);tap(tap[0],tap[1]);await(()->InputTestActivity.touchEvents>before,"app touch resumes after idle shortcut disappears");
  fresh();idle(true);centered();main(()->session.preview(1920,1080,240));await(()->!session.busy&&session.baseH==1080,"idle Fit preview ready");Thread.sleep(250);idle(true);centered();tap(960,4);page("closed");idle(true);click("monitor-idle-apps");page("home");tap(960,4);page("closed");idle(true);main(()->session.confirm(false));await(()->!session.busy,"idle Fit preview reverted");restore();return results.toString();
 }catch(Throwable error){throw new RuntimeException(results+"FAIL idle acceptance",error);}finally{restore();if(bound)runner.getTargetContext().unbindService(binding);}}
}
