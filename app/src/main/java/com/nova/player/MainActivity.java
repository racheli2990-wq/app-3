package com.nova.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int BG=Color.rgb(10,13,22), PANEL=Color.rgb(22,27,41), TEXT=Color.rgb(241,244,252), MUTED=Color.rgb(145,154,177), ACCENT=Color.rgb(116,94,255), CYAN=Color.rgb(62,213,202);
    private ExoPlayer player;
    private PlayerView playerView;
    private LinearLayout root, library;
    private TextView nowPlaying, modelStatus, empty;
    private Uri currentUri, subtitleUri, modelUri;
    private final ArrayList<Uri> media = new ArrayList<>();
    private final ArrayList<String> names = new ArrayList<>();
    private SharedPreferences prefs;
    private final ActivityResultLauncher<String[]> pickMedia = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> { if(uri!=null){ getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); addMedia(uri); play(uri); }});
    private final ActivityResultLauncher<String[]> pickSubtitle = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> { if(uri!=null){ getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); subtitleUri=uri; attachSubtitle(); toast("הכתוביות צורפו"); }});
    private final ActivityResultLauncher<String[]> pickModel = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> { if(uri!=null){ getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); modelUri=uri; prefs.edit().putString("model",uri.toString()).apply(); updateModel(); toast("קובץ המודל נשמר במכשיר"); }});

    @Override public void onCreate(Bundle b){ super.onCreate(b); prefs=getSharedPreferences("nova",MODE_PRIVATE); String m=prefs.getString("model",null); if(m!=null) modelUri=Uri.parse(m); getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG); buildUi(); }
    @Override protected void onStart(){ super.onStart(); if(player==null){ player=new ExoPlayer.Builder(this).build(); if(playerView!=null) playerView.setPlayer(player); } }
    @Override protected void onStop(){ super.onStop(); if(player!=null){ player.release(); player=null; } }
    private void buildUi(){
        root=new LinearLayout(this); root.setOrientation(1); root.setBackgroundColor(BG); root.setPadding(dp(18),dp(12),dp(18),dp(10)); root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); setContentView(root);
        LinearLayout header=row(); TextView logo=label("N",22,TEXT,true); logo.setGravity(Gravity.CENTER); logo.setBackground(round(ACCENT,18)); header.addView(logo,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout brand=col(); brand.setPadding(dp(11),0,0,0); brand.addView(label("NOVA PLAYER",19,TEXT,true)); brand.addView(label("המדיה שלך. בלי גבולות. בלי רשת.",11,MUTED,false)); header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView offline=chip("●  אופליין",CYAN); header.addView(offline); root.addView(header); space(root,16);
        TextView title=label("המסך שלך, בדיוק כמו שאת אוהבת",20,TEXT,true); root.addView(title); root.addView(label("נגן וידאו ומוזיקה חכם עם שליטה מלאה",12,MUTED,false)); space(root,14);
        playerView=new PlayerView(this); playerView.setUseController(true); playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); playerView.setBackground(round(Color.BLACK,20)); LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(220)); root.addView(playerView,pp);
        nowPlaying=label("עדיין לא נבחר קובץ",12,MUTED,false); nowPlaying.setGravity(Gravity.CENTER); nowPlaying.setPadding(0,dp(9),0,dp(9)); root.addView(nowPlaying);
        LinearLayout actions=row(); actions.addView(action("＋  פתיחת מדיה",ACCENT,()->pickMedia.launch(new String[]{"video/*","audio/*"})),new LinearLayout.LayoutParams(0,dp(48),1)); actions.addView(action("CC  כתוביות",PANEL,()->pickSubtitle.launch(new String[]{"text/*","application/x-subrip","application/octet-stream"})),weighted()); root.addView(actions); space(root,10);
        LinearLayout second=row(); second.addView(action("⏪  10 שנ׳",PANEL,()->seek(-10000)),weighted()); second.addView(action("⏩  10 שנ׳",PANEL,()->seek(10000)),weighted()); second.addView(action("1×  מהירות",PANEL,this::speedDialog),weighted()); root.addView(second); space(root,15);
        LinearLayout section=row(); section.addView(label("הספרייה שלי",16,TEXT,true),new LinearLayout.LayoutParams(0,-2,1)); section.addView(action("＋ הוספה",PANEL,()->pickMedia.launch(new String[]{"video/*","audio/*"}))); root.addView(section); space(root,8);
        ScrollView sc=new ScrollView(this); library=col(); sc.addView(library); root.addView(sc,new LinearLayout.LayoutParams(-1,0,1)); empty=label("הספרייה ריקה כרגע. פתחי קובץ מהמכשיר כדי להתחיל.",12,MUTED,false); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(8),dp(24),dp(8),dp(24)); library.addView(empty);
        LinearLayout footer=row(); footer.addView(action("⌂  נגן",PANEL,()->{}),weighted()); footer.addView(action("✦  מודל מקומי",PANEL,this::settings),weighted()); root.addView(footer); updateModel();
    }
    private void addMedia(Uri u){ if(!media.contains(u)){media.add(u); names.add(displayName(u)); refreshLibrary();}}
    private void refreshLibrary(){ library.removeAllViews(); if(media.isEmpty()){library.addView(empty);return;} for(int i=0;i<media.size();i++){ final Uri u=media.get(i); LinearLayout item=row(); TextView icon=label(isVideo(u)?"▶":"♫",20,ACCENT,true); icon.setGravity(Gravity.CENTER); item.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(44))); TextView name=label(names.get(i),13,TEXT,true); name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END); item.addView(name,new LinearLayout.LayoutParams(0,dp(44),1)); TextView playBtn=action("הפעלה",ACCENT,()->play(u)); item.addView(playBtn); item.setPadding(dp(9),dp(4),dp(9),dp(4)); item.setBackground(round(PANEL,14)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(7);library.addView(item,p);}}
    private void play(Uri uri){ currentUri=uri; if(player==null) player=new ExoPlayer.Builder(this).build(); playerView.setPlayer(player); MediaItem item=MediaItem.fromUri(uri); player.setMediaItem(item); player.prepare(); player.play(); nowPlaying.setText("▶  "+displayName(uri)); }
    private void attachSubtitle(){ if(currentUri==null){toast("בחרי קודם קובץ מדיה");return;} if(player==null)return; MediaItem.SubtitleConfiguration sub=new MediaItem.SubtitleConfiguration.Builder(subtitleUri).setMimeType(subtitleUri.toString().toLowerCase().endsWith(".vtt")?MimeTypes.TEXT_VTT:MimeTypes.APPLICATION_SUBRIP).setLanguage("und").setSelectionFlags(1).build(); player.setMediaItem(new MediaItem.Builder().setUri(currentUri).setSubtitleConfigurations(java.util.Collections.singletonList(sub)).build()); player.prepare(); player.play(); }
    private void seek(long ms){if(player!=null)player.seekTo(Math.max(0,player.getCurrentPosition()+ms));}
    private void speedDialog(){ if(player==null)return; String[] opts={"0.5×","0.75×","1×","1.25×","1.5×","2×"}; float[] speeds={.5f,.75f,1f,1.25f,1.5f,2f}; new AlertDialog.Builder(this).setTitle("מהירות ניגון").setItems(opts,(d,w)->player.setPlaybackSpeed(speeds[w])).show(); }
    private void settings(){ LinearLayout box=col(); box.setPadding(dp(18),dp(8),dp(18),dp(8)); box.addView(label("מודל תמלול מקומי",17,TEXT,true)); box.addView(label("ייבאי קובץ מודל מהמכשיר. בחירת קובץ שומרת אליו גישה מקומית בלבד.",12,MUTED,false)); modelStatus=label("",12,CYAN,false); box.addView(modelStatus); updateModel(); new AlertDialog.Builder(this).setTitle("הגדרות NOVA").setView(box).setPositiveButton("בחירת מודל", (d,w)->pickModel.launch(new String[]{"application/octet-stream","*/*"})).setNegativeButton("סגירה",null).show(); }
    private void updateModel(){ if(modelStatus!=null) modelStatus.setText(modelUri==null?"לא נבחר מודל עדיין":"מודל נבחר: "+displayName(modelUri)); }
    private boolean isVideo(Uri u){String s=u.toString().toLowerCase();return s.contains("video")||s.endsWith(".mp4")||s.endsWith(".mkv")||s.endsWith(".webm")||s.endsWith(".mov");}
    private String displayName(Uri u){String n=u.getLastPathSegment(); if(n==null)n="קובץ מדיה"; int slash=n.lastIndexOf('/');return slash>=0?n.substring(slash+1):n;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return l;}
    private TextView label(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,1);return t;}
    private TextView chip(String s,int color){TextView t=label(s,11,color,true);t.setPadding(dp(10),dp(7),dp(10),dp(7));t.setBackground(round(Color.rgb(27,49,53),40));return t;}
    private TextView action(String s,int bg,Runnable r){TextView t=label(s,12,TEXT,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(9),0,dp(9),0);t.setBackground(round(bg,14));t.setOnClickListener(v->r.run());return t;}
    private LinearLayout.LayoutParams weighted(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.leftMargin=dp(5);p.rightMargin=dp(5);return p;}
    private void space(LinearLayout l,int h){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private android.graphics.drawable.GradientDrawable round(int c,int rad){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(c);d.setCornerRadius(dp(rad));return d;}
}
