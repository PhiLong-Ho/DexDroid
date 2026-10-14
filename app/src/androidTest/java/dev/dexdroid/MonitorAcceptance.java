package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Injects real physical-monitor events; checks external app and native shell outcomes. */
final class MonitorAcceptance {
 private final Instrumentation runner;
 private SessionService session;
 private ServiceConnection binding;
 private Activity phone;
 private boolean bound;
 private Map<String,?> preferences;
 private int originalRotation;
 private long down;
 private final StringBuilder results=new StringBuilder();
 MonitorAcceptance(Instrumentation runner){this.runner=runner;}
 private void main(Runnable action){runner.runOnMainSync(action);}
 private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);results.append("PASS ").append(label).append('\n');}
 private void await(BooleanSupplier predicate,String label)throws Exception{long until=SystemClock.uptimeMillis()+10000;while(!predicate.getAsBoolean()&&SystemClock.uptimeMillis()<until)Thread.sleep(50);check(predicate.getAsBoolean(),label);}
 private void restore(){if(preferences==null)return;android.content.SharedPreferences.Editor edit=session.prefs.edit().clear();for(Map.Entry<String,?> entry:preferences.entrySet()){Object v=entry.getValue();String k=entry.getKey();if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Long)edit.putLong(k,(Long)v);}edit.commit();}
 private void event(int action,float[][] points)throws Exception{long now=SystemClock.uptimeMillis();if(action==MotionEvent.ACTION_DOWN)down=now;MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[points.length];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[points.length];for(int i=0;i<points.length;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=i+7;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=points[i][0];coords[i].y=points[i][1];coords[i].pressure=1;}MotionEvent e=MotionEvent.obtain(down,now,action,points.length,props,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);try{MotionEvent.class.getMethod("setDisplayId",int.class).invoke(e,session.external.getDisplayId());check(runner.getUiAutomation().injectInputEvent(e,true),"physical monitor event "+action);}finally{e.recycle();}}
 private float[] physical(float x,float y){Display.Mode mode=session.external.getMode();return Geometry.map(x/session.width(),y/session.height(),session.rotation,mode.getPhysicalWidth(),mode.getPhysicalHeight(),session.width(),session.height());}
 private void swipe(float x,float y,float endX,float endY)throws Exception{event(0,new float[][]{physical(x,y)});Thread.sleep(30);event(2,new float[][]{physical(endX,endY)});event(1,new float[][]{physical(endX,endY)});Thread.sleep(100);}
 private void tap(float x,float y)throws Exception{event(0,new float[][]{{x,y}});event(1,new float[][]{{x,y}});Thread.sleep(100);}
 private void click(String tag)throws Exception{Rect rect=new Rect();main(()->{View root=session.monitorShellView();View view=root==null?null:root.findViewWithTag(tag);check(view!=null&&view.isShown()&&view.getGlobalVisibleRect(rect),"monitor control visible: "+tag);});tap(rect.exactCenterX(),rect.exactCenterY());}
 private void page(String value)throws Exception{await(()->session.monitorShellPage().equals(value),"monitor page "+value);}
 private void resume()throws Exception{while(!session.monitorShellPage().equals("closed")){main(()->session.key(KeyEvent.KEYCODE_BACK));Thread.sleep(80);}page("closed");}
 private void homeGesture()throws Exception{float d=session.dpi/160f;swipe(session.width()*.6f,session.height()-4*d,session.width()*.6f,session.height()-100*d);page("home");}
 private void controlsGesture()throws Exception{float d=session.dpi/160f;swipe(session.width()*.6f,4*d,session.width()*.6f,100*d);page("controls");}
 String run()throws Exception{try{
  phone=runner.startActivitySync(new Intent(runner.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));CountDownLatch ready=new CountDownLatch(1);binding=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){session=((SessionService.LocalBinder)b).get();ready.countDown();}public void onServiceDisconnected(ComponentName n){}};bound=runner.getTargetContext().bindService(new Intent(runner.getTargetContext(),SessionService.class),binding,Context.BIND_AUTO_CREATE);check(ready.await(5,TimeUnit.SECONDS),"monitor controller bound");await(()->session.id>=0&&!session.busy,"automatic monitor desktop ready");preferences=new HashMap<>(session.prefs.getAll());originalRotation=session.rotation;
  main(()->{session.setMonitorNavigationEnabled(true);session.setMonitorTouchEnabled(true);});ComponentName test=new ComponentName(runner.getTargetContext(),InputTestActivity.class);
  for(int r=0;r<4;r++){final int selected=r;main(()->session.launch(test,selected));await(()->!session.busy&&session.rotation==selected,"monitor rotation "+r);Thread.sleep(500);float d=session.dpi/160f;int back=InputTestActivity.backEvents,cancel=InputTestActivity.cancelEvents;
   swipe(session.width()-4*d,session.height()*.4f,session.width()-100*d,session.height()*.4f);await(()->InputTestActivity.backEvents==back+1,"right-edge Back exactly once rotation "+r);await(()->InputTestActivity.cancelEvents>cancel,"edge Back cancels app contact rotation "+r);check(!session.pointerVisible,"edge input hides cursor "+r);
   homeGesture();click("app.dev.dexdroid");page("closed");await(()->session.activePackage.equals("dev.dexdroid")&&!session.busy,"monitor launcher targets external app "+r);Thread.sleep(300);
   main(()->session.rotate(selected,false));await(()->!session.busy&&session.rotation==selected,"control panel test orientation "+r);Thread.sleep(300);
   controlsGesture();main(()->check(session.monitorShellView().findViewWithTag("monitor-status")!=null,"full status panel on monitor "+selected));click("monitor-settings");page("settings");click("monitor-touch");check(!session.monitorTouchEnabled(),"monitor settings touch Off "+r);click("monitor-touch");check(session.monitorTouchEnabled(),"monitor settings touch On "+r);resume();
   // Launcher selection applies the app's saved/default rotation; re-establish
   // this test's explicit orientation before the next edge gesture.
   main(()->session.rotate(selected,false));await(()->!session.busy&&session.rotation==selected,"restore test rotation "+r);Thread.sleep(300);
  }
  float d=session.dpi/160f;int back=InputTestActivity.backEvents;
  swipe(session.width()-4*d,session.height()*.4f,session.width()-20*d,session.height()*.4f);check(InputTestActivity.backEvents==back,"short edge swipe rejected");
  swipe(session.width()-12*d,session.height()*.4f,session.width()-2*d,session.height()*.4f);check(InputTestActivity.backEvents==back,"outward edge swipe rejected");
  swipe(session.width()-4*d,session.height()*.3f,session.width()-5*d,session.height()*.5f);check(InputTestActivity.backEvents==back,"vertical side-edge swipe remains app input");
  event(0,new float[][]{physical(session.width()-4*d,session.height()*.4f)});event(5|256,new float[][]{physical(session.width()-4*d,session.height()*.4f),physical(session.width()-30*d,session.height()*.5f)});event(2,new float[][]{physical(session.width()-100*d,session.height()*.4f),physical(session.width()-120*d,session.height()*.5f)});event(6|256,new float[][]{physical(session.width()-100*d,session.height()*.4f),physical(session.width()-120*d,session.height()*.5f)});event(1,new float[][]{physical(session.width()-100*d,session.height()*.4f)});Thread.sleep(150);check(InputTestActivity.backEvents==back,"multi-touch edge gesture rejected");
  main(()->session.setMonitorNavigationEnabled(false));swipe(session.width()-4*d,session.height()*.4f,session.width()-100*d,session.height()*.4f);check(InputTestActivity.backEvents==back,"disabled edge navigation leaves native input");main(()->session.setMonitorNavigationEnabled(true));
  swipe(4*d,session.height()*.4f,100*d,session.height()*.4f);await(()->InputTestActivity.backEvents==back+1,"left-edge inward Back");
  homeGesture();int underlying=InputTestActivity.touchEvents;click("monitor-close");page("closed");check(InputTestActivity.touchEvents==underlying,"launcher close does not click underlying app");
  main(()->{View button=session.monitorShellView().findViewWithTag("monitor-controls");int[] where={0,0};View current=button;while(current!=session.monitorShellView()){where[0]+=current.getLeft();where[1]+=current.getTop();current=(View)current.getParent();}session.cx=where[0]+button.getWidth()/2f;session.cy=where[1]+button.getHeight()/2f;session.click();});page("controls");check(session.pointerVisible,"phone pointer operates monitor dock");click("monitor-scroll");page("scroll");click("monitor-swipe");check(session.swipeScrolling(),"monitor selects native swipe mode");click("monitor-speed-0");check(session.scrollSpeed()==.2f,"monitor saves scroll speed");resume();
  homeGesture();android.widget.ListView[] appList={null};int[] first={-1};main(()->{appList[0]=(android.widget.ListView)session.monitorShellView().findViewWithTag("monitor-app-list");first[0]=appList[0].getFirstVisiblePosition();float x=0,y=0;View current=appList[0];while(current!=session.monitorShellView()){x+=current.getLeft();y+=current.getTop();View parent=(View)current.getParent();x-=parent.getScrollX();y-=parent.getScrollY();current=parent;}session.cx=x+appList[0].getWidth()/2f;session.cy=y+appList[0].getHeight()/2f;session.wheel(0,-20);});await(()->appList[0].getFirstVisiblePosition()>first[0],"phone wheel scrolls native monitor launcher");check(session.pointerVisible,"phone menu scrolling restores cursor");resume();
  controlsGesture();int rotateTo=(session.rotation+1)%4;click("monitor-rotate");page("rotation");click("monitor-rotation-"+rotateTo);await(()->!session.busy&&session.rotation==rotateTo,"monitor Rotate changes upright desktop");page("controls");click("monitor-save");check(session.prefs.getInt("app.dev.dexdroid",-1)==session.rotation,"monitor Save persists rotation");click("monitor-reset");check(!session.prefs.contains("app.dev.dexdroid"),"monitor Reset clears app rotation");await(()->!session.busy,"reset rotation complete");resume();
  main(()->session.rotate(0,false));await(()->!session.busy&&session.rotation==0,"Fit test landscape");main(()->session.preview(1920,1080,240));await(()->!session.busy&&session.baseH==1080,"Fit test preview");event(0,new float[][]{{960,4}});event(2,new float[][]{{960,250}});event(1,new float[][]{{960,250}});Thread.sleep(100);page("closed");check(session.diagnosticText().contains("Monitor edge recognized")&&session.diagnosticText().contains("Monitor edge rejected"),"edge outcomes in diagnostic log");main(()->session.confirm(false));await(()->!session.busy,"Fit reverted");
  main(()->phone.finish());Thread.sleep(300);homeGesture();click("app.dev.dexdroid");page("closed");controlsGesture();check(session.sessionAwake(),"monitor shell works with phone controller destroyed");click("monitor-stop");await(()->session.id<0&&session.monitorShellView()==null,"monitor Stop releases shell and display");restore();return results.toString();
 }catch(Throwable error){throw new RuntimeException(results+"FAIL monitor acceptance",error);}finally{restore();if(bound)runner.getTargetContext().unbindService(binding);}}
}
