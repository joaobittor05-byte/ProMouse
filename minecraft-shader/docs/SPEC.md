# Spec v1.0 — Realistic Universal Shader

A especificação oficial do projeto é o prompt mestre do usuário: realismo integrado para Minecraft Bedrock / Pocket Edition, sem depender de RTX ou hardware de ray tracing.

## Prioridades
1. Realismo
2. Água
3. Reflexos
4. Iluminação solar
5. Raios solares
6. Sombras
7. Materiais
8. Vegetação
9. Iluminação dinâmica
10. Chuva / wetness
11. Atmosfera / biomas
12. Mobs / olhos emissivos
13. Estabilidade
14. Compatibilidade
15. Performance adaptativa

## Compatibilidade alvo
- NVIDIA / AMD Radeon / Intel Arc / Intel iGPU
- Snapdragon / Adreno
- MediaTek / Mali / Immortalis
- Samsung Exynos
- Apple A-Series / M-Series

## Regra central
Preferir sempre um efeito estável, coerente e com fallback a uma técnica mais pesada que gere artefatos ou dependa de um fabricante específico.

## Direção técnica
Usar os recursos oficialmente expostos pelo Bedrock/Vibrant Visuals: lighting, atmospherics, water settings, PBR texture sets, point lights, shadows, color grading e client biome bindings. Técnicas não expostas diretamente pelo resource pack devem ser aproximadas usando os recursos nativos disponíveis, sem inventar chaves de renderer.
