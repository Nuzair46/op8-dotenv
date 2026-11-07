package dev.nuzair.phonehome;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.View;

/** Original line artwork for common phone functions; platform icons for other apps. */
final class OutlineIcon extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String kind;
    private final Drawable icon;
    private boolean monochrome = true;
    OutlineIcon(Context c, AppEntry app, String explicit) {
        super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon = app == null ? null : app.icon.mutate();
        kind = explicit == null ? classify(app.pkg()) : explicit;
    }
    void setMonochrome(boolean enabled) { monochrome = enabled; invalidate(); }
    private String classify(String name) {
        name = name.toLowerCase();
        if (name.contains("dialer")) return "phone";
        if (name.contains("messaging") || name.contains("messenger")) return "message";
        if (name.contains("camera")) return "camera";
        if (name.equals("com.android.settings")) return "settings";
        if (name.contains("vending")) return "store";
        if (name.contains("deskclock")) return "clock";
        if (name.contains("calculator")) return "calculator";
        if (name.contains("calendar")) return "calendar";
        if (name.contains("documentsui") || name.contains("filemanager")) return "folder";
        if (name.contains("gallery") || name.contains("glimpse") || name.contains("photos")) return "gallery";
        if (name.contains("browser") || name.contains("jelly") || name.contains("chrome")) return "browser";
        if (name.contains("contacts")) return "contacts";
        if (name.contains("recorder")) return "mic";
        if (name.contains("email") || name.contains("gmail")) return "email";
        return "app";
    }
    private void line(Canvas c, float a, float b, float x, float y) { c.drawLine(a,b,x,y,p); }
    private void circle(Canvas c, float x, float y, float r) { c.drawCircle(x,y,r,p); }
    private void box(Canvas c,float l,float t,float r,float b,float radius) { c.drawRoundRect(l,t,r,b,radius,radius,p); }
    @Override protected void onDraw(Canvas original) {
        super.onDraw(original);
        if ((!monochrome || kind.equals("app")) && icon != null) {
            Drawable draw = icon;
            if (monochrome && Build.VERSION.SDK_INT >= 33 && icon instanceof AdaptiveIconDrawable) {
                Drawable mono = ((AdaptiveIconDrawable) icon).getMonochrome();
                if (mono != null) { draw = mono.mutate(); draw.setTint(0xffeeeeee); }
            }
            if (monochrome && draw == icon) {
                ColorMatrix matrix = new ColorMatrix(); matrix.setSaturation(0);
                draw.setColorFilter(new ColorMatrixColorFilter(matrix));
            } else if (!monochrome) draw.clearColorFilter();
            int pad = Math.round(getWidth() * .04f); draw.setBounds(pad,pad,getWidth()-pad,getHeight()-pad); draw.draw(original); return;
        }
        Canvas c = original; int saved = c.save(); c.scale(getWidth()/48f,getHeight()/48f);
        p.setColor(0xfff0f0f0); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.65f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        Path path = new Path();
        switch(kind) {
            case "phone":
                path.moveTo(15,8); path.lineTo(10,9); path.cubicTo(3,21,24,44,37,37); path.lineTo(39,31); path.lineTo(29,26); path.lineTo(25,31); path.cubicTo(20,29,16,25,14,20); path.lineTo(19,17); path.close(); c.drawPath(path,p); break;
            case "message": box(c,6,8,42,35,8); path.moveTo(14,35);path.lineTo(13,42);path.lineTo(23,35);c.drawPath(path,p);line(c,15,19,33,19);line(c,15,25,28,25);break;
            case "camera": box(c,5,13,43,39,6);path.moveTo(13,13);path.lineTo(17,7);path.lineTo(30,7);path.lineTo(34,13);c.drawPath(path,p);circle(c,24,26,8);circle(c,36,19,1);break;
            case "settings": circle(c,24,24,8);circle(c,24,24,16);for(int i=0;i<8;i++){double a=i*Math.PI/4;line(c,24+(float)Math.cos(a)*16,24+(float)Math.sin(a)*16,24+(float)Math.cos(a)*21,24+(float)Math.sin(a)*21);}break;
            case "store": box(c,9,13,39,42,4);c.drawArc(17,4,31,22,180,180,false,p);path.moveTo(21,23);path.lineTo(30,28);path.lineTo(21,33);path.close();c.drawPath(path,p);break;
            case "clock":circle(c,24,24,18);line(c,24,13,24,25);line(c,24,25,32,29);break;
            case "calculator":box(c,9,5,39,43,5);box(c,14,11,34,20,2);for(int y=27;y<=36;y+=9)for(int x=16;x<=32;x+=8)circle(c,x,y,1);break;
            case "calendar":box(c,7,9,41,41,5);line(c,7,19,41,19);line(c,16,5,16,13);line(c,32,5,32,13);line(c,16,26,20,26);line(c,28,26,32,26);line(c,16,33,20,33);break;
            case "folder":path.moveTo(5,17);path.lineTo(5,11);path.quadTo(5,7,10,7);path.lineTo(20,7);path.lineTo(25,13);path.lineTo(38,13);path.quadTo(43,13,43,18);path.lineTo(43,36);path.quadTo(43,40,38,40);path.lineTo(10,40);path.quadTo(5,40,5,36);path.close();c.drawPath(path,p);line(c,5,19,43,19);break;
            case "gallery":box(c,6,7,42,41,5);circle(c,31,17,4);path.moveTo(7,34);path.lineTo(18,23);path.lineTo(28,33);path.lineTo(34,28);path.lineTo(42,36);c.drawPath(path,p);break;
            case "browser":circle(c,24,24,18);c.drawOval(16,6,32,42,p);line(c,7,24,41,24);c.drawArc(7,10,41,25,0,180,false,p);c.drawArc(7,24,41,38,180,180,false,p);break;
            case "contacts":circle(c,24,16,8);c.drawArc(8,29,40,50,180,180,false,p);line(c,8,40,40,40);break;
            case "mic":box(c,18,5,30,30,6);c.drawArc(12,16,36,36,0,180,false,p);line(c,24,36,24,43);line(c,16,43,32,43);break;
            case "email":box(c,5,10,43,38,4);path.moveTo(6,13);path.lineTo(24,27);path.lineTo(42,13);c.drawPath(path,p);break;
            case "search":circle(c,21,21,12);line(c,30,30,40,40);break;
            case "grid":for(int y=10;y<40;y+=14)for(int x=10;x<40;x+=14)box(c,x-3,y-3,x+3,y+3,1);break;
            case "back":line(c,30,10,16,24);line(c,16,24,30,38);break;
            default:box(c,8,8,40,40,9);line(c,17,24,31,24);line(c,24,17,24,31);
        }
        c.restoreToCount(saved);
    }
}
