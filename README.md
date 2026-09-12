# Leo Optimazer 1.0.0

Interface Android nativa baseada na Alpha18 (VSync Control).

- Início: memória disponível real e atalhos.
- Jogos: perfis compactos, ícones, abrir jogo e menu de ajustes.
- Ajustes: conexão, dispositivo, versão e diagnóstico sob demanda.
- RAM: limpeza e agendamento em segundos, minutos ou horas.
- Frame Repeat: modo e VSync por jogo, com detalhes recolhidos.

Mantém os mecanismos de Shizuku, perfis, DPI, resolução, toque e Frame Repeat da Alpha18. Sem novos serviços gráficos ou animações contínuas.

## Compilar

JDK 17, Gradle 8.7, Android SDK 35. Execute `gradle :app:assembleDebug`.
O workflow da branch gera `LeoOptimazer-1.0.0.apk`.

A Alpha18 foi assinada com uma chave de depuração indisponível nesta sessão. Uma nova assinatura exige reinstalação, que remove os dados locais do app. Registre os perfis antes de remover a versão anterior.

A compilação verifica integração e empacotamento. O funcionamento no aparelho com Shizuku precisa ser conferido no dispositivo.
