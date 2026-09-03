# Realistic Universal Shader v0.3.0 — Integrated Realism

Continuação direta da v0.2.1. O projeto não foi recriado.

## Direção
Buscar realismo integrado no Minecraft Bedrock sem depender de RTX/DXR/hardware de ray tracing dedicado.

## v0.3.0
- Corrige a biblioteca de materiais que estava produzindo pedras com cores RGB artificiais.
- Regenera materiais PBR de HIGH/ULTRA/EXTREME com paletas físicas coerentes.
- Água com animação própria em várias direções + sistema oficial de ondas do Vibrant Visuals para deixar o balanço claramente perceptível.
- Roughness da água baixa e opacidade ajustada para favorecer os reflexos SSR/IBL do RenderDragon.
- Tocha visualmente corrigida e iluminação local migrada para `local_lighting`.
- `soft_shadows` permanece ativo em todos os perfis.
- Luz solar continua limitada à escala segura do Bedrock.

## Perfis
- LOW — Universal Mobile
- MEDIUM — Balanced
- HIGH — Realistic
- ULTRA — Advanced
- EXTREME — Cinematic Integrated Realism

Todos permanecem desbloqueados com `memory_tier: 1`.

## Compatibilidade
O pack não utiliza código específico de NVIDIA, AMD, Intel, Adreno, Mali/Immortalis ou Apple GPU. O dispositivo ainda precisa oferecer suporte ao pipeline Vibrant Visuals do próprio Minecraft.

## Limites do renderer
Reflexos são calculados pelo Bedrock via SSR/IBL. O Resource Pack controla materiais, roughness, metalness, normals, água e iluminação, mas não inventa campos inexistentes para planar reflection, PCSS ou TAA customizado.
