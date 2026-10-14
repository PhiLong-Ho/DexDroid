package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.Button;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Real controller interactions and external-app navigation, not resource snapshots. */
public class UiAcceptance {
 private final Instrumentation runner;
 public UiAcceptance(Instrumentation runner){this.runner=runner;}
 private Context getTargetContext(){return runner.getTargetContext();}
 private void runOnMainSync(Runnable action){runner.runOnMainSync(action);}
 private SessionService session;
 private Activity controller;
 private ServiceConnection binding;
 private boolean bound;
 private final StringBuilder results=new StringBuilder();
 private void check(boolean value,String message){if(!value)throw new AssertionError(message);results.append("PASS ").append(message).append('\n');}
 private void await(BooleanSupplier predicate,String message)throws Exception{long end=SystemClock.uptimeMillis()+10000;while(!predicate.getAsBoolean()&&SystemClock.uptimeMillis()<end)Thread.sleep(50);check(predicate.getAsBoolean(),message);}
 private Button find(View root,String text){if(root instanceof Button&&text.contentEquals(((Button)root).getText()))return (Button)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){Button found=find(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
 private Button button(String text){Button found=find(controller.getWindow().getDecorView(),text);check(found!=null&&found.isShown(),text+" visible");return found;}
 private int[] position(View view){int[] out=new int[2];view.getLocationOnScreen(out);return out;}
 public String run()throws Exception{try{
  controller=runner.startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));CountDownLatch connected=new CountDownLatch(1);
  binding=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){session=((SessionService.LocalBinder)b).get();connected.countDown();}public void onServiceDisconnected(ComponentName n){}};
  bound=getTargetContext().bindService(new Intent(getTargetContext(),SessionService.class),binding,Context.BIND_AUTO_CREATE);check(connected.await(5,TimeUnit.SECONDS),"controller service bound");await(()->session.id>=0&&!session.busy,"automatic desktop ready");
  runOnMainSync(()->{
   Button apps=button("Apps"),rotate=button("Rotate"),scroll=button("Scroll"),stop=button("Stop"),save=button("Save app"),back=button("Back");
   check(MenuTextAssertions.check(controller.getWindow().getDecorView())>0,"phone text encoding and glyph availability");
   int[] a=position(apps),r=position(rotate),s=position(scroll),t=position(stop),v=position(save),b=position(back);
   check(a[1]==r[1]&&r[1]==s[1]&&a[0]<r[0]&&r[0]<s[0],"first row Apps, Rotate, Scroll");
   check(t[1]==v[1]&&v[1]==b[1]&&t[0]<v[0]&&v[0]<b[0]&&t[1]>a[1],"second row Stop, Save app, Back");
   check(Math.abs(a[0]-t[0])<=1&&Math.abs(r[0]-v[0])<=1&&Math.abs(s[0]-b[0])<=1,"columns align across both rows");
   for(Button control:new Button[]{rotate,scroll,stop,save,back})check(Math.abs(control.getWidth()-apps.getWidth())<=1&&control.getHeight()==apps.getHeight(),control.getText()+" equal size");
  });
  ComponentName test=new ComponentName(getTargetContext(),InputTestActivity.class);runOnMainSync(()->session.launch(test,null));await(()->!session.busy,"external app configuration ready");Thread.sleep(600);
  runOnMainSync(()->button("Stop").performClick());await(()->session.id<0&&!session.desktopRequested()&&controller.hasWindowFocus(),"Stop releases desktop and preserves controller focus");
  runOnMainSync(()->{Button start=button("Start"),save=button("Save app"),back=button("Back");check(position(start)[0]<position(save)[0]&&position(save)[0]<position(back)[0],"Start remains left of Save app and Back");start.performClick();});
  await(()->session.id>=0&&!session.busy,"Start resumes desktop");runOnMainSync(()->session.launch(test,null));await(()->!session.busy,"resumed app configuration ready");Thread.sleep(600);int before=InputTestActivity.backEvents;
  runOnMainSync(()->button("Back").performClick());await(()->InputTestActivity.backEvents>before,"right-hand Back reaches external app");
  // Keyboard focus can belong to the external display while the phone's
  // controller remains visible. Assert visibility/lifecycle, not global focus.
  runOnMainSync(()->check(!controller.isFinishing()&&!controller.isDestroyed()&&controller.getWindow().getDecorView().getWindowVisibility()==View.VISIBLE&&button("Back").isShown(),"Back keeps phone controller open"));
  return results.toString();
 }finally{if(bound)getTargetContext().unbindService(binding);}}
}
