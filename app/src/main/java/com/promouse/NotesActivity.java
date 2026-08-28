package com.promouse;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class NotesActivity extends Activity {
    private final int bg = Color.rgb(8, 12, 19);
    private final int card = Color.rgb(18, 25, 36);
    private final int border = Color.rgb(42, 54, 72);
    private final int muted = Color.rgb(160, 170, 188);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    private ScrollView buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        root.addView(text("Observações", 27, Color.WHITE, true));
        root.addView(text("Guia de ativação do ProMouse", 13, muted, false), topWrap(4));

        root.addView(section("ADB Wi-Fi",
                "1. O aparelho precisa usar Android 11 ou superior para o fluxo padrão de Depuração sem fio.\n" +
                "2. Ative Opções do desenvolvedor.\n" +
                "3. Abra Depuração sem fio.\n" +
                "4. Escolha Parear dispositivo com código de pareamento.\n" +
                "5. Volte à notificação do ProMouse e conclua o pareamento.\n\n" +
                "Xiaomi / Redmi / POCO / HyperOS:\n" +
                "Configurações → Sobre o telefone/tablet → Informações detalhadas e especificações → toque várias vezes em Versão do OS/MIUI. Depois: Configurações → Configurações adicionais → Opções do desenvolvedor.\n\n" +
                "Em aparelhos onde o caminho tiver outro nome, procure por 'Opções do desenvolvedor' e 'Depuração sem fio' dentro das Configurações."));

        root.addView(section("ROOT",
                "1. O dispositivo precisa possuir root funcional.\n" +
                "2. Abra ☰ → Ativação → ROOT.\n" +
                "3. Magisk, KernelSU ou APatch poderá mostrar a solicitação de superusuário.\n" +
                "4. Conceda o acesso.\n" +
                "5. O ProMouse confirma o root antes de marcar a sessão como ativa."), topWrap(12));

        String code = ActivationStore.bshellCode(this);
        String pc = "adb shell am broadcast -a com.promouse.BSHELL_ACTIVATE --es code " + code + " -n com.promouse/.BShellReceiver";
        String brevent = "am broadcast -a com.promouse.BSHELL_ACTIVATE --es code " + code + " -n com.promouse/.BShellReceiver";
        LinearLayout bShell = section("BShell",
                "Código atual: " + code + "\n\nPC: conecte o aparelho ao ADB e execute o comando abaixo.\n\nBrevent: use o comando sem o prefixo 'adb shell'.\n\nEsta primeira reconstrução usa o código como handshake de ativação. O backend privilegiado de injeção será ligado a essa sessão nas próximas etapas.");
        bShell.addView(command("PC", pc));
        bShell.addView(command("Brevent", brevent), topWrap(8));
        root.addView(bShell, topWrap(12));

        scroll.addView(root);
        return scroll;
    }

    private LinearLayout command(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(round(Color.rgb(11, 16, 24), 12, border));
        box.addView(text(label, 11, muted, true));
        TextView cmd = text(value, 12, Color.WHITE, false);
        cmd.setTextIsSelectable(true);
        box.addView(cmd, topWrap(5));
        Button copy = new Button(this);
        copy.setText("Copiar"); copy.setAllCaps(false); copy.setTextColor(Color.WHITE); copy.setTextSize(12);
        copy.setOnClickListener(v -> copy(value));
        box.addView(copy, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
        return box;
    }

    private LinearLayout section(String title, String body) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(14), dp(15), dp(14));
        box.setBackground(round(card, 15, border));
        box.addView(text(title, 17, Color.WHITE, true));
        box.addView(text(body, 13, muted, false), topWrap(8));
        return box;
    }

    private void copy(String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("ProMouse", value));
        Toast.makeText(this, "Comando copiado", Toast.LENGTH_SHORT).show();
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color); t.setGravity(Gravity.START);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD); return t;
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), stroke); return d;
    }

    private LinearLayout.LayoutParams topWrap(int value) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin = dp(value); return lp;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
