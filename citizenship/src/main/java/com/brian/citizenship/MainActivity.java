package com.brian.citizenship;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.speech.RecognizerIntent;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
 LinearLayout root,body;TextView title,status;ScrollView scroller;
 JSONArray civics,exReading,exWriting,readingVocab,writingVocab;
 int current=0,mode=0,mockIndex=0,mockGood=0,mockBad=0,practiceTry=0;
 ArrayList<Integer> mockIds=new ArrayList<>();String expectedSpeech="";
 TextView questionView,answerView;EditText typed;
 boolean showChinese=true,showAnswer=true;float speed=.8f;
 final int IN_SPEECH=901;
 final int BG=Color.rgb(248,249,248),TEXT=Color.rgb(23,42,45),TEAL=Color.rgb(25,111,95),MUTED=Color.rgb(107,119,124);
 @Override public void onCreate(Bundle b){
  super.onCreate(b);getWindow().setStatusBarColor(TEAL);getWindow().setNavigationBarColor(BG);
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);
  speed=getPreferences(0).getFloat("speed",.8f);current=getPreferences(0).getInt("current",0);
  try{civics=new JSONArray(asset("civics.json"));JSONObject e=new JSONObject(asset("exercises.json"));
    exReading=e.getJSONArray("reading");exWriting=e.getJSONArray("writing");
    readingVocab=e.optJSONArray("reading_vocab");writingVocab=e.optJSONArray("writing_vocab");
    if(civics.length()!=128)throw new IllegalStateException("128-question dataset incomplete");
  }catch(Exception e){new AlertDialog.Builder(this).setTitle("题库未安装完整").setMessage(e.toString()+"\n请使用最新云编译 APK").setPositiveButton("知道了",null).show();return;}
  root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);setContentView(root);
  LinearLayout top=new LinearLayout(this);top.setPadding(dp(16),dp(10),dp(12),dp(10));top.setGravity(Gravity.CENTER_VERTICAL);
  TextView logo=text("🇺🇸 美国入籍学习",20,TEXT,true);top.addView(logo,new LinearLayout.LayoutParams(0,dp(44),1));
  Button home=small("首页",()->home());top.addView(home);root.addView(top);
  View line=new View(this);line.setBackgroundColor(Color.rgb(216,226,222));root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
  scroller=new ScrollView(this);root.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
  body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(18),dp(14),dp(18),dp(40));scroller.addView(body);
  status=text("就绪 · 2025版官方128题",12,MUTED,false);status.setPadding(dp(18),dp(8),dp(18),dp(8));root.addView(status);
  android.content.IntentFilter f=new IntentFilter(StudyService.ACTION);
  if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,f);
  home();
 }
 final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context context,Intent i){String s=i.getStringExtra("text");if(s!=null)status.setText(s);}};
 int dp(int x){return (int)(getResources().getDisplayMetrics().density*x+.5f);}
 String asset(String file)throws Exception{try(InputStream f=getAssets().open(file);ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] a=new byte[8192];int n;while((n=f.read(a))!=-1)b.write(a,0,n);return b.toString("UTF-8");}}
 TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,1);t.setPadding(0,dp(4),0,dp(6));return t;}
 void heading(String s){body.addView(text(s,23,TEXT,true));}
 void subtitle(String s){body.addView(text(s,14,MUTED,false));}
 void para(String s){TextView t=text(s,17,TEXT,false);t.setLineSpacing(dp(6),1.12f);body.addView(t);}
 Button small(String s,Runnable run){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(13);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(9),dp(3),dp(9),dp(3));b.setOnClickListener(v->run.run());return b;}
 void button(String s,Runnable run){Button b=small(s,run);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.setMargins(0,dp(5),0,dp(6));body.addView(b,p);}
 void actions(String a,Runnable ar,String b,Runnable br){LinearLayout row=new LinearLayout(this);Button x=small(a,ar),y=small(b,br);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1);p.setMargins(dp(2),dp(5),dp(2),dp(5));row.addView(x,p);row.addView(y,new LinearLayout.LayoutParams(0,dp(50),1));body.addView(row);}
 void reset(int m){mode=m;body.removeAllViews();scroller.scrollTo(0,0);}
 void talk(String sentence){Intent i=new Intent(this,StudyService.class).setAction("SAY").putExtra("text",sentence).putExtra("rate",speed);startForegroundService(i);}
 void stop(){startService(new Intent(this,StudyService.class).setAction("STOP"));}
 void home(){reset(0);heading("US Citizenship AI");subtitle("2025版 · USCIS 128题 · 简体中文辅助");
  para("今天从第 "+(current+1)+" 题继续 · 已掌握 "+getPreferences(0).getInt("known",0)+" 题");
  button("▶ 继续学习",()->civics(current));
  button("📚 128道公民题｜英文问答·中文解释",()->civics(current));
  button("🎧 循环听题｜每题2遍·每组10轮",()->listenMenu());
  button("🎤 英语跟读｜发音识别练习",()->readMode());
  button("🎓 模拟面试｜公民知识20题",()->startMock());
  button("✏ 英语阅读、拼写与听写",()->writingMenu());
  button("📈 学习统计与进度",()->progress());
  button("⚙ 语速与官员答案提醒",()->settings());
  subtitle("非 USCIS 官方 APP。阅读、写作练习句为自编训练题，不是公开的真实考试原句。");
 }
 JSONObject item(int i){try{return civics.getJSONObject(Math.floorMod(i,civics.length()));}catch(Exception e){return new JSONObject();}}
 void civics(int n){current=Math.floorMod(n,128);getPreferences(0).edit().putInt("current",current).apply();reset(1);
  JSONObject o=item(current);
  heading("第 "+(current+1)+" / 128 题");
  subtitle("2025 USCIS 原题 · "+(o.optBoolean("dynamic")?"官员/地区答案需核实":"官方认可答案"));
  questionView=text(o.optString("question"),24,TEXT,true);body.addView(questionView);
  if(showChinese)subtitle(o.optString("question_zh","中文资料准备中"));
  button("🔊 听英文问题（可重复）",()->talk(o.optString("question")));
  if(showAnswer){para("推荐完整答案：");answerView=text(o.optString("answer"),22,TEAL,true);body.addView(answerView);
   if(showChinese)subtitle(o.optString("answer_zh",""));
  }else subtitle("答案已隐藏，请先尝试自己回答");
  if(o.optBoolean("needs_update"))subtitle("⚠ 现任官员可能发生变化，面试前必须去 USCIS 官网确认。");
  actions(showAnswer?"隐藏答案":"显示答案",()->{showAnswer=!showAnswer;civics(current);},"🎤 练习回答",()->recognize(o.optString("answer")));
  actions("上一题",()->civics(current-1),"下一题",()->civics(current+1));
  actions("✅ 我会了",()->{int count=getPreferences(0).getInt("known",0);getPreferences(0).edit().putInt("known",Math.min(128,count+1)).apply();civics(current+1);},"再练一次",()->{talk(o.optString("question")+". "+o.optString("answer"));});
  actions("全部官方备选答案",()->new AlertDialog.Builder(this).setTitle("第"+(current+1)+"题·官方全部答案").setMessage(allAnswers(o)).setPositiveButton("关闭",null).show(),"中文开/关",()->{showChinese=!showChinese;civics(current);});
 }
 String allAnswers(JSONObject o){JSONArray a=o.optJSONArray("all_accepted_answers");if(a==null)return "";StringBuilder b=new StringBuilder();for(int i=0;i<a.length();i++)b.append(i+1).append(". ").append(a.optString(i)).append("\n");return b.toString();}
 void listenMenu(){reset(2);heading("循环听题");para("默认每道题：问题2遍、答案2遍；每组10轮。");
  subtitle("英文自然稍慢，题间停顿。后台播放可通过通知栏暂停、继续或停止。");
  Spinner amount=new Spinner(this);amount.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"5题","10题","20题"}));body.addView(amount);
  Spinner loops=new Spinner(this);loops.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"1轮","3轮","5轮","10轮"}));loops.setSelection(3);body.addView(loops);
  button("▶ 从第 "+(current+1)+" 题开始循环",()->{int[] nums={5,10,20},cycles={1,3,5,10};
    Intent i=new Intent(this,StudyService.class).setAction("LOOP").putExtra("start",current).putExtra("count",nums[amount.getSelectedItemPosition()]).putExtra("loops",cycles[loops.getSelectedItemPosition()]).putExtra("rate",speed).putExtra("pause",2800);
    startForegroundService(i);status.setText("循环朗读已经开始");});
  actions("⏸ 暂停/继续",()->startService(new Intent(this,StudyService.class).setAction("PAUSE")),"■ 停止",()->stop());
  button("⏲ 设定睡眠定时",()->timer());
 }
 void timer(){String[] opts={"取消","15分钟","30分钟","45分钟","60分钟","90分钟"};new AlertDialog.Builder(this).setTitle("定时停止").setItems(opts,(d,which)->{int[] m={0,15,30,45,60,90};startService(new Intent(this,StudyService.class).setAction("TIMER").putExtra("minutes",m[which]));}).show();}
 void recognize(String expected){expectedSpeech=expected;
  try{
   Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
   i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
   i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US");
   i.putExtra(RecognizerIntent.EXTRA_PROMPT,"请用英语朗读或回答");
   startActivityForResult(i,IN_SPEECH);
  }catch(Exception e){new AlertDialog.Builder(this).setTitle("语音识别暂不可用").setMessage("需要手机安装并启用 Google 语音识别服务；部分语言识别需要联网。").setPositiveButton("确定",null).show();}
 }
 @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);
  if(req==IN_SPEECH&&res==RESULT_OK&&data!=null){
   ArrayList<String> list=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
   if(list!=null&&!list.isEmpty()){
    String said=list.get(0),ref=expectedSpeech;
    new AlertDialog.Builder(this).setTitle("跟读反馈（仅辅助）").setMessage("识别到：\n"+said+"\n\n参考答案：\n"+ref+"\n\n系统语音识别不等于 USCIS 考官评分。")
    .setPositiveButton("继续",null).setNeutralButton("听参考发音",(d,w)->talk(ref)).show();
   }
  }
 }
 void readMode(){reset(3);heading("英语阅读考试练习");subtitle("USCIS 考试形式：最多朗读3句话，正确读出1句即通过。此处是非官方练习句。");
  int idx=getPreferences(0).getInt("reading_idx",0)%Math.max(1,exReading.length());
  String sentence=exReading.optString(idx);
  para("请用英语读出：");body.addView(text(sentence,24,TEXT,true));
  button("🎤 点击开始朗读并识别",()->recognize(sentence));
  actions("🔊 听标准发音",()->talk(sentence),"下一句",()->{getPreferences(0).edit().putInt("reading_idx",idx+1).apply();readMode();});
  subtitle("练习模式显示文本；正式模拟不应显示中文释义。语音识别结果只作辅助。");
 }
 void writingMenu(){reset(4);heading("读写与拼写");subtitle("使用 USCIS 词汇制作的训练内容，不冒充真实考试原句。");
  button("📖 阅读练习 · 开口朗读",()->readMode());
  button("⌨ 句子听写 · 听后输入",()->writeMode(false));
  button("🔡 单词拼写 · 听单词",()->writeMode(true));
  button("📋 官方阅读词汇",()->showVocab(readingVocab,"阅读词汇"));
  button("📋 官方写作词汇",()->showVocab(writingVocab,"写作词汇"));
 }
 void showVocab(JSONArray words,String label){reset(5);heading(label);if(words==null||words.length()==0){para("词汇源暂未成功提取，请下载最新版");return;}
  para("共 "+words.length()+" 个练习词条。");for(int i=0;i<Math.min(150,words.length());i++){String word=words.optString(i);button(word+"   🔊",()->talk(word));}
 }
 void writeMode(boolean spell){reset(6);
  JSONArray list=spell?writingVocab:exWriting;
  if(list==null||list.length()==0){para("单词列表未准备好");return;}
  int idx=getPreferences(0).getInt(spell?"spell_idx":"write_idx",0)%list.length();String value=list.optString(idx);
  heading(spell?"英文单词拼写":"英文句子听写");
  subtitle(spell?"听单词后拼写":"USCIS真实写作考试是听考官说句子后书写；这里使用自编练习句");
  button("🔊 点击播放英语（可重复）",()->talk(value));
  typed=new EditText(this);typed.setSingleLine(false);typed.setMinLines(2);typed.setTextSize(20);typed.setHint("请输入听到的英文");body.addView(typed);
  button("检查我的拼写",()->{String actual=typed.getText().toString().trim(),reference=value.trim();boolean exact=normalize(actual).equals(normalize(reference));
    new AlertDialog.Builder(this).setTitle(exact?"✅ 拼写匹配":"继续练习")
     .setMessage("标准英文：\n"+reference+"\n\n你的输入：\n"+actual+"\n\n练习模式按文本匹配；真实USCIS写作评分不完全等同逐字匹配。").setPositiveButton("知道了",null).show();});
  button("下一题",()->{getPreferences(0).edit().putInt(spell?"spell_idx":"write_idx",idx+1).apply();writeMode(spell);});
 }
 String normalize(String s){return s.toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]","").replaceAll("\\s+"," ").trim();}
 void startMock(){reset(7);mockIds.clear();ArrayList<Integer> pool=new ArrayList<>();for(int i=0;i<128;i++)pool.add(i);Collections.shuffle(pool);for(int i=0;i<20;i++)mockIds.add(pool.get(i));mockIndex=0;mockGood=0;mockBad=0;mockCard();}
 void mockCard(){reset(7);if(mockGood>=12||mockBad>=9||mockIndex>=20){
   heading("模拟面试成绩");para("答对："+mockGood+" / 答错："+mockBad+" / 已问："+mockIndex+"题");
   para(mockGood>=12?"公民知识模拟通过。":"本轮未达到12题正确。继续复习错题。");
   button("再模拟一次",()->startMock());return;
  }
  JSONObject q=item(mockIds.get(mockIndex));heading("模拟考官 · "+(mockIndex+1)+" / 20");
  subtitle("已答对"+mockGood+"题 · 已答错"+mockBad+"题 · 目标12题正确");
  button("🔊 考官英文提问",()->talk(q.optString("question")));
  body.addView(text(q.optString("question"),23,TEXT,true));
  button("🎤 用英语回答（语音识别）",()->recognize(q.optString("answer")));
  actions("显示参考答案",()->new AlertDialog.Builder(this).setTitle("参考完整答案").setMessage(q.optString("answer")).setPositiveButton("确定",null).show(),"跳过并算错",()->score(false));
  actions("✅ 回答正确",()->score(true),"❌ 回答错误",()->score(false));
  subtitle("本版本由学习者对照官方答案自行判定；不自动把语音识别当作考官评分。");
 }
 void score(boolean right){mockIndex++;if(right)mockGood++;else mockBad++;mockCard();}
 void settings(){reset(8);heading("学习设置");
  subtitle("英语语速");Spinner sp=new Spinner(this);String[] options={"0.7×","0.8×","0.9×","1.0×","1.2×"};float[] values={.7f,.8f,.9f,1f,1.2f};
  sp.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));sp.setSelection(speed==.7f?0:speed==.8f?1:speed==.9f?2:speed==1.2f?4:3);body.addView(sp);
  button("保存语速",()->{speed=values[sp.getSelectedItemPosition()];getPreferences(0).edit().putFloat("speed",speed).apply();status.setText("已保存 "+speed+"×");});
  button("打开 USCIS 官员更新官网",()->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.uscis.gov/citizenship/testupdates"))));
  subtitle("佛罗里达州适用信息：第23题参议员、第29题国会选区、第61题州长、第62题州首府。动态官员应在面试当天核实。");
  button("官方2025题库 PDF",()->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.uscis.gov/sites/default/files/document/questions-and-answers/2025-Civics-Test-128-Questions-and-Answers.pdf"))));
 }
 void progress(){reset(9);heading("学习进度");para("目前上次学习位置：第 "+(current+1)+" 题");
  para("标记已掌握次数："+getPreferences(0).getInt("known",0)+"（初版按次数记录，后续升级为逐题准确追踪）");
  button("继续学习",()->civics(current));
 }
 @Override public void onBackPressed(){if(mode!=0)home();else super.onBackPressed();}
 @Override public void onDestroy(){try{unregisterReceiver(receiver);}catch(Exception ignored){}super.onDestroy();}
}
