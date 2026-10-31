# Meu Ensaio — app Android

Lê o PDF da partitura (exportado do Finale, MuseScore, Sibelius…), toca o baixo e canta o nome das notas (Dó, Ré, Mi…),
no andamento da partitura ou no que você escolher. Funciona offline; tudo fica no celular.

## Baixar o APK
Página **Releases** deste repositório → última versão → `MeuEnsaio.apk`.

Link direto (sempre a versão mais nova):
`https://github.com/<SEU-USUARIO>/meu-ensaio-app/releases/latest/download/MeuEnsaio.apk`

## Como atualizar
Qualquer alteração enviada para a branch `main` gera um APK novo automaticamente (aba **Actions**).
O app novo instala por cima do antigo e mantém as partituras (a chave de assinatura é fixa: `app/meuensaio.jks`).

## Onde está o quê
- `app/src/main/assets/index.html` — o app inteiro (leitor de PDF, som, voz, tela)
- `app/src/main/java/.../MainActivity.java` — abre a página, escolhe arquivos e recebe PDFs compartilhados
- `.github/workflows/gerar-apk.yml` — compila o APK no GitHub
