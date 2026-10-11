package ChefBot_Telegram.service;

import ChefBot_Telegram.dto.IngredientDTO;
import ChefBot_Telegram.dto.InstructionDTO;
import ChefBot_Telegram.dto.RecipeResponseDTO;
import ChefBot_Telegram.model.Ingredient;
import ChefBot_Telegram.model.Instruction;
import ChefBot_Telegram.model.Recipe;
import ChefBot_Telegram.repository.RecipeRepository;
import ChefBot_Telegram.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RecipeServiceUpdatePersistenceTest {

    private RecipeRepository recipeRepository;
    private UserRepository userRepository;
    private RecipeService recipeService;

    private static final Long CHAT_ID = 555L;
    private static final Long RECIPE_ID = 10L;

    @BeforeEach
    void setUp() {
        recipeRepository = Mockito.mock(RecipeRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        recipeService = new RecipeService(userRepository, recipeRepository);
    }

    private Recipe createExistingRecipe() {
        Recipe r = new Recipe();
        r.setId(RECIPE_ID);
        r.setTitle("Bolo de Laranja");
        r.setCategory("Sobremesa");
        r.setSourceAuthor("Vovó");
        r.setPrepTimeMinutes(50);
        r.setPortions(8);

        Ingredient ing1 = new Ingredient();
        ing1.setName("Laranja");
        ing1.setQuantity("2");
        r.addIngredient(ing1);

        Ingredient ing2 = new Ingredient();
        ing2.setName("Farinha");
        ing2.setQuantity("3");
        ing2.setUnit("xícaras");
        r.addIngredient(ing2);

        Instruction inst = new Instruction();
        inst.setStepNumber(1);
        inst.setInstructionText("Bata o suco com os ovos");
        r.addInstruction(inst);

        return r;
    }

    @Test
    @DisplayName("Deve atualizar somente o título sem perder ingredientes ou modo de preparo originais")
    void shouldUpdateOnlyTitleWithoutLosingIngredientsOrInstructions() {
        Recipe existing = createExistingRecipe();
        when(recipeRepository.findByUserTelegramChatIdAndId(CHAT_ID, RECIPE_ID)).thenReturn(Optional.of(existing));
        when(recipeRepository.saveAndFlush(any(Recipe.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecipeResponseDTO updateDTO = new RecipeResponseDTO(
            true, null, "Bolo de Laranja Especial", null, null, null, null, null, null
        );

        Recipe updated = recipeService.updateRecipeFromDTO(CHAT_ID, RECIPE_ID, updateDTO);

        assertEquals("Bolo de Laranja Especial", updated.getTitle());
        assertEquals("Sobremesa", updated.getCategory());
        assertEquals("Vovó", updated.getSourceAuthor());
        assertEquals(50, updated.getPrepTimeMinutes());
        // Ingredientes originais devem ser 100% preservados
        assertEquals(2, updated.getIngredients().size());
        assertEquals("Laranja", updated.getIngredients().get(0).getName());
        assertEquals("Farinha", updated.getIngredients().get(1).getName());
        // Instruções originais preservadas
        assertEquals(1, updated.getInstructions().size());
        assertEquals("Bata o suco com os ovos", updated.getInstructions().get(0).getInstructionText());
    }

    @Test
    @DisplayName("Não deve apagar ingredientes se a lista de ingredientes no DTO for vazia")
    void shouldNotClearIngredientsWhenDtoIngredientsEmpty() {
        Recipe existing = createExistingRecipe();
        when(recipeRepository.findByUserTelegramChatIdAndId(CHAT_ID, RECIPE_ID)).thenReturn(Optional.of(existing));
        when(recipeRepository.saveAndFlush(any(Recipe.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecipeResponseDTO updateDTO = new RecipeResponseDTO(
            true, null, null, null, null, 40, null, List.of(), List.of()
        );

        Recipe updated = recipeService.updateRecipeFromDTO(CHAT_ID, RECIPE_ID, updateDTO);

        assertEquals(40, updated.getPrepTimeMinutes());
        assertEquals(2, updated.getIngredients().size());
        assertEquals(1, updated.getInstructions().size());
    }

    @Test
    @DisplayName("Deve substituir ingredientes com segurança quando uma nova lista for fornecida")
    void shouldUpdateIngredientsSafelyWhenProvided() {
        Recipe existing = createExistingRecipe();
        when(recipeRepository.findByUserTelegramChatIdAndId(CHAT_ID, RECIPE_ID)).thenReturn(Optional.of(existing));
        when(recipeRepository.saveAndFlush(any(Recipe.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IngredientDTO novoIng = new IngredientDTO("Adoçante", "1", "xícara", null);
        RecipeResponseDTO updateDTO = new RecipeResponseDTO(
            true, null, null, null, null, null, null, List.of(novoIng), null
        );

        Recipe updated = recipeService.updateRecipeFromDTO(CHAT_ID, RECIPE_ID, updateDTO);

        assertEquals(1, updated.getIngredients().size());
        assertEquals("Adoçante", updated.getIngredients().get(0).getName());
        assertSame(updated, updated.getIngredients().get(0).getRecipe());
    }

    @Test
    @DisplayName("Deve permitir limpar categoria enviando string em branco")
    void shouldClearCategoryWhenEmptyStringProvided() {
        Recipe existing = createExistingRecipe();
        when(recipeRepository.findByUserTelegramChatIdAndId(CHAT_ID, RECIPE_ID)).thenReturn(Optional.of(existing));
        when(recipeRepository.saveAndFlush(any(Recipe.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecipeResponseDTO updateDTO = new RecipeResponseDTO(
            true, null, null, "   ", null, null, null, null, null
        );

        Recipe updated = recipeService.updateRecipeFromDTO(CHAT_ID, RECIPE_ID, updateDTO);

        assertNull(updated.getCategory());
    }
}
