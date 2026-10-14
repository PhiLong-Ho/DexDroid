package dev.dexdroid;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/** Bounded, in-memory event history; never records screen or typed content. */
final class DiagnosticLog {
 private final ArrayDeque<String> events=new ArrayDeque<>();
 synchronized void add(String event){
  events.addLast(new SimpleDateFormat("HH:mm:ss.SSS",Locale.US).format(new Date())+"  "+event);
  while(events.size()>250)events.removeFirst();
 }
 synchronized String snapshot(){return events.isEmpty()?"No events recorded.":String.join("\n",events);}
 synchronized void clear(){events.clear();}
}
