package ChefBot_Telegram.service;

import ChefBot_Telegram.config.RestClientConfig;
import ChefBot_Telegram.dto.RecipeResponseDTO;
import ChefBot_Telegram.model.Ingredient;
import ChefBot_Telegram.model.Instruction;
import ChefBot_Telegram.model.Recipe;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GeminiServiceUpdatePromptTest {

    private GeminiService geminiService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new RestClientConfig().objectMapper();
        RestClient restClient = RestClient.builder().build();
        geminiService = new GeminiService(restClient, objectMapper);
    }

    private Recipe createSampleRecipe() {
        Recipe r = new Recipe();
        r.setId(1L);
        r.setTitle("Bolo Simples");
        r.setCategory("Doces");
        r.setSourceAuthor("Ana");
        r.setPrepTimeMinutes(30);
        r.setPortions(4);

        Ingredient ing = new Ingredient();
        ing.setName("Açúcar");
        ing.setQuantity("1");
        ing.setUnit("xícara");
        r.addIngredient(ing);

        Instruction inst = new Instruction();
        inst.setStepNumber(1);
        inst.setInstructionText("Misture tudo");
        r.addInstruction(inst);

        return r;
    }

    @Test
    @DisplayName("Deve resolver alteração de autor via Fast-Path sem chamar API externa")
    void shouldUpdateAuthorViaFastPathWithoutCallingAi() {
        Recipe recipe = createSampleRecipe();

        Optional<RecipeResponseDTO> result = geminiService.tryDirectFieldEdit(recipe, "mudar o autor para Minha Mãe");

        assertTrue(result.isPresent());
        RecipeResponseDTO dto = result.get();
        assertEquals("Minha Mãe", dto.sourceAuthor());
        assertEquals("Bolo Simples", dto.title());
        assertEquals(30, dto.prepTimeMinutes());
        assertEquals(1, dto.ingredients().size());
        assertEquals("Açúcar", dto.ingredients().get(0).name());
    }

    @Test
    @DisplayName("Deve resolver alteração de título via Fast-Path")
    void shouldUpdateTitleViaFastPath() {
        Recipe recipe = createSampleRecipe();

        Optional<RecipeResponseDTO> result = geminiService.tryDirectFieldEdit(recipe, "mudar o título para Bolo de Chocolate");

        assertTrue(result.isPresent());
        RecipeResponseDTO dto = result.get();
        assertEquals("Bolo de Chocolate", dto.title());
        assertEquals("Ana", dto.sourceAuthor());
    }

    @Test
    @DisplayName("Deve resolver alteração de tempo via Fast-Path")
    void shouldUpdatePrepTimeViaFastPath() {
        Recipe recipe = createSampleRecipe();

        Optional<RecipeResponseDTO> result = geminiService.tryDirectFieldEdit(recipe, "tempo de preparo é 45 minutos");

        assertTrue(result.isPresent());
        RecipeResponseDTO dto = result.get();
        assertEquals(45, dto.prepTimeMinutes());
        assertEquals("Bolo Simples", dto.title());
    }

    @Test
    @DisplayName("Deve resolver alteração de porções via Fast-Path")
    void shouldUpdatePortionsViaFastPath() {
        Recipe recipe = createSampleRecipe();

        Optional<RecipeResponseDTO> result = geminiService.tryDirectFieldEdit(recipe, "rendimento: 8 porções");

        assertTrue(result.isPresent());
        RecipeResponseDTO dto = result.get();
        assertEquals(8, dto.portions());
    }

    @Test
    @DisplayName("Deve delegar para IA quando a instrução for complexa (ex: troca de ingredientes)")
    void shouldDelegateToAiWhenComplexInstruction() {
        Recipe recipe = createSampleRecipe();

        Optional<RecipeResponseDTO> result = geminiService.tryDirectFieldEdit(recipe, "troque o açúcar por 1 xícara de adoçante");

        assertFalse(result.isPresent(), "Instruções complexas de ingredientes devem acionar a IA e não o Fast-Path");
    }
}
