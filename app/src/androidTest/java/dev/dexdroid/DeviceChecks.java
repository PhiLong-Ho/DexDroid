package dev.dexdroid;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** No test framework dependency; exercises the real non-root Shizuku path. */
public class DeviceChecks extends Instrumentation {
 private SessionService session;
 private java.util.Map<String,?> savedPreferences;
 private String physicalDisplay;
 private boolean controllerOnly;
 private void restorePreferences(){if(session==null||savedPreferences==null)return;android.content.SharedPreferences.Editor edit=session.prefs.edit().clear();for(java.util.Map.Entry<String,?> item:savedPreferences.entrySet()){Object value=item.getValue();String key=item.getKey();if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof String)edit.putString(key,(String)value);}edit.commit();}
 private StringBuilder results=new StringBuilder();
 private long gestureDown;
 private long monitorDown;
 private void monitorGesture(int action,float[] xs,float[] ys,int[] ids)throws Exception{
  long now=SystemClock.uptimeMillis();if(action==MotionEvent.ACTION_DOWN)monitorDown=now;
  MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[xs.length];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[xs.length];
  for(int i=0;i<xs.length;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=ids[i];props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=xs[i];coords[i].y=ys[i];coords[i].pressure=1;coords[i].size=1;}
  MotionEvent event=MotionEvent.obtain(monitorDown,now,action,xs.length,props,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
  try{MotionEvent.class.getMethod("setDisplayId",int.class).invoke(event,session.external.getDisplayId());check(getUiAutomation().injectInputEvent(event,true),"physical monitor event "+action);}finally{event.recycle();}
 }
 private void monitorTap(float u,float v)throws Exception{
  float[] p=Geometry.map(u,v,session.rotation,1920,1200,session.width(),session.height());
  monitorGesture(0,new float[]{p[0]},new float[]{p[1]},new int[]{7});
  monitorGesture(1,new float[]{p[0]},new float[]{p[1]},new int[]{7});
  long expectedDown=monitorDown; await(()->InputTestActivity.lastUpDownTime==expectedDown,"monitor tap delivered");
  check(!session.pointerVisible,"monitor touch hides pointer");
  check(Math.abs(InputTestActivity.lastUpX-u*session.width())<2&&Math.abs(InputTestActivity.lastUpY-v*session.height())<2,"monitor tap coordinate alignment");
 }
 private void gesture(View pad,int action,float[] xs,float[] ys){long now=SystemClock.uptimeMillis();if(action==0)gestureDown=now;MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[xs.length];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[xs.length];for(int i=0;i<xs.length;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=i;props[i].toolType=1;coords[i]=new MotionEvent.PointerCoords();coords[i].x=xs[i];coords[i].y=ys[i];coords[i].pressure=1;}MotionEvent e=MotionEvent.obtain(gestureDown,now,action,xs.length,props,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);runOnMainSync(()->pad.dispatchTouchEvent(e));e.recycle();}
 private void check(boolean condition,String name){if(!condition)throw new AssertionError(name);results.append("PASS ").append(name).append('\n');}
 private void await(BooleanSupplier condition,String name)throws Exception{long end=SystemClock.uptimeMillis()+10000;while(!condition.getAsBoolean()&&SystemClock.uptimeMillis()<end)Thread.sleep(50);check(condition.getAsBoolean(),name);}
 @Override public void onCreate(Bundle args){super.onCreate(args);physicalDisplay=args.getString("physicalDisplay");controllerOnly="true".equals(args.getString("controllerOnly"));start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  for(int r=0;r<4;r++){int w=r%2==0?1920:1200,h=r%2==0?1200:1920;float[] center=Geometry.map(.5f,.5f,r,1920,1200,w,h);check(Math.abs(center[0]-960)<.01&&Math.abs(center[1]-600)<.01,"rotation "+r+" center");float[][] corners={{0,0},{1,0},{1,1},{0,1}};for(float[] c:corners){float[] p=Geometry.map(c[0],c[1],r,1920,1200,w,h);check((p[0]==0||p[0]==1920)&&(p[1]==0||p[1]==1200),"rotation "+r+" corner");}}
  check(Geometry.recommendedDpi(1200)==240&&Geometry.recommendedDpi(800)==160,"density recommendations");float[] fit=Geometry.map(0,0,0,1920,1200,1920,1080);check(fit[1]==60,"16:9 Fit bars on 16:10 monitor");
  Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));CountDownLatch connected=new CountDownLatch(1);
  ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){session=((SessionService.LocalBinder)b).get();connected.countDown();}public void onServiceDisconnected(ComponentName n){}};
  getTargetContext().bindService(new Intent(getTargetContext(),SessionService.class),connection,Context.BIND_AUTO_CREATE);check(connected.await(5,TimeUnit.SECONDS),"controller bind");savedPreferences=new java.util.HashMap<>(session.prefs.getAll());if(controllerOnly){
   await(()->!session.needsRecovery(),"Shizuku connects automatically without Start button");
   check(session.external==null&&session.id<0,"no desktop created on phone without monitor");
   ComponentName queued=new ComponentName(getTargetContext(),InputTestActivity.class);
   runOnMainSync(()->session.launch(queued,null));
   check(session.diagnosticText().contains("App launch queued:"),"app launch queued while monitor unavailable");
   View controllerPad=activity.getWindow().getDecorView().findViewWithTag("touchpad");
   gesture(controllerPad,0,new float[]{100},new float[]{300});gesture(controllerPad,1,new float[]{100},new float[]{300});
   check(session.diagnosticText().contains("Touchpad ignored: desktop=-1"),"diagnostics explain unavailable desktop input");
   runOnMainSync(()->session.pauseDesktop());
   session.clearDiagnostics();for(int i=0;i<300;i++)session.log("ring-check-"+i+";");
   check(!session.diagnosticText().contains("ring-check-0;")&&session.diagnosticText().contains("ring-check-299;"),"diagnostic history is bounded");
   session.clearDiagnostics();check(session.diagnosticText().contains("No events recorded."),"diagnostics clear");
   runOnMainSync(()->activity.finish());getTargetContext().unbindService(connection);
   result.putString("stream",results.toString());finish(Activity.RESULT_OK,result);return;
  }
  await(()->session.id>=0&&!session.busy,"automatic Shizuku virtual desktop creation");int display=session.id;
  ComponentName inputTest=new ComponentName(getTargetContext(),InputTestActivity.class);runOnMainSync(()->session.launch(inputTest,null));Thread.sleep(1000);
  runOnMainSync(()->{session.prefs.edit().putBoolean("swipeScroll",false).remove("swipeScroll.dev.dexdroid").remove("swipeScroll.eu.kanade.tachiyomi").remove("scrollSpeed.eu.kanade.tachiyomi").apply();String original=session.activePackage;session.activePackage="eu.kanade.tachiyomi";check(session.swipeScrolling(),"Tachiyomi defaults to native swipe");session.setSwipeScrolling(false);session.prefs.edit().putFloat("scrollSpeed."+session.activePackage,.5f).apply();session.saveAppSettings();check(!session.swipeScrolling()&&session.prefs.getInt("app."+session.activePackage,-1)==session.rotation,"Save app persists scroll mode and rotation");session.activePackage="dev.dexdroid";check(!session.swipeScrolling(),"scroll mode isolated between apps");session.activePackage="eu.kanade.tachiyomi";session.resetAppSettings();check(session.swipeScrolling()&&session.scrollSpeed()==.2f&&!session.prefs.contains("app."+session.activePackage),"Reset app restores defaults and clears rotation");session.activePackage=original;session.notifyUi();});await(()->!session.busy,"app settings ready");
  for(int r=0;r<4;r++){final int rotation=r;runOnMainSync(()->session.rotate(rotation,true));await(()->!session.busy&&session.rotation==rotation,"resize rotation "+r);check(session.id==display,"task-preserving display ID "+r);await(()->{android.view.Display d=((android.hardware.display.DisplayManager)getTargetContext().getSystemService(Context.DISPLAY_SERVICE)).getDisplay(session.id);return d!=null && d.getMode().getPhysicalWidth()==session.width() && d.getMode().getPhysicalHeight()==session.height();},"actual display dimensions "+r);Thread.sleep(500);if(physicalDisplay!=null){try(android.os.ParcelFileDescriptor capture=getUiAutomation().executeShellCommand("screencap -d "+physicalDisplay+" -p /sdcard/dexdroid-check-r"+r+".png");java.io.InputStream stream=new android.os.ParcelFileDescriptor.AutoCloseInputStream(capture)){while(stream.read()!=-1){}}}int before=InputTestActivity.touchEvents;runOnMainSync(()->{session.center();session.click();});await(()->InputTestActivity.touchEvents>before,"targeted tap "+r);monitorTap(.5f,.5f);monitorTap(.1f,.1f);monitorTap(.9f,.1f);monitorTap(.1f,.9f);monitorTap(.9f,.9f);if(r==1)monitorTap(.5f,1);else if(r==2||r==3)monitorTap(1,.5f);else monitorTap(0,.5f);}
  int pinch=InputTestActivity.pinchEvents;runOnMainSync(()->{session.touch(0,new float[]{400},new float[]{500});session.touch(5|256,new float[]{400,600},new float[]{500,500});session.touch(2,new float[]{350,650},new float[]{500,500});session.touch(6|256,new float[]{350,650},new float[]{500,500});session.touch(1,new float[]{350},new float[]{500});});await(()->InputTestActivity.pinchEvents>=pinch+3,"native two-pointer zoom ordering");int wheel=InputTestActivity.wheelEvents;runOnMainSync(()->{session.prefs.edit().putFloat("scrollSpeed."+session.activePackage,.2f).apply();session.wheel(1,1);});await(()->InputTestActivity.wheelEvents>wheel,"horizontal and vertical wheel input");check(Math.abs(InputTestActivity.lastWheelH-.2f)<.001f&&Math.abs(InputTestActivity.lastWheelV-.2f)<.001f,"per-app scroll speed applied to both axes");check(session.pointerVisible,"touchpad wheel restores pointer");
  runOnMainSync(()->session.prefs.edit().putBoolean("swipeScroll",false).putBoolean("reverseScroll",false).apply());
  View pad=activity.getWindow().getDecorView().findViewWithTag("touchpad");check(pad!=null,"phone touchpad exists");
  int tapsBefore=InputTestActivity.touchEvents;gesture(pad,0,new float[]{300},new float[]{300});gesture(pad,1,new float[]{300},new float[]{300});await(()->InputTestActivity.touchEvents>tapsBefore,"phone tap recognizer");check(session.pointerVisible,"phone tap restores pointer");
  int pinchBefore=InputTestActivity.pinchEvents;gesture(pad,0,new float[]{300},new float[]{300});gesture(pad,5|256,new float[]{300,500},new float[]{300,300});Thread.sleep(25);gesture(pad,2,new float[]{260,540},new float[]{300,300});Thread.sleep(25);gesture(pad,2,new float[]{220,580},new float[]{300,300});gesture(pad,6|256,new float[]{220,580},new float[]{300,300});gesture(pad,1,new float[]{220},new float[]{300});await(()->InputTestActivity.pinchEvents>=pinchBefore+3,"phone pinch recognizer");
  int wheelBefore=InputTestActivity.wheelEvents;gesture(pad,0,new float[]{300},new float[]{300});gesture(pad,5|256,new float[]{300,500},new float[]{300,300});Thread.sleep(25);gesture(pad,2,new float[]{300,500},new float[]{360,360});gesture(pad,6|256,new float[]{300,500},new float[]{360,360});gesture(pad,1,new float[]{300},new float[]{360});await(()->InputTestActivity.wheelEvents>wheelBefore,"phone scroll recognizer");
  int backBefore=InputTestActivity.backEvents;gesture(pad,0,new float[]{100},new float[]{300});gesture(pad,5|256,new float[]{100,200},new float[]{300,300});gesture(pad,5|512,new float[]{100,200,300},new float[]{300,300,300});gesture(pad,2,new float[]{350,450,550},new float[]{300,300,300});gesture(pad,6|512,new float[]{350,450,550},new float[]{300,300,300});gesture(pad,6|256,new float[]{350,450},new float[]{300,300});gesture(pad,1,new float[]{350},new float[]{300});await(()->InputTestActivity.backEvents>backBefore,"three finger Back recognizer");
  await(()->session.diagnosticText().contains("Three-finger Back recognized")&&session.diagnosticText().contains("Key sent: KEYCODE_BACK"),"three-finger recognition and Back dispatch logged");
  int rejectedBack=InputTestActivity.backEvents;gesture(pad,0,new float[]{100},new float[]{300});gesture(pad,5|256,new float[]{100,200},new float[]{300,300});gesture(pad,5|512,new float[]{100,200,300},new float[]{300,300,300});gesture(pad,2,new float[]{110,210,310},new float[]{300,300,300});gesture(pad,6|512,new float[]{110,210,310},new float[]{300,300,300});gesture(pad,6|256,new float[]{110,210},new float[]{300,300});gesture(pad,1,new float[]{110},new float[]{300});Thread.sleep(150);check(InputTestActivity.backEvents==rejectedBack&&session.diagnosticText().contains("Three-finger Back rejected"),"short three-finger movement rejected and explained");
  int nativePinch=InputTestActivity.pinchEvents;
  monitorGesture(0,new float[]{800},new float[]{600},new int[]{7});
  monitorGesture(5|256,new float[]{800,1120},new float[]{600,600},new int[]{7,11});
  monitorGesture(2,new float[]{700,1220},new float[]{600,600},new int[]{7,11});
  monitorGesture(6|256,new float[]{700,1220},new float[]{600,600},new int[]{7,11});
  monitorGesture(1,new float[]{700},new float[]{600},new int[]{7});
  await(()->InputTestActivity.pinchEvents>=nativePinch+3,"physical monitor pinch forwarding");
  check(InputTestActivity.lastPointer0==7&&InputTestActivity.lastPointer1==11,"monitor pointer IDs preserved");check(InputTestActivity.lastDownTime==monitorDown,"monitor down time preserved");
  int arbitration=InputTestActivity.touchEvents;
  monitorGesture(0,new float[]{960},new float[]{600},new int[]{7});await(()->InputTestActivity.touchEvents>arbitration,"monitor contact active");int monitorHeld=InputTestActivity.touchEvents;runOnMainSync(()->session.click());Thread.sleep(200);check(InputTestActivity.touchEvents==monitorHeld,"phone clicks suppressed during monitor gesture");monitorGesture(1,new float[]{960},new float[]{600},new int[]{7});await(()->InputTestActivity.touchEvents>monitorHeld,"monitor gesture ends");
  runOnMainSync(()->session.preview(1920,1080,240));await(()->!session.busy&&session.baseH==1080,"Fit resolution preview");int ignored=InputTestActivity.touchEvents;monitorGesture(0,new float[]{960},new float[]{5},new int[]{7});monitorGesture(1,new float[]{960},new float[]{5},new int[]{7});Thread.sleep(200);check(InputTestActivity.touchEvents==ignored,"Fit bars reject monitor touch");
  int canceled=InputTestActivity.cancelEvents;monitorGesture(0,new float[]{960},new float[]{600},new int[]{7});monitorGesture(2,new float[]{960},new float[]{5},new int[]{7});monitorGesture(1,new float[]{960},new float[]{5},new int[]{7});await(()->InputTestActivity.cancelEvents>canceled,"monitor contact canceled on leaving content");runOnMainSync(()->session.confirm(false));await(()->!session.busy&&session.baseH==1200,"Fit preview reverted");
  runOnMainSync(()->session.setMonitorTouchEnabled(false));int disabled=InputTestActivity.touchEvents;monitorGesture(0,new float[]{960},new float[]{600},new int[]{7});monitorGesture(1,new float[]{960},new float[]{600},new int[]{7});Thread.sleep(200);check(InputTestActivity.touchEvents==disabled,"monitor touch disable control");runOnMainSync(()->session.setMonitorTouchEnabled(true));
  int originalW=session.baseW,originalH=session.baseH,originalDpi=session.dpi;runOnMainSync(()->session.preview(1280,800,160));await(()->!session.busy&&session.baseW==1280,"lower resolution preview");monitorTap(.5f,.5f);monitorTap(.1f,.1f);monitorTap(.9f,.9f);Thread.sleep(16000);await(()->!session.busy&&session.baseW==originalW&&session.baseH==originalH&&session.dpi==originalDpi,"15 second automatic rollback");
  runOnMainSync(()->session.preview(1280,800,160));await(()->!session.busy&&session.baseW==1280,"second preview");runOnMainSync(()->session.confirm(true));check(session.prefs.getInt(session.monitorKey()+".w",0)==1280,"confirmed monitor profile persistence");runOnMainSync(()->session.rememberApp(true));check(session.prefs.getInt("app.dev.dexdroid",-1)==session.rotation,"saved app orientation");
  runOnMainSync(()->{session.rememberApp(false);session.resetMonitor();session.pauseDesktop();});await(()->session.id==-1,"Stop releases desktop");runOnMainSync(()->session.launch(inputTest,null));await(()->session.id>=0&&!session.busy,"app launch automatically resumes desktop");check(session.baseW==1920&&session.baseH==1200,"current output recommendation after reset");runOnMainSync(()->session.launch(inputTest,null));Thread.sleep(600);
  runOnMainSync(()->{session.rotate(0,true);session.report("Device checks passed. Ready to experiment.");});await(()->!session.busy,"final landscape");  int runningId=session.id;int backgroundBefore=InputTestActivity.touchEvents;
  monitorGesture(0,new float[]{960},new float[]{600},new int[]{7});await(()->InputTestActivity.touchEvents>backgroundBefore,"monitor gesture before backgrounding");
  runOnMainSync(()->activity.finish());getTargetContext().unbindService(connection);Thread.sleep(800);
  check(session.id==runningId&&session.monitorInputView()!=null&&session.sessionAwake(),"desktop persists with controller destroyed and no bindings");
  int afterClose=InputTestActivity.touchEvents;monitorGesture(2,new float[]{1000},new float[]{640},new int[]{7});monitorGesture(1,new float[]{1000},new float[]{640},new int[]{7});await(()->InputTestActivity.lastUpDownTime==monitorDown,"monitor touch works in background");monitorTap(.1f,.1f);
  restorePreferences();result.putString("stream",results.toString());finish(Activity.RESULT_OK,result);
 }catch(Throwable e){restorePreferences();result.putString("stream",results+"FAIL "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
}
