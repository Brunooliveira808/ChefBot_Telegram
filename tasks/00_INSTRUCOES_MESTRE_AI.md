# 🧭 Guia Mestre de Execução para Inteligência Artificial (AI Master Guide)

Este documento é a diretriz absoluta para qualquer modelo de linguagem (LLM / IA) ou desenvolvedor responsável por implementar as melhorias e correções no projeto **ChefBot_Telegram**.

---

## 📌 Contexto do Projeto

- **Caminho do Projeto:** `/home/bruno/projetos_Java/ChefBot_Telegram/ChefBot_Telegram`
- **Linguagem:** Java 25 (OpenJDK 25)
- **Framework:** Spring Boot 4.1.1 (Spring 7.x, Spring Data JPA, Spring WebMvc)
- **Banco de Dados:** H2 Database (local/testes) e PostgreSQL
- **Integração Telegram:** `telegrambots-springboot-longpolling-starter:7.11.0`
- **Integração IA:** Google Gemini API (via Spring `RestClient`)
- **Gerenciador de Dependências e Build:** Gradle 8+ (`./gradlew`)

---

## 🛑 Regras de Ouro para a LLM Executora

1. **EXECUTE UMA TASK DE CADA VEZ:**
   - NUNCA tente resolver múltiplas tasks simultaneamente.
   - Siga rigorosamente a ordem numérica: `TASK_00`, `TASK_01`, `TASK_02`, etc.
   - Cada task possui critérios de aceitação e testes obrigatórios.

2. **NUNCA DEIXE OS TESTES QUEBRADOS:**
   - Antes de iniciar qualquer task, rode `./gradlew test`.
   - Após finalizar qualquer alteração de uma task, rode `./gradlew test` (ou `./gradlew test --tests "NomeDoTeste"`).
   - O código só é considerado pronto se o comando `./gradlew test` terminar com `BUILD SUCCESSFUL`.

3. **PRESERVE COMENTÁRIOS E CÓDIGO EXISTENTE:**
   - Não delete classes ou métodos existentes a menos que a task ordene explicitamente.
   - Mantenha a documentação JavaDoc e anotações originais.

4. **CONFIGURAÇÃO E AMBIENTE:**
   - O projeto possui a pasta raiz `/home/bruno/projetos_Java/ChefBot_Telegram` e o subprojeto Gradle em `/home/bruno/projetos_Java/ChefBot_Telegram/ChefBot_Telegram`.
   - Todos os comandos `./gradlew` devem ser executados a partir de `/home/bruno/projetos_Java/ChefBot_Telegram/ChefBot_Telegram`.

---

## 📋 Matriz de Dependência e Sequência de Execução

| Ordem | Arquivo de Task | Objetivo Principal | Dependência |
| :--- | :--- | :--- | :--- |
| **0** | `TASK_00_CONFIGURACAO_E_AMBIENTE_DE_TESTES.md` | Habilitar execução de testes sem credenciais reais | Nenhuma |
| **1** | `TASK_01_CORRECAO_MODELOS_E_CLIENTE_GEMINI.md` | Corrigir nomes de modelos inexistentes da Gemini API e timeouts | TASK 00 |
| **2** | `TASK_02_MOTOR_DE_BUSCA_NORMALIZACAO_E_TOKENIZACAO.md` | Resolver algoritmo de busca de receitas (palavras soltas, stopwords, instruções) | TASK 00 |
| **3** | `TASK_03_UX_E_APRESENTACAO_DE_BUSCA_TELEGRAM.md` | Resolver orientação ao usuário, sugestões quando não achar e limite de mensagens | TASK 02 |
| **4** | `TASK_04_PERSISTENCIA_E_INTEGRIDADE_ATUALIZACAO_JPA.md` | Corrigir problemas de persistência Hibernate/JPA ao atualizar receitas | TASK 00 |
| **5** | `TASK_05_LOGICA_DE_ATUALIZACAO_COM_IA_E_PROMPT.md` | Corrigir prompt de atualização para evitar perdas de ingredientes | TASK 01, TASK 04 |
| **6** | `TASK_06_FLUXO_CONVERSACIONAL_E_ESTADO_DE_EDICAO.md` | Criar máquina de estados para `/atualizar` em 2 passos e busca por nome | TASK 03, TASK 05 |
| **7** | `TASK_07_ROBUSTEZ_TELEGRAM_FORMATACAO_E_MIDIA.md` | Prevenir quebras de parse Markdown/HTML e suportar áudios anexos | TASK 03, TASK 06 |

---

## 🧪 Como Validar a Execução

Cada arquivo de task contém uma seção final chamada **"Como Testar e Ter Certeza que Funciona"**.
A IA executora DEVE:
1. Criar ou atualizar os testes unitários/integrados correspondentes.
2. Executar o comando Gradle indicado na task.
3. Verificar a saída e confirmar que o teste passou (código de saída 0).
4. Marcar o checkbox de conclusão da task.
