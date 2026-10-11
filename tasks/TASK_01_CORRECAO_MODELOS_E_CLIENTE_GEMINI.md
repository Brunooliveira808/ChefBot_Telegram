# 🎯 TASK 01: Correção dos Modelos Gemini e Resiliência do RestClient

## 📌 Contexto e Problema
No arquivo `GeminiService.java` (linhas 34-38), a lista de modelos configurada é:
```java
    private static final List<String> MODELS = List.of(
        "gemini-3.1-flash-lite",
        "gemini-3.5-flash-lite",
        "gemini-3.5-flash"
    );
```
**Estes modelos não existem na API pública da Google!**
Ao tentar fazer chamadas HTTP na URL `https://generativelanguage.googleapis.com/v1beta/models/...:generateContent`, a API do Google retorna erro **HTTP 404 Not Found** para os 3 modelos em sequência. Como consequência:
- Nenhuma receita pode ser salva por texto.
- Nenhuma receita pode ser salva por áudio.
- Nenhuma receita pode ser atualizada com o comando `/atualizar`.
- Todas as tentativas caem no log: `"Todos os modelos Gemini falharam"`.

Além disso, em `RestClientConfig.java`, o `RestClient` é construído sem nenhum timeout configurado (`RestClient.builder().build()`). Se a API do Google ou a rede oscilarem, a requisição fica travada indefinidamente.

---

## 🎯 Objetivos da Task
1. Substituir os nomes inválidos por modelos Gemini estáveis e suportados pela API (`gemini-2.5-flash`, `gemini-2.0-flash`, `gemini-1.5-flash`).
2. Permitir que a lista de modelos ou modelo principal seja configurável via `application.properties` com fallback seguro.
3. Configurar timeouts explícitos de conexão (10s) e leitura (60s) no `RestClientConfig`.
4. Criar teste unitário para `GeminiService` utilizando mock do `RestClient` para garantir que a montagem de payload, fallback e parsing de resposta JSON funcionem corretamente.

---

## 📂 Arquivos Envolvidos
- `ChefBot_Telegram/src/main/java/ChefBot_Telegram/service/GeminiService.java`
- `ChefBot_Telegram/src/main/java/ChefBot_Telegram/config/RestClientConfig.java`
- `ChefBot_Telegram/src/test/java/ChefBot_Telegram/service/GeminiServiceTest.java` (criar)

---

## 🔨 Instruções Passo a Passo para a LLM

### Passo 1: Atualizar a lista de modelos em `GeminiService.java`
Substitua os modelos fictícios por modelos oficiais da família Gemini:
```java
    // Modelos oficiais do Google AI Studio com fallback de alta disponibilidade
    private static final List<String> MODELS = List.of(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash"
    );
```
Adicionalmente, certifique-se de que caso o usuário defina uma propriedade no `application.properties` (ex: `gemini.model.primary`), ela tenha preferência.

### Passo 2: Configurar Timeouts no `RestClientConfig.java`
Configurar `ClientHttpRequestFactory` com timeout apropriado para não travar a aplicação:
```java
package ChefBot_Telegram.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        return mapper;
    }
}
```

### Passo 3: Criar Teste Unitário `GeminiServiceTest.java`
Crie um teste que verifique:
1. O parsing do JSON retornado pela API Gemini quando a resposta vem envolta em blocos markdown (` ```json ... ``` `) e quando vem como JSON puro.
2. A conversão para `RecipeResponseDTO` com campos de título, ingredientes e instruções.
3. O comportamento quando `isRecipe` é retornado como `false`.

---

## 🧪 Como Testar e Ter Certeza que Funciona
Execute:
```bash
./gradlew test --tests "ChefBot_Telegram.service.GeminiServiceTest"
```

### Critério de Sucesso:
- Testes de extração e parsing passam com `BUILD SUCCESSFUL`.
- Nenhum modelo fantasma (`3.1`, `3.5`) permanece no código.
