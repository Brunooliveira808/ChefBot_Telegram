# 🎯 TASK 06: Fluxo Conversacional e Estado de Edição no Telegram

## 📌 Contexto e Problema
O comando `/atualizar` atual exige que o usuário digite o comando completo em uma única linha:
`/atualizar <ID> <instruções>`

No Telegram, os usuários interagem de forma conversacional e cometem dois erros recorrentes:
1. **O usuário digita apenas `/atualizar 1` sem instruções:**
   - O bot responde mostrando a receita encontrada e diz: *"Para atualizá-la, envie: /atualizar 1 <o que deseja alterar>"*.
   - Quando o usuário responde logo em seguida com: *"troque o açúcar por mel"*, o bot não lembra que estava falando da receita 1!
   - Como o bot é stateless (sem estado de conversa), ele trata *"troque o açúcar por mel"* como uma receita nova, passa pelo Gemini e ou falha dizendo que não é receita ou cadastra uma receita bizarra com um único ingrediente!
2. **O usuário não lembra o ID e digita pelo nome da receita:**
   - Exemplo: `/atualizar bolo de cenoura trocar açucar por mel`.
   - O código faz `Long.parseLong(parts[0])`, onde `parts[0]` é `"bolo"`, e responde de forma fria: *"❌ ID inválido. Use um número"*.
   - O usuário se frustra porque é forçado a listar receitas, caçar o ID numérico e redigitar tudo.

---

## 🎯 Objetivos da Task
1. Implementar um gerenciador de sessão conversacional em memória (`UserSessionService`):
   - Se o usuário digitar `/atualizar 1` (sem instruções), o bot armazena que aquele `chatId` está no estado `EDITING_RECIPE` com o `recipeId = 1`.
   - A próxima mensagem de texto daquele usuário é automaticamente processada como a instrução de atualização da receita 1!
   - Fornecer comando `/cancelar` para abortar a edição a qualquer momento.
2. Permitir buscar a receita pelo nome no comando `/atualizar`:
   - Se o primeiro argumento não for um número (ex: `/atualizar bolo ...`), buscar receitas com aquele nome.
   - Se encontrar apenas 1 correspondência, prosseguir com a atualização diretamente.
   - Se encontrar mais de 1, perguntar ao usuário qual delas ele quer editar.
3. Criar testes unitários em `UserSessionServiceTest.java` e `TelegramBotServiceUpdateFlowTest.java`.

---

## 📂 Arquivos Envolvidos
- `ChefBot_Telegram/src/main/java/ChefBot_Telegram/service/UserSessionService.java` (criar)
- `ChefBot_Telegram/src/main/java/ChefBot_Telegram/service/TelegramBotService.java`
- `ChefBot_Telegram/src/test/java/ChefBot_Telegram/service/UserSessionServiceTest.java` (criar)

---

## 🔨 Instruções Passo a Passo para a LLM

### Passo 1: Criar `UserSessionService.java`
Crie uma classe `@Service` leve com `ConcurrentHashMap` para rastrear estados de conversa:
```java
package ChefBot_Telegram.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserSessionService {

    public enum SessionState {
        IDLE,
        AWAITING_RECIPE_UPDATE
    }

    public record UserSession(SessionState state, Long targetRecipeId) {}

    private final Map<Long, UserSession> sessions = new ConcurrentHashMap<>();

    public void startUpdateSession(Long chatId, Long recipeId) {
        sessions.put(chatId, new UserSession(SessionState.AWAITING_RECIPE_UPDATE, recipeId));
    }

    public Optional<UserSession> getSession(Long chatId) {
        return Optional.ofNullable(sessions.get(chatId));
    }

    public void clearSession(Long chatId) {
        sessions.remove(chatId);
    }
}
```

### Passo 2: Integrar `UserSessionService` em `TelegramBotService.java`
1. No método `consume` / `handleTextMessage`:
   - Verificar se o usuário digitou `/cancelar`: se tiver sessão ativa, cancela e avisa: *"Edição cancelada."*.
   - Verificar se o `chatId` possui sessão ativa com estado `AWAITING_RECIPE_UPDATE`:
     Se sim, usar a mensagem recebida como as `updateInstructions` para o `targetRecipeId`, limpar a sessão e executar a atualização!
2. No método `handleUpdateCommand`:
   - Se o usuário digitar `/atualizar 1` (sem instruções):
     - Ativar a sessão: `userSessionService.startUpdateSession(chatId, recipeId);`
     - Enviar mensagem:
       ```
       ✏️ *Modo de edição ativado para:* Bolo de Cenoura (#1)
       
       Envie agora uma mensagem dizendo o que deseja alterar (ex: 'mudar o autor para Minha Mãe' ou 'trocar açúcar por adoçante').
       
       💡 Para desistir, digite `/cancelar`.
       ```
   - Se o primeiro argumento não for número:
     - Usar `recipeService.searchRecipes(chatId, query)` para localizar a receita pretendida.
     - Se encontrar exatamente 1 receita, aplicar a alteração nela.

### Passo 3: Criar Testes Unitários
Criar testes validando:
1. Ativação e limpeza de sessão em `UserSessionServiceTest`.
2. Fluxo em dois passos: `/atualizar 1` seguido de `"mudar autor para Vovó"`.
3. Comando `/cancelar`.

---

## 🧪 Como Testar e Ter Certeza que Funciona
Execute:
```bash
./gradlew test --tests "ChefBot_Telegram.service.UserSessionServiceTest"
```

### Critério de Sucesso:
- Testes passam com `BUILD SUCCESSFUL`.
- Usuário consegue editar receitas em conversa natural de duas etapas sem precisar digitar `/atualizar <ID>` na mesma linha.
