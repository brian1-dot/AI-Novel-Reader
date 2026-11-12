package com.example.novelreader;
import android.Manifest;
import android.app.AlertDialog;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import android.speech.tts.*;
import java.util.*;

public class MainActivity extends Activity {
 WebView web; EditText url; TextView info; TextToSpeech voicePreview;
 String next="",prev=""; boolean auto=true, moving=false;
 Handler handler=new Handler(Looper.getMainLooper());
 static final String JS="(function(){function t(x){return (x||'').trim()} let e=['#content','#chaptercontent','.chapter-content','.read-content','.content','article','main'].map(s=>document.querySelector(s)).filter(Boolean).sort((a,b)=>b.innerText.length-a.innerText.length)[0];let body=e?e.innerText:'';if(body.length<80)body=[...document.querySelectorAll('p')].filter(p=>p.innerText.length>18).map(p=>p.innerText).join('\\n');let a=[...document.querySelectorAll('a[href]')];function link(re){let z=a.find(x=>re.test(t(x.innerText)));return z?z.href:''}return JSON.stringify({title:document.title,text:body,next:link(/下一章|下一页|下一节/),prev:link(/上一章|上一节/)})})()";
 final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if("com.example.novelreader.NEXT".equals(i.getAction())){if(auto&&next.startsWith("https://")){moving=true;web.loadUrl(next);}else info.setText("本章结束，未找到下一章");}else if("com.example.novelreader.STATUS".equals(i.getAction()))info.setText(i.getStringExtra("status"));}};
 @Override public void onCreate(Bundle state){super.onCreate(state);
 if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},4);
 LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.WHITE);
 url=new EditText(this);url.setSingleLine(true);url.setTextSize(12);url.setText(getPreferences(0).getString("url","https://m.hetushu.com/book/9/6612.html"));root.addView(url);
 LinearLayout row=new LinearLayout(this);button(row,"打开网址",()->open(url.getText().toString()));button(row,"朗读本章",()->read());root.addView(row);
 LinearLayout row2=new LinearLayout(this);button(row2,"暂停/继续",()->command(ReaderService.PAUSE));button(row2,"上一章",()->move(prev));button(row2,"下一章",()->move(next));root.addView(row2);
 LinearLayout voices=new LinearLayout(this);button(voices,"选择朗读声音",()->chooseVoice());button(voices,"语音设置",()->{try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}catch(Exception e){info.setText("请到手机设置中搜索：文字转语音输出");}});root.addView(voices);
 LinearLayout row3=new LinearLayout(this);TextView label=new TextView(this);label.setText("语速 ");row3.addView(label);
 Spinner speed=new Spinner(this);speed.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"0.8×","1.0×","1.2×","1.5×"}));speed.setSelection(1);speed.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){getPreferences(0).edit().putFloat("rate",new float[]{.8f,1f,1.2f,1.5f}[pos]).apply();}});row3.addView(speed);
 CheckBox cb=new CheckBox(this);cb.setText("自动下一章");cb.setChecked(true);cb.setOnCheckedChangeListener((v,x)->auto=x);row3.addView(cb);root.addView(row3);
 info=new TextView(this);info.setText("打开小说正文后点击朗读本章");root.addView(info);
 web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){url.setText(u);getPreferences(0).edit().putString("url",u).apply();if(moving){moving=false;handler.postDelayed(()->read(),1400);}else info.setText("网页已打开，可尝试朗读");}@Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())info.setText("网站无法访问，请检查网址及访问限制");}});
 root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
 IntentFilter filter=new IntentFilter();filter.addAction("com.example.novelreader.NEXT");filter.addAction("com.example.novelreader.STATUS");if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,filter);
 open(url.getText().toString());}
 void button(LinearLayout row,String title,Runnable r){Button b=new Button(this);b.setText(title);b.setTextSize(12);row.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->r.run());}
 void chooseVoice(){info.setText("正在读取手机可用的中文声音…");voicePreview=new TextToSpeech(this,status->{if(status!=TextToSpeech.SUCCESS){info.setText("语音引擎初始化失败");return;}runOnUiThread(()->{ArrayList<Voice> vs=new ArrayList<>();try{Set<Voice> all=voicePreview.getVoices();if(all!=null)for(Voice v:all)if("zh".equals(v.getLocale().getLanguage()))vs.add(v);}catch(Exception e){}Collections.sort(vs,(a,b)->a.getName().compareTo(b.getName()));if(vs.isEmpty()){info.setText("手机没有可选中文语音，请在系统语音设置中安装中文语音");return;}String[] names=new String[vs.size()+1];names[0]="系统默认中文声音";for(int i=0;i<vs.size();i++){Voice v=vs.get(i);names[i+1]=(i+1)+". "+v.getName()+" · "+v.getLocale().toLanguageTag()+(v.isNetworkConnectionRequired()?"（需联网）":"（本地）");}new AlertDialog.Builder(this).setTitle("选择中文朗读声音（可点击试听）").setItems(names,(dialog,which)->{String id=which==0?"":vs.get(which-1).getName();getPreferences(0).edit().putString("voice",id).apply();try{if(which>0)voicePreview.setVoice(vs.get(which-1));else voicePreview.setLanguage(Locale.SIMPLIFIED_CHINESE);voicePreview.speak("你好，这是小说朗读声音试听。",TextToSpeech.QUEUE_FLUSH,null,"voice_test");}catch(Exception e){}info.setText(which==0?"已选择系统默认声音":"已选择 "+id+"，下一次朗读生效");}).setNegativeButton("取消",null).show();});});}
 void open(String s){if(!s.trim().startsWith("https://")){info.setText("只支持 HTTPS 网址");return;}web.loadUrl(s.trim());}
 void move(String u){if(u.startsWith("https://")){moving=true;web.loadUrl(u);}else info.setText("没有识别到章节链接");}
 void command(String action){Intent i=new Intent(this,ReaderService.class);i.setAction(action);startService(i);}
 void read(){web.evaluateJavascript(JS,raw->{try{String decoded=new JSONArray("["+raw+"]").getString(0);JSONObject data=new JSONObject(decoded);String text=data.optString("text","").trim();if(text.length()<80){info.setText("未能提取正文，网站可能限制了读取");return;}next=data.optString("next");prev=data.optString("prev");Intent i=new Intent(this,ReaderService.class);i.setAction(ReaderService.PLAY);i.putExtra("text",text);i.putExtra("title",data.optString("title","小说听书"));i.putExtra("rate",getPreferences(0).getFloat("rate",1f));i.putExtra("voice",getPreferences(0).getString("voice",""));startForegroundService(i);info.setText("开始朗读 "+text.length()+" 字");}catch(Exception ex){info.setText("章节识别失败");}});}
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
 @Override public void onDestroy(){try{unregisterReceiver(receiver);}catch(Exception e){}web.destroy();if(voicePreview!=null){voicePreview.stop();voicePreview.shutdown();}super.onDestroy();}
}
