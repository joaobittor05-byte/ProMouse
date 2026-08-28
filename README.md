# ProMouse — Rebuild v0.1

Nova base do ProMouse. O projeto anterior baseado em AccessibilityService e mapeamento fixo foi removido.

## Direção do projeto

O ProMouse será um mapper configurável de mouse/teclado para touch. Nenhuma tecla ou posição de jogo vem pronta: o usuário adiciona o jogo e constrói o próprio mapa.

### Tela principal
- status compacto: Status / Método / Mapper
- lista grande e rolável de jogos
- botão + para adicionar qualquer app/jogo instalado
- menu ☰ com Ativação e Observações

### Ativação
- ADB Wi-Fi: fluxo de preparação/pairing pela Depuração sem fio
- ROOT: verificação real por `su -c id`
- BShell: handshake por código com comandos separados para PC e Brevent

### Overlay do jogo
Ao abrir um jogo com sessão ativa e permissão de overlay, o ProMouse mostra uma bolha PM arrastável. Ela abre o widget inicial com:
- FPS
- TOQUE
- ANALÓGICO
- Configurações

Nesta v0.1 o overlay e o sistema de ativação são a fundação visual/estrutural. O backend privilegiado de captura HID e injeção touch ADB/Root será conectado em etapas seguintes; não há AccessibilityService nesta base.

## Build
Todo push em `main` gera o artefato `ProMouse-APK` no GitHub Actions.
