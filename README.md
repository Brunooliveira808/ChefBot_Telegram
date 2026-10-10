# 👨‍🍳 ChefBot Telegram

> **Caderno Inteligente de Receitas no Telegram potencializado por Inteligência Artificial Multimodal (Google Gemini).**

![Status](https://img.shields.io/badge/Status-Em%20Construção%20%2F%20Under%20Development-yellow?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-21%2B%20%2F%2025-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen?style=for-the-badge&logo=springboot)
![Telegram](https://img.shields.io/badge/Telegram%20Bot%20API-7.x-blue?style=for-the-badge&logo=telegram)
![Google Gemini](https://img.shields.io/badge/Google%20Gemini%20AI-Multimodal-purple?style=for-the-badge&logo=googlegemini)

---

## 📌 Sobre o Projeto

O **ChefBot** é um assistente virtual para o Telegram projetado para simplificar a captura, organização e consulta de receitas culinárias. 

Em vez de exigir formulários rígidos, o usuário pode simplesmente **gravar um áudio** explicando como fazer o prato (ou gravar alguém da família ensinando) ou **escrever em linguagem natural e informal**. A IA analisa o conteúdo, extrai os ingredientes, medidas, passos de preparo, tempo e rendimento, salvando tudo de forma estruturada.

---

## 🚀 Funcionalidades Atuais

- 🎙️ **Processamento Multimodal de Áudio:** O usuário envia um áudio/mensagem de voz no Telegram e o modelo Gemini ouve, transcreve e estrutura a receita em um único passo.
- ✍️ **Extração de Texto Livre:** Receba textos informais ou mensagens desorganizadas e converta-os em fichas técnicas padronizadas.
- 🛡️ **Validação Inteligente:** O bot reconhece conversas comuns, saudações ou piadas e só cadastra o que for de fato uma receita culinária.
- 🏷️ **Fidelidade de Título:** O título dado pelo usuário é preservado com exatidão sem alterações arbitrárias pela IA.
- 📚 **Listagem (`/receitas`):** Visualização de todo o caderno de receitas do usuário com seus respectivos IDs.
- 🔍 **Busca Flexível (`/buscar <termo>`):**
  - Busca por ID numérico (`/buscar 1`).
  - Busca normalizada (insensível a acentos e maiúsculas) por título, ingrediente, autor ou categoria.
- ✏️ **Atualização com IA (`/atualizar <ID> <alteração>`):** Edição em linguagem natural (ex: *"/atualizar 1 Troque o açúcar por 1 xícara de adoçante"*).
- 🗑️ **Exclusão de Receitas (`/deletar <ID>`):** Exclusão em cascata de receitas do banco de dados.

---

## 🛠️ Tecnologias Utilizadas

- **Linguagem:** Java 25 / 21
- **Framework:** Spring Boot
- **Cliente HTTP:** Spring `RestClient` moderno
- **Persistência Relacional:** Spring Data JPA / Hibernate
- **Banco de Dados Atual:** H2 Database (arquivo local) / Suporte a PostgreSQL
- **Integração Telegram:** `telegrambots-springboot-longpolling-starter`
- **Inteligência Artificial:** Google Gemini API (modelos `gemini-flash` com fallback de resiliência)
- **Serialização/JSON:** Jackson com módulos de deserialização resiliente

---

## 🗺️ Estado Atual & Próximos Passos (Roadmap)

> [!NOTE]
> O projeto encontra-se atualmente em **fase ativa de construção e prototipagem**. A arquitetura inicial com banco de dados relacional (JPA/H2) serviu como validação do fluxo funcional.

### 🎯 Próximos Passos:

1. 🍃 **Migração para NoSQL Document Database (MongoDB / DocumentDB):**
   - Transição da camada de persistência para um banco orientado a documentos (NoSQL).
   - *Motivo:* Receitas culinárias são entidades altamente variáveis (variações de ingredientes, substituições, passos opcionais, campos dinâmicos). O modelo de documentos NoSQL confere escalabilidade, flexibilidade de esquema e consultas ricas por estruturas aninhadas sem a sobrecarga de tabelas relacionais intermediárias.
2. 📸 **Reconhecimento Visual de Receitas (OCR e Visão):**
   - Suporte ao envio de fotos de cadernos de receitas manuscritos ou fotos de pratos para extração automática via visão computacional do Gemini.
3. 🥗 **Informações Nutricionais e Filtros Dietéticos:**
   - Cálculo automático de calorias estimadas e tags automáticas (ex: *Vegano*, *Sem Glúten*, *Low Carb*, *Rápido*).
4. 📤 **Exportação e Compartilhamento:**
   - Geração de cartões de receita em PDF ou imagens estilizadas para compartilhamento.
5. 🌐 **Dashboard Web Administrativo:**
   - Interface Web (Spring MVC / React) para visualizar e gerenciar o caderno de receitas no navegador.

---

## 💻 Como Executar o Projeto Localmente

### Pré-requisitos
- JDK 21 ou superior
- Gradle 8+
- Token de Bot do Telegram (criado via [@BotFather](https://t.me/BotFather))
- Chave de API do Google AI Studio ([Google Gemini](https://aistudio.google.com/))

### Configuração
Crie ou configure o arquivo `src/main/resources/application.properties`:

```properties
spring.application.name=ChefBot_Telegram

# Telegram Bot
telegram.bot.token=SEU_TELEGRAM_BOT_TOKEN
telegram.bot.username=SEU_TELEGRAM_BOT_USERNAME

# Google Gemini API
gemini.api.key=SUA_GEMINI_API_KEY

# Banco de dados local H2
spring.datasource.url=jdbc:h2:file:./data/chefbotdb;AUTO_SERVER=TRUE
spring.datasource.driverClassName=org.h2.Driver
spring.jpa.hibernate.ddl-auto=update
```

### Execução
Pelo terminal na raiz do projeto:

```bash
./gradlew bootRun
```

Ou execute a classe principal [`Application.java`](file:///home/bruno/workspace/ChefBot_Telegram/src/main/java/ChefBot_Telegram/Application.java) pela sua IDE de preferência (Eclipse, IntelliJ IDEA, VS Code).

---

## 👤 Autor

Desenvolvido por **[Bruno Oliveira](https://github.com/Brunooliveira808)**.