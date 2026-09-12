package com.leo.optimazer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** Native, lightweight design components. */
final class LeoUi {
    static final int BG=0xff080c12,CARD=0xff101923,SURFACE=0xff182430,LINE=0xff233340;
    static final int TEXT=0xffedf6fa,MUTED=0xff9dafbe,CYAN=0xff65e4ef,GREEN=0xff7ee6b0,WARN=0xffffce80;
    static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(1);return l;}
    static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    static TextView text(Context c,String s,int size,int color,boolean bold){
        TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",0));return t;
    }
    static GradientDrawable bg(Context c,int color,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));
        if(stroke!=0)d.setStroke(dp(c,1),stroke);return d;
    }
    static void click(View v,int color,int stroke,int radius){
        Context c=v.getContext();v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3065e4ef),bg(c,color,stroke,radius),bg(c,0xffffffff,0,radius)));
    }
    static View gap(Context c,int h){View v=new View(c);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(c,h)));return v;}
    static LinearLayout card(Context c){LinearLayout l=column(c);l.setPadding(dp(c,18),dp(c,18),dp(c,18),dp(c,18));l.setBackground(bg(c,CARD,LINE,22));return l;}
    static TextView section(Context c,String s){TextView t=text(c,s,12,MUTED,true);t.setLetterSpacing(.1f);t.setPadding(0,dp(c,24),0,dp(c,12));return t;}
    static Button button(Context c,String s,boolean primary,View.OnClickListener action){
        Button b=new Button(c);b.setText(s);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?BG:TEXT);
        b.setTypeface(Typeface.create("sans-serif-medium",0));b.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12));
        b.setMinHeight(dp(c,52));b.setMinimumHeight(dp(c,52));b.setStateListAnimator(null);
        b.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));click(b,primary?CYAN:SURFACE,primary?0:LINE,16);b.setOnClickListener(action);return b;
    }
    static LinearLayout link(Context c,String icon,String title,String sub,View.OnClickListener action){
        LinearLayout l=row(c);l.setPadding(dp(c,16),dp(c,16),dp(c,12),dp(c,16));click(l,CARD,LINE,20);
        Glyph g=new Glyph(c,icon,CYAN);g.setBackground(bg(c,SURFACE,0,12));l.addView(g,new LinearLayout.LayoutParams(dp(c,42),dp(c,42)));
        LinearLayout labels=column(c);labels.setPadding(dp(c,14),0,dp(c,8),0);labels.addView(text(c,title,16,TEXT,true));
        if(sub!=null){TextView t=text(c,sub,12,MUTED,false);t.setPadding(0,dp(c,4),0,0);labels.addView(t);}
        l.addView(labels,new LinearLayout.LayoutParams(0,-2,1));l.addView(text(c,"›",24,MUTED,false));
        l.setFocusable(true);l.setOnClickListener(action);return l;
    }
    static LinearLayout page(Activity a,String title,String sub,int tab,boolean back){
        LinearLayout shell=column(a);shell.setBackgroundColor(BG);
        shell.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(i.left,i.top,i.right,i.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
        });
        a.getWindow().setStatusBarColor(BG);a.getWindow().setNavigationBarColor(BG);
        if(Build.VERSION.SDK_INT>=30)a.getWindow().setDecorFitsSystemWindows(false);
        else a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body=column(a);body.setPadding(dp(a,22),dp(a,16),dp(a,22),dp(a,28));scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(title!=null){LinearLayout heading=row(a);
            if(back){Button b=button(a,"‹",false,v->a.finish());b.setContentDescription("Voltar");heading.addView(b,new LinearLayout.LayoutParams(dp(a,48),dp(a,48)));}
            LinearLayout labels=column(a);if(back)labels.setPadding(dp(a,12),0,0,0);labels.addView(text(a,title,28,TEXT,true));
            if(sub!=null)labels.addView(text(a,sub,13,MUTED,false));heading.addView(labels,new LinearLayout.LayoutParams(0,-2,1));body.addView(heading);body.addView(gap(a,22));}
        if(tab>=0){LinearLayout nav=row(a);nav.setPadding(dp(a,12),dp(a,8),dp(a,12),dp(a,8));nav.setBackground(bg(a,BG,LINE,0));
            String[] names={"Início","Jogos","Ajustes"},icons={"home","game","settings"};
            for(int n=0;n<3;n++){final int i=n;boolean active=n==tab;LinearLayout item=column(a);item.setGravity(Gravity.CENTER);item.setMinimumHeight(dp(a,58));
                click(item,active?CARD:BG,0,16);item.setFocusable(true);item.setSelected(active);item.setContentDescription(names[n]);
                item.addView(new Glyph(a,icons[n],active?CYAN:MUTED),new LinearLayout.LayoutParams(dp(a,26),dp(a,26)));
                item.addView(text(a,names[n],11,active?CYAN:MUTED,active));item.setOnClickListener(v->{if(i!=tab)navigate(a,i);});nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));}
            shell.addView(nav);}
        a.setContentView(shell);shell.requestApplyInsets();return body;
    }
    static void navigate(Activity a,int tab){Intent i=new Intent(a,tab==1?MainActivity.class:DashboardActivity.class);i.putExtra("tab",tab);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);a.startActivity(i);}
    static String appName(Context c,String pkg){try{return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception e){return pkg;}}
    static String version(Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception e){return "1.0.0";}}
    static class Glyph extends View {
        final Paint p=new Paint(3);final String icon;final int color;
        Glyph(Context c,String icon,int color){super(c);this.icon=icon;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float size=Math.min(getWidth(),getHeight());c.save();c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/48,size/48);
            p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);Path path=new Path();
            switch(icon){
                case "home":path.moveTo(10,23);path.lineTo(24,11);path.lineTo(38,23);path.moveTo(14,21);path.lineTo(14,37);path.lineTo(34,37);path.lineTo(34,21);c.drawPath(path,p);c.drawRect(21,28,27,37,p);break;
                case "game":c.drawRoundRect(new RectF(8,15,40,35),7,7,p);c.drawLine(14,25,22,25,p);c.drawLine(18,21,18,29,p);c.drawCircle(30,22,1,p);c.drawCircle(34,28,1,p);break;
                case "settings":for(int k=0;k<3;k++){float y=14+k*10;c.drawLine(10,y,38,y,p);c.drawCircle(k==1?30:18,y,4,p);}break;
                case "memory":c.drawRoundRect(new RectF(14,14,34,34),4,4,p);for(int k=0;k<3;k++){int x=18+k*6;c.drawLine(x,9,x,14,p);c.drawLine(x,34,x,39,p);c.drawLine(9,x,14,x,p);c.drawLine(34,x,39,x,p);}c.drawRect(20,20,28,28,p);break;
                case "frame":c.drawRoundRect(new RectF(9,10,33,29),3,3,p);c.drawLine(16,35,39,35,p);c.drawLine(39,35,39,17,p);break;
                case "shield":path.moveTo(24,8);path.lineTo(37,13);path.lineTo(35,29);path.quadTo(32,36,24,40);path.quadTo(16,36,13,29);path.lineTo(11,13);path.close();c.drawPath(path,p);c.drawLine(18,23,23,28,p);c.drawLine(23,28,31,19,p);break;
                default:p.setStyle(Paint.Style.FILL);path.moveTo(12,9);path.lineTo(20,9);path.lineTo(20,31);path.lineTo(30,31);path.lineTo(25,39);path.lineTo(12,39);path.close();c.drawPath(path,p);path.reset();path.moveTo(30,7);path.lineTo(24,25);path.lineTo(31,25);path.lineTo(27,41);path.lineTo(40,19);path.lineTo(32,19);path.lineTo(37,7);path.close();p.setColor(CYAN);c.drawPath(path,p);
            }c.restore();}
    }
    static class MemoryDial extends View {
        final Paint p=new Paint(3);float fraction;
        MemoryDial(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void setFraction(float n){fraction=Math.max(0,Math.min(1,n));invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float s=Math.min(getWidth(),getHeight());c.save();c.translate((getWidth()-s)/2,(getHeight()-s)/2);c.scale(s/240,s/240);
            p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(1);p.setColor(LINE);c.drawArc(new RectF(8,8,232,232),140,260,false,p);
            for(int k=0;k<=36;k++){double a=Math.toRadians(140+k*260f/36);float r=k%3==0?101:105;c.drawLine(120+(float)Math.cos(a)*r,120+(float)Math.sin(a)*r,120+(float)Math.cos(a)*111,120+(float)Math.sin(a)*111,p);}
            p.setStrokeWidth(7);p.setColor(SURFACE);c.drawArc(new RectF(29,29,211,211),140,260,false,p);p.setColor(CYAN);c.drawArc(new RectF(29,29,211,211),140,260*fraction,false,p);c.restore();}
    }
}
