package ChefBot_Telegram.dto;

public record IngredientDTO(
    String name,
    String quantity,
    String unit,
    String notes
) {}
