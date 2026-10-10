package ChefBot_Telegram.dto;

import java.util.List;

public record RecipeResponseDTO(
    Boolean isRecipe,
    String refusalReason,
    String title,
    String category,
    String sourceAuthor,
    Integer prepTimeMinutes,
    Integer portions,
    List<IngredientDTO> ingredients,
    List<InstructionDTO> instructions
) {}

