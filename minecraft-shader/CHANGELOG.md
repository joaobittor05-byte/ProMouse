# Changelog

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

## v0.2.0 — Water Reflection + Soft Shadows Correction
- Ondas de água corrigidas com parâmetros documentados do Vibrant Visuals.
- Normal maps animados e material de água próprio.
- Resposta de reflexão fortalecida via roughness/PBR.
- Sombras movidas para `soft_shadows`.

## v0.1.1 — All Profiles Unlocked
- LOW, MEDIUM, HIGH, ULTRA and EXTREME/CINEMATIC use `memory_tier: 1`.

## v0.1.0 — Core Preview
- reconstrução completa a partir de uma base limpa
- 5 perfis adaptativos
- iluminação em escala segura do Bedrock
- atmosfera por grupo de bioma
- água animada por perfil e ambiente
- PBR fallback
- ACES nos perfis mais altos
- bindings por bioma
