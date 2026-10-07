// SPDX-License-Identifier: MIT
package io.github.muyuchen.duokannight;
import android.app.*;
import android.content.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.util.Log;
import java.lang.reflect.*;
import java.util.*;

/** Transient E-Ink theme experiment. Does not patch native code, APK or preference keys. */
public class NightSession extends Instrumentation {
  private static final String TAG="DuokanNight";
  private final Handler main=new Handler(Looper.getMainLooper());
  private boolean night;
  private boolean observeOnly;
  private String startingNonce;
  private final Map<Object,Object> originals=new WeakHashMap<Object,Object>();
  private final Map<Activity,Integer> generations=new WeakHashMap<Activity,Integer>();
  public void onCreate(Bundle args) {
    super.onCreate(args);
    String mode=args==null?"night":args.getString("mode","night");
    if (!mode.equals("audit") && !mode.equals("night") && !mode.equals("day")) {
      Bundle r=new Bundle();r.putString("error","Unsupported mode");finish(1,r);return;
    }
    startingNonce=args==null?null:args.getString("nonce");
    night=mode.equals("night");observeOnly=mode.equals("audit");start();
  }
  public void onStart() {
    getTargetContext().registerReceiver(new BroadcastReceiver(){
      public void onReceive(Context context,Intent query){
        try { StateProvider.report(context,query.getStringExtra("nonce"),night && !observeOnly); }
        catch(RuntimeException error){Log.w(TAG,"STATE_REPORT_FAILED",error);}
      }
    },new IntentFilter(StateProvider.QUERY_ACTION));
    try { StateProvider.report(getTargetContext(),startingNonce,night && !observeOnly); }
    catch(RuntimeException error){Log.w(TAG,"INITIAL_STATE_REPORT_FAILED",error);}
    ((Application)getTargetContext().getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
      public void onActivityResumed(Activity a){Log.i(TAG,"RESUMED "+a.getClass().getName());schedule(a);}
      public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){} public void onActivityPaused(Activity a){} public void onActivityStopped(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle b){} public void onActivityDestroyed(Activity a){generations.remove(a);}
    });
    Intent home=new Intent().setClassName("com.duokan.einkreader","com.duokan.einkreader.EInkReaderActivity");
    home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);getTargetContext().startActivity(home);
    Log.i(TAG,"SESSION_STARTED mode="+(observeOnly?"audit":night?"night":"day")+" preferencesWritten=false");
    // Return without finish: app uses the regular main loop. No permanent polling or helper service.
  }
  public void callActivityOnResume(Activity a) { super.callActivityOnResume(a);schedule(a); }
  public void callActivityOnNewIntent(Activity a,Intent i) { super.callActivityOnNewIntent(a,i);schedule(a); }
  public void callActivityOnDestroy(Activity a) { generations.remove(a);super.callActivityOnDestroy(a); }
  private void schedule(final Activity a) {
    if (!a.getClass().getName().equals("com.duokan.einkreader.EInkReadingActivity")) return;
    final int generation=generations.containsKey(a)?generations.get(a)+1:1;generations.put(a,generation);
    main.postDelayed(new Runnable(){int attempts=0;public void run(){
      if (!generations.containsKey(a) || generations.get(a)!=generation || a.isFinishing() || a.isDestroyed()) return;
      try {
        if (!inspectAndApply(a)) { if(++attempts<50)main.postDelayed(this,200);else Log.e(TAG,"TIMEOUT native reading controller not ready"); }
      } catch(Throwable e) { Log.e(TAG,"SESSION_FAILED",e); }
    }},200);
  }
  private Field field(Class<?> c,String name) throws Exception {
    for(Class<?> t=c;t!=null;t=t.getSuperclass()) { try{Field f=t.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException next){} }
    throw new NoSuchFieldException(name);
  }
  private Object call(Object object,Class<?> owner,String name) throws Exception { Method m=owner.getMethod(name);m.setAccessible(true);return m.invoke(object); }
  private boolean inspectAndApply(final Activity a) throws Exception {
    Object reader=field(a.getClass(),"mReaderController").get(a);if(reader==null){Log.i(TAG,"PENDING reader");return false;}
    Object controller=call(reader,getTargetContext().getClassLoader().loadClass("com.duokan.reader.ReaderController"),"getReaderController");
    if(controller==null){Log.i(TAG,"PENDING reading controller");return false;}
    String cls=controller.getClass().getName();
    if(!cls.equals("com.duokan.reader.ui.reading.EInkTxtController") && !cls.equals("com.duokan.reader.ui.reading.EInkEpubController")) {
      Log.i(TAG,"UNSUPPORTED unchanged controller="+cls);return true;
    }
    Object feature=field(controller.getClass(),"mReadingFeature").get(controller);
    Class<?> featureApi=getTargetContext().getClassLoader().loadClass("com.duokan.reader.ui.reading.ReadingFeature");
    if(!((Boolean)call(feature,featureApi,"isReadingReady"))){Log.i(TAG,"PENDING ready "+cls);return false;}
    Field themeField=field(controller.getClass(),"mEinkTheme");final Object original=themeField.get(controller);if(original==null)return false;
    if(originals.containsKey(controller))return true;
    Class<?> api=getTargetContext().getClassLoader().loadClass("com.duokan.reader.ui.reading.IEInkReadingThemeInterface");
    int beforeText=(Integer)call(feature,featureApi,"getPageTextColor");
    Drawable beforeBackground=(Drawable)call(feature,featureApi,"getPageBackground");
    int beforeBg=beforeBackground instanceof ColorDrawable?((ColorDrawable)beforeBackground).getColor():0;
    if(!observeOnly) {
      final ColorDrawable background=new ColorDrawable(night?Color.BLACK:Color.WHITE);
      Object replacement=Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},new InvocationHandler(){
        public Object invoke(Object proxy,Method method,Object[] args) throws Throwable {
          if(method.getName().equals("getPageBackground"))return background;
          if(method.getName().equals("getPageTextColor"))return night?Color.WHITE:Color.BLACK;
          if(method.getName().equals("getStatusColor"))return night?0xffdddddd:0xff888888;
          if(method.getName().equals("equals"))return proxy==args[0];
          if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
          if(method.getName().equals("toString"))return "TransientEInkTheme";
          throw new UnsupportedOperationException(method.getName());
        }
      });
      themeField.set(controller,replacement);
      try{call(feature,featureApi,"applyPrefs");}
      catch(Exception failed){themeField.set(controller,original);throw failed;}
      originals.put(controller,original);
    }else originals.put(controller,original);
    int afterText=(Integer)call(feature,featureApi,"getPageTextColor");
    Drawable afterBackground=(Drawable)call(feature,featureApi,"getPageBackground");
    int afterBg=afterBackground instanceof ColorDrawable?((ColorDrawable)afterBackground).getColor():0;
    Log.i(TAG,"THEME_APPLIED controller="+cls+" beforeText="+Integer.toHexString(beforeText)+" beforeBg="+Integer.toHexString(beforeBg)+" afterText="+Integer.toHexString(afterText)+" afterBg="+Integer.toHexString(afterBg)+" observeOnly="+observeOnly);
    final String label=observeOnly?"audit":night?"night":"day";

    return true;
  }
}
