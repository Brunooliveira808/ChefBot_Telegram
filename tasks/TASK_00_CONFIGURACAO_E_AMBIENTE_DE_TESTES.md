# 🎯 TASK 00: Configuração de Ambiente e Suíte de Testes Isolada

## 📌 Contexto e Problema
Atualmente, a execução de `./gradlew test` falha com `PlaceholderResolutionException` porque não existe nenhum arquivo `application.properties` ou `application-test.properties` configurado no repositório.
Além disso, a inicialização padrão do Spring Boot tenta instanciar `TelegramBotService` (que implementa `SpringLongPollingBot`), tentando abrir conexões de long-polling com a API do Telegram durante os testes.

Sem resolver esta task, **nenhum teste automatizado poderá ser executado**, impedindo que qualquer alteração seja validada com segurança.

---

## 🎯 Objetivos da Task
1. Criar um template de configuração em `ChefBot_Telegram/src/main/resources/application.properties.example` para guiar novos desenvolvedores.
2. Criar um arquivo de configuração de teste em `ChefBot_Telegram/src/test/resources/application.properties` com valores falsos/mockados e banco H2 em memória.
3. Garantir que o Spring Boot Test carregue o contexto sem tentar conectar ao Telegram real e sem estourar falta de chaves da Gemini API.
4. Fazer o teste `ApplicationTests.java` passar com sucesso via `./gradlew test`.

---

## 📂 Arquivos Envolvidos
- `ChefBot_Telegram/src/main/resources/application.properties.example` (criar)
- `ChefBot_Telegram/src/test/resources/application.properties` (criar)
- `ChefBot_Telegram/src/test/java/ChefBot_Telegram/ApplicationTests.java` (ajustar anotações ou profile)
- `ChefBot_Telegram/src/main/java/ChefBot_Telegram/service/TelegramBotService.java` (adicionar condicional ou suporte a mock se necessário)

---

## 🔨 Instruções Passo a Passo para a LLM

### Passo 1: Criar `ChefBot_Telegram/src/main/resources/application.properties.example`
Crie o arquivo com o seguinte conteúdo de exemplo:
```properties
spring.application.name=ChefBot_Telegram

# Telegram Bot Credentials
telegram.bot.token=SEU_TELEGRAM_BOT_TOKEN
telegram.bot.username=SEU_TELEGRAM_BOT_USERNAME

# Google Gemini API Key
gemini.api.key=SUA_GEMINI_API_KEY

# Banco de dados local H2
spring.datasource.url=jdbc:h2:file:./data/chefbotdb;AUTO_SERVER=TRUE
spring.datasource.driverClassName=org.h2.Driver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
```

### Passo 2: Criar `ChefBot_Telegram/src/test/resources/application.properties`
Crie o arquivo com propriedades específicas para o ambiente de testes (banco H2 em memória e tokens fictícios):
```properties
spring.application.name=ChefBot_Telegram_Test

# Dummy Credentials para não quebrar injeção de dependência @Value
telegram.bot.token=123456789:TEST_BOT_TOKEN_DUMMY
telegram.bot.username=ChefBotTest
gemini.api.key=test-gemini-api-key

# H2 em memória isolado
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
```

### Passo 3: Ajustar `ApplicationTests.java`
No arquivo `ChefBot_Telegram/src/test/java/ChefBot_Telegram/ApplicationTests.java`, evitar que o `TelegramBotService` inicie um loop de polling real contra a API do Telegram que não existe:
- Caso o starter do Telegram tente inicializar a sessão do bot, utilize `@MockBean` para o `TelegramClient` ou configure a inicialização para ignorar falha de conexão de rede em ambiente de teste.
- Exemplo no teste:
```java
package ChefBot_Telegram;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@SpringBootTest
class ApplicationTests {

    @MockitoBean
    private TelegramClient telegramClient;

    @Test
    void contextLoads() {
        // Valida se todos os componentes do Spring Boot carregam sem exceções
    }
}
```
*(Nota: Spring Boot 3.4+ / 4.x usa `@MockitoBean` ou `@MockBean` do Spring Data JPA / Boot)*.

---

## 🧪 Como Testar e Ter Certeza que Funciona
Execute no terminal a partir de `ChefBot_Telegram/ChefBot_Telegram`:

```bash
./gradlew test --tests "ChefBot_Telegram.ApplicationTests"
```

### Critério de Sucesso:
- Saída: `BUILD SUCCESSFUL`
- 1 test completed, 0 failed.
