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
     * Retorna todas as receitas salvas pelo usuário.
     */
    @Transactional(readOnly = true)
    public List<Recipe> findRecipesByChatId(Long telegramChatId) {
        return recipeRepository.findByUserTelegramChatId(telegramChatId);
    }

    /**
     * Busca receitas pelo título (ignorando maiúsculas/minúsculas).
     */
    @Transactional(readOnly = true)
    public List<Recipe> searchRecipesByTitle(Long telegramChatId, String title) {
        return recipeRepository.findByUserTelegramChatIdAndTitleContainingIgnoreCase(telegramChatId, title);
    }

    /**
     * Busca uma receita específica por ID.
     */
    @Transactional(readOnly = true)
    public Optional<Recipe> findRecipeById(Long recipeId) {
        return recipeRepository.findById(recipeId);
    }

    /**
     * Formata uma entidade Recipe em texto Markdown amigável para envio no chat do Telegram.
     */
    public String formatRecipeForTelegram(Recipe recipe) {
        StringBuilder sb = new StringBuilder();

        sb.append("🍽️ *").append(recipe.getTitle()).append("*\n\n");

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
