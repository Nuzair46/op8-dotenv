package dev.nuzair.phonehome;

import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int WHITE=0xfff0f0f0, MUTED=0xffa9a9ad;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<AppEntry> apps=new ArrayList<>();
    private SharedPreferences preferences;
    private FrameLayout root;
    private LinearLayout content, drawer;
    private GridLayout drawerGrid;
    private TextView clock,date,count;
    private EditText search;
    private int topInset,bottomInset,generation;
    private boolean resumed,drawerOpen;
    private float downX,downY;
    private final Runnable tick=new Runnable(){public void run(){updateClock();if(resumed)handler.postDelayed(this,60000-System.currentTimeMillis()%60000);}};
    private final BroadcastReceiver packages=new BroadcastReceiver(){public void onReceive(Context c,Intent i){refreshApps();}};

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        preferences=getSharedPreferences("appearance",MODE_PRIVATE);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarContrastEnforced(false);
        getWindow().setStatusBarContrastEnforced(false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        root=new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x66000000,0x10000000,0xcc000000}));
        root.setOnApplyWindowInsetsListener((view,insets)->{
            Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            topInset=bars.top;bottomInset=bars.bottom;
            if(content!=null)content.setPadding(dp(24),topInset+dp(10),dp(24),bottomInset+dp(12));
            if(drawer!=null){int ime=insets.getInsets(WindowInsets.Type.ime()).bottom;drawer.setPadding(dp(24),topInset+dp(12),dp(24),Math.max(bottomInset,ime)+dp(8));}
            return insets;
        });
        setContentView(root);
        WindowInsetsController bars=getWindow().getInsetsController();
        if(bars!=null)bars.setSystemBarsAppearance(0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        IntentFilter filter=new IntentFilter();
        for(String action:new String[]{Intent.ACTION_PACKAGE_ADDED,Intent.ACTION_PACKAGE_REMOVED,Intent.ACTION_PACKAGE_CHANGED,Intent.ACTION_PACKAGE_REPLACED})filter.addAction(action);
        filter.addDataScheme("package");
        registerReceiver(packages,filter,Context.RECEIVER_NOT_EXPORTED);
        showHome();
    }
    @Override protected void onResume(){super.onResume();resumed=true;handler.removeCallbacks(tick);tick.run();refreshApps();}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){generation++;handler.removeCallbacksAndMessages(null);unregisterReceiver(packages);worker.shutdownNow();super.onDestroy();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);showHome();}
    @Override public void onBackPressed(){
        if(drawerOpen){
            if(search!=null&&search.hasFocus()){search.clearFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);}
            showHome();
        }
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent e){
        if(!drawerOpen){
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();}
            if(e.getActionMasked()==MotionEvent.ACTION_UP&&downY-e.getY()>dp(75)&&Math.abs(e.getX()-downX)<dp(100)){
                MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();showDrawer();return true;
            }
        }
        return super.dispatchTouchEvent(e);
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,float size,int color){
        TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));view.setIncludeFontPadding(false);return view;
    }
    private GradientDrawable shape(int color,float radius,int stroke){
        GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(radius));if(stroke!=0)bg.setStroke(dp(1),stroke);return bg;
    }
    private Drawable pressable(int color,float radius,int stroke){
        return new RippleDrawable(ColorStateList.valueOf(0x25ffffff),shape(color,radius,stroke),shape(Color.WHITE,radius,0));
    }
    private LinearLayout vertical(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private void spacer(LinearLayout parent,int h){parent.addView(new View(this),lp(1,h));}
    private void launch(AppEntry app){
        try{
            Intent intent;
            // This stock port needs its public camera-entry dispatcher for initialization.
            if(app.pkg().equals("com.oneplus.camera"))intent=new Intent("android.media.action.STILL_IMAGE_CAMERA").setClassName("com.oneplus.camera","com.oneplus.camera.CameraEntry");
            else intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component);
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        }
        catch(RuntimeException e){Toast.makeText(this,"This app is unavailable",Toast.LENGTH_SHORT).show();refreshApps();}
    }
    private AppEntry find(String packageName){for(AppEntry app:apps)if(app.pkg().equals(packageName))return app;return null;}
    private void refreshApps(){
        if(worker.isShutdown())return;final int stamp=++generation;
        worker.execute(()->{List<AppEntry> result=AppEntry.load(this);handler.post(()->{
            if(stamp!=generation||isFinishing()||isDestroyed())return;
            apps.clear();apps.addAll(result);
            if(drawerOpen)updateDrawer(search.getText().toString());else showHome();
        });});
    }
    private void updateClock(){
        Date now=new Date();
        if(clock!=null)clock.setText(new SimpleDateFormat(android.text.format.DateFormat.is24HourFormat(this)?"HH:mm":"h:mm",Locale.getDefault()).format(now));
        if(date!=null)date.setText(new SimpleDateFormat("EEE, d MMM",Locale.getDefault()).format(now).toUpperCase(Locale.getDefault()));
    }
    private void showHome(){
        drawerOpen=false;drawer=null;search=null;drawerGrid=null;
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);
        root.removeAllViews();content=vertical();content.setPadding(dp(24),topInset+dp(10),dp(24),bottomInset+dp(12));
        root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        FrameLayout appearance=iconButton("settings","Appearance",()->appearance());header.addView(appearance,lp(48,48));content.addView(header,lp(-1,48));
        spacer(content,20);
        clock=text("",80,WHITE);clock.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));clock.setLetterSpacing(-.065f);clock.setContentDescription("Clock");
        clock.setOnClickListener(v->{AppEntry a=find("com.android.deskclock");if(a!=null)launch(a);});content.addView(clock,lp(-1,100));
        date=text("",12,0xffcccccf);date.setLetterSpacing(.12f);content.addView(date,lp(-1,25));updateClock();
        View space=new View(this);space.setOnLongClickListener(v->{appearance();return true;});content.addView(space,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout dock=new LinearLayout(this);dock.setGravity(Gravity.CENTER);dock.setBackground(shape(0x990d0d0d,28,0));dock.setPadding(dp(8),dp(6),dp(8),dp(6));
        for(String[] choices:new String[][]{{"com.android.dialer"},{"com.android.messaging"},{"org.lineageos.jelly","com.android.chrome"},{"com.oneplus.camera","org.lineageos.aperture"}}){
            AppEntry app=null;for(String pkg:choices){app=find(pkg);if(app!=null)break;}
            if(app!=null){final AppEntry chosen=app;FrameLayout cell=iconButtonForApp(app);cell.setOnClickListener(v->launch(chosen));cell.setOnLongClickListener(v->{appMenu(chosen);return true;});dock.addView(cell,new LinearLayout.LayoutParams(0,dp(58),1));}
        }
        content.addView(dock,lp(-1,70));root.requestApplyInsets();
    }
    private FrameLayout iconButtonForApp(AppEntry app){
        FrameLayout frame=new FrameLayout(this);frame.setContentDescription(app.label);frame.setBackground(pressable(Color.TRANSPARENT,22,0));
        OutlineIcon icon=new OutlineIcon(this,app,null);icon.setMonochrome(preferences.getBoolean("monochrome",true));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(29),dp(29),Gravity.CENTER);frame.addView(icon,p);return frame;
    }
    private FrameLayout iconButton(String type,String description,Runnable action){
        FrameLayout frame=new FrameLayout(this);frame.setContentDescription(description);frame.setBackground(pressable(Color.TRANSPARENT,24,0));
        OutlineIcon icon=new OutlineIcon(this,null,type);frame.addView(icon,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));frame.setOnClickListener(v->action.run());return frame;
    }
    private void addTile(GridLayout grid,AppEntry app,boolean inDrawer){
        LinearLayout tile=vertical();tile.setGravity(Gravity.CENTER_HORIZONTAL);tile.setPadding(dp(2),dp(6),dp(2),dp(4));tile.setBackground(pressable(Color.TRANSPARENT,16,0));
        tile.setContentDescription(app.label);tile.setOnClickListener(v->launch(app));tile.setOnLongClickListener(v->{appMenu(app);return true;});
        FrameLayout iconFrame=new FrameLayout(this);iconFrame.setBackground(shape(0x450c0c0c,18,0x20ffffff));
        OutlineIcon icon=new OutlineIcon(this,app,null);icon.setMonochrome(preferences.getBoolean("monochrome",true));iconFrame.addView(icon,new FrameLayout.LayoutParams(dp(31),dp(31),Gravity.CENTER));tile.addView(iconFrame,lp(56,56));
        TextView label=text(app.label,10.5f,0xffdddddf);label.setGravity(Gravity.CENTER);label.setMaxLines(2);label.setEllipsize(TextUtils.TruncateAt.END);label.setPadding(0,dp(6),0,0);label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);tile.addView(label,lp(-1,32));
        GridLayout.LayoutParams layout=new GridLayout.LayoutParams();layout.width=(getResources().getDisplayMetrics().widthPixels-dp(48))/4;layout.height=dp(100);layout.columnSpec=GridLayout.spec(GridLayout.UNDEFINED);grid.addView(tile,layout);
    }
    private void showDrawer(){
        drawerOpen=true;root.removeAllViews();content=null;clock=null;date=null;
        drawer=vertical();drawer.setPadding(dp(24),topInset+dp(12),dp(24),bottomInset+dp(8));drawer.setBackgroundColor(0xf5090909);drawer.setFocusableInTouchMode(true);
        root.addView(drawer,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("Apps",32,WHITE);header.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));
        header.addView(iconButton("back","Close app drawer",()->showHome()),lp(48,48));drawer.addView(header,lp(-1,56));spacer(drawer,12);
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(15);search.setTextColor(WHITE);search.setHintTextColor(0xff8c8c92);search.setHint("Search apps");search.setContentDescription("Search apps");search.setPadding(dp(16),0,dp(16),0);search.setBackground(shape(0xff181818,18,0x25ffffff));search.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);drawer.addView(search,lp(-1,48));
        count=text("",10,MUTED);count.setLetterSpacing(.1f);count.setGravity(Gravity.CENTER_VERTICAL);drawer.addView(count,lp(-1,40));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        drawerGrid=new GridLayout(this);drawerGrid.setColumnCount(4);scroll.addView(drawerGrid,new ScrollView.LayoutParams(-1,-2));drawer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){updateDrawer(s.toString());}public void afterTextChanged(Editable e){}});
        search.setOnEditorActionListener((v,action,event)->{((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();return true;});
        drawer.requestFocus();updateDrawer("");root.requestApplyInsets();
    }
    private void updateDrawer(String query){
        if(drawerGrid==null)return;drawerGrid.removeAllViews();int matches=0;String needle=query.trim().toLowerCase(Locale.getDefault());
        for(AppEntry app:apps)if(app.label.toLowerCase(Locale.getDefault()).contains(needle)||app.pkg().toLowerCase(Locale.ROOT).contains(needle)){addTile(drawerGrid,app,true);matches++;}
        count.setText(matches==0?"NO MATCHING APPS":matches+(matches==1?" APP":" APPS"));
    }
    private void appMenu(AppEntry app){
        new AlertDialog.Builder(this).setTitle(app.label).setItems(new String[]{"App info"},(dialog,index)->{
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+app.pkg())));
        }).show();
    }
    private void appearance(){
        final Dialog dialog=new Dialog(this);LinearLayout panel=vertical();panel.setPadding(dp(24),dp(22),dp(24),dp(24)+bottomInset);panel.setBackground(shape(0xff141414,28,0x30ffffff));
        TextView title=text("Appearance",24,WHITE);panel.addView(title,lp(-1,44));
        Switch mono=new Switch(this);mono.setText("Monochrome icons");mono.setTextColor(WHITE);mono.setTextSize(15);mono.setChecked(preferences.getBoolean("monochrome",true));mono.setOnCheckedChangeListener((v,value)->{preferences.edit().putBoolean("monochrome",value).apply();});panel.addView(mono,lp(-1,54));
        menuRow(panel,"Neural live wallpaper","Set the matching animated background",()->{dialog.dismiss();openWallpaper();});
        menuRow(panel,"Phone settings","Theme, display and system controls",()->{dialog.dismiss();startActivity(new Intent(Settings.ACTION_SETTINGS));});
        menuRow(panel,"Default home app","Switch between CustomPhone and Trebuchet",()->{dialog.dismiss();startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));});
        menuRow(panel,"Done",null,()->dialog.dismiss());
        dialog.setContentView(panel);Window window=dialog.getWindow();window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams params=window.getAttributes();params.dimAmount=.5f;window.setAttributes(params);window.setGravity(Gravity.BOTTOM);window.setLayout(-1,-2);
        dialog.setOnDismissListener(d->{if(drawerOpen)updateDrawer(search.getText().toString());else showHome();});dialog.show();window.setLayout(-1,-2);
    }
    private void menuRow(LinearLayout panel,String title,String subtitle,Runnable action){
        LinearLayout row=vertical();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(12),0,dp(12));row.setBackground(pressable(Color.TRANSPARENT,12,0));row.addView(text(title,15,WHITE),lp(-1,-2));
        if(subtitle!=null){TextView small=text(subtitle,11,MUTED);small.setPadding(0,dp(5),0,0);row.addView(small,lp(-1,-2));}row.setOnClickListener(v->action.run());panel.addView(row,lp(-1,-2));
    }
    private void openWallpaper(){
        try{startActivity(new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,new ComponentName(this,NeuralWallpaper.class)));}
        catch(RuntimeException e){Toast.makeText(this,"Open Wallpaper & style in Settings",Toast.LENGTH_LONG).show();}
    }
    @Override public void dump(String prefix,FileDescriptor fd,PrintWriter writer,String[] args){
        super.dump(prefix,fd,writer,args);writer.println(prefix+"CustomPhone apps="+apps.size()+" drawer="+drawerOpen+" monochrome="+preferences.getBoolean("monochrome",true));
    }
}
