package ChefBot_Telegram.service;

import ChefBot_Telegram.dto.IngredientDTO;
import ChefBot_Telegram.dto.InstructionDTO;
import ChefBot_Telegram.dto.RecipeResponseDTO;
import ChefBot_Telegram.model.Ingredient;
import ChefBot_Telegram.model.Instruction;
import ChefBot_Telegram.model.Recipe;
import ChefBot_Telegram.model.User;
import ChefBot_Telegram.repository.RecipeRepository;
import ChefBot_Telegram.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class RecipeService {

    private static final Logger log = LoggerFactory.getLogger(RecipeService.class);

    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;

    public RecipeService(UserRepository userRepository, RecipeRepository recipeRepository) {
        this.userRepository = userRepository;
        this.recipeRepository = recipeRepository;
    }

    /**
     * Busca ou cria o usuário pelo telegramChatId e salva a receita com ingredientes e passos.
     */
    @Transactional
    public Recipe saveRecipeFromDTO(Long telegramChatId, String userName, RecipeResponseDTO dto, String rawTranscript) {
        User user = userRepository.findByTelegramChatId(telegramChatId)
            .orElseGet(() -> {
                User newUser = new User();
                newUser.setTelegramChatId(telegramChatId);
                newUser.setName(userName != null ? userName : "Usuário");
                return userRepository.save(newUser);
            });

        Recipe recipe = new Recipe();
        recipe.setUser(user);
        recipe.setTitle(dto.title() != null ? dto.title() : "Receita sem título");
        recipe.setCategory(dto.category());
        recipe.setSourceAuthor(dto.sourceAuthor());
        recipe.setPrepTimeMinutes(dto.prepTimeMinutes());
        recipe.setPortions(dto.portions());
        recipe.setRawTranscript(rawTranscript);

        // Mapear Ingredientes
        if (dto.ingredients() != null) {
            for (IngredientDTO ingDTO : dto.ingredients()) {
                Ingredient ingredient = new Ingredient();
                ingredient.setName(ingDTO.name());
                ingredient.setQuantity(ingDTO.quantity());
                ingredient.setUnit(ingDTO.unit());
                ingredient.setNotes(ingDTO.notes());
                recipe.addIngredient(ingredient);
            }
        }

        // Mapear Instruções de Preparo
        if (dto.instructions() != null) {
            for (InstructionDTO instDTO : dto.instructions()) {
                Instruction instruction = new Instruction();
                instruction.setStepNumber(instDTO.stepNumber());
                instruction.setInstructionText(instDTO.description());
                recipe.addInstruction(instruction);
            }
        }

        Recipe savedRecipe = recipeRepository.save(recipe);
        log.info("Receita '{}' (ID: {}) salva com sucesso para o usuário chatId: {}", 
                 savedRecipe.getTitle(), savedRecipe.getId(), telegramChatId);

        return savedRecipe;
    }

    /**
     * Atualiza uma receita existente a partir do DTO retornado pela IA.
     */
    @Transactional
    public Recipe updateRecipeFromDTO(Long telegramChatId, Long recipeId, RecipeResponseDTO dto) {
        Recipe recipe = recipeRepository.findByUserTelegramChatIdAndId(telegramChatId, recipeId)
            .orElseThrow(() -> new RuntimeException("Receita não encontrada para este usuário."));

        if (dto.title() != null && !dto.title().isBlank()) {
            recipe.setTitle(dto.title());
        }
        if (dto.category() != null) {
            recipe.setCategory(dto.category());
        }
        if (dto.sourceAuthor() != null) {
            recipe.setSourceAuthor(dto.sourceAuthor());
        }
        if (dto.prepTimeMinutes() != null) {
            recipe.setPrepTimeMinutes(dto.prepTimeMinutes());
        }
        if (dto.portions() != null) {
            recipe.setPortions(dto.portions());
        }

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

        // Atualizar Instruções
        if (dto.instructions() != null && !dto.instructions().isEmpty()) {
            recipe.getInstructions().clear();
            for (InstructionDTO instDTO : dto.instructions()) {
                Instruction instruction = new Instruction();
                instruction.setStepNumber(instDTO.stepNumber());
                instruction.setInstructionText(instDTO.description());
                recipe.addInstruction(instruction);
            }
        }

        Recipe updated = recipeRepository.save(recipe);
        log.info("Receita ID: {} atualizada com sucesso.", updated.getId());
        return updated;
    }

    /**
     * Deleta uma receita pertencente ao usuário.
     */
    @Transactional
    public boolean deleteRecipe(Long telegramChatId, Long recipeId) {
        Optional<Recipe> recipeOpt = recipeRepository.findByUserTelegramChatIdAndId(telegramChatId, recipeId);
        if (recipeOpt.isPresent()) {
            recipeRepository.delete(recipeOpt.get());
            log.info("Receita ID: {} deletada com sucesso pelo usuário chatId: {}", recipeId, telegramChatId);
            return true;
        }
        return false;
    }

    /**
     * Retorna todas as receitas salvas pelo usuário.
     */
    @Transactional(readOnly = true)
    public List<Recipe> findRecipesByChatId(Long telegramChatId) {
        return recipeRepository.findByUserTelegramChatId(telegramChatId);
    }

    private static final Set<String> STOPWORDS = Set.of(
        "de", "da", "do", "das", "dos", "com", "sem", "para", "em", "e", "ou", "a", "o", "as", "os", "um", "uma", "uns", "umas", "no", "na", "nos", "nas"
    );

    public List<String> extractSearchTokens(String query) {
        if (query == null) return List.of();
        String normalized = normalizeText(query);
        String[] words = normalized.split("[^a-z0-9]+");
        List<String> tokens = new ArrayList<>();
        for (String w : words) {
            if (!w.isBlank() && !STOPWORDS.contains(w)) {
                tokens.add(w);
            }
        }
        return tokens.isEmpty() && !normalized.isBlank() ? List.of(normalized.trim()) : tokens;
    }

    private boolean textMatchesToken(String text, String token) {
        if (text == null || token == null || token.isBlank()) return false;
        String normalizedText = normalizeText(text);
        if (normalizedText.contains(token)) return true;
        // Tratamento simples de plural/singular em português (ex: ovos -> ovo)
        if (token.endsWith("s") && token.length() > 3) {
            String singular = token.substring(0, token.length() - 1);
            if (normalizedText.contains(singular)) return true;
        }
        return false;
    }

    /**
     * Busca receitas pelo ID ou por palavras-chave com relevância, tokenização e busca em instruções.
     */
    @Transactional(readOnly = true)
    public List<Recipe> searchRecipes(Long telegramChatId, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String cleanQuery = query.trim();
        List<Recipe> allUserRecipes = recipeRepository.findByUserTelegramChatId(telegramChatId);
        if (allUserRecipes.isEmpty()) {
            return List.of();
        }

        List<String> tokens = extractSearchTokens(cleanQuery);
        String normalizedFullQuery = normalizeText(cleanQuery);

        // Checar se a query tem ID numérico direto (ex: "1" ou "#1")
        Long explicitId = null;
        String numeric = cleanQuery.startsWith("#") ? cleanQuery.substring(1).trim() : cleanQuery;
        if (numeric.matches("^\\d+$")) {
            try {
                explicitId = Long.parseLong(numeric);
            } catch (NumberFormatException ignored) {}
        }

        record ScoredRecipe(Recipe recipe, int score) {}
        List<ScoredRecipe> scored = new ArrayList<>();

        for (Recipe r : allUserRecipes) {
            int score = 0;

            // 1. Prioridade máxima se o ID for idêntico ao buscado
            if (explicitId != null && r.getId().equals(explicitId)) {
                score += 100;
            }

            // 2. Pontuação por correspondência exata de frase completa no título
            String normalizedTitle = normalizeText(r.getTitle());
            if (!normalizedFullQuery.isBlank() && normalizedTitle.contains(normalizedFullQuery)) {
                score += 25;
            }

            int titleTokensMatched = 0;

            for (String token : tokens) {
                // Título: peso 10 por token
                if (textMatchesToken(r.getTitle(), token)) {
                    score += 10;
                    titleTokensMatched++;
                }
                // Categoria: peso 5 por token
                if (r.getCategory() != null && textMatchesToken(r.getCategory(), token)) {
                    score += 5;
                }
                // Autor: peso 5 por token
                if (r.getSourceAuthor() != null && textMatchesToken(r.getSourceAuthor(), token)) {
                    score += 5;
                }
                // Ingredientes: peso 4 por token
                if (r.getIngredients() != null) {
                    for (Ingredient ing : r.getIngredients()) {
                        if (textMatchesToken(ing.getName(), token) || 
                            (ing.getNotes() != null && textMatchesToken(ing.getNotes(), token))) {
                            score += 4;
                            break;
                        }
                    }
                }
                // Modo de preparo / Instruções: peso 2 por token
                if (r.getInstructions() != null) {
                    for (Instruction inst : r.getInstructions()) {
                        if (textMatchesToken(inst.getInstructionText(), token)) {
                            score += 2;
                            break;
                        }
                    }
                }
            }

            // Bônus se todos os tokens foram encontrados no título
            if (!tokens.isEmpty() && titleTokensMatched == tokens.size()) {
                score += 15;
            }

            if (score > 0) {
                scored.add(new ScoredRecipe(r, score));
            }
        }

        // Ordenar por pontuação decrescente (mais relevantes primeiro)
        return scored.stream()
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(ScoredRecipe::recipe)
                .toList();
    }

    public String normalizeText(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase()
            .trim();
    }

    /**
     * Busca uma receita específica por ID e ChatId do usuário.
     */
    @Transactional(readOnly = true)
    public Optional<Recipe> findRecipeByUserAndId(Long telegramChatId, Long recipeId) {
        return recipeRepository.findByUserTelegramChatIdAndId(telegramChatId, recipeId);
    }

    /**
     * Formata uma entidade Recipe em texto Markdown amigável para envio no chat do Telegram.
     */
    public String formatRecipeForTelegram(Recipe recipe) {
        StringBuilder sb = new StringBuilder();

        sb.append("🍽️ *").append(recipe.getTitle()).append("* `(ID: #").append(recipe.getId()).append(")`\n\n");

        if (recipe.getSourceAuthor() != null && !recipe.getSourceAuthor().isBlank()) {
            sb.append("👤 *Ensinado por:* ").append(recipe.getSourceAuthor()).append("\n");
        }
        if (recipe.getCategory() != null && !recipe.getCategory().isBlank()) {
            sb.append("📂 *Categoria:* ").append(recipe.getCategory()).append("\n");
        }
        if (recipe.getPrepTimeMinutes() != null) {
            sb.append("⏱️ *Tempo:* ").append(recipe.getPrepTimeMinutes()).append(" min");
        }
        if (recipe.getPortions() != null) {
            sb.append(" | 👥 *Rendimento:* ").append(recipe.getPortions()).append(" porções");
        }
        sb.append("\n\n");

        sb.append("🛒 *Ingredientes:*\n");
        if (recipe.getIngredients().isEmpty()) {
            sb.append("• Nenhum ingrediente especificado.\n");
        } else {
            for (Ingredient ing : recipe.getIngredients()) {
                sb.append("• ");
                if (ing.getQuantity() != null && !ing.getQuantity().isBlank()) {
                    sb.append(ing.getQuantity()).append(" ");
                }
                if (ing.getUnit() != null && !ing.getUnit().isBlank()) {
                    sb.append(ing.getUnit()).append(" de ");
                }
                sb.append(ing.getName());
                if (ing.getNotes() != null && !ing.getNotes().isBlank()) {
                    sb.append(" (").append(ing.getNotes()).append(")");
                }
                sb.append("\n");
            }
        }
        sb.append("\n");

        sb.append("👩‍🍳 *Modo de Preparo:*\n");
        if (recipe.getInstructions().isEmpty()) {
            sb.append("1. Nenhum passo especificado.\n");
        } else {
            for (Instruction inst : recipe.getInstructions()) {
                sb.append(inst.getStepNumber()).append(". ").append(inst.getInstructionText()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Formata um resumo compacto de uma linha da receita para listagens e resultados múltiplos.
     */
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
}


