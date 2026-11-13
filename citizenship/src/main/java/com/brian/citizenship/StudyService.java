package com.brian.citizenship;
import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.os.*;
import android.speech.tts.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class StudyService extends Service implements TextToSpeech.OnInitListener {
 static final String CHANNEL="citizenship_speech", ACTION="com.brian.citizenship.SPEAK";
 TextToSpeech tts;boolean ready=false,active=false,paused=false;Handler handler=new Handler(Looper.getMainLooper());
 ArrayList<String> queue=new ArrayList<>();int position=0;float rate=.8f;long stopAt=0;int pauseMs=1600; boolean loopPlayback=false;int loopBegin=0,loopCount=5,loopRounds=10;
 final Runnable nextLine=()->speakNext();
 @Override public void onCreate(){super.onCreate();NotificationChannel c=new NotificationChannel(CHANNEL,"入籍学习语音",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);tts=new TextToSpeech(this,this);}
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(intent==null)return START_NOT_STICKY;
  String action=intent.getAction();
  if("STOP".equals(action)){active=false;paused=false;queue.clear();handler.removeCallbacks(nextLine);if(tts!=null)tts.stop();stopForeground(true);stopSelf();return START_NOT_STICKY;}
  if("PAUSE".equals(action)){if(active){paused=!paused;if(paused){tts.stop();handler.removeCallbacks(nextLine);}else speakNext();notifyStatus(paused?"已暂停":"继续朗读");}return START_STICKY;}
  if("TIMER".equals(action)){int m=intent.getIntExtra("minutes",0);stopAt=m==0?0:android.os.SystemClock.elapsedRealtime()+m*60000L;notifyStatus(m==0?"定时已取消":"已设置 "+m+" 分钟定时");return START_STICKY;}
  if("SAY".equals(action)||"LOOP".equals(action)){
   handler.removeCallbacks(nextLine);if(tts!=null)tts.stop();queue.clear();position=0;active=true;paused=false;loopPlayback="LOOP".equals(action);
   rate=intent.getFloatExtra("rate",.8f);pauseMs=intent.getIntExtra("pause",1600);
   if("SAY".equals(action)){String text=intent.getStringExtra("text");if(text!=null)queue.add(text);}
   else{
    int begin=intent.getIntExtra("start",0),count=intent.getIntExtra("count",5),loops=intent.getIntExtra("loops",10);
    try{
     JSONArray a=new JSONArray(readAsset("civics.json"));
     begin=Math.max(0,Math.min(begin,a.length()-1));
     count=Math.max(1,Math.min(count,a.length()-begin));loops=Math.max(1,Math.min(loops,20));
     loopBegin=begin;loopCount=count;loopRounds=loops;
     for(int cycle=0;cycle<loops;cycle++)for(int x=0;x<count;x++){
      JSONObject q=a.getJSONObject(begin+x);
      queue.add(q.optString("question"));queue.add(q.optString("question"));
      String answer=q.optString("answer");
      queue.add(answer);queue.add(answer);
     }
    }catch(Exception e){notifyStatus("读取题库失败");active=false;return START_NOT_STICKY;}
   }
   startForeground(31,notification("准备播放"));
   if(ready)speakNext();
  }
  return START_STICKY;
 }
 String readAsset(String file)throws IOException{try(InputStream f=getAssets().open(file);ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=f.read(buf))!=-1)b.write(buf,0,n);return b.toString("UTF-8");}}
 @Override public void onInit(int status){
  if(status!=TextToSpeech.SUCCESS){notifyStatus("手机英语朗读引擎不可用");return;}
  ready=true;tts.setLanguage(Locale.US);
  tts.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(AudioAttributes.USAGE_MEDIA).build());
  tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
   @Override public void onStart(String utteranceId){}
   @Override public void onDone(String id){handler.post(()->{if(active&&!paused){position++;handler.postDelayed(nextLine,pauseMs);}});}
   @Override public void onError(String id){handler.post(()->{position++;handler.postDelayed(nextLine,pauseMs);});}
  });
  if(active)speakNext();
 }
 void speakNext(){
  if(!active||paused||!ready)return;
  if(stopAt>0&&android.os.SystemClock.elapsedRealtime()>=stopAt){end("睡眠定时已结束");return;}
  if(position>=queue.size()){end("本组循环朗读已完成");return;}
  String phrase=queue.get(position).replace("\n", ". ");
  notifyStatus("朗读中 "+(position+1)+"/"+queue.size());
  if(loopPlayback){
   int item=(position/4)%loopCount;
   int cycle=(position/4)/loopCount+1;
   int part=position%4;
   int number=loopBegin+item;
   Intent progress=new Intent(ACTION).setPackage(getPackageName());
   progress.putExtra("kind","loop_progress");
   progress.putExtra("questionIndex",number);
   progress.putExtra("cycle",cycle);
   progress.putExtra("cycles",loopRounds);
   progress.putExtra("item",item+1);
   progress.putExtra("items",loopCount);
   progress.putExtra("isAnswer",part>=2);
   progress.putExtra("repeat",part%2+1);
   sendBroadcast(progress);
  }
  tts.setSpeechRate(rate);
  int status=tts.speak(phrase,TextToSpeech.QUEUE_FLUSH,null,"u"+position);
  if(status==TextToSpeech.ERROR){end("语音合成失败");}
 }
 void end(String reason){active=false;queue.clear();notifyStatus(reason);stopForeground(true);stopSelf();}
 Notification notification(String value){
  PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  PendingIntent pause=PendingIntent.getService(this,1,new Intent(this,StudyService.class).setAction("PAUSE"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,StudyService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  return new Notification.Builder(this,CHANNEL).setContentTitle("US Citizenship AI").setContentText(value).setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(pi).addAction(android.R.drawable.ic_media_pause,"暂停/继续",pause).addAction(android.R.drawable.ic_delete,"停止",stop).build();
 }
 void notifyStatus(String s){getSystemService(NotificationManager.class).notify(31,notification(s));sendBroadcast(new Intent(ACTION).setPackage(getPackageName()).putExtra("text",s));}
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public void onDestroy(){handler.removeCallbacks(nextLine);if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
