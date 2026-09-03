# Realistic Universal Shader — v0.3.1 Mirror Wave & Material Fix

Correção direta da v0.3.0, sem recriar o projeto.

## Principais mudanças
- Tocha: removidos os overrides de textura/PBR que estavam quebrando o visual. A textura/modelo volta ao asset estável do Minecraft, enquanto a iluminação local continua customizada.
- Água: EXTREME usa o máximo de `depth=3.0`, 30 octaves e mistura alta de ondas oficiais, além de flipbooks procedurais de alta resolução com ondas cruzadas.
- Reflexos: água em EXTREME usa roughness quase zero para maximizar SSR/IBL do RenderDragon e superfície mais neutra para não mascarar reflexos.
- Pedra: albedo mineral neutralizado, normal maps muito mais suaves e roughness alta para remover aspecto colorido/plástico.
- Sombras: `soft_shadows` mantido em todos os perfis.

## Compatibilidade
O projeto continua usando o pipeline PBR/Vibrant Visuals, sem depender de RTX dedicado, CUDA, OptiX ou GPU específica.

## Limites do renderer
Vibrant Visuals calcula reflexos via SSR + IBL. Um Resource Pack pode maximizar esse caminho com roughness, normals, água e materiais, mas não consegue obrigar o renderer a refletir objetos fora da tela nem implementar planar reflection própria.
