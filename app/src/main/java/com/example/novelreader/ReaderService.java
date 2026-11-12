package com.example.novelreader;
import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.os.*;
import android.speech.tts.*;
import java.util.*;

public class ReaderService extends Service implements TextToSpeech.OnInitListener {
 public static final String PLAY="com.example.novelreader.PLAY",PAUSE="com.example.novelreader.PAUSE",TIMER="com.example.novelreader.TIMER",STOP="com.example.novelreader.STOP";
 static final String CHANNEL="reader_audio";
 TextToSpeech tts; ArrayList<SentenceSplitter.Piece> chunks=new ArrayList<>(); String chapterText=""; int index=0;boolean ready=false,paused=false,playing=false;String title="小说听书";float rate=1f;String selectedVoice="";
 Handler handler=new Handler(Looper.getMainLooper());
 long stopAt=0;final Runnable stopTask=new Runnable(){@Override public void run(){if(stopAt>0&&android.os.SystemClock.elapsedRealtime()>=stopAt){stopAt=0;paused=true;playing=false;if(tts!=null)tts.stop();if(Build.VERSION.SDK_INT>=24)stopForeground(STOP_FOREGROUND_REMOVE);else stopForeground(true);stopSelf();}else if(stopAt>0)handler.postDelayed(this,1000);}};
 @Override public void onCreate(){super.onCreate();NotificationChannel channel=new NotificationChannel(CHANNEL,"听书播放",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(channel);tts=new TextToSpeech(this,this);}
 @Override public int onStartCommand(Intent i,int flags,int startId){if(i==null)return START_NOT_STICKY;
 if(STOP.equals(i.getAction())){playing=false;paused=true;if(tts!=null)tts.stop();update("已停止上一章，切换章节中");return START_NOT_STICKY;}
 if(TIMER.equals(i.getAction())){int minutes=i.getIntExtra("minutes",0);handler.removeCallbacks(stopTask);stopAt=minutes>0?android.os.SystemClock.elapsedRealtime()+minutes*60000L:0;if(stopAt>0)handler.postDelayed(stopTask,1000);return START_NOT_STICKY;}
 if(PLAY.equals(i.getAction())){String txt=i.getStringExtra("text");if(txt==null)return START_NOT_STICKY;title=i.getStringExtra("title");if(title==null)title="小说听书";rate=i.getFloatExtra("rate",1f);selectedVoice=i.getStringExtra("voice");if(selectedVoice==null)selectedVoice="";if(ready)applyVoice();if(tts!=null)tts.stop();chunks.clear();index=0;paused=false;playing=true;
 chapterText=txt;chunks=SentenceSplitter.split(txt);startForeground(101,notification("准备朗读"));if(ready)speak();}
 else if(PAUSE.equals(i.getAction())){if(paused){paused=false;playing=true;speak();}else{paused=true;playing=false;if(tts!=null)tts.stop();update("已暂停");}}
 return START_NOT_STICKY;}
 @Override public void onInit(int result){if(result!=TextToSpeech.SUCCESS){update("语音初始化失败");return;}ready=true;int l=tts.setLanguage(Locale.SIMPLIFIED_CHINESE);if(l==TextToSpeech.LANG_MISSING_DATA||l==TextToSpeech.LANG_NOT_SUPPORTED){update("请先安装中文语音包");return;}
 tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
 applyVoice();
 tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){@Override public void onStart(String s){}@Override public void onDone(String s){handler.post(()->{if(playing&&!paused){index++;speak();}});}@Override public void onError(String s){handler.post(()->{paused=true;playing=false;update("朗读出错");});}});
 if(playing&&!paused)speak();}
 void applyVoice(){if(tts==null||!ready)return;try{tts.setLanguage(Locale.SIMPLIFIED_CHINESE);if(selectedVoice!=null&&!selectedVoice.isEmpty()){for(Voice v:tts.getVoices()){if(v.getName().equals(selectedVoice)){tts.setVoice(v);return;}}}else{for(Voice v:tts.getVoices()){String n=v.getName().toLowerCase(Locale.ROOT);if(v.getLocale().getLanguage().equals("zh")&&(n.contains("male")||n.contains("男"))){tts.setVoice(v);return;}}}}catch(Exception ignored){}}
 void speak(){if(!ready||paused||!playing)return;if(index>=chunks.size()){playing=false;update("本章已结束");sendBroadcast(new Intent("com.example.novelreader.NEXT").setPackage(getPackageName()));return;}tts.setSpeechRate(rate);int ret=tts.speak(chapterText.substring(chunks.get(index).start,chunks.get(index).end),TextToSpeech.QUEUE_FLUSH,null,"segment_"+index);if(ret==TextToSpeech.ERROR){playing=false;update("朗读失败");}else {sendBroadcast(new Intent("com.example.novelreader.SENTENCE").setPackage(getPackageName()).putExtra("index",index));update("朗读中 "+(index+1)+"/"+chunks.size());}}
 Notification notification(String message){PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);PendingIntent pause=PendingIntent.getService(this,1,new Intent(this,ReaderService.class).setAction(PAUSE),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,CHANNEL).setContentTitle(title).setContentText(message).setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(open).addAction(android.R.drawable.ic_media_pause,paused?"继续":"暂停",pause).setOngoing(!paused).build();}
 void update(String message){getSystemService(NotificationManager.class).notify(101,notification(message));sendBroadcast(new Intent("com.example.novelreader.STATUS").setPackage(getPackageName()).putExtra("status",message));}
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public void onDestroy(){handler.removeCallbacks(stopTask);if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
