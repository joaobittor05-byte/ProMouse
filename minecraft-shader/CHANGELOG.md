# Changelog

## v0.2.1 — Heavy Realism

- Correção feita sobre a shader existente; o projeto não foi recriado.
- Água recebeu ondas procedurais reforçadas e assets animados próprios de color/normal/MER.
- EXTREME usa até 30 octaves de ondas e material de água de roughness muito baixa para maximizar SSR/IBL do Vibrant Visuals.
- `blocky_shadows` foi substituído por `soft_shadows` no caminho oficial do Bedrock.
- Biblioteca PBR pesada adicionada por níveis: HIGH 128px, ULTRA 256px, EXTREME 512px, com 1024px para materiais prioritários.
- O pacote final ultrapassa 100 MB usando apenas assets visuais úteis, sem padding artificial.
- Todos os perfis permanecem desbloqueados com `memory_tier: 1`.

## v0.2.0 — Water Reflection + Soft Shadows Correction

- Ondas de água corrigidas com parâmetros documentados do Vibrant Visuals.
- Normal maps animados e material de água próprio.
- Resposta de reflexão fortalecida via roughness/PBR para o SSR/IBL controlado pelo RenderDragon.
- Sombras movidas para `soft_shadows`.
- Mantida a escala segura de iluminação do Bedrock.

## v0.1.1 — All Profiles Unlocked

- LOW, MEDIUM, HIGH, ULTRA and EXTREME/CINEMATIC use `memory_tier: 1`.
- Hardware-memory gating removed for testing.
- Same safe Bedrock lighting scale retained.
- Unlocking a profile does not guarantee acceptable performance on weak devices.

## v0.1.0 — Core Preview

- reconstrução completa a partir de uma base limpa
- 5 perfis adaptativos
- iluminação em escala oficial (~100 máximo para o sol)
- atmosfera por grupo de bioma
- água animada por perfil e ambiente
- point light colors
- PBR fallback físico sem transformar o mundo em espelho
- ACES nos perfis mais altos
- bindings por bioma
- validator e GitHub Actions
- sem entity overrides / texture-set custom nesta preview
