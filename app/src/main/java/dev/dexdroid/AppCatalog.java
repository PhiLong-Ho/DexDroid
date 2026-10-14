package dev.dexdroid;

import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import java.util.*;

final class AppCatalog {
 static class Entry {final String name;final ComponentName component;final Drawable icon;Entry(String n,ComponentName c,Drawable i){name=n;component=c;icon=i;}}
 static List<Entry> installed(Context context){
  PackageManager pm=context.getPackageManager();List<Entry> apps=new ArrayList<>();Set<String> seen=new HashSet<>();
  for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)){String pkg=r.activityInfo.packageName;if(pkg.equals(context.getPackageName())||!seen.add(pkg))continue;apps.add(new Entry(r.loadLabel(pm).toString(),new ComponentName(pkg,r.activityInfo.name),r.loadIcon(pm)));}
  apps.add(new Entry("DexDroid input test",new ComponentName(context,InputTestActivity.class),context.getApplicationInfo().loadIcon(pm)));
  Set<String> favorites=new HashSet<>(Arrays.asList("dev.dexdroid","dev.portraitdesk","org.mozilla.firefox","app.revanced.android.youtube","eu.kanade.tachiyomi","com.android.chrome"));
  apps.sort((a,b)->{int order=Boolean.compare(favorites.contains(b.component.getPackageName()),favorites.contains(a.component.getPackageName()));return order!=0?order:a.name.compareToIgnoreCase(b.name);});return apps;
 }
}
