package com.leo.optimazer;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.Gravity;
import android.widget.*;
import java.util.Locale;

public class DashboardActivity extends Activity {
    private int tab;private TextView memory,total,status,caption;private LeoUi.MemoryDial dial;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){update();handler.postDelayed(this,3000);}};
    @Override protected void onCreate(Bundle b){super.onCreate(b);tab=b==null?getIntent().getIntExtra("tab",0):b.getInt("tab",0);render();}
    @Override protected void onSaveInstanceState(Bundle b){b.putInt("tab",tab);super.onSaveInstanceState(b);}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);tab=i.getIntExtra("tab",0);render();}
    @Override protected void onResume(){super.onResume();ShizukuCore.bindUserService();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    private void open(Class<?> c){startActivity(new Intent(this,c));}
    private void render(){
        memory=null;
        if(tab==2){settings();return;}
        LinearLayout body=LeoUi.page(this,null,null,0,false),header=LeoUi.row(this);
        LeoUi.Glyph logo=new LeoUi.Glyph(this,"logo",LeoUi.TEXT);logo.setBackground(LeoUi.bg(this,LeoUi.CARD,LeoUi.LINE,15));
        header.addView(logo,new LinearLayout.LayoutParams(LeoUi.dp(this,48),LeoUi.dp(this,48)));
        LinearLayout brand=LeoUi.column(this);brand.setPadding(LeoUi.dp(this,12),0,0,0);
        TextView name=LeoUi.text(this,"LEO",19,LeoUi.TEXT,true);name.setLetterSpacing(.17f);brand.addView(name);
        TextView sub=LeoUi.text(this,"OPTIMAZER",10,LeoUi.MUTED,true);sub.setLetterSpacing(.17f);brand.addView(sub);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));header.addView(LeoUi.text(this,"v"+LeoUi.version(this),12,LeoUi.MUTED,false));
        body.addView(header);body.addView(LeoUi.gap(this,28));body.addView(LeoUi.text(this,"Seu próximo nível.",30,LeoUi.TEXT,true));
        body.addView(LeoUi.gap(this,5));body.addView(LeoUi.text(this,"Controle para a próxima partida.",14,LeoUi.MUTED,false));body.addView(LeoUi.gap(this,22));
        LinearLayout hero=LeoUi.card(this);status=LeoUi.text(this,"",12,LeoUi.MUTED,true);status.setGravity(Gravity.CENTER);status.setMinHeight(LeoUi.dp(this,48));status.setFocusable(true);status.setOnClickListener(v->open(ActivationActivity.class));hero.addView(status);
        FrameLayout meter=new FrameLayout(this);dial=new LeoUi.MemoryDial(this);meter.addView(dial,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout values=LeoUi.column(this);values.setGravity(Gravity.CENTER);values.addView(LeoUi.text(this,"MEMÓRIA LIVRE",10,LeoUi.MUTED,true));
        memory=LeoUi.text(this,"—",36,LeoUi.TEXT,true);total=LeoUi.text(this,"",12,LeoUi.MUTED,false);values.addView(memory);values.addView(total);meter.addView(values,new FrameLayout.LayoutParams(-1,-1));hero.addView(meter,new LinearLayout.LayoutParams(-1,LeoUi.dp(this,218)));
        hero.addView(LeoUi.button(this,"Abrir meus jogos  →",true,v->LeoUi.navigate(this,1)));caption=LeoUi.text(this,"",12,LeoUi.MUTED,false);caption.setGravity(Gravity.CENTER);caption.setPadding(0,LeoUi.dp(this,12),0,0);hero.addView(caption);body.addView(hero);
        body.addView(LeoUi.section(this,"CONTROLE DA PARTIDA"));
        body.addView(LeoUi.link(this,"memory","Memória RAM","Limpeza e agendamento",v->startActivity(new Intent(this,MainActivity.class).putExtra("screen","ram"))));body.addView(LeoUi.gap(this,10));
        body.addView(LeoUi.link(this,"frame","Frame Repeat","Modo e VSync por jogo",v->open(FrameRepeatActivity.class)));update();
    }
    private void update(){if(memory==null)return;
        ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(info);
        memory.setText(String.format(Locale.forLanguageTag("pt-BR"),"%.1f GB",info.availMem/1073741824.0));total.setText(String.format(Locale.forLanguageTag("pt-BR"),"de %.1f GB",info.totalMem/1073741824.0));dial.setFraction(info.totalMem==0?0:info.availMem/(float)info.totalMem);
        boolean ready=ShizukuCore.isOperational();status.setText(ready?"●  Conectado":"○  Ativação necessária");status.setTextColor(ready?LeoUi.GREEN:LeoUi.WARN);
        int n=ProfileStore.all(this).size();caption.setText(n==0?"Adicione seu primeiro jogo":n+(n==1?" perfil salvo":" perfis salvos"));}
    private void settings(){LinearLayout root=LeoUi.page(this,"Ajustes","Do seu jeito.",2,false);
        root.addView(LeoUi.link(this,"shield","Conexão",ShizukuCore.isOperational()?"Conectado ao Shizuku":"Ativar com Shizuku",v->open(ActivationActivity.class)));root.addView(LeoUi.gap(this,12));
        root.addView(LeoUi.link(this,"settings","Diagnóstico","Detalhes e registros",v->open(DiagnosticsActivity.class)));root.addView(LeoUi.section(this,"SEU DISPOSITIVO"));
        LinearLayout device=LeoUi.card(this);device.addView(LeoUi.text(this,Build.MODEL,19,LeoUi.TEXT,true));device.addView(LeoUi.gap(this,5));device.addView(LeoUi.text(this,"Android "+Build.VERSION.RELEASE,13,LeoUi.MUTED,false));root.addView(device);
        root.addView(LeoUi.section(this,"LEO OPTIMAZER"));LinearLayout about=LeoUi.card(this);about.addView(LeoUi.text(this,"Versão "+LeoUi.version(this),17,LeoUi.CYAN,true));about.addView(LeoUi.gap(this,8));about.addView(LeoUi.text(this,"Seus jogos. Seu controle.",14,LeoUi.MUTED,false));root.addView(about);}
}
