# 📦 Pacote de Tasks para Implementação — ChefBot Telegram

Este diretório contém o plano completo, detalhado e modular de correções e melhorias para o **ChefBot Telegram**, estruturado para que qualquer modelo de linguagem (LLM) ou desenvolvedor execute passo a passo com total segurança e testes automatizados.

---

## 🗂️ Índice das Tasks

| Arquivo | Título | Foco Principal |
| :--- | :--- | :--- |
| [00_INSTRUCOES_MESTRE_AI.md](file:///home/bruno/projetos_Java/ChefBot_Telegram/tasks/00_INSTRUCOES_MESTRE_AI.md) | **Guia Mestre de Execução para IA** | Regras, ciclo TDD, diretrizes de segurança |
| [TASK_00_CONFIGURACAO_E_AMBIENTE_DE_TESTES.md](file:///home/bruno/projetos_Java/ChefBot_Telegram/tasks/TASK_00_CONFIGURACAO_E_AMBIENTE_DE_TESTES.md) | **Configuração & Testes Isolados** | Habilitar `./gradlew test` sem falha de `@Value` ou Telegram real |
| [TASK_01_CORRECAO_MODELOS_E_CLIENTE_GEMINI.md](file:///home/bruno/projetos_Java/ChefBot_Telegram/tasks/TASK_01_CORRECAO_MODELOS_E_CLIENTE_GEMINI.md) | **Modelos Gemini & RestClient** | Corrigir modelos inexistentes (404) e configurar timeouts de rede |
| [TASK_02_MOTOR_DE_BUSCA_NORMALIZACAO_E_TOKENIZACAO.md](file:///home/bruno/projetos_Java/ChefBot_Telegram/tasks/TASK_02_MOTOR_DE_BUSCA_NORMALIZACAO_E_TOKENIZACAO.md) | **Motor de Busca de Receitas** | Tokenização, stopwords, múltiplos termos, busca em preparo e ranking |
| [TASK_03_UX_E_APRESENTACAO_DE_BUSCA_TELEGRAM.md](file:///home/bruno/projetos_Java/ChefBot_Telegram/tasks/TASK_03_UX_E_APRESENTACAO_DE_BUSCA_TELEGRAM.md) | **UX & Apresentação de Busca** | Resumo compacto contra spam, sugestões quando não achar e linguagem natural |
| [TASK_04_PERSISTENCIA_E_INTEGRIDADE_ATUALIZACAO_JPA.md](file:///home/bruno/projetos_Java/ChefBot_Telegram/tasks/TASK_04_PERSISTENCIA_E_INTEGRIDADE_ATUALIZACAO_JPA.md) | **Persistência JPA ao Atualizar** | Prevenção de perda acidental de ingredientes e integridade de coleções |
| [TASK_05_LOGICA_DE_ATUALIZACAO_COM_IA_E_PROMPT.md](file:///home/bruno/projetos_Java/ChefBot_Telegram/tasks/TASK_05_LOGICA_DE_ATUALIZACAO_COM_IA_E_PROMPT.md) | **Lógica e Prompt da IA para Edição** | Blindagem contra alucinação de ingredientes e atalhos rápidos sem IA |
| [TASK_06_FLUXO_CONVERSACIONAL_E_ESTADO_DE_EDICAO.md](file:///home/bruno/projetos_Java/ChefBot_Telegram/tasks/TASK_06_FLUXO_CONVERSACIONAL_E_ESTADO_DE_EDICAO.md) | **Fluxo Conversacional e Estado** | Edição em 2 etapas (`/atualizar 1` -> texto), `/cancelar` e busca por nome |
| [TASK_07_ROBUSTEZ_TELEGRAM_FORMATACAO_E_MIDIA.md](file:///home/bruno/projetos_Java/ChefBot_Telegram/tasks/TASK_07_ROBUSTEZ_TELEGRAM_FORMATACAO_E_MIDIA.md) | **Formatação Segura e Áudios** | Migração para HTML seguro e suporte a arquivos de áudio além de notas de voz |

---

## 🚀 Como Executar

Para orientar uma IA a executar uma das tasks, basta passar o prompt:
> *"Por favor, leia o arquivo `/home/bruno/projetos_Java/ChefBot_Telegram/tasks/00_INSTRUCOES_MESTRE_AI.md` e em seguida implemente exclusivamente a task descrita em `/home/bruno/projetos_Java/ChefBot_Telegram/tasks/TASK_XX_....md`. Não avance para outra task até que os testes passem com 100% de sucesso."*
