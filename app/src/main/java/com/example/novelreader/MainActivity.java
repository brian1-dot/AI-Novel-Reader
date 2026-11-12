package com.example.novelreader;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.os.*;
import android.speech.tts.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.util.*;

public class MainActivity extends Activity {
 WebView web;ScrollView readingScroll;TextView readingText;TextView info,chapter,timerStatus;String currentText="";ArrayList<SentenceSplitter.Piece> sentences=new ArrayList<>(); boolean showingReader=false;TextToSpeech voicePreview;
 String next="",prev="";boolean auto=true,moving=false;String currentChapterUrl="";int expectedChapter=-1;
 Handler handler=new Handler(Looper.getMainLooper());
 static final String JS="(function(){\n const title=(document.querySelector('h1')||document.querySelector('h2')||document.querySelector('.title')||document.querySelector('.headline'));\n let el=['#content','#chaptercontent','#chapterContent','.chapter-content','.read-content','.content','article','main'].map(s=>document.querySelector(s)).filter(Boolean).sort((a,b)=>b.innerText.length-a.innerText.length)[0];\n let body=el?el.innerText:'';\n if(body.length<80)body=[...document.querySelectorAll('p')].filter(p=>p.innerText.length>18).map(p=>p.innerText).join('\\n');\n const url=new URL(location.href),match=url.pathname.match(/^\\/book\\/(\\d+)\\/(\\d+)\\.html$/);\n function candidate(kind){\n  let selectors=kind==='next'?['a#next','a[rel=next]','a.next','.next a','a.nextpage']:['a#pre','a#prev','a[rel=prev]','a.prev','.prev a','a.prevpage'];\n  let matches=selectors.flatMap(sel=>[...document.querySelectorAll(sel)]);\n  let name=kind==='next'?/下一章|下章|下一节/:/上一章|上章|上一节/;\n  matches.push(...[...document.querySelectorAll('a[href]')].filter(a=>name.test((a.innerText||'').trim())));\n  let seen=new Set();\n  for(let a of matches){\n   let href=a.href;\n   if(!href||seen.has(href))continue;seen.add(href);\n   try{let u=new URL(href,location.href);let m=u.pathname.match(/^\\/book\\/(\\d+)\\/(\\d+)\\.html$/);\n    if(u.origin!==url.origin||u.href===url.href||!m||!match||m[1]!==match[1])continue; if(url.hostname.endsWith('hetushu.com') && Number(m[2])!==Number(match[2])+(kind==='next'?1:-1))continue;\n    return u.href;\n   }catch(e){}\n  }\n  return '';\n }\n return JSON.stringify({title:(title&&/第\\s*\\d+\\s*章/.test(title.innerText))?title.innerText:document.title,text:body,next:candidate('next'),prev:candidate('prev'),page:location.href});\n})()";
 final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){String a=i.getAction();if("com.example.novelreader.NEXT".equals(a)){if(auto){moveDirection(1);}else info.setText("本章已读完，自动下一章未开启");}else if("com.example.novelreader.SENTENCE".equals(a)){highlightSentence(i.getIntExtra("index",-1));}else if("com.example.novelreader.STATUS".equals(a)){String s=i.getStringExtra("status");if(s!=null)info.setText(s);}}};
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(19,37,43));getWindow().setNavigationBarColor(Color.rgb(19,37,43));
 if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},4);
 LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.rgb(250,249,246));root.setPadding(dp(12),dp(8),dp(12),0);
 LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView appTitle=new TextView(this);appTitle.setText("📖 AI 听书 v1.5");appTitle.setTextSize(21);appTitle.setTextColor(Color.rgb(28,53,55));appTitle.setTypeface(null,1);top.addView(appTitle,new LinearLayout.LayoutParams(0,dp(52),1));
 button(top,"原网页 / 跟读",()->toggleReading());button(top,"我的书架",()->showBookshelf());button(top,"换书 / 网址",()->showUrlDialog());root.addView(top);
 chapter=new TextView(this);chapter.setText("打开小说后，点击下方朗读");chapter.setTextSize(16);chapter.setTextColor(Color.rgb(40,88,77));chapter.setGravity(Gravity.CENTER);chapter.setPadding(dp(2),dp(8),dp(2),dp(8));root.addView(chapter);
 web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.setBackgroundColor(Color.WHITE);web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){currentChapterUrl=u;next="";prev="";getPreferences(0).edit().putString("url",u).apply();web.evaluateJavascript("document.title",result->{if(result!=null)chapter.setText(result.replace("\\u0022","").replace("\"",""));});if(moving){moving=false;showWeb();handler.postDelayed(()->read(),1300);}else info.setText("网页已打开，可开始朗读");}@Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())info.setText("网页打开失败，请检查网址");}});
 root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
 readingScroll=new ScrollView(this);readingScroll.setFillViewport(true);readingScroll.setBackgroundColor(Color.WHITE);readingText=new TextView(this);readingText.setTextSize(21);readingText.setTextColor(Color.rgb(35,42,42));readingText.setLineSpacing(dp(8),1.18f);readingText.setPadding(dp(14),dp(18),dp(14),dp(60));readingScroll.addView(readingText);root.addView(readingScroll,new LinearLayout.LayoutParams(-1,0,1));readingScroll.setVisibility(View.GONE);
 LinearLayout player=new LinearLayout(this);player.setOrientation(1);player.setPadding(dp(2),dp(6),dp(2),dp(4));player.setBackgroundColor(Color.rgb(235,244,239));
 LinearLayout transport=new LinearLayout(this);button(transport,"◀ 上一章",()->moveDirection(-1));button(transport,"▶ 朗读 / 重播",()->read());button(transport,"下一章 ▶",()->moveDirection(1));player.addView(transport);
 LinearLayout options=new LinearLayout(this);button(options,"暂停 / 继续",()->command(ReaderService.PAUSE));button(options,"🎙 声音",()->chooseVoice());button(options,"⏲ 定时",()->showTimer());player.addView(options);
 LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER_VERTICAL);
 Spinner speed=new Spinner(this);speed.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"0.8×","1.0×","1.2×","1.5×"}));
 float rate=getPreferences(0).getFloat("rate",1f);speed.setSelection(rate==.8f?0:rate==1.2f?2:rate==1.5f?3:1);
 speed.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){getPreferences(0).edit().putFloat("rate",new float[]{.8f,1f,1.2f,1.5f}[pos]).apply();}});
 TextView speedLabel=new TextView(this);speedLabel.setText("语速 ");bottom.addView(speedLabel);bottom.addView(speed,new LinearLayout.LayoutParams(dp(100),dp(44)));
 CheckBox cb=new CheckBox(this);cb.setText("自动下一章");cb.setTextSize(13);cb.setChecked(getPreferences(0).getBoolean("auto",true));auto=cb.isChecked();cb.setOnCheckedChangeListener((v,x)->{auto=x;getPreferences(0).edit().putBoolean("auto",x).apply();});bottom.addView(cb);player.addView(bottom);
 info=new TextView(this);info.setText("就绪");info.setTextSize(12);info.setTextColor(Color.DKGRAY);player.addView(info);
 timerStatus=new TextView(this);timerStatus.setText("");timerStatus.setTextSize(12);player.addView(timerStatus);
 root.addView(player);setContentView(root);
 IntentFilter filter=new IntentFilter();filter.addAction("com.example.novelreader.NEXT");filter.addAction("com.example.novelreader.STATUS");filter.addAction("com.example.novelreader.SENTENCE");if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,filter);
 String last=getPreferences(0).getString("url","https://m.hetushu.com/book/9/6612.html");open(last);
 }
 int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 void button(LinearLayout row,String title,Runnable run){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(12);b.setPadding(dp(2),0,dp(2),0);row.addView(b,new LinearLayout.LayoutParams(0,dp(49),1));b.setOnClickListener(v->run.run());}
 void showUrlDialog(){final EditText field=new EditText(this);field.setSingleLine(true);field.setTextSize(14);field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);field.setText(web.getUrl()==null?"https://":web.getUrl());field.selectAll();new AlertDialog.Builder(this).setTitle("打开其他小说网址").setMessage("粘贴新的小说章节链接，不再固定一本书").setView(field).setNegativeButton("取消",null).setNeutralButton("加入书架",(d,w)->saveBook(field.getText().toString(),chapter.getText().toString())).setPositiveButton("打开", (d,w)->open(field.getText().toString())).show();}
 void saveBook(String url,String title){if(!url.startsWith("https://")){info.setText("请先打开 HTTPS 小说网址");return;}try{JSONArray a=new JSONArray(getPreferences(0).getString("books","[]"));for(int i=0;i<a.length();i++)if(url.equals(a.getJSONObject(i).optString("url"))){info.setText("这本书已经在书架");return;}JSONObject b=new JSONObject();b.put("title",title);b.put("url",url);a.put(b);getPreferences(0).edit().putString("books",a.toString()).apply();info.setText("已加入我的书架");}catch(Exception e){info.setText("保存失败");}}
 void showBookshelf(){try{JSONArray a=new JSONArray(getPreferences(0).getString("books","[]"));String[] names=new String[a.length()+1];names[0]="＋ 收藏当前章节";for(int i=0;i<a.length();i++)names[i+1]=a.getJSONObject(i).optString("title","小说")+" · "+(i+1);new AlertDialog.Builder(this).setTitle("我的书架").setItems(names,(d,n)->{if(n==0)saveBook(web.getUrl(),chapter.getText().toString());else try{open(a.getJSONObject(n-1).getString("url"));}catch(Exception e){}}).setPositiveButton("关闭",null).show();}catch(Exception e){info.setText("书架读取失败");}}
 void showTimer(){String[] choices={"取消定时","15分钟后停止","30分钟后停止","45分钟后停止","60分钟后停止","90分钟后停止","自定义分钟数"};new AlertDialog.Builder(this).setTitle("睡眠定时器").setSingleChoiceItems(choices,-1,(d,n)->{d.dismiss();if(n==6){EditText mins=new EditText(this);mins.setInputType(2);mins.setHint("输入分钟数，例如 25");new AlertDialog.Builder(this).setTitle("自定义停止时间").setView(mins).setNegativeButton("取消",null).setPositiveButton("确定",(x,y)->{try{int m=Integer.parseInt(mins.getText().toString());if(m>=1&&m<=600)setTimer(m);else info.setText("请输入 1～600 分钟");}catch(Exception e){info.setText("分钟数不正确");}}).show();}else setTimer(new int[]{0,15,30,45,60,90}[n]);}).show();}
 void setTimer(int minutes){Intent i=new Intent(this,ReaderService.class);i.setAction(ReaderService.TIMER);i.putExtra("minutes",minutes);startService(i);timerStatus.setText(minutes==0?"已取消定时":("⏲ "+minutes+" 分钟后停止朗读"));}
 void chooseVoice(){info.setText("正在读取中文声音…");if(voicePreview!=null){voicePreview.stop();voicePreview.shutdown();}voicePreview=new TextToSpeech(this,status->{if(status!=TextToSpeech.SUCCESS){info.setText("语音引擎初始化失败");return;}runOnUiThread(()->{ArrayList<Voice> vs=new ArrayList<>();try{Set<Voice> all=voicePreview.getVoices();if(all!=null)for(Voice v:all)if("zh".equals(v.getLocale().getLanguage()))vs.add(v);}catch(Exception e){}Collections.sort(vs,(a,b)->a.getName().compareTo(b.getName()));if(vs.isEmpty()){info.setText("请在手机系统设置中安装中文语音");return;}String[] names=new String[vs.size()+1];names[0]="系统默认中文声音";for(int i=0;i<vs.size();i++){Voice v=vs.get(i);names[i+1]=v.getName()+" · "+v.getLocale().toLanguageTag()+(v.isNetworkConnectionRequired()?" (联网)":" (本地)");}new AlertDialog.Builder(this).setTitle("点击选择并试听").setItems(names,(d,n)->{String id=n==0?"":vs.get(n-1).getName();getPreferences(0).edit().putString("voice",id).apply();try{if(n>0)voicePreview.setVoice(vs.get(n-1));else voicePreview.setLanguage(Locale.SIMPLIFIED_CHINESE);voicePreview.speak("你好，这是小说朗读声音试听。",TextToSpeech.QUEUE_FLUSH,null,"voice_test");}catch(Exception e){}info.setText("新声音已保存，下次朗读生效");}).setNegativeButton("取消",null).show();});});}
 void showWeb(){showingReader=false;if(web!=null)web.setVisibility(View.VISIBLE);if(readingScroll!=null)readingScroll.setVisibility(View.GONE);}
 void showReading(){showingReader=true;web.setVisibility(View.GONE);readingScroll.setVisibility(View.VISIBLE);readingText.setText(currentText);readingScroll.scrollTo(0,0);}
 void toggleReading(){if(showingReader)showWeb();else if(currentText.length()>0)showReading();else info.setText("先点击朗读本章生成跟读文本");}
 void highlightSentence(int index){if(index<0||index>=sentences.size()||currentText.length()==0||!showingReader)return;SentenceSplitter.Piece p=sentences.get(index);SpannableString span=new SpannableString(currentText);span.setSpan(new BackgroundColorSpan(Color.rgb(255,234,144)),p.start,p.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);span.setSpan(new ForegroundColorSpan(Color.rgb(40,72,44)),p.start,p.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);readingText.setText(span);readingText.post(()->{android.text.Layout layout=readingText.getLayout();if(layout==null)return;int line=layout.getLineForOffset(p.start);int y=layout.getLineTop(line);int target=Math.max(0,y-readingScroll.getHeight()/3);readingScroll.smoothScrollTo(0,target);});}
 void open(String url){if(url==null||!url.trim().startsWith("https://")){info.setText("请输入 HTTPS 小说网址");return;}web.loadUrl(url.trim());}
 void moveDirection(int direction){
  String explicit=direction>0?next:prev;
  String target=isValidChapterLink(explicit)&&isCorrectDirection(explicit,direction)?explicit:fallbackHetushuUrl(direction);
  if(!isValidChapterLink(target)){info.setText("未找到相邻章节，请打开原网页目录选择");showWeb();return;}
  int oldChapter=parseChapterNumber(chapter.getText().toString());
  expectedChapter=oldChapter>0?oldChapter+direction:-1;
  moving=true;
  command(ReaderService.STOP);
  showWeb();
  info.setText("正在打开"+(direction>0?"下一章":"上一章")+"…");
  web.loadUrl(target);
 }
 boolean isCorrectDirection(String link,int direction){try{
  android.net.Uri a=android.net.Uri.parse(web.getUrl()),b=android.net.Uri.parse(link);
  String[] x=a.getPath().split("/"),y=b.getPath().split("/");
  long oldId=Long.parseLong(x[3].replace(".html","")),newId=Long.parseLong(y[3].replace(".html",""));
  return newId-oldId==direction;
 }catch(Exception e){return false;}}
 String fallbackHetushuUrl(int direction){
  try{
   android.net.Uri u=android.net.Uri.parse(web.getUrl());
   String host=u.getHost();
   if(host==null||!(host.equals("hetushu.com")||host.endsWith(".hetushu.com")))return "";
   java.util.regex.Matcher m=java.util.regex.Pattern.compile("^/book/(\\d+)/(\\d+)\\.html$").matcher(u.getPath());
   if(!m.matches())return "";
   long id=Long.parseLong(m.group(2))+direction;
   if(id<=0)return "";
   return new android.net.Uri.Builder().scheme("https").authority(host).path("/book/"+m.group(1)+"/"+id+".html").build().toString();
  }catch(Exception e){return "";}
 }
 int parseChapterNumber(String name){
  try{
   java.util.regex.Matcher m=java.util.regex.Pattern.compile("第\\s*(\\d+)\\s*章").matcher(name);
   return m.find()?Integer.parseInt(m.group(1)):-1;
  }catch(Exception e){return -1;}
 }
 boolean isValidChapterLink(String link){
  try{
   if(link==null||link.isEmpty())return false;
   android.net.Uri base=android.net.Uri.parse(web.getUrl()),target=android.net.Uri.parse(link);
   if(!"https".equals(target.getScheme())||base.getHost()==null||!base.getHost().equals(target.getHost())||base.getPath().equals(target.getPath()))return false;
   java.util.regex.Matcher x=java.util.regex.Pattern.compile("^/book/(\\d+)/(\\d+)\\.html$").matcher(base.getPath());
   java.util.regex.Matcher y=java.util.regex.Pattern.compile("^/book/(\\d+)/(\\d+)\\.html$").matcher(target.getPath());
   return x.matches()&&y.matches()&&x.group(1).equals(y.group(1))&&Math.abs(Long.parseLong(x.group(2))-Long.parseLong(y.group(2)))==1;
  }catch(Exception e){return false;}
 }
 void command(String action){Intent i=new Intent(this,ReaderService.class);i.setAction(action);startService(i);}
 void read(){web.evaluateJavascript(JS,raw->{try{String decoded=new JSONArray("["+raw+"]").getString(0);JSONObject data=new JSONObject(decoded);String text=data.optString("text","").trim();String actualTitle=data.optString("title","");int actualNum=parseChapterNumber(actualTitle);if(expectedChapter>0 && actualNum!=expectedChapter){int wanted=expectedChapter;expectedChapter=-1;showWeb();info.setText("章节检查未通过：期望第"+wanted+"章，实际"+(actualNum>0?"第"+actualNum+"章":"未识别章节标题")+"。已停止自动连播");return;}expectedChapter=-1;chapter.setText(actualTitle);if(text.length()<80){info.setText("未识别到正文，网站可能不支持");return;}next=data.optString("next");prev=data.optString("prev");currentText=text;sentences=SentenceSplitter.split(text);showReading();Intent i=new Intent(this,ReaderService.class);i.setAction(ReaderService.PLAY);i.putExtra("text",text);i.putExtra("title",data.optString("title","小说听书"));i.putExtra("rate",getPreferences(0).getFloat("rate",1f));i.putExtra("voice",getPreferences(0).getString("voice",""));startForegroundService(i);info.setText("开始朗读 "+text.length()+" 字");}catch(Exception e){info.setText("章节识别失败");}});}
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
 @Override public void onDestroy(){try{unregisterReceiver(receiver);}catch(Exception e){}if(voicePreview!=null){voicePreview.stop();voicePreview.shutdown();}web.destroy();super.onDestroy();}
}
