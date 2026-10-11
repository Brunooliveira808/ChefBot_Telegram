# 🎯 TASK 04: Problemas ao Atualizar Receitas — Persistência JPA e Integridade de Dados

## 📌 Contexto e Problema
O usuário relatou: *"Problemas ao Atualizar receitas."*

Analisando a camada de persistência em `RecipeService.updateRecipeFromDTO`:
```java
        // Atualizar Ingredientes
        if (dto.ingredients() != null && !dto.ingredients().isEmpty()) {
            recipe.getIngredients().clear();
            for (IngredientDTO ingDTO : dto.ingredients()) {
                Ingredient ingredient = new Ingredient();
                ingredient.setName(ingDTO.name());
                ingredient.setQuantity(ingDTO.quantity());
                ingredient.setUnit(ingDTO.unit());
                ingredient.setNotes(ingDTO.notes());
                recipe.addIngredient(ingredient);
            }
        }
```

E no modelo `Recipe.java`:
```java
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @org.hibernate.annotations.Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
    private List<Ingredient> ingredients = new ArrayList<>();
```

Existem sérios problemas aqui:
1. **Perda de Dados Acidental:**
   Se o usuário solicita uma atualização parcial via IA (ex: *"Mude o tempo de preparo para 40 minutos"* ou *"Mude o autor para Vovó"*), e a resposta da IA contiver uma lista vazia ou nula de ingredientes (ou omitir passos), ou se a IA alucinar, o método pode:
   - Ou ignorar a atualização (`!dto.ingredients().isEmpty()`),
   - Ou se a IA retornar apenas 1 ingrediente alterado em vez da lista completa, o código faz `recipe.getIngredients().clear()` e **APAGA TODOS OS OUTROS INGREDIENTES DA RECEITA ANTIGA**!
2. **Conflito de Estado em Hibernate/JPA:**
   Fazer `clear()` em uma coleção com `orphanRemoval = true` e reinserir novas instâncias de entidades na mesma sessão/transação sem chamar `recipeRepository.flush()` pode causar exceções do Hibernate (como `ConstraintViolationException` ou duplicações temporárias em cache de primeiro nível).
3. **Impossibilidade de Limpar Campos Opcionais:**
   Todos os campos simples são protegidos com `if (dto.campo() != null)`. Se o usuário pedir expressamente para remover o autor (`"Remova o autor da receita"`), o DTO terá autor nulo ou vazio e a entidade nunca atualizará o campo para nulo.

---

## 🎯 Objetivos da Task
1. Tornar a atualização de coleções em `RecipeService` atômica, segura e livre de perda acidental de ingredientes e passos.
2. Garantir que coleções de ingredientes e instruções só sejam substituídas se o DTO for validado e contiver uma lista consistente.
3. Chamar `recipeRepository.flush()` para sincronizar com o banco de dados antes do encerramento da transação.
4. Suportar remoção intencional de campos (tratando strings vazias ou marcadores de remoção).
5. Criar testes unitários em `RecipeServiceUpdatePersistenceTest.java` para garantir que atualizações parciais nunca apaguem dados existentes.

---

## 📂 Arquivos Envolvidos
- `ChefBot_Telegram/src/main/java/ChefBot_Telegram/service/RecipeService.java`
- `ChefBot_Telegram/src/test/java/ChefBot_Telegram/service/RecipeServiceUpdatePersistenceTest.java` (criar)

---

## 🔨 Instruções Passo a Passo para a LLM

### Passo 1: Refatorar `updateRecipeFromDTO` em `RecipeService.java`
Implemente salvaguardas rigorosas contra exclusão acidental:
```java
@Transactional
public Recipe updateRecipeFromDTO(Long telegramChatId, Long recipeId, RecipeResponseDTO dto) {
    Recipe recipe = recipeRepository.findByUserTelegramChatIdAndId(telegramChatId, recipeId)
        .orElseThrow(() -> new IllegalArgumentException("Receita não encontrada para este usuário."));

    // 1. Atualização de Campos Básicos com suporte a alteração consciente
    if (dto.title() != null && !dto.title().isBlank()) {
        recipe.setTitle(dto.title().trim());
    }
    if (dto.category() != null) {
        recipe.setCategory(dto.category().isBlank() ? null : dto.category().trim());
    }
    if (dto.sourceAuthor() != null) {
        recipe.setSourceAuthor(dto.sourceAuthor().isBlank() ? null : dto.sourceAuthor().trim());
    }
    if (dto.prepTimeMinutes() != null) {
        recipe.setPrepTimeMinutes(dto.prepTimeMinutes() > 0 ? dto.prepTimeMinutes() : null);
    }
    if (dto.portions() != null) {
        recipe.setPortions(dto.portions() > 0 ? dto.portions() : null);
    }

    // 2. Atualização Segura de Ingredientes
    // Salvaguarda: só substitui se a lista recebida for válida e não estiver vazia
    if (dto.ingredients() != null && !dto.ingredients().isEmpty()) {
        recipe.getIngredients().clear();
        for (IngredientDTO ingDTO : dto.ingredients()) {
            if (ingDTO.name() != null && !ingDTO.name().isBlank()) {
                Ingredient ingredient = new Ingredient();
                ingredient.setName(ingDTO.name().trim());
                ingredient.setQuantity(ingDTO.quantity());
                ingredient.setUnit(ingDTO.unit());
                ingredient.setNotes(ingDTO.notes());
                recipe.addIngredient(ingredient);
            }
        }
    }

    // 3. Atualização Segura de Instruções
    if (dto.instructions() != null && !dto.instructions().isEmpty()) {
        recipe.getInstructions().clear();
        int step = 1;
        for (InstructionDTO instDTO : dto.instructions()) {
            if (instDTO.description() != null && !instDTO.description().isBlank()) {
                Instruction instruction = new Instruction();
                instruction.setStepNumber(instDTO.stepNumber() != null ? instDTO.stepNumber() : step);
                instruction.setInstructionText(instDTO.description().trim());
                recipe.addInstruction(instruction);
                step++;
            }
        }
    }

    Recipe updated = recipeRepository.saveAndFlush(recipe);
    log.info("Receita ID: {} atualizada com sucesso no banco de dados.", updated.getId());
    return updated;
}
```

### Passo 2: Criar Testes em `RecipeServiceUpdatePersistenceTest.java`
Crie testes com banco H2 em memória:
1. **Teste de Atualização de Título:** Alterar apenas o título e garantir que os ingredientes e passos da receita original permaneçam 100% intactos.
2. **Teste de Atualização de Ingredientes:** Alterar um ingrediente e verificar que a contagem e os nomes dos ingredientes são atualizados corretamente.
3. **Teste de Proteção contra DTO Vazio:** Enviar um DTO com `ingredients = null` ou `ingredients = []` e garantir que os ingredientes existentes NÃO sejam apagados do banco.
4. **Teste de Atualização de Tempo/Porções:** Alterar tempo e rendimento sem alterar texto.

---

## 🧪 Como Testar e Ter Certeza que Funciona
Execute:
```bash
./gradlew test --tests "ChefBot_Telegram.service.RecipeServiceUpdatePersistenceTest"
```

### Critério de Sucesso:
- Todos os testes de atualização e integridade de entidades passam com `BUILD SUCCESSFUL`.
- Nenhuma receita perde seus ingredientes ou passos durante edições parciais.
