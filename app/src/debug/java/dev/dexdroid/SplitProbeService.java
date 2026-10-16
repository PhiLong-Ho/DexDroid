package dev.dexdroid;

import android.os.*;
import android.view.*;
import java.lang.reflect.Method;

/** Experimental shell bridge. Distinct component names give each pane its own process/display. */
@android.annotation.SuppressLint("PrivateApi")
public class SplitProbeService extends DeskService {
    static final String PROTOCOL = "dev.dexdroid.splitprobe.input";
    static final int INPUT = IBinder.FIRST_CALL_TRANSACTION + 100;
    private int owned = -1;
    private Object input;
    private Method inject;

    public static final class Left extends SplitProbeService {}

    public static final class Right extends SplitProbeService {}

    @Override
    public synchronized int create(
            Surface surface,
            int width,
            int height,
            int dpi,
            IBinder owner,
            IDesktopListener listener) {
        owned = super.create(surface, width, height, dpi, owner, listener);
        return owned;
    }

    @Override
    public synchronized void stop() {
        owned = -1;
        super.stop();
    }

    @Override
    public synchronized boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code != INPUT) return super.onTransact(code, data, reply, flags);
        data.enforceInterface(PROTOCOL);
        int display = data.readInt(), kind = data.readInt();
        MotionEvent motion = null;
        long identity = Binder.clearCallingIdentity();
        try {
            if (display < 1 || display != owned)
                throw new IllegalStateException("Obsolete prototype display");
            if (android.os.Process.myUid() != 2000)
                throw new SecurityException("Prototype requires Shizuku shell");
            if (input == null) {
                Class<?> manager;
                try {
                    manager = Class.forName("android.hardware.input.InputManagerGlobal");
                } catch (ClassNotFoundException legacy) {
                    manager = Class.forName("android.hardware.input.InputManager");
                }
                input = manager.getMethod("getInstance").invoke(null);
                inject = manager.getMethod("injectInputEvent", InputEvent.class, int.class);
            }
            InputEvent event;
            if (kind == 1) {
                MotionEvent received = MotionEvent.CREATOR.createFromParcel(data);
                try {
                    motion = normalized(received);
                } finally {
                    received.recycle();
                }
                event = motion;
            } else if (kind == 2) {
                KeyEvent key = KeyEvent.CREATOR.createFromParcel(data);
                if (key.getAction() == KeyEvent.ACTION_DOWN) focusTask(display);
                event = key;
            } else throw new IllegalArgumentException("Unknown prototype input");
            event.getClass().getMethod("setDisplayId", int.class).invoke(event, display);
            if (!(Boolean) inject.invoke(input, event, 0))
                throw new IllegalStateException("Prototype input rejected");
            reply.writeNoException();
        } catch (Exception error) {
            reply.writeException(new IllegalStateException("Prototype relay failed", error));
        } finally {
            if (motion != null) motion.recycle();
            Binder.restoreCallingIdentity(identity);
        }
        return true;
    }

    private static MotionEvent normalized(MotionEvent event) throws Exception {
        int count = event.getPointerCount();
        if (count < 1 || count > 16)
            throw new IllegalArgumentException("Invalid prototype contacts");
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            event.getPointerProperties(i, properties[i]);
            coords[i] = new MotionEvent.PointerCoords();
            event.getPointerCoords(i, coords[i]);
        }
        // A child View's local offsets are not a new display coordinate system.
        // Rebase raw coordinates AND mouse cursor coordinates before reinjection.
        MotionEvent copy =
                MotionEvent.obtain(
                        event.getDownTime(),
                        event.getEventTime(),
                        event.getAction(),
                        count,
                        properties,
                        coords,
                        event.getMetaState(),
                        event.getButtonState(),
                        event.getXPrecision(),
                        event.getYPrecision(),
                        event.getDeviceId(),
                        event.getEdgeFlags(),
                        event.getSource(),
                        event.getFlags());
        if (event.getActionButton() != 0)
            MotionEvent.class
                    .getMethod("setActionButton", int.class)
                    .invoke(copy, event.getActionButton());
        return copy;
    }

    private static void focusTask(int display) throws Exception {
        Object tasks =
                Class.forName("android.app.ActivityTaskManager")
                        .getMethod("getService")
                        .invoke(null);
        Class<?> api = Class.forName("android.app.IActivityTaskManager");
        String query = Build.VERSION.SDK_INT >= 31 ? "getAllRootTaskInfos" : "getAllStackInfos";
        for (Object root : (java.util.List<?>) api.getMethod(query).invoke(tasks)) {
            if (root.getClass().getField("displayId").getInt(root) != display) continue;
            int[] ids = (int[]) root.getClass().getField("taskIds").get(root);
            if (ids != null && ids.length > 0) {
                api.getMethod("setFocusedTask", int.class).invoke(tasks, ids[ids.length - 1]);
                return;
            }
        }
        throw new IllegalStateException("No focusable task in prototype pane");
    }
}
