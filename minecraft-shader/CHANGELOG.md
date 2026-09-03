# Changelog

## v0.3.1 — Mirror Wave & Material Fix
- Água com balanço/ondas oficiais significativamente mais fortes.
- EXTREME: `depth 3.0`, 30 octaves e `mix 0.76`.
- Água EXTREME regenerada com ondas cruzadas e normal maps animados de maior resolução.
- Roughness da água reduzida para aproximadamente 1–3 para maximizar reflexos do engine.
- Tint/opacity da água por bioma recalibrados.
- Overrides de textura da tocha removidos; `local_lighting` mantida e corrigida.
- Materiais minerais refeitos com normal suave, metallic 0 e roughness física alta.
- `soft_shadows` preservadas em todos os perfis.

## v0.3.0 — Integrated Realism
- Correção global aplicada sobre a v0.2.1; sem reiniciar o projeto.
- Biblioteca PBR reparada para remover pedras e materiais com variação RGB artificial.
- HIGH/ULTRA/EXTREME regenerados com materiais físicos coerentes.
- Água reconstruída com animação multi-direcional, micro-ondulações e ondas oficiais reforçadas.
- EXTREME usa profundidade de ondas 2.85, 30 octaves e direção incremental 137.5°.
- Roughness/opacidade da água ajustadas para melhorar a resposta SSR/IBL do RenderDragon.
- Tocha corrigida visualmente e iluminação local migrada para `local_lighting`.
- `soft_shadows` ativo em todos os perfis.
- Todos os perfis permanecem desbloqueados.

## v0.2.1 — Heavy Realism
- Correção feita sobre a shader existente; o projeto não foi recriado.
- Água recebeu ondas procedurais reforçadas e assets animados próprios de color/normal/MER.
- EXTREME usa até 30 octaves de ondas e material de água de roughness muito baixa para maximizar SSR/IBL do Vibrant Visuals.
- `blocky_shadows` foi substituído por `soft_shadows` no caminho oficial do Bedrock.
- Biblioteca PBR pesada adicionada por níveis.
- Todos os perfis permanecem desbloqueados com `memory_tier: 1`.
