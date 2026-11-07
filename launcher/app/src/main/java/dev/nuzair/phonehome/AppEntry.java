package dev.nuzair.phonehome;

import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import java.text.Collator;
import java.util.*;

final class AppEntry {
    final ComponentName component;
    final String label;
    final Drawable icon;
    AppEntry(ComponentName c, String l, Drawable d) { component = c; label = l; icon = d; }
    String key() { return component.flattenToString(); }
    String pkg() { return component.getPackageName(); }
    static List<AppEntry> load(Context context) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> candidates = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        ArrayList<AppEntry> result = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (ResolveInfo info : candidates) {
            ActivityInfo a = info.activityInfo;
            if (a == null || a.packageName.equals(context.getPackageName())) continue;
            ComponentName component = new ComponentName(a.packageName, a.name);
            if (!seen.add(component.flattenToString())) continue;
            try { result.add(new AppEntry(component, info.loadLabel(pm).toString(), info.loadIcon(pm))); }
            catch (RuntimeException ignored) {}
        }
        Collator sorter = Collator.getInstance();
        result.sort((a,b) -> sorter.compare(a.label, b.label));
        return result;
    }
}
