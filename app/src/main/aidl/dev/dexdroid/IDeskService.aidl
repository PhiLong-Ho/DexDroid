package dev.dexdroid;
import android.view.Surface;
import android.view.MotionEvent;
import android.view.KeyEvent;
import dev.dexdroid.IDesktopListener;
import android.os.Bundle;
interface IDeskService {
 int create(in Surface surface, int width, int height, int dpi, IBinder owner, IDesktopListener listener);
 void resize(in Surface surface, int width, int height, int dpi);
 void touch(int display, int action, in float[] xs, in float[] ys);
 void relayTouch(int display, in MotionEvent event);
 void relayMotion(int display, in MotionEvent event);
 void relayKey(int display, in KeyEvent event);
 void closeApps(int display);
 void scroll(int display, float x, float y, float horizontal, float vertical);
 String launch(int display, String component);
 void key(int display, int code);
 void stop();
 boolean focusController(int taskId);
 Bundle audioBegin(long epoch, IBinder owner);
 Bundle audioQuery(long epoch);
 Bundle audioSelect(long epoch, String key);
 Bundle audioEnd(long epoch);
 Bundle disableDebugging();
}
