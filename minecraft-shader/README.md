# Realistic Universal Shader — Bedrock

Projeto novo, reconstruído do zero para Minecraft Bedrock / Pocket Edition usando o pipeline oficial Vibrant Visuals/PBR.

## Objetivo
Buscar **realismo integrado** sem depender de RTX, DXR, CUDA, OptiX ou hardware dedicado de ray tracing. O pack usa os controles que o Bedrock realmente expõe para resource packs: iluminação solar/lunar, atmosfera Rayleigh/Mie, água animada e caustics, PBR fallback, point lights, sombras, color grading/tonemapping e bindings por bioma.

## Perfis — todos desbloqueados na v0.1.1
- LOW — Universal Mobile [UNLOCKED]
- MEDIUM — Balanced [UNLOCKED]
- HIGH — Realistic [UNLOCKED]
- ULTRA — Advanced [UNLOCKED]
- EXTREME — Cinematic [UNLOCKED]

Todos usam `memory_tier: 1` para permitir teste manual. Isso remove a trava de seleção, mas não garante desempenho adequado em hardware fraco.

## Compatibilidade
O projeto não usa código específico de NVIDIA, AMD, Intel, Adreno, Mali/Immortalis ou Apple GPU. A compatibilidade final depende de o próprio Minecraft/dispositivo oferecer suporte ao pipeline Vibrant Visuals.

## Estratégia
A base v0.1.x prioriza estabilidade: sem overrides de mobs e sem texture sets customizados nesta fase. Os próximos módulos entram gradualmente: PBR por material, água/reflexos, vegetação e emissivos, sempre com validação antes do build.
