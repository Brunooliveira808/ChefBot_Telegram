# 🎯 TASK 03: UX e Apresentação de Busca no Telegram

## 📌 Contexto e Problema
O usuário relatou: *"O usuário não sabe exatamente como deve escrever para buscar corretamente."*

Analisando `TelegramBotService.handleSearchCommand` e o fluxo atual:
1. **Inundação do Chat (Spam de mensagens):**
   ```java
   for (Recipe r : recipes) {
       sendTextMessage(chatId, recipeService.formatRecipeForTelegram(r));
   }
   ```
   Se a busca retornar 10 receitas contendo o ingrediente "ovo", o bot dispara **10 mensagens gigantescas** de receitas completas para o usuário, poluindo todo o histórico do Telegram e arriscando bloqueio de taxa do Telegram (HTTP 429 - Too Many Requests).
2. **Falta de Clareza e Sugestões:**
   - Quando o usuário digita `/buscar` sem argumentos, a mensagem de ajuda é estática e pouco explicativa.
   - Quando o usuário digita um termo e nada é encontrado (0 resultados), o bot apenas diz: *"Nenhuma receita encontrada para o termo: X. Use /receitas para ver todas"*. Não sugere palavras parecidas, não mostra receitas recentes e não explica como simplificar o termo.
3. **Mensagens em Linguagem Natural que Deveriam ser Buscas:**
   - Se o usuário manda no chat: *"como faz o bolo de cenoura?"* ou *"tem receita com frango?"*, o bot tenta passar essa pergunta para a IA criar uma receita nova (`geminiService.extractRecipeFromText`), resultando na mensagem confusa: *"Não identifiquei uma receita culinária..."*.

---

## 🎯 Objetivos da Task
1. Reformular o formato de exibição dos resultados da busca no Telegram:
   - **Exatamente 1 resultado encontrado:** Exibir a receita completa com todos os detalhes.
   - **Mais de 1 resultado encontrado (2 ou mais):** Exibir um índice resumido e compacto com título, ID, categoria e tempo, orientando o usuário a visualizar a receita completa digitando `/buscar <ID>` (ou clicando em botão interativo).
2. Quando a busca retornar 0 resultados:
   - Apresentar sugestões práticas: dicas de busca, últimas 3 receitas cadastradas pelo usuário e categorias existentes.
3. Detectar intenção de busca em linguagem natural:
   - Se uma mensagem de texto começar com perguntas como *"como faz..."*, *"tem receita de..."*, *"qual o preparo de..."*, o bot deve direcionar para a busca interna em vez de tentar cadastrar uma nova receita.

---

## 📂 Arquivos Envolvidos
- `ChefBot_Telegram/src/main/java/ChefBot_Telegram/service/TelegramBotService.java`
- `ChefBot_Telegram/src/main/java/ChefBot_Telegram/service/RecipeService.java`
- `ChefBot_Telegram/src/test/java/ChefBot_Telegram/service/TelegramBotServiceSearchUXTest.java` (criar)

---

## 🔨 Instruções Passo a Passo para a LLM

### Passo 1: Criar método de Formatação Resumida em `RecipeService.java`
Adicione o método `formatRecipeSummary(Recipe recipe)`:
```java
public String formatRecipeSummary(Recipe recipe) {
    StringBuilder sb = new StringBuilder();
    sb.append("• `[#").append(recipe.getId()).append("]` *").append(recipe.getTitle()).append("*");
    if (recipe.getCategory() != null && !recipe.getCategory().isBlank()) {
        sb.append(" _(").append(recipe.getCategory()).append(")_");
    }
    if (recipe.getPrepTimeMinutes() != null) {
        sb.append(" ⏱️ ").append(recipe.getPrepTimeMinutes()).append("m");
    }
    return sb.toString();
}
```

### Passo 2: Reformular `handleSearchCommand` em `TelegramBotService.java`
Substitua a lógica de envio em loop:
```java
private void handleSearchCommand(Long chatId, String args) {
    if (args.isBlank()) {
        sendTextMessage(chatId, """
            🔍 *Como buscar no seu caderno:*

            Você pode pesquisar de várias formas:
            • **Por Nome:** `/buscar bolo` ou `/buscar torta de limao`
            • **Por Ingrediente:** `/buscar chocolate` ou `/buscar frango`
            • **Por Categoria:** `/buscar sobremesa` ou `/buscar almoco`
            • **Por Modo de Preparo:** `/buscar airfryer` ou `/buscar forno`
            • **Por ID Direto:** `/buscar 1` (mostra a ficha completa)

            💡 *Dica:* Não se preocupe com acentos ou letras maiúsculas!
            """);
        return;
    }

    List<Recipe> recipes = recipeService.searchRecipes(chatId, args);

    if (recipes.isEmpty()) {
        List<Recipe> recent = recipeService.findRecipesByChatId(chatId);
        StringBuilder sb = new StringBuilder();
        sb.append("🔍 *Nenhuma receita encontrada para:* \"").append(args).append("\"\n\n");
        sb.append("💡 *Dicas para encontrar:*\n");
        sb.append("• Tente usar uma única palavra-chave (ex: 'frango' em vez de 'frango desfiado com molho').\n");
        sb.append("• Use `/receitas` para ver todas as suas receitas cadastradas.\n");

        if (!recent.isEmpty()) {
            sb.append("\n📚 *Suas receitas recentes:*\n");
            int limit = Math.min(3, recent.size());
            for (int i = 0; i < limit; i++) {
                sb.append(recipeService.formatRecipeSummary(recent.get(i))).append("\n");
            }
        }
        sendTextMessage(chatId, sb.toString());
        return;
    }

    // Se houver apenas 1 resultado, exibe a ficha completa
    if (recipes.size() == 1) {
        sendTextMessage(chatId, recipeService.formatRecipeForTelegram(recipes.get(0)));
        return;
    }

    // Se houver múltiplos resultados, exibe uma lista elegante e compacta
    StringBuilder sb = new StringBuilder();
    sb.append("🔍 *Encontrei ").append(recipes.size()).append(" receitas para \"").append(args).append("\":*\n\n");

    for (Recipe r : recipes) {
        sb.append(recipeService.formatRecipeSummary(r)).append("\n");
    }

    sb.append("\n👉 *Para abrir a receita completa, digite:* `/buscar <ID>` (ex: `/buscar ").append(recipes.get(0).getId()).append("`)");
    sendTextMessage(chatId, sb.toString());
}
```

### Passo 3: Interceptar perguntas no `handleTextMessage`
No início de `handleTextMessage`, antes de enviar para o Gemini para cadastro de receita, verificar se o usuário fez uma pergunta ou tentativa de busca informal:
```java
String lowerText = text.toLowerCase().trim();
if (lowerText.startsWith("como faz ") || lowerText.startsWith("tem receita de ") || 
    lowerText.startsWith("qual a receita de ") || lowerText.startsWith("procurar ") ||
    lowerText.startsWith("busca ")) {
    
    // Extrai o termo da pergunta e delega para handleSearchCommand
    String searchTerm = text.replaceAll("(?i)^(como faz|tem receita de|qual a receita de|procurar|busca)\\s*", "").trim();
    if (!searchTerm.isBlank()) {
        handleSearchCommand(chatId, searchTerm);
        return;
    }
}
```

### Passo 4: Criar Testes em `TelegramBotServiceSearchUXTest.java`
Criar testes com mocks para verificar:
1. Resposta quando não há argumentos.
2. Resposta compacta quando há mais de 1 receita.
3. Resposta detalhada quando há 1 receita.
4. Redirecionamento de "como faz bolo de cenoura" para busca.

---

## 🧪 Como Testar e Ter Certeza que Funciona
Execute:
```bash
./gradlew test --tests "ChefBot_Telegram.service.TelegramBotServiceSearchUXTest"
```

### Critério de Sucesso:
- Testes passam com `BUILD SUCCESSFUL`.
- Múltiplos resultados de busca nunca geram mais de 1 mensagem no Telegram.
