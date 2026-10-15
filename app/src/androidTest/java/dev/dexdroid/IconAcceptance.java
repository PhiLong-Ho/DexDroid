package dev.dexdroid;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import java.io.*;

/** Checks installed launcher resources through Android's real vector renderer. */
final class IconAcceptance {
    private final Instrumentation runner;
    private final StringBuilder results = new StringBuilder();

    IconAcceptance(Instrumentation runner) {
        this.runner = runner;
    }

    private void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        results.append("PASS ").append(label).append('\n');
    }

    private Drawable vector(int id) {
        return runner.getTargetContext().getResources().getDrawable(id, null).mutate();
    }

    private Bitmap foreground(Drawable drawable, int size) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        drawable.setBounds(-size / 4, -size / 4, size * 5 / 4, size * 5 / 4);
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }

    private Path mask(String name, int size) {
        float half = size / 2f;
        Path path = new Path();
        if (name.equals("Circle")) path.addCircle(half, half, half, Path.Direction.CW);
        else if (name.equals("Rounded square"))
            path.addRoundRect(
                    0, 0, size, size, size * 24f / 108, size * 24f / 108, Path.Direction.CW);
        else if (name.equals("Squircle")) {
            for (int i = 0; i <= 256; i++) {
                double angle = i * Math.PI * 2 / 256, cos = Math.cos(angle), sin = Math.sin(angle);
                float x = half + (float) Math.copySign(Math.sqrt(Math.abs(cos)) * half, cos),
                        y = half + (float) Math.copySign(Math.sqrt(Math.abs(sin)) * half, sin);
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            path.close();
        } else path.addRect(0, 0, size, size, Path.Direction.CW);
        return path;
    }

    private Bitmap clipped(Bitmap foreground, Path mask, boolean background) {
        Bitmap result =
                Bitmap.createBitmap(
                        foreground.getWidth(), foreground.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.clipPath(mask);
        if (background) canvas.drawColor(0xff080808);
        canvas.drawBitmap(foreground, 0, 0, null);
        return result;
    }

    private long alpha(Bitmap bitmap) {
        long sum = 0;
        for (int y = 0; y < bitmap.getHeight(); y++)
            for (int x = 0; x < bitmap.getWidth(); x++) sum += Color.alpha(bitmap.getPixel(x, y));
        return sum;
    }

    private void grayscale(Bitmap bitmap, String label) {
        int ink = 0, dark = 0;
        for (int y = 0; y < bitmap.getHeight(); y++)
            for (int x = 0; x < bitmap.getWidth(); x++) {
                int pixel = bitmap.getPixel(x, y);
                checkColor(pixel, label);
                if (Color.alpha(pixel) > 200) {
                    if (Color.red(pixel) > 220) ink++;
                    if (Color.red(pixel) < 20) dark++;
                }
            }
        check(
                ink > bitmap.getWidth() * bitmap.getHeight() / 30,
                label + " has readable white artwork");
        check(
                dark > bitmap.getWidth() * bitmap.getHeight() / 3,
                label + " retains black negative space");
    }

    private void checkColor(int pixel, String label) {
        if (Color.alpha(pixel) != 0
                && (Color.red(pixel) != Color.green(pixel)
                        || Color.green(pixel) != Color.blue(pixel)))
            throw new AssertionError(label + " contains an accent color");
    }

    private void save(Bitmap bitmap, File file) throws Exception {
        try (FileOutputStream stream = new FileOutputStream(file)) {
            check(
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream),
                    "exported " + file.getName());
        }
    }

    private float[] eye(float localX) {
        double angle = Math.PI * 225 / 180;
        float x = 61 + .85f * (float) (localX * Math.cos(angle) + 11.5 * Math.sin(angle)),
                y = 38 + .85f * (float) (localX * Math.sin(angle) - 11.5 * Math.cos(angle));
        return new float[] {(x * 1.5f - 27) * 512 / 108, (y * 1.5f - 27) * 512 / 108};
    }

    String run() throws Exception {
        Context context = runner.getTargetContext();
        Drawable installed =
                context.getPackageManager().getApplicationIcon(context.getPackageName());
        check(
                installed instanceof AdaptiveIconDrawable,
                "installed app exposes an adaptive launcher icon");
        AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) installed;
        check(
                adaptive.getBackground() instanceof ColorDrawable
                        && ((ColorDrawable) adaptive.getBackground()).getColor() == 0xff080808,
                "installed adaptive background matches the black app theme");
        File directory = new File(context.getExternalCacheDir(), "icon-acceptance");
        check(directory.isDirectory() || directory.mkdirs(), "preview directory available");
        Bitmap raw = foreground(vector(R.drawable.ic_launcher_foreground), 512),
                mono = foreground(vector(R.drawable.ic_launcher_monochrome), 512);
        long originalAlpha = alpha(raw);
        check(originalAlpha > 0, "packaged foreground renders visible artwork");
        check(raw.sameAs(mono), "themed and standard icons use the same silhouette");
        for (float localX : new float[] {-3.5f, 3.5f}) {
            float[] p = eye(localX);
            check(
                    Color.alpha(raw.getPixel(Math.round(p[0]), Math.round(p[1]))) < 20,
                    "Android eye remains a transparent cutout");
        }
        Drawable tinted = vector(R.drawable.ic_launcher_monochrome);
        tinted.setTint(Color.BLACK);
        Bitmap black = foreground(tinted, 512);
        check(alpha(black) == originalAlpha, "theme tint preserves silhouette and eye cutouts");
        black.recycle();
        Bitmap sheet = Bitmap.createBitmap(896, 280, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(0xff202020);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(16);
        text.setTextAlign(Paint.Align.CENTER);
        String[] shapes = {"Circle", "Rounded square", "Squircle", "Square"};
        for (int i = 0; i < shapes.length; i++) {
            String name = shapes[i];
            Bitmap bounded = clipped(raw, mask(name, 512), false);
            check(
                    alpha(bounded) == originalAlpha,
                    name + " mask does not clip the robot or monitor");
            bounded.recycle();
            Bitmap icon = clipped(raw, mask(name, 512), true);
            grayscale(icon, name);
            canvas.drawBitmap(
                    icon,
                    null,
                    new Rect(16 + i * 224, 16, 208 + i * 224, 208),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
            canvas.drawText(name, 112 + i * 224, 242, text);
            if (i == 1) save(icon, new File(directory, "dexdroid-icon.png"));
            icon.recycle();
        }
        save(sheet, new File(directory, "icon-mask-preview.png"));
        sheet.recycle();
        for (int size : new int[] {48, 64, 96}) {
            Bitmap small = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            adaptive.setBounds(0, 0, size, size);
            adaptive.draw(new Canvas(small));
            grayscale(small, "installed icon at " + size + " px");
            small.recycle();
        }
        Bitmap system = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        adaptive.setBounds(0, 0, 512, 512);
        adaptive.draw(new Canvas(system));
        save(system, new File(directory, "system-launcher-icon.png"));
        system.recycle();
        raw.recycle();
        mono.recycle();
        return results.toString();
    }
}
