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

    /**
     * Busca receitas pelo ID ou por palavras-chave com normalização de acentos e termos parciais.
     */
    @Transactional(readOnly = true)
    public List<Recipe> searchRecipes(Long telegramChatId, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String cleanQuery = query.trim();
        List<Recipe> allUserRecipes = recipeRepository.findByUserTelegramChatId(telegramChatId);
        List<Recipe> results = new ArrayList<>();

        // 1. Verificação por ID (ex: "1" ou "#1")
        String numeric = cleanQuery.startsWith("#") ? cleanQuery.substring(1).trim() : cleanQuery;
        if (numeric.matches("^\\d+$")) {
            try {
                Long id = Long.parseLong(numeric);
                for (Recipe r : allUserRecipes) {
                    if (r.getId().equals(id)) {
                        results.add(r);
                        return results;
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        // 2. Busca por texto normalizado (sem acentos, minúsculo)
        String normalizedQuery = normalizeText(cleanQuery);

        for (Recipe r : allUserRecipes) {
            boolean matches = false;

            // Checa título
            if (normalizeText(r.getTitle()).contains(normalizedQuery)) {
                matches = true;
            }
            // Checa categoria
            else if (r.getCategory() != null && normalizeText(r.getCategory()).contains(normalizedQuery)) {
                matches = true;
            }
            // Checa autor
            else if (r.getSourceAuthor() != null && normalizeText(r.getSourceAuthor()).contains(normalizedQuery)) {
                matches = true;
            }
            // Checa ingredientes
            else if (r.getIngredients() != null) {
                for (Ingredient ing : r.getIngredients()) {
                    if (normalizeText(ing.getName()).contains(normalizedQuery) ||
                        (ing.getNotes() != null && normalizeText(ing.getNotes()).contains(normalizedQuery))) {
                        matches = true;
                        break;
                    }
                }
            }

            if (matches && results.stream().noneMatch(existing -> existing.getId().equals(r.getId()))) {
                results.add(r);
            }
        }

        return results;
    }

    private String normalizeText(String input) {
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
}


