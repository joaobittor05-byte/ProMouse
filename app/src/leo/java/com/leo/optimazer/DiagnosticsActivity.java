package com.leo.optimazer;
import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.widget.*;
public class DiagnosticsActivity extends Activity {
    TextView output;
    protected void onCreate(Bundle b){super.onCreate(b);LinearLayout root=LeoUi.page(this,"Diagnóstico","Detalhes para suporte.",-1,true);
        root.addView(LeoUi.button(this,"Atualizar",false,v->refresh()));root.addView(LeoUi.gap(this,10));
        root.addView(LeoUi.button(this,"Copiar diagnóstico",false,v->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Leo Optimazer",output.getText()));Toast.makeText(this,"Diagnóstico copiado",0).show();}));
        root.addView(LeoUi.gap(this,20));LinearLayout card=LeoUi.card(this);output=LeoUi.text(this,"",12,LeoUi.MUTED,false);output.setTextIsSelectable(true);card.addView(output);root.addView(card);refresh();}
    private void refresh(){output.setText("Carregando…");new Thread(()->{
        SharedPreferences p=getSharedPreferences(MonitorService.PREFS,0);
        String report="Leo Optimazer "+LeoUi.version(this)+"\n\n"+DeviceCompat.summary()+"\n\nConexão\n"+ShizukuCore.runtimeDiagnostic()+"\n"+ShizukuCore.getLastBindError()
            +"\n\nRAM\n"+p.getString(MonitorService.KEY_LAST_CLEANUP_RESULT,"Sem registro")+"\n\nPerfil\n"+p.getString(MonitorService.KEY_LAST_PROFILE_RESULT,"Sem registro")
            +"\n\nTouch Engine\n"+p.getString(MonitorService.KEY_LAST_TOUCH_RESULT,"Sem registro")+"\n\nFrame Repeat\n"+p.getString(MonitorService.KEY_LAST_FRAME_RESULT,"Sem registro")
            +"\n\nAjuda\nOs recursos dependem do Android e do jogo. A repetição não cria novos quadros de movimento. O VSync controla a política do Leo; não desativa o VSync global do Android.\n\nDPI e resolução são vinculadas ao perfil. O Touch Engine usa recursos AOSP disponíveis e restaura ajustes ao sair. O arrasto linear depende do resampling da ROM; não duplica o toque físico quando o monitoramento bruto não está disponível.";
        runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())output.setText(report);});},"Leo-Diagnostics").start();}
}
