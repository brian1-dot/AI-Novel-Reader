package com.example.novelreader;
import java.util.*;
import java.util.regex.*;

public final class SentenceSplitter {
 public static final class Piece {
  public final int start,end;
  public Piece(int start,int end){this.start=start;this.end=end;}
 }
 private SentenceSplitter(){}
 public static ArrayList<Piece> split(String text){
  ArrayList<Piece> result=new ArrayList<>();
  if(text==null)return result;
  Matcher matcher=Pattern.compile("[^。！？；.!?\\n]+[。！？；.!?]*|\\n+").matcher(text);
  while(matcher.find()){
   String candidate=matcher.group();
   if(candidate.trim().isEmpty())continue;
   int from=matcher.start(),to=matcher.end();
   while(from<to&&Character.isWhitespace(text.charAt(from)))from++;
   while(to>from&&Character.isWhitespace(text.charAt(to-1)))to--;
   while(from<to){
    int partEnd=Math.min(from+240,to);
    if(partEnd<to&&Character.isHighSurrogate(text.charAt(partEnd-1)))partEnd--;
    if(partEnd<=from)break;
    result.add(new Piece(from,partEnd));
    from=partEnd;
   }
  }
  return result;
 }
}