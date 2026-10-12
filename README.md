# 👨‍🍳 ChefBot Telegram

> **Caderno Inteligente de Receitas no Telegram potencializado por Inteligência Artificial Multimodal (Google Gemini).**

![Status](https://img.shields.io/badge/Status-Funcional%20%26%20Testado-brightgreen?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-21%2B%20%2F%2025-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen?style=for-the-badge&logo=springboot)
![Telegram](https://img.shields.io/badge/Telegram%20Bot%20API-7.x-blue?style=for-the-badge&logo=telegram)
![Google Gemini](https://img.shields.io/badge/Google%20Gemini%20AI-Multimodal-purple?style=for-the-badge&logo=googlegemini)

---

## 📌 Sobre o Projeto

O **ChefBot** é um assistente virtual para o Telegram projetado para simplificar a captura, organização, busca e edição de receitas culinárias. 

Em vez de exigir preenchimento de formulários manuais ou comandos rígidos, o usuário pode simplesmente **gravar uma nota de voz**, enviar um **arquivo de áudio** (`.mp3`, `.ogg`) ou **digitar livremente em linguagem natural**. O bot processa o conteúdo com modelos de IA do Google Gemini, extrai o título com fidelidade, categoriza os ingredientes com suas respectivas medidas, enumera os passos de preparo, estima tempos e porções, e salva tudo estruturado em um banco de dados local.

---

## 🚀 Principais Funcionalidades

- 🎙️ **Processamento Multimodal de Áudio & Voz:** Envie notas de voz gravadas no microfone do Telegram ou arquivos de áudio encaminhados. A IA transcreve, interpreta e estrutura a receita automaticamente.
- ✍️ **Extração de Texto Livre:** Envie mensagens informais ou listas soltas e receba de volta a ficha técnica padronizada.
- 🛡️ **Validação de Contexto Culinário:** O bot distingue conversas casuais e saudações ("Oi", "Bom dia", "Tudo bem?") de receitas reais, evitando cadastros indevidos.
- 🔍 **Motor de Busca Inteligente por Relevância (`/buscar <termo>`):**
  - Busca normalizada (insensível a acentos, maiúsculas e plurais).
  - Remoção automática de stopwords em português (*de, com, em, para, do, da*).
  - Varredura em **títulos**, **ingredientes** e **modo de preparo** (ex: buscar *"airfryer"*, *"chocolate"* ou *"forno"*).
  - Apresentação em lista compacta de mensagem única (sem spam de mensagens) e busca direta por ID (`/buscar 1`).
- ✏️ **Atualização Inteligente de Receitas (`/atualizar`):**
  - **Fluxo em 2 passos (Conversacional):** Envie `/atualizar 1` e o bot entrará em modo de edição, aguardando suas instruções na mensagem seguinte.
  - **Comando direto:** Envie `/atualizar 1 troque o açúcar por mel` ou `/atualizar bolo trocar manteiga por óleo`.
  - **Fast-Path Instantâneo:** Edições pontuais de campos escalares (autor, título, porções, tempo) são aplicadas na hora, sem latência externa.
  - **Garantia de Imutabilidade:** Ingredientes e passos que não foram pedidos para mudar são rigorosamente preservados.
- 💬 **Formatação Segura em HTML:** Mensagens enviadas em HTML padronizado com escape de caracteres especiais (`&`, `<`, `>`) e fallback resiliente em texto puro.
- 📚 **Gestão Completa do Caderno:** Comandos `/receitas` (listar todas), `/deletar <ID>` (remover) e `/cancelar` (abortar edições pendentes).

---

## 🛠️ Tecnologias Utilizadas

- **Linguagem:** Java 25 (compatível com Java 21+)
- **Framework:** Spring Boot 4.x
- **Persistência Relacional:** Spring Data JPA / Hibernate (com `saveAndFlush` e integridade referencial)
- **Banco de Dados:** H2 Database (arquivo local persistido em `./data/chefbotdb.mv.db`)
- **Integração Telegram:** `telegrambots-springboot-longpolling-starter` (Long Polling resiliente)
- **Inteligência Artificial:** Google Gemini Generative Language API (com fallback dinâmico entre `gemini-3.1-flash-lite`, `gemini-flash-lite-latest` e `gemini-3.5-flash`)
- **Cliente HTTP:** Spring `RestClient` com timeouts de rede configurados

---

## 💻 Como Iniciar e Testar o Projeto

### 📋 Pré-requisitos
- **Java JDK 21 ou 25** instalado.
- **Git** instalado.

---

### 1. Clonar o Repositório
```bash
git clone https://github.com/Brunooliveira808/ChefBot_Telegram.git
cd ChefBot_Telegram
```

---

### 2. Configurar as Chaves e Credenciais

O projeto já inclui um arquivo de modelo em `ChefBot_Telegram/src/main/resources/application.properties.example`.

Crie a sua cópia local em `ChefBot_Telegram/src/main/resources/application.properties`:
```bash
cp ChefBot_Telegram/src/main/resources/application.properties.example ChefBot_Telegram/src/main/resources/application.properties
```

Abra o arquivo recém-criado e preencha com suas credenciais:

```properties
spring.application.name=ChefBot_Telegram

# 1. Telegram Bot (Obtenha com o @BotFather no Telegram)
telegram.bot.token=SEU_TOKEN_DO_TELEGRAM_AQUI
telegram.bot.username=NOME_DO_SEU_BOT_AQUI

# 2. Google Gemini API Key (Obtenha em: https://aistudio.google.com/app/apikey)
gemini.api.key=SUA_CHAVE_GEMINI_AQUI
gemini.model.primary=gemini-3.1-flash-lite

# 3. Banco de Dados Local (Criado automaticamente na pasta ./data)
spring.datasource.url=jdbc:h2:file:./data/chefbotdb;AUTO_SERVER=TRUE
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
```

> [!IMPORTANT]
> O arquivo `application.properties` está registrado no `.gitignore`. Suas chaves privadas e tokens **nunca serão enviados ao repositório Git**.

---

### 3. Como Obter as Chaves Necessárias

#### 🤖 Token do Telegram:
1. Abra o Telegram e inicie uma conversa com o usuário oficial **`@BotFather`**.
2. Digite `/newbot` e siga as instruções para escolher o nome e o username do bot (deve terminar com `bot`, ex: `MeuChef_bot`).
3. O BotFather responderá com o seu token de API (formato `1234567890:ABCdef...`).

#### 🧠 API Key do Google Gemini:
1. Acesse o **[Google AI Studio](https://aistudio.google.com/app/apikey)** com a sua conta Google.
2. Clique em **"Create API key"** e selecione ou crie um projeto padrão.
3. Copie a chave gerada (inicia com `AIzaSy...` ou `AQ...`).

---

### 4. Executar os Testes Automatizados

A suíte de testes unitários e de integração utiliza banco de dados em memória e mocks, permitindo validação completa sem depender de chaves reais ou conexão externa:

```bash
cd ChefBot_Telegram
./gradlew test
```

Para reexecutar todos os testes garantindo que não há cache:
```bash
./gradlew test --rerun
```

Todos os testes devem finalizar com **`BUILD SUCCESSFUL`**.

---

### 5. Executar o Bot Localmente

A partir da pasta `ChefBot_Telegram`:
```bash
./gradlew bootRun
```

Assim que a aplicação inicializar:
1. Abra o Telegram e procure pelo seu bot pelo username cadastrado.
2. Envie o comando `/start`.
3. Pronto! O bot responderá com o menu de boas-vindas e estará pronto para receber receitas.

---

## 📱 Guia de Comandos no Telegram

| Comando | Descrição | Exemplo de Uso |
| :--- | :--- | :--- |
| `/start` ou `/ajuda` | Exibe o guia de introdução e lista de comandos. | `/ajuda` |
| `/receitas` | Lista todas as receitas cadastradas do usuário. | `/receitas` |
| `/buscar <termo>` | Pesquisa por nome, ingrediente, método ou ID. | `/buscar bolo`, `/buscar airfryer`, `/buscar 1` |
| `/atualizar <ID>` | Inicia o modo interativo de edição em 2 passos. | `/atualizar 1` |
| `/atualizar <ID> <alteração>` | Aplica uma alteração diretamente na receita. | `/atualizar 1 Mudar autor para Tia Maria` |
| `/deletar <ID>` | Exclui permanentemente uma receita do seu caderno. | `/deletar 1` |
| `/cancelar` | Cancela uma sessão de edição interativa ativa. | `/cancelar` |

---

## Próxima Alteração

1. Adicionar Document Database

---

## 👤 Autor

Desenvolvido por **[Bruno Oliveira](https://github.com/Brunooliveira808)**.
