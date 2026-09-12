package com.leo.optimazer;
import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.util.List;

public class FrameRepeatActivity extends Activity {
    private String expanded;
    protected void onCreate(Bundle b){super.onCreate(b);expanded=b==null?getIntent().getStringExtra("package"):b.getString("expanded");}
    protected void onResume(){super.onResume();render();}
    protected void onSaveInstanceState(Bundle b){b.putString("expanded",expanded);super.onSaveInstanceState(b);}
    private void render(){
        LinearLayout root=LeoUi.page(this,"Frame Repeat","Cadência sob seu controle.",-1,true);
        List<ProfileStore.Profile> profiles=ProfileStore.all(this);
        if(profiles.isEmpty()){LinearLayout empty=LeoUi.card(this);empty.addView(LeoUi.text(this,"Escolha seu primeiro jogo",18,LeoUi.TEXT,true));empty.addView(LeoUi.gap(this,8));empty.addView(LeoUi.text(this,"Crie um perfil para ajustar modo e VSync.",14,LeoUi.MUTED,false));empty.addView(LeoUi.gap(this,18));empty.addView(LeoUi.button(this,"Adicionar jogo",true,v->LeoUi.navigate(this,1)));root.addView(empty);}
        for(ProfileStore.Profile profile:profiles){String pkg=profile.packageName;LinearLayout card=LeoUi.card(this);
            Switch toggle=new Switch(this);toggle.setText(LeoUi.appName(this,pkg));toggle.setTextSize(17);toggle.setTextColor(LeoUi.TEXT);toggle.setMinHeight(LeoUi.dp(this,52));toggle.setSwitchPadding(LeoUi.dp(this,16));
            toggle.setThumbTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{LeoUi.CYAN,LeoUi.MUTED}));
            toggle.setChecked(FrameRepeatPrefs.isEnabled(this,pkg,true));card.addView(toggle);
            String mode=FrameRepeatPrefs.mode(this,pkg),vsync=FrameRepeatPrefs.vsync(this,pkg);
            card.addView(LeoUi.text(this,profile.enabled?FrameRepeatPrefs.friendlyName(mode)+" · VSync "+friendlyVsync(vsync):"Perfil pausado",12,LeoUi.MUTED,false));card.addView(LeoUi.gap(this,14));
            LinearLayout controls=LeoUi.column(this);controls.setVisibility(pkg.equals(expanded)?View.VISIBLE:View.GONE);
            card.addView(LeoUi.button(this,"Ajustar modo e VSync",false,v->{boolean show=controls.getVisibility()!=View.VISIBLE;controls.setVisibility(show?View.VISIBLE:View.GONE);expanded=show?pkg:null;}));
            controls.addView(LeoUi.gap(this,12));controls.addView(LeoUi.link(this,"frame","Modo",FrameRepeatPrefs.friendlyName(mode),v->choose(pkg,false)));
            controls.addView(LeoUi.gap(this,8));controls.addView(LeoUi.link(this,"settings","VSync",friendlyVsync(vsync),v->choose(pkg,true)));card.addView(controls);root.addView(card);root.addView(LeoUi.gap(this,12));
            toggle.setOnCheckedChangeListener((b,enabled)->{FrameRepeatPrefs.setEnabled(this,pkg,enabled);applyPreference();});
        }
        root.addView(LeoUi.gap(this,8));root.addView(LeoUi.button(this,"Como funciona",false,v->new AlertDialog.Builder(this).setTitle("Frame Repeat")
            .setMessage("Repete quadros existentes; não cria novos quadros de movimento.\n\nCompetitivo: prioriza a resposta.\nQualidade: busca uma cadência regular.\nSuave: busca estabilidade visual.\n\nO VSync controla a política do Leo. Desligado libera o limite mínimo imposto pelo Leo; não desliga o VSync global do Android. O resultado depende do aparelho e do jogo.").setPositiveButton("Entendi",null).show()));
    }
    private String friendlyVsync(String s){return FrameRepeatPrefs.VSYNC_ON.equals(s)?"Ligado":FrameRepeatPrefs.VSYNC_OFF.equals(s)?"Desligado":"Automático";}
    private void choose(String pkg,boolean vsync){
        String[] values=vsync?new String[]{FrameRepeatPrefs.VSYNC_AUTO,FrameRepeatPrefs.VSYNC_ON,FrameRepeatPrefs.VSYNC_OFF}:new String[]{FrameRepeatPrefs.MODE_COMPETITIVE,FrameRepeatPrefs.MODE_QUALITY,FrameRepeatPrefs.MODE_SMOOTH};
        String[] labels=vsync?new String[]{"Automático","Ligado","Desligado"}:new String[]{"Competitivo","Qualidade","Suave"};
        String current=vsync?FrameRepeatPrefs.vsync(this,pkg):FrameRepeatPrefs.mode(this,pkg);int selected=values[1].equals(current)?1:values[2].equals(current)?2:0;
        new AlertDialog.Builder(this).setTitle(vsync?"VSync do Leo":"Modo da partida").setSingleChoiceItems(labels,selected,(d,n)->{
            if(vsync)FrameRepeatPrefs.setVsync(this,pkg,values[n]);else FrameRepeatPrefs.setMode(this,pkg,values[n]);expanded=pkg;d.dismiss();applyPreference();render();
        }).setNegativeButton("Cancelar",null).show();
    }
    private void applyPreference(){
        if(!ShizukuCore.isOperational()){Toast.makeText(this,"Preferência salva. Ative a conexão para aplicar.",0).show();return;}
        try{Intent i=new Intent(this,FrameRepeatService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
        catch(RuntimeException e){Toast.makeText(this,"Preferência salva. Verifique a conexão.",0).show();}
    }
}
