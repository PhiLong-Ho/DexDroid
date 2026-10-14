package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.display.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.FrameLayout;
import java.util.*;
import java.util.concurrent.*;
import rikka.shizuku.Shizuku;

/** Owns the desktop independently of the controller Activity. */
public class SessionService extends Service implements DisplayManager.DisplayListener {
 public interface Listener {void changed();}
 public Listener listener;
 public String status="Connect Shizuku to begin",activePackage="";
 public volatile int id=-1,rotation=0,defaultRotation=0,baseW=1920,baseH=1200,dpi=240;
 public float cx=960,cy=600; boolean pointerVisible=false;
 public volatile boolean busy=false;
 public Display external;
 public SharedPreferences prefs;
 private IDeskService bridge;
 private Monitor monitor;
 private DisplayManager displays;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>());
 private final IBinder binder=new LocalBinder();
 private volatile int generation=0; private final IBinder sessionOwner=new Binder();
 private boolean bound=false,permissionRequested=false,permissionDenied=false,autoStartRequested=false;
 private ComponentName pendingApp;
 private Integer pendingOrientation;
 private final DiagnosticLog diagnostics=new DiagnosticLog();
 private int[] pendingProfile;
 private String profileKey; private float[] contactsX,contactsY;
 private MotionEvent monitorContacts;
 private boolean monitorBlocked;
 private PowerManager.WakeLock sessionWakeLock;
 private final Runnable rollback=()->confirm(false);
 private Shizuku.UserServiceArgs args;
 public class LocalBinder extends Binder {public SessionService get(){return SessionService.this;}}
 private final Shizuku.OnRequestPermissionResultListener permission=(code,result)->main.post(()->{if(code==71){permissionRequested=false;permissionDenied=result!=PackageManager.PERMISSION_GRANTED;if(!permissionDenied)connect();else report("Shizuku authorization denied. Retry from Options when ready.");}});
 private final Shizuku.OnBinderReceivedListener received=()->main.post(()->{log("Shizuku service available");if(autoStartRequested)connect();});
 private final Shizuku.OnBinderDeadListener death=()->main.post(()->{bridge=null;bound=false;stopDesktop();report("Shizuku stopped. Open Shizuku to restart its service.");});
 private final ServiceConnection connection=new ServiceConnection(){
  public void onServiceConnected(ComponentName name,IBinder value){main.post(()->{bridge=IDeskService.Stub.asInterface(value);report("Shizuku connected");if(autoStartRequested)startDesktop();});}
  public void onServiceDisconnected(ComponentName name){main.post(()->{bridge=null;bound=false;stopDesktop();report("Shizuku disconnected. Retry from Options.");});}
 };
 @Override public void onCreate(){super.onCreate();prefs=getSharedPreferences("desktop",0);defaultRotation=prefs.getInt("rotation",0);rotation=defaultRotation;
  args=new Shizuku.UserServiceArgs(new ComponentName(this,DeskService.class)).daemon(false).processNameSuffix("desktop").debuggable(true).version(7);
  sessionWakeLock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"DexDroid:desktop");
  sessionWakeLock.setReferenceCounted(false);
  displays=(DisplayManager)getSystemService(DISPLAY_SERVICE);displays.registerDisplayListener(this,main);
  Shizuku.addRequestPermissionResultListener(permission);Shizuku.addBinderDeadListener(death);Shizuku.addBinderReceivedListenerSticky(received);detect();log("Controller service created");
 }
 @Override public IBinder onBind(Intent intent){return binder;}
 @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent!=null && "stop".equals(intent.getAction()))pauseDesktop();return START_NOT_STICKY;}
 public void log(String message){diagnostics.add(message);android.util.Log.i("DexDroid",message);}
 public String diagnosticText(){return "DexDroid "+BuildConfig.VERSION_NAME+" | Android "+Build.VERSION.RELEASE+" | "+Build.MODEL+"\nMonitor: "+(external==null?"disconnected":external.getName())+" | Virtual display: "+id+"\nDesktop: "+width()+"x"+height()+" @ "+dpi+" DPI | Rotation: "+(rotation*90)+"°\nShizuku bridge: "+(bridge==null?"disconnected":"connected")+" | Busy: "+busy+"\nLast 250 events, memory only. Key sent means IPC completed, not proof an app handled it.\n\n"+diagnostics.snapshot();}
 public void clearDiagnostics(){diagnostics.clear();}
 public boolean needsRecovery(){return bridge==null;}
 public boolean desktopRequested(){return autoStartRequested;}
 public void ensureDesktop(){autoStartRequested=true;if(bridge==null)connect();else startDesktop();}
 public void retryDesktop(){permissionDenied=false;permissionRequested=false;ensureDesktop();}
 public void pauseDesktop(){autoStartRequested=false;pendingApp=null;pendingOrientation=null;stopDesktop();}
 public void report(String message){log(message);main.post(()->{status=message;notifyUi();});}
 public void notifyUi(){if(listener!=null)listener.changed();}
 public void connect(){try{
  if(!Shizuku.pingBinder()){report(getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api")==null?"Install Shizuku, then start its wireless-debugging service.":"Shizuku is stopped. Open Shizuku and start it through wireless debugging.");return;}
  if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){if(!permissionRequested&&!permissionDenied){permissionRequested=true;log("Requesting Shizuku authorization");Shizuku.requestPermission(71);}return;}
  if(bridge!=null){if(autoStartRequested)startDesktop();return;}if(bound)return;bound=true;Shizuku.bindUserService(args,connection);
  report("Connecting to Shizuku…");
 }catch(Throwable e){bound=false;permissionRequested=false;report("Connection failed: "+e.getMessage());}}
 public void detect(){external=null;for(Display display:displays.getDisplays()){int flags=display.getFlags();if(display.getDisplayId()!=Display.DEFAULT_DISPLAY && !display.getName().contains("DexDroid") && !display.getName().contains("PortraitDesk") && (display.getName().contains("HDMI") || (flags&Display.FLAG_PRESENTATION)!=0)){external=display;break;}}notifyUi();}
 public String monitorKey(){if(external==null)return "none";ArrayList<String> modes=new ArrayList<>();for(Display.Mode mode:external.getSupportedModes())modes.add(mode.getPhysicalWidth()+"x"+mode.getPhysicalHeight()+"@"+Math.round(mode.getRefreshRate()));Collections.sort(modes);return "monitor."+external.getName()+"."+Integer.toHexString(modes.toString().hashCode());}
 public String capabilities(){if(external==null)return "No external monitor detected";Display.Mode mode=external.getMode();DisplayMetrics metrics=new DisplayMetrics();external.getRealMetrics(metrics);String physical=(metrics.xdpi>=30 && metrics.xdpi<=600 && metrics.ydpi>=30 && metrics.ydpi<=600)?String.format(Locale.US,"%.0f / %.0f",metrics.xdpi,metrics.ydpi):"unknown (unreliable report)";StringBuilder text=new StringBuilder(external.getName()+"\nCurrent output: "+mode.getPhysicalWidth()+" × "+mode.getPhysicalHeight()+" @ "+Math.round(mode.getRefreshRate())+" Hz\nPhysical DPI: "+physical+"\nReported signal modes:\n");for(Display.Mode m:external.getSupportedModes())text.append(m.getPhysicalWidth()).append(" × ").append(m.getPhysicalHeight()).append(" @ ").append(Math.round(m.getRefreshRate())).append(" Hz\n");return text+"\nExternal touch: "+externalTouchDevices()+"\nDesktop resolution and UI DPI are independently adjustable. Signal-mode switching is not yet validated on JoyUI. Profiles match monitor name and reported modes; reset if two monitors share these values.";}
 public int width(){return rotation%2==0?baseW:baseH;}
 public int height(){return rotation%2==0?baseH:baseW;}
 public void startDesktop(){if(bridge==null){ensureDesktop();return;}if(monitor!=null)return;detect();if(external==null){report("Waiting for an external monitor");return;}
  Display.Mode mode=external.getMode();String key=monitorKey();baseW=prefs.getInt(key+".w",mode.getPhysicalWidth());baseH=prefs.getInt(key+".h",mode.getPhysicalHeight());dpi=prefs.getInt(key+".dpi",Geometry.recommendedDpi(Math.min(baseW,baseH)));rotation=defaultRotation;
  if(baseW<480||baseH<480||baseW>4096||baseH>4096||dpi<120||dpi>480){baseW=mode.getPhysicalWidth();baseH=mode.getPhysicalHeight();dpi=Geometry.recommendedDpi(Math.min(baseW,baseH));}
  cx=width()/2f;cy=height()/2f;pointerVisible=false;busy=true;
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("desktop","Desktop session",NotificationManager.IMPORTANCE_LOW));
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,SessionService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  startService(new Intent(this,SessionService.class));startForeground(1,new Notification.Builder(this,"desktop").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("DexDroid desktop running").setContentText("Tap to return to the touchpad").setContentIntent(open).addAction(new Notification.Action.Builder(null,"Stop",stop).build()).setOngoing(true).build());
  sessionWakeLock.acquire();
  try{monitor=new Monitor(external);monitor.show();report("Starting desktop…");}catch(Throwable e){stopDesktop();report("Monitor unavailable: "+e.getMessage());}
 }
 interface Work {void run(IDeskService target)throws Exception;}
 private void queue(Work action){IDeskService target=bridge;int token=generation;worker.execute(()->{if(token!=generation||target==null){log("Queued operation discarded: session changed or bridge unavailable");return;}try{action.run(target);}catch(Throwable e){report("Desktop operation failed: "+e.getMessage());}});}
 public void touch(int action,float[] xs,float[] ys){
  if(id<0||busy||monitorContacts!=null)return;
  int masked=action&255;
  if(masked==MotionEvent.ACTION_DOWN){showTouchpadPointer();cancelPhoneInput();}
  else if(contactsX==null)return;
  if(masked==MotionEvent.ACTION_MOVE&&worker.getQueue().size()>32)return;
  if(masked==MotionEvent.ACTION_UP||masked==MotionEvent.ACTION_CANCEL){contactsX=null;contactsY=null;}
  else if(masked==MotionEvent.ACTION_POINTER_UP){int remaining=((action>>8)&255)==0?1:0;contactsX=new float[]{xs[remaining]};contactsY=new float[]{ys[remaining]};}
  else{contactsX=xs.clone();contactsY=ys.clone();}
  int target=id;queue(s->s.touch(target,action,xs,ys));
 }
 public void click(){touch(0,new float[]{cx},new float[]{cy});touch(1,new float[]{cx},new float[]{cy});}
 public float scrollSpeed(){return prefs.getFloat("scrollSpeed."+activePackage,"eu.kanade.tachiyomi".equals(activePackage)?.2f:.5f);}
 public boolean swipeScrolling(){return prefs.getBoolean("swipeScroll."+activePackage,"eu.kanade.tachiyomi".equals(activePackage)||prefs.getBoolean("swipeScroll",false));}
 public void setSwipeScrolling(boolean swipe){if(activePackage.isEmpty()){report("Launch an app first");return;}cancelPhoneInput();prefs.edit().putBoolean("swipeScroll."+activePackage,swipe).apply();report(swipe?"Swipe scrolling saved for active app":"Wheel scrolling saved for active app");}
 public void saveAppSettings(){if(activePackage.isEmpty()){report("Launch an app first");return;}prefs.edit().putInt("app."+activePackage,rotation).putFloat("scrollSpeed."+activePackage,scrollSpeed()).putBoolean("swipeScroll."+activePackage,swipeScrolling()).apply();report("Rotation, scroll mode and speed saved for active app");}
 public void resetAppSettings(){if(activePackage.isEmpty()){report("Launch an app first");return;}if(busy){report("Wait for the display change to finish");return;}cancelPhoneInput();prefs.edit().remove("app."+activePackage).remove("scrollSpeed."+activePackage).remove("swipeScroll."+activePackage).apply();if(id>=0&&rotation!=defaultRotation)rotate(defaultRotation,false);report("Active app settings reset to defaults");}
 public void showTouchpadPointer(){if(id<0||busy||monitorContacts!=null)return;pointerVisible=true;if(monitor!=null)monitor.cursor.invalidate();}
 public void wheel(float dx,float dy){if(id<0||busy||monitorContacts!=null||worker.getQueue().size()>32)return;int target=id;float x=cx,y=cy;showTouchpadPointer();float speed=scrollSpeed();queue(s->s.scroll(target,x,y,dx*speed,dy*speed));}
 public void key(int key){if(id<0||busy){log("Key ignored: "+KeyEvent.keyCodeToString(key)+"; desktop "+(id<0?"unavailable":"busy"));return;}int target=id;log("Key queued: "+KeyEvent.keyCodeToString(key)+" -> display "+target);queue(s->{s.key(target,key);log("Key sent: "+KeyEvent.keyCodeToString(key)+" -> display "+target);});}
 public void move(float dx,float dy){if(monitorContacts!=null)return;showTouchpadPointer();cx=Geometry.clamp(cx+dx,0,width()-1);cy=Geometry.clamp(cy+dy,0,height()-1);if(monitor!=null)monitor.cursor.invalidate();}
 public void center(){cx=width()/2f;cy=height()/2f;move(0,0);}
 public void cancelPhoneInput(){if(id>=0&&contactsX!=null){int target=id;float[] x=contactsX,y=contactsY;contactsX=null;contactsY=null;queue(s->s.touch(target,MotionEvent.ACTION_CANCEL,x,y));}}
 public void cancelInput(){cancelPhoneInput();cancelMonitorInput();}
 private void relay(MotionEvent event){
  int target=id,token=generation;IDeskService service=bridge;
  if((event.getActionMasked()==MotionEvent.ACTION_MOVE&&worker.getQueue().size()>32)||target<0){event.recycle();return;}
  worker.execute(()->{try{if(token==generation&&service!=null)service.relayTouch(target,event);}catch(Throwable e){report("Monitor touch failed: "+e.getMessage());}finally{event.recycle();}});
 }
 private void cancelMonitorInput(){
  if(monitorContacts!=null){MotionEvent cancel=MotionEvent.obtainNoHistory(monitorContacts);cancel.setAction(MotionEvent.ACTION_CANCEL);relay(cancel);monitorContacts.recycle();monitorContacts=null;}
  monitorBlocked=true;
 }
 public void setMonitorTouchEnabled(boolean enabled){cancelMonitorInput();prefs.edit().putBoolean("monitorTouch",enabled).apply();report(enabled?"Monitor touch enabled":"Monitor touch disabled");}
 public boolean monitorTouchEnabled(){return prefs.getBoolean("monitorTouch",true);}
 /** Only the physical monitor window calls this. Injected events target the virtual display. */
 boolean monitorTouch(MotionEvent original,float physicalW,float physicalH){
  if(!original.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)||external==null)return false;
  int action=original.getActionMasked();if(action==MotionEvent.ACTION_DOWN){pointerVisible=false;if(monitor!=null)monitor.cursor.invalidate();}if(action==MotionEvent.ACTION_DOWN)android.util.Log.i("DexDroidTouch","physical DOWN target="+id+" busy="+busy+" enabled="+monitorTouchEnabled()+" size="+physicalW+"x"+physicalH);
  if(action==MotionEvent.ACTION_DOWN){cancelMonitorInput();monitorBlocked=false;}
  if(!monitorTouchEnabled()||id<0||busy){if(action==MotionEvent.ACTION_DOWN)log("Monitor touch ignored: enabled="+monitorTouchEnabled()+" display="+id+" busy="+busy);cancelMonitorInput();return true;}
  if(monitorBlocked)return true;
  if(action!=MotionEvent.ACTION_DOWN&&monitorContacts==null)return true;
  for(int i=0;i<original.getPointerCount();i++)if(Geometry.unmap(original.getX(i),original.getY(i),rotation,physicalW,physicalH,width(),height())==null){log("Monitor touch canceled: contact outside desktop Fit area");cancelMonitorInput();return true;}
  if(action==MotionEvent.ACTION_DOWN)cancelPhoneInput();
  float[] src={0,0,width(),0,width(),height(),0,height()},dst=new float[8];
  float[][] corners={{0,0},{1,0},{1,1},{0,1}};
  for(int i=0;i<4;i++){float[] p=Geometry.map(corners[i][0],corners[i][1],rotation,physicalW,physicalH,width(),height());dst[i*2]=p[0];dst[i*2+1]=p[1];}
  Matrix forward=new Matrix(),inverse=new Matrix();forward.setPolyToPoly(src,0,dst,0,4);
  if(!forward.invert(inverse)){cancelMonitorInput();return true;}
  MotionEvent mapped=MotionEvent.obtain(original);mapped.transform(inverse);mapped=boundMonitorContacts(mapped);
  cx=Geometry.clamp(mapped.getX(),0,width()-1);cy=Geometry.clamp(mapped.getY(),0,height()-1);
  if(monitor!=null)monitor.cursor.invalidate();
  if(monitorContacts!=null)monitorContacts.recycle();
  monitorContacts=remainingMonitorContacts(mapped);
  if(action!=MotionEvent.ACTION_MOVE)log("Monitor touch: "+MotionEvent.actionToString(original.getAction())+" pointers="+original.getPointerCount());relay(mapped);return true;
 }
 /** An exact rotated screen edge can map to width/height; use the last valid pixel. */
 private MotionEvent boundMonitorContacts(MotionEvent event){
  boolean needsClamp=false;
  for(int i=0;i<event.getPointerCount();i++)if(event.getX(i)<0||event.getY(i)<0||event.getX(i)>width()-1||event.getY(i)>height()-1)needsClamp=true;
  if(!needsClamp)return event;
  int count=event.getPointerCount();MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[count];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
  for(int i=0;i<count;i++){props[i]=new MotionEvent.PointerProperties();coords[i]=new MotionEvent.PointerCoords();event.getPointerProperties(i,props[i]);event.getPointerCoords(i,coords[i]);coords[i].x=Geometry.clamp(coords[i].x,0,width()-1);coords[i].y=Geometry.clamp(coords[i].y,0,height()-1);}
  MotionEvent bounded=MotionEvent.obtain(event.getDownTime(),event.getEventTime(),event.getAction(),count,props,coords,event.getMetaState(),event.getButtonState(),event.getXPrecision(),event.getYPrecision(),event.getDeviceId(),event.getEdgeFlags(),event.getSource(),event.getFlags());event.recycle();return bounded;
 }
 private MotionEvent remainingMonitorContacts(MotionEvent event){
  int action=event.getActionMasked();
  if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)return null;
  if(action!=MotionEvent.ACTION_POINTER_UP)return MotionEvent.obtainNoHistory(event);
  int count=event.getPointerCount()-1;
  MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[count];
  MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
  for(int i=0,out=0;i<event.getPointerCount();i++){if(i==event.getActionIndex())continue;props[out]=new MotionEvent.PointerProperties();coords[out]=new MotionEvent.PointerCoords();event.getPointerProperties(i,props[out]);event.getPointerCoords(i,coords[out]);out++;}
  return MotionEvent.obtain(event.getDownTime(),event.getEventTime(),MotionEvent.ACTION_MOVE,count,props,coords,event.getMetaState(),event.getButtonState(),event.getXPrecision(),event.getYPrecision(),event.getDeviceId(),event.getEdgeFlags(),event.getSource(),event.getFlags());
 }
 public String externalTouchDevices(){
  StringBuilder names=new StringBuilder();
  for(int deviceId:InputDevice.getDeviceIds()){InputDevice device=InputDevice.getDevice(deviceId);if(device!=null&&device.isExternal()&&device.supportsSource(InputDevice.SOURCE_TOUCHSCREEN)){if(names.length()>0)names.append(", ");names.append(device.getName());}}
  return names.length()==0?"No external touchscreen detected":names.toString();
 }
 View monitorInputView(){return monitor==null?null:monitor.getWindow().getDecorView();}
 boolean sessionAwake(){return sessionWakeLock!=null&&sessionWakeLock.isHeld();}
 public boolean backgroundProtectionEnabled(){return ((PowerManager)getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName());}
 public void rotate(int selected,boolean saveDefault){if(selected<0||selected>3||busy)return;if(saveDefault){defaultRotation=selected;prefs.edit().putInt("rotation",selected).apply();}configure(baseW,baseH,dpi,selected,null);}
 private void configure(int bw,int bh,int density,int selected,Runnable after){
  if(id<0){baseW=bw;baseH=bh;dpi=density;rotation=selected;if(after!=null)after.run();notifyUi();return;}
  cancelInput();busy=true;float u=cx/width(),v=cy/height();int oldW=baseW,oldH=baseH,oldDpi=dpi,oldRotation=rotation;int newW=selected%2==0?bw:bh,newH=selected%2==0?bh:bw;int token=generation;IDeskService target=bridge;monitor.texture.getSurfaceTexture().setDefaultBufferSize(newW,newH);Surface resizedSurface=new Surface(monitor.texture.getSurfaceTexture());
  worker.execute(()->{try{target.resize(resizedSurface,newW,newH,density);main.post(()->{if(token!=generation)return;baseW=bw;baseH=bh;dpi=density;rotation=selected;cx=u*newW;cy=v*newH;monitor.texture.getSurfaceTexture().setDefaultBufferSize(newW,newH);monitor.transform();main.postDelayed(()->{if(token!=generation)return;busy=false;notifyUi();if(after!=null)after.run();launchPending();},250);});}catch(Throwable e){try{target.resize(resizedSurface,oldRotation%2==0?oldW:oldH,oldRotation%2==0?oldH:oldW,oldDpi);}catch(Throwable ignored){}main.post(()->{busy=false;report("Resize failed; retained previous settings: "+e.getMessage());if(pendingProfile!=null)confirm(false);});}finally{resizedSurface.release();}});
 }
 private void launchPending(){if(pendingApp!=null&&id>=0&&!busy){ComponentName app=pendingApp;Integer orientation=pendingOrientation;pendingApp=null;pendingOrientation=null;launch(app,orientation);}}
 public void launch(ComponentName component,Integer oneTime){if(id<0||busy){pendingApp=component;pendingOrientation=oneTime;log("App launch queued: "+component.flattenToShortString());ensureDesktop();return;}activePackage=component.getPackageName();String packageName=activePackage;int selected=oneTime!=null?oneTime:prefs.getInt("app."+activePackage,defaultRotation);notifyUi();Runnable start=()->{int target=id;queue(s->{String result=s.launch(target,component.flattenToString());report("Opened "+packageName+" • "+width()+" × "+height()+" • "+dpi+" UI DPI\n"+result.trim());});};if(selected!=rotation)configure(baseW,baseH,dpi,selected,start);else start.run();}
 public void rememberApp(boolean remember){if(activePackage.isEmpty()){report("Launch an app first");return;}SharedPreferences.Editor edit=prefs.edit();if(remember)edit.putInt("app."+activePackage,rotation);else edit.remove("app."+activePackage);edit.apply();report(remember?"Orientation saved for "+activePackage:"App now uses the session orientation");}
 public boolean awaitingConfirmation(){return pendingProfile!=null;}
 public void preview(int bw,int bh,int density){if(busy||id<0||pendingProfile!=null){report("Start desktop and finish any pending change first");return;}pendingProfile=new int[]{baseW,baseH,dpi,rotation};profileKey=monitorKey();configure(bw,bh,density,rotation,()->{report("Keep this desktop resolution? Automatically reverts in 15 seconds.");main.postDelayed(rollback,15000);notifyUi();});}
 public void confirm(boolean keep){if(pendingProfile==null || (keep&&busy))return;main.removeCallbacks(rollback);int[] previous=pendingProfile;pendingProfile=null;if(keep){prefs.edit().putInt(profileKey+".w",baseW).putInt(profileKey+".h",baseH).putInt(profileKey+".dpi",dpi).apply();report("Monitor profile saved");}else configure(previous[0],previous[1],previous[2],previous[3],()->report("Previous desktop settings restored"));notifyUi();}
 public void resetMonitor(){String key=monitorKey();prefs.edit().remove(key+".w").remove(key+".h").remove(key+".dpi").apply();report("Saved monitor profile reset. Current session unchanged.");}
 public void stopDesktop(){cancelInput();generation++;id=-1;busy=false;main.removeCallbacks(rollback);pendingProfile=null;if(monitor!=null){Monitor old=monitor;monitor=null;old.dismiss();}IDeskService target=bridge;if(target!=null)worker.execute(()->{try{target.stop();}catch(Throwable ignored){}});if(sessionWakeLock!=null&&sessionWakeLock.isHeld())sessionWakeLock.release();stopForeground(true);stopSelf();report("Desktop stopped");}
 @Override public void onDisplayAdded(int added){detect();if(external!=null&&external.getDisplayId()==added){log("Monitor connected: "+external.getName());if(autoStartRequested)ensureDesktop();}}
 @Override public void onDisplayRemoved(int removed){if(external!=null&&external.getDisplayId()==removed){stopDesktop();report("Monitor disconnected. Waiting for reconnection.");}detect();}
 @Override public void onDisplayChanged(int changed){if(monitor!=null&&monitor.getDisplay().getDisplayId()==changed)monitor.transform();}
 @Override public void onDestroy(){autoStartRequested=false;stopDesktop();displays.unregisterDisplayListener(this);Shizuku.removeRequestPermissionResultListener(permission);Shizuku.removeBinderDeadListener(death);Shizuku.removeBinderReceivedListener(received);if(bound)try{Shizuku.unbindUserService(args,connection,true);}catch(Throwable ignored){}worker.shutdown();super.onDestroy();}
 private class Monitor extends Presentation implements TextureView.SurfaceTextureListener {
  TextureView texture;View cursor;
  Monitor(Display display){super(SessionService.this,display);}
  @Override protected void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(5894);FrameLayout frame=new FrameLayout(getContext());frame.setBackgroundColor(Color.BLACK);texture=new TextureView(getContext());texture.setSurfaceTextureListener(this);frame.addView(texture,new FrameLayout.LayoutParams(-1,-1));cursor=new View(getContext()){@Override protected void onDraw(Canvas canvas){if(!pointerVisible)return;float[] point=Geometry.map(cx/width(),cy/height(),rotation,getWidth(),getHeight(),width(),height());Paint p=new Paint(3);p.setColor(Color.BLACK);canvas.drawCircle(point[0],point[1],13,p);p.setColor(Color.rgb(94,234,212));canvas.drawCircle(point[0],point[1],9,p);}};frame.addView(cursor,new FrameLayout.LayoutParams(-1,-1));setContentView(frame);}
  @Override public boolean dispatchTouchEvent(MotionEvent event){if(texture!=null&&monitorTouch(event,texture.getWidth(),texture.getHeight()))return true;return super.dispatchTouchEvent(event);}
  void transform(){int w=texture.getWidth(),h=texture.getHeight();if(w==0||h==0)return;float[] src={0,0,w,0,w,h,0,h},dst=new float[8];float[][] corners={{0,0},{1,0},{1,1},{0,1}};for(int i=0;i<4;i++){float[] mapped=Geometry.map(corners[i][0],corners[i][1],rotation,w,h,width(),height());dst[i*2]=mapped[0];dst[i*2+1]=mapped[1];}Matrix matrix=new Matrix();matrix.setPolyToPoly(src,0,dst,0,4);texture.setTransform(matrix);cursor.invalidate();}
  public void onSurfaceTextureAvailable(SurfaceTexture surface,int w,int h){surface.setDefaultBufferSize(width(),height());transform();Surface target=new Surface(surface);int token=generation;IDeskService service=bridge;worker.execute(()->{try{int created=service.create(target,width(),height(),dpi,sessionOwner);main.post(()->{if(token!=generation)return;id=created;busy=false;report("Desktop ready • "+width()+" × "+height()+" • "+dpi+" UI DPI. Choose Apps.");launchPending();});}catch(Throwable e){main.post(()->{if(token==generation){stopDesktop();report("Desktop creation failed: "+e.getMessage());}});}finally{target.release();}});}
  public void onSurfaceTextureSizeChanged(SurfaceTexture surface,int w,int h){transform();}
  public boolean onSurfaceTextureDestroyed(SurfaceTexture surface){if(monitor==this){stopDesktop();}return true;}
  public void onSurfaceTextureUpdated(SurfaceTexture surface){}
 }
}
